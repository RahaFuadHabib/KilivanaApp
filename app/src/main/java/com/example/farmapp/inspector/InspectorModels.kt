package com.example.farmapp.inspector

import com.example.farmapp.data.model.canonical.CanonicalAccount
import java.time.LocalDateTime

data class InspectionDto(
    val id: Long? = null, val inspectorId: Long? = null, val targetType: String? = null,
    val targetId: Long? = null, val status: String? = null, val result: String? = null,
    val notes: String? = null, val evidenceUrls: String? = null,
    val inspectedAt: String? = null, val createdAt: String? = null, val updatedAt: String? = null
)
data class InspectorImageDto(val id: Long? = null, val url: String? = null, val publicId: String? = null,
    val assetId: String? = null, val sortOrder: Int? = null, val isPrimary: Boolean? = null,
    val createdAt: String? = null, val updatedAt: String? = null)
data class InspectorProfileDto(val id: Long? = null, val userId: Long? = null,
    val inspectorDetails: String? = null, val specialization: String? = null, val assignedArea: String? = null,
    val status: String? = null, val createdAt: String? = null, val updatedAt: String? = null,
    val images: List<InspectorImageDto>? = null)

enum class InspectionStatus(val label: String) {
    ASSIGNED("Assigned"), PENDING("Pending"), IN_PROGRESS("In progress"), COMPLETED("Completed"), CANCELLED("Cancelled")
}
data class Inspection(val id: Long, val inspectorId: Long, val targetType: String, val targetId: Long,
    val status: InspectionStatus, val result: String?, val notes: String?,
    val inspectedAt: LocalDateTime?, val createdAt: LocalDateTime?, val updatedAt: LocalDateTime?)
data class InspectorProfile(val account: CanonicalAccount, val details: InspectorProfileDto?)
data class InspectionCounts(val assigned: Int, val pending: Int, val inProgress: Int, val completed: Int, val cancelled: Int)
fun inspectionCounts(jobs: List<Inspection>) = InspectionCounts(
    jobs.size, jobs.count { it.status == InspectionStatus.ASSIGNED || it.status == InspectionStatus.PENDING },
    jobs.count { it.status == InspectionStatus.IN_PROGRESS }, jobs.count { it.status == InspectionStatus.COMPLETED },
    jobs.count { it.status == InspectionStatus.CANCELLED })

class InspectorFailure(message: String, val rejected: Boolean = false) : java.io.IOException(message)
enum class InspectionFinding(val label: String) {
    APPROVED("Approved"), REJECTED("Rejected"), CHANGES_REQUIRED("Changes required")
}
data class InspectionFindingRequest(val inspectorId: Long, val targetType: String, val targetId: Long,
    val status: String, val result: String, val notes: String) {
    override fun toString() = "InspectionFindingRequest(redacted)"
}
/** The server accepts any declared status; it defines no terminal-state/transition matrix. */
fun supportedStatusChanges(current: InspectionStatus) = InspectionStatus.entries.filter { it != current }
fun validateFindingNotes(notes: String): String {
    val value = notes.trim()
    if (value.isEmpty() || value.length > 4000) throw InspectorFailure("Enter findings notes of 1 to 4000 characters.", true)
    return value
}
const val EVIDENCE_PRIVACY_BLOCKER = "Evidence uploads are unavailable until the backend provides private, assignment-authorized image delivery. Do not select or upload identity or land documents."
internal fun InspectionDto.toInspection(owner: Long): Inspection {
    if (id == null || id <= 0 || inspectorId != owner || targetId == null || targetId <= 0 || targetType.isNullOrBlank())
        throw InspectorFailure("Inspection ownership or response could not be verified.")
    val phase = InspectionStatus.entries.firstOrNull { it.name == status }
        ?: throw InspectorFailure("The server returned an unsupported inspection status.")
    if (result != null && result !in setOf("APPROVED", "REJECTED", "CHANGES_REQUIRED"))
        throw InspectorFailure("The server returned an unsupported inspection result.")
    fun date(raw: String?): LocalDateTime? = raw?.let {
        try { LocalDateTime.parse(it) } catch (_: java.time.DateTimeException) { throw InspectorFailure("Invalid inspection date.") }
    }
    return Inspection(id, owner, targetType, targetId, phase, result, notes, date(inspectedAt), date(createdAt), date(updatedAt))
}
