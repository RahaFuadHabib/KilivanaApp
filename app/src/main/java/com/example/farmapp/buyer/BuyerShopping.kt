package com.example.farmapp.buyer

import com.example.farmapp.data.model.canonical.CanonicalCategoryDto
import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import com.example.farmapp.data.model.canonical.CanonicalProductStatus
import com.example.farmapp.marketplace.MarketplaceRepository
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.security.MessageDigest

internal const val BUYER_ADDRESS_NOTICE = "Saved addresses come from your account. Add a delivery address from Checkout. Editing and deleting are not offered here."

internal fun buyerScope(origin: String, userId: String): String = MessageDigest.getInstance("SHA-256")
    .digest((origin + "|BUYER|" + userId).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Only product IDs and quantities persist. No private product snapshots, addresses or tokens. */
internal data class BuyerSelection(val saved: Set<Long> = emptySet(), val cart: Map<Long, Int> = emptyMap())
internal interface BuyerSelectionStore {
    fun read(): BuyerSelection
    fun write(value: BuyerSelection)
}
internal interface BuyerCatalog {
    suspend fun page(session: SessionSnapshot, query: String, category: Long?, page: Int): Pair<List<CanonicalFarmerProduct>, Boolean>
    suspend fun categories(session: SessionSnapshot): List<CanonicalCategoryDto>
    suspend fun detail(session: SessionSnapshot, id: Long): CanonicalFarmerProduct
}
internal class ExistingBuyerCatalog(private val repository: MarketplaceRepository) : BuyerCatalog {
    override suspend fun page(session: SessionSnapshot, query: String, category: Long?, page: Int) = repository.page(session, query, category, page)
    override suspend fun categories(session: SessionSnapshot) = repository.categories(session)
    override suspend fun detail(session: SessionSnapshot, id: Long) = repository.detail(session, id)
}
internal fun buyerQuantity(product: CanonicalFarmerProduct, quantity: Int): Int {
    check(product.sector == "FARM_PRODUCE" && product.status == CanonicalProductStatus.ACTIVE) { "Only available Farmer produce can be added." }
    check(quantity > 0 && BigDecimal.valueOf(quantity.toLong()) >= product.minimumOrderQuantity &&
        BigDecimal.valueOf(quantity.toLong()) <= product.availableQuantity) { "Quantity must meet the minimum order and available stock." }
    return quantity
}
internal fun buyerLineTotal(product: CanonicalFarmerProduct, quantity: Int): BigDecimal =
    product.unitPrice.multiply(BigDecimal.valueOf(buyerQuantity(product, quantity).toLong()))

internal data class BuyerShoppingState(
    val products: List<CanonicalFarmerProduct> = emptyList(), val categories: List<CanonicalCategoryDto> = emptyList(),
    val busy: Boolean = false, val loaded: Boolean = false, val error: String? = null, val categoryError: String? = null,
    val query: String = "", val category: Long? = null, val page: Int = 0, val last: Boolean = true,
    val detail: CanonicalFarmerProduct? = null, val selectedId: Long? = null, val detailBusy: Boolean = false, val detailError: String? = null,
    val selection: BuyerSelection = BuyerSelection(), val known: Map<Long, CanonicalFarmerProduct> = emptyMap(),
    val selectionBusy: Boolean = false, val unavailable: Set<Long> = emptySet(), val notice: String? = null
)

/** Buyer-only controller. Pure coroutine/state boundary allows synthetic transports in unit/UI tests. */
internal class BuyerShoppingController(
    private val catalog: BuyerCatalog, private val sessions: SessionStore, private val expected: SessionSnapshot,
    private val persistence: BuyerSelectionStore, private val scope: CoroutineScope,
    private val monotonicNanos: () -> Long = System::nanoTime
) {
    private val mutable = MutableStateFlow(BuyerShoppingState())
    val state = mutable.asStateFlow()
    private val lastAdd = mutableMapOf<Long, Long>()
    private var detailRequest = 0L
    private var attemptedQuery = ""
    private var attemptedCategory: Long? = null

    private fun current(): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId &&
            now.auth.sessionOrigin == expected.auth?.sessionOrigin) { "Sign in again to load your shopping data." }
        return now
    }
    private fun checked(row: CanonicalFarmerProduct, id: Long = row.productId.toLong()): CanonicalFarmerProduct {
        check(row.productId == id.toString() && row.sector == "FARM_PRODUCE" && row.status == CanonicalProductStatus.ACTIVE && row.availableQuantity.signum() > 0)
        return row
    }
    fun start() {
        if (!isCurrent()) return
        try {
            val stored = persistence.read()
            mutable.update { it.copy(selection = BuyerSelection(stored.saved.filter { id -> id > 0 }.take(100).toSet(),
                stored.cart.filter { (id, qty) -> id > 0 && qty > 0 }.entries.take(100).associate { e -> e.toPair() })) }
        } catch (_: Exception) { mutable.update { it.copy(notice = "Saved shopping selections could not be loaded.") } }
        refresh()
        refreshSelections()
    }
    fun refresh(query: String = attemptedQuery, category: Long? = attemptedCategory, more: Boolean = false) {
        if (mutable.value.busy || more && mutable.value.last) return
        try { current() } catch (_: Exception) { return }
        val before = mutable.value
        // Continue the displayed result set after a failed filter request, never mix filters.
        val effectiveQuery = if (more) before.query else query.trim()
        val effectiveCategory = if (more) before.category else category
        if (!more) { attemptedQuery = effectiveQuery; attemptedCategory = effectiveCategory }
        val page = if (more) before.page + 1 else 0
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                if (!more) {
                    try {
                        val rows = catalog.categories(current())
                        current(); mutable.update { it.copy(categories = rows, categoryError = null) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { current(); mutable.update { it.copy(categoryError = "Categories unavailable. Products can still be loaded.") } }
                }
                val (rows, last) = catalog.page(current(), effectiveQuery, effectiveCategory, page)
                rows.forEach { checked(it) }
                check(rows.map { it.productId }.distinct().size == rows.size)
                current()
                mutable.update { it.copy(products = (if (more) before.products + rows else rows).distinctBy { p -> p.productId },
                    known = it.known + rows.associateBy { p -> p.productId.toLong() }, loaded = true,
                    query = effectiveQuery, category = effectiveCategory, page = page, last = last, error = null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (isCurrent()) mutable.update { it.copy(error = "Products could not be refreshed. Any previous results are still shown. Retry to apply the requested filters.") } }
            finally { if (isCurrent()) mutable.update { it.copy(busy = false) } }
        }
    }
    private fun isCurrent() = runCatching { current(); true }.getOrDefault(false)
    fun open(id: Long) {
        if (!isCurrent()) return
        val request = ++detailRequest
        mutable.update { it.copy(selectedId = id, detail = null, detailBusy = true, detailError = null) }
        scope.launch {
            try {
                val row = checked(catalog.detail(current(), id), id)
                current()
                if (request == detailRequest) mutable.update { it.copy(detail = row, known = it.known + (id to row), unavailable = it.unavailable - id) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (isCurrent() && request == detailRequest) mutable.update { it.copy(detailError = "This product could not be verified. Refresh or return to shopping.") } }
            finally { if (isCurrent() && request == detailRequest) mutable.update { it.copy(detailBusy = false) } }
        }
    }
    fun closeDetail() { detailRequest++; mutable.update { it.copy(selectedId = null, detail = null, detailBusy = false, detailError = null) } }
    fun refreshSelections() {
        if (mutable.value.selectionBusy || !isCurrent()) return
        val ids = (mutable.value.selection.saved + mutable.value.selection.cart.keys).toList()
        mutable.update { it.copy(selectionBusy = true) }
        scope.launch {
            try {
                for (id in ids) {
                    try {
                        val row = checked(catalog.detail(current(), id), id)
                        current(); mutable.update { it.copy(known = it.known + (id to row), unavailable = it.unavailable - id) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { if (!isCurrent()) return@launch; mutable.update { it.copy(unavailable = it.unavailable + id) } }
                }
            } finally { if (isCurrent()) mutable.update { it.copy(selectionBusy = false) } }
        }
    }
    private fun save(value: BuyerSelection) {
        current()
        check(value.saved.size <= 100 && value.cart.size <= 100) { "Keep up to 100 items in each shopping list." }
        persistence.write(value)
        mutable.update { it.copy(selection = value) }
    }
    private fun edit(action: () -> Unit) {
        try { current(); action() }
        catch (_: Exception) { if (isCurrent()) mutable.update { it.copy(notice = "Shopping selection was not saved. Check quantity, minimum order and stock, then retry.") } }
    }
    fun toggleSaved(id: Long) = edit {
        check(id > 0)
        val before = mutable.value.selection
        save(before.copy(saved = if (id in before.saved) before.saved - id else before.saved + id))
    }
    fun add(product: CanonicalFarmerProduct, quantity: Int) = edit {
        checked(product)
        check(quantity > 0)
        val id = product.productId.toLong()
        val now = monotonicNanos()
        val previous = lastAdd[id]
        if (previous != null && now - previous < 600_000_000L) return@edit
        check(id !in mutable.value.unavailable)
        val before = mutable.value.selection
        val total = Math.addExact(before.cart[id] ?: 0, quantity)
        buyerQuantity(product, total)
        save(before.copy(cart = before.cart + (id to total)))
        lastAdd[id] = now
        mutable.update { it.copy(known = it.known + (id to product), notice = "Added to cart.") }
    }
    fun setQuantity(id: Long, quantity: Int) = edit {
        check(id !in mutable.value.unavailable)
        val row = mutable.value.known[id] ?: error("Reload this item first")
        buyerQuantity(row, quantity)
        check(id in mutable.value.selection.cart)
        save(mutable.value.selection.copy(cart = mutable.value.selection.cart + (id to quantity)))
    }
    fun remove(id: Long) = edit { save(mutable.value.selection.copy(cart = mutable.value.selection.cart - id)) }
    fun clearCart() = edit { save(mutable.value.selection.copy(cart = emptyMap())) }
    fun dismissNotice() { mutable.update { it.copy(notice = null) } }
}
