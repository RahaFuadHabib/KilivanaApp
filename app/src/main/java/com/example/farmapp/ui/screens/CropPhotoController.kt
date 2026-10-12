package com.example.farmapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CropPhotoController(
    val draft: CropDraft,
    val busy: Boolean,
    val error: String?,
    val update: (CropDraft) -> Unit,
    val takePhoto: () -> Unit,
    val choosePhotos: () -> Unit,
    val removePhoto: (String) -> Unit,
    val storage: CropPhotoStorage
)

private val DraftSaver = listSaver<CropDraft, String>(
    save = { it.savedValues() },
    restore = { CropDraft.fromSavedValues(it) }
)

/** Hoisted within FarmerShell, so launchers and pending IO survive switching tabs.
 * Saved state holds strings/filenames, never bitmaps or a second session owner.
 */
@Composable
internal fun rememberCropPhotoController(userId: String = "default"): CropPhotoController {
    val context = LocalContext.current.applicationContext
    val storage = remember(context) { CropPhotoStorage(context) }
    val scope = rememberCoroutineScope()
    val journal = remember(context, userId) { context.getSharedPreferences("crop_publish_" + userId, android.content.Context.MODE_PRIVATE) }
    fun restoredDraft(): CropDraft = runCatching {
        val json = journal.getString("pending", null) ?: return@runCatching CropDraft()
        CropDraft.fromSavedValues(com.google.gson.Gson().fromJson(json, Array<String>::class.java).toList())
    }.getOrDefault(CropDraft())
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(restoredDraft()) }
    LaunchedEffect(userId) { if (journal.contains("pending")) draft = restoredDraft() }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var externalRequest by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = pendingCamera
        pendingCamera = null
        scope.launch {
            busy = true
            try {
                if (name != null) {
                    val valid = success && withContext(Dispatchers.IO) { storage.isImage(name) }
                    if (valid) draft = draft.addPhotos(listOf(name))
                    else {
                        withContext(Dispatchers.IO) { storage.delete(name) }
                        if (success) error = "The camera did not save a readable photo. Please try again."
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                name?.let { storage.delete(it) }
                error = "Unable to read the captured photo. Please try again."
            } finally { busy = false }
        }
    }

    fun launchCamera() {
        if (busy || pendingCamera != null || draft.photos.size >= MAX_CROP_PHOTOS) return
        error = null
        scope.launch {
            busy = true
            var name: String? = null
            try {
                name = withContext(Dispatchers.IO) { storage.create() }
                pendingCamera = name
                camera.launch(storage.uri(name))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                name?.let { storage.delete(it) }
                pendingCamera = null
                error = "Camera unavailable. You can choose photos from Gallery instead."
            } finally { busy = false }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        externalRequest = false
        if (granted) launchCamera()
        else error = "Camera permission was not granted. Choose from Gallery or enable Camera in app settings."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_CROP_PHOTOS)) { uris ->
        externalRequest = false
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            error = null
            var failed = false
            try {
                // The documents fallback may return more than requested; enforce our local limit.
                for (uri in uris.take(MAX_CROP_PHOTOS - draft.photos.size)) {
                    try {
                        val name = withContext(Dispatchers.IO) { storage.importImage(uri) }
                        draft = draft.addPhotos(listOf(name))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                }
                if (failed) error = "Some photos could not be opened. Choose readable images up to 30 MB each."
            } finally { busy = false }
        }
    }

    return CropPhotoController(draft, busy || pendingCamera != null || externalRequest, error,
        update = {
            // A synchronous tiny journal protects the ID/upload cursor across app restart.
            val editor = journal.edit()
            if (it.productId != null || it.creationUncertain) editor.putString("pending", com.google.gson.Gson().toJson(it.savedValues()))
            else editor.remove("pending")
            check(editor.commit()) { "Unable to save publishing progress" }
            draft = it; error = null
        },
        takePhoto = {
            if (!busy && pendingCamera == null && !externalRequest) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else try { externalRequest = true; permission.launch(Manifest.permission.CAMERA) }
                catch (_: Exception) { externalRequest = false; error = "Unable to request camera access. Choose from Gallery instead." }
            }
        },
        choosePhotos = {
            if (!busy && pendingCamera == null && !externalRequest && draft.photos.size < MAX_CROP_PHOTOS) {
                error = null
                try { externalRequest = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: Exception) { externalRequest = false; error = "Photo picker unavailable on this device. Please try again." }
            }
        },
        removePhoto = { name ->
            draft = draft.removePhoto(name)
            scope.launch(Dispatchers.IO) { storage.delete(name) }
        }, storage = storage)
}
