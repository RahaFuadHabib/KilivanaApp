package com.example.farmapp.checkout

import com.example.farmapp.buyers.BuyerCheckoutContract
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import com.example.farmapp.supplier.supplierAuth
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.time.Instant

class CheckoutControllerTest {
    private class Fixture(role:String="BUYER") {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        private val delegate=signedInStore(supplierAuth().copy(role="BUYER"))
        val session=MutableStateFlow(delegate.session.value.let{it.copy(auth=it.auth!!.copy(role=role))})
        val sessions=object:SessionStore by delegate {override val session=this@Fixture.session}
        val expected=session.value
        val storage=MemoryCheckoutJournalStorage()
        var now=Instant.parse("2026-11-05T12:00:00Z")
        val items=mapOf(7L to 3)
        val subtotal=BigDecimal("3.21")
        val door=CheckoutQuoteRequest("DOOR_DELIVERY",addressId=8)
        var quote=CheckoutQuote("q1",8,subtotal,BigDecimal("150.00"),BigDecimal("153.21"),"KES","2026-11-05T12:15:00Z","DOOR_DELIVERY")
        var order=BuyerOrderDto(105,42,"placed",quote.total,"pending",items=listOf(
            BuyerOrderItemDto(1,7,9,"Synthetic produce","kg",3,BigDecimal("1.07"),subtotal)),
            subtotal=subtotal,deliveryFee=quote.deliveryFee,addressId=8)
        var orders=emptyList<BuyerOrderDto>()
        val calls=mutableListOf<String>()
        var refreshedPrice: BigDecimal? = null
        var priceReads=0
        var processing=false
        var cartId=10L
        var interrupt:String?=null
        var cancel:String?=null
        var conflict:String?=null
        var hold:CompletableDeferred<Unit>?=null
        var hook:(String)->Unit={}
        fun journal()=CheckoutJournalJson.decode(storage.record.json!!)
        private suspend fun record(stage:String){
            calls+=stage
            val reserved=mapOf("upload" to "UPLOAD_PENDING","quantity" to "QUANTITY_PENDING","checkout" to "CHECKOUT_PENDING","create" to "ORDER_PENDING")[stage]
            if(reserved!=null)assertEquals("Reservation must exist before $stage",reserved,journal().phase)
            if(stage=="upload")hold?.await()
            hook(stage)
            if(interrupt==stage)throw SocketTimeoutException("Synthetic interrupted $stage")
            if(cancel==stage)throw CancellationException("Synthetic cancelled $stage")
        }
        private fun metadata()=JsonParser.parseString("""{"id":$cartId,"buyerId":42,"status":"${if(processing)"CHECKOUT_PROCESSING" else "ACTIVE"}"}""").asJsonObject
        private fun<T> response(data:T)=Response.success(CanonicalApiResponse(true,data=data))
        val repository=object:CheckoutRepository<JsonObject,JsonElement,BuyerOrderDto>{
            override suspend fun refreshedSubtotal(items:Map<Long,Int>):BigDecimal? { priceReads++;return refreshedPrice }

            override suspend fun readCart(purchaserId:Long):Response<CanonicalApiResponse<JsonObject>>{record("cart");assertEquals(42,purchaserId);return response(metadata())}
            override suspend fun addItem(productId:Long,quantity:Int):Response<CanonicalApiResponse<JsonObject>>{
                record("upload");assertEquals(7,productId);assertEquals(3,quantity)
                return conflict?.let{Response.error(409,"""{"message":"$it"}""".toResponseBody())} ?: response(metadata())
            }
            override suspend fun setQuantity(cartId:Long,productId:Long,quantity:Int):Response<CanonicalApiResponse<JsonObject>>{
                record("quantity");assertEquals(10,cartId);assertEquals(7,productId);assertEquals(3,quantity);return response(metadata())
            }
            override suspend fun startCheckout():Response<CanonicalApiResponse<Unit>>{record("checkout");processing=true;return response(Unit)}
            override suspend fun deliveryQuote(request:CheckoutQuoteRequest):Response<CanonicalApiResponse<CheckoutQuote>>{
                record("quote");assertEquals(quote.deliveryMethod,request.deliveryMethod);assertEquals(quote.addressId,request.addressId)
                assertEquals(quote.pickupStationId,request.pickupStationId);return response(quote)
            }
            override suspend fun createOrder(quoteId:String,phoneNumber:String):Response<CanonicalApiResponse<JsonElement>>{
                record("create");assertEquals("q1",quoteId);assertEquals("254712345678",phoneNumber);return response<JsonElement>(JsonPrimitive(105))
            }
            override suspend fun listOrders(purchaserId:Long):Response<CanonicalApiResponse<List<BuyerOrderDto>>>{record("orders");assertEquals(42,purchaserId);return response(orders)}
            override suspend fun readOrder(orderId:Long):Response<CanonicalApiResponse<BuyerOrderDto>>{record("order");assertEquals(105,orderId);return response(order)}
        }
        fun controller(policy:PurchaserPolicy=BuyerPurchaserPolicy)=CheckoutController(repository,BuyerCheckoutContract,sessions,expected,policy,VersionedCheckoutJournalStore(storage),scope){now}
        val controller=controller()
        fun prepare()=controller.prepare(items,subtotal,door)
        fun create()=controller.create(items,"q1","0712345678")
        fun seed(value:CheckoutJournal){val store=VersionedCheckoutJournalStore(storage);store.read();store.write(value)}
    }
    private fun scenario(role:String="BUYER",block:Fixture.()->Unit)=runBlocking {val f=Fixture(role);try{f.block()}finally{f.scope.cancel()}}

