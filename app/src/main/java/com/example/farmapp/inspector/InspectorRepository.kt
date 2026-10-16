package com.example.farmapp.inspector

import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.toAccount
import retrofit2.Response

class InspectorRepository(private val api: InspectorApi, private val sessions: SessionStore, private val origin: String) {
    internal fun owner(expected: SessionSnapshot): Long {
        val current = sessions.session.value
        val auth = current.auth
        if (current.generation != expected.generation || current.loggingOut || current.isDemo ||
            auth == null || auth.userId != expected.auth?.userId || auth.role != "INSPECTOR" ||
            auth.backendContract != "canonical" || auth.sessionOrigin != origin)
            throw InspectorFailure("Sign in with your shared-backend Inspector account.")
        return auth.userId.toLongOrNull()?.takeIf { it > 0 && it.toString() == auth.userId }
            ?: throw InspectorFailure("Inspector identity could not be verified.")
    }

    suspend fun jobs(expected: SessionSnapshot): List<Inspection> {
        val id = owner(expected)
        val jobs = read(api.inspections(id)).map { it.toInspection(id) }
        owner(expected)
        if (jobs.map { it.id }.distinct().size != jobs.size) throw InspectorFailure("Duplicate inspection records received.")
        return jobs.sortedWith(compareByDescending<Inspection> { it.createdAt }.thenByDescending { it.id })
    }
    suspend fun detail(expected: SessionSnapshot, id: Long): Inspection {
        val ownerId = owner(expected)
        if (id <= 0) throw InspectorFailure("Invalid inspection selection.")
        val job = read(api.inspection(id)).toInspection(ownerId)
        owner(expected)
        if (job.id != id) throw InspectorFailure("Inspection selection could not be verified.")
        return job
    }
    suspend fun profile(expected: SessionSnapshot): InspectorProfile {
        val id = owner(expected)
        val account = read(api.me()).toAccount()
        owner(expected)
        if (account.userId != id.toString() || account.role != "INSPECTOR") throw InspectorFailure("Inspector identity could not be verified.")
        val response = api.profile(id)
        val profile = if (response.code() == 404) { response.errorBody()?.close(); null } else read(response)
        owner(expected)
        if (profile != null && (profile.userId != id || profile.id == null || profile.id <= 0))
            throw InspectorFailure("Inspector profile ownership could not be verified.")
        return InspectorProfile(account, profile)
    }
    suspend fun evidence(expected: SessionSnapshot, id: Long): List<InspectorImageDto> {
        detail(expected, id) // Verify current assignment before accessing scoped metadata.
        val images = read(api.evidence(id))
        owner(expected)
        if (images.any { it.id == null || it.id <= 0 || it.url.isNullOrBlank() } ||
            images.map { it.id }.distinct().size != images.size)
            throw InspectorFailure("Evidence metadata could not be verified.")
        return images
    }
    suspend fun changeStatus(expected: SessionSnapshot, id: Long, status: InspectionStatus): Inspection {
        val current = detail(expected, id)
        if (status !in supportedStatusChanges(current.status)) throw InspectorFailure("This status is already recorded.", true)
        owner(expected)
        val updated = read(api.updateStatus(id, status.name, expected)).toInspection(owner(expected))
        if (updated.id != id) throw InspectorFailure("The returned inspection could not be verified. Refresh before continuing.")
        return updated
    }
    suspend fun submitFinding(expected: SessionSnapshot, id: Long, result: InspectionFinding, notes: String): Inspection {
        val valid = validateFindingNotes(notes)
        val current = detail(expected, id)
        if (current.result == result.name && current.notes?.trim() == valid)
            throw InspectorFailure("These findings are already recorded.", true)
        owner(expected)
        val request = InspectionFindingRequest(current.inspectorId, current.targetType, current.targetId,
            current.status.name, result.name, valid)
        val updated = read(api.updateResult(id, result.name, request, expected)).toInspection(owner(expected))
        if (updated.id != id) throw InspectorFailure("The returned inspection could not be verified. Refresh before continuing.")
        return updated // Result submission does not imply a status transition on this backend.
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw InspectorFailure(when (response.code()) {
                400, 422 -> "The server rejected these inspection values. Check the notes and selected status or result."
                401 -> "Your session could not be verified. Sign in again."
                403 -> "Access was denied. Ask your administrator to check Inspector permissions."
                404 -> "This inspection is no longer available. Refresh your assignments."
                409 -> "The inspection changed on the server. Reload it before continuing."
                429 -> "Too many requests. Wait before trying again."
                else -> "The inspection service is unavailable. Try again."
            }, rejected = response.code() in setOf(400, 401, 403, 404, 409, 413, 415, 422, 429))
        }
        return response.body()?.requireData() ?: throw InspectorFailure("Incomplete inspection response.")
    }
}
