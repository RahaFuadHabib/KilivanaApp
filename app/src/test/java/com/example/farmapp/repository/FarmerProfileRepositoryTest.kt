package com.example.farmapp.repository

import com.example.farmapp.data.remote.FarmApiService
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FarmerProfileRepositoryTest {
    private val profile = """{"userId":"eb1f7b4e-8392-4bf4-8cdc-f96083411000","email":"mary@example.test","fullName":"Mary Wanjiku","phoneNumber":"+254712345678","role":"FARMER","region":"Nakuru County"}"""
    private fun repository(handler: (Request) -> Pair<Int, String>): FarmerProfileRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return FarmerProfileRepository(Retrofit.Builder().baseUrl("http://localhost/api/v1/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(FarmApiService::class.java))
    }

    @Test fun partialPhoneUpdateUsesExistingEndpointAndNeverSendsEmailOrOtherFields() = runBlocking {
        val calls = mutableListOf<String>()
        val repo = repository { request ->
            calls += "${request.method} ${request.url.encodedPath}"
            if (request.method == "PUT") {
                val json = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
                assertEquals(setOf("phoneNumber"), json.keySet())
                assertEquals("+254712345678", json["phoneNumber"].asString)
            }
            200 to profile
        }
        assertEquals("Mary Wanjiku", repo.load().fullName)
        assertEquals("+254712345678", repo.savePhone(" +254712345678 ").phoneNumber)
        assertEquals(listOf("GET /api/v1/users/me", "PUT /api/v1/users/me"), calls)
    }

    @Test fun invalidPhoneDoesNotSendRequest() = runBlocking {
        var calls = 0
        val repo = repository { calls++; 200 to profile }
        try { repo.savePhone("bad-number"); fail("Expected validation failure") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }

    @Test fun conflictNeverReturnsFakeSuccess() = runBlocking {
        val repo = repository { 409 to "{}" }
        try { repo.savePhone("+254712345678"); fail("Expected server failure") }
        catch (e: IllegalStateException) { assertEquals("This phone number is already registered.", e.message) }
    }

    @Test fun mismatchedRoleIsRejected() = runBlocking {
        val repo = repository { 200 to profile.replace("FARMER", "BUYER") }
        try { repo.load(); fail("Expected role rejection") }
        catch (_: IllegalArgumentException) { }
    }
}
