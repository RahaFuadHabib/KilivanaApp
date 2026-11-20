package com.example.farmapp.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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

// Design tokens
private val TrkGreen = Color(0xFF00834A)
private val TrkGreenLight = Color(0xFFE8F5EE)
private val TrkAmber = Color(0xFFFFF8E1)
private val TrkAmberText = Color(0xFFE65100)
private val TrkBlue = Color(0xFFE3F2FD)
private val TrkBlueText = Color(0xFF0D47A1)
private val TrkRed = Color(0xFFFFEBEE)
private val TrkRedText = Color(0xFFB71C1C)
private val TrkGrey = Color(0xFFF5F5F5)
private val TrkGreyText = Color(0xFF555555)
private val TrkInk = Color(0xFF101827)
private val TrkMuted = Color(0xFF526074)
private val TrkPage = Color(0xFFF0F3EE)
private val TrkSurface = Color(0xFFFFFFFF)
private val TrkBorder = Color(0xFFDCE3E0)
private val TrkHeaderGreen = Color(0xFF00644B)

@Composable internal fun DeliveryTrackingScreen(order: BuyerOrderDto, modifier: Modifier = Modifier, onBack: () -> Unit) {
    DeliveryTrackingContent(order, TokenManager, ApiClient.deliveryTrackingApi, modifier, onBack)
}

@Composable internal fun DeliveryTrackingContent(
    order: BuyerOrderDto, sessions: SessionStore, api: DeliveryTrackingApi,
    modifier: Modifier = Modifier, onBack: () -> Unit
) {
    val expected = remember { sessions.session.value }
    val repository = remember(expected, order.id, order.buyerId) {
        com.example.farmapp.buyers.BuyerDeliveryTrackingRepository(api, sessions, expected, order.id ?: 0, order.buyerId ?: 0)
    }
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
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId.toLongOrNull() == order.buyerId) { "Sign in as this order's buyer." }
        return now
    }
    fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) { response.errorBody()?.close(); error(if (response.code() in setOf(401, 403)) "This account cannot view this delivery." else "Delivery data could not be refreshed.") }
        return response.body()?.requireData() ?: error("Incomplete delivery response.")
    }

    LaunchedEffect(order.id, selectedId, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var waitMillis = 15_000L
            while (true) {
                busy = true
                try {
                    val rows = read(repository.jobs(order.id!!.also { current() }))
                    rows.forEach { validateDeliveryJob(it, order.id) }
                    check(rows.map { it.id }.distinct().size == rows.size)
                    current(); jobs = rows
                    val job = rows.firstOrNull { it.id == selectedId } ?: rows.singleOrNull()
                    if (job != null) {
                        location = location?.takeIf { it.tripId == job.id && it.driverId == job.driverId }
                        val history = read(repository.events(job.also { current() }))
                        check(history.all { it.logisticsJobId == job.id && SupplierDeliveryStatus.parse(it.status) != null })
                        current(); events = history.sortedBy { it.recordedAt }
                        try {
                            val response = repository.location(job.also { current() })
                            if (response.code() == 404) {
                                response.errorBody()?.close(); location = null; locationError = "No driver location has been recorded yet."
                            } else {
                                val fix = validateDeliveryLocation(read(response), job)
                                current(); location = fix; locationError = null
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { locationError = "Latest driver location is unavailable." }
                        error = null
                        if (job.status in setOf("delivered", "cancelled", "failed")) { busy = false; break }
                    } else {
                        events = emptyList(); location = null
                        error = if (rows.isEmpty()) "No delivery job has been assigned to this order yet." else "Choose a delivery job below."
                    }
                    waitMillis = 15_000
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = if (e is IllegalStateException) e.message else "Delivery service unavailable. Retrying while this screen is visible."; waitMillis = (waitMillis * 2).coerceAtMost(60_000) }
                finally { busy = false }
                delay(waitMillis)
            }
        }
    }

    val selectedJob = jobs.firstOrNull { it.id == selectedId } ?: jobs.singleOrNull()

    Column(modifier.fillMaxSize().background(TrkPage)) {
        // Header
        Box(Modifier.fillMaxWidth().background(TrkHeaderGreen)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Track Delivery", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Order #${order.id}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), color = Color.White, strokeWidth = 2.dp)
                IconButton(onClick = { retry++ }, enabled = !busy) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                }
            }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = TrkGreen)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Error banner
            error?.let { msg ->
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = TrkRed, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = TrkRedText, modifier = Modifier.size(20.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = TrkRedText)
                        }
                    }
                }
            }

            // Job selector (only when multiple jobs)
            if (jobs.size > 1) item {
                Surface(shape = RoundedCornerShape(14.dp), color = TrkSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Delivery jobs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TrkInk)
                        jobs.forEach { job ->
                            val isSelected = job.id == selectedId
                            OutlinedButton(
                                onClick = { selectedId = job.id; events = emptyList(); location = null },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = TrkGreenLight, contentColor = TrkGreen)
                                         else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Icon(Icons.Default.LocalShipping, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("${job.status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }} · ${if (job.driverId != null) "Driver assigned" else "Awaiting driver"}")
                            }
                        }
                    }
                }
            }

            // Status card
            selectedJob?.let { job ->
                item {
                    val (statusBg, statusFg, statusIcon) = deliveryStatusStyle(job.status)
                    Surface(shape = RoundedCornerShape(16.dp), color = statusBg, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(statusFg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(statusIcon, null, tint = statusFg, modifier = Modifier.size(28.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    job.status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = statusFg
                                )
                                Text(
                                    if (job.driverId != null) "Driver assigned · Job #${job.id}" else "Awaiting driver assignment",
                                    style = MaterialTheme.typography.bodySmall, color = TrkMuted
                                )
                                job.updatedAt?.let { Text("Updated: ${buyerDate(it)}", style = MaterialTheme.typography.labelSmall, color = TrkMuted) }
                            }
                        }
                    }
                }
            }

            // Driver location card
            location?.let { fix ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = TrkSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TrkGreenLight), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LocationOn, null, tint = TrkGreen, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Driver location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TrkInk)
                                    Text("Last report: ${buyerDate(fix.serverTimestamp)}", style = MaterialTheme.typography.labelSmall, color = TrkMuted)
                                }
                                fix.accuracyMetres?.let {
                                    Surface(shape = RoundedCornerShape(20.dp), color = TrkGreenLight) {
                                        Text("±${it.toInt()} m", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall, color = TrkGreen, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(8.dp), color = TrkGrey, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("Latitude", style = MaterialTheme.typography.labelSmall, color = TrkMuted)
                                        Text("%.5f".format(fix.latitude), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TrkInk)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = TrkGrey, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("Longitude", style = MaterialTheme.typography.labelSmall, color = TrkMuted)
                                        Text("%.5f".format(fix.longitude), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TrkInk)
                                    }
                                }
                            }
                            DriverLocationMap(fix)
                        }
                    }
                }
            }

            // Location error
            locationError?.let { msg ->
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = TrkAmber, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOff, null, tint = TrkAmberText, modifier = Modifier.size(18.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = TrkAmberText)
                        }
                    }
                }
            }

            // Timeline
            if (events.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = TrkSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TrkGreenLight), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Timeline, null, tint = TrkGreen, modifier = Modifier.size(20.dp))
                                }
                                Text("Delivery timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TrkInk)
                            }
                            Spacer(Modifier.height(14.dp))
                            events.forEachIndexed { index, event ->
                                val isLast = index == events.lastIndex
                                val (evBg, evFg, evIcon) = deliveryStatusStyle(event.status)
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(32.dp).clip(CircleShape).background(evBg), contentAlignment = Alignment.Center) {
                                            Icon(evIcon, null, tint = evFg, modifier = Modifier.size(16.dp))
                                        }
                                        if (!isLast) Box(Modifier.width(2.dp).height(32.dp).background(TrkBorder))
                                    }
                                    Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            event.status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Update",
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TrkInk
                                        )
                                        Text(buyerDate(event.recordedAt), style = MaterialTheme.typography.labelSmall, color = TrkMuted)
                                        event.note?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = TrkMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!busy && error == null) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = TrkSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timeline, null, tint = TrkMuted, modifier = Modifier.size(36.dp))
                            Text("No delivery updates yet", style = MaterialTheme.typography.bodyMedium, color = TrkMuted)
                        }
                    }
                }
            }
        }
    }
}

