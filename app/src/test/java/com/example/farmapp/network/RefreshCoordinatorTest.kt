package com.example.farmapp.network

import com.example.farmapp.data.model.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RefreshCoordinatorTest {
    @Test fun rotatedSnapshotIsReusedEvenIfAccessTokenStringIsUnchanged() = runBlocking {
        val store = signedInStore()
        val before = store.session.value
        var calls = 0
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                calls++
                return Response.success(testAuth("access-old", "refresh-rotated"))
            }
        }, store)
        val first = coordinator.refresh(before)
        assertSame(first, coordinator.refresh(before))
        assertEquals(1, calls)
        assertEquals("refresh-rotated", store.session.value.auth!!.refreshToken)
    }

    @Test fun concurrentUnauthorizedRequestsShareOneRefresh() = runBlocking {
        val store = signedInStore()
        val original = store.session.value
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                calls.incrementAndGet()
                entered.complete(Unit)
                release.await()
                return Response.success(testAuth("rotated-access", "rotated-refresh"))
            }
        }
        val coordinator = RefreshCoordinator(service, store)
        val requests = (1..12).map { async(Dispatchers.Default) { coordinator.refresh(original) } }
        entered.await()
        release.complete(Unit)
        val results = requests.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(results.all { it === store.session.value })
        assertEquals("rotated-refresh", store.session.value.auth!!.refreshToken)
    }

    @Test fun refreshRejectionClearsSessionAndDoesNotRetry() = runBlocking {
        val store = signedInStore()
        val before = store.session.value
        var calls = 0
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                calls++
                return Response.error(401, "{}".toResponseBody())
            }
        }, store)
        assertNull(coordinator.refresh(before))
        assertNull(coordinator.refresh(before))
        assertNull(store.session.value.auth)
        assertEquals(1, calls)
    }

    @Test fun transientFailuresPreserveSessionAndAreThrottled() = runBlocking {
        val store = signedInStore()
        val before = store.session.value
        var calls = 0
        var time = 0L
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                calls++
                throw IOException("offline")
            }
        }, store, nanoTime = { time })
        repeat(8) { assertNull(coordinator.refresh(before)) }
        assertSame(before, store.session.value)
        assertEquals(1, calls)
        time = 6_000_000_000
        coordinator.refresh(before)
        assertEquals(2, calls)
    }

    @Test fun lateRefreshCannotResurrectLogout() = runBlocking {
        val store = signedInStore()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                started.complete(Unit)
                release.await()
                return Response.success(testAuth("late-token"))
            }
        }, store)
        val before = store.session.value
        val refresh = async(Dispatchers.Default) { coordinator.refresh(before) }
        started.await()
        store.clear(store.beginLogout()!!)
        release.complete(Unit)
        assertNull(refresh.await())
        assertNull(store.session.value.auth)
    }

    @Test fun changedIdentityInRefreshIsRejected() = runBlocking {
        val store = signedInStore()
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> =
                Response.success(testAuth().copy(userId = "eb1f7b4e-8392-4bf4-8cdc-f96083411001"))
        }, store)
        assertNull(coordinator.refresh(store.session.value))
        assertNull(store.session.value.auth)
    }

    @Test fun refreshUpgradesLegacyProfileThroughNormalSessionReplacement() = runBlocking {
        val legacy = testAuth().copy(username = null, region = null, phoneNumber = null, verificationStatus = null)
        val disk = MemoryPersistence(legacy)
        val store = SessionState().apply { initialize(disk) }
        val expected = testAuth("new-access", "new-refresh").copy(username = "updated", region = "Kiambu", phoneNumber = "+254712345678")
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest) = Response.success(expected)
        }, store)
        assertEquals(expected, coordinator.refresh(store.session.value)!!.auth)
        assertEquals(expected, disk.saved)
    }

    @Test fun cancellationIsRethrown() = runBlocking {
        val store = signedInStore()
        val coordinator = RefreshCoordinator(object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> = throw CancellationException()
        }, store)
        try { coordinator.refresh(store.session.value); fail() } catch (_: CancellationException) { }
        assertNotNull(store.session.value.auth)
    }
}
