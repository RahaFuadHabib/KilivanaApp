package com.example.farmapp.buyers

import android.content.Context
import androidx.lifecycle.*
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyers.data.*
import com.example.farmapp.buyer.*
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.orders.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.math.BigDecimal
import java.time.Instant

internal data class CartUpload(val productId:Long,val quantity:Int)
internal data class QuoteOrderRequest(val quoteId:String,val phoneNumber:String) { override fun toString()="QuoteOrderRequest(redacted)" }
internal interface BrianCheckoutApi {
    @GET("carts/buyer/{buyerId}") suspend fun cart(@Path("buyerId") buyerId:Long,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>>
    @POST("cart/items") suspend fun add(@Body item:CartUpload,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>>
    @PUT("carts/{cartId}/items/{productId}") suspend fun quantity(@Path("cartId") cartId:Long,@Path("productId") productId:Long,@Query("quantity") quantity:Int,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>>
    @POST("checkout") suspend fun checkout(@Tag session:SessionSnapshot):Response<CanonicalApiResponse<Unit>>
    @POST("checkout/delivery-quote") suspend fun quote(@Body request:DeliveryQuoteRequest,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<DeliveryQuote>>
    @POST("checkout/order") suspend fun order(@Body request:QuoteOrderRequest,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonElement>>
}
/** Journal contains no credentials or phone number. Reserve BEFORE every write; never replay an unknown outcome. */
internal data class CheckoutJournal(val phase:String="NONE",val items:Map<Long,Int> = emptyMap(),val expectedSubtotal:BigDecimal?=null,
    val cartId:Long?=null,val quote:DeliveryQuote?=null,val request:DeliveryQuoteRequest?=null,val orderId:Long?=null,val beforeOrderIds:Set<Long> = emptySet()) {
    override fun toString()="CheckoutJournal(redacted)"
}
internal interface CheckoutJournalStore { fun read():CheckoutJournal; fun write(value:CheckoutJournal) }
internal class CheckoutPreferences(context:Context,user:String):CheckoutJournalStore {
    private val prefs=context.applicationContext.getSharedPreferences("buyer-checkout-"+buyerScope(BuildConfig.API_BASE_URL,user),Context.MODE_PRIVATE)
    private var version=-1L
    override fun read():CheckoutJournal=synchronized(lock){
        version=prefs.getLong("version",0)
        prefs.getString("journal",null)?.let{runCatching{CanonicalJson.gson.fromJson(it,CheckoutJournal::class.java)}.getOrElse{CheckoutJournal("UNRESOLVED")}} ?: CheckoutJournal()
    }
    override fun write(value:CheckoutJournal)=synchronized(lock){
        check(version>=0 && prefs.getLong("version",0)==version){"Another checkout changed. Reopen checkout before continuing."}
        check(prefs.edit().putString("journal",CanonicalJson.gson.toJson(value)).putLong("version",version+1).commit());version++;Unit
    }
    companion object{private val lock=Any()}
}
internal data class BrianCheckoutState(val busy:Boolean=false,val quote:DeliveryQuote?=null,val order:BuyerOrderDto?=null,
    val notice:String?=null,val uncertain:Boolean=false,val phone:String="") { override fun toString()="BrianCheckoutState(redacted)" }
internal fun verifiedCheckoutOrderId(data:JsonElement):Long {
    // The deployed schema is ApiResponse<Long>. Reject invented wrapper fallbacks.
    check(data.isJsonPrimitive && data.asJsonPrimitive.isNumber){"Order response did not contain the documented numeric ID."}
    return data.asBigDecimal.longValueExact().also{check(it>0)}
}
internal fun verifyPurchasedOrder(row:BuyerOrderDto,buyer:Long,items:Map<Long,Int>,quote:DeliveryQuote):BuyerOrderDto {
    validateBuyerOrder(row,buyer)
    val lines=row.items!!
    check(lines.map{it.productId}.distinct().size==lines.size && lines.associate{it.productId!! to it.quantity!!}==items){"The created order does not match your intended products and quantities. Do not pay; contact support."}
    check(row.total!!.compareTo(quote.total)==0 && row.subtotal?.compareTo(quote.subtotal)==0 && row.deliveryFee?.compareTo(quote.deliveryFee)==0){"The order amount differs from the reviewed quote. Do not pay; refresh Orders."}
    check(if(quote.deliveryMethod=="DOOR_DELIVERY")row.addressId==quote.addressId && row.pickupLocation==null else row.addressId==null && row.pickupLocation==quote.pickupLocation){"The order delivery location differs from your selection. Do not pay; contact support."}
    check(lines.all{it.subtotal==null || it.subtotal.signum()>=0} && lines.fold(BigDecimal.ZERO){sum,line->sum+(line.subtotal ?: line.unitPrice!!.multiply(line.quantity!!.toBigDecimal()))}.compareTo(row.subtotal)==0){"Order line amounts could not be verified. Do not pay; contact support."}
    return row
}
internal class BrianCheckoutController(private val api:BrianCheckoutApi,private val orders:BuyerOrdersApi,private val sessions:SessionStore,
    private val expected:SessionSnapshot,private val disk:CheckoutJournalStore,private val scope:CoroutineScope,private val now:()->Instant={Instant.now()}) {
    private var journal=runCatching{disk.read()}.getOrElse{CheckoutJournal("UNRESOLVED")}
    private val mutable=MutableStateFlow(BrianCheckoutState(quote=journal.quote.takeIf{journal.phase=="QUOTE_READY"},uncertain=journal.phase !in setOf("NONE","QUOTE_READY","ORDER_VERIFIED"),
        notice=if(journal.phase=="NONE")null else "An earlier checkout is saved. Check its status before submitting anything again."))
    val state=mutable.asStateFlow()
    fun matchesCart(items:Map<Long,Int>)=journal.items==items
    private fun current():SessionSnapshot=sessions.session.value.also{
        check(it.generation==expected.generation && !it.loggingOut && !it.isDemo && it.auth?.role=="BUYER" && it.auth.backendContract=="canonical" && it.auth.userId==expected.auth?.userId && it.auth.sessionOrigin==expected.auth?.sessionOrigin){"Sign in again to continue checkout."}
    }
    private fun save(value:CheckoutJournal){disk.write(value);journal=value}
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
    private fun metadata(value:JsonObject):BuyerCartMetadata=buyerCartEvidence(value,current().auth!!.userId.toLong()).metadata
    private fun launch(action:suspend()->Unit){
        if(mutable.value.busy)return
        mutable.update{it.copy(busy=true,notice=null)}
        scope.launch{try{current();action()}catch(e:CancellationException){throw e}catch(e:Exception){
            if(e is Rejected && e.status in setOf(400,401,403,404,409,422,429)) {
                // Only documented, definite rejections can be resubmitted by another explicit user action.
                if(journal.phase in setOf("UPLOAD_PENDING","QUANTITY_PENDING"))runCatching{save(journal.copy(phase="CART_REJECTED"))}
                if(journal.phase=="ORDER_PENDING" && e.status!=409)runCatching{save(journal.copy(phase="QUOTE_READY"))}
            }
            val pending=journal.phase !in setOf("NONE","QUOTE_READY","ORDER_VERIFIED")
            mutable.update{it.copy(notice=if(e is Rejected)e.message else when(e){
                is java.io.IOException->"Connection interrupted. The last operation may have reached the server. Check checkout status; no write was retried."
                else->e.message?.takeIf{msg->msg in safeMessages} ?: "Checkout could not be verified. Check status before submitting again."},uncertain=pending,quote=it.quote.takeUnless{pending})}
        }finally{mutable.update{it.copy(busy=false)}}}
    }
    fun prepare(items:Map<Long,Int>,subtotal:BigDecimal,request:DeliveryQuoteRequest)=launch {
        check(items.isNotEmpty() && items.keys.all{it>0} && items.values.all{it>0} && subtotal.signum()>0)
        if(journal.phase=="QUOTE_READY"){
            check(journal.items==items){"A different cart checkout is already in progress."}
            save(journal.copy(expectedSubtotal=subtotal,quote=null))
            mutable.update{it.copy(quote=null)}
            refreshQuote(request);return@launch
        }
        check(journal.phase in setOf("NONE","CART_REJECTED")){"An earlier checkout is unresolved. Check status instead of submitting again."}
        save(CheckoutJournal("NONE",items,subtotal))
        // GET item lines are optional here: upload acknowledgements + quote + exact order readback form the verification chain.
        val response=api.cart(current().auth!!.userId.toLong(),current())
        val cart=if(response.code()==404){response.errorBody()?.close();null}else metadata(data(response,"Read cart"))
        check(cart==null || cart.status=="ACTIVE"){"An earlier checkout is unresolved. Check status instead of submitting again."}
        save(journal.copy(cartId=cart?.id))
        items.toSortedMap().forEach{(id,q)->
            save(journal.copy(phase="UPLOAD_PENDING"))
            val upload=api.add(CartUpload(id,q),current())
            val accepted=if(upload.code()==409){
                upload.errorBody()?.close()
                val existing=journal.cartId ?: metadata(data(api.cart(current().auth!!.userId.toLong(),current()),"Read existing cart")).id!!
                // 409 is a documented rejection, not a timeout. Set NEW quantity; never increment or blindly POST again.
                save(journal.copy(phase="QUANTITY_PENDING",cartId=existing))
                data(api.quantity(existing,id,q,current()),"Set cart quantity")
            }else data(upload,"Upload cart item")
            val verified=metadata(accepted)
            check(verified.status=="ACTIVE" && (journal.cartId==null || verified.id==journal.cartId))
            save(journal.copy(phase="UPLOAD_ACKNOWLEDGED",cartId=verified.id))
        }
        save(journal.copy(phase="CHECKOUT_PENDING",request=request))
        envelope(api.checkout(current()),"Start checkout") // ApiResponse<Void>, not an order.
        save(journal.copy(phase="CHECKOUT_ACKNOWLEDGED"))
        val processing=metadata(data(api.cart(current().auth!!.userId.toLong(),current()),"Verify checkout processing"))
        check(processing.id==journal.cartId && processing.status=="CHECKOUT_PROCESSING")
        save(journal.copy(phase="QUOTE_READY"))
        refreshQuote(request)
    }
    private suspend fun refreshQuote(request:DeliveryQuoteRequest){
        val quote=verifiedQuote(data(api.quote(request,current()),"Delivery quote"),request,now())
        // Detect unexpected existing server products/repricing before order creation; quote is still the authoritative payable amount.
        check(quote.subtotal.compareTo(journal.expectedSubtotal)==0){"The server subtotal differs from your selected items. Refresh product prices and reconcile your server cart before creating an order."}
        save(journal.copy(phase="QUOTE_READY",quote=quote,request=request))
        mutable.update{it.copy(quote=quote,uncertain=false,notice="Review the server-confirmed total before creating your order.")}
    }
    fun create(items:Map<Long,Int>,quoteId:String,phone:String)=launch {
        check(journal.phase=="QUOTE_READY" && journal.items==items && journal.quote?.quoteId==quoteId)
        val quote=verifiedQuote(journal.quote!!,journal.request!!,now())
        val number=buyerMpesaPhone(phone)
        val before=data(orders.orders(current().auth!!.userId.toLong(),current()),"Read existing orders")
            .map{validateBuyerOrder(it,current().auth!!.userId.toLong()).id!!}.toSet()
        save(journal.copy(phase="ORDER_PENDING",beforeOrderIds=before))
        val id=verifiedCheckoutOrderId(data(api.order(QuoteOrderRequest(quote.quoteId,number),current()),"Create order"))
        check(id !in before)
        save(journal.copy(phase="ORDER_RETURNED",orderId=id)) // durable before payment screen or any STK call
        val row=verifyPurchasedOrder(data(orders.order(id,current()),"Read created order"),current().auth!!.userId.toLong(),journal.items,quote)
        check(row.id==id)
        check(row.status in setOf("placed","confirmed")){"The returned order is not payable."}
        save(journal.copy(phase="ORDER_VERIFIED"))
        mutable.update{it.copy(order=row,phone=number,quote=quote,uncertain=false,notice="Order verified. Review the amount and phone number before confirming payment.")}
    }
    /** Explicit new purchase only after the previous owned order is paid/refunded/cancelled. No server mutation. */
    fun startNewBasket()=launch {
        check(journal.phase=="ORDER_VERIFIED" && journal.orderId!=null)
        val row=validateBuyerOrder(data(orders.order(journal.orderId!!,current()),"Check previous order"),current().auth!!.userId.toLong())
        check(row.id==journal.orderId && (buyerPaymentOutcome(row.paymentStatus) in setOf("confirmed","refunded") || row.status=="cancelled")){"Finish or resolve your existing order before starting another checkout."}
        save(CheckoutJournal())
        mutable.value=BrianCheckoutState(busy=true,notice="Ready for a new checkout. Review your cart before uploading it.")
    }
    /** Read-only reconciliation. Never uses similarity alone to adopt an order after an unknown create result. */
    fun recover()=launch {
        if(journal.orderId!=null){
            val row=data(orders.order(journal.orderId!!,current()),"Recover order")
            verifyPurchasedOrder(row,current().auth!!.userId.toLong(),journal.items,checkNotNull(journal.quote))
            check(row.id==journal.orderId);save(journal.copy(phase="ORDER_VERIFIED"))
            mutable.update{it.copy(order=row,uncertain=false,notice="Existing order recovered. No new order or payment was submitted.")}
        }else if(journal.phase=="ORDER_PENDING"){
            val rows=data(orders.orders(current().auth!!.userId.toLong(),current()),"Reconcile orders")
                .map{validateBuyerOrder(it,current().auth!!.userId.toLong())}
            val candidates=rows.count{it.id !in journal.beforeOrderIds && runCatching{verifyPurchasedOrder(it,current().auth!!.userId.toLong(),journal.items,journal.quote!!)}.isSuccess}
            mutable.update{it.copy(uncertain=true,notice="Order submission remains unresolved. $candidates matching new order(s) found. Inspect Orders and contact support; do not create another order.")}
        }else {
            val cart=data(api.cart(current().auth!!.userId.toLong(),current()),"Reconcile cart").let(::metadata)
            if(journal.phase in setOf("CHECKOUT_PENDING","CHECKOUT_ACKNOWLEDGED") && cart.id==journal.cartId && cart.status=="CHECKOUT_PROCESSING") {
                save(journal.copy(phase="QUOTE_READY"));refreshQuote(checkNotNull(journal.request));return@launch
            }
            mutable.update{it.copy(uncertain=journal.phase !in setOf("NONE","QUOTE_READY"),notice="Cart status refreshed. An uncertain upload cannot be retried without authoritative item reconciliation.")}
        }
    }
    companion object {private val safeMessages=setOf("The created order does not match your intended products and quantities. Do not pay; contact support.","The order amount differs from the reviewed quote. Do not pay; refresh Orders.","The order delivery location differs from your selection. Do not pay; contact support.","Order line amounts could not be verified. Do not pay; contact support.","The server subtotal differs from your selected items. Refresh product prices and reconcile your server cart before creating an order.","An earlier checkout is unresolved. Check status instead of submitting again.","A different cart checkout is already in progress.","Sign in again to continue checkout.","Finish or resolve your existing order before starting another checkout.")}
}
internal class BrianCheckoutModel(context:Context):ViewModel(){
    private val expected=TokenManager.session.value
    val controller=BrianCheckoutController(ApiClient.authenticatedService(BrianCheckoutApi::class.java),ApiClient.buyerOrdersApi,TokenManager,expected,CheckoutPreferences(context,expected.auth!!.userId),viewModelScope)
    companion object{fun factory(context:Context)=object:ViewModelProvider.Factory{override fun<T:ViewModel>create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST")return BrianCheckoutModel(context.applicationContext) as T}}}
}