    @Test fun successfulSharedCheckoutReservesBeforeWritesAndPublishesTheVerifiedBuyerOrder()=scenario {
        prepare();assertNull(controller.state.value.order);assertEquals("QUOTE_READY",journal().phase)
        create()
        assertEquals(order,controller.state.value.order);assertEquals("254712345678",controller.state.value.phone)
        assertEquals("ORDER_VERIFIED",journal().phase);assertEquals(105L,journal().orderId)
        assertFalse(storage.record.json!!.contains("254712345678"))
        assertEquals(listOf("cart","upload","quantity","checkout","quote","orders","create","order"),calls)
    }

    @Test fun duplicatePrepareAndCreateActionsNeverReplayWrites()=scenario {
        hold=CompletableDeferred();repeat(5){prepare()}
        assertEquals(1,calls.count{it=="upload"});assertTrue(controller.state.value.busy)
        hold!!.complete(Unit);repeat(5){create()}
        assertEquals(1,calls.count{it=="checkout"});assertEquals(1,calls.count{it=="create"})
        val restarted=controller();restarted.create(items,"q1","0712345678")
        assertEquals(1,calls.count{it=="create"})
    }

    @Test fun failedDurableReservationPreventsEveryRepositoryCall()=scenario {
        storage.fail=true;prepare()
        assertTrue(calls.isEmpty());assertEquals(StoredCheckoutJournal(0,null),storage.record)
    }

    @Test fun uncertainUploadStaysUnresolvedEvenIfMetadataLaterSaysProcessing()=scenario {
        interrupt="upload";prepare();val saved=storage.record
        processing=true
        val restarted=controller();restarted.prepare(items,subtotal,door);restarted.recover()
        assertTrue(restarted.state.value.uncertain);assertEquals(saved,storage.record)
        assertEquals(1,calls.count{it=="upload"});assertFalse("quote" in calls);assertFalse("create" in calls)
    }

    @Test fun unknownOrderOutcomeCannotBeAdoptedBySimilarityOrSubmittedAgain()=scenario {
        prepare();interrupt="create";create();orders=listOf(order)
        val saved=storage.record;val restarted=controller()
        restarted.recover();restarted.prepare(items,subtotal,door);restarted.create(items,"q1","0712345678")
        assertEquals(saved,storage.record);assertNull(restarted.state.value.order);assertTrue(restarted.state.value.uncertain)
        assertEquals(1,calls.count{it=="create"})
    }

