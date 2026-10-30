package com.example.farmapp.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.buyer.buyerDate
import com.example.farmapp.orders.BuyerOrderDto
import com.example.farmapp.supplier.SupplierDeliveryStatus
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import kotlinx.coroutines.*
import retrofit2.Response
import retrofit2.http.*

data class DeliveryJobDto(val id: Long? = null, val orderId: Long? = null, val driverId: Long? = null,
    val status: String? = null, val updatedAt: String? = null)
data class DeliveryEventDto(val id: Long? = null, val logisticsJobId: Long? = null, val driverId: Long? = null,
    val status: String? = null, val recordedAt: String? = null, val note: String? = null)
data class DeliveryLocationDto(val tripId: Long? = null, val orderId: Long? = null, val driverId: Long? = null,
    val latitude: Double? = null, val longitude: Double? = null, val status: String? = null, val serverTimestamp: String? = null,
    val accuracyMetres: Double? = null)
interface DeliveryTrackingApi {
    @GET("logistics/jobs/order/{id}") suspend fun jobs(@Path("id") order: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<DeliveryJobDto>>>
    @GET("logistics/jobs/{id}/tracking") suspend fun events(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<DeliveryEventDto>>>
    @GET("tracking/{id}/location") suspend fun location(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryLocationDto>>
}
internal fun validateDeliveryJob(job: DeliveryJobDto, orderId: Long) {
    check(job.id != null && job.id > 0 && job.orderId == orderId && SupplierDeliveryStatus.parse(job.status) != null) { "Delivery job could not be linked to this order." }
    check(job.driverId == null || job.driverId > 0)
}
internal fun validateDeliveryLocation(location: DeliveryLocationDto, job: DeliveryJobDto): DeliveryLocationDto {
    check(location.tripId == job.id && location.orderId == job.orderId && location.driverId == job.driverId && job.driverId != null) { "Driver location does not match this delivery." }
    check(location.latitude != null && location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
        location.longitude != null && location.longitude.isFinite() && location.longitude in -180.0..180.0) { "Invalid driver coordinates." }
    return location
}

@Composable internal fun DeliveryTrackingScreen(order: BuyerOrderDto, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val expected = remember { TokenManager.session.value }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var jobs by remember { mutableStateOf(emptyList<DeliveryJobDto>()) }
    var selectedId by remember { mutableStateOf(order.logisticsJobId) }
    var events by remember { mutableStateOf(emptyList<DeliveryEventDto>()) }
    var location by remember { mutableStateOf<DeliveryLocationDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    fun current(): SessionSnapshot {
        val now = TokenManager.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId.toLongOrNull() == order.buyerId) { "Sign in as this order's buyer." }
        return now
    }
    fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) { response.errorBody()?.close(); error(if (response.code() in setOf(401, 403)) "This account cannot view this delivery." else "Delivery data could not be refreshed. Previously loaded data may be out of date.") }
        return response.body()?.requireData() ?: error("Incomplete delivery response.")
    }
    LaunchedEffect(order.id, selectedId, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var waitMillis = 15_000L
            while (true) {
                busy = true
                try {
                    val rows = read(ApiClient.deliveryTrackingApi.jobs(order.id!!, current()))
                    rows.forEach { validateDeliveryJob(it, order.id) }
                    check(rows.map { it.id }.distinct().size == rows.size)
                    current(); jobs = rows
                    val job = rows.firstOrNull { it.id == selectedId } ?: rows.singleOrNull()
                    if (job != null) {
                        location = location?.takeIf { it.tripId == job.id && it.driverId == job.driverId }
                        val history = read(ApiClient.deliveryTrackingApi.events(job.id!!, current()))
                        check(history.all { it.logisticsJobId == job.id && SupplierDeliveryStatus.parse(it.status) != null })
                        current(); events = history.sortedBy { it.recordedAt }
                        try {
                            val response = ApiClient.deliveryTrackingApi.location(job.id, current())
                            if (response.code() == 404) {
                                response.errorBody()?.close(); location = null; locationError = "No driver location has been recorded yet."
                            } else {
                                val fix = validateDeliveryLocation(read(response), job)
                                current(); location = fix; locationError = null
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { locationError = "Latest driver location is unavailable. Any shown position is the last successful report." }
                        error = null
                        if (job.status in setOf("delivered", "cancelled", "failed")) { busy = false; break }
                    } else { events = emptyList(); location = null; error = if (rows.isEmpty()) "No delivery job has been assigned to this order yet." else "Choose a delivery job below." }
                    waitMillis = 15_000
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = if (e is IllegalStateException) e.message else "Delivery service unavailable. Retrying while this screen is visible."; waitMillis = (waitMillis * 2).coerceAtMost(60_000) }
                finally { busy = false }
                delay(waitMillis)
            }
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to order") }; Text("Delivery tracking", style = MaterialTheme.typography.headlineSmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { retry++ }, enabled = !busy) { Text("Refresh delivery") }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(jobs, key = { it.id!! }) { job ->
            OutlinedButton(onClick = { selectedId = job.id; events = emptyList(); location = null }, enabled = !busy) {
                Text("${job.status?.replace('_', ' ')} - ${if (job.driverId != null) "Driver assigned" else "Awaiting driver"}")
            }
        }
        locationError?.let { item { Text(it) } }
        location?.let { fix -> item {
            Text("Last driver report: ${buyerDate(fix.serverTimestamp)}")
            Text("${fix.latitude}, ${fix.longitude}${fix.accuracyMetres?.let { "; accuracy ${it.toInt()} m" }.orEmpty()}")
            DriverLocationMap(fix)
        } }
        item { Text("Delivery timeline", style = MaterialTheme.typography.titleLarge) }
        if (events.isEmpty()) item { Text("No delivery updates yet.") }
        items(events) { event -> Text("${event.status?.replace('_', ' ')} · ${buyerDate(event.recordedAt)}\n${event.note.orEmpty()}") }
    }
}

@Composable private fun DriverLocationMap(fix: DeliveryLocationDto) {
    val point = remember(fix.latitude, fix.longitude) { Point.fromLngLat(fix.longitude!!, fix.latitude!!) }
    val viewport = rememberMapViewportState { setCameraOptions { center(point); zoom(14.0) } }
    LaunchedEffect(point) { viewport.setCameraOptions { center(point); zoom(14.0) } }
    MapboxMap(Modifier.fillMaxWidth().height(260.dp), mapViewportState = viewport) {
        DisposableMapEffect(point) { mapView ->
            val manager = mapView.annotations.createCircleAnnotationManager()
            manager.create(CircleAnnotationOptions().withPoint(point).withCircleRadius(8.0).withCircleColor("#2E9B42")
                .withCircleStrokeWidth(3.0).withCircleStrokeColor("#FFFFFF"))
            onDispose { mapView.annotations.removeAnnotationManager(manager) }
        }
    }
}
