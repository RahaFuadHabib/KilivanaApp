package com.example.farmapp.network

import com.example.farmapp.data.model.RefreshRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Shared by all authenticated calls. The service uses a separate, non-refreshing client. */
class RefreshCoordinator(
    private val service: ApiService,
    private val sessions: SessionStore,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val mutex = Mutex()
    private var failedSnapshot: SessionSnapshot? = null
    private var retryAfterNanos = 0L

    suspend fun refresh(failed: SessionSnapshot): SessionSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = sessions.session.value
            val auth = current.auth ?: return@withLock null
            if (current.generation != failed.generation || current.loggingOut || current.isDemo) return@withLock null
            // A concurrent request already rotated the pair for this same session.
            if (current !== failed) return@withLock current
            if (failedSnapshot === current && nanoTime() < retryAfterNanos) return@withLock null
            if (!AuthValidation.canRefresh(auth)) {
                sessions.clear(current)
                return@withLock null
            }
            try {
                val response = service.refresh(RefreshRequest(auth.refreshToken))
                if (response.isSuccessful) {
                    val updated = AuthValidation.validate(response.body())
                    if (!AuthValidation.canRefresh(updated)) throw InvalidAuthResponse()
                    val rotated = sessions.rotate(current, updated)
                    failedSnapshot = null
                    rotated
                } else {
                    response.errorBody()?.close()
                    if (response.code() in setOf(400, 401, 403)) {
                        sessions.clear(current)
                    } else {
                        // Keep an otherwise valid session on transient failure; suppress a 401 stampede.
                        failedSnapshot = current
                        retryAfterNanos = nanoTime() + 5_000_000_000L
                    }
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: InvalidAuthResponse) {
                sessions.clear(current)
                null
            } catch (_: IOException) {
                failedSnapshot = current
                retryAfterNanos = nanoTime() + 5_000_000_000L
                null
            } catch (_: com.google.gson.JsonParseException) {
                sessions.clear(current)
                null
            }
        }
    }
}

class AuthTokenInterceptor(
    private val baseUrl: HttpUrl,
    private val sessions: SessionStore,
    private val refresh: RefreshCoordinator
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val belongsToApi = url.scheme == baseUrl.scheme && url.host == baseUrl.host &&
            url.port == baseUrl.port && url.encodedPath.startsWith(baseUrl.encodedPath)
        if (!belongsToApi || url.encodedPath.startsWith(baseUrl.encodedPath + "auth/")) {
            return chain.proceed(request)
        }
        val snapshot = sessions.session.value
        if (snapshot.isDemo) throw IOException("Offline demo mode does not call the backend.")
        if (snapshot.loggingOut) throw IOException("Sign-out is in progress.")
        val auth = snapshot.auth ?: return chain.proceed(request)
        val authenticated = request.newBuilder().header("Authorization", "Bearer ${auth.accessToken}").build()
        val response = chain.proceed(authenticated)
        if (response.code != 401 || request.body?.isOneShot() == true ||
            request.body?.isDuplex() == true || chain.call().isCanceled()) return response

        val updated = try {
            // OkHttp interceptors are synchronous. Only this bridge blocks; Retrofit refresh is suspend
            // on a separate client's dispatcher, avoiding recursive interception and pool starvation.
            runBlocking { refresh.refresh(snapshot) }
        } catch (e: Exception) {
            response.close()
            throw e
        } ?: return response
        if (sessions.session.value !== updated || chain.call().isCanceled()) return response
        response.close()
        val retried = chain.proceed(request.newBuilder()
            .header("Authorization", "Bearer ${updated.auth!!.accessToken}").build())
        if (retried.code == 401) {
            try { sessions.clear(updated) } catch (_: IOException) { /* Memory is cleared even if disk fails. */ }
        }
        return retried
    }
}
