package com.example.farmapp.checkout

import com.example.farmapp.network.canonical.CanonicalJson
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CheckoutCartIdentityTest {
    private val request=CheckoutQuoteRequest("DOOR_DELIVERY",8,cartId=10)
    private val line=CheckoutQuoteItem(7,1,BigDecimal("1.00"),BigDecimal("1.00"))
    private val quote=CheckoutQuote("synthetic",8,BigDecimal("1.00"),BigDecimal("1.00"),BigDecimal("2.00"),
        "KES","2026-11-10T12:15:00Z","DOOR_DELIVERY",cartId=10,items=listOf(line))

    @Test fun exactIntendedCartAndDecimalLinesAreAccepted() {
        val parsed=CanonicalJson.gson.fromJson(CanonicalJson.gson.toJson(quote),CheckoutQuote::class.java)
        verifiedCheckoutQuote(parsed,request,Instant.parse("2026-11-10T12:00:00Z"))
        verifyQuotedBasket(parsed,10,mapOf(7L to 1))
        val decimal=line.copy(quantity=3,unitPrice=BigDecimal("0.07"),lineSubtotal=BigDecimal("0.21"))
        verifyQuotedBasket(quote.copy(subtotal=BigDecimal("0.21"),items=listOf(decimal)),10,mapOf(7L to 3))
    }
    @Test fun wrongOrMissingCartCannotPassEqualSubtotal() {
        for(id in listOf(99L,null)) assertThrows(IllegalArgumentException::class.java) {
            verifiedCheckoutQuote(quote.copy(cartId=id),request,Instant.MIN)
        }
        assertThrows(IllegalArgumentException::class.java) { request.copy(cartId=0) }
    }
    @Test fun missingDuplicateWrongProductQuantityAndBrokenLineMathAreRejected() {
        val bad=listOf(null,emptyList(),listOf(line,line),listOf(line.copy(productId=99)),
            listOf(line.copy(quantity=2)),listOf(line.copy(lineSubtotal=BigDecimal("0.07"))),
            listOf(line.copy(unitPrice=BigDecimal("0.07"))))
        for(lines in bad) assertThrows(IllegalArgumentException::class.java) {
            verifyQuotedBasket(quote.copy(items=lines),10,mapOf(7L to 1))
        }
    }
    @Test fun journalPreservesBindingAndRejectsConflictingCartIdentity() {
        val journal=CheckoutJournal("QUOTE_READY",mapOf(7L to 1),BigDecimal("1.00"),10,quote,request)
        val saved=CheckoutJournalJson.decode(CheckoutJournalJson.encode(journal))
        assertEquals(10L,saved.request!!.cartId);assertEquals(quote,saved.quote)
        assertThrows(IllegalArgumentException::class.java) { CheckoutJournalJson.encode(journal.copy(cartId=99)) }
    }
}
