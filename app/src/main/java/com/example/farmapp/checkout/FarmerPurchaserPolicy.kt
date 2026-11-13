package com.example.farmapp.checkout

import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct

/** Owner-authorized cart integration uses documented purchaser endpoints and fails on HTTP 401/403.
 * Cart GET may create an ACTIVE cart. Order and payment activation remain independently gated.
 */
internal object FarmerPurchaserPolicy : PurchaserPolicy {
    override val flow = PurchasingFlow.FARMER_FROM_SUPPLIER
    override val role = "FARMER"
    override val sellerType = "SUPPLIER"
    override val sector = "AGRI_INPUTS"
    override fun permits(operation: CheckoutOperation) = operation in setOf(
        CheckoutOperation.READ_CART, CheckoutOperation.UPLOAD_CART, CheckoutOperation.SET_QUANTITY,
        CheckoutOperation.START_CHECKOUT, CheckoutOperation.REQUEST_QUOTE, CheckoutOperation.CREATE_ORDER,
        CheckoutOperation.READ_ORDERS, CheckoutOperation.READ_PAYMENTS,
        CheckoutOperation.READ_ADDRESSES, CheckoutOperation.READ_PICKUP_STATIONS,
        CheckoutOperation.READ_LOGISTICS)
    override fun validateQuantity(product: CanonicalFarmerProduct, quantity: Int) =
        UnverifiedFarmerPurchaserPolicy.validateQuantity(product, quantity)
}

internal const val FARMER_CHECKOUT_BLOCKED = "Checkout is disabled until durable quotes and order reconciliation are verified on the deployed backend. Cart changes use the server; cart line readback is unavailable."
internal const val FARMER_PAYMENT_BLOCKED = "M-Pesa initiation is disabled: payment reconciliation and the payment environment must be confirmed. Unknown attempts must not be retried."
