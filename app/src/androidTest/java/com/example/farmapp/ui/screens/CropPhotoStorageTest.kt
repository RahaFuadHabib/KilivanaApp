package com.example.farmapp.ui.screens

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CropPhotoStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun providerUsesContentUriAndImportCreatesAnIndependentPreviewableCopy() {
        val storage = CropPhotoStorage(context)
        val original = storage.create()
        var copy: String? = null
        try {
            val uri = storage.uri(original)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.crop.photos", uri.authority)
            val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
            try {
                context.contentResolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            } finally { bitmap.recycle() }
            copy = storage.importImage(uri)
            storage.delete(original)
            assertTrue(storage.isImage(copy))
            val preview = storage.preview(copy)
            try { assertTrue(preview.width <= 720); assertTrue(preview.height <= 720) }
            finally { preview.recycle() }
        } finally { storage.delete(original); copy?.let { storage.delete(it) } }
    }
    @Test fun fileAccessRejectsTraversalAndUnrelatedCacheNames() {
        val storage = CropPhotoStorage(context)
        for (name in listOf("../session.enc", "/tmp/photo.jpg", "unrelated.jpg", "crop_x/../x.jpg")) {
            try { storage.file(name); fail(name) } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun failedImportRemovesItsTemporaryFile() {
        val storage = CropPhotoStorage(context)
        val original = storage.create()
        try {
            storage.file(original).writeText("not an image")
            val before = storage.file(original).parentFile!!.list()!!.toSet()
            try { storage.importImage(storage.uri(original)); fail() } catch (_: java.io.IOException) { }
            assertEquals(before, storage.file(original).parentFile!!.list()!!.toSet())
        } finally { storage.delete(original) }
    }
}
