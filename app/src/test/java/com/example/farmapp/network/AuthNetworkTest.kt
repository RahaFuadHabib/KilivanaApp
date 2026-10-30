package com.example.farmapp.network

import com.example.farmapp.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class AuthNetworkTest {
    private val base = "https://example.test/api/v1/"
    private fun response(request: Request, code: Int, body: String = "{}") =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(body.toResponseBody("application/json".toMediaType())).build()

    private fun service(transport: Interceptor): ApiService = Retrofit.Builder()
        .baseUrl(base)
        .client(OkHttpClient.Builder().addInterceptor(transport).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)

    @Test fun retrofitUsesExactUrlsBodiesAndNoBearerForAuthEndpoints() = runBlocking {
        val requests = CopyOnWriteArrayList<Pair<Request, String>>()
        val api = service { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            requests.add(request to body)
            response(request, 200, if (request.url.encodedPath.endsWith("logout")) "" else Gson().toJson(testAuth()))
        }
        assertTrue(api.signup(SignupValidation.prepare(signupInput())).isSuccessful)
        assertTrue(api.login(LoginRequest("person@example.test", "password")).isSuccessful)
        assertTrue(api.refresh(RefreshRequest("refresh-token")).isSuccessful)
        assertTrue(api.logout(LogoutRequest("refresh-token")).isSuccessful)
        assertEquals(listOf("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"),
            requests.map { it.first.url.encodedPath })
        assertTrue(requests.all { it.first.header("Authorization") == null })
        assertEquals(setOf("identifier", "password"), JsonParser.parseString(requests[1].second).asJsonObject.keySet())
        assertEquals("refresh-token", JsonParser.parseString(requests[2].second).asJsonObject["refreshToken"].asString)
        assertEquals(setOf("refreshToken"), JsonParser.parseString(requests[3].second).asJsonObject.keySet())
    }

    @Test fun protectedSingleReadDoesNotRefreshRetryOrClearSessionOn401() {
        val store = signedInStore()
        val before = store.session.value
        var refreshCalls = 0; var gets = 0
        val api = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): retrofit2.Response<AuthResponse> {
                refreshCalls++; error("Read-only diagnostic must not refresh")
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(base.toHttpUrl(), store, RefreshCoordinator(api, store)))
            .addInterceptor { chain -> gets++; response(chain.request(), 401) }.build()
        client.newCall(Request.Builder().url(base + "carts/buyer/42")
            .tag(PreserveSessionOnReadFailure::class.java, PreserveSessionOnReadFailure()).build())
            .execute().use { assertEquals(401, it.code) }
        assertEquals(1, gets); assertEquals(0, refreshCalls); assertSame(before, store.session.value)
    }

    @Test fun unauthorizedApiRequestRefreshesAndRetriesOnceWithRotatedToken() {
        val store = signedInStore()
        val refreshCalls = AtomicInteger()
        val api = service { chain ->
            refreshCalls.incrementAndGet()
            response(chain.request(), 200, Gson().toJson(testAuth("access-new", "refresh-new")))
        }
        val headers = mutableListOf<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(base.toHttpUrl(), store, RefreshCoordinator(api, store)))
            .addInterceptor { chain ->
                headers.add(chain.request().header("Authorization"))
                response(chain.request(), if (headers.size == 1) 401 else 200)
            }.build()
        client.newCall(Request.Builder().url(base + "farmers").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(listOf("Bearer access-old", "Bearer access-new"), headers)
        assertEquals(1, refreshCalls.get())
        assertEquals("refresh-new", store.session.value.auth!!.refreshToken)
    }

    @Test fun secondUnauthorizedResponseStopsAndClearsSession() {
        val store = signedInStore()
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(base.toHttpUrl(), store, RefreshCoordinator(StubAuthService(), store)))
            .addInterceptor { chain -> requests.incrementAndGet(); response(chain.request(), 401) }.build()
        client.newCall(Request.Builder().url(base + "farmers").build()).execute().use { assertEquals(401, it.code) }
        assertEquals(2, requests.get())
        assertNull(store.session.value.auth)
    }

    @Test fun otherOriginsAndAuthRequestsNeverReceiveSessionHeadersOrRefresh() {
        val store = signedInStore()
        val headers = mutableListOf<String?>()
        var refreshCalls = 0
        val api = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): retrofit2.Response<AuthResponse> {
                refreshCalls++
                return super.refresh(request)
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(base.toHttpUrl(), store, RefreshCoordinator(api, store)))
            .addInterceptor { chain -> headers.add(chain.request().header("Authorization")); response(chain.request(), 401) }.build()
        listOf(base + "auth/refresh", "https://elsewhere.test/api/v1/farmers", "https://example.test/other").forEach {
            client.newCall(Request.Builder().url(it).build()).execute().close()
        }
        assertTrue(headers.all { it == null })
        assertEquals(0, refreshCalls)
    }

    @Test fun refreshEndpoint401DoesNotRecurseIntoAnotherRefresh() {
        val store = signedInStore()
        val refreshCalls = AtomicInteger()
        val api = service { chain -> refreshCalls.incrementAndGet(); response(chain.request(), 401) }
        val dataCalls = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(base.toHttpUrl(), store, RefreshCoordinator(api, store)))
            .addInterceptor { chain -> dataCalls.incrementAndGet(); response(chain.request(), 401) }.build()
        client.newCall(Request.Builder().url(base + "farmers").build()).execute().use { assertEquals(401, it.code) }
        assertEquals(1, refreshCalls.get())
        assertEquals(1, dataCalls.get())
        assertNull(store.session.value.auth)
    }

    @Test fun demoTokensCannotReachProductionApi() {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        store.establish(store.session.value, testAuth(), true)
        var reachedTransport = false
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(base.toHttpUrl(), store, RefreshCoordinator(StubAuthService(), store)))
            .addInterceptor { chain -> reachedTransport = true; response(chain.request(), 200) }.build()
        try {
            client.newCall(Request.Builder().url(base + "farmers").build()).execute().close()
            fail()
        } catch (_: java.io.IOException) { }
        assertFalse(reachedTransport)
    }
}
