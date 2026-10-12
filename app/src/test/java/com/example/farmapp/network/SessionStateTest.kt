package com.example.farmapp.network

import com.example.farmapp.data.model.AuthResponse
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SessionStateTest {
    @Test fun completeSessionSurvivesARecreatedOwner() {
        val disk = MemoryPersistence()
        val first = SessionState().apply { initialize(disk) }
        assertTrue(first.establish(first.session.value, testAuth(), false))
        val recreated = SessionState().apply { initialize(disk) }
        assertEquals(testAuth().copy(refreshTokenExpiresAt = disk.saved!!.refreshTokenExpiresAt), recreated.session.value.auth)
    }

    @Test fun refreshAtomicallyReplacesTheEntireResponseAndPersistsIt() {
        val disk = MemoryPersistence(testAuth())
        val store = SessionState().apply { initialize(disk) }
        val before = store.session.value
        val updated = testAuth("new-access", "new-refresh", "BUYER")
        val result = store.rotate(before, updated)
        assertSame(result, store.session.value)
        assertEquals(updated, disk.saved)
        assertEquals(before.generation, result!!.generation)
        assertNull(store.rotate(before, testAuth("stale-access")))
    }

    @Test fun legacySessionRestoresAndRefreshAtomicallyAddsAuthoritativeProfile() {
        val legacy = testAuth().copy(username = null, region = null, phoneNumber = null, verificationStatus = null)
        val disk = MemoryPersistence(legacy)
        val store = SessionState().apply { initialize(disk) }
        assertEquals(legacy, store.session.value.auth)
        val updated = testAuth("new-access", "new-refresh").copy(username = "authoritative", region = "Kiambu", phoneNumber = "+254712345678")
        assertNotNull(store.rotate(store.session.value, updated))
        assertEquals(updated, store.session.value.auth)
        assertEquals(updated, SessionState().apply { initialize(disk) }.session.value.auth)
    }

    @Test fun logoutBlocksLateRefreshAndLoginCompletion() {
        val store = signedInStore()
        val before = store.session.value
        val loggingOut = store.beginLogout()!!
        assertNull(store.rotate(before, testAuth("late-access")))
        assertFalse(store.establish(before, testAuth("late-login"), false))
        assertTrue(store.clear(loggingOut))
        assertNull(store.session.value.auth)
        assertNull(store.rotate(before, testAuth("later-access")))
    }

    @Test fun oldRequestsCannotClearANewerSession() {
        val store = signedInStore()
        val before = store.session.value
        store.clear(before)
        store.establish(store.session.value, testAuth("new-login"), false)
        assertFalse(store.clear(before))
        assertEquals("new-login", store.session.value.auth!!.accessToken)
    }

    @Test fun expiredOrMalformedStoredSessionsAreDiscarded() {
        val expired = MemoryPersistence(testAuth().copy(refreshTokenExpiresAt = Instant.EPOCH.toString()))
        val store = SessionState().apply { initialize(expired) }
        assertNull(store.session.value.auth)
        assertNull(expired.saved)
        val malformed = Gson().fromJson("{}", AuthResponse::class.java)
        val other = SessionState().apply { initialize(MemoryPersistence(malformed)) }
        assertNull(other.session.value.auth)
    }

    @Test fun demoSessionsAreNeverPersisted() {
        val disk = MemoryPersistence()
        val store = SessionState().apply { initialize(disk) }
        store.establish(store.session.value, testAuth(), true)
        assertTrue(store.session.value.isDemo)
        assertNull(disk.saved)
        assertEquals(0, disk.writes)
    }

    @Test fun failedPersistenceDoesNotPublishPartiallyUpdatedTokens() {
        val disk = MemoryPersistence(testAuth())
        val store = SessionState().apply { initialize(disk) }
        val before = store.session.value
        disk.failWrite = true
        try { store.rotate(before, testAuth("new-access", "new-refresh")); fail() }
        catch (_: java.io.IOException) { }
        assertSame(before, store.session.value)
        assertEquals(before.auth, disk.saved)
    }
}
