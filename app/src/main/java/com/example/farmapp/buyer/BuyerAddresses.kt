package com.example.farmapp.buyer

import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Tag

internal data class BuyerAddressDto(val id: Long? = null, val userId: Long? = null, val label: String? = null,
    val addressText: String? = null, val latitude: Double? = null, val longitude: Double? = null)
internal data class BuyerAddressRequest(val addressText: String, val label: String? = null,
    val latitude: Double? = null, val longitude: Double? = null) {
    override fun toString() = "BuyerAddressRequest(redacted)"
}
internal fun buyerAddressRequest(label: String, text: String, latitude: String = "", longitude: String = ""): BuyerAddressRequest {
    require(text.isNotBlank())
    require(latitude.isBlank() == longitude.isBlank())
    val lat = latitude.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val lon = longitude.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    require(latitude.isBlank() || lat != null && lat.isFinite() && lat in -90.0..90.0)
    require(longitude.isBlank() || lon != null && lon.isFinite() && lon in -180.0..180.0)
    return BuyerAddressRequest(text.trim(), label.trim().ifBlank { null }, lat, lon)
}
internal interface BuyerDeliveryStore {
    fun selectedAddress(): Long?
    fun selectAddress(id: Long?)
    fun addressSavePending(): Boolean
    fun markAddressSave(pending: Boolean)
}
internal interface BuyerAddressApi {
    @POST("users/addresses") suspend fun create(@Body request: BuyerAddressRequest,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerAddressDto>>
    @GET("users/addresses") suspend fun list(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerAddressDto>>>
}
internal fun validateBuyerAddresses(rows: List<BuyerAddressDto>, userId: Long): List<BuyerAddressDto> {
    check(rows.all { row -> row.id != null && row.id > 0 && row.userId == userId && !row.addressText.isNullOrBlank() &&
        (row.latitude == null || row.latitude.isFinite() && row.latitude in -90.0..90.0) &&
        (row.longitude == null || row.longitude.isFinite() && row.longitude in -180.0..180.0) })
    check(rows.map { it.id }.distinct().size == rows.size)
    return rows
}
internal data class BuyerAddressState(val rows: List<BuyerAddressDto> = emptyList(), val busy: Boolean = false,
    val loaded: Boolean = false, val error: String? = null, val selectedId: Long? = null,
    val saving: Boolean = false, val saveUncertain: Boolean = false, val saveError: String? = null, val createdId: Long? = null)
internal class BuyerAddressController(private val api: BuyerAddressApi, private val sessions: SessionStore,
    private val expected: SessionSnapshot, private val scope: CoroutineScope, private val deliveryStore: BuyerDeliveryStore? = null) {
    private val mutable = MutableStateFlow(BuyerAddressState())
    val state = mutable.asStateFlow()
    private fun current(): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId &&
            now.auth.sessionOrigin == expected.auth?.sessionOrigin)
        return now
    }
    fun refresh() {
        if (mutable.value.busy || mutable.value.saving || runCatching { current() }.isFailure) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                val response = api.list(current())
                if (!response.isSuccessful) {
                    response.errorBody()?.close()
                    throw AddressReadFailure(when(response.code()) {
                        401, 403 -> "Sign in again to load your addresses."
                        404 -> "Address service is unavailable. Try again."
                        else -> "Couldn't load your addresses. Try again."
                    })
                }
                val rows = validateBuyerAddresses(response.body()?.requireData() ?: error("Missing addresses"), current().auth!!.userId.toLong())
                current()
                var preferenceFailure = false
                val stored = runCatching { deliveryStore?.selectedAddress() }.getOrElse { preferenceFailure = true; null }
                val selected = (mutable.value.selectedId ?: stored)?.takeIf { id -> rows.any { it.id == id } }
                if (!preferenceFailure) runCatching { deliveryStore?.selectAddress(selected) }.onFailure { preferenceFailure = true }
                val pending = runCatching { deliveryStore?.addressSavePending() ?: mutable.value.saveUncertain }
                    .getOrElse { preferenceFailure = true; true }
                mutable.update { it.copy(rows = rows, loaded = true, error = null, selectedId = selected.takeUnless { preferenceFailure },
                    saveError = if (preferenceFailure) "Couldn't save the selected address. Please select it again." else null,
                    saveUncertain = pending) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (runCatching { current() }.isSuccess) mutable.update { it.copy(error = when(e) {
                    is AddressReadFailure -> e.message
                    is java.io.IOException -> "Couldn't connect to the address service. Try again."
                    else -> "Couldn't verify your addresses. Try again."
                }) }
            } finally { if (runCatching { current() }.isSuccess) mutable.update { it.copy(busy = false) } }
        }
    }
    private class AddressReadFailure(message: String) : IllegalStateException(message)
    fun select(id: Long) {
        if (!buyerDeliverySelectionValid(mutable.value, id) || runCatching { current() }.isFailure) return
        try {
            deliveryStore?.selectAddress(id)
            mutable.update { it.copy(selectedId = id, saveError = null) }
        } catch (_: Exception) { mutable.update { it.copy(saveError = "Location selection could not be saved. Try again.") } }
    }
    fun create(request: BuyerAddressRequest) {
        if (mutable.value.saving || mutable.value.busy || mutable.value.saveUncertain || runCatching { current() }.isFailure) return
        try { buyerAddressRequest(request.label.orEmpty(), request.addressText,
            request.latitude?.toString().orEmpty(), request.longitude?.toString().orEmpty()) }
        catch (_: Exception) { mutable.update { it.copy(saveError = "Enter a full address and valid optional coordinates.") }; return }
        mutable.update { it.copy(saving = true, saveError = null, createdId = null) }
        scope.launch {
            var attempted = false
            try {
                current()
                check(deliveryStore?.addressSavePending() != true)
                deliveryStore?.markAddressSave(true)
                attempted = true
                val response = api.create(request, current())
                if (!response.isSuccessful) {
                    response.errorBody()?.close()
                    if (response.code() in setOf(400, 401, 403, 404, 422)) {
                        deliveryStore?.markAddressSave(false)
                        attempted = false
                    }
                    error("Save not confirmed")
                }
                val row = validateBuyerAddresses(listOf(response.body()?.requireData() ?: error("Missing saved address")), current().auth!!.userId.toLong()).single()
                current()
                deliveryStore?.selectAddress(row.id)
                deliveryStore?.markAddressSave(false)
                mutable.update { it.copy(rows = (it.rows.filterNot { old -> old.id == row.id } + row), loaded = true,
                    error = null, selectedId = row.id, createdId = row.id, saveUncertain = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (runCatching { current() }.isSuccess) mutable.update { it.copy(saveUncertain = attempted,
                saveError = if (attempted) "Address save was not confirmed. Refresh saved addresses before taking further action; do not repeat the save."
                    else "Address could not be saved. Check your session and local storage.") } }
            finally { if (runCatching { current() }.isSuccess) mutable.update { it.copy(saving = false) } }
        }
    }
}
internal fun buyerDeliverySelectionValid(state: BuyerAddressState, id: Long? = state.selectedId): Boolean =
    state.loaded && !state.busy && !state.saving && state.error == null && id != null &&
        state.rows.any { it.id == id && !it.addressText.isNullOrBlank() }

