package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.canonical.CanonicalLoginRequest
import com.example.farmapp.data.model.canonical.CanonicalAuthTokenDto
import com.example.farmapp.repository.AuthFailure
import com.google.gson.JsonParseException
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ForwardingSource
import okio.buffer
import retrofit2.Response
import retrofit2.http.*
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.UUID

enum class AuthStage { OTP_REQUEST, OTP_VERIFY, REGISTRATION_REQUEST, LOGIN_REQUEST, RESPONSE_READ, JSON_DECODE, TOKEN_VALIDATION, ROLE_VALIDATION, FARMER_PROFILE, SUPPLIER_PROFILE, SESSION_RESTORE, SESSION_SAVE, SESSION_CHANGED }
enum class AuthFailureKind { NETWORK, TIMEOUT, HTTP, MALFORMED, CONTRACT, STORAGE }
data class AuthDiagnostic(val stage: AuthStage, val kind: AuthFailureKind, val httpStatus: Int? = null) {
    val summary get() = "AUTH_${stage.name}_${kind.name}" + (httpStatus?.let { " · HTTP $it" } ?: "")
}
class AuthTransportFailure(val diagnostic: AuthDiagnostic) : IOException(diagnostic.summary)
/** Only fixed enums and HTTP status are retained. No URL, exception text, identity, body or headers. */
class AuthLoginDiagnosticInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        if (!chain.request().url.encodedPath.endsWith("/auth/login")) return chain.proceed(chain.request())
        val response = try { chain.proceed(chain.request()) }
        catch (e: IOException) { throw AuthTransportFailure(AuthDiagnostic(AuthStage.LOGIN_REQUEST,
            if (e is SocketTimeoutException) AuthFailureKind.TIMEOUT else AuthFailureKind.NETWORK)) }
        val original = response.body ?: return response
        val source = object : ForwardingSource(original.source()) {
            override fun read(sink: okio.Buffer, byteCount: Long): Long = try { super.read(sink, byteCount) }
            catch (e: IOException) { throw AuthTransportFailure(AuthDiagnostic(AuthStage.RESPONSE_READ,
                if (e is SocketTimeoutException) AuthFailureKind.TIMEOUT else AuthFailureKind.NETWORK, response.code)) }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = original.contentType()
            override fun contentLength() = original.contentLength()
            override fun source() = source
        }).build()
    }
}
interface CanonicalLoginTransport {
    @Streaming @POST("auth/login") suspend fun login(@Body request: CanonicalLoginRequest): Response<ResponseBody>
}

internal suspend fun readCanonicalLogin(api: CanonicalLoginTransport, request: CanonicalLoginRequest): Response<CanonicalApiResponse<CanonicalAuthTokenDto>> {
    val response = try { api.login(request) }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { throw CanonicalAuthErrors.exception(e) }
    if (!response.isSuccessful) return Response.error(response.code(), response.errorBody() ?: "".toResponseBody())
    val bytes = try {
        response.body()?.use { body -> body.byteStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 256 * 1024) throw AuthFailure("Sign-in returned an unexpected response format: response exceeds the account contract limit.", AuthDiagnostic(AuthStage.RESPONSE_READ, AuthFailureKind.CONTRACT, response.code()))
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } } ?: throw AuthFailure("Sign-in returned an empty response format.", AuthDiagnostic(AuthStage.RESPONSE_READ, AuthFailureKind.CONTRACT, response.code()))
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: AuthFailure) { throw e }
    catch (e: Exception) { throw AuthFailure(if (e is SocketTimeoutException || e is AuthTransportFailure && e.diagnostic.kind == AuthFailureKind.TIMEOUT)
        "The sign-in response timed out while being read. Please try again." else "The sign-in response could not be read completely. Check your connection and try again.",
        AuthDiagnostic(AuthStage.RESPONSE_READ, if (e is SocketTimeoutException || e is AuthTransportFailure && e.diagnostic.kind == AuthFailureKind.TIMEOUT) AuthFailureKind.TIMEOUT else AuthFailureKind.NETWORK, response.code())) }
    val envelope = try {
        CanonicalJson.gson.fromJson<CanonicalApiResponse<CanonicalAuthTokenDto>>(String(bytes, Charsets.UTF_8),
            object : com.google.gson.reflect.TypeToken<CanonicalApiResponse<CanonicalAuthTokenDto>>() {}.type)
            ?: throw JsonParseException("Missing envelope")
    } catch (_: Exception) { throw AuthFailure("Sign-in returned an incomplete or unexpected response format.", AuthDiagnostic(AuthStage.JSON_DECODE, AuthFailureKind.MALFORMED, response.code())) }
    finally { bytes.fill(0) }
    return Response.success(response.code(), envelope)
}

data class EmailVerificationPending(val email: String, val challengeId: UUID? = null, val registeredUserId: Long? = null,
    val flowId: String = UUID.randomUUID().toString()) {
    override fun toString() = "EmailVerificationPending(redacted)"
}
class EmailVerificationRequired(val pending: EmailVerificationPending) : AuthFailure("Please verify your email to continue.")

internal fun requireEmailVerification(user: com.example.farmapp.data.model.canonical.CanonicalUserDto, email: String, registered: Boolean = false) {
    if (user.emailVerified == false || user.emailVerificationChallengeId != null)
        throw EmailVerificationRequired(EmailVerificationPending(email, user.emailVerificationChallengeId, user.id.takeIf { registered }))
}
