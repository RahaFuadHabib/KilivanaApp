package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyer.*
import com.example.farmapp.checkout.*
import com.example.farmapp.marketplace.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalJson
import com.example.farmapp.payments.*
import com.example.farmapp.orders.*
import com.example.farmapp.supplier.supplierAuth
import com.example.farmapp.tracking.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal class FarmerPurchaseFixture {
    val origin = "https://farmer-purchases.example.test/api/v1/"
    private val delegate = signedInStore(supplierAuth().copy(role = "FARMER", sessionOrigin = origin))
    val session = MutableStateFlow(delegate.session.value)
    val sessions = object : SessionStore by delegate { override val session = this@FarmerPurchaseFixture.session }
    val expected = session.value
    val requests = mutableListOf<Request>()
    var hook: () -> Unit = {}
    var status = 200
    var handler: (Request) -> String = { request -> when (request.url.encodedPath.removePrefix("/api/v1/")) {
        "products/7" -> productJson
        "orders/seller/42" -> """{"success":true,"data":[{"id":105,"status":"CONFIRMED","total":376.50,"createdAt":"2026-11-12T10:00:00","itemCount":2}]}"""
        "orders/buyer/42" -> """{"success":true,"data":[$orderJson]}"""
        "orders/105" -> """{"success":true,"data":$orderJson}"""
        "users/addresses" -> """{"success":true,"data":[{"id":8,"userId":42,"addressText":"Synthetic farm","latitude":-1.2,"longitude":36.8}]}"""
        "pickup-stations/availability" -> """{"success":true,"data":true}"""
        "pickup-stations/available" -> """{"success":true,"data":[$stationJson]}"""
        "pickup-stations/3" -> """{"success":true,"data":$stationJson}"""
        "pickup-stations/my-stations" -> """{"success":true,"data":[{"id":91,"userId":42,"pickupStationId":3,"pickupStationAvailable":true,"isDefault":true}]}"""
        "payments/stk-push/buyer" -> """{"success":true,"data":{"paymentId":1001,"orderId":105,"amount":426.50,"status":"pending"}}"""
        "payments/order/105" -> """{"success":true,"data":[]}"""
        "logistics/jobs/order/105" -> """{"success":true,"data":[{"id":10,"orderId":105,"driverId":9,"status":"assigned"}]}"""
        "logistics/jobs/10/tracking" -> """{"success":true,"data":[{"id":1,"logisticsJobId":10,"status":"assigned"}]}"""
        "tracking/10/location" -> """{"success":true,"data":{"tripId":10,"orderId":105,"driverId":9,"latitude":-1.2,"longitude":36.8}}"""
        else -> error("Unexpected API call: ${request.url.encodedPath}")
    } }
    val retrofit = Retrofit.Builder().baseUrl(origin).addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false)
            .addInterceptor(AuthTokenInterceptor(origin.toHttpUrl(), sessions, RefreshCoordinator(StubAuthService(), sessions)))
            .addInterceptor { chain ->
                requests += chain.request(); val body = handler(chain.request()); hook()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()).build()
    inline fun <reified T> api(): T = retrofit.create(T::class.java)
    val orders = FarmerCheckoutRepository(api(), sessions, expected)
    val addresses = FarmerAddressRepository(api(), sessions, expected)
    val pickup = FarmerPickupRepository(api(), sessions, expected)
    val payment = FarmerPaymentRepository(api(), sessions, expected)
    val tracking = FarmerTrackingRepository(api(), orders, sessions, expected)
    val marketplace = MarketplaceRepository(api(), sessions)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var basketValues: Map<Long, Int> = emptyMap()
    var basketLabels: Map<Long, String> = emptyMap()
    var corruptBasket = false
    var basketWrites = 0
    val basket = object : FarmerBasketStore {
        override fun labels() = basketLabels
        override fun saveLabels(labels: Map<Long, String>) { basketLabels = labels }
        override fun basket(): Map<Long, Int> { check(!corruptBasket); return basketValues }
        override fun saveBasket(items: Map<Long, Int>) { basketWrites++; basketValues = items }
    }
    var journalValue = CheckoutJournal()
    var journalWrites = 0
    val journal = object : CheckoutJournalStore {
        override fun read() = journalValue
        override fun write(value: CheckoutJournal) { journalWrites++; journalValue = value }
    }
    val reservations = mutableSetOf<Long>()
    val attempts = object : PaymentAttemptStore {
        override fun started(orderId: Long) = orderId in reservations
        override fun reserve(orderId: Long) = reservations.add(orderId)
    }
    fun controller(cart: FarmerCartRepository? = null) = FarmerPurchaseController(marketplace, orders, addresses, pickup, tracking, payment,
        sessions, expected, journal, attempts, basket, scope, cart)
    fun stale() { session.value = expected.copy(generation = expected.generation + 1) }
    suspend fun idle(controller: FarmerPurchaseController) {
        withTimeout(5000) { while (controller.state.value.busy || controller.payments.state.value.busy) delay(5) }
    }
    companion object {
        const val orderJson = """{"id":105,"buyerId":42,"status":"placed","total":426.50,"subtotal":376.50,"deliveryFee":50,"paymentStatus":"pending","pickupStationId":3,"items":[{"id":1,"productId":7,"sellerId":9,"productName":"Synthetic seed","quantity":3,"unitPrice":125.50,"subtotal":376.50}]}"""
        const val productJson = """{"success":true,"data":{"id":7,"sellerId":9,"sellerType":"supplier","categoryId":2,"name":"Synthetic seed","unit":"bag","price":125.50,"stockQty":12,"reservedQty":2,"soldQty":0,"minimumOrderQty":1,"status":"active","images":[]}}"""
        const val stationJson = """{"id":3,"name":"Synthetic station","address":"Farm Road","region":"Synthetic region","latitude":-1.2,"longitude":36.8,"isAvailable":true}"""
    }
}
