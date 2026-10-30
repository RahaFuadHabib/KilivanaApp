package com.example.farmapp.supplier

import com.example.farmapp.ui.screens.ScopedImageMemory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SupplierImageMemoryTest {
    @Test fun simultaneousHeroAndThumbnailShareOneLoad() = runBlocking {
        var loads = 0
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val memory = ScopedImageMemory<String>({ true }, { it.length }, scope = scope)
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { memory.get("a") { loads++; release.await(); "image" } }
            val second = async(start = CoroutineStart.UNDISPATCHED) { memory.get("a") { loads++; "wrong" } }
            assertEquals(1, loads)
            release.complete(Unit)
            assertEquals("image", first.await()); assertEquals("image", second.await())
        } finally { memory.close() }
    }
    @Test fun accountChangeRejectsCachedAndLateImages() = runBlocking {
        var current = true
        val memory = ScopedImageMemory<String>({ current }, { it.length })
        try {
            assertEquals("image", memory.get("a") { "image" })
            current = false
            assertNull(memory.get("a") { fail("Must not load after session change"); null })
        } finally { memory.close() }
        var ownerCurrent = true
        val late = ScopedImageMemory<String>({ ownerCurrent }, { it.length })
        try { assertNull(late.get("a") { ownerCurrent = false; "private image" }) }
        finally { late.close() }
    }
    @Test fun byteBudgetAndEntryBudgetEvictWithoutDiskStorage() = runBlocking {
        var loads = 0
        val memory = ScopedImageMemory<String>({ true }, { it.length }, maxBytes = 4, maxEntries = 2)
        try {
            suspend fun load(key: String) = memory.get(key) { loads++; "xx" }
            load("a"); load("b"); load("a"); load("c"); load("b")
            assertEquals(4, loads)
        } finally { memory.close() }
    }
    @Test fun failuresAreNotCachedAndClosePreventsFurtherLoads() = runBlocking {
        val memory = ScopedImageMemory<String>({ true }, { it.length })
        var loads = 0
        assertNull(memory.get("a") { loads++; null })
        assertEquals("ok", memory.get("a") { loads++; "ok" })
        assertEquals(2, loads)
        memory.close()
        assertNull(memory.get("a") { fail("Closed gallery must not load"); null })
    }
    @Test fun cancelledConsumerDoesNotCancelOtherImageConsumers() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val memory = ScopedImageMemory<String>({ true }, { it.length }, scope = scope)
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { memory.get("a") { release.await(); "image" } }
            val second = async(start = CoroutineStart.UNDISPATCHED) { memory.get("a") { error("Duplicate fetch") } }
            first.cancel(); release.complete(Unit)
            assertEquals("image", second.await())
        } finally { memory.close() }
    }
}
