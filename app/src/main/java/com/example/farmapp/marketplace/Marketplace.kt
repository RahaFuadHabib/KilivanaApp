package com.example.farmapp.marketplace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.ui.screens.ProductImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.util.Locale

data class ProductPage(val content: List<CanonicalProductDto>? = null, val number: Int? = null,
    val last: Boolean? = null, val totalPages: Int? = null)
interface MarketplaceApi {
    @GET("products/search") suspend fun search(@Query("sellerType") type: String, @Query("name") name: String?,
        @Query("categoryId") category: Long?, @Query("page") page: Int, @Query("size") size: Int = 20,
        @Query("sort") sort: String = "id,desc", @Tag session: SessionSnapshot): Response<CanonicalApiResponse<ProductPage>>
    @GET("categories/type/{type}") suspend fun categories(@Path("type") type: String,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalCategoryDto>>>
    @GET("products/{id}") suspend fun detail(@Path("id") id: Long,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>>
}

/** Buyer produce and Farmer inputs share transport, never an unrestricted mixed-role catalogue. */
internal fun marketplaceSeller(role: String): String = when (role) {
    "BUYER" -> "farmer"
    "FARMER" -> "supplier"
    else -> throw IllegalStateException("This role has no purchasing marketplace.")
}
internal fun marketplaceProduct(dto: CanonicalProductDto, sellerType: String): CanonicalFarmerProduct? {
    check(dto.sellerType?.lowercase() == sellerType) { "Marketplace seller type did not match the requested catalogue." }
    val value = dto.toFarmerProduct()
    return value.takeIf { it.status == CanonicalProductStatus.ACTIVE && it.availableQuantity.signum() > 0 }
}

internal class MarketplaceRepository(private val api: MarketplaceApi, private val sessions: SessionStore) {
    fun current(expected: SessionSnapshot): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.backendContract == "canonical" &&
            now.auth?.userId == expected.auth?.userId && now.auth?.role == expected.auth?.role) { "Sign in to reload this marketplace." }
        marketplaceSeller(now.auth!!.role)
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            error(when (response.code()) {
                401, 403 -> "Your account cannot access this marketplace. Sign in again."
                404 -> "This product or marketplace route is unavailable."
                else -> "Marketplace request failed. Previously loaded products are still shown."
            })
        }
        return response.body()?.requireData() ?: error("Incomplete marketplace response.")
    }
    suspend fun page(expected: SessionSnapshot, query: String, category: Long?, page: Int): Pair<List<CanonicalFarmerProduct>, Boolean> {
        val type = marketplaceSeller(current(expected).auth!!.role)
        // Search binds a raw String to the native query, against EnumType.STRING storage.
        // Unlike response JSON (lowercase), that parameter must use the database enum name.
        val result = read(api.search(type.uppercase(Locale.ROOT), query.trim().ifBlank { null }, category, page, session = current(expected)))
        current(expected)
        check(result.number == page && result.last != null && result.content != null) { "Incomplete marketplace pagination." }
        val rows = result.content.mapNotNull { marketplaceProduct(it, type) }
        check(rows.map { it.productId }.distinct().size == rows.size) { "Duplicate marketplace records." }
        return rows to result.last
    }
    suspend fun categories(expected: SessionSnapshot): List<CanonicalCategoryDto> {
        val type = marketplaceSeller(current(expected).auth!!.role)
        val rows = read(api.categories(type.uppercase(Locale.ROOT), current(expected)))
        current(expected)
        check(rows.all { it.id != null && it.id > 0 && !it.name.isNullOrBlank() && it.type?.lowercase() == type && it.active != null })
        return rows.filter { it.active == true }
    }
    suspend fun detail(expected: SessionSnapshot, id: Long): CanonicalFarmerProduct {
        val type = marketplaceSeller(current(expected).auth!!.role)
        val dto = read(api.detail(id, current(expected)))
        current(expected)
        check(dto.id == id)
        return marketplaceProduct(dto, type) ?: error("This listing is no longer available. Refresh the marketplace.")
    }
}

internal data class MarketplaceState(val products: List<CanonicalFarmerProduct> = emptyList(), val categories: List<CanonicalCategoryDto> = emptyList(),
    val busy: Boolean = false, val error: String? = null, val categoryError: String? = null, val query: String = "", val category: Long? = null,
    val page: Int = 0, val last: Boolean = true, val loaded: Boolean = false, val detail: CanonicalFarmerProduct? = null,
    val selectedId: Long? = null, val detailBusy: Boolean = false, val detailError: String? = null)

