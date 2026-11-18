package com.example.farmapp.checkout

import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct

/** Farmer-as-purchaser operations are restricted to the signed-in Farmer's own cart and orders. */
internal object FarmerPurchaserPolicy : PurchaserPolicy {
    override val flow = PurchasingFlow.FARMER_FROM_SUPPLIER
    override val role = "FARMER"
    override val sellerType = "SUPPLIER"
    override val sector = "AGRI_INPUTS"
    override fun permits(operation: CheckoutOperation) = operation in setOf(
        CheckoutOperation.READ_CART, CheckoutOperation.UPLOAD_CART, CheckoutOperation.SET_QUANTITY,
        CheckoutOperation.START_CHECKOUT, CheckoutOperation.REQUEST_QUOTE, CheckoutOperation.CREATE_ORDER,
        CheckoutOperation.READ_ORDERS, CheckoutOperation.READ_PAYMENTS, CheckoutOperation.INITIATE_PAYMENT,
        CheckoutOperation.READ_ADDRESSES, CheckoutOperation.READ_PICKUP_STATIONS,
        CheckoutOperation.READ_LOGISTICS)
    override fun validateQuantity(product: CanonicalFarmerProduct, quantity: Int) =
        UnverifiedFarmerPurchaserPolicy.validateQuantity(product, quantity)
}

internal const val FARMER_CHECKOUT_BLOCKED = "Checkout is disabled until durable quotes and order reconciliation are verified on the deployed backend. Cart changes use the server; cart line readback is unavailable."
internal const val FARMER_PAYMENT_BLOCKED = "M-Pesa initiation is disabled: payment reconciliation and the payment environment must be confirmed. Unknown attempts must not be retried."
