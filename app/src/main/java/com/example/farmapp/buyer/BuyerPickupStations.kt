package com.example.farmapp.buyer

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*

internal data class BuyerPickupStation(val id: Long? = null, val name: String? = null, val address: String? = null,
    val latitude: Double? = null, val longitude: Double? = null, val region: String? = null, val isAvailable: Boolean? = null,
    val openingHours: String? = null, val contactPhone: String? = null, val pickupInstructions: String? = null)
internal data class BuyerSavedPickup(val id: Long? = null, val userId: Long? = null, val pickupStationId: Long? = null,
    val pickupStationName: String? = null, val pickupStationAddress: String? = null, val pickupStationRegion: String? = null,
    val pickupStationAvailable: Boolean? = null, val label: String? = null, val isDefault: Boolean? = null)
internal data class BuyerSavePickupRequest(val pickupStationId: Long, val label: String? = null, val isDefault: Boolean = false)
internal interface BuyerPickupApi {
    @GET("pickup-stations/availability") suspend fun availability(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<Boolean>>
    @GET("pickup-stations/available") suspend fun available(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerPickupStation>>>
    @GET("pickup-stations/available/region/{region}") suspend fun region(@Path("region") region: String, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerPickupStation>>>
    @GET("pickup-stations/{stationId}") suspend fun detail(@Path("stationId") stationId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerPickupStation>>
    @GET("pickup-stations/my-stations") suspend fun saved(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerSavedPickup>>>
    @POST("pickup-stations/my-stations") suspend fun save(@Body request: BuyerSavePickupRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerSavedPickup>>
    @DELETE("pickup-stations/my-stations/{userStationId}") suspend fun remove(@Path("userStationId") userStationId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
    @PUT("pickup-stations/my-stations/{userStationId}/default") suspend fun setDefault(@Path("userStationId") userStationId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerSavedPickup>>
}
internal data class BuyerPickupMutation(val kind: String, val platformId: Long, val userStationId: Long? = null,
    val label: String? = null, val isDefault: Boolean = false)
internal interface BuyerPickupStore {
    fun selectedPickup(): Long?
    fun selectPickup(id: Long?)
    fun pendingPickup(): BuyerPickupMutation?
    fun pendingPickup(value: BuyerPickupMutation?)
}
internal fun checkedPickup(row: BuyerPickupStation): BuyerPickupStation {
    check(row.id != null && row.id > 0 && !row.name.isNullOrBlank() && !row.address.isNullOrBlank() && !row.region.isNullOrBlank() && row.isAvailable != null)
    check(row.latitude == null || row.latitude.isFinite() && row.latitude in -90.0..90.0)
    check(row.longitude == null || row.longitude.isFinite() && row.longitude in -180.0..180.0)
    return row
}
internal fun checkedSavedPickups(rows: List<BuyerSavedPickup>, buyer: Long): List<BuyerSavedPickup> {
    check(rows.all { it.id != null && it.id > 0 && it.userId == buyer && it.pickupStationId != null && it.pickupStationId > 0 &&
        it.pickupStationAvailable != null && it.isDefault != null })
    check(rows.map { it.id }.distinct().size == rows.size && rows.map { it.pickupStationId }.distinct().size == rows.size && rows.count { it.isDefault == true } <= 1)
    return rows
}
internal data class BuyerPickupState(val busy: Boolean = false, val loaded: Boolean = false, val available: Boolean = false,
    val stations: List<BuyerPickupStation> = emptyList(), val allStations: List<BuyerPickupStation> = emptyList(),
    val saved: List<BuyerSavedPickup> = emptyList(), val region: String? = null, val selected: BuyerPickupStation? = null,
    val error: String? = null, val notice: String? = null, val uncertain: Boolean = false) {
    val regions get() = allStations.mapNotNull { it.region }.distinct().sorted()
}
internal fun pickupSelectionValid(state: BuyerPickupState) = state.loaded && !state.busy && state.error == null && state.available && state.selected?.isAvailable == true
internal class BuyerPickupController(private val api: BuyerPickupApi, private val sessions: SessionStore,
    private val expected: SessionSnapshot, private val store: BuyerPickupStore, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(BuyerPickupState())
    val state = mutable.asStateFlow()
    private fun current(): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId && now.auth.sessionOrigin == expected.auth?.sessionOrigin)
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw PickupFailure(when(response.code()) {
                401, 403 -> "Sign in again to load pickup stations."
                404 -> "Pickup stations are not available right now."
                else -> "Couldn't load pickup stations. Try again."
            }, response.code() in setOf(400, 401, 403, 404, 422))
        }
        return response.body()?.requireData() ?: error("Incomplete response")
    }
    private suspend fun ownSaved() = checkedSavedPickups(read(api.saved(current())), current().auth!!.userId.toLong())
    private fun stations(rows: List<BuyerPickupStation>): List<BuyerPickupStation> {
        rows.forEach(::checkedPickup); check(rows.map { it.id }.distinct().size == rows.size)
        return rows.filter { it.isAvailable == true }
    }
    private fun reconciled(p: BuyerPickupMutation, rows: List<BuyerSavedPickup>): Boolean = when(p.kind) {
        "SAVE" -> rows.any { it.pickupStationId == p.platformId && (p.label == null || it.label == p.label) && (!p.isDefault || it.isDefault == true) }
        "REMOVE" -> rows.none { it.id == p.userStationId }
        "DEFAULT" -> rows.any { it.id == p.userStationId && it.isDefault == true }
        else -> false
    }
    private suspend fun load(region: String?) {
        val available = read(api.availability(current()))
        val all = if (available) stations(read(api.available(current()))) else emptyList()
        val saved = ownSaved()
        val filtered = if (available && region != null) stations(read(api.region(region, current()))).also { rows ->
            check(rows.all { it.region.equals(region, ignoreCase = true) })
        } else all
        val pending = store.pendingPickup()
        val confirmed = pending != null && reconciled(pending, saved)
        if (confirmed) store.pendingPickup(null)
        val desired = if (confirmed && (pending?.kind == "DEFAULT" || pending?.kind == "SAVE" && pending.isDefault)) pending.platformId
            else if (confirmed && pending?.kind == "REMOVE" && pending.platformId == mutable.value.selected?.id) null
            else mutable.value.selected?.id ?: store.selectedPickup() ?: saved.firstOrNull { it.isDefault == true }?.pickupStationId
        val selected = desired?.takeIf { id -> available && all.any { it.id == id } }?.let { id ->
            checkedPickup(read(api.detail(id, current()))).also { check(it.id == id && it.isAvailable == true) }
        }
        current(); store.selectPickup(selected?.id)
        mutable.value = BuyerPickupState(busy = true, loaded = true, available = available, stations = filtered, allStations = all,
            saved = saved, region = region, selected = selected, uncertain = store.pendingPickup() != null,
            notice = if (store.pendingPickup() != null) "A station change is not confirmed. Refresh before trying another change." else if (confirmed) "Saved stations updated." else null)
    }
    fun refresh(region: String? = mutable.value.region) {
        if (mutable.value.busy || runCatching { current() }.isFailure) return
        if (region != null && region !in mutable.value.regions) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try { load(region) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (runCatching { current() }.isSuccess) mutable.update { it.copy(error = (e as? PickupFailure)?.message ?: "Couldn't verify pickup stations. Try again.") } }
            finally { if (runCatching { current() }.isSuccess) mutable.update { it.copy(busy = false) } }
        }
    }
    fun select(id: Long) {
        if (mutable.value.busy || !mutable.value.available || mutable.value.error != null || mutable.value.allStations.none { it.id == id }) return
        mutable.update { it.copy(busy = true) }
        scope.launch {
            try {
                val row = checkedPickup(read(api.detail(id, current()))); check(row.id == id && row.isAvailable == true)
                current(); store.selectPickup(id); mutable.update { it.copy(selected = row, notice = null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (runCatching { current() }.isSuccess) mutable.update { it.copy(selected = null, error = "This station couldn't be verified. Refresh and choose again.") } }
            finally { if (runCatching { current() }.isSuccess) mutable.update { it.copy(busy = false) } }
        }
    }
    fun save(id: Long, label: String, isDefault: Boolean) {
        if (mutable.value.allStations.none { it.id == id } || mutable.value.saved.any { it.pickupStationId == id }) return
        mutate(BuyerPickupMutation("SAVE", id, label = label.trim().ifBlank { null }, isDefault = isDefault))
    }
    fun remove(userStationId: Long) {
        val row = mutable.value.saved.firstOrNull { it.id == userStationId } ?: return
        mutate(BuyerPickupMutation("REMOVE", row.pickupStationId!!, userStationId))
    }
    fun setDefault(userStationId: Long) {
        val row = mutable.value.saved.firstOrNull { it.id == userStationId && it.pickupStationAvailable == true } ?: return
        mutate(BuyerPickupMutation("DEFAULT", row.pickupStationId!!, userStationId))
    }
    private fun mutate(pending: BuyerPickupMutation) {
        if (mutable.value.busy || mutable.value.uncertain || mutable.value.error != null || runCatching { current() }.isFailure) return
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            var sent = false
            try {
                check(store.pendingPickup() == null)
                if (pending.kind != "REMOVE") {
                    val station = checkedPickup(read(api.detail(pending.platformId, current())))
                    check(station.id == pending.platformId && station.isAvailable == true)
                }
                current(); store.pendingPickup(pending); sent = true
                when (pending.kind) {
                    "SAVE" -> checkedSavedPickups(listOf(read(api.save(BuyerSavePickupRequest(pending.platformId, pending.label, pending.isDefault), current()))), current().auth!!.userId.toLong()).also { check(it.single().pickupStationId == pending.platformId) }
                    "DEFAULT" -> checkedSavedPickups(listOf(read(api.setDefault(pending.userStationId!!, current()))), current().auth!!.userId.toLong()).also { check(it.single().id == pending.userStationId && it.single().isDefault == true) }
                    "REMOVE" -> {
                        val response = api.remove(pending.userStationId!!, current())
                        if (!response.isSuccessful) { response.errorBody()?.close(); throw PickupFailure("Station removal wasn't confirmed.", response.code() in setOf(400,401,403,404,422)) }
                        response.body()?.requireSuccess() ?: error("Missing response")
                    }
                }
                current(); load(mutable.value.region)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (runCatching { current() }.isSuccess) {
                    if (e is PickupFailure && e.rejected) runCatching { store.pendingPickup(null) }
                    mutable.update { it.copy(uncertain = runCatching { store.pendingPickup() != null }.getOrDefault(true),
                        error = if (sent) "Station change wasn't confirmed. Refresh saved stations." else (e as? PickupFailure)?.message ?: "Couldn't update this station. Try again.") }
                }
            } finally { if (runCatching { current() }.isSuccess) mutable.update { it.copy(busy = false) } }
        }
    }
    private class PickupFailure(message: String, val rejected: Boolean) : IllegalStateException(message)
}
