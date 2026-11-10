package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyer.*
import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.supplier.SupplierDeliveryStatus
import com.example.farmapp.tracking.*
import retrofit2.Response

internal class FarmerAddressRepository(private val api: BuyerAddressApi, sessions: SessionStore, expected: SessionSnapshot) :
    DeliveryAddressRepository<BuyerAddressDto, BuyerAddressRequest> {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    override suspend fun list() = requests.call(CheckoutOperation.READ_ADDRESSES, { api.list(it) }) {
        validateBuyerAddresses(it, requests.purchaserId())
    }
    override suspend fun create(input: BuyerAddressRequest): Response<CanonicalApiResponse<BuyerAddressDto>> = requests.blocked()
}

/** A saved association's id is never used as a checkout pickupStationId. */
internal class FarmerPickupRepository(private val api: BuyerPickupApi, sessions: SessionStore, expected: SessionSnapshot) :
    PickupStationRepository<BuyerPickupStation, BuyerSavedPickup, BuyerSavePickupRequest> {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    private fun stations(rows: List<BuyerPickupStation>) {
        rows.forEach(::checkedPickup); check(rows.map { it.id }.distinct().size == rows.size)
    }
    override suspend fun availability() = requests.call(CheckoutOperation.READ_PICKUP_STATIONS, { api.availability(it) })
    override suspend fun available() = requests.call(CheckoutOperation.READ_PICKUP_STATIONS, { api.available(it) }, ::stations)
    override suspend fun region(region: String) = requests.call(CheckoutOperation.READ_PICKUP_STATIONS, {
        require(region.isNotBlank()); api.region(region, it)
    }) { rows -> stations(rows); check(rows.all { it.region.equals(region, true) }) }
    override suspend fun detail(platformStationId: Long) = requests.call(CheckoutOperation.READ_PICKUP_STATIONS, {
        require(platformStationId > 0); api.detail(platformStationId, it)
    }) { checkedPickup(it); check(it.id == platformStationId) }
    override suspend fun saved() = requests.call(CheckoutOperation.READ_PICKUP_STATIONS, { api.saved(it) }) {
        checkedSavedPickups(it, requests.purchaserId())
    }
    override suspend fun save(input: BuyerSavePickupRequest): Response<CanonicalApiResponse<BuyerSavedPickup>> = requests.blocked()
    override suspend fun remove(userStationId: Long): Response<CanonicalApiResponse<Unit>> = requests.blocked()
    override suspend fun setDefault(userStationId: Long): Response<CanonicalApiResponse<BuyerSavedPickup>> = requests.blocked()
}

/** Own-order readback precedes every tracking read. Never trust a seller order or caller job. */
internal class FarmerTrackingRepository(private val api: DeliveryTrackingApi,
    private val orders: FarmerCheckoutRepository, sessions: SessionStore, expected: SessionSnapshot) :
    OrderLogisticsRepository<DeliveryJobDto>, DeliveryTrackingRepository<DeliveryJobDto, DeliveryEventDto, DeliveryLocationDto> {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    override suspend fun jobs(orderId: Long): Response<CanonicalApiResponse<List<DeliveryJobDto>>> {
        orders.readOrder(orderId).requirePurchaseData()
        return requests.call(CheckoutOperation.READ_LOGISTICS, { api.jobs(orderId, it) }) { rows ->
            rows.forEach { validateDeliveryJob(it, orderId) }; check(rows.map { it.id }.distinct().size == rows.size)
        }
    }
    private suspend fun checkedJob(job: DeliveryJobDto) {
        require(job.orderId != null && job.id != null)
        val fresh = jobs(job.orderId).requirePurchaseData().singleOrNull { it.id == job.id }
            ?: error("This delivery is not associated with the purchase.")
        check(fresh.driverId == job.driverId && fresh.orderId == job.orderId)
    }
    override suspend fun events(job: DeliveryJobDto): Response<CanonicalApiResponse<List<DeliveryEventDto>>> {
        checkedJob(job)
        return requests.call(CheckoutOperation.READ_LOGISTICS, { api.events(job.id!!, it) }) { rows ->
            check(rows.all { it.logisticsJobId == job.id && SupplierDeliveryStatus.parse(it.status) != null })
        }
    }
    override suspend fun location(job: DeliveryJobDto): Response<CanonicalApiResponse<DeliveryLocationDto>> {
        checkedJob(job)
        return requests.call(CheckoutOperation.READ_LOGISTICS, { api.location(job.id!!, it) }) { validateDeliveryLocation(it, job) }
    }
}
