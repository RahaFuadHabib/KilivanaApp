package com.example.farmapp.buyers

import android.content.Context
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.*
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import retrofit2.http.*
import java.security.MessageDigest

internal interface BrianAddressApi {
    @GET("users/addresses") suspend fun list(@Tag session:SessionSnapshot):Response<CanonicalApiResponse<List<BuyerAddressDto>>>
    @POST("users/addresses") suspend fun create(@Body input:BuyerAddressRequest,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<BuyerAddressDto>>
    @PUT("users/addresses/{id}") suspend fun update(@Path("id") id:Long,@Body input:BuyerAddressRequest,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<BuyerAddressDto>>
    @DELETE("users/addresses/{id}") suspend fun remove(@Path("id") id:Long,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<Unit>>
}
internal data class BrianAddressPending(val kind:String,val id:Long?,val fingerprint:String?,val beforeIds:List<Long> = emptyList())
/** Backend-owned addresses, durable unknown-write protection; no second local address book. */
internal class BrianAddressRepository(context:Context,private val sessions:SessionStore,private val expected:SessionSnapshot,
    private val api:BrianAddressApi=ApiClient.authenticatedService(BrianAddressApi::class.java)) {
    private val prefs=context.applicationContext.getSharedPreferences("brian-address-"+buyerScope(BuildConfig.API_BASE_URL,expected.auth!!.userId),Context.MODE_PRIVATE)
    private val legacy=BuyerPreferences(context,BuildConfig.API_BASE_URL,expected.auth!!.userId)
    private val mutex=Mutex()
    private fun current():SessionSnapshot = sessions.session.value.also {
        check(it.generation==expected.generation && !it.loggingOut && it.auth?.role=="BUYER" && it.auth.userId==expected.auth?.userId && it.auth.sessionOrigin==expected.auth?.sessionOrigin)
    }
    private fun fingerprint(a:BuyerAddressDto):String = fingerprint(BuyerAddressRequest(a.addressText.orEmpty(),a.label,a.latitude,a.longitude))
    private fun fingerprint(a:BuyerAddressRequest):String = MessageDigest.getInstance("SHA-256").digest(CanonicalJson.gson.toJson(a).toByteArray()).joinToString(""){"%02x".format(it)}
    private fun pending()=prefs.getString("pending",null)?.let{CanonicalJson.gson.fromJson(it,BrianAddressPending::class.java)}
    private fun pending(value:BrianAddressPending?){check(prefs.edit().putString("pending",value?.let{CanonicalJson.gson.toJson(it)}).commit())}
    private fun <T> read(r:Response<CanonicalApiResponse<T>>,write:Boolean=false):T {
        if(!r.isSuccessful){r.errorBody()?.close();if(write && r.code() in setOf(400,401,403,404,422,429)) pending(null);error("Address request was not confirmed (HTTP ${r.code()}). Refresh before trying again.")}
        return r.body()?.requireData() ?: error("Address response could not be verified.")
    }
    private suspend fun verified():List<BuyerAddressDto> {
        val rows=validateBuyerAddresses(read(api.list(current())),current().auth!!.userId.toLong())
        val p=pending()
        if(p!=null && when(p.kind){"REMOVE"->rows.none{it.id==p.id};"UPDATE"->rows.any{it.id==p.id && fingerprint(it)==p.fingerprint};else->rows.count{it.id !in p.beforeIds && fingerprint(it)==p.fingerprint}==1}) pending(null)
        return rows
    }
    suspend fun save(id:Long?,label:String,text:String,lat:Double?,lon:Double?):String?=mutex.withLock {
        try {
            check(!legacy.addressSavePending()){ "An earlier address save is unresolved. Confirm it before saving again." }
            val input=buyerAddressRequest(label,text,lat?.toString().orEmpty(),lon?.toString().orEmpty())
            val rows=verified();check(pending()==null){"An earlier address change is unresolved. Refresh; do not submit it again."}
            if(id!=null)check(rows.any{it.id==id}){"This address is no longer available."}
            pending(BrianAddressPending(if(id==null)"CREATE" else "UPDATE",id,fingerprint(input),rows.mapNotNull{it.id}))
            val row=read(if(id==null)api.create(input,current()) else api.update(id,input,current()),true)
            validateBuyerAddresses(listOf(row),current().auth!!.userId.toLong());if(id!=null)check(row.id==id)
            verified();check(pending()==null){"Saved address could not be confirmed. Refresh instead of saving again."};null
        }catch(e:CancellationException){throw e}catch(_:Exception){"Address change could not be confirmed. Refresh addresses before trying again."}
    }
    suspend fun remove(id:Long):String?=mutex.withLock {
        try {
            check(verified().any{it.id==id});check(pending()==null)
            pending(BrianAddressPending("REMOVE",id,null))
            val r=api.remove(id,current())
            if(!r.isSuccessful){r.errorBody()?.close();if(r.code() in setOf(400,401,403,404,422))pending(null);error("Not confirmed")}
            r.body()?.requireSuccess() ?: error("Not confirmed")
            verified();check(pending()==null);null
        }catch(e:CancellationException){throw e}catch(_:Exception){"Address removal could not be confirmed. Refresh before trying again."}
    }
}


