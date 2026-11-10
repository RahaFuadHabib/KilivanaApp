package com.example.farmapp.buyers

import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.RequiresAuthenticatedBuyer
import com.example.farmapp.BuildConfig
import com.example.farmapp.network.EncryptedSessionPersistence
import com.example.farmapp.buyer.buyerScope
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.Assert.*

/** Explicit genuine-session read-only probe. Never refreshes tokens or sends writes. */
@RequiresAuthenticatedBuyer
class QuoteReadOnlyDiagnosticsTest {
    private fun emit(value: String) { val b = android.os.Bundle(); b.putString("quote_read_probe", value); InstrumentationRegistry.getInstrumentation().sendStatus(2, b) }
    @Test fun selectedAddressPrerequisites() {
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
        val selection = journal.getAsJsonObject("request")
        val id = selection.get("addressId").asLong
        val request = Request.Builder().url(BuildConfig.API_BASE_URL + "users/addresses")
            .header("Authorization", "Bearer " + auth.accessToken).build()
        OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).build().newCall(request).execute().use { response ->
            emit("QUOTE_READ_PROBE addressListHttp=" + response.code)
            assertEquals(200, response.code)
            val rows = JsonParser.parseString(response.body!!.string()).asJsonObject.getAsJsonArray("data")
            val row = rows.map { it.asJsonObject }.singleOrNull { it.get("id")?.asLong == id }
            emit("QUOTE_READ_PROBE selectedAddressExists=" + (row != null))
            emit("QUOTE_READ_PROBE selectedAddressOwned=" + (row?.get("userId")?.asString == auth.userId))
            emit("QUOTE_READ_PROBE latitudePresent=" + (row?.get("latitude")?.isJsonNull == false))
            emit("QUOTE_READ_PROBE longitudePresent=" + (row?.get("longitude")?.isJsonNull == false))
            emit("QUOTE_READ_PROBE coordinatesPresent=" + (row?.get("latitude")?.isJsonNull == false && row.get("longitude")?.isJsonNull == false))
        }
    }
}
