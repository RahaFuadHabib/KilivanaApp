package com.example.farmapp.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking

/** Only image delivery routes on the configured origin may receive credentials. */
fun protectedImageUrl(base: HttpUrl, target: HttpUrl): Boolean =
    target.username.isEmpty() && target.password.isEmpty() &&
        target.scheme == base.scheme && target.host == base.host && target.port == base.port &&
        (target.encodedPath.startsWith(base.encodedPath + "images/") ||
            target.encodedPath.startsWith("/uploads/"))

fun imageSessionCurrent(expected: SessionSnapshot, current: SessionSnapshot): Boolean =
    expected.generation == current.generation && !current.loggingOut && !current.isDemo

class ImageRequestInterceptor(
    private val base: HttpUrl,
    private val sessions: SessionStore,
    private val refresh: RefreshCoordinator
) : Interceptor {
    private val auth = AuthTokenInterceptor(base, sessions, refresh) { protectedImageUrl(base, it) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val expected = sessions.session.value
        if (!imageSessionCurrent(expected, sessions.session.value)) throw IOException("Image session changed")
        val credentials = expected.auth
        // Public routes mask expired credentials as anonymous (and private images as 404).
        // Refresh before delivery rather than interpreting a visibility 404 as token expiry.
        if (protectedImageUrl(base, chain.request().url) && credentials?.backendContract == "canonical") {
            val expiry = credentials.accessTokenExpiresAt?.let(Instant::parse)
            if (expiry == null || !expiry.isAfter(Instant.now().plusSeconds(30))) {
                runBlocking { refresh.refresh(expected) }
                if (!imageSessionCurrent(expected, sessions.session.value)) throw IOException("Image session changed")
            }
        }
        // No caller-supplied credentials, HTTP cache, redirects, or HTTP logging on this client.
        val response = auth.intercept(chain)
        if (!imageSessionCurrent(expected, sessions.session.value)) {
            response.close()
            throw IOException("Image session changed")
        }
        return response
    }
}
