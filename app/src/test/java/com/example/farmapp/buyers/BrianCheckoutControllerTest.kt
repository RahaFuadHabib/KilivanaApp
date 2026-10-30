package com.example.farmapp.buyers

import com.example.farmapp.buyers.data.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.orders.*
import com.example.farmapp.supplier.supplierAuth
import com.google.gson.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant
import java.lang.reflect.Proxy

class BrianCheckoutControllerTest {
    private class Disk(var value:CheckoutJournal=CheckoutJournal()):CheckoutJournalStore {
        var fail=false
        override fun read()=value
        override fun write(value:CheckoutJournal){check(!fail);this.value=value}
    }
    private class Api:BrianCheckoutApi {
        var adds=0;var updates=0;var starts=0;var quotes=0;var creates=0;var processing=false
        var conflict=false;var uploadTimeout=false;var orderTimeout=false;var readTimeout=false;var reject=0
        var hold:CompletableDeferred<Unit>?=null
        var quote=DeliveryQuote("synthetic-quote",8,BigDecimal("3.21"),BigDecimal("150"),BigDecimal("153.21"),"KES","2026-10-29T12:15:00Z","DOOR_DELIVERY")
        fun metadata()=JsonParser.parseString("""{"id":10,"buyerId":42,"status":"${if(processing)"CHECKOUT_PROCESSING" else "ACTIVE"}"}""").asJsonObject
        override suspend fun cart(buyerId:Long,session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>> {
            assertEquals(42,buyerId);if(readTimeout)throw java.net.SocketTimeoutException()
            return Response.success(CanonicalApiResponse(true,data=metadata()))
        }
        override suspend fun add(item:CartUpload,session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>> {
            adds++;assertEquals(CartUpload(7,3),item);hold?.await()
            if(uploadTimeout)throw java.net.SocketTimeoutException()
            if(conflict || reject!=0)return Response.error(if(conflict)409 else reject,"synthetic rejection".toResponseBody())
            return Response.success(CanonicalApiResponse(true,data=metadata()))
        }
        override suspend fun quantity(cartId:Long,productId:Long,quantity:Int,session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>> {
            updates++;assertEquals(10,cartId);assertEquals(7,productId);assertEquals(3,quantity)
            return Response.success(CanonicalApiResponse(true,data=metadata()))
        }
        override suspend fun checkout(session:SessionSnapshot):Response<CanonicalApiResponse<Unit>> {
            starts++;processing=true;return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun quote(request:DeliveryQuoteRequest,session:SessionSnapshot):Response<CanonicalApiResponse<DeliveryQuote>> {
            quotes++;assertEquals(DeliveryQuoteRequest("DOOR_DELIVERY",8),request)
            return Response.success(CanonicalApiResponse(true,data=quote))
        }
        override suspend fun order(request:QuoteOrderRequest,session:SessionSnapshot):Response<CanonicalApiResponse<JsonElement>> {
            creates++;assertEquals(QuoteOrderRequest("synthetic-quote","254712345678"),request)
            if(orderTimeout)throw java.net.SocketTimeoutException()
            return Response.success(CanonicalApiResponse(true,data=JsonPrimitive(105)))
        }
    }
    private class Fixture {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val sessions=signedInStore(supplierAuth().copy(role="BUYER"));val api=Api();val disk=Disk()
        var now=Instant.parse("2026-10-29T12:00:00Z");var orderReads=0;var paymentWrites=0;var failOrderRead=false
        var rows=emptyList<BuyerOrderDto>()
        var order=BuyerOrderDto(105,42,"placed",BigDecimal("153.21"),"pending",items=listOf(BuyerOrderItemDto(1,7,9,"Synthetic produce","kg",3,BigDecimal("1.07"),BigDecimal("3.21"))),subtotal=BigDecimal("3.21"),deliveryFee=BigDecimal("150"),addressId=8)
        val orders=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(BuyerOrdersApi::class.java)){_,m,_->when(m.name){
            "orders"->Response.success(CanonicalApiResponse(true,data=rows))
            "order"->{orderReads++;if(failOrderRead)throw java.net.SocketTimeoutException();Response.success(CanonicalApiResponse(true,data=order))}
            "initiateBuyerStk"->{paymentWrites++;error("Checkout must never call STK")}
            else->error("Unexpected API: ${m.name}")
        }} as BuyerOrdersApi
        fun controller()=BrianCheckoutController(api,orders,sessions,sessions.session.value,disk,scope){now}
        val controller=controller()
        fun prepare()=controller.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        fun create()=controller.create(mapOf(7L to 3),"synthetic-quote","0712345678")
    }
    private fun scenario(block:suspend Fixture.()->Unit)=runBlocking {val f=Fixture();try{f.block()}finally{f.scope.cancel()}}
    @Test fun metadataOnlyCartCanReachVerifiedOrderWithoutInitiatingPayment()=scenario {
        prepare();assertNotNull(controller.state.value.quote);assertEquals(1,api.adds);assertEquals(1,api.starts);assertEquals(0,api.creates)
        create();assertEquals(105L,controller.state.value.order?.id);assertEquals("254712345678",controller.state.value.phone)
        assertEquals(1,api.creates);assertEquals(0,paymentWrites);assertEquals("ORDER_VERIFIED",disk.value.phase)
    }
    @Test fun knownConflictSetsExactQuantityWithoutRepeatingPost()=scenario {
        api.conflict=true;prepare();assertEquals(1,api.adds);assertEquals(1,api.updates);assertNotNull(controller.state.value.quote)
    }
    @Test fun rapidPrepareTapsNeverDuplicateUploads()=scenario {
        api.hold=CompletableDeferred();repeat(5){prepare()};assertEquals(1,api.adds)
        api.hold!!.complete(Unit);assertEquals(1,api.starts)
    }
    @Test fun uploadTimeoutPersistsUncertaintyAcrossRestartWithoutReplay()=scenario {
        api.uploadTimeout=true;prepare();assertTrue(controller.state.value.uncertain)
        val restarted=controller();restarted.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8));restarted.recover()
        assertEquals(1,api.adds);assertEquals(0,api.starts);assertEquals(0,api.creates)
    }
    @Test fun duplicateOrderTapsAndRestartsCannotCreateAgain()=scenario {
        prepare();repeat(5){create()};assertEquals(1,api.creates)
        controller().create(mapOf(7L to 3),"synthetic-quote","0712345678");assertEquals(1,api.creates)
    }
    @Test fun unknownOrderCreationReconcilesButNeverAdoptsSimilarOrderOrRetries()=scenario {
        prepare();api.orderTimeout=true;create();rows=listOf(order)
        val restarted=controller();restarted.recover();restarted.create(mapOf(7L to 3),"synthetic-quote","0712345678")
        assertEquals(1,api.creates);assertNull(restarted.state.value.order);assertTrue(restarted.state.value.uncertain)
    }
    @Test fun returnedOrderIdSurvivesReadTimeoutAndIsRecoveredWithoutAnotherCreate()=scenario {
        prepare();failOrderRead=true;create();assertEquals(105L,disk.value.orderId)
        failOrderRead=false;val restarted=controller();restarted.recover()
        assertEquals(105L,restarted.state.value.order?.id);assertEquals(1,api.creates);assertEquals(0,paymentWrites)
    }
    @Test fun expiryAndChangedCartPreventOrderCreation()=scenario {
        prepare();controller.create(mapOf(7L to 2),"synthetic-quote","0712345678");assertEquals(0,api.creates)
        now=Instant.parse(api.quote.expiresAt);create();assertEquals(0,api.creates)
    }
    @Test fun differentServerSubtotalPreventsOrderCreation()=scenario {
        api.quote=api.quote.copy(subtotal=BigDecimal("4.21"),total=BigDecimal("154.21"));prepare()
        assertNull(controller.state.value.quote);create();assertEquals(0,api.creates)
    }
    @Test fun wrongOrderProductsOrAmountsNeverReachPayment()=scenario {
        prepare();order=order.copy(items=order.items!!.map{it.copy(quantity=4)})
        create();assertNull(controller.state.value.order);assertEquals(0,paymentWrites);assertEquals("ORDER_RETURNED",disk.value.phase)
    }
    @Test fun wrongDeliveryAddressAndExtraProductFailVerification()=scenario {
        assertThrows(IllegalStateException::class.java){verifyPurchasedOrder(order.copy(addressId=9),42,mapOf(7L to 3),api.quote)}
        assertThrows(IllegalStateException::class.java){verifyPurchasedOrder(order.copy(items=order.items!!+order.items!!.single().copy(id=2,productId=8)),42,mapOf(7L to 3),api.quote)}
    }
    @Test fun failedDurableReservationSendsNoWrites()=scenario {
        disk.fail=true;prepare();assertEquals(0,api.adds);assertEquals(0,api.starts);assertEquals(0,api.creates)
    }
    @Test fun invalidPhoneNeverCreatesOrder()=scenario {
        prepare();controller.create(mapOf(7L to 3),"synthetic-quote","123");assertEquals(0,api.creates)
    }
    @Test fun pendingOrderCannotBeResetIntoAnotherCheckout()=scenario {
        prepare();create();controller.startNewBasket()
        assertEquals("ORDER_VERIFIED",disk.value.phase);assertEquals(1,api.creates)
        assertNotNull(controller.state.value.order)
    }
    @Test fun paidOrderAllowsExplicitNewBasketWithoutAutomaticWrites()=scenario {
        prepare();create();order=order.copy(paymentStatus="held");controller.startNewBasket()
        assertEquals("NONE",disk.value.phase);assertNull(controller.state.value.order)
        assertEquals(1,api.creates);assertEquals(1,api.adds);assertEquals(0,paymentWrites)
    }
    @Test fun unknownOrderOutcomeCannotBeClearedAsNewBasket()=scenario {
        prepare();api.orderTimeout=true;create();controller.startNewBasket()
        assertEquals("ORDER_PENDING",disk.value.phase);assertEquals(1,api.creates)
    }
    @Test fun expiredQuoteRefreshDoesNotReuploadOrRestartCheckout()=scenario {
        prepare();now=Instant.parse(api.quote.expiresAt)
        api.quote=api.quote.copy(expiresAt=now.plusSeconds(900).toString())
        prepare();assertEquals(1,api.adds);assertEquals(1,api.starts);assertEquals(2,api.quotes)
        create();assertEquals(1,api.creates)
    }
    @Test fun accountChangeDuringUploadStopsCheckoutAndOrder()=scenario {
        api.hold=CompletableDeferred();prepare();sessions.beginLogout();api.hold!!.complete(Unit)
        assertEquals(1,api.adds);assertEquals(0,api.starts);assertEquals(0,api.creates)
    }
    @Test fun paymentEnvironmentMustMatchExplicitConfirmedBackend() {
        assertFalse(verifiedMpesaEnvironment("UNKNOWN","","https://synthetic.test/api/v1/"))
        assertFalse(verifiedMpesaEnvironment("SANDBOX","https://other.test/api/v1/","https://synthetic.test/api/v1/"))
        assertTrue(verifiedMpesaEnvironment("SANDBOX","https://synthetic.test/api/v1/","https://synthetic.test/api/v1/"))
    }
    @Test fun numericOrderResponseIsExactAndNotFabricated() {
        assertEquals(105L,verifiedCheckoutOrderId(JsonPrimitive(105)))
        assertThrows(IllegalStateException::class.java){verifiedCheckoutOrderId(JsonParser.parseString("""{"id":105}"""))}
        assertThrows(ArithmeticException::class.java){verifiedCheckoutOrderId(JsonPrimitive(BigDecimal("105.5")))}
    }
}
