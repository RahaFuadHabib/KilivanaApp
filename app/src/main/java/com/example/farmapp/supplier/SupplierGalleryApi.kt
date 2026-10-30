package com.example.farmapp.supplier

import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.canonical.CanonicalApiResponse
import retrofit2.Response
import retrofit2.http.*

interface SupplierGalleryApi {
    @DELETE("products/{productId}/images/{imageId}") suspend fun delete(@Path("productId") productId: Long,
        @Path("imageId") imageId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
    @PUT("products/{productId}/images/{imageId}/primary") suspend fun primary(@Path("productId") productId: Long,
        @Path("imageId") imageId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
    @PUT("products/{productId}/images/reorder") suspend fun reorder(@Path("productId") productId: Long,
        @Body imageIds: List<Long>, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
}
enum class GalleryChange { DELETE, PRIMARY, MOVE_UP }
data class GalleryAction(val imageId: Long, val change: GalleryChange)
