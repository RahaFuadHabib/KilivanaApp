package com.example.farmapp.orders

import com.example.farmapp.network.canonical.CanonicalJson
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BuyerOrderDesignTest {
    private val row = BuyerOrderDto(10, 42, "placed", BigDecimal("1370.25"), "pending", items = emptyList())
    @Test fun backendSummaryFieldsAndLineSubtotalsParseWithoutInventedValues() {
        val order = CanonicalJson.gson.fromJson("""{"id":10,"buyerId":42,"status":"placed","paymentStatus":"pending","total":1370.25,"subtotal":1250.25,"deliveryFee":120,"deliveryAddress":"Synthetic location","paymentMethod":"mpesa","items":[{"id":1,"productId":25,"sellerId":70,"quantity":5,"unitPrice":100.05,"subtotal":500.25}]}""", BuyerOrderDto::class.java)
        assertEquals(BigDecimal("1250.25"), order.subtotal)
        assertEquals(BigDecimal("120"), order.deliveryFee)
        assertEquals("Synthetic location", order.deliveryAddress)
        assertEquals("mpesa", order.paymentMethod)
        assertEquals(BigDecimal("500.25"), order.items!!.single().subtotal)
        assertNull(row.deliveryFee); assertEquals("Not available", orderMoney(row.deliveryFee))
    }
    @Test fun listFiltersUseRealOrderStatusesAndAllRetainsCancelled() {
        assertTrue(orderMatchesFilter(row, "Pending")); assertFalse(orderMatchesFilter(row, "Processing"))
        assertTrue(orderMatchesFilter(row.copy(status = "in_transit"), "Processing"))
        assertTrue(orderMatchesFilter(row.copy(status = "completed"), "Delivered"))
        assertTrue(orderMatchesFilter(row.copy(status = "cancelled"), "All"))
        assertFalse(orderMatchesFilter(row.copy(status = "cancelled"), "Pending"))
    }
    @Test fun paidOrderRequiresBackendConfirmationNotInitiationOrPartialPayment() {
        fun state(status: String, amount: String) = BuyerPaymentState(10, payments = listOf(PaymentDto(9,10,"mpesa",BigDecimal(amount),status)))
        assertFalse(orderPaymentConfirmed(row, state("pending", "1370.25")))
        assertFalse(orderPaymentConfirmed(row, state("held", "100.00")))
        assertFalse(orderPaymentConfirmed(row, state("cancelled", "1370.25")))
        assertTrue(orderPaymentConfirmed(row, state("held", "1370.25")))
        assertTrue(orderPaymentConfirmed(row.copy(paymentStatus = "paid"), null))
        assertFalse(orderPaymentConfirmed(row, state("paid", "1370.25").copy(orderId = 99)))
    }
    @Test fun moneyUsesKenyanLabelAndReadableDecimalGrouping() {
        assertEquals("KSh 1,370.25", orderMoney(BigDecimal("1370.25")))
        assertEquals("KSh 1,370", orderMoney(BigDecimal("1370.00")))
    }
}
