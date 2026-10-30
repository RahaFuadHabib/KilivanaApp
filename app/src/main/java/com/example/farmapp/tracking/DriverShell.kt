package com.example.farmapp.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.ui.screens.auth.validEmailOtp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.time.Instant
import kotlin.coroutines.resume

data class DriverLocationRequest(val latitude: Double, val longitude: Double, val clientTimestamp: String, val accuracyMetres: Double?) {
    override fun toString() = "DriverLocationRequest(redacted)"
}
data class DriverLocationAck(val tripId: Long? = null, val serverTimestamp: String? = null)
interface DriverApi {
    @GET("logistics/jobs/driver/{id}") suspend fun jobs(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<DeliveryJobDto>>>
    @GET("logistics/jobs/{id}") suspend fun job(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>>
    @GET("logistics/jobs/{id}/tracking") suspend fun events(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<DeliveryEventDto>>>
    @POST("logistics/jobs/{id}/accept") suspend fun accept(@Path("id") id: Long, @Query("driverId") driverId: Long,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>>
    @PUT("logistics/jobs/{id}/status") suspend fun status(@Path("id") id: Long, @Query("status") status: String,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>>
    @POST("logistics/jobs/{id}/otp/verify") suspend fun otp(@Path("id") id: Long, @Query("otp") code: String,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryJobDto>>
    @POST("tracking/{id}/location") suspend fun report(@Path("id") id: Long, @Body location: DriverLocationRequest,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<DriverLocationAck>>
}
internal fun nextDriverStatus(status: String?) = when(status) {
    "accepted" -> "en_route_to_pickup"
    "en_route_to_pickup" -> "arrived_at_pickup"
    "arrived_at_pickup" -> "picked_up"
    "picked_up" -> "in_transit"
    "in_transit" -> "arrived_at_destination"
    else -> null // Delivery completion goes through the verified handover OTP.
}
internal val reportingStates = setOf("accepted", "en_route_to_pickup", "arrived_at_pickup", "picked_up", "in_transit", "arrived_at_destination")
internal fun validateDriverJob(job: DeliveryJobDto, driverId: Long): DeliveryJobDto {
    check(job.orderId != null && job.orderId > 0 && job.driverId == driverId) { "This job is not assigned to your Driver account." }
    validateDeliveryJob(job, job.orderId)
    return job
}

internal class DriverRepository(private val api: DriverApi, private val sessions: SessionStore) {
    fun current(expected: SessionSnapshot): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "DRIVER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId) { "Sign in with your assigned Driver account." }
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            error(when(response.code()) { 401,403 -> "Your account cannot manage this delivery."; 429 -> "Too many attempts. Wait before trying again."; 400,409,422 -> "The backend rejected this action or delivery code. Refresh the job before continuing."; else -> "Driver request was not confirmed. Reload before trying again; no automatic action retry was made." })
        }
        return response.body()?.requireData() ?: error("Incomplete Driver response.")
    }
    suspend fun jobs(expected: SessionSnapshot): List<DeliveryJobDto> {
        val id = current(expected).auth!!.userId.toLong()
        val rows = read(api.jobs(id, current(expected))).map { validateDriverJob(it, id) }
        current(expected); check(rows.map { it.id }.distinct().size == rows.size)
        return rows
    }
    suspend fun job(expected: SessionSnapshot, id: Long): DeliveryJobDto {
        val row = validateDriverJob(read(api.job(id, current(expected))), current(expected).auth!!.userId.toLong())
        current(expected); check(row.id == id)
        return row
    }
    suspend fun advance(expected: SessionSnapshot, before: DeliveryJobDto, code: String?): DeliveryJobDto {
        val row = job(expected, before.id!!)
        check(row.status == before.status && row.orderId == before.orderId && row.driverId == before.driverId) { "The delivery changed. Refresh before continuing." }
        val response = when {
            code != null -> {
                check(row.status == "arrived_at_destination" && validEmailOtp(code)) { "Enter the recipient's six-digit handover code at the destination." }
                api.otp(row.id!!, code, current(expected))
            }
            row.status == "assigned" -> api.accept(row.id!!, current(expected).auth!!.userId.toLong(), current(expected))
            else -> api.status(row.id!!, nextDriverStatus(row.status) ?: error("No permitted next step."), current(expected))
        }
        val saved = validateDriverJob(read(response), current(expected).auth!!.userId.toLong())
        check(saved.id == row.id && saved.orderId == row.orderId)
        val target = if (code != null) "delivered" else if (row.status == "assigned") "accepted" else nextDriverStatus(row.status)
        check(saved.status == target)
        current(expected); return saved
    }
    suspend fun events(expected: SessionSnapshot, id: Long): List<DeliveryEventDto> {
        job(expected, id)
        val rows = read(api.events(id, current(expected)))
        check(rows.all { it.logisticsJobId == id && com.example.farmapp.supplier.SupplierDeliveryStatus.parse(it.status) != null })
        current(expected); return rows.sortedBy { it.recordedAt }
    }
    suspend fun report(expected: SessionSnapshot, id: Long, location: Location): String {
        val job = job(expected, id) // Recheck assignment before every foreground report.
        check(job.status in reportingStates) { "Location reporting has stopped for this job." }
        check(SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0..30_000_000_000L) { "Waiting for a fresh location fix." }
        check(!location.hasAccuracy() || location.accuracy in 0f..500f) { "Location accuracy is insufficient. Move to an open area or allow precise location." }
        val ack = read(api.report(id, DriverLocationRequest(location.latitude, location.longitude, Instant.ofEpochMilli(location.time).toString(),
            location.accuracy.toDouble().takeIf { location.hasAccuracy() }), current(expected)))
        check(ack.tripId == id && !ack.serverTimestamp.isNullOrBlank())
        current(expected); return ack.serverTimestamp
    }
}

internal data class DriverState(val jobs: List<DeliveryJobDto> = emptyList(), val busy: Boolean = false, val error: String? = null,
    val selectedId: Long? = null, val notice: String? = null, val needsReload: Boolean = false, val events: List<DeliveryEventDto> = emptyList())
internal class DriverModel : ViewModel() {
    private val expected = TokenManager.session.value
    private val repository = DriverRepository(ApiClient.driverApi, TokenManager)
    private val mutable = MutableStateFlow(DriverState())
    val state = mutable.asStateFlow()
    init { refresh() }
    fun refresh() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val rows = repository.jobs(expected); mutable.update { it.copy(jobs = rows, error = null, needsReload = false) }
                mutable.value.selectedId?.let { id -> val history = repository.events(expected, id); mutable.update { it.copy(events = history) } }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = if (e is IllegalStateException) e.message else "Could not load assigned jobs.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun select(id: Long?) { if (!mutable.value.busy) { mutable.update { it.copy(selectedId = id, notice = null, events = emptyList()) }; if (id != null) refresh() } }
    fun advance(code: String? = null) {
        val row = mutable.value.jobs.firstOrNull { it.id == mutable.value.selectedId } ?: return
        if (mutable.value.busy || mutable.value.needsReload) return
        mutable.update { it.copy(busy = true, notice = null, needsReload = true) }
        viewModelScope.launch {
            try { val saved = repository.advance(expected, row, code); mutable.update { it.copy(jobs = it.jobs.map { old -> if (old.id == saved.id) saved else old }, notice = "Backend confirmed ${saved.status?.replace('_', ' ')}.", needsReload = false) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(error = "Action not confirmed. Refresh this job before another action. No automatic retry was made.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DriverShell(session: SessionSnapshot, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val model: DriverModel = viewModel(key = "driver-${session.generation}")
    val state by model.state.collectAsStateWithLifecycle()
    val selected = state.jobs.firstOrNull { it.id == state.selectedId }
    var confirm by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(false) }
    BackHandler(state.selectedId != null) { code = ""; model.select(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Kilivana · Driver") }, actions = {
        TextButton(onClick = { profile = true }) { Text("Profile") }
        TextButton(onClick = onLogout, enabled = !state.busy) { Text("Sign out") }
    }) }) { padding ->
        PullToRefreshBox(isRefreshing = state.busy, onRefresh = model::refresh, modifier = modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Your assigned deliveries", style = MaterialTheme.typography.headlineSmall) }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = model::refresh, enabled = !state.busy) { Text("Reload jobs") } } }
                state.notice?.let { item { Text(it) } }
                if (selected == null) {
                    if (!state.busy && state.jobs.isEmpty() && state.error == null) item { Text("No deliveries assigned yet.") }
                    items(state.jobs, key = { it.id!! }) { job -> OutlinedCard(onClick = { model.select(job.id) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("Job #${job.id} · Order #${job.orderId}"); Text(job.status?.replace('_', ' ').orEmpty()) }
                    } }
                } else {
                    item { TextButton(onClick = { code = ""; model.select(null) }, enabled = !state.busy) { Text("Back to deliveries") }
                        Text("Job #${selected.id} · Order #${selected.orderId}", style = MaterialTheme.typography.titleLarge)
                        Text("Current state: ${selected.status?.replace('_', ' ')}")
                        if (selected.status == "assigned" || nextDriverStatus(selected.status) != null) Button(onClick = { confirm = true }, enabled = !state.busy && !state.needsReload) {
                            Text(if (selected.status == "assigned") "Accept delivery" else "Mark ${nextDriverStatus(selected.status)?.replace('_', ' ')}")
                        }
                        if (selected.status == "arrived_at_destination") {
                            OutlinedTextField(code, { code = it.filter { char -> char in '0'..'9' }.take(6) }, label = { Text("Recipient's 6-digit delivery code") }, enabled = !state.busy)
                            Button(onClick = { confirm = true }, enabled = !state.busy && !state.needsReload && validEmailOtp(code)) { Text("Verify handover & complete delivery") }
                        }
                    }
                    item { key(selected.id) { DriverLocationSharing(selected, session) } }
                    item { Text("Recorded delivery events", style = MaterialTheme.typography.titleMedium) }
                    if (state.events.isEmpty()) item { Text("No delivery events loaded. Only the current server status is known.") }
                    items(state.events) { event -> Text("${event.status?.replace('_', ' ')} · ${event.recordedAt.orEmpty()}\n${event.note.orEmpty()}") }
                }
            }
        }
    }
    if (profile) com.example.farmapp.profile.AccountProfilePage { profile = false }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Confirm delivery action") },
        text = { Text("Confirm this step only when it reflects the actual delivery. Handover completion verifies the recipient's code on the backend.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.advance(code.takeIf { selected?.status == "arrived_at_destination" }); code = "" }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable private fun DriverLocationSharing(job: DeliveryJobDto, expected: SessionSnapshot) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var sharing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    fun permission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        sharing = permission(); if (!sharing) feedback = "Location permission denied. Sharing is off."
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) sharing = false }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(job.status) { if (job.status !in reportingStates) sharing = false }
    LaunchedEffect(sharing, job.id, lifecycle) {
        if (sharing) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val repository = DriverRepository(ApiClient.driverApi, TokenManager)
            while (sharing) {
                try {
                    check(permission()) { "Location permission is required. Sharing stopped." }
                    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                    val fix = withTimeoutOrNull(20_000) { suspendCancellableCoroutine<Location?> { continuation ->
                        val cancellation = CancellationSignal()
                        continuation.invokeOnCancellation { cancellation.cancel() }
                        LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    } } ?: error("No location fix yet. Move to an open area and start sharing again.")
                    feedback = "Location accepted at ${repository.report(expected, job.id!!, fix)}"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { feedback = if (e is IllegalStateException) e.message else "Location could not be sent. Sharing stopped; restart when ready."; sharing = false }
                if (sharing) delay(15_000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Driver location sharing", style = MaterialTheme.typography.titleMedium)
        Text("Share your location with this delivery's buyer and sellers every 15 seconds while this job screen is open. Sharing stops when you leave or put the app in the background.")
        Button(onClick = {
            if (sharing) sharing = false else if (permission()) sharing = true else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }, enabled = sharing || job.status in reportingStates) { Text(if (sharing) "Stop sharing location" else "Start sharing location") }
        Text(if (sharing) "Location sharing is ON" else "Location sharing is OFF")
        feedback?.let { Text(it) }
    }
}
