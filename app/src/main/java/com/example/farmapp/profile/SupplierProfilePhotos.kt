package com.example.farmapp.profile

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.model.canonical.CanonicalProductImageDto
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.supplier.*
import com.example.farmapp.ui.screens.ProductImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.*

/** These are profile images; a product ID is never sent to these routes. */
interface SupplierProfilePhotosApi {
    @GET("profiles/suppliers/{id}/images") suspend fun images(@Path("id") userId: Long,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductImageDto>>>
    @Multipart @POST("profiles/suppliers/{id}/images") suspend fun upload(@Path("id") userId: Long,
        @Part image: MultipartBody.Part, @Query("isPrimary") primary: Boolean,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductImageDto>>>
}
internal data class ProfilePhotosState(val images: List<CanonicalProductImageDto> = emptyList(), val busy: Boolean = false,
    val error: String? = null, val pending: Boolean = false, val uploaded: Int = 0, val loaded: Boolean = false)
internal class SupplierProfilePhotosModel(context: Context) : ViewModel() {
    private val expected = TokenManager.session.value
    private val operations = SupplierPreferences(context.applicationContext)
    private val key = BuildConfig.API_BASE_URL + "|supplier-profile|" + expected.auth?.userId
    private val mutable = MutableStateFlow(ProfilePhotosState())
    val state = mutable.asStateFlow()
    init { load() }
    private fun current(): SessionSnapshot {
        val now = TokenManager.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "SUPPLIER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId) { "Sign in as the Supplier profile owner." }
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) { response.errorBody()?.close(); error("Profile photo request failed. Reload and inspect existing photos before another upload.") }
        return response.body()?.requireData() ?: error("Incomplete profile photo response.")
    }
    private suspend fun images(): List<CanonicalProductImageDto> {
        val id = current().auth!!.userId.toLong()
        val profile = read(ApiClient.profileEditorApi.business(id, current()))
        check(profile.userId == id && profile.id != null && profile.id > 0)
        val rows = read(ApiClient.supplierProfilePhotosApi.images(id, current()))
        rows.forEach { it.toImageView() }; check(rows.map { it.id }.distinct().size == rows.size)
        current(); return rows
    }
    private fun launch(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(error = "Profile photo request was not confirmed. Reload and inspect your profile photos before uploading again.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun load() = launch {
        val pending = withContext(Dispatchers.IO) { operations.read(key) != null }
        mutable.update { it.copy(pending = pending) }
        val rows = images(); mutable.update { it.copy(images = rows, loaded = true) }
    }
    fun upload(photo: SupplierPhoto) {
        if (mutable.value.pending || !mutable.value.loaded) return
        launch {
            validateSupplierPhoto(photo)
            val existing = images()
            withContext(Dispatchers.IO) { operations.write(key, PendingSupplyOperation(SupplyOperation.UPLOAD)) }
            mutable.update { it.copy(pending = true) }
            val id = current().auth!!.userId.toLong()
            val returned = read(ApiClient.supplierProfilePhotosApi.upload(id, MultipartBody.Part.createFormData("image", photo.filename,
                photo.bytes.toRequestBody(photo.mime.toMediaType())), existing.isEmpty(), current()))
            returned.forEach { it.toImageView() }
            val latest = images()
            check(returned.isNotEmpty() && latest.any { image -> image.id in returned.map { it.id } && existing.none { it.id == image.id } })
            withContext(Dispatchers.IO) { operations.write(key, null) }
            mutable.update { it.copy(images = latest, pending = false, uploaded = it.uploaded + 1) }
        }
    }
    fun acknowledge() = launch {
        val rows = images()
        withContext(Dispatchers.IO) { operations.write(key, null) }
        mutable.update { it.copy(images = rows, pending = false, loaded = true) }
    }
    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return SupplierProfilePhotosModel(context) as T
            }
        }
    }
}

@Composable internal fun SupplierProfilePhotos() {
    val context = LocalContext.current.applicationContext
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: SupplierProfilePhotosModel = viewModel(key = "supplier-profile-photos-${session.generation}", factory = SupplierProfilePhotosModel.factory(context))
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<SupplierPhoto?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparing = true
            try { photo = withContext(Dispatchers.IO) { readSupplierPhoto(context, uri) }; error = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Choose a JPEG, PNG, GIF or WebP photo no larger than 5 MiB." }
            finally { preparing = false }
        }
    }
    LaunchedEffect(state.uploaded) { if (state.uploaded > 0) photo = null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Supplier profile photos", style = MaterialTheme.typography.titleMedium)
        Text("These photographs represent your business profile, separately from product listings.", style = MaterialTheme.typography.bodySmall)
        state.images.forEach { image -> ProductImage(image.url!!, "Supplier business profile") }
        if (state.busy || preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        (state.error ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = model::load, enabled = !state.busy) { Text("Reload profile photos") }
        if (state.pending) {
            Text("A previous profile upload may have reached the server. Inspect existing photos before continuing.")
            TextButton(onClick = { review = true }, enabled = !state.busy) { Text("Review profile photo submission") }
        } else {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy && !preparing && state.loaded) { Text("Choose business profile photo") }
            photo?.let { selected ->
                val bitmap = remember(selected) { BitmapFactory.decodeByteArray(selected.bytes, 0, selected.bytes.size)?.asImageBitmap() }
                bitmap?.let { Image(it, "Selected business profile photo", Modifier.fillMaxWidth().height(140.dp)) }
                TextButton(onClick = { confirm = true }, enabled = !state.busy) { Text("Upload business profile photo") }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Upload business profile photo?") },
        text = { Text("This photo will appear on your Supplier profile. Do not use identity or verification documents.") },
        confirmButton = { TextButton(onClick = { confirm = false; photo?.let(model::upload) }) { Text("Upload") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Review existing profile photographs") },
        text = { Text("Inspect the current gallery first. A delayed response may still complete. Acknowledge only after checking that another upload will not create a duplicate.") },
        confirmButton = { TextButton(onClick = { review = false; model.acknowledge() }) { Text("Reload and acknowledge") } }, dismissButton = { TextButton(onClick = { review = false }) { Text("Keep blocked") } })
}
