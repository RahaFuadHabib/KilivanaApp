package com.example.farmapp.buyer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import java.io.ByteArrayOutputStream

/** This service has exactly one route. No request-body or auth-header parameter exists. */
internal interface DebugBuyerCartReadApi {
    @Streaming @GET("carts/buyer/{buyerId}") suspend fun read(@Path("buyerId") buyerId: Long,
        @Tag session: SessionSnapshot, @Tag policy: PreserveSessionOnReadFailure): Response<ResponseBody>
}
internal object DebugCartReportCache {
    @Volatile private var report: String? = null
    @Volatile private var expires = 0L
    fun publish(sanitized: String) { report = sanitized; expires = System.nanoTime() + 600_000_000_000L }
    fun clear() { report = null; expires = 0L }
    fun read() = if (System.nanoTime() < expires) report ?: "No user-triggered sanitized report is available."
        else "No user-triggered sanitized report is available."
}
/** In-app only. Does not restore/read storage or access any token/header/cookie property. */
private suspend fun inspectCurrentBuyerCart(local: BuyerShoppingState): String {
    val session = TokenManager.session.value
    val auth = session.auth
    if (session.loggingOut || session.isDemo || auth?.role != "BUYER" || auth.backendContract != "canonical" ||
        auth.sessionOrigin != BuildConfig.API_BASE_URL) return "Buyer session usable: no. No request was made."
    val buyer = auth.userId.toLongOrNull()?.takeIf { it > 0 && it.toString() == auth.userId }
        ?: return "Buyer session usable: no. No request was made."
    fun unchanged(): Boolean {
        val now = TokenManager.session.value
        return now.generation == session.generation && !now.loggingOut && now.auth?.userId == auth.userId &&
            now.auth?.role == "BUYER" && now.auth?.sessionOrigin == auth.sessionOrigin
    }
    return try {
        val response = ApiClient.authenticatedService(DebugBuyerCartReadApi::class.java)
            .read(buyer, session, PreserveSessionOnReadFailure())
        val json = withContext(Dispatchers.IO) {
            (response.body() ?: response.errorBody())?.use { body ->
                val bytes = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 256 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                try { parseCartStructureJson(String(bytes, Charsets.UTF_8)) } finally { bytes.fill(0) }
            }
        }
        if (!unchanged()) "Session changed; response structure discarded."
        else "Buyer session usable: yes\n" + sanitizeBuyerCartStructure(response.code(), json,
            local.selection.cart.size, local.selection.cart.values.sumOf { it.toLong() })
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { "Buyer session available, but the single GET could not be inspected. No exception, URL or response value is exported. No refresh or write retry was attempted." }
}
@Composable internal fun BuyerCartDiagnosticEntry(state: BuyerShoppingState) {
    if (!BuildConfig.ENABLE_CART_STRUCTURE_DIAGNOSTIC) return
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val session by TokenManager.session.collectAsStateWithLifecycle()
    DisposableEffect(session.generation) {
        onDispose { job?.cancel(); DebugCartReportCache.clear() }
    }
    OutlinedButton(onClick = { open = true; report = null; DebugCartReportCache.clear() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Inspect server cart (debug, read-only)")
    }
    if (open) Dialog(onDismissRequest = { job?.cancel(); open = false; DebugCartReportCache.clear() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            DebugBuyerCartDiagnosticContent(busy, report, onRun = {
                if (!busy && report == null) {
                    busy = true
                    job = scope.launch {
                        try {
                            val sanitized = inspectCurrentBuyerCart(state)
                            report = sanitized
                            DebugCartReportCache.publish(sanitized)
                        } finally { busy = false }
                    }
                }
            }, onClose = { job?.cancel(); open = false; DebugCartReportCache.clear() })
        }
    }
}
@Composable internal fun DebugBuyerCartDiagnosticContent(busy: Boolean, report: String?, onRun: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Read-only Buyer cart inspection", style = MaterialTheme.typography.titleLarge)
        Text("One GET using the current Buyer session. No checkout, cart changes or payment. Only sanitized field names, types, presence and counts are displayed.", Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRun, enabled = !busy && report == null) { Text("Run read-only GET") }
            TextButton(onClick = onClose) { Text("Close") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(report ?: "Not run. Waiting for your explicit trigger.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
