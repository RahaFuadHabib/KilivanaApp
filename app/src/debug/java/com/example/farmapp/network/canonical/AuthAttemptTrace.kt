package com.example.farmapp.network.canonical

import com.example.farmapp.BuildConfig

/** Volatile, bounded, debug-only, ten-minute user opt-in. No network or storage access. */
object AuthAttemptTrace {
    private val events = ArrayDeque<AuthAttemptEvent>()
    private var until = 0L
    @Synchronized fun arm() { if (BuildConfig.ENABLE_AUTH_DIAGNOSTICS) { events.clear(); until = System.nanoTime() + 600_000_000_000L } }
    @Synchronized fun clear() { events.clear(); until = 0L }
    @Synchronized fun record(event: AuthAttemptEvent) {
        if (!BuildConfig.ENABLE_AUTH_DIAGNOSTICS || until == 0L || System.nanoTime() >= until) return
        if (events.size >= 64) events.removeFirst()
        events.addLast(event)
    }
    @Synchronized fun readReport(): String {
        if (!BuildConfig.ENABLE_AUTH_DIAGNOSTICS || until == 0L || System.nanoTime() >= until) {
            events.clear(); return "No active user-armed authentication report."
        }
        return "Debug authentication trace; user armed; metadata only.\nCanonical API enabled: ${BuildConfig.USE_CANONICAL_BACKEND}\n" +
            "Events: ${events.size}\n" + events.mapIndexed { i, e ->
                "${i+1}. ${e.operation} ${e.step}" +
                    (e.httpStatus?.takeIf { it in 100..599 }?.let { " HTTP=$it" } ?: "") +
                    (e.network?.let { " network=$it" } ?: "") + (e.format?.let { " format=$it" } ?: "") +
                    (e.stage?.let { " stage=$it" } ?: "") + (e.kind?.let { " kind=$it" } ?: "")
            }.joinToString("\n")
    }
}
