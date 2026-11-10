package com.example.farmapp.checkout

import com.example.farmapp.buyer.*
import com.example.farmapp.buyers.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalJson
import com.example.farmapp.supplier.supplierAuth
import com.example.farmapp.tracking.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException

/** In-process transport only: these tests never connect to a backend. */
class DeliveryRepositoryTest {
    private class Fixture {
        private val delegate = signedInStore(supplierAuth().copy(role = "BUYER"))
        val session = MutableStateFlow(delegate.session.value)
        val sessions = object : SessionStore by delegate { override val session = this@Fixture.session }
        val expected = session.value
        val requests = mutableListOf<Request>()
        var data = "null"
        var status = 200
        var failure = false
        var beforeResponse: () -> Unit = {}
        var bodyFor: ((Request) -> String)? = null
        val retrofit = Retrofit.Builder().baseUrl("https://delivery.example.test/api/v1/")
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                requests += chain.request()
                beforeResponse()
                if (failure) throw SocketTimeoutException("Synthetic interrupted write")
                val body = bodyFor?.invoke(chain.request()) ?: """{"success":true,"data":$data}"""
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()).build()
        val addressApi = retrofit.create(BuyerAddressApi::class.java)
        val pickupApi = retrofit.create(BuyerPickupApi::class.java)
        val addresses = BuyerDeliveryAddressRepository(addressApi, sessions, expected)
        val managed = BuyerManagedAddressRepository(retrofit.create(BrianAddressApi::class.java), sessions, expected)
        val pickup = BuyerPickupRepository(pickupApi, sessions, expected)
        val nearby = BuyerNearbyPickupRepository(retrofit.create(BuyerPickupLocationApi::class.java), sessions, expected)
        val tracking = BuyerDeliveryTrackingRepository(retrofit.create(DeliveryTrackingApi::class.java), sessions, expected, 105, 42)
        val job = DeliveryJobDto(10, 105, 55, "in_transit")
        val input = buyerAddressRequest("Home", "Synthetic address")
        val address = """{"id":8,"userId":42,"addressText":"Synthetic address"}"""
        val station = """{"id":11,"name":"Synthetic station","address":"Synthetic place","region":"North","isAvailable":true}"""
        val saved = """{"id":1011,"userId":42,"pickupStationId":11,"pickupStationAvailable":true,"isDefault":true}"""
        val jobJson = """{"id":10,"orderId":105,"driverId":55,"status":"in_transit"}"""
        val fix = """{"tripId":10,"orderId":105,"driverId":55,"latitude":-1.2,"longitude":36.8}"""
        fun json(index: Int) = Buffer().also { requests[index].body!!.writeTo(it) }.readUtf8()
        fun operations(): List<suspend () -> Unit> = listOf(
            { addresses.list(); Unit }, { addresses.create(input); Unit }, { managed.list(); Unit },
            { managed.create(input); Unit }, { managed.update(8, input); Unit }, { managed.remove(8); Unit },
            { pickup.availability(); Unit }, { pickup.available(); Unit }, { pickup.region("North"); Unit },
            { pickup.detail(11); Unit }, { pickup.saved(); Unit }, { pickup.save(BuyerSavePickupRequest(11)); Unit },
            { pickup.remove(1011); Unit }, { pickup.setDefault(1011); Unit },
            { nearby.nearest(-1.2, 36.8); Unit }, { tracking.jobs(105); Unit },
            { tracking.events(job); Unit }, { tracking.location(job); Unit }
        )
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Invalid data or session accepted") }
        catch (_: IllegalStateException) { }
        catch (_: IllegalArgumentException) { }
    }

    private suspend fun completed(busy: () -> Boolean) = withTimeout(5_000) {
        while (busy()) delay(5)
    }

    @Test fun addressAdaptersPreserveVerifiedPathsBodiesTagsAndOptionalCoordinates() = runBlocking {
        val f = Fixture(); f.data = "[${f.address}]"; f.addresses.list(); f.managed.list()
        f.data = f.address; f.addresses.create(f.input); f.managed.create(f.input); f.managed.update(8, f.input)
        f.data = "null"; f.managed.remove(8)
        assertEquals(listOf("GET", "GET", "POST", "POST", "PUT", "DELETE"), f.requests.map { it.method })
        assertEquals(listOf("users/addresses", "users/addresses", "users/addresses", "users/addresses", "users/addresses/8", "users/addresses/8"), f.requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        assertEquals("""{"addressText":"Synthetic address","label":"Home"}""", f.json(2))
        f.requests.forEach { assertEquals(f.expected, it.tag(SessionSnapshot::class.java)) }
        assertNull(f.addresses.create(f.input).body()!!.data) // missing data is not fabricated
    }

    @Test fun stationSelectionAndAssociationManagementKeepDistinctWireIdentities() = runBlocking {
        val f = Fixture(); f.data = "true"; f.pickup.availability()
        f.data = "[${f.station}]"; f.pickup.available(); f.pickup.region("North")
        f.data = f.station; assertEquals(11L, f.pickup.detail(11).body()!!.data!!.id)
        f.data = "[${f.saved}]"; assertEquals(11L, f.pickup.saved().body()!!.data!!.single().pickupStationId)
        f.data = f.saved; assertEquals(1011L, f.pickup.save(BuyerSavePickupRequest(11, "Home", true)).body()!!.data!!.id)
        f.pickup.setDefault(1011); f.data = "null"; f.pickup.remove(1011)
        assertEquals(listOf("GET", "GET", "GET", "GET", "GET", "POST", "PUT", "DELETE"), f.requests.map { it.method })
        assertEquals(listOf("pickup-stations/availability", "pickup-stations/available", "pickup-stations/available/region/North", "pickup-stations/11", "pickup-stations/my-stations", "pickup-stations/my-stations", "pickup-stations/my-stations/1011/default", "pickup-stations/my-stations/1011"), f.requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        assertEquals("""{"pickupStationId":11,"label":"Home","isDefault":true}""", f.json(5))
        f.requests.forEach { assertEquals(f.expected, it.tag(SessionSnapshot::class.java)) }
    }

    @Test fun trackingAndNearestUseOnlyExistingPathsAndParameters() = runBlocking {
        val f = Fixture(); f.data = "[${f.jobJson}]"; f.tracking.jobs(105)
        f.data = """[{"logisticsJobId":10,"status":"in_transit"}]"""; f.tracking.events(f.job)
        f.data = f.fix; assertEquals(-1.2, f.tracking.location(f.job).body()!!.data!!.latitude!!, 0.0)
        f.data = "[]"; assertTrue(f.nearby.nearest(-1.2, 36.8).body()!!.data!!.isEmpty())
        assertEquals(listOf("logistics/jobs/order/105", "logistics/jobs/10/tracking", "tracking/10/location", "pickup-stations/nearest"), f.requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        assertEquals("lat=-1.2&lng=36.8&radiusKm=10.0&limit=20", f.requests.last().url.encodedQuery)
    }

    @Test fun foreignAddressOwnersAndWrongUpdatedIdsAreRejected() = runBlocking {
        val f = Fixture(); f.data = "[${f.address.replace("42", "99")}]"
        rejects { f.addresses.list() }; rejects { f.managed.list() }
        f.data = f.address.replace("42", "99"); rejects { f.addresses.create(f.input) }; rejects { f.managed.create(f.input) }
        f.data = f.address.replace("\"id\":8", "\"id\":9"); rejects { f.managed.update(8, f.input) }
    }

    @Test fun stationMismatchesForeignSavedRowsAndDuplicateAssociationsAreRejected() = runBlocking {
        val f = Fixture(); f.data = f.station.replace("\"id\":11", "\"id\":12"); rejects { f.pickup.detail(11) }
        f.data = f.saved.replace("\"pickupStationId\":11", "\"pickupStationId\":12"); rejects { f.pickup.save(BuyerSavePickupRequest(11)) }
        f.data = f.saved.replace("\"id\":1011", "\"id\":1012"); rejects { f.pickup.setDefault(1011) }
        f.data = "[${f.saved.replace("42", "99")}]"; rejects { f.pickup.saved() }
        f.data = "[${f.saved},${f.saved}]"; rejects { f.pickup.saved() }
        f.data = "[${f.station}]"; rejects { f.pickup.region("South") }
    }

    @Test fun staleSessionsAndUnverifiedRolesCannotCallAnyDeliveryBoundary() = runBlocking {
        for (kind in listOf("account", "origin", "generation", "logout", "demo", "contract", "signedOut", "FARMER", "SUPPLIER", "INSPECTOR")) {
            val f = Fixture(); val auth = f.expected.auth!!
            f.session.value = when (kind) {
                "account" -> f.expected.copy(auth = auth.copy(userId = "99"))
                "origin" -> f.expected.copy(auth = auth.copy(sessionOrigin = "https://other.example.test/"))
                "generation" -> f.expected.copy(generation = f.expected.generation + 1)
                "logout" -> f.expected.copy(loggingOut = true)
                "demo" -> f.expected.copy(isDemo = true)
                "contract" -> f.expected.copy(auth = auth.copy(backendContract = "legacy"))
                "signedOut" -> f.expected.copy(auth = null)
                else -> f.expected.copy(auth = auth.copy(role = kind))
            }
            f.operations().forEach { rejects(it) }
            assertTrue("Unexpected request for $kind", f.requests.isEmpty())
        }
    }

    @Test fun sessionChangesDuringRequestsRejectAllLateResponsesWithoutRetries() = runBlocking {
        for (index in Fixture().operations().indices) {
            val f = Fixture(); f.beforeResponse = { f.session.value = f.expected.copy(loggingOut = true) }
            rejects(f.operations()[index]); assertEquals(1, f.requests.size)
        }
    }

    @Test fun invalidInputsNeverReachTheTransport() = runBlocking {
        val f = Fixture()
        listOf<suspend () -> Unit>(
            { f.addresses.create(f.input.copy(latitude = 91.0, longitude = 1.0)); Unit },
            { f.managed.update(0, f.input); Unit }, { f.managed.remove(-1); Unit },
            { f.pickup.detail(0); Unit }, { f.pickup.save(BuyerSavePickupRequest(0)); Unit },
            { f.pickup.setDefault(0); Unit }, { f.pickup.remove(0); Unit },
            { f.nearby.nearest(Double.NaN, 36.8); Unit }, { f.nearby.nearest(-1.2, 181.0); Unit },
            { f.nearby.nearest(-1.2, 36.8, 0.0); Unit }, { f.nearby.nearest(-1.2, 36.8, limit = 0); Unit }
        ).forEach { rejects(it) }
        assertTrue(f.requests.isEmpty())
    }

    @Test fun trackingRequiresExactOrderJobAndDriverAssociations() = runBlocking {
        val f = Fixture(); rejects { f.tracking.jobs(106) }; rejects { f.tracking.events(f.job.copy(orderId = 106)) }
        assertTrue(f.requests.isEmpty())
        f.data = "[${f.jobJson.replace("105", "106")}]"; rejects { f.tracking.jobs(105) }
        f.data = "[${f.jobJson},${f.jobJson}]"; rejects { f.tracking.jobs(105) }
        f.data = """[{"logisticsJobId":11,"status":"in_transit"}]"""; rejects { f.tracking.events(f.job) }
        for ((field, old, new) in listOf(Triple("tripId", "10", "11"), Triple("orderId", "105", "106"), Triple("driverId", "55", "56"))) {
            f.data = f.fix.replace("\"$field\":$old", "\"$field\":$new"); rejects { f.tracking.location(f.job) }
        }
        f.data = f.fix; rejects { f.tracking.location(f.job.copy(driverId = null)) }
        val foreign = BuyerDeliveryTrackingRepository(f.retrofit.create(DeliveryTrackingApi::class.java), f.sessions, f.expected, 105, 99)
        rejects { foreign.jobs(105) }
    }

    @Test fun invalidCoordinatesAndMissingFixesNeverBecomeMapPositions() = runBlocking {
        val f = Fixture()
        for ((field, value) in listOf("latitude" to "91", "longitude" to "181", "latitude" to "null", "longitude" to "null")) {
            f.data = f.fix.replace(Regex("\"$field\":[-0-9.]+"), "\"$field\":$value")
            rejects { f.tracking.location(f.job) }
        }
        f.data = "[${f.station.dropLast(1)},\"latitude\":91}]"; rejects { f.pickup.available() }
        f.data = "[${f.station.dropLast(1)},\"distanceKm\":-1}]"; rejects { f.nearby.nearest(-1.2, 36.8) }
    }

    @Test fun unavailableLocationAndHttpFailuresReachExistingPresentationUnchanged() = runBlocking {
        val f = Fixture(); f.status = 404; f.data = "null"
        val response = f.tracking.location(f.job)
        assertEquals(404, response.code()); response.errorBody()!!.close(); assertEquals(1, f.requests.size)
        f.status = 503; assertEquals(503, f.tracking.location(f.job).also { it.errorBody()?.close() }.code())
        f.status = 200; f.data = "null"; assertNull(f.tracking.location(f.job).body()!!.data)
        f.data = "[]"; assertTrue(f.tracking.jobs(105).body()!!.data!!.isEmpty())
    }

    @Test fun interruptedWritesAreNeverRetriedByRepositoryAdapters() = runBlocking {
        val f = Fixture(); f.failure = true
        val writes: List<suspend () -> Unit> = listOf(
            { f.addresses.create(f.input); Unit }, { f.managed.update(8, f.input); Unit },
            { f.managed.remove(8); Unit }, { f.pickup.save(BuyerSavePickupRequest(11)); Unit },
            { f.pickup.setDefault(1011); Unit }, { f.pickup.remove(1011); Unit }
        )
        writes.forEach { operation -> try { operation(); fail("Timeout expected") } catch (_: SocketTimeoutException) { } }
        assertEquals(writes.size, f.requests.size)
    }

    @Test fun addressPendingMarkerSurvivesInterruptionAndBlocksSubmissionAfterRestart() = runBlocking {
        val f = Fixture(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = object : BuyerDeliveryStore {
            var pending = false
            override fun selectedAddress(): Long? = null
            override fun selectAddress(id: Long?) { fail("No address was confirmed") }
            override fun addressSavePending() = pending
            override fun markAddressSave(pending: Boolean) { this.pending = pending }
        }
        try {
            f.failure = true
            val original = BuyerAddressController(f.addressApi, f.sessions, f.expected, scope, store)
            original.create(f.input); completed { original.state.value.saving }
            assertTrue(store.pending); assertEquals(1, f.requests.size)
            f.failure = false; f.data = "[]"
            val restarted = BuyerAddressController(f.addressApi, f.sessions, f.expected, scope, store)
            // Refresh may clear a stale selected ID, but must retain the uncertain-save marker.
            restarted.create(f.input)
            completed { restarted.state.value.saving }
            assertTrue(store.pending); assertEquals(1, f.requests.size)
        } finally { scope.cancel() }
    }

    @Test fun pickupWriteReservationRecoversReadOnlyUsingPlatformAndAssociationIds() = runBlocking {
        val f = Fixture(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = object : BuyerPickupStore {
            var selected: Long? = null; var pending: BuyerPickupMutation? = null
            override fun selectedPickup() = selected
            override fun selectPickup(id: Long?) { selected = id }
            override fun pendingPickup() = pending
            override fun pendingPickup(value: BuyerPickupMutation?) { pending = value }
        }
        var committed = false
        f.bodyFor = { request ->
            val data = when {
                request.method == "POST" -> { assertEquals(11L, store.pending!!.platformId); committed = true; throw SocketTimeoutException("Synthetic committed response lost") }
                request.url.encodedPath.endsWith("availability") -> "true"
                request.url.encodedPath.endsWith("available") -> "[${f.station}]"
                request.url.encodedPath.endsWith("my-stations") -> if (committed) "[${f.saved}]" else "[]"
                else -> f.station
            }
            """{"success":true,"data":$data}"""
        }
        try {
            val original = BuyerPickupController(f.pickupApi, f.sessions, f.expected, store, scope)
            original.refresh(); completed { original.state.value.busy }
            original.save(11, "", true); completed { original.state.value.busy }
            assertTrue(original.state.value.uncertain); assertNotNull(store.pending)
            val restarted = BuyerPickupController(f.pickupApi, f.sessions, f.expected, store, scope)
            restarted.refresh()
            completed { restarted.state.value.busy }
            assertNull(store.pending); assertEquals(11L, store.selected); assertEquals(1011L, restarted.state.value.saved.single().id)
            assertEquals(1, f.requests.count { it.method == "POST" })
        } finally { scope.cancel() }
    }
}
