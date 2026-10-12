package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import com.example.farmapp.data.remote.FarmApiService
import com.example.farmapp.repository.ProductRepository
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal

class CropSaveControllerTest {
    // Physical-device diagnostic values are test fixtures only, never production defaults.
    private val fruits = "c1200000-0000-4000-8000-000000000004"
    private val avocado = FarmerProductResponse("dbeca98d-ca5b-4b5e-9180-46b50ce6b27d", "Avocado",
        "Fresh and sweet avocado", "pieces", BigDecimal("100.00"), BigDecimal("5.00"),
        categoryId = null, minimumOrderQuantity = BigDecimal("1.00"))
    private val draft get() = CropEdit.from(avocado).copy(categoryId = fruits)
    private val updated get() = avocado.copy(categoryId = fruits, categoryName = "Fruits")
    private fun api(transport: Interceptor) = Retrofit.Builder()
        .baseUrl("http://10.20.33.108:8080/api/v1/")
        .client(OkHttpClient.Builder().addInterceptor(transport).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(FarmApiService::class.java)
    private fun response(request: Request, code: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    @Test fun exactAvocadoPutBodyIncludesSelectedFruitsAndRefreshesBeforeSuccess() = runBlocking {
        val calls = mutableListOf<String>()
        var body = ""
        val repository = ProductRepository(api { chain ->
            val request = chain.request()
            calls += "${request.method} ${request.url.encodedPath}"
            if (request.method == "PUT") body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            response(request, 200, if (request.method == "PUT") Gson().toJson(updated) else Gson().toJson(listOf(updated)))
        })
        val manager = CropManagement({ id, request -> repository.update(id, request); Unit }, {}, { repository.mine() })
        val save = CropSaveController(manager)
        var visible = listOf(avocado)
        assertTrue(save.begin())
        save.save(avocado, draft, { visible = it })
        assertEquals("""{"title":"Avocado","description":"Fresh and sweet avocado","unit":"pieces","unitPrice":100.00,"availableQuantity":5.00,"categoryId":"c1200000-0000-4000-8000-000000000004","minimumOrderQuantity":1.00}""", body)
        assertEquals(listOf("PUT /api/v1/products/${avocado.productId}", "GET /api/v1/products/mine"), calls)
        assertEquals(listOf(updated), visible)
        assertFalse(save.state.saving); assertFalse(save.state.error)
        assertEquals(1, save.state.completedSaves)
        assertEquals("Crop updated successfully.", save.state.message)
    }
    @Test fun completeNonNullStockSettingsAlsoSerializeExactly() {
        val request = draft.request(avocado.copy(lowStockThreshold = BigDecimal("2.50")))
        val json = com.google.gson.JsonParser.parseString(Gson().toJson(request)).asJsonObject
        assertEquals(fruits, json["categoryId"].asString)
        assertEquals("2.50", json["lowStockThreshold"].asString)
        assertEquals("1.00", json["minimumOrderQuantity"].asString)
        assertEquals(8, json.size())
    }
    @Test fun failedPutExposesHttpErrorAndAllowsRetryWithoutRemovingCurrentData() = runBlocking {
        var reject = true
        var puts = 0
        var gets = 0
        val repository = ProductRepository(api { chain ->
            val request = chain.request()
            if (request.method == "PUT") puts++ else gets++
            if (reject) response(request, 500, """{"code":"INTERNAL_ERROR","message":"Unexpected server error","requestId":"diagnostic-reference","fieldErrors":[]}""")
            else response(request, 200, if (request.method == "PUT") Gson().toJson(updated) else Gson().toJson(listOf(updated)))
        })
        val save = CropSaveController(CropManagement({ id, request -> repository.update(id, request); Unit }, {}, { repository.mine() }))
        var visible = listOf(avocado)
        assertTrue(save.begin()); save.save(avocado, draft, { visible = it })
        assertEquals(1, puts); assertEquals(0, gets); assertEquals(listOf(avocado), visible)
        assertFalse(save.state.saving); assertTrue(save.state.error); assertFalse(save.state.refreshRequired)
        assertTrue(save.state.message!!.contains("HTTP 500"))
        assertTrue(save.state.message!!.contains("Unexpected server error"))
        assertTrue(save.state.message!!.contains("diagnostic-reference"))
        assertEquals("Save changes", save.state.buttonLabel)
        reject = false
        assertTrue(save.begin()); save.save(avocado, draft, { visible = it })
        assertEquals(listOf(updated), visible); assertEquals(1, save.state.completedSaves)
    }
    @Test fun successfulPutWithFailedRefreshOffersRefreshOnlyRetry() = runBlocking {
        var updates = 0
        var failRefresh = true
        val save = CropSaveController(CropManagement({ _, _ -> updates++ }, {}, {
            if (failRefresh) throw java.io.IOException("offline")
            listOf(updated)
        }))
        var visible = listOf(avocado)
        assertTrue(save.begin()); save.save(avocado, draft, { visible = it })
        assertEquals(listOf(avocado), visible); assertEquals(0, save.state.completedSaves)
        assertTrue(save.state.refreshRequired); assertTrue(save.state.error)
        assertTrue(save.state.message!!.startsWith("Crop updated, but My Crops could not refresh."))
        assertEquals("Retry refresh", save.state.buttonLabel)
        failRefresh = false
        assertTrue(save.begin()); save.save(avocado, draft, { visible = it }, validate = { fail("Must not validate or resend update") })
        assertEquals(1, updates); assertEquals(listOf(updated), visible)
        assertEquals(1, save.state.completedSaves); assertFalse(save.state.refreshRequired)
    }
    @Test fun duplicateTapsAreRejectedBeforeCoroutineStartsAndWhileRequestWaits() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var updates = 0
        val save = CropSaveController(CropManagement({ _, _ -> updates++; started.complete(Unit); release.await() }, {}, { listOf(updated) }))
        assertTrue(save.begin()); assertFalse(save.begin())
        val job = launch { save.save(avocado, draft, {}) }
        started.await()
        assertTrue(save.state.saving); assertEquals("Saving…", save.state.buttonLabel)
        assertFalse(save.begin()); assertEquals(1, updates)
        release.complete(Unit); job.join()
        assertFalse(save.state.saving); assertEquals(1, updates)
    }
    @Test fun preRequestValidationFailureIsRetryableAndDoesNotAttemptPut() = runBlocking {
        val save = CropSaveController(CropManagement({ _, _ -> fail("PUT should not run") }, {}, { fail("Refresh should not run"); emptyList() }))
        assertTrue(save.begin())
        save.save(avocado, draft, {}, validate = { throw IllegalArgumentException("Select an available category.") })
        assertTrue(save.state.error); assertFalse(save.state.saving)
        assertTrue(save.state.message!!.contains("Select an available category."))
        assertTrue(save.begin())
    }
}
