package com.example.farmapp.inspector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InspectorUiState(
    val generation: Long = -1, val jobs: List<Inspection> = emptyList(), val profile: InspectorProfile? = null,
    val loadingJobs: Boolean = false, val loadingProfile: Boolean = false,
    val jobsError: String? = null, val profileError: String? = null,
    val selectedId: Long? = null, val detail: Inspection? = null,
    val loadingDetail: Boolean = false, val detailError: String? = null,
    val evidence: List<InspectorImageDto> = emptyList(), val loadingEvidence: Boolean = false,
    val evidenceError: String? = null, val submitting: Boolean = false,
    val writeError: String? = null, val writeNotice: String? = null,
    val reconciliationRequired: Set<Long> = emptySet()
)

/** Session-aware state engine is separately testable without Android's main dispatcher. */
internal class InspectorStateHolder(
    private val repository: InspectorRepository, private val sessions: SessionStore, private val scope: CoroutineScope
) {
    private val state = MutableStateFlow(InspectorUiState())
    val uiState = state.asStateFlow()
    private val refreshes = MutableStateFlow(0L)
    private var detailJob: Job? = null
    private var writeJob: Job? = null
    private val writeGate = java.util.concurrent.atomic.AtomicBoolean(false)
    private val refreshGate = java.util.concurrent.atomic.AtomicBoolean(false)
    private val sessionGuard = scope.launch {
        sessions.session.collect { session ->
            if (session.generation != state.value.generation || session.loggingOut || session.isDemo || session.auth?.role != "INSPECTOR") {
                state.value = InspectorUiState()
                detailJob?.cancel()
                writeJob?.cancel()
            }
        }
    }
    private val observer = scope.launch {
        combine(sessions.session.map { Triple(it.generation, it.loggingOut || it.isDemo, it.auth?.let { auth ->
            "${auth.backendContract}:${auth.role}:${auth.userId}"
        }) }.distinctUntilChanged(), refreshes) { identity, refresh -> identity to refresh }
            .collectLatest {
                detailJob?.cancel()
                val expected = sessions.session.value
                val previous = state.value.takeIf { it.generation == expected.generation } ?: InspectorUiState()
                try { repository.owner(expected) } catch (e: InspectorFailure) {
                    if (expected.auth?.role == "INSPECTOR" && !expected.loggingOut) {
                        state.value = InspectorUiState(generation = expected.generation, jobsError = e.message, profileError = e.message)
                    }
                    refreshGate.set(false)
                    return@collectLatest
                }
                refreshGate.set(true)
                state.value = previous.copy(generation = expected.generation, loadingJobs = true, loadingProfile = true)
                try { coroutineScope {
                    launch {
                        try {
                            val jobs = repository.jobs(expected)
                            publish(expected) { it.copy(jobs = jobs, loadingJobs = false, jobsError = null) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { publish(expected) { it.copy(loadingJobs = false, jobsError = safeMessage(e)) } }
                    }
                    launch {
                        try {
                            val profile = repository.profile(expected)
                            publish(expected) { it.copy(profile = profile, loadingProfile = false, profileError = null) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { publish(expected) { it.copy(loadingProfile = false, profileError = safeMessage(e)) } }
                    }
                } } finally { refreshGate.set(false) }
            }
    }
    private fun publish(expected: SessionSnapshot, update: (InspectorUiState) -> InspectorUiState) {
        try { repository.owner(expected) } catch (_: InspectorFailure) { return }
        state.update(update)
    }
    fun refresh() { if (!writeGate.get() && refreshGate.compareAndSet(false, true)) refreshes.value++ }
    fun open(id: Long) {
        if (writeGate.get()) return
        if (state.value.jobs.none { it.id == id }) return
        detailJob?.cancel()
        val expected = sessions.session.value
        try { repository.owner(expected) } catch (_: InspectorFailure) { return }
        state.update { it.copy(selectedId = id, detail = null, loadingDetail = true, detailError = null,
            evidence = emptyList(), loadingEvidence = true, evidenceError = null, writeError = null, writeNotice = null) }
        detailJob = scope.launch {
            try {
                val detail = repository.detail(expected, id)
                publish(expected) { if (it.selectedId == id) it.copy(detail = detail, loadingDetail = false,
                    reconciliationRequired = it.reconciliationRequired - id) else it }
                try {
                    val evidence = repository.evidence(expected, id)
                    publish(expected) { if (it.selectedId == id) it.copy(evidence = evidence, loadingEvidence = false) else it }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { publish(expected) { if (it.selectedId == id) it.copy(loadingEvidence = false, evidenceError = safeMessage(e)) else it } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { publish(expected) { if (it.selectedId == id) it.copy(loadingDetail = false, loadingEvidence = false, detailError = safeMessage(e)) else it } }
        }
    }
    fun closeDetail() {
        if (writeGate.get()) return
        detailJob?.cancel()
        state.update { it.copy(selectedId = null, detail = null, detailError = null, loadingDetail = false,
            evidence = emptyList(), evidenceError = null, loadingEvidence = false, writeError = null, writeNotice = null) }
    }
    fun changeStatus(status: InspectionStatus) = submit { expected, id -> repository.changeStatus(expected, id, status) }
    fun submitFinding(result: InspectionFinding, notes: String) = submit { expected, id -> repository.submitFinding(expected, id, result, notes) }
    private fun submit(action: suspend (SessionSnapshot, Long) -> Inspection) {
        val selected = state.value.detail ?: return
        if (selected.id in state.value.reconciliationRequired || state.value.loadingDetail || refreshGate.get() || !writeGate.compareAndSet(false, true)) return
        val expected = sessions.session.value
        try { repository.owner(expected) } catch (_: InspectorFailure) { writeGate.set(false); return }
        state.update { it.copy(submitting = true, writeError = null, writeNotice = null) }
        writeJob = scope.launch {
            try {
                val updated = action(expected, selected.id)
                publish(expected) { it.copy(detail = updated, jobs = it.jobs.map { job -> if (job.id == updated.id) updated else job },
                    writeNotice = "Server response: ${updated.status.label}; result ${updated.result ?: "not recorded"}.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val uncertain = e !is InspectorFailure || !e.rejected
                publish(expected) { it.copy(writeError = if (uncertain)
                    "The submission outcome is unknown. Check your connection, then reload this inspection and inspect the server state before another submission."
                    else safeMessage(e), reconciliationRequired = if (uncertain) it.reconciliationRequired + selected.id else it.reconciliationRequired) }
            } finally {
                writeGate.set(false)
                publish(expected) { it.copy(submitting = false) }
            }
        }
    }
    fun close() { observer.cancel(); sessionGuard.cancel(); detailJob?.cancel(); writeJob?.cancel(); state.value = InspectorUiState() }
    private fun safeMessage(e: Exception) = if (e is InspectorFailure) e.message ?: "Inspection unavailable."
        else "Unable to load Inspector data. Check your connection and try again."
}

class InspectorViewModel : ViewModel() {
    private val holder = InspectorStateHolder(InspectorRepository(ApiClient.inspectorApi, TokenManager,
        BuildConfig.API_BASE_URL), TokenManager, viewModelScope)
    val uiState = holder.uiState
    fun refresh() = holder.refresh()
    fun open(id: Long) = holder.open(id)
    fun closeDetail() = holder.closeDetail()
    fun changeStatus(status: InspectionStatus) = holder.changeStatus(status)
    fun submitFinding(result: InspectionFinding, notes: String) = holder.submitFinding(result, notes)
    override fun onCleared() { holder.close() }
    companion object {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(InspectorViewModel::class.java))
                @Suppress("UNCHECKED_CAST") return InspectorViewModel() as T
            }
        }
    }
}
