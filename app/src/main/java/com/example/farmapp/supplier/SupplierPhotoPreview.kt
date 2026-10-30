package com.example.farmapp.supplier

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview only; original upload bytes and picker quality are unchanged. */
internal suspend fun supplierPreview(photo: SupplierPhoto, pixels: Int,
    decode: (ByteArray, BitmapFactory.Options) -> android.graphics.Bitmap? = { bytes, options ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }): ImageBitmap? = withContext(Dispatchers.Default) {
    require(photo.bytes.size in 1..5 * 1024 * 1024)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(photo.bytes, options)
    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 50_000_000)
    val target = pixels.coerceIn(1, 1024)
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >= target) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    decode(photo.bytes, options)?.asImageBitmap()
}

@Composable
internal fun SupplierPhotoPreview(photo: SupplierPhoto) {
    val pixels = with(LocalDensity.current) { 96.dp.roundToPx() }
    var loading by remember(photo, pixels) { mutableStateOf(true) }
    val preview by produceState<ImageBitmap?>(null, photo, pixels) {
        value = try { supplierPreview(photo, pixels) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        loading = false
    }
    val modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
    when {
        preview != null -> Image(preview!!, "Selected supply photo preview", modifier)
        loading -> CircularProgressIndicator(modifier)
        else -> Text("Photo preview unavailable")
    }
}
