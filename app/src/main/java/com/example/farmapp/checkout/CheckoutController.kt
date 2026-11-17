package com.example.farmapp.checkout

import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.CanonicalResponseFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant

/** One workflow; role-specific repositories, policies and validators supply the supported contract. */
internal class CheckoutController<CartData,CreatedOrderId,OrderData>(
    private val repository:CheckoutRepository<CartData,CreatedOrderId,OrderData>,
    private val contract:CheckoutContract<CartData,CreatedOrderId,OrderData>,
    sessions:SessionStore,
    expected:SessionSnapshot,
    policy:PurchaserPolicy,
    private val disk:CheckoutJournalStore,
    private val scope:CoroutineScope,
    private val now:()->Instant={Instant.now()}
) {
    private val sessionGuard=CheckoutSessionGuard(sessions,expected,policy)
    private var journal: CheckoutJournal
    init {
        // Read once so the VersionedCheckoutJournalStore has the correct version before any write.
        val loaded = runCatching { disk.read() }.getOrElse { CheckoutJournal("UNRESOLVED") }
        // Silently reset phases with no server-side order risk so the user never sees a stale
        // warning on a fresh session. Phases where an order may already exist on the server or
        // a cart upload was mid-flight are kept so recover() can reconcile them.
        val safeToReset = loaded.orderId == null && loaded.phase !in setOf(
            "ORDER_PENDING", "ORDER_RETURNED", "ORDER_VERIFIED",
            "UPLOAD_PENDING", "QUANTITY_PENDING", "CHECKOUT_PENDING"
        )
        journal = if (safeToReset && loaded.phase != "NONE") {
            runCatching { disk.write(CheckoutJournal()) }
            CheckoutJournal()
        } else loaded
    }
    private val mutable=MutableStateFlow(CheckoutState<OrderData>(quote=journal.quote.takeIf{journal.phase=="QUOTE_READY"},uncertain=journal.phase !in setOf("NONE","QUOTE_READY","ORDER_VERIFIED"),
        needsRecovery=journal.orderId!=null,
        notice=if(journal.phase=="NONE")null else "An earlier checkout is saved. Check its status before submitting anything again."))
    val state=mutable.asStateFlow()
    fun matchesCart(items:Map<Long,Int>)=journal.items==items
    private fun current(operation:CheckoutOperation=CheckoutOperation.READ_CART):SessionSnapshot = sessionGuard.current(operation)
    private suspend fun <T> performRequest(operation:CheckoutOperation,action:suspend()->Response<CanonicalApiResponse<T>>):Response<CanonicalApiResponse<T>> {
        current(operation)
        val response=action()
        try{current(operation)}catch(failure:Exception){response.errorBody()?.close();throw failure}
        return response
    }
    private fun save(value:CheckoutJournal){disk.write(value);journal=value}
    private class SubtotalMismatch(message: String): Exception(message)
    private class QuoteContractMismatch(message: String): Exception(message)
    private class Rejected(val stage:String,val status:Int,val code:String?=null):Exception("$stage failed (HTTP $status${code?.let{", $it"}.orEmpty()}). "+when(status){
        400,422->"The backend rejected these checkout details. Check quantity, stock and delivery information."
        401,403->"Sign in again or check Buyer permissions."
        404->"The backend could not find the requested cart, product, address or endpoint."
        409->"The backend reported a checkout conflict. Check your existing cart and Orders."
        429->"Too many requests. Wait before trying again."
        else->"The result may be unresolved. Check status before submitting again."})
    private fun <T> envelope(r:Response<CanonicalApiResponse<T>>,stage:String):CanonicalApiResponse<T> {
        if(!r.isSuccessful){
            // Read a bounded error fragment in-app; retain ONLY a machine-code token, never raw bodies/messages.
            val code=runCatching{r.errorBody()?.use{body->body.charStream().use{reader->
                val buffer=CharArray(8192);val count=reader.read(buffer)
                if(count<=0)null else com.google.gson.JsonParser.parseString(String(buffer,0,count)).asJsonObject
                    .getAsJsonObject("error")?.get("code")?.takeIf{it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
            }}}.getOrNull()?.takeIf{Regex("[A-Z][A-Z0-9_]{0,63}").matches(it)}
            throw Rejected(stage,r.code(),code)
        }
        return (r.body() ?: error("$stage returned an incomplete response.")).also{
            try{it.requireSuccess()}catch(e:CanonicalResponseFailure){throw Rejected(stage,r.code(),e.errorCode?.takeIf{code->Regex("[A-Z][A-Z0-9_]{0,63}").matches(code)})}
        }
    }
    private fun <T> data(r:Response<CanonicalApiResponse<T>>,stage:String)=envelope(r,stage).requireData()
    private fun metadata(value:CartData):CheckoutCartMetadata=contract.cartMetadata(value,current().auth!!.userId.toLong())
    private fun launch(action:suspend()->Unit){
        if(mutable.value.busy)return
        mutable.update{it.copy(busy=true,notice=null)}
        scope.launch{try{current();action()}catch(e:CancellationException){throw e}catch(e:Exception){
            if(e is Rejected && e.status in setOf(400,401,403,404,409,422,429)) {
                // Only documented, definite rejections can be resubmitted by another explicit user action.
                if(journal.phase in setOf("UPLOAD_PENDING","QUANTITY_PENDING"))runCatching{save(journal.copy(phase="CART_REJECTED"))}
                // An order error does not prove that no order was created. Preserve its reservation.
            }
            val pending=journal.phase !in setOf("NONE","QUOTE_READY","ORDER_VERIFIED")
            mutable.update{it.copy(notice=if(e is Rejected)e.message else when(e){
                is SubtotalMismatch->e.message
                is QuoteContractMismatch->e.message
                is java.io.IOException->"Connection interrupted. The last operation may have reached the server. Check checkout status; no write was retried."
                else->e.message?.takeIf{msg->msg in safeMessages} ?: "Checkout could not be verified. Check status before submitting again."},uncertain=pending,quote=it.quote.takeUnless{pending})}
        }finally{mutable.update{it.copy(busy=false)}}}
    }
    fun prepare(items:Map<Long,Int>,subtotal:BigDecimal,request:CheckoutQuoteRequest)=launch {
        check(items.isNotEmpty() && items.keys.all{it>0} && items.values.all{it>0} && subtotal.signum()>0)
        if(journal.phase in setOf("QUOTE_READY","CHECKOUT_ACKNOWLEDGED") && journal.items!=items) {
            // These phases have not submitted an order, so a different current basket can safely replace them.
            save(CheckoutJournal())
            mutable.update{it.copy(quote=null,order=null,phone="",uncertain=false)}
        }
        check(journal.phase in setOf("NONE","CART_REJECTED","QUOTE_READY","CHECKOUT_ACKNOWLEDGED")){"An earlier checkout is unresolved. Check status instead of submitting again."}
        if(journal.phase in setOf("QUOTE_READY","CHECKOUT_ACKNOWLEDGED")) check(journal.items==items){"A different cart checkout is already in progress."}
        val refreshedSubtotal = repository.refreshedSubtotal(items) ?: subtotal
        if(journal.phase=="CHECKOUT_ACKNOWLEDGED") {
            check(journal.items==items){"A different cart checkout is already in progress."}
            // Preparation already succeeded. Retain its basket and refresh only its price expectation.
            save(journal.copy(expectedSubtotal=refreshedSubtotal))
            refreshQuote(request)
            return@launch
        }
        if(journal.phase=="QUOTE_READY"){
            check(journal.items==items){"A different cart checkout is already in progress."}
            save(journal.copy(expectedSubtotal=refreshedSubtotal,quote=null))
            mutable.update{it.copy(quote=null)}
            refreshQuote(request);return@launch
        }

        // Only recover() may reconcile an interrupted checkout, using its ORIGINAL snapshot.
        // Cart metadata alone cannot prove an upload or an unknown order outcome.
        check(journal.phase in setOf("NONE","CART_REJECTED")){"An earlier checkout is unresolved. Check status instead of submitting again."}
        save(CheckoutJournal("NONE",items,refreshedSubtotal))

        // GET item lines are optional here: upload acknowledgements + quote + exact order readback form the verification chain.
        val response=performRequest(CheckoutOperation.READ_CART){repository.readCart(current().auth!!.userId.toLong())}
        val cartData=if(response.code()==404){response.errorBody()?.close();null}else data(response,"Read cart")
        val cart=cartData?.let(::metadata)
        check(cart==null || cart.status=="ACTIVE"){"An earlier checkout is unresolved. Check status instead of submitting again."}
        save(journal.copy(cartId=cart?.id))
        // Only remove explicit server lines absent from the reviewed basket. Never clear blindly.
        cartData?.let { value -> contract.cartItems(value)?.keys?.minus(items.keys)?.sorted()?.forEach { id ->
            save(journal.copy(phase="QUANTITY_PENDING"))
            envelope(performRequest(CheckoutOperation.SET_QUANTITY){repository.removeItem(checkNotNull(journal.cartId),id)},"Remove stale cart item")
        } }
        items.toSortedMap().forEach{(id,q)->
            save(journal.copy(phase="UPLOAD_PENDING"))
            val upload=performRequest(CheckoutOperation.UPLOAD_CART){repository.addItem(id,q)}
            val accepted=if(upload.code()==409){
                // Do not turn an arbitrary conflict into a different write. Require the
                // documented duplicate-product rejection before setting an exact quantity.
                val duplicate=runCatching{upload.errorBody()?.use{body->body.charStream().use{reader->
                    val buffer=CharArray(8192);val count=reader.read(buffer)
                    count>0 && com.google.gson.JsonParser.parseString(String(buffer,0,count)).asJsonObject
                        .get("message")?.asString=="Product already in cart"
                }}==true}.getOrDefault(false)
                if(!duplicate)throw Rejected("Upload cart item",409)
                val existing=journal.cartId ?: metadata(data(performRequest(CheckoutOperation.READ_CART){repository.readCart(current().auth!!.userId.toLong())},"Read existing cart")).id
                save(journal.copy(phase="QUANTITY_PENDING",cartId=existing))
                data(performRequest(CheckoutOperation.SET_QUANTITY){repository.setQuantity(existing,id,q)},"Set cart quantity")
            }else {
                val added=metadata(data(upload,"Upload cart item"))
                check(added.status=="ACTIVE" && (journal.cartId==null || added.id==journal.cartId))
                // POST may increment an existing line. The reviewed basket specifies an exact quantity.
                save(journal.copy(phase="QUANTITY_PENDING",cartId=added.id))
                data(performRequest(CheckoutOperation.SET_QUANTITY){repository.setQuantity(added.id,id,q)},"Set cart quantity")
            }
            val verified=metadata(accepted)
            check(verified.status=="ACTIVE" && (journal.cartId==null || verified.id==journal.cartId))
            save(journal.copy(phase="UPLOAD_ACKNOWLEDGED",cartId=verified.id))
        }
        save(journal.copy(phase="CHECKOUT_PENDING",request=request))
        envelope(performRequest(CheckoutOperation.START_CHECKOUT){repository.startCheckout(checkNotNull(journal.cartId))},"Start checkout")
        save(journal.copy(phase="CHECKOUT_ACKNOWLEDGED"))
        // Cart GET returns the ACTIVE basket, creating another one after checkout.
        // The quote endpoint validates the authenticated CHECKOUT_PROCESSING basket.
        refreshQuote(request)
    }
    private suspend fun refreshQuote(request:CheckoutQuoteRequest){
        // Bind preparation and quoting to the same acknowledged server cart.
        val boundRequest = if (repository.requiresCartIdentity) request.copy(cartId=checkNotNull(journal.cartId)) else request
        val responseQuote=data(performRequest(CheckoutOperation.REQUEST_QUOTE){repository.deliveryQuote(boundRequest)},"Delivery quote")
        if(repository.requiresCartIdentity && (responseQuote.cartId==null || responseQuote.items==null)) {
            throw QuoteContractMismatch("The backend returned a delivery quote without the cart ID and item lines needed to verify your selected products. No order was created. The backend must return cartId and items (productId, quantity, unitPrice and lineSubtotal); retry after it is updated.")
        }
        val quote=verifiedCheckoutQuote(responseQuote,boundRequest,now())
        // Accept the server's subtotal as authoritative — it is the amount the buyer will pay.
        // Update expectedSubtotal so subsequent re-quotes against the same cart don't mismatch.
        if (journal.expectedSubtotal != null && quote.subtotal.compareTo(journal.expectedSubtotal) != 0) {
            save(journal.copy(expectedSubtotal=quote.subtotal))
        }
        if (repository.requiresCartIdentity) {
            try { verifyQuotedBasket(quote, checkNotNull(journal.cartId), journal.items) }
            catch (_: IllegalArgumentException) {
                throw QuoteContractMismatch("The backend delivery quote does not match your selected cart and product quantities. No order was created. Check the server cart before retrying.")
            }
        }
        save(journal.copy(phase="QUOTE_READY",quote=quote,request=boundRequest))
        mutable.update{it.copy(quote=quote,uncertain=false,notice="Review the server-confirmed total before creating your order.")}
    }
    fun create(items:Map<Long,Int>,quoteId:String,phone:String)=launch {
        check(journal.phase=="QUOTE_READY" && journal.items==items && journal.quote?.quoteId==quoteId)
        val quote=verifiedCheckoutQuote(journal.quote!!,journal.request!!,now())
        if (repository.requiresCartIdentity) verifyQuotedBasket(quote, checkNotNull(journal.cartId), journal.items)
        val number=contract.normalizePhone(phone)
        val before=data(performRequest(CheckoutOperation.READ_ORDERS){repository.listOrders(current().auth!!.userId.toLong())},"Read existing orders")
            .map{contract.orderId(contract.validateOrder(it,current().auth!!.userId.toLong()))}.toSet()
        save(journal.copy(phase="ORDER_PENDING",beforeOrderIds=before))
        val id=contract.createdOrderId(data(performRequest(CheckoutOperation.CREATE_ORDER){repository.createOrder(quote.quoteId,number)},"Create order"))
        check(id !in before)
        save(journal.copy(phase="ORDER_RETURNED",orderId=id)) // durable before payment screen or any STK call
        val row=contract.verifyOrder(data(performRequest(CheckoutOperation.READ_ORDERS){repository.readOrder(id)},"Read created order"),current().auth!!.userId.toLong(),journal.items,quote)
        check(contract.orderId(row)==id)
        check(contract.isPayable(row)){"The returned order is not payable."}
        save(journal.copy(phase="ORDER_VERIFIED"))
        mutable.update{it.copy(order=row,phone=number,quote=quote,uncertain=false,justCreated=true,notice="Order verified. Review the amount and phone number before confirming payment.")}
    }
    /** Discard a stuck or mismatched checkout journal so the user can start fresh. Safe only when no order has been created. */
    fun resetJournal() {
        if (mutable.value.busy) return
        // Never discard a journal that has an order ID — that order may exist on the server.
        if (journal.orderId != null || journal.phase in setOf("ORDER_PENDING", "ORDER_RETURNED", "ORDER_VERIFIED")) return
        save(CheckoutJournal())
        mutable.value = CheckoutState<OrderData>(notice = "Checkout reset. You can now start a fresh quote.")
    }
    /** One-shot flag consumed by the UI to navigate to a freshly created order. Recovered orders never trigger it. */
    fun consumeJustCreated() { mutable.update { it.copy(justCreated = false) } }

    /** Explicit new purchase only after the previous owned order is paid/refunded/cancelled. No server mutation. */
    fun startNewBasket()=launch {
        check(journal.phase=="ORDER_VERIFIED" && journal.orderId!=null)
        val row=contract.validateOrder(data(performRequest(CheckoutOperation.READ_ORDERS){repository.readOrder(journal.orderId!!)},"Check previous order"),current().auth!!.userId.toLong())
        check(contract.orderId(row)==journal.orderId && contract.isFinished(row)){"Finish or resolve your existing order before starting another checkout."}
        save(CheckoutJournal())
        mutable.value=CheckoutState<OrderData>(busy=true,notice="Ready for a new checkout. Review your cart before uploading it.")
    }
    /** Read-only reconciliation. Never uses similarity alone to adopt an order after an unknown create result. */
    fun recover()=launch {
        if(journal.orderId!=null){
            val row=data(performRequest(CheckoutOperation.READ_ORDERS){repository.readOrder(journal.orderId!!)},"Recover order")
            contract.verifyOrder(row,current().auth!!.userId.toLong(),journal.items,checkNotNull(journal.quote))
            check(contract.orderId(row)==journal.orderId)
            if(contract.isFinished(row) || contract.isAwaitingPayment(row)){
                // Owned order is settled or has an outstanding payment attempt awaiting admin
                // confirmation. It remains visible in Orders; clear the checkout journal so a
                // new basket can start instead of re-hitting the "earlier checkout is unresolved"
                // guard. A pending payment is the buyer's responsibility, not a cancellation.
                save(CheckoutJournal())
                mutable.update{it.copy(order=null,uncertain=false,needsRecovery=false,justCreated=false,notice="Your order and payment are in progress. It stays visible in Orders while the admin confirms payment.")}
                return@launch
            }
            save(journal.copy(phase="ORDER_VERIFIED"))
            mutable.update{it.copy(order=row,uncertain=false,needsRecovery=false,justCreated=false,notice="Existing order recovered. No new order or payment was submitted.")}
        }else if(journal.phase=="CHECKOUT_ACKNOWLEDGED" && journal.request!=null && journal.items.isNotEmpty() && journal.expectedSubtotal!=null){
            // The acknowledged transition is durable. ACTIVE cart GET is not its status lookup.
            refreshQuote(journal.request!!)
        }else if(journal.phase=="ORDER_PENDING"){
            val rows=data(performRequest(CheckoutOperation.READ_ORDERS){repository.listOrders(current().auth!!.userId.toLong())},"Reconcile orders")
                .map{contract.validateOrder(it,current().auth!!.userId.toLong())}
            val candidates=rows.count{contract.orderId(it) !in journal.beforeOrderIds && runCatching{contract.verifyOrder(it,current().auth!!.userId.toLong(),journal.items,journal.quote!!)}.isSuccess}
            mutable.update{it.copy(uncertain=true,notice="Order submission remains unresolved. $candidates matching new order(s) found. Inspect Orders and contact support; do not create another order.")}
        }else {
            val cart=data(performRequest(CheckoutOperation.READ_CART){repository.readCart(current().auth!!.userId.toLong())},"Reconcile cart").let(::metadata)
            if(journal.phase in setOf("CHECKOUT_PENDING","CHECKOUT_ACKNOWLEDGED") && journal.cartId!=null &&
                cart.id==journal.cartId && cart.status=="CHECKOUT_PROCESSING" && journal.request!=null &&
                journal.items.isNotEmpty() && journal.expectedSubtotal!=null) {
                refreshQuote(journal.request!!);return@launch
            }
            mutable.update{it.copy(uncertain=journal.phase !in setOf("NONE","QUOTE_READY"),notice="Cart status refreshed. An uncertain upload cannot be retried without authoritative item reconciliation.")}
        }
    }
    companion object {private val safeMessages=setOf("The created order does not match your intended products and quantities. Do not pay; contact support.","The order amount differs from the reviewed quote. Do not pay; refresh Orders.","The order delivery location differs from your selection. Do not pay; contact support.","Order line amounts could not be verified. Do not pay; contact support.","The server subtotal differs from your selected items. Refresh product prices and reconcile your server cart before creating an order.","An earlier checkout is unresolved. Check status instead of submitting again.","A different cart checkout is already in progress.","Sign in again to continue checkout.","Finish or resolve your existing order before starting another checkout.")}
}
