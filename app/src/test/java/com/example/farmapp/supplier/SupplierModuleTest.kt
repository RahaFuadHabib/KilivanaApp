package com.example.farmapp.supplier

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.payments.VerifiedPaymentStatus
import com.example.farmapp.repository.AuthFailure
import com.example.farmapp.repository.AuthRepository
import com.example.farmapp.ui.navigation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

class SupplierModuleTest {
    private suspend fun rejects(block: suspend () -> Unit): SupplierFailure {
        try { block() } catch (e: SupplierFailure) { return e }
        throw AssertionError("Expected Supplier rejection")
    }
    private fun repository(api: SupplierApi, store: SessionStore) = SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN)

    @Test fun routingUsesSupplierRoleAndLeavesOtherRolesUnchanged() {
        assertEquals(AuthDestination.SUPPLIER, authDestination(supplierAuth()))
        assertEquals(AuthDestination.SUPPLIER, authDestination(supplierAuth().copy(verificationStatus = "PENDING")))
        assertEquals(AuthDestination.FARMER, authDestination(supplierAuth().copy(role = "FARMER")))
        assertEquals(AuthDestination.INSPECTOR, authDestination(supplierAuth().copy(role = "INSPECTOR")))
        assertEquals(AuthDestination.BUYER, authDestination(supplierAuth().copy(role = "BUYER")))
    }
    @Test fun provisionedSupplierCanLoginAndInspectorCannotRegister() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CanonicalBackendApi::class.java)) { _, method, _ ->
            calls += method.name
            check(method.name == "login")
            Response.success(CanonicalApiResponse(true, data = CanonicalAuthTokenDto("fixture-access", "fixture-refresh", "Bearer", 3600, supplierUser())))
        } as CanonicalBackendApi
        val service = CanonicalAuthService(transport, SUPPLIER_TEST_ORIGIN)
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        AuthRepository(service, store).login("supplier@example.test", "synthetic-password")
        assertEquals("SUPPLIER", store.session.value.auth!!.role)
        try { service.signup(SignupRequest("inspector@example.test", "synthetic-password", "Synthetic inspector", "+254700000000", "INSPECTOR", "synthetic_inspector", "Synthetic area", employeeCode = "Synthetic code")); fail() }
        catch (_: AuthFailure) { }
        assertEquals(listOf("login"), calls)
    }
    @Test fun profileUsesAuthenticatedSupplierAndMissingBusinessIsExplicit() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore(); val repo = repository(api, store)
        val profile = repo.profile(store.session.value)
        assertEquals("Synthetic input business", profile.business!!.businessName)
        assertEquals("ACTIVE", profile.account.accountStatus)
        api.business = null
        assertNull(repo.profile(store.session.value).business)
    }
    @Test fun wrongProfileOrAccountIdentityIsRejected() = runBlocking {
        val store = supplierStore(); val api = StubSupplierApi(); val repo = repository(api, store)
        api.business = SupplierProfileDto(1, 99)
        rejects { repo.profile(store.session.value) }
        api.account = supplierUser(99)
        rejects { repo.profile(store.session.value) }
        Unit
    }
    @Test fun supplierListRejectsMixedOwnersAndFarmerProducts() = runBlocking {
        val store = supplierStore(); val api = StubSupplierApi(); val repo = repository(api, store)
        assertEquals(42L, repo.listings(store.session.value).let { api.ownerRequested })
        api.values = listOf(supplyDto(), supplyDto(8, 99))
        rejects { repo.listings(store.session.value) }
        api.values = listOf(supplyDto().copy(sellerType = "farmer"))
        rejects { repo.listings(store.session.value) }
        Unit
    }
    @Test fun wrongRoleOriginLogoutAndOldGenerationAreRejectedBeforeRequests() = runBlocking {
        val store = supplierStore(); val api = StubSupplierApi(); val before = store.session.value
        rejects { SupplierRepository(api, store, "https://other.test/api/v1/").listings(before) }
        store.clear(store.beginLogout()!!)
        rejects { repository(api, store).listings(before) }
        store.establish(store.session.value, supplierAuth().copy(role = "FARMER"), false)
        rejects { repository(api, store).listings(store.session.value) }
        assertNull(api.ownerRequested)
    }
    @Test fun categoryTypeActivityAndDuplicatesAreValidated() = runBlocking {
        val store = supplierStore(); val api = StubSupplierApi(); val repo = repository(api, store)
        assertEquals(1, repo.categories(store.session.value).size)
        api.categoryValues = listOf(CanonicalCategoryDto(1, "Inactive", "supplier", false))
        assertTrue(repo.categories(store.session.value).isEmpty())
        api.categoryValues = listOf(CanonicalCategoryDto(1, "Farmer", "farmer", true))
        rejects { repo.categories(store.session.value) }
        Unit
    }
    @Test fun listingFormValidatesRequiredFieldsQuantitiesMoneyAndCategories() = runBlocking {
        val categories = StubSupplierApi().categoryValues
        val invalid = listOf(supplyDraft().copy(name = ""), supplyDraft().copy(description = " "), supplyDraft().copy(unit = ""),
            supplyDraft().copy(price = "0"), supplyDraft().copy(price = "1.005"), supplyDraft().copy(available = "1.5"),
            supplyDraft().copy(available = "-1"), supplyDraft().copy(minimum = "0"), supplyDraft().copy(categoryId = 99))
        for (draft in invalid) assertTrue(rejects { draft.request(categories) }.rejected)
        assertTrue(rejects { supplyDraft().copy(available = Int.MAX_VALUE.toString()).request(categories, 1) }.rejected)
        assertEquals(12, supplyDraft().request(categories, 2).stockQty)
    }
    @Test fun createUsesSupplierCategoryAndNeverOverridesModeration() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore()
        val product = repository(api, store).create(store.session.value, supplyDraft())
        assertEquals(CanonicalProductStatus.PENDING_APPROVAL, product.status)
        assertEquals(2L, api.request!!.categoryId)
        assertEquals(10, api.request!!.stockQty)
        assertEquals(1, api.writes)
        api.current = supplyDto().copy(status = "active")
        assertFalse(rejects { repository(api, store).create(store.session.value, supplyDraft()) }.rejected)
    }
    @Test fun editsPreserveFreshReservationsAndBlockPublishedContentChanges() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore(); val repo = repository(api, store)
        api.current = supplyDto().copy(reservedQty = 3)
        val edited = repo.update(store.session.value, 7, supplyDraft().copy(available = "5"))
        assertEquals(8, api.request!!.stockQty)
        assertEquals(5, edited.available)
        for (status in listOf("active", "suspended", "rejected")) {
            api.current = supplyDto().copy(status = status)
            assertTrue(rejects { repo.update(store.session.value, 7, supplyDraft()) }.rejected)
        }
        assertEquals(1, api.writes)
    }
    @Test fun photoValidationRejectsWrongTypeSizeNameAndSignature() = runBlocking {
        validateSupplierPhoto(supplyPhoto())
        for (photo in listOf(supplyPhoto().copy(mime = "application/pdf"), supplyPhoto().copy(bytes = byteArrayOf()),
            supplyPhoto().copy(bytes = ByteArray(5 * 1024 * 1024 + 1)), supplyPhoto().copy(filename = "../private.jpg"),
            supplyPhoto().copy(mime = "image/png", filename = "photo.png"))) {
            assertTrue(rejects { validateSupplierPhoto(photo) }.rejected)
        }
    }
    @Test fun productPhotoUploadUsesImagePartGalleryOrderAndActualServerGallery() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore()
        api.imageValues = listOf(CanonicalProductImageDto(1, "/api/v1/images/1", "1", sortOrder = 2))
        val updated = repository(api, store).upload(store.session.value, 7, supplyPhoto())
        assertEquals(3, api.sortOrder); assertEquals(false, api.primary)
        assertTrue(api.part!!.headers!!["Content-Disposition"]!!.contains("name=\"image\""))
        assertEquals(2, updated.images.size)
    }
    @Test fun postUploadReadFailureRemainsUncertainDespite404() = runBlocking {
        val api = object : StubSupplierApi() {
            override suspend fun listing(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>> =
                if (writes == 0) super.listing(id, session) else Response.error(404, "private".toResponseBody())
        }
        val store = supplierStore()
        assertFalse(rejects { repository(api, store).upload(store.session.value, 7, supplyPhoto()) }.rejected)
        assertEquals(1, api.writes)
    }
    @Test fun invalidPhotosAndCrossAccountUploadsNeverWrite() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore(); val repo = repository(api, store)
        rejects { repo.upload(store.session.value, 7, supplyPhoto().copy(bytes = byteArrayOf())) }
        api.current = supplyDto(owner = 99)
        rejects { repo.upload(store.session.value, 7, supplyPhoto()) }
        assertEquals(0, api.writes)
    }
    @Test fun metricsOnlyUseRealListingDataAndNeverInventOrderStatistics() {
        val pending = supplyDto().toSupply(42)
        val active = supplyDto(8).copy(status = "active", stockQty = 2, reservedQty = 2).toSupply(42)
        assertEquals(SupplierMetrics(2, 1, 1, 1), supplierMetrics(listOf(pending, active)))
        assertEquals(SupplierMetrics(0, 0, 0, 0), supplierMetrics(emptyList()))
    }
    @Test fun orderDeliveryAndPaymentStatusesMatchSourceAndRejectInventedStates() {
        assertEquals(SupplierOrderStatus.PLACED, SupplierOrderStatus.parse("placed"))
        assertEquals(SupplierOrderStatus.CANCELLED, SupplierOrderStatus.parse("cancelled"))
        assertNull(SupplierOrderStatus.parse("processing"))
        assertEquals(SupplierDeliveryStatus.PICKED_UP, SupplierDeliveryStatus.parse("picked_up"))
        assertEquals(SupplierDeliveryStatus.FAILED, SupplierDeliveryStatus.parse("failed"))
        assertNull(SupplierDeliveryStatus.parse("live_gps"))
        assertEquals(VerifiedPaymentStatus.PENDING, VerifiedPaymentStatus.parse("pending"))
        assertEquals(VerifiedPaymentStatus.PAID, VerifiedPaymentStatus.parse("paid"))
        assertEquals(VerifiedPaymentStatus.HELD, VerifiedPaymentStatus.parse("held"))
        assertEquals(VerifiedPaymentStatus.FAILED, VerifiedPaymentStatus.parse("failed"))
        assertNull(VerifiedPaymentStatus.parse("cancelled")) // Backend collapses non-success/cancellation to FAILED.
        assertNull(VerifiedPaymentStatus.parse("STK accepted"))
    }
    @Test fun supplierApiHasNoOrderDriverPaymentOrPublicRegistrationRequests() {
        val paths = SupplierApi::class.java.methods.mapNotNull { method ->
            method.getAnnotation(retrofit2.http.GET::class.java)?.value ?: method.getAnnotation(retrofit2.http.POST::class.java)?.value ?: method.getAnnotation(retrofit2.http.PUT::class.java)?.value
        }
        assertFalse(paths.any { it.contains("orders") || it.contains("payments") || it.contains("logistics") || it.contains("register") })
    }
    @Test fun httpRejectionsHaveSafeMessagesAndUnknownServerOutcomesAreNotRetried() = runBlocking {
        val store = supplierStore()
        for ((code, word) in listOf(401 to "session", 403 to "denied", 413 to "limit", 415 to "format", 422 to "rejected", 500 to "unavailable")) {
            val api = object : StubSupplierApi() {
                override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductDto>>> = Response.error(code, "sensitive response".toResponseBody())
            }
            val error = rejects { repository(api, store).listings(store.session.value) }
            assertTrue(error.message!!.contains(word)); assertFalse(error.message!!.contains("sensitive"))
            assertEquals(code != 500, error.rejected)
        }
    }
    @Test fun duplicateCreationIsSingleFlightAndMarkerIsWrittenBeforeRequest() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore(); val markers = MemorySupplierOperations()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        api.beforeCreate = { assertTrue(markers.values.isNotEmpty()); entered.complete(Unit); release.await() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, markers, scope)
        try {
            holder.create(supplyDraft()); entered.await(); holder.create(supplyDraft())
            assertEquals(1, api.writes); assertTrue(holder.uiState.value.submitting)
            release.complete(Unit)
            val state = withTimeout(5000) { holder.uiState.first { !it.submitting } }
            assertEquals(7L, state.detail!!.id); assertNull(state.pending); assertTrue(markers.values.isEmpty())
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun uncertainCreationSurvivesStateRecreationAndBlocksAutomaticRetry() = runBlocking {
        val api = StubSupplierApi().apply { beforeCreate = { throw IOException("private endpoint") } }
        val store = supplierStore(); val markers = MemorySupplierOperations()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = SupplierStateHolder(repository(api, store), store, markers, scope)
        try {
            first.create(supplyDraft())
            val failed = withTimeout(5000) { first.uiState.first { !it.submitting && it.pending != null } }
            assertFalse(failed.actionError!!.contains("private endpoint"))
            first.close()
            val restored = SupplierStateHolder(repository(api, store), store, markers, scope)
            try {
                assertEquals(SupplyOperation.CREATE, restored.uiState.value.pending!!.kind)
                restored.create(supplyDraft()); assertEquals(1, api.writes)
                restored.acknowledgePending()
                withTimeout(5000) { restored.uiState.first { it.pending == null } }
                assertTrue(markers.values.isEmpty())
            } finally { restored.close() }
        } finally { first.close(); scope.cancel() }
    }
    @Test fun recoveryIsSingleFlightAndBlocksNewSubmissionsUntilMarkerIsCleared() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var recoveryReads = 0
        val api = object : StubSupplierApi() {
            override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductDto>>> {
                if (recoveryReads > 0) { recoveryReads++; entered.complete(Unit); release.await() }
                return super.listings(id, session)
            }
        }
        val store = supplierStore(); val markers = MemorySupplierOperations()
        markers.write(repository(api, store).key(store.session.value), PendingSupplyOperation(SupplyOperation.CREATE))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, markers, scope)
        try {
            recoveryReads = 1
            holder.acknowledgePending(); entered.await()
            holder.acknowledgePending(); holder.create(supplyDraft())
            assertEquals(2, recoveryReads); assertEquals(0, api.writes)
            assertTrue(holder.uiState.value.submitting); assertTrue(markers.values.isNotEmpty())
            release.complete(Unit)
            withTimeout(5000) { holder.uiState.first { !it.submitting && it.pending == null } }
            assertTrue(markers.values.isEmpty())
        } finally { release.complete(Unit); holder.close(); scope.cancel() }
    }

    @Test fun rejectedCreateCanBeCorrectedWithoutUncertaintyMarker() = runBlocking {
        val store = supplierStore(); val markers = MemorySupplierOperations()
        val api = object : StubSupplierApi() {
            override suspend fun create(request: CanonicalProductRequest, session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>> { writes++; return Response.error(422, "private validation details".toResponseBody()) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, markers, scope)
        try {
            holder.create(supplyDraft())
            val state = withTimeout(5000) { holder.uiState.first { !it.submitting && it.actionError != null } }
            assertNull(state.pending); assertTrue(markers.values.isEmpty()); assertTrue(state.actionError!!.contains("rejected"))
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun logoutAndSwitchClearPrivateDataAndRejectLateCreation() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore(); val markers = MemorySupplierOperations()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        api.beforeCreate = { withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, markers, scope)
        try {
            holder.create(supplyDraft()); entered.await()
            store.clear(store.beginLogout()!!)
            assertTrue(holder.uiState.value.listings.isEmpty()); assertNull(holder.uiState.value.profile)
            store.establish(store.session.value, supplierAuth("99"), false)
            release.complete(Unit)
            yield()
            assertTrue(holder.uiState.value.listings.isEmpty()); assertNull(holder.uiState.value.detail)
            assertNull(holder.uiState.value.pending) // Old account's durable marker never appears for 99.
        } finally { release.complete(Unit); holder.close(); scope.cancel() }
    }
    @Test fun loadingAndNetworkFailureDoNotProduceSuccessMetricsOrPrivateErrors() = runBlocking {
        val store = supplierStore(); val release = CompletableDeferred<Unit>()
        val api = object : StubSupplierApi() {
            override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductDto>>> { release.await(); throw IOException("sensitive endpoint") }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, MemorySupplierOperations(), scope)
        try {
            assertTrue(holder.uiState.value.loading); assertNotNull(holder.uiState.value.profile)
            release.complete(Unit)
            assertTrue(holder.uiState.value.listings.isEmpty()); assertNotNull(holder.uiState.value.error)
            assertFalse(holder.uiState.value.error!!.contains("sensitive"))
        } finally { holder.close(); scope.cancel() }
    }

    @Test fun markerStorageFailurePreventsRemoteMutation() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore()
        val broken = object : SupplierOperationStore {
            override fun read(accountKey: String): PendingSupplyOperation? = null
            override fun write(accountKey: String, pending: PendingSupplyOperation?) { throw IOException("private storage path") }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, broken, scope)
        try {
            holder.create(supplyDraft())
            val state = withTimeout(5000) { holder.uiState.first { !it.submitting && it.actionError != null } }
            assertEquals(0, api.writes); assertFalse(state.actionError!!.contains("private storage path"))
        } finally { holder.close(); scope.cancel() }
    }

    @Test fun failedMarkerClearKeepsConfirmedListingVisibleAndFurtherSubmissionsBlocked() = runBlocking {
        val api = StubSupplierApi(); val store = supplierStore()
        val memory = MemorySupplierOperations()
        val broken = object : SupplierOperationStore {
            override fun read(accountKey: String) = memory.read(accountKey)
            override fun write(accountKey: String, pending: PendingSupplyOperation?) {
                if (pending == null) throw IOException("cannot clear")
                memory.write(accountKey, pending)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(repository(api, store), store, broken, scope)
        try {
            holder.create(supplyDraft())
            val state = withTimeout(5000) { holder.uiState.first { !it.submitting && it.actionError != null } }
            assertEquals(7L, state.detail!!.id); assertEquals(SupplyOperation.CREATE, state.pending!!.kind)
            holder.create(supplyDraft()); assertEquals(1, api.writes)
        } finally { holder.close(); scope.cancel() }
    }
}
