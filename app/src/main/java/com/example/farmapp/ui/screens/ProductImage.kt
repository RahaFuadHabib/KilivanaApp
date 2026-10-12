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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

/** Uses backend URLs and the existing authenticated Retrofit client; bounded temporary download/decode. */
@Composable
internal fun ProductImage(url: String, name: String) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = try {
            withContext(Dispatchers.IO) {
                val base = BuildConfig.API_BASE_URL.toHttpUrl()
                val target = base.resolve(url) ?: error("Invalid image URL")
                require(target.scheme == base.scheme && target.host == base.host && target.port == base.port && target.encodedPath.startsWith(base.encodedPath))
                val response = ApiClient.farmApiService.productImageContent(target.toString())
                if (!response.isSuccessful) { response.errorBody()?.close(); return@withContext null }
                val body = response.body() ?: return@withContext null
                val file = File.createTempFile("product_preview_", ".img", context.cacheDir)
                try {
                    body.use { data -> data.byteStream().use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 5L * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                    } } }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    require(options.outWidth > 0 && options.outHeight > 0)
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 720) options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
                } finally { body.close(); file.delete() }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    bitmap?.let { Image(it, "$name photo", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop) }
        ?: Text("Photo preview unavailable")
}
