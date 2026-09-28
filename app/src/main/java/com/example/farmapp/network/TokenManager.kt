package com.example.farmapp.network

import com.example.farmapp.data.model.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One immutable snapshot carries identity and both tokens, preventing mixed token pairs. */
data class SessionSnapshot(
    val auth: AuthResponse? = null,
    val generation: Long = 0,
    val isDemo: Boolean = false,
    val loggingOut: Boolean = false
) {
    override fun toString() = "SessionSnapshot(redacted)"
}

interface SessionPersistence {
    fun read(): AuthResponse?
    fun write(auth: AuthResponse)
    fun clear()
}

interface SessionStore {
    val session: StateFlow<SessionSnapshot>
    fun initialize(persistence: SessionPersistence)
    fun establish(expected: SessionSnapshot, auth: AuthResponse, demo: Boolean): Boolean
    fun rotate(expected: SessionSnapshot, auth: AuthResponse): SessionSnapshot?
    fun beginLogout(): SessionSnapshot?
    fun clear(expected: SessionSnapshot): Boolean
}

/** Sole production session owner; the internal engine is separately testable. */
object TokenManager : SessionStore by SessionState()

internal class SessionState : SessionStore {
    private val lock = Any()
    private val state = MutableStateFlow(SessionSnapshot())
    override val session = state.asStateFlow()
    private var persistence: SessionPersistence? = null

    override fun initialize(persistence: SessionPersistence) = synchronized(lock) {
        if (this.persistence != null) return@synchronized
        val restored = persistence.read()
        val valid = if (restored != null) {
            try {
                AuthValidation.validate(restored).takeIf { AuthValidation.canRefresh(it) }
            } catch (_: InvalidAuthResponse) { null }
        } else null
        if (restored != null && valid == null) persistence.clear()
        this.persistence = persistence
        state.value = SessionSnapshot(auth = valid, generation = state.value.generation + 1)
    }

    override fun establish(expected: SessionSnapshot, auth: AuthResponse, demo: Boolean): Boolean = synchronized(lock) {
        if (state.value !== expected || expected.loggingOut) return@synchronized false
        AuthValidation.validate(auth)
        val disk = checkNotNull(persistence) { "Session storage is not initialized" }
        if (demo) disk.clear() else disk.write(auth)
        state.value = SessionSnapshot(auth, expected.generation + 1, isDemo = demo)
        true
    }

    override fun rotate(expected: SessionSnapshot, auth: AuthResponse): SessionSnapshot? = synchronized(lock) {
        if (state.value !== expected || expected.loggingOut || expected.isDemo || expected.auth == null) {
            return@synchronized null
        }
        AuthValidation.validate(auth)
        if (auth.userId != expected.auth.userId) throw InvalidAuthResponse()
        checkNotNull(persistence).write(auth)
        SessionSnapshot(auth, expected.generation).also { state.value = it }
    }

    override fun beginLogout(): SessionSnapshot? = synchronized(lock) {
        val current = state.value
        if (current.auth == null || current.loggingOut) return@synchronized null
        current.copy(loggingOut = true).also { state.value = it }
    }

    override fun clear(expected: SessionSnapshot): Boolean = synchronized(lock) {
        if (state.value !== expected) return@synchronized false
        try {
            persistence?.clear()
        } finally {
            // A failed disk operation must never leave an authenticated UI in memory.
            state.value = SessionSnapshot(generation = expected.generation + 1)
        }
        true
    }
}
