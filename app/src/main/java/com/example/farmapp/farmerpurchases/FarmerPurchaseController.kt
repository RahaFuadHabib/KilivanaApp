package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyer.*
import com.example.farmapp.checkout.*
import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import com.example.farmapp.marketplace.MarketplaceRepository
import com.example.farmapp.network.*
import com.example.farmapp.orders.*
import com.example.farmapp.payments.*
import com.example.farmapp.tracking.DeliveryJobDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

internal interface FarmerBasketStore {
    fun basket(): Map<Long, Int>
    fun labels(): Map<Long, String> = emptyMap()
    fun saveLabels(labels: Map<Long, String>) {}
    fun saveBasket(items: Map<Long, Int>)
}

internal data class FarmerPurchaseState(val items: Map<Long, Int> = emptyMap(),
    val products: List<CanonicalFarmerProduct> = emptyList(), val busy: Boolean = false,
    val basketVerified: Boolean = false, val notice: String? = null,
    val addresses: List<BuyerAddressDto> = emptyList(), val stations: List<BuyerPickupStation> = emptyList(),
    val savedStations: List<BuyerSavedPickup> = emptyList(), val selection: DeliverySelection? = null,
    val orders: List<BuyerOrderDto> = emptyList(), val selected: BuyerOrderDto? = null,
    val jobs: List<DeliveryJobDto> = emptyList(), val tracking: List<String> = emptyList(),
    val serverCartId: Long? = null, val labels: Map<Long, String> = emptyMap(),
    val cartActionId: Long? = null, val cartErrorId: Long? = null, val cartWriteBlocked: Boolean = false,
    val noticeIsError: Boolean = false) {
    val estimatedSubtotal: BigDecimal? get() = if (!basketVerified || products.size != items.size) null else
        products.fold(BigDecimal.ZERO) { sum, product -> sum + product.unitPrice * (items[product.productId.toLong()] ?: 0).toBigDecimal() }
}

