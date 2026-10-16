package com.example.farmapp.inspector

import com.example.farmapp.data.model.canonical.CanonicalUserDto
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.CanonicalJson
import com.example.farmapp.ui.navigation.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class InspectorModuleTest {
    private val origin = "https://fixture.example.test/api/v1/"
    private fun auth(id: String = "42") = testAuth(role = "INSPECTOR").copy(userId = id,
        backendContract = "canonical", sessionOrigin = origin, refreshTokenExpiresAt = "", verificationStatus = "NOT_REQUIRED")
    private fun store() = signedInStore(auth())
    private fun job(id: Long = 1, owner: Long = 42, status: String = "ASSIGNED") =
        InspectionDto(id, owner, "PRODUCT", 7, status, createdAt = "2026-10-16T09:00:00")
    private open inner class FakeApi : InspectorApi {
        var requestedOwner: Long? = null
        var requestedDetail: Long? = null
        var list: suspend () -> Response<CanonicalApiResponse<List<InspectionDto>>> = { Response.success(CanonicalApiResponse(true, data = listOf(job()))) }
        override suspend fun me() = Response.success(CanonicalApiResponse(true, data =
            CanonicalUserDto(42, "Synthetic Inspector", "inspector@example.test", "+254700000000",
                role = "INSPECTOR", status = "ACTIVE", verificationStatus = "NOT_REQUIRED")))
        override suspend fun profile(userId: Long) = Response.success(CanonicalApiResponse(true, data =
            InspectorProfileDto(2, userId, specialization = "Synthetic produce", assignedArea = "Synthetic region")))
        override suspend fun inspections(inspectorId: Long): Response<CanonicalApiResponse<List<InspectionDto>>> {
            requestedOwner = inspectorId; return list()
        }
        override suspend fun inspection(id: Long): Response<CanonicalApiResponse<InspectionDto>> {
            requestedDetail = id; return Response.success(CanonicalApiResponse(true, data = job(id)))
        }
        override suspend fun evidence(id: Long): Response<CanonicalApiResponse<List<InspectorImageDto>>> =
            Response.success(CanonicalApiResponse(true, data = emptyList()))
        override suspend fun updateStatus(id: Long, status: String, session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>> = error("No writes in read-only tests")
        override suspend fun updateResult(id: Long, result: String, request: InspectionFindingRequest, session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>> = error("No writes in read-only tests")
    }
    private suspend fun rejects(block: suspend () -> Unit): InspectorFailure {
        try { block() } catch (e: InspectorFailure) { return e }
        throw AssertionError("Expected fail-closed Inspector rejection")
    }

    @Test fun canonicalInspectorRoutesByRoleIncludingNotRequiredVerification() {
        assertEquals(AuthDestination.INSPECTOR, authDestination(auth()))
        assertEquals(AuthDestination.INSPECTOR, authDestination(auth().copy(verificationStatus = "PENDING")))
        assertEquals(AuthDestination.AWAITING_VERIFICATION, authDestination(testAuth(role = "INSPECTOR").copy(verificationStatus = "PENDING")))
        assertEquals(AuthDestination.FARMER, authDestination(auth().copy(role = "FARMER")))
        assertEquals(AuthDestination.BUYER, authDestination(auth().copy(role = "BUYER")))
        assertEquals(AuthDestination.SUPPLIER, authDestination(auth().copy(role = "SUPPLIER")))
    }
    @Test fun mappingPreservesActualFieldsAndStatusValues() {
        val dto = CanonicalJson.gson.fromJson("""{"id":1,"inspectorId":42,"targetType":"FARMER","targetId":8,
            "status":"COMPLETED","result":"CHANGES_REQUIRED","notes":"Synthetic note","inspectedAt":"2026-10-16T10:00:00"}""", InspectionDto::class.java)
        val value = dto.toInspection(42)
        assertEquals(InspectionStatus.COMPLETED, value.status)
        assertEquals("CHANGES_REQUIRED", value.result)
        assertEquals(8L, value.targetId)
        assertEquals("Synthetic note", value.notes)
        assertEquals(10, value.inspectedAt!!.hour)
        assertNull(value.createdAt)
    }
    @Test fun countsUseAllFiveRealStatusesAndEmptyIsZero() {
        val jobs = InspectionStatus.entries.mapIndexed { index, status -> job(index + 1L, status = status.name).toInspection(42) }
        assertEquals(InspectionCounts(5, 2, 1, 1, 1), inspectionCounts(jobs))
        assertEquals(InspectionCounts(0, 0, 0, 0, 0), inspectionCounts(emptyList()))
    }
    @Test fun unexpectedStatusAndInvalidDateAreRejected() = runBlocking {
        rejects { job(status = "FAKE_STATUS").toInspection(42) }
        rejects { job().copy(createdAt = "tomorrow").toInspection(42) }
        rejects { job().copy(result = "PASSED").toInspection(42) }
        Unit
    }
    @Test fun listUsesAuthenticatedUserAndRejectsMixedOwnership() = runBlocking {
        val sessions = store(); val api = FakeApi(); val repository = InspectorRepository(api, sessions, origin)
        assertEquals(1, repository.jobs(sessions.session.value).size)
        assertEquals(42L, api.requestedOwner)
        api.list = { Response.success(CanonicalApiResponse(true, data = listOf(job(), job(2, 99)))) }
        rejects { repository.jobs(sessions.session.value) }
        Unit
    }
    @Test fun wrongDetailIdentityOrAssignmentIsRejected() = runBlocking {
        val sessions = store()
        for (dto in listOf(job(2), job(1, 99))) {
            val api = object : FakeApi() {
                override suspend fun inspection(id: Long) = Response.success(CanonicalApiResponse(true, data = dto))
            }
            rejects { InspectorRepository(api, sessions, origin).detail(sessions.session.value, 1) }
        }
    }
    @Test fun nonInspectorAndOtherBackendMakeNoListRequest() = runBlocking {
        val sessions = store(); val api = FakeApi()
        rejects { InspectorRepository(api, sessions, "https://other.test/api/v1/").jobs(sessions.session.value) }
        sessions.establish(sessions.session.value, auth().copy(role = "FARMER"), false)
        rejects { InspectorRepository(api, sessions, origin).jobs(sessions.session.value) }
        assertNull(api.requestedOwner)
    }
    @Test fun profileMapsAdminFieldsAndMissingProfileIsEmpty() = runBlocking {
        val sessions = store(); val api = FakeApi()
        val result = InspectorRepository(api, sessions, origin).profile(sessions.session.value)
        assertEquals("Synthetic region", result.details!!.assignedArea)
        assertEquals("Synthetic produce", result.details.specialization)
        val missing = object : FakeApi() {
            override suspend fun profile(userId: Long): Response<CanonicalApiResponse<InspectorProfileDto>> = Response.error(404, "{}".toResponseBody())
        }
        assertNull(InspectorRepository(missing, sessions, origin).profile(sessions.session.value).details)
    }
    @Test fun anotherUsersProfileIsRejected() = runBlocking {
        val sessions = store()
        val api = object : FakeApi() {
            override suspend fun profile(userId: Long) = Response.success(CanonicalApiResponse(true, data = InspectorProfileDto(2, 99)))
        }
        rejects { InspectorRepository(api, sessions, origin).profile(sessions.session.value) }
        Unit
    }
    @Test fun serverErrorsAreSafeAndPreserveHttpMeaning() = runBlocking {
        for ((status, text) in listOf(401 to "session", 403 to "denied", 404 to "no longer", 500 to "unavailable")) {
            val sessions = store(); val api = FakeApi()
            api.list = { Response.error(status, "sensitive-server-body".toResponseBody()) }
            val failure = rejects { InspectorRepository(api, sessions, origin).jobs(sessions.session.value) }
            assertTrue(failure.message!!.contains(text))
            assertFalse(failure.message!!.contains("sensitive"))
        }
    }
    @Test fun stateHasLoadingErrorRetryAndIndependentProfile() = runBlocking {
        val sessions = store(); val api = FakeApi(); val release = CompletableDeferred<Unit>()
        api.list = { release.await(); Response.error(403, "private".toResponseBody()) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            assertTrue(holder.uiState.value.loadingJobs)
            assertNotNull(holder.uiState.value.profile)
            release.complete(Unit)
            assertTrue(holder.uiState.value.jobsError!!.contains("denied"))
            assertTrue(holder.uiState.value.jobs.isEmpty())
            api.list = { Response.success(CanonicalApiResponse(true, data = emptyList())) }
            holder.refresh()
            assertNull(holder.uiState.value.jobsError)
            assertFalse(holder.uiState.value.loadingJobs)
            assertTrue(holder.uiState.value.jobs.isEmpty())
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun logoutClearsLoadedPrivateStateAndUnassignedDetailIsNeverRequested() = runBlocking {
        val sessions = store(); val api = FakeApi(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            assertEquals(1, holder.uiState.value.jobs.size)
            holder.open(99)
            assertNull(api.requestedDetail)
            holder.open(1)
            assertEquals(1L, holder.uiState.value.detail!!.id)
            sessions.beginLogout()
            assertTrue(holder.uiState.value.jobs.isEmpty())
            assertNull(holder.uiState.value.profile)
            assertNull(holder.uiState.value.detail)
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun latePreviousAccountResponseCannotPopulateNewSession() = runBlocking {
        val sessions = store(); val api = FakeApi(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        api.list = { withContext(NonCancellable) { started.complete(Unit); release.await()
            Response.success(CanonicalApiResponse(true, data = listOf(job()))) } }
        val expected = sessions.session.value
        val result = async { rejects { InspectorRepository(api, sessions, origin).jobs(expected) } }
        started.await()
        sessions.clear(sessions.beginLogout()!!)
        sessions.establish(sessions.session.value, auth("99"), false)
        release.complete(Unit)
        result.await()
        assertEquals("99", sessions.session.value.auth!!.userId)
    }

    @Test fun stateSwitchesToOnlyNewInspectorsAssignmentsAndKeepsTokenRotation() = runBlocking {
        val sessions = store(); val api = FakeApi()
        api.list = { Response.success(CanonicalApiResponse(true, data = listOf(job(owner = api.requestedOwner!!)))) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            assertEquals(42L, holder.uiState.value.jobs.single().inspectorId)
            sessions.rotate(sessions.session.value, auth().copy(accessToken = "synthetic-rotated"))
            assertEquals(42L, holder.uiState.value.jobs.single().inspectorId)
            sessions.clear(sessions.beginLogout()!!)
            sessions.establish(sessions.session.value, auth("99"), false)
            assertEquals(99L, api.requestedOwner)
            assertEquals(99L, holder.uiState.value.jobs.single().inspectorId)
            assertNull(holder.uiState.value.profile) // Fake /auth/me still identifies 42, so it must be rejected.
            assertNotNull(holder.uiState.value.profileError)
        } finally { holder.close(); scope.cancel() }
    }

    @Test fun malformedOrDuplicateListDoesNotBecomeDashboardStatistics() = runBlocking {
        val sessions = store(); val api = FakeApi()
        api.list = { Response.success(CanonicalApiResponse(true, data = listOf(job(), job()))) }
        rejects { InspectorRepository(api, sessions, origin).jobs(sessions.session.value) }
        api.list = { Response.success(CanonicalApiResponse(false, data = listOf(job()))) }
        try { InspectorRepository(api, sessions, origin).jobs(sessions.session.value); fail() }
        catch (_: com.example.farmapp.network.canonical.CanonicalResponseFailure) { }
    }
}
