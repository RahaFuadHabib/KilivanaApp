package com.example.farmapp.network.canonical

/** Release has no diagnostic provider, UI or retained trace. */
object AuthAttemptTrace {
    fun record(@Suppress("UNUSED_PARAMETER") event: AuthAttemptEvent) = Unit
}
