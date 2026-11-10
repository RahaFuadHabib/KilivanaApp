package com.example.farmapp.checkout

import com.example.farmapp.network.canonical.CanonicalApiResponse
import retrofit2.Response

/** Transport boundaries only. Controllers retain write reservations and reconciliation. */
internal interface DeliveryAddressRepository<Address, Request> {
    suspend fun list(): Response<CanonicalApiResponse<List<Address>>>
    suspend fun create(input: Request): Response<CanonicalApiResponse<Address>>
}

internal interface ManagedDeliveryAddressRepository<Address, Request> : DeliveryAddressRepository<Address, Request> {
    suspend fun update(addressId: Long, input: Request): Response<CanonicalApiResponse<Address>>
    suspend fun remove(addressId: Long): Response<CanonicalApiResponse<Unit>>
}

/** Platform IDs select a delivery station; association IDs manage a user's saved stations. */
internal interface PickupStationRepository<Station, SavedStation, SaveRequest> {
    suspend fun availability(): Response<CanonicalApiResponse<Boolean>>
    suspend fun available(): Response<CanonicalApiResponse<List<Station>>>
    suspend fun region(region: String): Response<CanonicalApiResponse<List<Station>>>
    suspend fun detail(platformStationId: Long): Response<CanonicalApiResponse<Station>>
    suspend fun saved(): Response<CanonicalApiResponse<List<SavedStation>>>
    suspend fun save(input: SaveRequest): Response<CanonicalApiResponse<SavedStation>>
    suspend fun remove(userStationId: Long): Response<CanonicalApiResponse<Unit>>
    suspend fun setDefault(userStationId: Long): Response<CanonicalApiResponse<SavedStation>>
}

internal interface NearbyPickupRepository<Station> {
    suspend fun nearest(latitude: Double, longitude: Double, radiusKm: Double = 10.0, limit: Int = 20): Response<CanonicalApiResponse<List<Station>>>
}

internal interface OrderLogisticsRepository<Job> {
    suspend fun jobs(orderId: Long): Response<CanonicalApiResponse<List<Job>>>
}

/** The complete job is supplied so order/job/driver association can be validated together. */
internal interface DeliveryTrackingRepository<Job, Event, Location> {
    suspend fun events(job: Job): Response<CanonicalApiResponse<List<Event>>>
    suspend fun location(job: Job): Response<CanonicalApiResponse<Location>>
}
