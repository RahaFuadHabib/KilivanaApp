package com.example.farmapp.inspector

import com.example.farmapp.data.model.canonical.CanonicalUserDto
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class InspectorWorkflowTest {
    @Test fun refreshIsSingleFlightAndRetainsAssignmentsOnFailure() = runBlocking {
        val sessions = store()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var failReads = false
        val api = object : Api() {
            override suspend fun inspections(inspectorId: Long): Response<CanonicalApiResponse<List<InspectionDto>>> {
                calls++
                if (calls > 1) release.await()
                if (failReads) throw IOException("Synthetic failure")
                return Response.success(CanonicalApiResponse(true, data = listOf(current)))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            assertEquals(1, holder.uiState.value.jobs.size)
            failReads = true
            holder.refresh(); holder.refresh()
            assertEquals(2, calls)
            assertEquals(1, holder.uiState.value.jobs.size)
            release.complete(Unit)
            assertFalse(holder.uiState.value.loadingJobs)
            assertNotNull(holder.uiState.value.jobsError)
            assertEquals(1, holder.uiState.value.jobs.size)
        } finally { holder.close(); scope.cancel() }
    }
    private val origin = "https://fixture.example.test/api/v1/"
    private fun auth() = testAuth(role = "INSPECTOR").copy(userId = "42", backendContract = "canonical",
        sessionOrigin = origin, refreshTokenExpiresAt = "", verificationStatus = "NOT_REQUIRED")
    private fun store() = signedInStore(auth())
    private fun dto() = InspectionDto(1, 42, "FARMER", 7, "ASSIGNED")
    private open inner class Api : InspectorApi {
        var writes = 0
        var lastBody: InspectionFindingRequest? = null
        var current = dto()
        var statusWrite: suspend () -> Unit = {}
        override suspend fun me() = Response.success(CanonicalApiResponse(true, data = CanonicalUserDto(42, "Synthetic Inspector", "synthetic@example.test", "+254700000000", role = "INSPECTOR", status = "ACTIVE", verificationStatus = "NOT_REQUIRED")))
        override suspend fun profile(userId: Long) = Response.success(CanonicalApiResponse(true, data = InspectorProfileDto(1, userId)))
        override suspend fun inspections(inspectorId: Long) = Response.success(CanonicalApiResponse(true, data = listOf(current)))
        override suspend fun inspection(id: Long) = Response.success(CanonicalApiResponse(true, data = current))
        override suspend fun evidence(id: Long) = Response.success(CanonicalApiResponse(true, data = emptyList<InspectorImageDto>()))
        override suspend fun updateStatus(id: Long, status: String, session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>> {
            writes++; statusWrite(); current = current.copy(status = status); return Response.success(CanonicalApiResponse(true, data = current))
        }
        override suspend fun updateResult(id: Long, result: String, request: InspectionFindingRequest, session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>> {
            writes++; lastBody = request; current = current.copy(result = result, notes = request.notes)
            return Response.success(CanonicalApiResponse(true, data = current))
        }
    }
    @Test fun statusChoicesFollowServerEnumWithoutInventingTerminalStates() {
        for (phase in InspectionStatus.entries) {
            assertEquals(InspectionStatus.entries.toSet() - phase, supportedStatusChanges(phase).toSet())
        }
    }
    @Test fun findingsValidateNotesAndUseFreshOwnedRecordWithoutChangingStatus() = runBlocking {
        val sessions = store(); val api = Api(); val repository = InspectorRepository(api, sessions, origin)
        for (notes in listOf(" ", "a".repeat(4001))) {
            try { repository.submitFinding(sessions.session.value, 1, InspectionFinding.APPROVED, notes); fail() } catch (e: InspectorFailure) { assertTrue(e.rejected) }
        }
        assertEquals(0, api.writes)
        val result = repository.submitFinding(sessions.session.value, 1, InspectionFinding.CHANGES_REQUIRED, " Synthetic findings ")
        assertEquals("ASSIGNED", api.lastBody!!.status)
        assertEquals(42L, api.lastBody!!.inspectorId)
        assertEquals("Synthetic findings", api.lastBody!!.notes)
        assertEquals("CHANGES_REQUIRED", result.result)
        assertEquals(InspectionStatus.ASSIGNED, result.status)
        try { repository.submitFinding(sessions.session.value, 1, InspectionFinding.CHANGES_REQUIRED, "Synthetic findings"); fail() } catch (e: InspectorFailure) { assertTrue(e.rejected) }
        assertEquals(1, api.writes)
    }
    @Test fun reassignedInspectionIsNeverWrittenOrUsedForEvidence() = runBlocking {
        val sessions = store(); val api = Api().apply { current = dto().copy(inspectorId = 99) }
        val repository = InspectorRepository(api, sessions, origin)
        for (action in listOf<suspend () -> Unit>(
            { repository.changeStatus(sessions.session.value, 1, InspectionStatus.IN_PROGRESS) },
            { repository.submitFinding(sessions.session.value, 1, InspectionFinding.REJECTED, "Synthetic") },
            { repository.evidence(sessions.session.value, 1) })) {
            try { action(); fail() } catch (_: InspectorFailure) { }
        }
        assertEquals(0, api.writes)
    }
    @Test fun doubleTapProducesOneWriteAndSuccessUsesActualServerResponse() = runBlocking {
        val sessions = store(); val api = Api(); val release = CompletableDeferred<Unit>()
        api.statusWrite = { release.await() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            holder.open(1); holder.changeStatus(InspectionStatus.IN_PROGRESS); holder.changeStatus(InspectionStatus.COMPLETED)
            assertTrue(holder.uiState.value.submitting); assertEquals(1, api.writes)
            holder.refresh(); holder.closeDetail()
            assertEquals(1L, holder.uiState.value.selectedId)
            release.complete(Unit)
            assertFalse(holder.uiState.value.submitting)
            assertEquals(InspectionStatus.IN_PROGRESS, holder.uiState.value.detail!!.status)
            assertEquals(InspectionStatus.IN_PROGRESS, holder.uiState.value.jobs.single().status)
            assertTrue(holder.uiState.value.writeNotice!!.contains("In progress"))
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun timeoutLocksFurtherSubmissionsUntilExplicitServerReload() = runBlocking {
        val sessions = store(); val api = Api().apply { statusWrite = { throw IOException("private URL must not appear") } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            holder.open(1); holder.changeStatus(InspectionStatus.IN_PROGRESS)
            assertTrue(1L in holder.uiState.value.reconciliationRequired)
            assertFalse(holder.uiState.value.writeError!!.contains("private URL"))
            holder.changeStatus(InspectionStatus.IN_PROGRESS); assertEquals(1, api.writes)
            holder.open(1); assertTrue(holder.uiState.value.reconciliationRequired.isEmpty())
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun forbiddenWriteShowsSafeRejectionWithoutAutomaticReplay() = runBlocking {
        val sessions = store()
        val api = object : Api() {
            override suspend fun updateStatus(id: Long, status: String, session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>> {
                writes++; return Response.error(403, "secret server body".toResponseBody())
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            holder.open(1); holder.changeStatus(InspectionStatus.IN_PROGRESS)
            assertEquals(1, api.writes); assertTrue(holder.uiState.value.writeError!!.contains("denied"))
            assertTrue(holder.uiState.value.reconciliationRequired.isEmpty())
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun logoutDuringWriteRejectsLateResponseAndClearsFindings() = runBlocking {
        val sessions = store(); val release = CompletableDeferred<Unit>(); val api = Api()
        api.statusWrite = { withContext(NonCancellable) { release.await() } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = InspectorStateHolder(InspectorRepository(api, sessions, origin), sessions, scope)
        try {
            holder.open(1); holder.changeStatus(InspectionStatus.IN_PROGRESS)
            sessions.clear(sessions.beginLogout()!!)
            release.complete(Unit)
            assertNull(holder.uiState.value.detail); assertTrue(holder.uiState.value.jobs.isEmpty())
            assertNull(holder.uiState.value.writeNotice)
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun retrofitWritesUseVerifiedRoutesAndRedactedBody() = runBlocking {
        val sessions = store(); var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); calls++
            assertEquals("PUT", request.method)
            assertEquals(sessions.session.value, request.tag(SessionSnapshot::class.java))
            if (calls == 1) {
                assertEquals("/api/v1/inspections/1/status", request.url.encodedPath)
                assertEquals("IN_PROGRESS", request.url.queryParameter("status"))
            } else {
                assertEquals("/api/v1/inspections/1/result", request.url.encodedPath)
                assertEquals("APPROVED", request.url.queryParameter("result"))
                val body = com.google.gson.JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
                assertEquals(setOf("inspectorId", "targetType", "targetId", "status", "result", "notes"), body.keySet())
                assertFalse(body.has("evidenceUrls"))
            }
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("""{"success":true,"data":{"id":1}}""".toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build().create(InspectorApi::class.java)
        api.updateStatus(1, "IN_PROGRESS", sessions.session.value)
        val body = InspectionFindingRequest(42, "FARMER", 7, "IN_PROGRESS", "APPROVED", "Synthetic notes")
        api.updateResult(1, "APPROVED", body, sessions.session.value)
        assertFalse(body.toString().contains("Synthetic notes")); assertEquals(2, calls)
    }
    @Test fun queuedRequestCannotUseAnotherAccountToken() {
        val sessions = store(); val expected = sessions.session.value
        sessions.clear(sessions.beginLogout()!!)
        sessions.establish(sessions.session.value, auth().copy(userId = "99"), false)
        var reachedTransport = false
        val client = OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor(origin.toHttpUrl(), sessions, RefreshCoordinator(StubAuthService(), sessions)))
            .addInterceptor { reachedTransport = true; error("No transport permitted") }.build()
        try { client.newCall(Request.Builder().url(origin + "inspections/1/status?status=IN_PROGRESS")
            .tag(SessionSnapshot::class.java, expected).put(ByteArray(0).toRequestBody()).build()).execute().close(); fail() }
        catch (_: IOException) { }
        assertFalse(reachedTransport)
    }
    @Test fun evidenceUploadRemainsUnavailableWithoutPrivateDelivery() {
        assertTrue(EVIDENCE_PRIVACY_BLOCKER.contains("private"))
        assertFalse(InspectorApi::class.java.methods.any { it.getAnnotation(retrofit2.http.Multipart::class.java) != null })
    }
}
