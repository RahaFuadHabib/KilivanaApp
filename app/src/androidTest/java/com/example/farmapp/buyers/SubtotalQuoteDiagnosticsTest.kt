package com.example.farmapp.buyers

import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.RequiresAuthenticatedBuyer
import com.example.farmapp.BuildConfig
import com.example.farmapp.network.EncryptedSessionPersistence
import com.example.farmapp.buyer.buyerScope
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Test
import org.junit.Assert.*

/** Explicit opt-in, one non-financial quote only. Never prepares cart, creates orders or initiates payment. */
@RequiresAuthenticatedBuyer
class SubtotalQuoteDiagnosticsTest {
    private fun emit(value: String) { val b = android.os.Bundle(); b.putString("quote_read_probe", value); InstrumentationRegistry.getInstrumentation().sendStatus(2, b) }
    @Test fun captureOneQuoteSubtotal() {
        check(InstrumentationRegistry.getArguments().getString("allowQuoteProbe") == "true")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = EncryptedSessionPersistence(context).read() ?: error("Authorized Buyer session required")
        check(auth.role == "BUYER" && auth.sessionOrigin == BuildConfig.API_BASE_URL)
        val prefs = context.getSharedPreferences("buyer-checkout-" + buyerScope(BuildConfig.API_BASE_URL, auth.userId), 0)
        val journal = JsonParser.parseString(prefs.getString("journal", null) ?: error("Checkout journal required")).asJsonObject
        val items = journal.getAsJsonObject("items").entrySet()
        var currentSubtotal = java.math.BigDecimal.ZERO
        val readClient = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build()
        for ((key, value) in items) {
            val productId = key.toLong().also { check(it > 0) }
            val quantity = value.asInt.also { check(it > 0) }
            val productRequest = Request.Builder().url(BuildConfig.API_BASE_URL + "products/" + productId)
                .header("Authorization", "Bearer " + auth.accessToken).build()
            readClient.newCall(productRequest).execute().use { response ->
                emit("SUBTOTAL_READ_PROBE productHttp=" + response.code)
                assertEquals(200,response.code)
                val product = JsonParser.parseString(response.body!!.string()).asJsonObject.getAsJsonObject("data")
                currentSubtotal = currentSubtotal.add(product.get("price").asBigDecimal.multiply(quantity.toBigDecimal()))
            }
        }
        emit("SUBTOTAL_READ_PROBE selectedItems=" + items.size + " distinctIds=" + items.size)
        emit("SUBTOTAL_READ_PROBE quantity=" + items.sumOf { it.value.asLong })
        emit("SUBTOTAL_READ_PROBE journalSubtotal=" + journal.get("expectedSubtotal").asBigDecimal.toPlainString())
        emit("SUBTOTAL_READ_PROBE freshProductSubtotal=" + currentSubtotal.toPlainString())
        emit("SUBTOTAL_READ_PROBE phase=" + journal.get("phase").asString)
        check(journal.get("phase").asString == "CHECKOUT_ACKNOWLEDGED")
        val selection = journal.getAsJsonObject("request")
        val id = selection.get("addressId").asLong
        val request = Request.Builder().url(BuildConfig.API_BASE_URL + "users/addresses")
            .header("Authorization", "Bearer " + auth.accessToken).build()
        OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build().newCall(request).execute().use { response ->
            emit("QUOTE_READ_PROBE addressListHttp=" + response.code)
            assertEquals(200, response.code)
            val rows = JsonParser.parseString(response.body!!.string()).asJsonObject.getAsJsonArray("data")
            val row = rows.map { it.asJsonObject }.singleOrNull { it.get("id")?.asLong == id }
            check(row != null && row.get("userId").asString == auth.userId)
            emit("QUOTE_READ_PROBE selectedAddressExists=" + (row != null))
            emit("QUOTE_READ_PROBE selectedAddressOwned=" + (row?.get("userId")?.asString == auth.userId))
            emit("QUOTE_READ_PROBE latitudePresent=" + (row?.get("latitude")?.isJsonNull == false))
            emit("QUOTE_READ_PROBE longitudePresent=" + (row?.get("longitude")?.isJsonNull == false))
            emit("QUOTE_READ_PROBE coordinatesPresent=" + (row?.get("latitude")?.isJsonNull == false && row.get("longitude")?.isJsonNull == false))
        }

        check(selection.get("deliveryMethod").asString == "DOOR_DELIVERY")
        val body = com.google.gson.JsonObject().apply {
            addProperty("deliveryMethod", "DOOR_DELIVERY")
            addProperty("addressId", id)
        }
        val quoteRequest = Request.Builder().url(BuildConfig.API_BASE_URL + "checkout/delivery-quote")
            .header("Authorization", "Bearer " + auth.accessToken)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        readClient.newCall(quoteRequest).execute().use { response ->
            emit("QUOTE_SUBTOTAL_PROBE http=" + response.code)
            assertEquals(200,response.code)
            val quote = JsonParser.parseString(response.body!!.string()).asJsonObject.getAsJsonObject("data")
            val server = quote.get("subtotal").asBigDecimal
            emit("QUOTE_SUBTOTAL_PROBE serverSubtotal=" + server.toPlainString())
            emit("QUOTE_SUBTOTAL_PROBE differenceFromFreshProducts=" + server.subtract(currentSubtotal).toPlainString())
            emit("QUOTE_SUBTOTAL_PROBE deliveryFee=" + quote.get("deliveryFee").asBigDecimal.toPlainString())
        }
    }
}
