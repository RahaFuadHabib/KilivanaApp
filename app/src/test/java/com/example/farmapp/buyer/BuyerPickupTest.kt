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
import java.time.*

class BuyerPickupTest {
    private class Store : BuyerPickupStore {
        var selected: Long? = null; var pending: BuyerPickupMutation? = null
        override fun selectedPickup() = selected
        override fun selectPickup(id: Long?) { selected = id }
        override fun pendingPickup() = pending
        override fun pendingPickup(value: BuyerPickupMutation?) { pending = value }
    }
    private class Api : BuyerPickupApi {
        var up = true; var writes = 0; var detailReads = 0; var regionRead: String? = null
        var lastRemoved: Long? = null; var lastDefault: Long? = null; var hold: CompletableDeferred<Unit>? = null
        var timeoutAfterSave = false; var wrongOwner = false
        val stations = listOf(BuyerPickupStation(11, "Synthetic north station", "Synthetic north address", region = "Synthetic North", isAvailable = true),
            BuyerPickupStation(12, "Synthetic south station", "Synthetic south address", region = "Synthetic South", isAvailable = true))
        var records = emptyList<BuyerSavedPickup>()
        override suspend fun availability(session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = up))
        override suspend fun available(session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerPickupStation>>> {
            hold?.await(); return Response.success(CanonicalApiResponse(true, data = stations))
        }
        override suspend fun region(region: String, session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerPickupStation>>> {
            regionRead = region; return Response.success(CanonicalApiResponse(true, data = stations.filter { it.region == region }))
        }
        override suspend fun detail(stationId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerPickupStation>> {
            detailReads++; return Response.success(CanonicalApiResponse(true, data = stations.first { it.id == stationId }))
        }
        override suspend fun saved(session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = records))
        override suspend fun save(request: BuyerSavePickupRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerSavedPickup>> {
            writes++
            val station = stations.first { it.id == request.pickupStationId }
            if (request.isDefault) records = records.map { it.copy(isDefault = false) }
            val record = BuyerSavedPickup(1000 + request.pickupStationId, if (wrongOwner) 99 else 42, station.id,
                station.name, station.address, station.region, true, request.label, request.isDefault)
            records = records + record
            if (timeoutAfterSave) throw java.net.SocketTimeoutException()
            return Response.success(CanonicalApiResponse(true, data = record))
        }
        override suspend fun remove(userStationId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<Unit>> {
            writes++; lastRemoved = userStationId; records = records.filterNot { it.id == userStationId }
            return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun setDefault(userStationId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerSavedPickup>> {
            writes++; lastDefault = userStationId; records = records.map { it.copy(isDefault = it.id == userStationId) }
            return Response.success(CanonicalApiResponse(true, data = records.first { it.id == userStationId }))
        }
    }
    private fun sessions() = signedInStore(supplierAuth().copy(role = "BUYER"))
    @Test fun availableRegionsSelectionSavedDefaultAndRemovalUseCorrectIdentities() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Store()
            val c = BuyerPickupController(api, sessions, sessions.session.value, disk, scope)
            c.refresh(); assertTrue(c.state.value.available); assertEquals(2, c.state.value.stations.size)
            c.refresh("Synthetic North"); assertEquals("Synthetic North", api.regionRead); assertEquals(1, c.state.value.stations.size)
            c.select(11); assertEquals(11L, disk.selected); assertTrue(pickupSelectionValid(c.state.value))
            c.save(11, "Favourite", false); assertEquals(1011L, c.state.value.saved.single().id); assertNull(disk.pending)
            c.select(12); c.setDefault(1011); assertEquals(11L, c.state.value.selected?.id); assertEquals(1011L, api.lastDefault); assertTrue(c.state.value.saved.single().isDefault!!)
            val restored = BuyerPickupController(api, sessions, sessions.session.value, disk, scope)
            restored.refresh(); assertEquals(11L, restored.state.value.selected?.id)
            restored.remove(1011); assertEquals(1011L, api.lastRemoved); assertTrue(restored.state.value.saved.isEmpty()); assertNull(restored.state.value.selected)
        } finally { scope.cancel() }
    }
    @Test fun timeoutReconcilesReadBackWithoutRepeatingSave() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Store()
            val c = BuyerPickupController(api, sessions, sessions.session.value, disk, scope)
            c.refresh(); api.timeoutAfterSave = true; c.save(11, "Favourite", true)
            assertTrue(c.state.value.uncertain); assertNotNull(disk.pending)
            c.save(11, "Favourite", true); assertEquals(1, api.writes)
            val restored = BuyerPickupController(api, sessions, sessions.session.value, disk, scope)
            restored.refresh(); assertNull(disk.pending); assertEquals(1, restored.state.value.saved.size)
            assertEquals(1, api.writes); assertEquals(11L, restored.state.value.selected?.id)
        } finally { scope.cancel() }
    }
    @Test fun unavailableServiceForeignOwnerAndLateLogoutNeverSelectOrPublish() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Store()
            val c = BuyerPickupController(api, sessions, sessions.session.value, disk, scope)
            api.up = false; c.refresh(); assertFalse(pickupSelectionValid(c.state.value)); c.select(11); assertNull(c.state.value.selected)
            api.up = true; c.refresh(); api.wrongOwner = true; c.save(11, "", false)
            assertTrue(c.state.value.saved.isEmpty()); assertNotNull(c.state.value.error)
            val held = Api().apply { hold = CompletableDeferred() }
            val late = BuyerPickupController(held, sessions, sessions.session.value, Store(), scope)
            late.refresh(); sessions.beginLogout(); held.hold!!.complete(Unit)
            assertTrue(late.state.value.stations.isEmpty()); assertTrue(late.state.value.saved.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun pickupAndDoorQuotesAreExclusiveAndExpireWithExactPrices() {
        val now = Instant.parse("2026-10-28T00:00:00Z")
        val door = BuyerDeliveryQuoteRequest.door(8); val pickup = BuyerDeliveryQuoteRequest.pickup(11)
        assertNull(door.pickupStationId); assertNull(pickup.addressId)
        assertTrue(runCatching { BuyerDeliveryQuoteRequest("DOOR_DELIVERY", 8, 11) }.isFailure)
        val quote = BuyerDeliveryQuote("opaque", null, BigDecimal("125.57"), BigDecimal("50.00"), BigDecimal("175.57"), "KES", now.plusSeconds(60).toString(), "PICKUP_STATION", 11)
        assertEquals(BigDecimal("175.57"), validatedDeliveryQuote(quote, pickup, now).total)
        assertTrue(runCatching { validatedDeliveryQuote(quote, door, now) }.isFailure)
        assertTrue(runCatching { validatedDeliveryQuote(quote, pickup, now.plusSeconds(61)) }.isFailure)
        assertTrue(runCatching { validatedDeliveryQuote(quote.copy(total = BigDecimal("205.57")), pickup, now) }.isFailure)
    }
    @Test fun retrofitUsesSavedRecordIdForWritesAndPlatformIdForSaveAndQuote() = runBlocking {
        val requests = mutableListOf<Request>()
        val retrofit = Retrofit.Builder().baseUrl("https://synthetic.example.test/api/v1/")
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                val data = when {
                    chain.request().url.encodedPath.endsWith("availability") -> "true"
                    chain.request().method == "GET" && !chain.request().url.encodedPath.endsWith("/11") -> "[]"
                    else -> "{}"
                }
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"success\":true,\"data\":$data}".toResponseBody("application/json".toMediaType())).build()
            }.build()).build()
        val session = sessions().session.value; val api = retrofit.create(BuyerPickupApi::class.java)
        api.available(session); api.region("Synthetic North", session); api.detail(11, session); api.availability(session); api.saved(session)
        assertEquals("/api/v1/pickup-stations/available/region/Synthetic%20North", requests[1].url.encodedPath)
        api.save(BuyerSavePickupRequest(11, "Favourite", true), session)
        val json = com.google.gson.JsonParser.parseString(Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(11L, json["pickupStationId"].asLong); assertEquals(setOf("pickupStationId", "label", "isDefault"), json.keySet())
        api.setDefault(1011, session); assertEquals("/api/v1/pickup-stations/my-stations/1011/default", requests.last().url.encodedPath)
        api.remove(1011, session); assertEquals("DELETE", requests.last().method); assertEquals("/api/v1/pickup-stations/my-stations/1011", requests.last().url.encodedPath)
        retrofit.create(BuyerCheckoutApi::class.java).quote(BuyerDeliveryQuoteRequest.pickup(11), session)
        val quoteJson = com.google.gson.JsonParser.parseString(Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("deliveryMethod", "pickupStationId"), quoteJson.keySet())
        assertEquals("PICKUP_STATION", quoteJson["deliveryMethod"].asString)
    }
}
