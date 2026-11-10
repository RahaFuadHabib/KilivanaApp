package com.example.farmapp.checkout

import com.example.farmapp.data.model.canonical.CanonicalProductStatus
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.toFarmerProduct
import com.example.farmapp.supplier.supplyDto
import com.example.farmapp.supplier.supplierAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CheckoutPolicyTest {
    private val inputs = supplyDto().copy(status="active").toFarmerProduct()
    private val produce = supplyDto().copy(sellerType="farmer", status="active").toFarmerProduct()
    private val buyer = SessionSnapshot(supplierAuth().copy(role="BUYER"), generation=7)

    private fun guard(expected: SessionSnapshot, actual: SessionSnapshot, policy: PurchaserPolicy): CheckoutSessionGuard {
        val flow = MutableStateFlow(actual)
        val store = object : SessionStore by signedInStore(supplierAuth()) {
            override val session: StateFlow<SessionSnapshot> = flow
        }
        return CheckoutSessionGuard(store, expected, policy)
    }

    @Test fun buyerAndFarmerSelectionPoliciesKeepTheirCataloguesSeparate() {
        assertEquals(1, BuyerPurchaserPolicy.validateQuantity(produce, 1))
        assertEquals(1, UnverifiedFarmerPurchaserPolicy.validateQuantity(inputs, 1))
        assertThrows(IllegalStateException::class.java) { BuyerPurchaserPolicy.validateQuantity(inputs, 1) }
        assertThrows(IllegalStateException::class.java) { UnverifiedFarmerPurchaserPolicy.validateQuantity(produce, 1) }
        assertEquals("FARMER", BuyerPurchaserPolicy.sellerType)
        assertEquals("SUPPLIER", UnverifiedFarmerPurchaserPolicy.sellerType)
    }

    @Test fun buyerQuantityRulesAndErrorMessagesRemainUnchanged() {
        val bounded = produce.copy(minimumOrderQuantity=BigDecimal("2"), availableQuantity=BigDecimal("3"))
        assertEquals(2, BuyerPurchaserPolicy.validateQuantity(bounded, 2))
        assertEquals(3, BuyerPurchaserPolicy.validateQuantity(bounded, 3))
        for (quantity in listOf(-1, 0, 1, 4)) {
            val failure = assertThrows(IllegalStateException::class.java) { BuyerPurchaserPolicy.validateQuantity(bounded, quantity) }
            assertEquals("Quantity must meet the minimum order and available stock.", failure.message)
        }
        val failure = assertThrows(IllegalStateException::class.java) {
            BuyerPurchaserPolicy.validateQuantity(produce.copy(status=CanonicalProductStatus.PENDING_APPROVAL), 1)
        }
        assertEquals("Only available Farmer produce can be added.", failure.message)
    }

    @Test fun allFarmerPurchaserApiOperationsRemainDeniedIncludingReadsAndStk() {
        val farmer = buyer.copy(auth=buyer.auth!!.copy(role="FARMER"))
        var operationsReached = 0
        for (operation in CheckoutOperation.entries) {
            assertFalse(UnverifiedFarmerPurchaserPolicy.permits(operation))
            assertThrows(IllegalStateException::class.java) {
                guard(farmer, farmer, UnverifiedFarmerPurchaserPolicy).current(operation)
                operationsReached++
            }
        }
        assertEquals(0, operationsReached)
    }

    @Test fun existingBuyerContractOperationsAcceptTheSameCurrentCanonicalSession() {
        for (operation in CheckoutOperation.entries) {
            assertTrue(BuyerPurchaserPolicy.permits(operation))
            assertSame(buyer, guard(buyer, buyer, BuyerPurchaserPolicy).current(operation))
        }
    }

    @Test fun staleAccountRoleOriginContractLogoutAndDemoSessionsAreRejected() {
        val changed = listOf(
            buyer.copy(generation=8), buyer.copy(loggingOut=true), buyer.copy(isDemo=true), buyer.copy(auth=null),
            buyer.copy(auth=buyer.auth!!.copy(userId="99")), buyer.copy(auth=buyer.auth.copy(role="FARMER")),
            buyer.copy(auth=buyer.auth.copy(sessionOrigin="https://other.test/api/v1/")),
            buyer.copy(auth=buyer.auth.copy(backendContract="legacy"))
        )
        for (snapshot in changed) assertThrows(IllegalStateException::class.java) {
            guard(buyer, snapshot, BuyerPurchaserPolicy).current(CheckoutOperation.CREATE_ORDER)
        }
        assertThrows(IllegalStateException::class.java) {
            guard(SessionSnapshot(), buyer, BuyerPurchaserPolicy).current(CheckoutOperation.CREATE_ORDER)
        }
    }

    @Test fun tokenRotationWithinTheSameSessionRemainsAllowed() {
        val rotated = buyer.copy(auth=buyer.auth!!.copy(accessToken="synthetic-rotated", refreshToken="synthetic-refresh"))
        assertSame(rotated, guard(buyer, rotated, BuyerPurchaserPolicy).current(CheckoutOperation.READ_CART))
    }
}
