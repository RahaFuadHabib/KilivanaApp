package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.data.remote.FarmApiService
import com.example.farmapp.network.SessionStore
import com.example.farmapp.repository.ProductFailure
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.math.BigDecimal
import java.io.IOException

fun CanonicalProductImageDto.toAndroidImage(): ProductImageResponse {
    val image = toImageView()
    return ProductImageResponse(image.imageId, null, null, null, image.url, image.sortOrder ?: 0)
}
fun CanonicalProductDto.toAndroidProduct(): FarmerProductResponse {
    val view = toFarmerProduct()
    return FarmerProductResponse(view.productId, view.name, view.description, view.unit, view.unitPrice,
        view.availableQuantity, images.orEmpty().sortedBy { it.sortOrder ?: 0 }.map { it.toAndroidImage() },
        view.status.name, view.categoryId, minimumOrderQuantity = view.minimumOrderQuantity,
        sellerId = view.sellerId, reservedQuantity = view.reservedQuantity)
}
fun canonicalWholeQuantity(value: BigDecimal): Int = try { canonicalStockQuantity(value) }
catch (_: CanonicalContractException) { throw ProductFailure("Enter a whole-number quantity within the supported range. Fractional quantities cannot be rounded.", true) }

class CanonicalFarmService(private val api: CanonicalBackendApi, private val sessions: SessionStore,
    private val download: suspend (String) -> Response<ResponseBody>) : FarmApiService {
    override val canonical = true
    private fun numericId(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw ProductFailure("The shared backend requires numeric IDs.", true)
    private fun farmerId(): Long {
        val auth = sessions.session.value.auth ?: throw IOException("Sign in first.")
        if (auth.backendContract != "canonical" || auth.role != "FARMER") throw IOException("A canonical farmer session is required.")
        return numericId(auth.userId)
    }
    override suspend fun farmerProfile() = api.me().mapped { it.toFarmerProfile() }
    override suspend fun updateFarmerPhone(request: UpdateFarmerPhoneRequest): Response<FarmerProfileResponse> {
        val current = api.me()
        if (!current.isSuccessful) return Response.error(current.code(), current.errorBody() ?: throw IOException("Missing error response"))
        val account = current.body()?.requireData()?.toAccount() ?: throw IOException("Missing profile")
        return api.updateProfile(CanonicalProfileUpdate(account.fullName, account.email, request.phoneNumber)).mapped { it.toFarmerProfile() }
    }
    override suspend fun categories(sector: String): Response<List<CategoryResponse>> {
        if (sector != "FARM_PRODUCE") throw IOException("Only farmer categories are connected.")
        return api.categories().mapped { list -> list.map { it.toCategory() }.filter { it.active && it.sector == sector } }
    }
    override suspend fun myProducts(): Response<List<FarmerProductResponse>> {
        val owner = farmerId()
        return api.products(owner).mapped { list ->
            if (list.any { it.sellerId != owner }) throw IOException("Unexpected product ownership")
            list.map { it.toAndroidProduct() }
        }
    }
    override suspend fun createProduct(request: CreateProductRequest): Response<FarmerProductResponse> {
        farmerId()
        if (request.lowStockThreshold != null) throw ProductFailure("Low-stock thresholds are not supported by the shared backend.", true)
        return api.create(CanonicalProductRequest(request.title, request.description, request.unit, request.unitPrice,
            canonicalWholeQuantity(request.availableQuantity), canonicalWholeQuantity(request.minimumOrderQuantity ?: BigDecimal.ONE),
            numericId(request.categoryId ?: throw ProductFailure("Choose a category.", true)))).mapped { it.toAndroidProduct() }
    }
    override suspend fun updateProduct(id: String, request: UpdateProductRequest): Response<FarmerProductResponse> {
        val owner = farmerId()
        val current = api.product(numericId(id))
        if (!current.isSuccessful) return Response.error(current.code(), current.errorBody() ?: throw IOException("Missing error response"))
        val dto = current.body()?.requireData() ?: throw IOException("Missing product")
        if (dto.sellerId != owner) throw ProductFailure("This crop belongs to another seller.", true)
        // UI edits sellable quantity. Preserve current reservations when writing total stock.
        val available = canonicalWholeQuantity(request.availableQuantity)
        val reserved = dto.reservedQty ?: throw IOException("Missing reserved stock")
        if (reserved < 0) throw IOException("Invalid reserved stock")
        val total = canonicalWholeQuantity(BigDecimal.valueOf(available.toLong()).add(BigDecimal.valueOf(reserved.toLong())))
        return api.update(numericId(id), CanonicalProductRequest(request.title, request.description, request.unit,
            request.unitPrice, total, canonicalWholeQuantity(request.minimumOrderQuantity ?: BigDecimal.ONE),
            numericId(request.categoryId ?: throw ProductFailure("Choose a category.", true)))).mapped { it.toAndroidProduct() }
    }
    override suspend fun productImages(productId: String) = api.images(numericId(productId)).mapped { it.map { image -> image.toAndroidImage() } }
    override suspend fun uploadProductImage(productId: String, file: MultipartBody.Part): Response<ProductImageResponse> {
        farmerId()
        val current = api.images(numericId(productId))
        if (!current.isSuccessful) return Response.error(current.code(), current.errorBody() ?: throw IOException("Missing error response"))
        val images = current.body()?.requireData() ?: throw IOException("Missing images")
        val filename = Regex("filename=\"([^\"]+)\"").find(file.headers?.get("Content-Disposition").orEmpty())?.groupValues?.get(1)
            ?: throw ProductFailure("Missing local photo filename.", true)
        val part = MultipartBody.Part.createFormData("image", filename, file.body)
        return api.upload(numericId(productId), part, (images.mapNotNull { it.sortOrder }.maxOrNull() ?: -1) + 1,
            images.isEmpty()).mapped { it.toAndroidImage() }
    }
    override suspend fun productImageContent(url: String) = download(url)
    override suspend fun productDetail(id: String): Response<FarmerProductResponse> =
        FarmerProductLifecycle(api, sessions).detail(numericId(id))
    override suspend fun changeProductLifecycle(id: String, action: FarmerProductAction): Response<Unit> =
        FarmerProductLifecycle(api, sessions).change(numericId(id), action)
    override suspend fun unlistProduct(id: String): Response<Void> {
        val response = changeProductLifecycle(id, FarmerProductAction.UNLIST)
        return if (response.isSuccessful) Response.success(null)
            else Response.error(response.code(), response.errorBody() ?: throw IOException("Missing error response"))
    }
    override suspend fun farmerOrders(): Response<List<FarmerOrderResponse>> = throw ProductFailure("Farmer orders are unavailable until a secure seller-scoped API exists.", true)
    override suspend fun getFarmers(): List<Farmer> = unavailable()
    override suspend fun getFarmerById(id: Long): Farmer = unavailable()
    override suspend fun addFarmer(farmer: Farmer): Farmer = unavailable()
    override suspend fun updateFarmer(id: Long, farmer: Farmer): Farmer = unavailable()
    override suspend fun deleteFarmer(id: Long): Response<Unit> = unavailable()
    override suspend fun searchFarmersByCounty(county: String): List<Farmer> = unavailable()
    private fun unavailable(): Nothing = throw IOException("Legacy farmer-roster operations are not connected.")
}
