package com.example.farmapp.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

/** Session-scoped memory only: private previews never enter a disk or shared bitmap cache. */
@Composable
internal fun ProductImage(url: String, name: String) {
    val session by TokenManager.session.collectAsState()
    key(url, session.generation, session.loggingOut, session.isDemo) {
        SessionProductImage(url, name, session)
    }
}

@Composable
private fun SessionProductImage(url: String, name: String, session: SessionSnapshot) {
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = try {
            withContext(Dispatchers.IO) {
                if (!imageSessionCurrent(session, TokenManager.session.value)) return@withContext null
                val base = BuildConfig.API_BASE_URL.toHttpUrl()
                val target = base.resolve(url) ?: error("Invalid image URL")
                require(com.example.farmapp.data.remote.allowedImageUrl(base, target))
                val response = ApiClient.imageContent(target.toString())
                if (!response.isSuccessful) { response.errorBody()?.close(); return@withContext null }
                val body = response.body() ?: return@withContext null
                body.use { data ->
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
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    bitmap?.let { Image(it, "$name photo", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop) }
        ?: Text("Photo preview unavailable")
}
