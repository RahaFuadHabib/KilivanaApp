package com.example.farmapp.buyers

import com.example.farmapp.buyers.data.*
import com.example.farmapp.checkout.MemoryCheckoutJournalStorage
import com.example.farmapp.checkout.VersionedCheckoutJournalStore
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
        var raw:String?=null
        override fun read()=raw?.let(CheckoutJournalJson::decode) ?: value
        override fun write(value:CheckoutJournal){
            check(!fail)
            val encoded=if(raw!=null)CheckoutJournalJson.encode(value) else null
            this.value=value
            if(raw!=null)raw=encoded
        }
    }
    private class Api:BrianCheckoutApi {
        var activeAfterCheckout = false
        var adds=0;var updates=0;var starts=0;var quotes=0;var creates=0;var processing=false
        var conflict=false;var uploadTimeout=false;var orderTimeout=false;var readTimeout=false;var reject=0
        var cartId=10L;var conflictBody="""{"message":"Product already in cart"}""";var orderReject=0
        var hold:CompletableDeferred<Unit>?=null
        var quote=DeliveryQuote("synthetic-quote",8,BigDecimal("3.21"),BigDecimal("150"),BigDecimal("153.21"),"KES","2026-10-29T12:15:00Z","DOOR_DELIVERY",cartId=10,items=listOf(com.example.farmapp.checkout.CheckoutQuoteItem(7,3,BigDecimal("1.07"),BigDecimal("3.21"))))
        fun metadata()=JsonParser.parseString("""{"id":$cartId,"buyerId":42,"status":"${if(processing && !activeAfterCheckout)"CHECKOUT_PROCESSING" else "ACTIVE"}"}""").asJsonObject
        override suspend fun cart(buyerId:Long,session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>> {
            assertEquals(42,buyerId);if(readTimeout)throw java.net.SocketTimeoutException()
            return Response.success(CanonicalApiResponse(true,data=metadata()))
        }
        override suspend fun add(item:CartUpload,session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>> {
            adds++;assertEquals(CartUpload(7,3),item);hold?.await()
            if(uploadTimeout)throw java.net.SocketTimeoutException()
            if(conflict || reject!=0)return Response.error(if(conflict)409 else reject,conflictBody.toResponseBody())
            return Response.success(CanonicalApiResponse(true,data=metadata()))
        }
        override suspend fun quantity(cartId:Long,productId:Long,quantity:Int,session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>> {
            updates++;assertEquals(10,cartId);assertEquals(7,productId);assertEquals(3,quantity)
            return Response.success(CanonicalApiResponse(true,data=metadata()))
        }
        override suspend fun remove(cartId:Long,productId:Long,session:SessionSnapshot):Response<CanonicalApiResponse<Unit>> {
            assertEquals(10,cartId);assertEquals(7,productId)
            return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun checkout(session:SessionSnapshot):Response<CanonicalApiResponse<Unit>> {
            starts++;processing=true;return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun checkoutCart(cartId:Long,session:SessionSnapshot):Response<CanonicalApiResponse<Unit>> {
            assertEquals(10,cartId);starts++;processing=true;return Response.success(CanonicalApiResponse(true))
        }
        override suspend fun quote(request:DeliveryQuoteRequest,session:SessionSnapshot):Response<CanonicalApiResponse<DeliveryQuote>> {
            quotes++;assertEquals(DeliveryQuoteRequest("DOOR_DELIVERY",8),request)
            return Response.success(CanonicalApiResponse(true,data=quote))
        }
        override suspend fun order(request:QuoteOrderRequest,session:SessionSnapshot):Response<CanonicalApiResponse<JsonElement>> {
            creates++;assertEquals(QuoteOrderRequest("synthetic-quote","254712345678"),request)
            if(orderTimeout)throw java.net.SocketTimeoutException()
            if(orderReject!=0)return Response.error(orderReject,"synthetic order rejection".toResponseBody())
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
        fun controller(store:CheckoutJournalStore=disk)=BrianCheckoutController(api,orders,sessions,sessions.session.value,store,scope){now}
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
    @Test fun uncertainUploadCannotBePromotedByProcessingCartMetadata()=scenario {
        disk.value=CheckoutJournal("UPLOAD_PENDING",mapOf(7L to 3),BigDecimal("3.21"),10L,request=DeliveryQuoteRequest("DOOR_DELIVERY",8))
        val saved=disk.value
        api.processing=true
        val restarted=controller()
        restarted.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        restarted.recover()
        assertEquals(0,api.quotes)
        assertEquals(0,api.adds)
        assertEquals(0,api.starts)
        assertEquals(saved,disk.value)
        assertTrue(restarted.state.value.uncertain)
    }
    @Test fun preparePreservesEveryUncertainOrSubmittedPhaseEvenForDifferentBasket() {
        for(phase in listOf("UPLOAD_PENDING","QUANTITY_PENDING","UPLOAD_ACKNOWLEDGED","CHECKOUT_PENDING","ORDER_PENDING","ORDER_RETURNED","ORDER_VERIFIED","UNRESOLVED")) scenario {
            disk.value=CheckoutJournal(phase,mapOf(7L to 3),BigDecimal("3.21"),10L,api.quote,DeliveryQuoteRequest("DOOR_DELIVERY",8),
                orderId=105L.takeIf{phase in setOf("ORDER_RETURNED","ORDER_VERIFIED")},beforeOrderIds=setOf(90L))
            val saved=disk.value;api.processing=true
            val restarted=controller()
            restarted.prepare(mapOf(8L to 1),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",9))
            assertEquals(phase,saved.phase);assertEquals(saved,disk.value)
            assertEquals(0,api.quotes);assertEquals(0,api.adds);assertEquals(0,api.starts);assertEquals(0,api.creates)
        }
    }
    @Test fun differentCurrentBasketReplacesAcknowledgedCheckoutAndUsesFreshActiveCart()=scenario {
        disk.value=CheckoutJournal("CHECKOUT_ACKNOWLEDGED",mapOf(8L to 1),BigDecimal("3.21"),99L,
            request=DeliveryQuoteRequest("DOOR_DELIVERY",9))
        api.processing=true
        api.activeAfterCheckout=true

        controller.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))

        assertEquals("QUOTE_READY",disk.value.phase)
        assertEquals(mapOf(7L to 3),disk.value.items)
        assertEquals(10L,disk.value.cartId)
        assertEquals(1,api.adds);assertEquals(1,api.updates);assertEquals(1,api.starts);assertEquals(1,api.quotes)
    }
    @Test fun acknowledgedCheckoutRecoveryUsesOriginalSnapshotWithoutRepeatingWrites() {
        for(phase in listOf("CHECKOUT_PENDING","CHECKOUT_ACKNOWLEDGED")) scenario {
            disk.value=CheckoutJournal(phase,mapOf(7L to 3),BigDecimal("3.21"),10L,request=DeliveryQuoteRequest("DOOR_DELIVERY",8))
            api.processing=true
            val restarted=controller();restarted.recover()
            assertEquals("QUOTE_READY",disk.value.phase);assertEquals(mapOf(7L to 3),disk.value.items)
            assertEquals(10L,disk.value.cartId);assertEquals(1,api.quotes)
            assertEquals(0,api.adds);assertEquals(0,api.starts);assertEquals(0,api.creates)
        }
    }
    @Test fun recoveryRejectsDifferentCartOrIncompleteOriginalEvidence() {
        for(kind in listOf("differentCart","missingCart","missingRequest","missingItems","missingSubtotal")) scenario {
            val original=CheckoutJournal("CHECKOUT_PENDING",mapOf(7L to 3),BigDecimal("3.21"),10L,request=DeliveryQuoteRequest("DOOR_DELIVERY",8))
            disk.value=when(kind){
                "missingCart"->original.copy(cartId=null)
                "missingRequest"->original.copy(request=null)
                "missingItems"->original.copy(items=emptyMap())
                "missingSubtotal"->original.copy(expectedSubtotal=null)
                else->original
            }
            val saved=disk.value;api.processing=true;if(kind=="differentCart")api.cartId=99
            val restarted=controller();restarted.recover()
            assertEquals(saved,disk.value);assertEquals(0,api.quotes);assertTrue(restarted.state.value.uncertain)
        }
    }
    @Test fun unknownConflictDoesNotAutomaticallySetQuantityOrStartCheckout()=scenario {
        api.conflict=true;api.conflictBody="""{"message":"Cart locked by another operation"}"""
        prepare()
        assertEquals(1,api.adds);assertEquals(0,api.updates);assertEquals(0,api.starts);assertEquals(0,api.quotes)
    }
    @Test fun malformedConflictDoesNotAutomaticallySetQuantity()=scenario {
        api.conflict=true;api.conflictBody="not JSON"
        prepare();assertEquals(0,api.updates);assertEquals(0,api.starts)
    }
    @Test fun orderErrorNeverReleasesTheOrderReservationForAnotherSubmission()=scenario {
        prepare();api.orderReject=400;create()
        val saved=disk.value
        assertEquals("ORDER_PENDING",saved.phase)
        val restarted=controller();restarted.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        restarted.create(mapOf(7L to 3),"synthetic-quote","0712345678")
        assertEquals(saved,disk.value);assertEquals(1,api.creates);assertEquals(1,api.quotes)
    }
    @Test fun stationOrderRequiresExactStationIdIncludingAfterRestart()=scenario {
        val stationQuote=api.quote.copy(addressId=null,deliveryMethod="PICKUP_STATION",pickupStationId=3)
        val correct=order.copy(addressId=null,pickupStationId=3)
        assertEquals(correct,verifyPurchasedOrder(correct,42,mapOf(7L to 3),stationQuote))
        for(station in listOf<Long?>(null,4)) {
            order=correct.copy(pickupStationId=station)
            assertThrows(IllegalStateException::class.java){verifyPurchasedOrder(order,42,mapOf(7L to 3),stationQuote)}
            disk.value=CheckoutJournal("ORDER_RETURNED",mapOf(7L to 3),BigDecimal("3.21"),10L,stationQuote,
                DeliveryQuoteRequest("PICKUP_STATION",pickupStationId=3),105L)
            val saved=disk.value;val restarted=controller();restarted.recover()
            assertNull(restarted.state.value.order);assertEquals(saved,disk.value);assertEquals(0,paymentWrites)
        }
        order=correct;val restarted=controller();restarted.recover()
        assertEquals(correct,restarted.state.value.order);assertEquals("ORDER_VERIFIED",disk.value.phase)
    }
    @Test fun doorAndTypedPickupRejectConflictingStationIds()=scenario {
        assertThrows(IllegalStateException::class.java){verifyPurchasedOrder(order.copy(pickupStationId=3),42,mapOf(7L to 3),api.quote)}
        val typedQuote=api.quote.copy(addressId=null,deliveryMethod="PICKUP_STATION",pickupLocation="Synthetic pickup")
        val typedOrder=order.copy(addressId=null,pickupLocation="Synthetic pickup")
        assertEquals(typedOrder,verifyPurchasedOrder(typedOrder,42,mapOf(7L to 3),typedQuote))
        assertThrows(IllegalStateException::class.java){verifyPurchasedOrder(typedOrder.copy(pickupLocation="Wrong location"),42,mapOf(7L to 3),typedQuote)}
        assertThrows(IllegalStateException::class.java){verifyPurchasedOrder(typedOrder.copy(pickupStationId=3),42,mapOf(7L to 3),typedQuote)}
    }
    @Test fun stationIdIsReadFromActualOrderJson() {
        val parsed=CanonicalJson.gson.fromJson("""{"id":105,"pickupStationId":3}""",BuyerOrderDto::class.java)
        assertEquals(3L,parsed.pickupStationId)
    }
    @Test fun serializedReturnedOrderSurvivesRestartAndNeverCreatesAnotherOrder()=scenario {
        disk.raw=CheckoutJournalJson.encode(CheckoutJournal())
        prepare();failOrderRead=true;create()
        assertEquals("ORDER_RETURNED",CheckoutJournalJson.decode(disk.raw!!).phase)
        failOrderRead=false
        val restarted=controller();restarted.recover()
        assertEquals(105L,restarted.state.value.order?.id)
        restarted.create(mapOf(7L to 3),"synthetic-quote","0712345678")
        assertEquals(1,api.creates);assertEquals(0,paymentWrites)
        assertEquals("ORDER_VERIFIED",CheckoutJournalJson.decode(disk.raw!!).phase)
    }
    @Test fun serializedUnknownOrderAndUploadOutcomesRemainBlockedAfterRestart() {
        for(upload in listOf(true,false))scenario {
            disk.raw=CheckoutJournalJson.encode(CheckoutJournal())
            if(upload)api.uploadTimeout=true
            prepare()
            if(!upload){api.orderTimeout=true;create()}
            val saved=disk.raw
            val restarted=controller()
            restarted.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
            restarted.recover()
            assertTrue(restarted.state.value.uncertain);assertEquals(saved,disk.raw)
            assertEquals(1,api.adds);assertEquals(if(upload)0 else 1,api.creates);assertEquals(0,paymentWrites)
        }
    }
    @Test fun corruptSerializedJournalCannotTriggerAnyCheckoutWrite()=scenario {
        disk.raw="""{"phase":"NONE","items":{"7":3,"7":4}}"""
        val saved=disk.raw;val restarted=controller()
        restarted.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        assertTrue(restarted.state.value.uncertain);assertEquals(saved,disk.raw)
        assertEquals(0,api.adds);assertEquals(0,api.starts);assertEquals(0,api.creates);assertEquals(0,paymentWrites)
    }
    @Test fun extractedVersionedStoreAndBuyerAdapterRecoverTheSameOrderAfterRestart()=scenario {
        val storage=MemoryCheckoutJournalStorage()
        val original=controller(VersionedCheckoutJournalStore(storage))
        original.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        failOrderRead=true
        original.create(mapOf(7L to 3),"synthetic-quote","0712345678")
        assertEquals("ORDER_RETURNED",CheckoutJournalJson.decode(storage.record.json!!).phase)
        failOrderRead=false
        val restarted=controller(VersionedCheckoutJournalStore(storage));restarted.recover()
        assertEquals(105L,restarted.state.value.order?.id)
        val saved=storage.record
        restarted.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        restarted.create(mapOf(7L to 3),"synthetic-quote","0712345678")
        assertEquals(saved,storage.record);assertEquals(1,api.adds);assertEquals(1,api.creates);assertEquals(0,paymentWrites)
    }
    @Test fun activeCartReadAfterCheckoutDoesNotSuppressValidServerQuote()=scenario {
        api.activeAfterCheckout = true
        prepare()
        assertNotNull(controller.state.value.quote)
        assertFalse(controller.state.value.uncertain)
        assertEquals(1, api.starts)
        assertEquals(1, api.quotes)
        assertEquals(0, api.creates)
    }
    @Test fun acknowledgedPreparationResumesQuoteWithoutUploadingOrPreparingAgain()=scenario {
        disk.value=CheckoutJournal("CHECKOUT_ACKNOWLEDGED",items=mapOf(7L to 3),expectedSubtotal=BigDecimal("3.21"),
            cartId=10,request=DeliveryQuoteRequest("DOOR_DELIVERY",8))
        val resumed=controller()
        resumed.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        assertNotNull(resumed.state.value.quote)
        assertEquals(0,api.adds)
        assertEquals(0,api.updates)
        assertEquals(0,api.starts)
        assertEquals(0,api.creates)
        assertEquals(1,api.quotes)
    }
    @Test fun equalSubtotalFromDifferentCartCannotReachOrderCreation()=scenario {
        api.quote=api.quote.copy(cartId=99)
        prepare();assertNull(controller.state.value.quote);create()
        assertEquals(0,api.creates);assertEquals(0,paymentWrites)
    }
    @Test fun equalSubtotalFromDifferentProductsOrQuantitiesIsBlocked() {
        for(line in listOf(
            com.example.farmapp.checkout.CheckoutQuoteItem(99,3,BigDecimal("1.07"),BigDecimal("3.21")),
            com.example.farmapp.checkout.CheckoutQuoteItem(7,1,BigDecimal("3.21"),BigDecimal("3.21"))
        ))scenario {
            api.quote=api.quote.copy(items=listOf(line))
            prepare();assertNull(controller.state.value.quote);create();assertEquals(0,api.creates)
        }
    }
    @Test fun missingCartOrLineEvidenceFromOldBackendIsBlocked() {
        for(missingCart in listOf(true,false))scenario {
            api.quote=if(missingCart)api.quote.copy(cartId=null) else api.quote.copy(items=null)
            prepare()
            assertNull(controller.state.value.quote)
            assertTrue(controller.state.value.notice!!.contains("backend must return cartId and items"))
            assertEquals("CHECKOUT_ACKNOWLEDGED",disk.value.phase)
            create();assertEquals(0,api.creates)
        }
    }
    @Test fun legacySavedQuoteCannotCreateUntilRequotedWithCartEvidence()=scenario {
        disk.value=CheckoutJournal("QUOTE_READY",mapOf(7L to 3),BigDecimal("3.21"),10,
            api.quote.copy(cartId=null,items=null),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        val resumed=controller();resumed.create(mapOf(7L to 3),"synthetic-quote","0712345678")
        assertEquals(0,api.creates)
        resumed.prepare(mapOf(7L to 3),BigDecimal("3.21"),DeliveryQuoteRequest("DOOR_DELIVERY",8))
        assertEquals(10L,resumed.state.value.quote?.cartId)
        assertEquals(0,api.starts);assertEquals(0,api.adds)
    }
    @Test fun resetJournalClearsSafePreOrderCheckoutWithoutServerCalls()=scenario {
        disk.value=CheckoutJournal("QUOTE_READY",mapOf(7L to 3),BigDecimal("3.21"),10,
            api.quote,DeliveryQuoteRequest("DOOR_DELIVERY",8))
        val saved=disk.value
        val restarted=controller()
        restarted.resetJournal()
        assertEquals(CheckoutJournal(),disk.value)
        assertNotEquals(saved,disk.value)
        assertEquals(0,api.adds);assertEquals(0,api.creates);assertEquals(0,api.quotes)
        assertFalse(restarted.state.value.busy)
    }
    @Test fun resetJournalNeverDiscardsAnOrderThatMayExistOnTheServer()=scenario {
        disk.value=CheckoutJournal("ORDER_RETURNED",mapOf(7L to 3),BigDecimal("3.21"),10,
            api.quote,DeliveryQuoteRequest("DOOR_DELIVERY",8),105L)
        val saved=disk.value
        val restarted=controller()
        restarted.resetJournal()
        assertEquals(saved,disk.value)
        assertFalse(restarted.state.value.busy)
    }
    @Test fun recoverClearsCheckoutJournalForFinishedOwnedOrderSoNewBasketCanStart()=scenario {
        prepare();create()
        for(status in listOf("held","paid","settled","refunded")) {
            order=order.copy(paymentStatus=status)
            val restarted=controller()
            restarted.recover()
            assertEquals("NONE",disk.value.phase);assertNull(restarted.state.value.order)
            assertFalse(restarted.state.value.uncertain)
            assertEquals(1,api.creates)
        }
        order=order.copy(status="cancelled");val restarted=controller();restarted.recover()
        assertEquals("NONE",disk.value.phase);assertNull(restarted.state.value.order)
    }
    @Test fun recoverKeepsUnfinishedOwnedOrderAndDoesNotClearJournal()=scenario {
        prepare();create()
        val restarted=controller();restarted.recover()
        assertEquals(order,restarted.state.value.order);assertEquals("ORDER_VERIFIED",disk.value.phase)
        assertEquals(1,api.creates)
    }
    @Test fun persistedOwnedOrderJournalTriggersRecoveryOnConstructionAndClearsWhenFinished()=scenario {
        disk.value=CheckoutJournal("ORDER_RETURNED",mapOf(7L to 3),BigDecimal("3.21"),10,
            api.quote,DeliveryQuoteRequest("DOOR_DELIVERY",8),105L)
        order=order.copy(paymentStatus="held")
        val restarted=controller()
        assertTrue(restarted.state.value.needsRecovery)
        restarted.recover()
        assertEquals("NONE",disk.value.phase);assertNull(restarted.state.value.order)
        assertFalse(restarted.state.value.needsRecovery)
        assertEquals(0,api.creates)
    }
    @Test fun persistedOwnedOrderJournalKeepsUnfinishedOrderAfterRecovery()=scenario {
        disk.value=CheckoutJournal("ORDER_RETURNED",mapOf(7L to 3),BigDecimal("3.21"),10,
            api.quote,DeliveryQuoteRequest("DOOR_DELIVERY",8),105L)
        val restarted=controller()
        assertTrue(restarted.state.value.needsRecovery)
        restarted.recover()
        assertEquals(order,restarted.state.value.order);assertEquals("ORDER_VERIFIED",disk.value.phase)
        assertFalse(restarted.state.value.needsRecovery)
        assertEquals(0,api.creates)
    }

}
