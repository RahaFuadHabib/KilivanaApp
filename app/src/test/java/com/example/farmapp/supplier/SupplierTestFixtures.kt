package com.example.farmapp.supplier

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

internal const val SUPPLIER_TEST_ORIGIN = "https://supplier.example.test/api/v1/"
internal fun supplierAuth(id: String = "42") = testAuth(role = "SUPPLIER").copy(userId = id, backendContract = "canonical",
    sessionOrigin = SUPPLIER_TEST_ORIGIN, refreshTokenExpiresAt = "", verificationStatus = "NOT_REQUIRED", accessTokenExpiresAt = java.time.Instant.now().plusSeconds(3600).toString())
internal fun supplierStore() = signedInStore(supplierAuth())
internal fun supplierUser(id: Long = 42) = CanonicalUserDto(id, "Synthetic Supplier", "supplier@example.test", "+254700000000",
    role = "SUPPLIER", status = "ACTIVE", verificationStatus = "NOT_REQUIRED")
internal fun supplyDto(id: Long = 7, owner: Long = 42) = CanonicalProductDto(id, owner, "supplier", 2,
    "Synthetic seed", "Synthetic description", "bag", BigDecimal("125.50"), 12, 2, 0, 1, "pending", images = emptyList())
internal fun supplyDraft() = SupplyDraft("Synthetic seed", "Synthetic description", "bag", "125.50", "10", "1", 2)
internal fun supplyPhoto() = SupplierPhoto(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0), filename = "synthetic.jpg")
internal class MemorySupplierOperations : SupplierOperationStore {
    val values = ConcurrentHashMap<String, PendingSupplyOperation>()
    override fun read(accountKey: String) = values[accountKey]
    override fun write(accountKey: String, pending: PendingSupplyOperation?) { if (pending == null) values.remove(accountKey) else values[accountKey] = pending }
}
internal open class StubSupplierApi : SupplierApi {
    override suspend fun orders(supplierId: Long, page: Int, size: Int, session: SessionSnapshot) =
        Response.success(CanonicalApiResponse(true, data = SupplierOrderPageDto(emptyList(), page, size, true, 0, 0)))
    var account = supplierUser()
    var business: SupplierProfileDto? = SupplierProfileDto(3, 42, "Synthetic input business", "Synthetic details", "Synthetic area")
    var categoryValues = listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true))
    var values = listOf(supplyDto())
    var current = supplyDto()
    var imageValues = emptyList<CanonicalProductImageDto>()
    var writes = 0
    var ownerRequested: Long? = null
    var request: CanonicalProductRequest? = null
    var part: MultipartBody.Part? = null
    var sortOrder: Int? = null
    var primary: Boolean? = null
    var beforeCreate: suspend () -> Unit = {}
    var beforeUpload: suspend () -> Unit = {}
    override suspend fun me(session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = account))
    override suspend fun profile(id: Long, session: SessionSnapshot) = business?.let { Response.success(CanonicalApiResponse(true, data = it)) }
        ?: Response.error(404, "{}".toResponseBody())
    override suspend fun categories(session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = categoryValues))
    override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductDto>>> {
        ownerRequested = id; return Response.success(CanonicalApiResponse(true, data = values))
    }
    override suspend fun listing(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = current))
    override suspend fun create(request: CanonicalProductRequest, session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>> {
        writes++; this.request = request; beforeCreate(); values = listOf(current); return Response.success(CanonicalApiResponse(true, data = current))
    }
    override suspend fun update(id: Long, request: CanonicalProductRequest, session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>> {
        writes++; this.request = request; current = current.copy(name = request.name, stockQty = request.stockQty)
        return Response.success(CanonicalApiResponse(true, data = current))
    }
    override suspend fun images(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = imageValues))
    override suspend fun upload(id: Long, image: MultipartBody.Part, sortOrder: Int, primary: Boolean, session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductImageDto>> {
        writes++; part = image; this.sortOrder = sortOrder; this.primary = primary; beforeUpload()
        val result = CanonicalProductImageDto(9, "/api/v1/images/9", "9", sortOrder = sortOrder, isPrimary = primary)
        imageValues = imageValues + result; current = current.copy(images = imageValues)
        return Response.success(CanonicalApiResponse(true, data = result))
    }
}
