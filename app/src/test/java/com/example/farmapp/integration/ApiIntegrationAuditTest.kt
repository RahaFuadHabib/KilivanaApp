package com.example.farmapp.integration

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.marketplace.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.orders.*
import com.example.farmapp.profile.*
import com.example.farmapp.supplier.*
import com.example.farmapp.tracking.*
import com.example.farmapp.ui.navigation.*
import com.example.farmapp.ui.screens.auth.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.util.UUID

class ApiIntegrationAuditTest {
    private fun store(role: String = "FARMER") = signedInStore(supplierAuth().copy(role = role))
    private fun product(type: String = "supplier", status: String = "active") = supplyDto().copy(sellerType = type, status = status)
    private fun <T> rejects(block: () -> T) {
        try { block(); fail("Expected validation failure") } catch (e: IllegalStateException) { /* expected */ }
    }
    private fun retrofit(requests: MutableList<Request>, body: String = "{\"success\":true}") = Retrofit.Builder()
        .baseUrl(SUPPLIER_TEST_ORIGIN).addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()).build()

    @Test fun roleMarketplacesNeverMixSellerTypes() {
        assertEquals("supplier", marketplaceSeller("FARMER")); assertEquals("farmer", marketplaceSeller("BUYER"))
        rejects { marketplaceSeller("SUPPLIER") }
        rejects { marketplaceProduct(product("farmer"), "supplier") }
        assertEquals("AGRI_INPUTS", marketplaceProduct(product(), "supplier")!!.sector)
    }
    @Test fun unpublishedAndReservedOutProductsAreNotShown() {
        for (status in listOf("pending", "rejected", "suspended", "unlisted", "archived", "deleted"))
            assertNull(marketplaceProduct(product(status = status), "supplier"))
        assertNull(marketplaceProduct(product().copy(stockQty = 3, reservedQty = 3), "supplier"))
        assertEquals(BigDecimal.TEN, marketplaceProduct(product(), "supplier")!!.availableQuantity)
    }
    @Test fun searchUsesVerifiedServerFiltersAndPageParameters() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = retrofit(requests).create(MarketplaceApi::class.java)
        for ((role, seller) in listOf("FARMER" to "SUPPLIER", "BUYER" to "FARMER")) {
            api.search(seller, "seed", 2, 3, session = store(role).session.value)
            val url = requests.last().url
            assertEquals("/api/v1/products/search", url.encodedPath)
            assertEquals(seller, url.queryParameter("sellerType")); assertEquals("seed", url.queryParameter("name"))
            assertEquals("2", url.queryParameter("categoryId")); assertEquals("3", url.queryParameter("page")); assertEquals("20", url.queryParameter("size"))
        }
    }
    private open inner class MarketApi : MarketplaceApi {
        var requestedType: String? = null
        var rows = listOf(product())
        var number = 0
        var last: Boolean? = false
        var after: () -> Unit = {}
        override suspend fun search(type: String, name: String?, category: Long?, page: Int, size: Int, sort: String, session: SessionSnapshot): Response<CanonicalApiResponse<ProductPage>> {
            requestedType = type
            after(); return Response.success(CanonicalApiResponse(true, data = ProductPage(rows, number, last)))
        }
        override suspend fun categories(type: String, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = emptyList<CanonicalCategoryDto>()))
        override suspend fun detail(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = rows.single()))
    }
    @Test fun marketplaceRepositoryUsesDatabaseEnumCaseForEachPurchasingRole() = runBlocking {
        for ((role, seller) in listOf("FARMER" to "SUPPLIER", "BUYER" to "FARMER")) {
            val store = store(role)
            val api = MarketApi().apply { rows = listOf(product(seller.lowercase())); last = true }
            val (rows, last) = MarketplaceRepository(api, store).page(store.session.value, "", null, 0)
            assertEquals(seller, api.requestedType)
            assertEquals(1, rows.size)
            assertTrue(last)
        }
    }
    @Test fun marketplaceRejectsWrongPageAndDuplicateRows() = runBlocking {
        val store = store(); val api = MarketApi(); val repository = MarketplaceRepository(api, store)
        api.number = 1
        rejects { runBlocking { repository.page(store.session.value, "", null, 0) } }
        api.number = 0; api.rows = listOf(product(), product())
        rejects { runBlocking { repository.page(store.session.value, "", null, 0) } }
    }
    @Test fun marketplaceRejectsLateReplyAfterLogout() = runBlocking {
        val store = store(); val expected = store.session.value; val api = MarketApi()
        api.after = { store.beginLogout() }
        rejects { runBlocking { MarketplaceRepository(api, store).page(expected, "", null, 0) } }
    }
    @Test fun accountUpdateCannotSendUsernameRoleOrApproval() = runBlocking {
        val requests = mutableListOf<Request>()
        retrofit(requests).create(ProfileEditorApi::class.java).save(AccountEditRequest("Person", "person@example.test", "+254712345678", "Nakuru"), store().session.value)
        val request = requests.single()
        assertEquals("PUT", request.method); assertEquals("/api/v1/users/me", request.url.encodedPath)
        val json = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("name", "email", "phone", "region"), json.keySet())
        assertNotNull(validateAccountEdit("", "+254712345678")); assertNotNull(validateAccountEdit("Person", "abc"))
        assertNull(validateAccountEdit("Person", "+254712345678"))
    }
    @Test fun otpUsesChallengeIdAndCodeButNoPasswordOrTokens() = runBlocking {
        val requests = mutableListOf<Request>(); val api = retrofit(requests).create(EmailVerificationApi::class.java)
        val id = UUID.fromString("00000000-0000-4000-8000-000000000001")
        val request = EmailOtpVerifyRequest(id, "person@example.test", "123456")
        api.request(EmailOtpRequest(request.email)); val response = api.verify(request)
        response.body()!!.requireSuccess() // Void response needs no data.
        assertEquals("/api/v1/auth/email-verification/request", requests[0].url.encodedPath)
        assertEquals("/api/v1/auth/email-verification/verify", requests[1].url.encodedPath)
        val body = JsonParser.parseString(Buffer().also { requests[1].body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("challengeId", "email", "code"), body.keySet())
        assertFalse(request.toString().contains("123456")); assertFalse(request.toString().contains(request.email))
        assertTrue(validEmailOtp("123456")); for (value in listOf("12345", "1234567", "abcdef", "１２３４５６")) assertFalse(validEmailOtp(value))
    }
    @Test fun galleryUsesPutPrimaryAndJsonIdArrayAndDelete() = runBlocking {
        val requests = mutableListOf<Request>(); val api = retrofit(requests).create(SupplierGalleryApi::class.java); val session = store("SUPPLIER").session.value
        api.primary(7, 9, session); api.reorder(7, listOf(10, 9), session); api.delete(7, 9, session)
        assertEquals(listOf("PUT", "PUT", "DELETE"), requests.map { it.method })
        assertEquals("/api/v1/products/7/images/9/primary", requests[0].url.encodedPath)
        assertEquals("[10,9]", Buffer().also { requests[1].body!!.writeTo(it) }.readUtf8())
    }
    private fun order() = BuyerOrderDto(7, 42, "delivered", BigDecimal("125.50"), "held", items = emptyList())
    @Test fun orderValidationRejectsOtherBuyersAndUnknownStates() {
        assertEquals(7L, validateBuyerOrder(order(), 42).id)
        rejects { validateBuyerOrder(order(), 43) }
        rejects { validateBuyerOrder(order().copy(status = "SHIPPED"), 42) }
        rejects { validateBuyerOrder(order().copy(paymentStatus = "accepted"), 42) }
        assertFalse(paymentStatuses.contains("cancelled")) // Do not invent a status missing from the contract.
    }
    @Test fun receiptAndStatusReadsUseVerifiedMethodsWithoutStatusMutation() = runBlocking {
        val requests = mutableListOf<Request>(); val api = retrofit(requests).create(BuyerOrdersApi::class.java); val session = store("BUYER").session.value
        api.receipt(7, session); api.payment(9, session); api.payments(7, session)
        assertEquals(listOf("POST", "GET", "GET"), requests.map { it.method })
        assertEquals("/api/v1/orders/7/confirm-receipt", requests[0].url.encodedPath)
        assertEquals("/api/v1/payments/9/status", requests[1].url.encodedPath)
    }
    @Test fun driverLocationMustMatchJobOrderAndAssignedDriver() {
        val job = DeliveryJobDto(10, 7, 55, "in_transit")
        val fix = DeliveryLocationDto(10, 7, 55, -1.2, 36.8)
        assertEquals(fix, validateDeliveryLocation(fix, job))
        rejects { validateDeliveryLocation(fix.copy(orderId = 8), job) }
        rejects { validateDeliveryLocation(fix.copy(tripId = 11), job) }
        rejects { validateDeliveryLocation(fix.copy(driverId = 56), job) }
        rejects { validateDeliveryLocation(fix.copy(latitude = Double.NaN), job) }
        rejects { validateDeliveryLocation(fix.copy(longitude = 181.0), job) }
    }
    @Test fun driverCannotSkipMilestonesOrCompleteWithoutOtp() {
        assertEquals("en_route_to_pickup", nextDriverStatus("accepted"))
        assertEquals("arrived_at_pickup", nextDriverStatus("en_route_to_pickup"))
        assertEquals("picked_up", nextDriverStatus("arrived_at_pickup"))
        assertEquals("in_transit", nextDriverStatus("picked_up"))
        assertEquals("arrived_at_destination", nextDriverStatus("in_transit"))
        assertNull(nextDriverStatus("arrived_at_destination")); assertNull(nextDriverStatus("delivered"))
        assertFalse("assigned" in reportingStates); assertFalse("delivered" in reportingStates)
    }
    @Test fun driverActionsAreQueryParametersNotInventedPatchBodies() = runBlocking {
        val requests = mutableListOf<Request>(); val api = retrofit(requests).create(DriverApi::class.java); val session = store("DRIVER").session.value
        api.accept(10, 55, session); api.status(10, "picked_up", session); api.otp(10, "123456", session)
        assertEquals(listOf("POST", "PUT", "POST"), requests.map { it.method })
        assertEquals("55", requests[0].url.queryParameter("driverId")); assertEquals("picked_up", requests[1].url.queryParameter("status"))
        assertEquals("123456", requests[2].url.queryParameter("otp"))
    }
    @Test fun canonicalDriverGetsDedicatedNavigationButLegacyDriverStaysUnsupported() {
        assertEquals(AuthDestination.DRIVER, authDestination(supplierAuth().copy(role = "DRIVER")))
        assertEquals(AuthDestination.UNSUPPORTED, authDestination(testAuth(role = "DRIVER")))
    }
    @Test fun profileImageUploadUsesUserIdAndDistinctListResponse() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = retrofit(requests, "{\"success\":true,\"data\":[]}").create(SupplierProfilePhotosApi::class.java)
        val session = store("SUPPLIER").session.value
        val photo = supplyPhoto()
        val part = MultipartBody.Part.createFormData("image", photo.filename,
            photo.bytes.toRequestBody(photo.mime.toMediaType()))
        assertTrue(api.upload(42, part, true, session).body()!!.requireData().isEmpty())
        assertEquals("/api/v1/profiles/suppliers/42/images", requests.single().url.encodedPath)
        assertEquals("true", requests.single().url.queryParameter("isPrimary"))
        assertEquals("POST", requests.single().method)
        assertTrue(Buffer().also { requests.single().body!!.writeTo(it) }.readUtf8().contains("name=\"image\""))
    }
    @Test fun passwordRecoveryRequestsRealEmailInstructionsWithoutFakeOtp() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = retrofit(requests, "{\"success\":true,\"data\":\"Instructions requested\"}").create(PasswordRecoveryApi::class.java)
        api.request("person@example.test").body()!!.requireSuccess()
        assertEquals("POST", requests.single().method)
        assertEquals("/api/v1/auth/forgot-password", requests.single().url.encodedPath)
        assertEquals("person@example.test", requests.single().url.queryParameter("email"))
        assertNull(requests.single().url.queryParameter("otp"))
    }
    private open inner class DriverFixture : DriverApi {
        var row = DeliveryJobDto(10, 7, 42, "assigned")
        var writes = 0
        var requestedStatus: String? = null
        override suspend fun jobs(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = listOf(row)))
        override suspend fun job(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = row))
        override suspend fun events(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = emptyList<DeliveryEventDto>()))
        override suspend fun accept(id: Long, driverId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>> {
            writes++; row = row.copy(status = "accepted"); return Response.success(CanonicalApiResponse(true, data = row))
        }
        override suspend fun status(id: Long, status: String, session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>> {
            writes++; requestedStatus = status; row = row.copy(status = status); return Response.success(CanonicalApiResponse(true, data = row))
        }
        override suspend fun otp(id: Long, code: String, session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>> {
            writes++; row = row.copy(status = "delivered"); return Response.success(CanonicalApiResponse(true, data = row))
        }
        override suspend fun report(id: Long, location: DriverLocationRequest, session: SessionSnapshot): Response<CanonicalApiResponse<DriverLocationAck>> = error("No GPS writes in JVM fixtures")
    }
    @Test fun driverAcceptsOnlyAssignedOwnedJobAndRechecksBeforeWriting() = runBlocking {
        val store = store("DRIVER"); val api = DriverFixture(); val repository = DriverRepository(api, store)
        val before = api.row
        assertEquals("accepted", repository.advance(store.session.value, before, null).status)
        rejects { runBlocking { repository.advance(store.session.value, before, null) } }
        assertEquals(1, api.writes)
        api.row = api.row.copy(driverId = 99)
        rejects { runBlocking { repository.advance(store.session.value, api.row, null) } }
        assertEquals(1, api.writes)
    }
    @Test fun driverHandoverRequiresDestinationAndSixDigits() = runBlocking {
        val store = store("DRIVER"); val api = DriverFixture(); val repository = DriverRepository(api, store)
        rejects { runBlocking { repository.advance(store.session.value, api.row, "123456") } }
        api.row = api.row.copy(status = "arrived_at_destination")
        rejects { runBlocking { repository.advance(store.session.value, api.row, "1234") } }
        assertEquals(0, api.writes)
        assertEquals("delivered", repository.advance(store.session.value, api.row, "123456").status)
        assertEquals(1, api.writes)
    }
    @Test fun wrongDriverAndLoggedOutSessionsNeverLoadAssignedJobs() = runBlocking {
        val store = store("DRIVER"); val api = DriverFixture(); val repository = DriverRepository(api, store)
        api.row = api.row.copy(driverId = 99)
        rejects { runBlocking { repository.jobs(store.session.value) } }
        val expected = store.session.value; store.beginLogout()
        rejects { runBlocking { repository.jobs(expected) } }
    }
    private inner class GalleryFixture : SupplierGalleryApi {
        var writes = 0
        var ids: List<Long>? = null
        override suspend fun delete(productId: Long, imageId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<Unit>> {
            writes++; return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun primary(productId: Long, imageId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<Unit>> {
            writes++; return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun reorder(productId: Long, imageIds: List<Long>, session: SessionSnapshot): Response<CanonicalApiResponse<Unit>> {
            writes++; ids = imageIds; return Response.success(CanonicalApiResponse(true))
        }
    }
    @Test fun galleryReorderUsesFreshCompleteGalleryAndBlocksUnknownImage() = runBlocking {
        val store = supplierStore(); val api = StubSupplierApi(); val gallery = GalleryFixture()
        api.imageValues = listOf(CanonicalProductImageDto(9, "/api/v1/images/9", "9", sortOrder = 0), CanonicalProductImageDto(10, "/api/v1/images/10", "10", sortOrder = 1))
        val repository = SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN, gallery)
        repository.gallery(store.session.value, 7, GalleryAction(10, GalleryChange.MOVE_UP))
        assertEquals(listOf(10L, 9L), gallery.ids)
        try { repository.gallery(store.session.value, 7, GalleryAction(99, GalleryChange.DELETE)); fail() } catch (_: SupplierFailure) { }
        assertEquals(1, gallery.writes)
        api.current = api.current.copy(status = "active")
        try { repository.gallery(store.session.value, 7, GalleryAction(9, GalleryChange.PRIMARY)); fail() } catch (_: SupplierFailure) { }
        assertEquals(1, gallery.writes)
    }
}
