package com.example.farmapp.buyer

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.supplier.supplierAuth
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.math.BigDecimal
import java.time.Instant

class BuyerDeliveryTest {
    private class Store : BuyerDeliveryStore {
        var id: Long? = null; var pending = false
        override fun selectedAddress() = id
        override fun selectAddress(id: Long?) { this.id = id }
        override fun addressSavePending() = pending
        override fun markAddressSave(pending: Boolean) { this.pending = pending }
    }
    private class Api : BuyerAddressApi {
        var rows = emptyList<BuyerAddressDto>(); var writes = 0; var fail = false; var wrongOwner = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun list(session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = rows))
        override suspend fun create(request: BuyerAddressRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerAddressDto>> {
            writes++; gate?.await(); if (fail) throw java.net.SocketTimeoutException()
            val row = BuyerAddressDto(7, if (wrongOwner) 99 else 42, request.label, request.addressText, request.latitude, request.longitude)
            rows = rows + row
            return Response.success(CanonicalApiResponse(true, data = row))
        }
    }
    private fun sessions() = signedInStore(supplierAuth().copy(role = "BUYER"))
    @Test fun fullAddressRequiredAndOptionalCoordinatesMustBeValidPairs() {
        assertNull(buyerAddressRequest("", "Synthetic town, landmark").latitude)
        for ((lat, lon) in listOf("1" to "", "NaN" to "2", "91" to "2", "1" to "181"))
            assertTrue(runCatching { buyerAddressRequest("Home", "Synthetic", lat, lon) }.isFailure)
        assertTrue(runCatching { buyerAddressRequest("Home", " ") }.isFailure)
        assertFalse(buyerAddressRequest("Home", "Private synthetic address").toString().contains("Private"))
    }
    @Test fun onlyConfirmedOwnedAddressIsSelectedAndSelectionRestoresWithoutChangingCart() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Store()
            val cart = BuyerSelection(cart = mapOf(8L to 3))
            val c = BuyerAddressController(api, sessions, sessions.session.value, scope, disk)
            c.refresh(); c.create(buyerAddressRequest("Home", "Synthetic address"))
            assertEquals(7L, c.state.value.selectedId); assertEquals(7L, disk.id); assertFalse(disk.pending)
            assertTrue(buyerDeliverySelectionValid(c.state.value))
            val restored = BuyerAddressController(api, sessions, sessions.session.value, scope, disk)
            restored.refresh(); assertEquals(7L, restored.state.value.selectedId)
            assertEquals(mapOf(8L to 3), cart.cart)
            api.rows = emptyList(); restored.refresh(); assertNull(restored.state.value.selectedId); assertNull(disk.id)
        } finally { scope.cancel() }
    }
    @Test fun duplicateSavesTimeoutAndRestartNeverPublishFakeAddress() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api().apply { gate = CompletableDeferred(); fail = true }; val sessions = sessions(); val disk = Store()
            val c = BuyerAddressController(api, sessions, sessions.session.value, scope, disk)
            c.create(buyerAddressRequest("", "Synthetic")); c.create(buyerAddressRequest("", "Synthetic"))
            assertEquals(1, api.writes); assertTrue(c.state.value.saving)
            api.gate!!.complete(Unit)
            assertTrue(c.state.value.rows.isEmpty()); assertNull(c.state.value.selectedId); assertTrue(disk.pending)
            val restored = BuyerAddressController(api, sessions, sessions.session.value, scope, disk)
            restored.refresh(); assertTrue(restored.state.value.saveUncertain)
            restored.create(buyerAddressRequest("", "Synthetic")); assertEquals(1, api.writes)
        } finally { scope.cancel() }
    }
    @Test fun wrongOwnerAndLateLogoutCannotSelectCreatedAddress() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api().apply { wrongOwner = true }; val sessions = sessions()
            val c = BuyerAddressController(api, sessions, sessions.session.value, scope, Store())
            c.create(buyerAddressRequest("", "Synthetic")); assertNull(c.state.value.selectedId); assertNotNull(c.state.value.saveError)
            val held = Api().apply { gate = CompletableDeferred() }
            val other = BuyerAddressController(held, sessions, sessions.session.value, scope, Store())
            other.create(buyerAddressRequest("", "Synthetic")); sessions.beginLogout(); held.gate!!.complete(Unit)
            assertTrue(other.state.value.rows.isEmpty()); assertNull(other.state.value.selectedId)
        } finally { scope.cancel() }
    }
    @Test fun staleLoadingAndMissingSelectionsCannotContinue() {
        val good = BuyerAddressState(rows = listOf(BuyerAddressDto(7, 42, "Home", "Synthetic")), loaded = true, selectedId = 7)
        assertTrue(buyerDeliverySelectionValid(good))
        for (bad in listOf(good.copy(error = "Unavailable"), good.copy(busy = true), good.copy(saving = true),
            good.copy(loaded = false), good.copy(selectedId = 8), good.copy(selectedId = null))) assertFalse(buyerDeliverySelectionValid(bad))
    }
    @Test fun addressAuthErrorsDifferFromLocalSelectionStorageFailure() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var deny = true
            val api = object : BuyerAddressApi {
                override suspend fun create(request: BuyerAddressRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerAddressDto>> = error("No writes")
                override suspend fun list(session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerAddressDto>>> =
                    if (deny) Response.error(403, "{}".toResponseBody("application/json".toMediaType()))
                    else Response.success(CanonicalApiResponse(true, data = listOf(BuyerAddressDto(7,42,"Home","Synthetic"))))
            }
            val prefs = object : BuyerDeliveryStore {
                override fun selectedAddress(): Long? = 7
                override fun selectAddress(id: Long?) { throw java.io.IOException("Synthetic storage failure") }
                override fun addressSavePending() = false
                override fun markAddressSave(pending: Boolean) = Unit
            }
            val sessions = sessions(); val c = BuyerAddressController(api, sessions, sessions.session.value, scope, prefs)
            c.refresh(); assertEquals("Sign in again to load your addresses.", c.state.value.error)
            deny = false; c.refresh(); assertNull(c.state.value.error); assertEquals(1, c.state.value.rows.size)
            assertTrue(c.state.value.loaded); assertNull(c.state.value.selectedId); assertNotNull(c.state.value.saveError)
        } finally { scope.cancel() }
    }
    @Test fun quoteValidationUsesExactTotalsAddressAndExpiration() {
        val now = Instant.parse("2026-10-28T00:00:00Z")
        val quote = BuyerDeliveryQuote("opaque", 7, BigDecimal("125.57"), BigDecimal("50.01"), BigDecimal("175.58"), "KES", now.plusSeconds(60).toString(), "DOOR_DELIVERY")
        assertEquals(BigDecimal("175.58"), validatedDeliveryQuote(quote, 7, now).total)
        for (bad in listOf(quote.copy(addressId = 8), quote.copy(total = BigDecimal("175.59")),
            quote.copy(expiresAt = now.toString()), quote.copy(currency = "USD")))
            assertTrue(runCatching { validatedDeliveryQuote(bad, 7, now) }.isFailure)
    }
    @Test fun addressAndQuoteRequestsUseOwnRoutesAndOnlySupportedFields() = runBlocking {
        val requests = mutableListOf<Request>()
        val retrofit = Retrofit.Builder().baseUrl("https://synthetic.example.test/api/v1/")
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"success\":true,\"data\":{}}".toResponseBody("application/json".toMediaType())).build()
            }.build()).build()
        val session = sessions().session.value
        retrofit.create(BuyerAddressApi::class.java).create(buyerAddressRequest("Home", "Synthetic address"), session)
        assertEquals("/api/v1/users/addresses", requests.last().url.encodedPath)
        fun fields(request: Request) = com.google.gson.JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("addressText", "label"), fields(requests.last()).keySet())
        retrofit.create(BuyerCheckoutApi::class.java).quote(BuyerDeliveryQuoteRequest.door(7), session)
        assertEquals("/api/v1/checkout/delivery-quote", requests.last().url.encodedPath)
        assertEquals(setOf("deliveryMethod", "addressId"), fields(requests.last()).keySet())
        assertEquals(7L, fields(requests.last())["addressId"].asLong)
    }
}
