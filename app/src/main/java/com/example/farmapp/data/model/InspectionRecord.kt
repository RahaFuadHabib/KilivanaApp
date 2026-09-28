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
    val notes: String,

    @SerializedName("parcelNumber")
    val parcelNumber: String = "LR-NAK-2024/88B",

    @SerializedName("gpsCoordinates")
    val gpsCoordinates: String = "-1.1824 S, 36.9312 E",

    @SerializedName("acreage")
    val acreage: Double = 5.4,

    @SerializedName("cropType")
    val cropType: String = "Hass Avocados"
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
                notes = "Organic orchard certification verified. Soil moisture and IPM standards fully compliant with KEPHIS export guidelines.",
                parcelNumber = "LR-NAK-2024/88B",
                gpsCoordinates = "-1.1824 S, 36.9312 E",
                acreage = 5.4,
                cropType = "Hass Avocados"
            ),
            InspectionRecord(
                id = 802L,
                farmId = 18L,
                farmerName = "Peter Kiprop",
                inspectionDate = "2026-09-18",
                documentVerified = true,
                status = "APPROVED",
                notes = "Cold chain handling facilities inspected. Traceability logs up to date for export batches.",
                parcelNumber = "LR-ELD-2023/14C",
                gpsCoordinates = "-1.1824 S, 36.9312 E",
                acreage = 12.0,
                cropType = "French Beans"
            ),
            InspectionRecord(
                id = 803L,
                farmId = 24L,
                farmerName = "Alice Chebet",
                inspectionDate = "2026-09-20",
                documentVerified = false,
                status = "PENDING_AUDIT",
                notes = "Awaiting updated water safety laboratory test results and grain storage fumigation certificate.",
                parcelNumber = "LR-KIT-2024/02A",
                gpsCoordinates = "-1.1824 S, 36.9312 E",
                acreage = 8.5,
                cropType = "Yellow Maize"
            ),
            InspectionRecord(
                id = 804L,
                farmId = 31L,
                farmerName = "David Njoroge",
                inspectionDate = "2026-09-21",
                documentVerified = true,
                status = "APPROVED",
                notes = "Pruning and shade tree ratio optimal. Processing facility meets Fair Trade environmental guidelines.",
                parcelNumber = "LR-NYE-2022/91D",
                gpsCoordinates = "-1.1824 S, 36.9312 E",
                acreage = 3.2,
                cropType = "Purple Tea Leaves"
            ),
            InspectionRecord(
                id = 805L,
                farmId = 45L,
                farmerName = "Hassan Abdi",
                inspectionDate = "2026-09-22",
                documentVerified = false,
                status = "PENDING_AUDIT",
                notes = "Field drainage adequate. Pending soil salinity analysis documentation from KALRO.",
                parcelNumber = "LR-KAJ-2024/73F",
                gpsCoordinates = "-1.1824 S, 36.9312 E",
                acreage = 15.0,
                cropType = "Red Bulb Onions"
            )
        )
    }
}

val mockInspectionRecords: List<InspectionRecord> get() = InspectionRecord.mockList