private fun deliveryStatusStyle(status: String?): Triple<Color, Color, ImageVector> = when (status?.lowercase()) {
    "delivered", "completed" -> Triple(Color(0xFFE8F5E9), Color(0xFF1B5E20), Icons.Default.CheckCircle)
    "cancelled", "failed" -> Triple(Color(0xFFFFEBEE), Color(0xFFB71C1C), Icons.Default.Cancel)
    "in_transit", "out_for_delivery", "picked_up" -> Triple(Color(0xFFE3F2FD), Color(0xFF0D47A1), Icons.Default.LocalShipping)
    "assigned" -> Triple(Color(0xFFF3E5F5), Color(0xFF6A1B9A), Icons.Default.Person)
    else -> Triple(Color(0xFFFFF8E1), Color(0xFFE65100), Icons.Default.HourglassEmpty)
}

@Composable private fun DriverLocationMap(fix: DeliveryLocationDto) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val token = remember {
        runCatching {
            context.getString(context.resources.getIdentifier("mapbox_access_token", "string", context.packageName))
        }.getOrNull()
    }
    val hasToken = !token.isNullOrBlank() && token != "your_mapbox_access_token_here"
    if (!hasToken) {
        Surface(shape = RoundedCornerShape(12.dp), color = TrkGrey, modifier = Modifier.fillMaxWidth().height(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("Map view requires a Mapbox access token.", style = MaterialTheme.typography.bodySmall, color = TrkMuted)
            }
        }
        return
    }
    val point = remember(fix.latitude, fix.longitude) { Point.fromLngLat(fix.longitude!!, fix.latitude!!) }
    val viewport = rememberMapViewportState { setCameraOptions { center(point); zoom(14.0) } }
    LaunchedEffect(point) { viewport.setCameraOptions { center(point); zoom(14.0) } }
    MapboxMap(
        Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)),
        mapViewportState = viewport
    ) {
        DisposableMapEffect(point) { mapView ->
            val manager = mapView.annotations.createCircleAnnotationManager()
            manager.create(CircleAnnotationOptions().withPoint(point).withCircleRadius(8.0).withCircleColor("#00834A")
                .withCircleStrokeWidth(3.0).withCircleStrokeColor("#FFFFFF"))
            onDispose { mapView.annotations.removeAnnotationManager(manager) }
        }
    }
}