    @Test fun returnedOrderRecoversAfterRestartWithoutCreatingAnotherOne()=scenario {
        prepare();interrupt="order";create()
        assertEquals("ORDER_RETURNED",journal().phase);assertEquals(105L,journal().orderId)
        interrupt=null;val restarted=controller();restarted.recover()
        assertEquals(order,restarted.state.value.order);assertEquals("ORDER_VERIFIED",journal().phase)
        assertEquals(1,calls.count{it=="create"})
    }

    @Test fun checkoutRecoveryUsesTheOriginalCartAndDeliveryRequest()=scenario {
        seed(CheckoutJournal("CHECKOUT_PENDING",items,subtotal,10,request=door));processing=true
        val restarted=controller()
        restarted.prepare(mapOf(9L to 1),subtotal,CheckoutQuoteRequest("DOOR_DELIVERY",addressId=99))
        restarted.recover()
        assertEquals(items,journal().items);assertEquals(door,journal().request);assertEquals(10L,journal().cartId)
        assertEquals(listOf("cart","quote"),calls);assertNotNull(restarted.state.value.quote)
    }

    @Test fun differentCurrentBasketReplacesSafePreOrderCheckoutSnapshots()=scenario {
        for(phase in listOf("CHECKOUT_ACKNOWLEDGED","QUOTE_READY")) {
            calls.clear()
            processing=false
            cartId=10L
            val savedQuote=if(phase=="QUOTE_READY")quote else null
            seed(CheckoutJournal(phase, mapOf(9L to 1), subtotal, 99, quote=savedQuote, request=door))
            val restarted=controller()

            restarted.prepare(items,subtotal,door)

            assertEquals("QUOTE_READY",journal().phase)
            assertEquals(items,journal().items)
            assertEquals(10L,journal().cartId)
            assertEquals(quote,restarted.state.value.quote)
            assertEquals(listOf("cart","upload","quantity","checkout","quote"),calls)
        }
    }

    @Test fun processingMetadataForAnotherCartDoesNotAuthorizeRecovery()=scenario {
        seed(CheckoutJournal("CHECKOUT_PENDING",items,subtotal,10,request=door));processing=true;cartId=99
        val saved=storage.record;val restarted=controller();restarted.recover()
        assertEquals(saved,storage.record);assertTrue(restarted.state.value.uncertain);assertEquals(listOf("cart"),calls)
    }

    @Test fun knownDuplicateConflictSetsExactQuantityButUnknownConflictDoesNot() {
        for(message in listOf("Product already in cart","Cart locked by another checkout"))scenario {
            conflict=message;prepare()
            assertEquals(1,calls.count{it=="upload"})
            if(message=="Product already in cart"){
                assertEquals(1,calls.count{it=="quantity"});assertNotNull(controller.state.value.quote)
            }else{assertFalse("quantity" in calls);assertFalse("checkout" in calls);assertNull(controller.state.value.quote)}
        }
    }

    @Test fun staleSessionsAreRejectedBeforeAnyRepositoryOrJournalWrite() {
        for(kind in listOf("generation","account","origin","logout","demo","role"))scenario {
            session.value=when(kind){
                "generation"->expected.copy(generation=expected.generation+1)
                "account"->expected.copy(auth=expected.auth!!.copy(userId="99"))
                "origin"->expected.copy(auth=expected.auth!!.copy(sessionOrigin="https://other.test/api/v1/"))
                "logout"->expected.copy(loggingOut=true)
                "demo"->expected.copy(isDemo=true)
                else->expected.copy(auth=expected.auth!!.copy(role="FARMER"))
            }
            prepare();assertTrue(calls.isEmpty());assertEquals(0,storage.writes)
        }
    }

    @Test fun sessionChangeDuringAnUploadCannotStartCheckoutOrPublishItsResult()=scenario {
        hook={stage->if(stage=="upload")session.value=expected.copy(loggingOut=true)}
        prepare()
        assertEquals("UPLOAD_PENDING",journal().phase);assertTrue(controller.state.value.uncertain)
        assertFalse("checkout" in calls);assertFalse("quote" in calls)
    }

