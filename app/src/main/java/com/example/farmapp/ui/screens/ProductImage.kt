package com.example.farmapp.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.farmapp.network.TokenManager
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.imageSessionCurrent
import androidx.compose.ui.unit.dp
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal typealias ProductBitmapLoader = suspend (String, SessionSnapshot) -> ImageBitmap?

/** Session-scoped memory only: private previews never enter a disk or shared bitmap cache. */
@Composable
internal fun ProductImage(url: String, name: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
    showRetry: Boolean = true,
    memory: ScopedImageMemory<ImageBitmap>? = null,
    loader: ProductBitmapLoader = ::loadProductBitmap,
    fallback: @Composable (() -> Unit)? = null) {
    val session by TokenManager.session.collectAsState()
    key(url, session.generation, session.loggingOut, session.isDemo) {
        SessionProductImage(url, name, session, modifier, showRetry, memory, loader, fallback)
    }
}

@Composable
private fun SessionProductImage(url: String, name: String, session: SessionSnapshot,
    modifier: Modifier, showRetry: Boolean, memory: ScopedImageMemory<ImageBitmap>?, loader: ProductBitmapLoader, fallback: @Composable (() -> Unit)?) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var loading by remember(url) { mutableStateOf(true) }
    val bitmap by produceState<ImageBitmap?>(null, url, attempt, memory) {
        loading = true
        value = try {
            withContext(Dispatchers.IO) {
                if (!imageSessionCurrent(session, TokenManager.session.value)) return@withContext null
                (if (memory == null) loader(url, session) else memory.get(url) { loader(url, session) })
                    .takeIf { imageSessionCurrent(session, TokenManager.session.value) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        loading = false
    }
    bitmap?.let { Image(it, "$name photo", modifier, contentScale = ContentScale.Crop) }
        ?: Column(modifier) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (fallback != null) fallback() else Text("Photo preview unavailable")
                }
                if (showRetry) TextButton(onClick = { attempt++ }) { Text("Retry photo") }
            }
        }
}

/** Each Supplier gallery owns its memory and pending work; other role callers remain uncached. */
@Composable
internal fun rememberGalleryImageMemory(contentKey: Any? = null): ScopedImageMemory<ImageBitmap> {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val memory = remember(session.generation, session.loggingOut, session.isDemo, session.auth?.userId, session.auth?.sessionOrigin, contentKey) {
        ScopedImageMemory<ImageBitmap>(current = {
            val now = TokenManager.session.value
            imageSessionCurrent(session, now) && session.auth?.userId == now.auth?.userId &&
                session.auth?.sessionOrigin == now.auth?.sessionOrigin && session.auth?.role == now.auth?.role
        }, weightOf = { it.asAndroidBitmap().allocationByteCount })
    }
    DisposableEffect(memory) { onDispose { memory.close() } }
    return memory
}

/** Unchanged bounded transport/decode, injectable only for synthetic rendering tests. */
internal suspend fun loadProductBitmap(url: String, session: SessionSnapshot): ImageBitmap? {
                if (!imageSessionCurrent(session, TokenManager.session.value)) return null
                val base = BuildConfig.API_BASE_URL.toHttpUrl()
                val target = base.resolve(url) ?: error("Invalid image URL")
                require(com.example.farmapp.data.remote.allowedImageUrl(base, target))
                val response = ApiClient.imageContent(target.toString())
                if (!response.isSuccessful) { response.errorBody()?.close(); return null }
                val body = response.body() ?: return null
                return body.use { data ->
                    val bytes = data.byteStream().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 10L * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth > 0 && options.outHeight > 0)
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 720) options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                    decoded.takeIf { imageSessionCurrent(session, TokenManager.session.value) }
                }
}
