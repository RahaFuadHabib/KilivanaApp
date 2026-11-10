package com.example.farmapp.checkout

import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import com.example.farmapp.data.model.canonical.CanonicalProductStatus
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import java.math.BigDecimal

/** Contract eligibility only; payment initiation still requires its existing environment guard. */
internal enum class CheckoutOperation {
    READ_CART, UPLOAD_CART, SET_QUANTITY, START_CHECKOUT, REQUEST_QUOTE, CREATE_ORDER,
    READ_ORDERS, READ_PAYMENTS, INITIATE_PAYMENT, READ_ADDRESSES, MANAGE_ADDRESSES,
    READ_PICKUP_STATIONS, MANAGE_PICKUP_STATIONS, READ_LOGISTICS
}

internal sealed interface PurchaserPolicy {
    val flow: PurchasingFlow
    val role: String
    val sellerType: String
    val sector: String
    fun permits(operation: CheckoutOperation): Boolean
    fun validateQuantity(product: CanonicalFarmerProduct, quantity: Int): Int
}

internal object BuyerPurchaserPolicy : PurchaserPolicy {
    override val flow = PurchasingFlow.BUYER_FROM_FARMER
    override val role = "BUYER"
    override val sellerType = "FARMER"
    override val sector = "FARM_PRODUCE"
    override fun permits(operation: CheckoutOperation) = true
    override fun validateQuantity(product: CanonicalFarmerProduct, quantity: Int): Int {
        check(product.sector == sector && product.status == CanonicalProductStatus.ACTIVE) { "Only available Farmer produce can be added." }
        return checkedQuantity(product, quantity)
    }
}

/** Supplier catalogue policy is known; NO Farmer purchaser API permission is assumed. */
internal object UnverifiedFarmerPurchaserPolicy : PurchaserPolicy {
    override val flow = PurchasingFlow.FARMER_FROM_SUPPLIER
    override val role = "FARMER"
    override val sellerType = "SUPPLIER"
    override val sector = "AGRI_INPUTS"
    override fun permits(operation: CheckoutOperation) = false
    override fun validateQuantity(product: CanonicalFarmerProduct, quantity: Int): Int {
        check(product.sector == sector && product.status == CanonicalProductStatus.ACTIVE) { "Only available Supplier inputs can be selected." }
        return checkedQuantity(product, quantity)
    }
}

private fun checkedQuantity(product: CanonicalFarmerProduct, quantity: Int): Int {
    check(quantity > 0 && BigDecimal.valueOf(quantity.toLong()) >= product.minimumOrderQuantity &&
        BigDecimal.valueOf(quantity.toLong()) <= product.availableQuantity) { "Quantity must meet the minimum order and available stock." }
    return quantity
}

/** No transport calls. Future repositories must check this before every operation. */
internal class CheckoutSessionGuard(
    private val sessions: SessionStore,
    private val expected: SessionSnapshot,
    private val policy: PurchaserPolicy
) {
    fun current(operation: CheckoutOperation): SessionSnapshot {
        val now = sessions.session.value
        val auth = now.auth
        check(expected.auth != null && auth != null && now.generation == expected.generation &&
            !now.loggingOut && !now.isDemo && auth.role == policy.role && auth.role == expected.auth.role &&
            auth.backendContract == "canonical" && auth.backendContract == expected.auth.backendContract &&
            auth.userId == expected.auth.userId && auth.sessionOrigin == expected.auth.sessionOrigin) {
            "Sign in again to continue checkout."
        }
        check(policy.permits(operation)) { "Purchasing permissions for this account have not been verified." }
        return now
    }
}