    @Test fun unverifiedFarmerPolicyCannotUseTheSharedControllerToPurchase()=scenario("FARMER") {
        val denied=controller(UnverifiedFarmerPurchaserPolicy)
        denied.prepare(items,subtotal,door);denied.create(items,"q1","0712345678");denied.recover()
        assertTrue(calls.isEmpty());assertEquals(0,storage.writes)
        assertTrue(CheckoutOperation.entries.all{!UnverifiedFarmerPurchaserPolicy.permits(it)})
    }

    @Test fun missingOrWrongStationIsNeverPublishedAndCorrectStationCanRecoverTheSameOrder()=scenario {
        quote=quote.copy(addressId=null,deliveryMethod="PICKUP_STATION",pickupStationId=3)
        controller.prepare(items,subtotal,CheckoutQuoteRequest("PICKUP_STATION",pickupStationId=3))
        order=order.copy(addressId=null,pickupStationId=4);create()
        assertNull(controller.state.value.order);assertEquals("ORDER_RETURNED",journal().phase)
        order=order.copy(pickupStationId=null);val restarted=controller();restarted.recover()
        assertNull(restarted.state.value.order)
        order=order.copy(pickupStationId=3);restarted.recover()
        assertEquals(order,restarted.state.value.order);assertEquals(1,calls.count{it=="create"})
    }

    @Test fun quoteExpiryPreventsOrderCreationAndAmountMismatchPreventsVerifiedOutput() {
        scenario {prepare();now=Instant.parse(quote.expiresAt);create();assertFalse("create" in calls)}
        scenario {prepare();order=order.copy(total=BigDecimal("999.00"));create();assertNull(controller.state.value.order);assertEquals("ORDER_RETURNED",journal().phase)}
        scenario {prepare();order=order.copy(items=order.items!!.map{it.copy(quantity=4)});create();assertNull(controller.state.value.order)}
    }

    @Test fun coroutineCancellationKeepsTheUploadReservationForRestart()=scenario {
        cancel="upload";prepare()
        assertEquals("UPLOAD_PENDING",journal().phase)
        val saved=storage.record;val restarted=controller();restarted.prepare(items,subtotal,door)
        assertEquals(saved,storage.record);assertEquals(1,calls.count{it=="upload"})
    }
    @Test fun stalePricesReconcileAgainstFreshProductReadsWithoutRepeatingPreparation()=scenario {
        seed(CheckoutJournal("CHECKOUT_ACKNOWLEDGED",items=items,expectedSubtotal=BigDecimal("1"),cartId=10,request=door))
        refreshedPrice=subtotal
        val resumed=controller()
        resumed.prepare(items,BigDecimal("1"),door)
        assertEquals(subtotal,resumed.state.value.quote?.subtotal)
        assertEquals(listOf("quote"),calls)
        assertEquals(1,priceReads)
    }
    @Test fun unreconcilableServerSubtotalReportsExactDifferenceAndNeverCreatesOrder()=scenario {
        refreshedPrice=subtotal
        quote=quote.copy(subtotal=BigDecimal("6.42"),total=BigDecimal("156.42"))
        prepare()
        assertNull(controller.state.value.quote)
        assertTrue(controller.state.value.notice!!.contains("Android KES 3.21, server KES 6.42, difference KES 3.21"))
        assertFalse(calls.contains("create"))
    }
    @Test fun mismatchedAcknowledgedBasketStartsFreshCheckout()=scenario {
        seed(CheckoutJournal("CHECKOUT_ACKNOWLEDGED",items=mapOf(9L to 1),expectedSubtotal=subtotal,cartId=10,request=door))
        val resumed=controller()
        resumed.prepare(items,subtotal,door)
        assertEquals(1,priceReads)
        assertEquals(listOf("cart","upload","quantity","checkout","quote"),calls)
        assertEquals("QUOTE_READY",journal().phase)
        assertEquals(items,journal().items)
    }
    @Test fun lowerServerSubtotalIsNotAdoptedOrRoundedAway()=scenario {
        refreshedPrice=subtotal
        quote=quote.copy(subtotal=BigDecimal("0.07"),total=BigDecimal("150.07"))
        prepare()
        assertNull(controller.state.value.quote)
        assertTrue(controller.state.value.notice!!.contains("difference KES -3.14"))
        assertFalse(calls.contains("create"))
    }
}