internal class MarketplaceModel : ViewModel() {
    private val expected = TokenManager.session.value
    private val repository = MarketplaceRepository(ApiClient.marketplaceApi, TokenManager)
    private val mutable = MutableStateFlow(MarketplaceState())
    val state = mutable.asStateFlow()
    private var detailJob: Job? = null
    private var attemptedQuery = ""
    private var attemptedCategory: Long? = null
    init { refresh() }
    fun refresh(query: String = attemptedQuery, category: Long? = attemptedCategory, more: Boolean = false) {
        if (mutable.value.busy || more && mutable.value.last) return
        val before = mutable.value
        val effectiveQuery = if (more) before.query else query.trim()
        val effectiveCategory = if (more) before.category else category
        if (!more) { attemptedQuery = effectiveQuery; attemptedCategory = effectiveCategory }
        val page = if (more) before.page + 1 else 0
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // Category failures must not prevent products from loading.
                if (!more) try { val rows = repository.categories(expected); mutable.update { it.copy(categories = rows, categoryError = null) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.update { it.copy(categoryError = "Categories unavailable. Pull down to retry.") } }
                val (rows, last) = repository.page(expected, effectiveQuery, effectiveCategory, page)
                repository.current(expected)
                mutable.update { it.copy(products = (if (more) before.products + rows else rows).distinctBy { product -> product.productId },
                    query = effectiveQuery, category = effectiveCategory, page = page, last = last, loaded = true, error = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = if (e is IllegalStateException) e.message else "Unable to load products. Check your connection and retry.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun open(id: Long) {
        if (mutable.value.detailBusy) return
        mutable.update { it.copy(selectedId = id, detailBusy = true, detailError = null, detail = it.detail?.takeIf { row -> row.productId == id.toString() }) }
        detailJob = viewModelScope.launch {
            try { val detail = repository.detail(expected, id); mutable.update { if (it.selectedId == id) it.copy(detail = detail) else it } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(detail = null, detailError = if (e is IllegalStateException) e.message else "Product details could not be loaded.") } }
            finally { mutable.update { it.copy(detailBusy = false) } }
        }
    }
    fun close() { detailJob?.cancel(); mutable.update { it.copy(selectedId = null, detail = null, detailBusy = false, detailError = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketplaceScreen(modifier: Modifier = Modifier) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: MarketplaceModel = viewModel(key = "marketplace-${session.generation}")
    val state by model.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(state.query) }
    var categoryMenu by remember { mutableStateOf(false) }
    BackHandler(state.selectedId != null) { model.close() }
    PullToRefreshBox(isRefreshing = state.busy || state.detailBusy,
        onRefresh = { if (state.selectedId != null) model.open(state.selectedId!!) else model.refresh() }, modifier = modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.selectedId != null) {
                item { TextButton(onClick = model::close) { Text("Back to marketplace") } }
                state.detailError?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { model.open(state.selectedId!!) }) { Text("Retry details") } } }
                state.detail?.let { row -> item {
                    MarketplaceProduct(row)
                    Text(row.description.orEmpty(), Modifier.padding(vertical = 12.dp))
                    row.images.sortedBy { it.sortOrder ?: 0 }.forEach { ProductImage(it.url, row.name) }
                    Text("Minimum order: ${row.minimumOrderQuantity.toPlainString()} ${row.unit}")
                    Text("Purchasing is unavailable for this account until the order API authorization is confirmed.", Modifier.padding(top = 12.dp))
                } }
            } else {
                item { Text(if (session.auth?.role == "FARMER") "Supplies marketplace" else "Farmer marketplace", style = MaterialTheme.typography.headlineSmall) }
                item { OutlinedTextField(query, { query = it }, label = { Text("Search products") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { model.refresh(query = query) }, enabled = !state.busy) { Text("Search") }
                        Box {
                            TextButton(onClick = { categoryMenu = true }, enabled = !state.busy) { Text(state.categories.firstOrNull { it.id == state.category }?.name ?: "All categories") }
                            DropdownMenu(categoryMenu, { categoryMenu = false }) {
                                DropdownMenuItem(text = { Text("All categories") }, onClick = { categoryMenu = false; model.refresh(category = null) })
                                state.categories.forEach { category -> DropdownMenuItem(text = { Text(category.name.orEmpty()) }, onClick = { categoryMenu = false; model.refresh(category = category.id) }) }
                            }
                        }
                    }
                }
                state.categoryError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { model.refresh() }, enabled = !state.busy) { Text("Retry marketplace") } } }
                if (state.loaded && !state.busy && state.error == null && state.products.isEmpty()) item { Text("No available published products match your search. Pending listings appear after backend approval.") }
                items(state.products, key = { it.productId }) { row -> Card(onClick = { model.open(row.productId.toLong()) }) { MarketplaceProduct(row) } }
                if (!state.last) item { OutlinedButton(onClick = { model.refresh(more = true) }, enabled = !state.busy) { Text("Load more") } }
            }
        }
    }
}

@Composable private fun MarketplaceProduct(row: CanonicalFarmerProduct) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val image = row.images.firstOrNull { it.isPrimary == true } ?: row.images.firstOrNull()
        if (image != null) ProductImage(image.url, row.name)
        Text(row.name, style = MaterialTheme.typography.titleLarge)
        Text("KES ${row.unitPrice.toPlainString()} / ${row.unit}")
        Text("${row.availableQuantity.toPlainString()} ${row.unit} available")
        Text("Seller #${row.sellerId}", style = MaterialTheme.typography.bodySmall)
    }
}
