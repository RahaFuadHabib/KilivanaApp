package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.CreateProductRequest
import com.example.farmapp.data.model.FarmerProductResponse
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal fun CropDraft.toCreateProductRequest() = CreateProductRequest(
    name.trim(), description.trim(), unit.label, pricePerUnit.toBigDecimal(), quantity.toBigDecimal(), categoryId = requireNotNull(categoryId?.takeIf { it.isNotBlank() }) { "Select a category." })

/** Checkpoints must be persisted before returning, including before a non-idempotent create call. */
internal class CropPublisher(
    private val create: suspend (CreateProductRequest) -> String,
    private val upload: suspend (String, String) -> Unit,
    private val mine: suspend () -> List<FarmerProductResponse>
) {
    suspend fun publish(initial: CropDraft, checkpoint: (CropDraft) -> Unit,
        progress: (String) -> Unit): List<FarmerProductResponse> {
        require(initial.validationError() == null) { initial.validationError().orEmpty() }
        var draft = initial
        if (draft.productId == null) {
            check(!draft.creationUncertain) { "The previous creation result is unknown. Refresh My Crops to check whether it exists before starting another listing." }
            progress("Publishing product…")
            // Finish receiving and recording the ID even if the composable is removed during the request.
            withContext(NonCancellable) {
                draft = draft.copy(creationUncertain = true)
                checkpoint(draft)
                try {
                    val id = create(draft.toCreateProductRequest())
                    check(id.isNotBlank()) { "Missing product ID" }
                    draft = draft.copy(productId = id, creationUncertain = false)
                    checkpoint(draft)
                } catch (e: com.example.farmapp.repository.ProductFailure) {
                    // Only explicit server rejection is safe to retry automatically.
                    if (e.rejected) {
                        draft = draft.copy(creationUncertain = false)
                        checkpoint(draft)
                    }
                    throw e
                }
            }
        }
        for (index in draft.uploadedPhotos until draft.photos.size) {
            progress("Uploading photos ${index + 1}/${draft.photos.size}…")
            withContext(NonCancellable) {
                upload(requireNotNull(draft.productId), draft.photos[index])
                draft = draft.copy(uploadedPhotos = index + 1)
                checkpoint(draft)
            }
        }
        progress("Refreshing My Crops…")
        val products = mine()
        checkpoint(CropDraft())
        return products
    }
}
