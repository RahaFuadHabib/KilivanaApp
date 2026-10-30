package com.example.farmapp.supplier

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.farmapp.ui.screens.ProductBitmapLoader
import com.example.farmapp.ui.screens.ProductImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.BitmapFactory
import android.os.Looper
import com.example.farmapp.ui.screens.ScopedImageMemory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import org.junit.Assert.*

/** Synthetic image loader; no API requests, credentials or personal images. */
class SupplierImagePerformanceTest {
    @get:Rule val compose = createComposeRule()
    private fun imageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        return output.toByteArray()
    }
    @Test fun heroAndTwoThumbnailsBaselineLoadBudget() {
        val loads = AtomicInteger()
        val bytes = imageBytes()
        val loader: ProductBitmapLoader = { _, _ ->
            loads.incrementAndGet()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
        }
        compose.setContent { Column {
            ProductImage("synthetic-a", "Hero", Modifier.size(96.dp), loader = loader)
            ProductImage("synthetic-a", "Thumbnail A", Modifier.size(48.dp), loader = loader)
            ProductImage("synthetic-b", "Thumbnail B", Modifier.size(48.dp), loader = loader)
        } }
        compose.waitUntil(5000) { loads.get() == 3 }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Hero photo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Hero photo").assertExists()
        compose.onNodeWithContentDescription("Thumbnail A photo").assertExists()
        assertEquals(3, loads.get())
    }
    @Test fun sharedGalleryLoadsTwoUniqueImagesOnceAndSurvivesRecomposition() {
        val loads = AtomicInteger()
        val ready = AtomicInteger()
        val bytes = imageBytes()
        val loader: ProductBitmapLoader = { _, _ ->
            loads.incrementAndGet()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
        }
        var update by mutableIntStateOf(0)
        compose.setContent {
            val memory = remember { ScopedImageMemory<ImageBitmap>({ true }, { it.asAndroidBitmap().allocationByteCount }) }
            DisposableEffect(memory) { onDispose { memory.close() } }
            Column {
                ProductImage("synthetic-a", "Hero $update", Modifier.size(96.dp), memory = memory, loader = loader)
                ProductImage("synthetic-a", "Thumbnail A", Modifier.size(48.dp), memory = memory, loader = loader)
                ProductImage("synthetic-b", "Thumbnail B", Modifier.size(48.dp), memory = memory, loader = loader)
            }
            SideEffect { ready.incrementAndGet() }
        }
        compose.waitUntil(5000) { loads.get() == 2 && ready.get() > 0 }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Hero 0 photo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Hero 0 photo").assertExists()
        compose.runOnIdle { update++ }
        compose.onNodeWithContentDescription("Hero 1 photo").assertExists()
        assertEquals(2, loads.get())
    }
    @Test fun previewDecodeRunsOffMainAndKeepsUploadBytes() {
        val source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, 85, output); source.recycle()
        val photo = SupplierPhoto(output.toByteArray())
        val original = photo.bytes.copyOf()
        val calls = AtomicInteger()
        val offMain = AtomicBoolean(true)
        val fullBytes = AtomicInteger()
        var preview by mutableStateOf<ImageBitmap?>(null)
        compose.setContent {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val full = BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size)
                    fullBytes.set(full.allocationByteCount); full.recycle()
                }
                preview = supplierPreview(photo, 300) { bytes, options ->
                    calls.incrementAndGet()
                    if (Looper.myLooper() == Looper.getMainLooper()) offMain.set(false)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
            }
            preview?.let { androidx.compose.foundation.Image(it, "Synthetic async preview") }
        }
        compose.waitUntil(5000) { preview != null }
        assertTrue(offMain.get()); assertEquals(2, calls.get())
        assertTrue(preview!!.width in 300..600)
        assertArrayEquals(original, photo.bytes)
        val sampledBytes = preview!!.asAndroidBitmap().allocationByteCount
        assertTrue(sampledBytes < fullBytes.get())
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("supplier_preview_allocation", "synthetic full=${fullBytes.get()} sampled=$sampledBytes; main-thread decode calls=0")
        })
    }
}
