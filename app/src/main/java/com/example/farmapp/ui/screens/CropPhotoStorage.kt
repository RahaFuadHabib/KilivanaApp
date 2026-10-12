package com.example.farmapp.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Temporary app-owned originals. Only this narrow cache directory is exposed by FileProvider. */
internal class CropPhotoStorage(private val context: Context) {
    private val directory get() = File(context.cacheDir, "crop_photos").apply {
        if (!exists() && !mkdirs()) throw IOException("Photo storage unavailable")
    }

    fun create(extension: String = ".jpg"): String = File.createTempFile("crop_", extension, directory).name
    fun file(name: String): File {
        require(name.matches(Regex("crop_[a-zA-Z0-9_-]+\\.(jpg|img)")))
        return File(directory, name)
    }
    fun uri(name: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.crop.photos", file(name))
    fun delete(name: String) { runCatching { file(name).delete() } }

    fun importImage(uri: Uri): String {
        val type = context.contentResolver.getType(uri)
        if (type != null && !type.startsWith("image/")) throw IOException("Not an image")
        val name = create(".img")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Photo unavailable")
            input.use { source -> file(name).outputStream().use { target ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 30L * 1024 * 1024) throw IOException("Photo exceeds 30 MB")
                    target.write(buffer, 0, count)
                }
            } }
            if (!isImage(name)) throw IOException("Image cannot be read")
            return name
        } catch (e: Exception) { delete(name); throw e }
    }

    fun multipart(name: String): okhttp3.MultipartBody.Part {
        val source = file(name)
        if (!source.exists() || source.length() == 0L) throw com.example.farmapp.repository.ProductFailure("Photo unavailable. Please check the saved draft.")
        if (source.length() > 5L * 1024 * 1024) throw com.example.farmapp.repository.ProductFailure("Photo exceeds 5 MiB. Choose a smaller image before publishing.")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, options)
        val mime = options.outMimeType
        val extension = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> throw com.example.farmapp.repository.ProductFailure("Choose JPEG, PNG, GIF or WebP photos.")
        }
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = source.length()
            override fun writeTo(sink: okio.BufferedSink) {
                source.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                    }
                }
            }
        }
        return okhttp3.MultipartBody.Part.createFormData("file", source.nameWithoutExtension + "." + extension, body)
    }

    fun isImage(name: String): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(name).absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    /** Decode off the UI thread and bound thumbnail memory; originals stay full resolution. */
    fun preview(name: String): Bitmap {
        val source = file(name)
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val scale = (720f / max(info.size.width, info.size.height)).coerceAtMost(1f)
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Photo unavailable")
        options.inSampleSize = 1
        while (max(options.outWidth, options.outHeight) / options.inSampleSize > 720) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, options) ?: throw IOException("Photo unavailable")
        val orientation = runCatching { ExifInterface(source.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> { setRotate(180f); postScale(-1f, 1f) }
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
    }
}
