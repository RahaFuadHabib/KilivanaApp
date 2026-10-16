package com.example.farmapp.supplier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Photo Picker grants only a selected URI. No storage permission, camera, file cache or URI persistence. */
internal fun readSupplierPhoto(context: Context, uri: Uri): SupplierPhoto {
    val mime = context.contentResolver.getType(uri)
    if (mime != null && mime !in setOf("image/jpeg", "image/png", "image/gif", "image/webp"))
        throw SupplierFailure("Choose JPEG, PNG, GIF or WebP.", true)
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var size = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            if (size > 5L * 1024 * 1024) throw SupplierFailure("Choose a photo no larger than 5 MiB.", true)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw SupplierFailure("The selected photo could not be opened.", true)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > 50_000_000)
        throw SupplierFailure("Choose a valid image with at most 50 million pixels.", true)
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw SupplierFailure("Photo preview is unavailable.", true)
    return try {
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw SupplierFailure("The photo could not be prepared.", true)
        SupplierPhoto(output.toByteArray()).also(::validateSupplierPhoto)
    } finally { bitmap.recycle() }
}
