package com.example.farmapp.supplier

import android.content.Context
import androidx.lifecycle.*
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.model.canonical.CanonicalCategoryDto
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

data class SupplierUiState(val generation: Long = -1, val listings: List<SupplyListing> = emptyList(),
    val categories: List<CanonicalCategoryDto> = emptyList(), val profile: SupplierProfile? = null,
    val loading: Boolean = false, val profileLoading: Boolean = false, val categoriesLoading: Boolean = false,
    val error: String? = null, val profileError: String? = null, val categoryError: String? = null,
    val selectedId: Long? = null, val detail: SupplyListing? = null, val detailLoading: Boolean = false,
    val detailError: String? = null, val submitting: Boolean = false, val actionError: String? = null,
    val notice: String? = null, val pending: PendingSupplyOperation? = null)

internal class SupplierStateHolder(private val repository: SupplierRepository, private val sessions: SessionStore,
    private val operations: SupplierOperationStore, private val scope: CoroutineScope) {
    private val state = MutableStateFlow(SupplierUiState())
    val uiState = state.asStateFlow()
    private val refreshes = MutableStateFlow(0L)
    private val gate = AtomicBoolean(false)
    private var detailJob: Job? = null
    private var writeJob: Job? = null
    private val sessionGuard = scope.launch {
        sessions.session.collect { snapshot ->
            if (snapshot.generation != state.value.generation || snapshot.loggingOut || snapshot.isDemo || snapshot.auth?.role != "SUPPLIER") {
                state.value = SupplierUiState(); detailJob?.cancel(); writeJob?.cancel()
            }
        }
    }
    private val observer = scope.launch {
        combine(sessions.session.map { Triple(it.generation, it.loggingOut || it.isDemo,
            it.auth?.let { auth -> "${auth.backendContract}:${auth.role}:${auth.userId}" }) }.distinctUntilChanged(), refreshes) { _, _ -> sessions.session.value }
            .collectLatest { expected ->
                detailJob?.cancel()
                state.value = SupplierUiState()
                try {
                    val key = repository.key(expected)
                    state.value = SupplierUiState(generation = expected.generation, loading = true, profileLoading = true,
                        categoriesLoading = true, pending = operations.read(key))
                } catch (e: Exception) {
                    if (expected.auth?.role == "SUPPLIER" && !expected.loggingOut) state.value = SupplierUiState(generation = expected.generation, error = message(e))
                    return@collectLatest
                }
                coroutineScope {
                    launch {
                        try { val listings = repository.listings(expected); publish(expected) { it.copy(listings = listings, loading = false) } }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { publish(expected) { it.copy(loading = false, error = message(e)) } }
                    }
                    launch {
                        try { val categories = repository.categories(expected); publish(expected) { it.copy(categories = categories, categoriesLoading = false) } }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { publish(expected) { it.copy(categoriesLoading = false, categoryError = message(e)) } }
                    }
                    launch {
                        try { val profile = repository.profile(expected); publish(expected) { it.copy(profile = profile, profileLoading = false) } }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { publish(expected) { it.copy(profileLoading = false, profileError = message(e)) } }
                    }
                }
            }
    }
    private fun publish(expected: SessionSnapshot, update: (SupplierUiState) -> SupplierUiState) {
        try { repository.owner(expected) } catch (_: SupplierFailure) { return }
        state.update(update)
    }
    fun refresh() { if (!gate.get()) refreshes.value++ }
    fun open(id: Long) {
        if (gate.get() || state.value.listings.none { it.id == id }) return
        detailJob?.cancel()
        val expected = sessions.session.value
        state.update { it.copy(selectedId = id, detail = null, detailLoading = true, detailError = null, actionError = null, notice = null) }
        detailJob = scope.launch {
            try { val result = repository.listing(expected, id); publish(expected) { if (it.selectedId == id) it.copy(detail = result, detailLoading = false) else it } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { publish(expected) { it.copy(detailLoading = false, detailError = message(e)) } }
        }
    }
    fun closeDetail() { if (!gate.get()) { detailJob?.cancel(); state.update { it.copy(selectedId = null, detail = null, detailError = null, actionError = null, notice = null) } } }
    fun create(draft: SupplyDraft) = mutate(PendingSupplyOperation(SupplyOperation.CREATE)) { expected ->
        draft.request(state.value.categories) // Validate locally before even the catalogue preflight.
        repository.create(expected, draft)
    }
    fun update(id: Long, draft: SupplyDraft) = mutate(PendingSupplyOperation(SupplyOperation.UPDATE, id)) { repository.update(it, id, draft) }
    fun upload(id: Long, photo: SupplierPhoto) = mutate(PendingSupplyOperation(SupplyOperation.UPLOAD, id)) { repository.upload(it, id, photo) }
    private fun mutate(pending: PendingSupplyOperation, action: suspend (SessionSnapshot) -> SupplyListing) {
        if (state.value.pending != null || !gate.compareAndSet(false, true)) return
        val expected = sessions.session.value
        val key = try { repository.key(expected) } catch (_: SupplierFailure) { gate.set(false); return }
        if (pending.listingId != null && state.value.listings.none { it.id == pending.listingId }) { gate.set(false); return }
        state.update { it.copy(submitting = true, actionError = null, notice = null) }
        writeJob = scope.launch {
            var markerWritten = false
            var confirmed: SupplyListing? = null
            try {
                withContext(Dispatchers.IO) { operations.write(key, pending) }
                markerWritten = true
                val result = action(expected)
                repository.owner(expected)
                confirmed = result
                withContext(Dispatchers.IO) { operations.write(key, null) }
                publish(expected) { it.copy(listings = (it.listings.filterNot { old -> old.id == result.id } + result).sortedByDescending { product -> product.id },
                    selectedId = result.id, detail = result, detailLoading = false, pending = null,
                    notice = if (pending.kind == SupplyOperation.CREATE) "Supply #${result.id} created. It awaits administrator approval. Add product photos below."
                        else "Server response received for supply #${result.id}.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val rejected = e is SupplierFailure && e.rejected
                if (rejected && markerWritten) try { withContext(Dispatchers.IO) { operations.write(key, null) } } catch (_: Exception) { /* Keep durable marker if clearing fails. */ }
                val unresolved = if (!markerWritten) null else if (!rejected) pending
                    else try { withContext(Dispatchers.IO) { operations.read(key) } } catch (_: Exception) { pending }
                publish(expected) { current -> current.copy(pending = unresolved,
                    selectedId = confirmed?.id ?: current.selectedId, detail = confirmed ?: current.detail,
                    notice = confirmed?.let { "Backend confirmed supply #${it.id}. Submission safety needs review." },
                    listings = confirmed?.let { result -> (current.listings.filterNot { it.id == result.id } + result).sortedByDescending { it.id } } ?: current.listings,
                    actionError = if (confirmed != null) "The backend confirmed supply #${confirmed!!.id}, but its submission safety marker could not be cleared. Inspect this record before continuing."
                    else if (unresolved != null)
                    "Submission outcome is uncertain. Refresh My supplies and inspect the server record/photos before acknowledging it. Do not repeat the submission."
                    else message(e)) }
            } finally { gate.set(false); publish(expected) { it.copy(submitting = false) } }
        }
    }
    /** Explicit acknowledgement after a successful fresh list/detail read; never auto-retry. */
    fun acknowledgePending() {
        if (state.value.pending == null || state.value.loading || state.value.error != null || !gate.compareAndSet(false, true)) return
        val expected = sessions.session.value
        state.update { it.copy(submitting = true, actionError = null) }
        writeJob = scope.launch {
            try {
                val key = repository.key(expected)
                val latest = repository.listings(expected)
                val pending = operations.read(key)
                if (pending?.listingId != null) repository.listing(expected, pending.listingId)
                repository.owner(expected)
                withContext(Dispatchers.IO) { operations.write(key, null) }
                publish(expected) { it.copy(pending = null, listings = latest, notice = "Server state reloaded. Review existing listings and photos before submitting anything again.", actionError = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { publish(expected) { it.copy(actionError = message(e)) } }
            finally { gate.set(false); publish(expected) { it.copy(submitting = false) } }
        }
    }
    fun close() { observer.cancel(); sessionGuard.cancel(); detailJob?.cancel(); writeJob?.cancel(); state.value = SupplierUiState() }
    private fun message(e: Exception) = if (e is SupplierFailure) e.message ?: "Supplier data unavailable." else "Unable to load Supplier data. Check your connection and try again."
}

class SupplierViewModel internal constructor(private val holderFactory: (CoroutineScope) -> SupplierStateHolder) : ViewModel() {
    private val holder = holderFactory(viewModelScope)
    val uiState = holder.uiState
    fun refresh() = holder.refresh()
    fun open(id: Long) = holder.open(id)
    fun closeDetail() = holder.closeDetail()
    fun create(draft: SupplyDraft) = holder.create(draft)
    fun update(id: Long, draft: SupplyDraft) = holder.update(id, draft)
    fun upload(id: Long, photo: SupplierPhoto) = holder.upload(id, photo)
    fun acknowledgePending() = holder.acknowledgePending()
    override fun onCleared() { holder.close() }
    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SupplierViewModel::class.java))
                @Suppress("UNCHECKED_CAST") return SupplierViewModel { scope -> SupplierStateHolder(
                    SupplierRepository(ApiClient.supplierApi, TokenManager, BuildConfig.API_BASE_URL), TokenManager, SupplierPreferences(app), scope) } as T
            }
        }
    }
}
