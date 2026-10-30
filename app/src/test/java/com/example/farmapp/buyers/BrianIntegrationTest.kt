package com.example.farmapp.buyers

import com.example.farmapp.buyers.data.CartViewModel
import com.example.farmapp.buyers.model.*
import com.example.farmapp.orders.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BrianIntegrationTest {
    private val row=BuyerOrderDto(105,42,"placed",BigDecimal("153.21"),"pending","2026-10-29T07:00:00Z",
        items=listOf(BuyerOrderItemDto(1,7,9,"Synthetic produce","kg",3,BigDecimal("1.07"),BigDecimal("3.21"))),
        subtotal=BigDecimal("3.21"),deliveryFee=BigDecimal("150.00"),deliveryAddress="Synthetic address",paymentMethod="mpesa")
    @Test fun preservesActualOrderPricesAndIdentifiers(){
        val display=brianOrder(row)
        assertEquals("105",display.id);assertEquals("7",display.items.single().product.id)
        assertEquals(BigDecimal("1.07"),display.items.single().product.pricePerUnit)
        assertEquals(BigDecimal("3.21"),display.items.single().lineSubtotal)
        assertEquals(row.total,display.total);assertEquals(row.deliveryFee,display.deliveryFee)
        assertEquals("Order",display.code);assertFalse(display.date.contains("T07:"))
    }
    @Test fun historicalOrderDoesNotDependOnCurrentCatalogOrInventImages(){
        val item=brianOrder(row).items.single()
        assertNull(item.product.imageUrl);assertNull(item.product.sellerName)
        assertEquals(StockStatus.OutOfStock,item.product.stockStatus)
    }
    @Test fun preservesMissingAmountsInsteadOfInventingFees(){
        val order=brianOrder(row.copy(subtotal=null,deliveryFee=null,deliveryAddress=null))
        assertNull(order.subtotal);assertNull(order.deliveryFee);assertEquals("",order.deliveryAddress)
        assertEquals("Unavailable",formatKes(null))
    }
    @Test fun currencyDoesNotRoundThroughFloat(){
        assertEquals("KES 1,234.57",formatKes(BigDecimal("1234.57")))
        assertEquals("KES 3.21",formatKes(BigDecimal("1.07").multiply(BigDecimal("3"))))
    }
    @Test fun cartFacadeOnlyDelegatesToExistingCart(){
        val calls=mutableListOf<String>()
        val vm=CartViewModel(BigDecimal("3.21"),{calls+="plus:$it"},{calls+="minus:$it"},{calls+="remove:$it"})
        vm.increment("7");vm.decrement("7");vm.remove("7")
        assertEquals(listOf("plus:7","minus:7","remove:7"),calls)
        assertEquals(BigDecimal("3.21"),vm.subtotal)
    }
    @Test fun sourceOrderStatusRemainsDistinctFromPayment(){
        val paid=brianOrder(row.copy(status="confirmed",paymentStatus="held"))
        assertEquals(OrderStatus.Confirmed,paid.status);assertEquals("held",paid.paymentStatus)
        assertEquals("pending",brianOrder(row).paymentStatus)
    }
    @Test fun disputedOrderDoesNotAppearPending(){
        assertEquals(OrderStatus.Disputed,brianOrder(row.copy(status="disputed")).status)
    }
    @Test fun currentQuoteUsesPickupLocationAndPreservesAuthoritativeMoney(){
        val request=com.example.farmapp.buyers.data.DeliveryQuoteRequest("PICKUP_STATION",pickupLocation="Synthetic location")
        val quote=com.example.farmapp.buyers.data.DeliveryQuote("synthetic",null,BigDecimal("3.21"),BigDecimal("50"),BigDecimal("53.21"),"KES","2026-10-29T12:15:00Z","PICKUP_STATION","Synthetic location")
        assertEquals(quote,com.example.farmapp.buyers.data.verifiedQuote(quote,request,java.time.Instant.parse("2026-10-29T12:00:00Z")))
        assertThrows(IllegalArgumentException::class.java){com.example.farmapp.buyers.data.verifiedQuote(quote,request,java.time.Instant.parse(quote.expiresAt))}
        assertThrows(IllegalArgumentException::class.java){com.example.farmapp.buyers.data.verifiedQuote(quote.copy(total=BigDecimal("83.21")),request,java.time.Instant.parse("2026-10-29T12:00:00Z"))}
        assertThrows(IllegalArgumentException::class.java){com.example.farmapp.buyers.data.DeliveryQuoteRequest("PICKUP_STATION",8,"Synthetic location")}
    }
}
