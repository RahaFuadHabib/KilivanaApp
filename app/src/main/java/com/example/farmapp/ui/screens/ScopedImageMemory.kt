package com.example.farmapp.ui.screens

import kotlinx.coroutines.*

/** Opt-in gallery lifetime only. No disk cache, credentials, global singleton or cross-account keys. */
internal class ScopedImageMemory<T>(private val current: () -> Boolean, private val weightOf: (T) -> Int,
    private val maxBytes: Int = 8 * 1024 * 1024, private val maxEntries: Int = 8,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) : AutoCloseable {
    private data class Entry<T>(val value: T, val weight: Int)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry<T>>(8, 0.75f, true)
    private val pending = mutableMapOf<String, Deferred<T?>>()
    private var bytes = 0
    private var closed = false

    suspend fun get(key: String, loader: suspend () -> T?): T? {
        val task: Deferred<T?>
        synchronized(lock) {
            if (closed || !current()) { entries.clear(); bytes = 0; return null }
            entries[key]?.let { return it.value }
            task = pending[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    val result = loader()
                    synchronized(lock) {
                        if (closed || !current()) null else {
                            if (result != null) {
                                val weight = weightOf(result)
                                if (weight in 1..maxBytes) {
                                    entries.put(key, Entry(result, weight))?.let { bytes -= it.weight }
                                    bytes += weight
                                    val iterator = entries.entries.iterator()
                                    while (bytes > maxBytes || entries.size > maxEntries) {
                                        bytes -= iterator.next().value.weight
                                        iterator.remove()
                                    }
                                }
                            }
                            result
                        }
                    }
                } finally { synchronized(lock) { pending.remove(key) } }
            }.also { pending[key] = it }
        }
        task.start()
        val result = task.await()
        return synchronized(lock) { if (!closed && current()) result else null }
    }
    override fun close() {
        synchronized(lock) { closed = true; entries.clear(); bytes = 0; pending.clear() }
        scope.cancel()
    }
}
