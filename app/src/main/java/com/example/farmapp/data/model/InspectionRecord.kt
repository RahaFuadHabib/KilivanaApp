package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing agricultural quality, safety, and compliance inspections.
 *
 * @property id Unique inspection record identifier.
 * @property farmId Identifier of the farm audited.
 * @property farmerName Name of the inspected farmer.
 * @property inspectionDate Date of inspection formatted as ISO string (YYYY-MM-DD).
 * @property documentVerified Indicates whether statutory documentation was verified.
 * @property status Audit outcome status (e.g. "APPROVED", "PENDING_AUDIT", "REJECTED").
 * @property notes Inspector field observations and recommendations.
 */
data class InspectionRecord(
    @SerializedName("id")
    val id: Long = 0L,

    @SerializedName("farmId")
    val farmId: Long,

    @SerializedName("farmerName")
    val farmerName: String,

    @SerializedName("inspectionDate")
    val inspectionDate: String,

    @SerializedName("documentVerified")
    val documentVerified: Boolean,

    @SerializedName("status")
    val status: String,

    @SerializedName("notes")
    val notes: String
) {
    companion object {
        val mockList: List<InspectionRecord> = listOf(
            InspectionRecord(
                id = 801L,
                farmId = 12L,
                farmerName = "Wanjiku Mwangi",
                inspectionDate = "2026-09-15",
                documentVerified = true,
                status = "APPROVED",
                notes = "Organic orchard certification verified. Soil moisture and IPM standards fully compliant with KEPHIS export guidelines."
            ),
            InspectionRecord(
                id = 802L,
                farmId = 18L,
                farmerName = "Peter Kiprop",
                inspectionDate = "2026-09-18",
                documentVerified = true,
                status = "APPROVED",
                notes = "Cold chain handling facilities inspected. Traceability logs up to date for export batches."
            ),
            InspectionRecord(
                id = 803L,
                farmId = 24L,
                farmerName = "Alice Chebet",
                inspectionDate = "2026-09-20",
                documentVerified = false,
                status = "PENDING_AUDIT",
                notes = "Awaiting updated water safety laboratory test results and grain storage fumigation certificate."
            ),
            InspectionRecord(
                id = 804L,
                farmId = 31L,
                farmerName = "David Njoroge",
                inspectionDate = "2026-09-21",
                documentVerified = true,
                status = "APPROVED",
                notes = "Pruning and shade tree ratio optimal. Processing facility meets Fair Trade environmental guidelines."
            ),
            InspectionRecord(
                id = 805L,
                farmId = 45L,
                farmerName = "Hassan Abdi",
                inspectionDate = "2026-09-22",
                documentVerified = false,
                status = "PENDING_AUDIT",
                notes = "Field drainage adequate. Pending soil salinity analysis documentation from KALRO."
            )
        )
    }
}

val mockInspectionRecords: List<InspectionRecord> get() = InspectionRecord.mockList
