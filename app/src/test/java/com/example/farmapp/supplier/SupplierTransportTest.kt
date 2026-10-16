package com.example.farmapp.supplier

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class SupplierTransportTest {
    private fun api(client: OkHttpClient) = Retrofit.Builder().baseUrl(SUPPLIER_TEST_ORIGIN).client(client)
        .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build().create(SupplierApi::class.java)

    @Test fun supplierRoutesBodiesMultipartAndSessionTagsMatchSourceContracts() = runBlocking {
        val store = supplierStore(); val session = store.session.value
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor(AuthTokenInterceptor(SUPPLIER_TEST_ORIGIN.toHttpUrl(), store, RefreshCoordinator(StubAuthService(), store)))
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic fixture")
                    .body("""{"success":true,"data":null}""".toResponseBody("application/json".toMediaType())).build()
            }.build()
        val transport = api(client)
        transport.me(session); transport.profile(42, session); transport.categories(session); transport.listings(42, session)
        transport.listing(7, session)
        val request = supplyDraft().request(StubSupplierApi().categoryValues)
        transport.create(request, session); transport.update(7, request, session); transport.images(7, session)
        transport.upload(7, MultipartBody.Part.createFormData("image", "supply.jpg", supplyPhoto().bytes.toRequestBody("image/jpeg".toMediaType())), 0, true, session)
        assertEquals(listOf("auth/me", "profiles/suppliers/42", "categories/type/SUPPLIER", "products/seller/42", "products/7", "products", "products/7", "products/7/images", "products/7/images"),
            requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        assertEquals(listOf("GET", "GET", "GET", "GET", "GET", "POST", "PUT", "GET", "POST"), requests.map { it.method })
        requests.forEach { assertSame(session, it.tag(SessionSnapshot::class.java)); assertEquals("Bearer access-old", it.header("Authorization")) }
        val json = com.google.gson.JsonParser.parseString(Buffer().also { requests[5].body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("name", "description", "unit", "price", "stockQty", "minimumOrderQty", "categoryId"), json.keySet())
        val upload = requests.last()
        assertEquals("0", upload.url.queryParameter("sortOrder")); assertEquals("true", upload.url.queryParameter("isPrimary"))
        assertTrue((upload.body as MultipartBody).parts.single().headers!!["Content-Disposition"]!!.contains("name=\"image\""))
        assertFalse(client.followRedirects)
    }
    @Test fun staleSupplierWriteIsRejectedBeforeNewAccountsTokenCanBeAttached() = runBlocking {
        val store = supplierStore(); val old = store.session.value
        store.clear(store.beginLogout()!!); store.establish(store.session.value, supplierAuth("99"), false)
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(SUPPLIER_TEST_ORIGIN.toHttpUrl(), store, RefreshCoordinator(StubAuthService(), store)))
            .addInterceptor { calls++; error("No stale transport call allowed") }.build()
        try { api(client).create(supplyDraft().request(StubSupplierApi().categoryValues), old); fail() }
        catch (_: IOException) { }
        assertEquals(0, calls)
    }

    @Test fun roleChangeDuringRefreshAlsoRejectsQueuedSupplierWrite() = runBlocking {
        val store = supplierStore(); val old = store.session.value
        store.rotate(old, supplierAuth().copy(role = "FARMER", accessToken = "rotated-fixture"))
        assertEquals(old.generation, store.session.value.generation)
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(SUPPLIER_TEST_ORIGIN.toHttpUrl(), store, RefreshCoordinator(StubAuthService(), store)))
            .addInterceptor { calls++; error("No stale-role request permitted") }.build()
        try { api(client).create(supplyDraft().request(StubSupplierApi().categoryValues), old); fail() }
        catch (_: IOException) { }
        assertEquals(0, calls)
    }
}