/** Presentation orchestration only. Checkout and payment state machines remain the Phase 3 engines. */
internal class FarmerPurchaseController(
    private val marketplace: MarketplaceRepository, private val orders: FarmerCheckoutRepository,
    private val addresses: FarmerAddressRepository, private val pickup: FarmerPickupRepository,
    private val tracking: FarmerTrackingRepository, payment: FarmerPaymentRepository,
    sessions: SessionStore, private val expected: SessionSnapshot,
    journal: CheckoutJournalStore, attempts: PaymentAttemptStore,
    private val basket: FarmerBasketStore, private val scope: CoroutineScope,
    private val cart: FarmerCartRepository? = null
) {
    val backendCartEnabled get() = cart != null
    private val requests = FarmerPurchaseRequests(sessions, expected)
    val checkout = CheckoutController(orders, FarmerCheckoutContract, sessions, expected, FarmerPurchaserPolicy, journal, scope)
    val payments = PurchaserPaymentController(payment, FarmerPaymentContract, sessions, expected,
        FarmerPurchaserPolicy, attempts, scope, paymentAllowed = { false })
    private val mutable = MutableStateFlow(FarmerPurchaseState())
    private var basketReadable = true
    val state = mutable.asStateFlow()
    init {
        try { requests.current(); mutable.value = FarmerPurchaseState(items = basket.basket(), labels = basket.labels(), cartWriteBlocked = cart?.hasPendingWrite() == true) }
        catch (_: Exception) { basketReadable = false; mutable.value = FarmerPurchaseState(notice = "Saved basket could not be verified. No records were changed.") }
    }
    private fun eligible(product: CanonicalFarmerProduct, quantity: Int) {
        requests.current()
        check(basketReadable) { "Saved basket could not be verified. No records were changed." }
        require(product.productId.toLongOrNull()?.let { it > 0 } == true && product.sellerId != requests.purchaserId().toString()) {
            "You cannot purchase your own listing."
        }
        FarmerPurchaserPolicy.validateQuantity(product, quantity)
    }
    fun add(product: CanonicalFarmerProduct, quantity: Int) {
        if (mutable.value.busy) return
        if (cart != null) {
            read(product.productId.toLongOrNull()) {
                eligible(product, quantity)
                val id = product.productId.toLong()
                val combined = Math.addExact(mutable.value.items[id] ?: 0, quantity)
                eligible(product, combined)
                val cartId = cart.add(id, quantity)
                val items = mutable.value.items + (id to combined)
                val labels = mutable.value.labels + (id to product.name)
                basket.saveLabels(labels)
                basket.saveBasket(items)
                mutable.update { it.copy(items = items, labels = labels, serverCartId = cartId,
                    products = it.products.filterNot { row -> row.productId == product.productId } + product,
                    basketVerified = false, notice = "${product.name} added to cart.", noticeIsError = false) }
            }
            return
        }
        try {
            eligible(product, quantity)
            val items = mutable.value.items + (product.productId.toLong() to quantity)
            basket.saveBasket(items)
            mutable.update { it.copy(items = items, products = emptyList(), basketVerified = false, notice = "Saved to your local basket. No backend cart was changed.") }
        } catch (e: Exception) { mutable.update { it.copy(notice = e.message) } }
    }
    fun remove(id: Long) {
        if (mutable.value.busy) return
        if (cart != null) {
            read {
                check(basketReadable)
                val cartId = cart.remove(id)
                val items = mutable.value.items - id
                basket.saveBasket(items)
                val labels = mutable.value.labels - id
                basket.saveLabels(labels)
                mutable.update { it.copy(items = items, labels = labels, serverCartId = cartId,
                    products = it.products.filterNot { row -> row.productId == id.toString() }, basketVerified = false, selection = null) }
            }
            return
        }
        try {
            requests.current(); check(basketReadable); val items = mutable.value.items - id; basket.saveBasket(items)
            mutable.update { it.copy(items = items, products = emptyList(), basketVerified = false, selection = null) }
        } catch (e: Exception) { mutable.update { it.copy(notice = e.message) } }
    }
    private fun read(productId: Long? = null, action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, notice = null, noticeIsError = false, cartActionId = productId, cartErrorId = null) }
        scope.launch {
            try { requests.current(); action(); requests.current() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (runCatching { requests.current() }.isFailure) mutable.value = FarmerPurchaseState(notice = "Session changed. Reopen Supplies after signing in.")
                else mutable.update { it.copy(notice = e.message ?: "Purchasing data is unavailable.", basketVerified = false,
                    noticeIsError = true, cartErrorId = productId, cartWriteBlocked = cart?.hasPendingWrite() == true) }
            } finally { mutable.update { it.copy(busy = false, cartActionId = null) } }
        }
    }
    fun reviewBasket() = read {
        if (cart != null) {
            val server = cart.read()
            mutable.update { it.copy(serverCartId = server.id) }
            check(!cart.hasPendingWrite()) { "A cart write is unresolved. Cart metadata cannot prove item quantities. Ask support to reconcile it before another write." }
        }
        val items = mutable.value.items
        mutable.update { it.copy(basketVerified = false) }
        val products = items.map { (id, qty) ->
            marketplace.detail(expected, id).also { requests.current(); eligible(it, qty) }
        }
        requests.current()
        mutable.update { it.copy(products = products, basketVerified = true) }
    }
    fun delivery() = read {
        val owned = addresses.list().requirePurchaseData()
        val available = if (pickup.availability().requirePurchaseData()) pickup.available().requirePurchaseData().filter { it.isAvailable == true } else emptyList()
        val saved = pickup.saved().requirePurchaseData()
        requests.current()
        mutable.update { it.copy(addresses = owned, stations = available, savedStations = saved, selection = null) }
    }
    fun selectAddress(id: Long) {
        if (mutable.value.busy) return
        try {
            requests.current()
            val address = mutable.value.addresses.single { it.id == id }
            check(address.latitude != null && address.longitude != null) { "This address needs verified coordinates before delivery can be quoted." }
            mutable.update { it.copy(selection = DeliverySelection.Door(id), notice = null) }
        } catch (e: Exception) { mutable.update { it.copy(selection = null, notice = e.message) } }
    }
    fun selectStation(platformId: Long) = read {
        check(mutable.value.stations.any { it.id == platformId }) { "Select an available platform station." }
        val fresh = pickup.detail(platformId).requirePurchaseData()
        check(fresh.isAvailable == true)
        mutable.update { it.copy(selection = DeliverySelection.Station(platformId)) }
    }
    fun history() = read {
        val rows = orders.listOrders(requests.purchaserId()).requirePurchaseData()
        requests.current(); mutable.update { it.copy(orders = rows.sortedByDescending { row -> row.id }, selected = null) }
    }
    fun openOrder(id: Long) = read {
        val order = orders.readOrder(id).requirePurchaseData()
        requests.current(); mutable.update { it.copy(selected = order, jobs = emptyList(), tracking = emptyList()) }
        payments.refresh(id) // Shared read-only payment inspection, never initiation.
    }
    fun track() = read {
        val id = mutable.value.selected?.id ?: error("Select a purchase first.")
        val jobs = tracking.jobs(id).requirePurchaseData()
        val lines = mutableListOf<String>()
        for (job in jobs) {
            val events = tracking.events(job).requirePurchaseData()
            lines += events.map { "Delivery #${job.id}: ${it.status}" }
            if (job.driverId != null) {
                val response = tracking.location(job)
                if (response.code() == 404) { response.errorBody()?.close(); lines += "Driver location unavailable." }
                else { val fix = response.requirePurchaseData(); lines += "Driver location: ${fix.latitude}, ${fix.longitude}" }
            }
        }
        requests.current(); mutable.update { it.copy(jobs = jobs, tracking = lines) }
    }
}
