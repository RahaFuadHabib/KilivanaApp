package com.example.farmapp.network.canonical

import okhttp3.*
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.net.UnknownHostException
import java.net.ConnectException
import java.io.InterruptedIOException
import javax.net.ssl.SSLException

enum class AuthTraceOperation { REGISTER, LOGIN, OTP_REQUEST, OTP_VERIFY }
enum class AuthTraceStep { REQUEST_STARTED, HTTP_RESPONSE, NETWORK_FAILURE, RESPONSE_READ_FAILURE, VERIFICATION_REQUIRED, FLOW_SUCCEEDED, FLOW_FAILED }
enum class AuthNetworkCategory { DNS, TLS, CONNECT, TIMEOUT, IO, OTHER }
enum class AuthResponseFormat { JSON, HTML, OTHER }
/** The recorder cannot accept identities, credentials, OTPs, URLs, raw headers, bodies or exception text. */
data class AuthAttemptEvent(val operation: AuthTraceOperation, val step: AuthTraceStep,
    val httpStatus: Int? = null, val network: AuthNetworkCategory? = null,
    val format: AuthResponseFormat? = null, val stage: AuthStage? = null, val kind: AuthFailureKind? = null)
internal fun authNetworkCategory(error: Exception): AuthNetworkCategory = when(error) {
    is UnknownHostException -> AuthNetworkCategory.DNS
    is SSLException -> AuthNetworkCategory.TLS
    is ConnectException -> AuthNetworkCategory.CONNECT
    is InterruptedIOException -> AuthNetworkCategory.TIMEOUT
    is IOException -> AuthNetworkCategory.IO
    else -> AuthNetworkCategory.OTHER
}
/** Scoped ngrok compatibility from the reference; never redirects or retries an auth request. */
internal class AuthGatewayHeadersInterceptor(private val origin: HttpUrl) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val u = request.url
        if (u.scheme != origin.scheme || u.host != origin.host || u.port != origin.port || !u.encodedPath.startsWith(origin.encodedPath))
            return chain.proceed(request)
        val builder = request.newBuilder()
        if (request.header("Accept") == null) builder.header("Accept", "application/json")
        if (u.host.endsWith(".ngrok-free.dev") || u.host.endsWith(".ngrok-free.app")) builder.header("ngrok-skip-browser-warning", "1")
        return chain.proceed(builder.build())
    }
}
/** Inert unless the debug user explicitly arms the finite metadata recorder. */
internal class SafeAuthTraceInterceptor(private val record: (AuthAttemptEvent) -> Unit = AuthAttemptTrace::record) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val operation = when(chain.request().url.encodedPath.substringAfterLast("/api/v1/", "")) {
            "auth/register" -> AuthTraceOperation.REGISTER
            "auth/login" -> AuthTraceOperation.LOGIN
            "auth/email-verification/request" -> AuthTraceOperation.OTP_REQUEST
            "auth/email-verification/verify" -> AuthTraceOperation.OTP_VERIFY
            else -> return chain.proceed(chain.request())
        }
        record(AuthAttemptEvent(operation, AuthTraceStep.REQUEST_STARTED))
        val response = try { chain.proceed(chain.request()) }
        catch (e: IOException) { record(AuthAttemptEvent(operation, AuthTraceStep.NETWORK_FAILURE, network = authNetworkCategory(e))); throw e }
        val format = when {
            response.header("Content-Type").orEmpty().contains("json", true) -> AuthResponseFormat.JSON
            response.header("Content-Type").orEmpty().contains("html", true) -> AuthResponseFormat.HTML
            else -> AuthResponseFormat.OTHER
        }
        record(AuthAttemptEvent(operation, AuthTraceStep.HTTP_RESPONSE, response.code, format = format))
        val body = response.body ?: return response
        val source = object : ForwardingSource(body.source()) {
            override fun read(sink: okio.Buffer, byteCount: Long): Long = try { super.read(sink, byteCount) }
            catch (e: IOException) { record(AuthAttemptEvent(operation, AuthTraceStep.RESPONSE_READ_FAILURE, response.code, authNetworkCategory(e))); throw e }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = source
        }).build()
    }
}
