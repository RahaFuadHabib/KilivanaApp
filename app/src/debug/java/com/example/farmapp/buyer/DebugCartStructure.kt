package com.example.farmapp.buyer

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** All output is generated from fixed labels, allowlisted field names, types, booleans and counts. */
internal fun sanitizeBuyerCartStructure(http: Int, json: JsonElement?, localRows: Int, localUnits: Long): String {
    val safeNames = setOf("success", "message", "data", "result", "content", "cart", "error", "code", "details", "timestamp",
        "id", "buyerId", "cartId", "status", "createdAt", "updatedAt", "items", "cartItems", "lineItems", "lines", "products",
        "entries", "product", "productId", "product_id", "itemId", "cartItemId", "quantity", "qty", "unitPrice", "price",
        "subtotal", "subTotal", "lineTotal", "itemTotal", "total", "totalPrice", "totalAmount", "currency", "amount",
        "name", "productName", "unit", "size", "number", "page", "totalElements", "empty", "revision", "version")
    val sensitiveNames = Regex("(?i).*(token|password|authorization|cookie|secret|credential).*")
    val listNames = setOf("items", "cartItems", "lineItems", "lines", "products", "entries")
    fun type(v: JsonElement?): String = when {
        v == null || v.isJsonNull -> "null"
        v.isJsonObject -> "object"
        v.isJsonArray -> "array"
        v.asJsonPrimitive.isBoolean -> "boolean"
        v.asJsonPrimitive.isNumber -> "number"
        else -> "string"
    }
    fun safe(name: String) = if (name in safeNames) name else "[unrecognized field]"
    val out = mutableListOf("HTTP status: $http", "Response type: ${if (json == null) "not JSON or unreadable" else type(json)}",
        "Local cart product rows: $localRows", "Local cart unit count: $localUnits")
    var candidates = 0
    var cartStatus = false
    var wrappers = mutableSetOf<String>()
    var remaining = 120
    fun visit(value: JsonElement, path: String, depth: Int, field: String = "") {
        if (remaining-- <= 0 || depth > 6) return
        when {
            value.isJsonObject -> {
                val entries = value.asJsonObject.entrySet().take(60)
                out += "$path keys/types: " + entries.joinToString(", ") { (key, child) ->
                    if (sensitiveNames.matches(key)) "[sensitive field omitted]" else "${safe(key)} (${type(child)})"
                }
                entries.forEach { (key, child) ->
                    if (key == "status") cartStatus = true
                    if (key in setOf("data", "result", "content", "cart")) wrappers += key
                    if (!sensitiveNames.matches(key) && (child.isJsonObject || child.isJsonArray))
                        visit(child, "$path.${safe(key)}", depth + 1, key)
                }
            }
            value.isJsonArray -> {
                val array = value.asJsonArray
                val sample = array.take(100)
                fun hasProduct(item: JsonElement): Boolean = item.isJsonObject &&
                    (item.asJsonObject.has("productId") || item.asJsonObject.has("product_id") ||
                        item.asJsonObject.get("product")?.takeIf { it.isJsonObject }?.asJsonObject?.has("id") == true)
                val candidate = field in listNames || sample.any { hasProduct(it) }
                if (candidate) {
                    candidates++
                    out += "Candidate item list: $path; present: yes; item count: ${array.size()}"
                    sample.forEachIndexed { index, item ->
                        if (index < 10 && remaining > 0) {
                            val obj = item.takeIf { it.isJsonObject }?.asJsonObject
                            fun has(vararg keys: String) = keys.any { obj?.has(it) == true }
                            out += "Candidate item ${index + 1}: type=${type(item)}, product identifier fields=${hasProduct(item)}, quantity fields=${has("quantity", "qty")}, price fields=${has("unitPrice", "price")}, total fields=${has("subtotal", "subTotal", "lineTotal", "total", "itemTotal")}"
                            visit(item, "$path[]", depth + 1)
                        }
                    }
                    if (array.size() > 10) out += "Only the first 10 items were inspected; remaining items not assessed."
                } else if (sample.isNotEmpty()) visit(sample.first(), "$path[]", depth + 1)
            }
        }
    }
    if (json != null) visit(json, "root", 0)
    out += "Wrapper field names present: " + wrappers.sorted().joinToString(", ").ifBlank { "none observed" }
    out += "Status field present: $cartStatus"
    if (candidates == 0) out += "Candidate item list present: no; server item count: not observable"
    out += "Local/server contents match: unverified (structure inspection only)"
    out += "Server cart populated: ${if (candidates == 0) "unknown; metadata is not proof of an empty or populated cart" else "candidate counts shown above; line mapping still requires verification"}"
    out += "No cart write, checkout, quote, order or payment request was made."
    return out.joinToString("\n")
}
internal fun parseCartStructureJson(text: String): JsonElement? = runCatching { JsonParser.parseString(text) }.getOrNull()
