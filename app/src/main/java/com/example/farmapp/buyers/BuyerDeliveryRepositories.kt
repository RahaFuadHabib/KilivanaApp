package com.example.farmapp.buyers

import com.example.farmapp.buyer.*
import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.supplier.SupplierDeliveryStatus
import com.example.farmapp.tracking.*
import retrofit2.Response

/** Checks both sides of suspension; never retries a request or owns a pending-write marker. */
internal class BuyerDeliveryRequests(sessions: SessionStore, expected: SessionSnapshot) {
    private val guard = CheckoutSessionGuard(sessions, expected, BuyerPurchaserPolicy)
    fun current(operation: CheckoutOperation) = guard.current(operation)
    suspend fun <T> call(operation: CheckoutOperation, action: suspend (SessionSnapshot) -> Response<CanonicalApiResponse<T>>): Response<CanonicalApiResponse<T>> {
        val response = action(current(operation))
        try { current(operation) } catch (failure: Exception) { response.errorBody()?.close(); throw failure }
        return response
    }
}

/** Validation runs only on successful data. HTTP/envelope failures retain controller handling. */
private inline fun <T> Response<CanonicalApiResponse<T>>.validated(check: (T) -> Unit): Response<CanonicalApiResponse<T>> {
    if (isSuccessful && body()?.success == true) body()?.data?.let(check)
    return this
}

internal class BuyerDeliveryAddressRepository(private val api: BuyerAddressApi, sessions: SessionStore, expected: SessionSnapshot) :
    DeliveryAddressRepository<BuyerAddressDto, BuyerAddressRequest> {
    private val requests = BuyerDeliveryRequests(sessions, expected)
    private fun validate(rows: List<BuyerAddressDto>) = validateBuyerAddresses(rows, requests.current(CheckoutOperation.READ_ADDRESSES).auth!!.userId.toLong())
    override suspend fun list() = requests.call(CheckoutOperation.READ_ADDRESSES) { api.list(it) }.validated { validate(it) }
    override suspend fun create(input: BuyerAddressRequest): Response<CanonicalApiResponse<BuyerAddressDto>> {
        buyerAddressRequest(input.label.orEmpty(), input.addressText, input.latitude?.toString().orEmpty(), input.longitude?.toString().orEmpty())
        return requests.call(CheckoutOperation.MANAGE_ADDRESSES) { api.create(input, it) }.validated { validate(listOf(it)) }
    }
}

internal class BuyerManagedAddressRepository(private val api: BrianAddressApi, sessions: SessionStore, expected: SessionSnapshot) :
    ManagedDeliveryAddressRepository<BuyerAddressDto, BuyerAddressRequest> {
    private val requests = BuyerDeliveryRequests(sessions, expected)
    private fun validate(row: BuyerAddressDto) = validateBuyerAddresses(listOf(row), requests.current(CheckoutOperation.READ_ADDRESSES).auth!!.userId.toLong())
    private fun input(value: BuyerAddressRequest) {
        buyerAddressRequest(value.label.orEmpty(), value.addressText, value.latitude?.toString().orEmpty(), value.longitude?.toString().orEmpty())
    }
    override suspend fun list() = requests.call(CheckoutOperation.READ_ADDRESSES) { api.list(it) }.validated {
        validateBuyerAddresses(it, requests.current(CheckoutOperation.READ_ADDRESSES).auth!!.userId.toLong())
    }
    override suspend fun create(input: BuyerAddressRequest): Response<CanonicalApiResponse<BuyerAddressDto>> {
        input(input)
        return requests.call(CheckoutOperation.MANAGE_ADDRESSES) { api.create(input, it) }.validated { validate(it) }
    }
    override suspend fun update(addressId: Long, input: BuyerAddressRequest): Response<CanonicalApiResponse<BuyerAddressDto>> {
        require(addressId > 0); input(input)
        return requests.call(CheckoutOperation.MANAGE_ADDRESSES) { api.update(addressId, input, it) }.validated { validate(it); check(it.id == addressId) }
    }
    override suspend fun remove(addressId: Long): Response<CanonicalApiResponse<Unit>> {
        require(addressId > 0)
        return requests.call(CheckoutOperation.MANAGE_ADDRESSES) { api.remove(addressId, it) }
    }
}

internal class BuyerPickupRepository(private val api: BuyerPickupApi, sessions: SessionStore, expected: SessionSnapshot) :
    PickupStationRepository<BuyerPickupStation, BuyerSavedPickup, BuyerSavePickupRequest> {
    private val requests = BuyerDeliveryRequests(sessions, expected)
    private fun stations(rows: List<BuyerPickupStation>) {
        rows.forEach(::checkedPickup); check(rows.map { it.id }.distinct().size == rows.size)
    }
    private fun saved(rows: List<BuyerSavedPickup>) = checkedSavedPickups(rows, requests.current(CheckoutOperation.READ_PICKUP_STATIONS).auth!!.userId.toLong())
    override suspend fun availability() = requests.call(CheckoutOperation.READ_PICKUP_STATIONS) { api.availability(it) }
    override suspend fun available() = requests.call(CheckoutOperation.READ_PICKUP_STATIONS) { api.available(it) }.validated { stations(it) }
    override suspend fun region(region: String): Response<CanonicalApiResponse<List<BuyerPickupStation>>> {
        require(region.isNotBlank())
        return requests.call(CheckoutOperation.READ_PICKUP_STATIONS) { api.region(region, it) }.validated { rows -> stations(rows); check(rows.all { it.region.equals(region, ignoreCase = true) }) }
    }
    override suspend fun detail(platformStationId: Long): Response<CanonicalApiResponse<BuyerPickupStation>> {
        require(platformStationId > 0)
        return requests.call(CheckoutOperation.READ_PICKUP_STATIONS) { api.detail(platformStationId, it) }.validated { checkedPickup(it); check(it.id == platformStationId) }
    }
    override suspend fun saved() = requests.call(CheckoutOperation.READ_PICKUP_STATIONS) { api.saved(it) }.validated { saved(it) }
    override suspend fun save(input: BuyerSavePickupRequest): Response<CanonicalApiResponse<BuyerSavedPickup>> {
        require(input.pickupStationId > 0)
        return requests.call(CheckoutOperation.MANAGE_PICKUP_STATIONS) { api.save(input, it) }.validated { saved(listOf(it)); check(it.pickupStationId == input.pickupStationId) }
    }
    override suspend fun remove(userStationId: Long): Response<CanonicalApiResponse<Unit>> {
        require(userStationId > 0)
        return requests.call(CheckoutOperation.MANAGE_PICKUP_STATIONS) { api.remove(userStationId, it) }
    }
    override suspend fun setDefault(userStationId: Long): Response<CanonicalApiResponse<BuyerSavedPickup>> {
        require(userStationId > 0)
        return requests.call(CheckoutOperation.MANAGE_PICKUP_STATIONS) { api.setDefault(userStationId, it) }.validated { saved(listOf(it)); check(it.id == userStationId && it.isDefault == true) }
    }
}

/** Available for the existing nearest endpoint; does not add location permissions or a UI flow. */
internal class BuyerNearbyPickupRepository(private val api: BuyerPickupLocationApi, sessions: SessionStore, expected: SessionSnapshot) : NearbyPickupRepository<BuyerNearestPickupStation> {
    private val requests = BuyerDeliveryRequests(sessions, expected)
    override suspend fun nearest(latitude: Double, longitude: Double, radiusKm: Double, limit: Int): Response<CanonicalApiResponse<List<BuyerNearestPickupStation>>> {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        require(radiusKm.isFinite() && radiusKm > 0 && limit > 0)
        return requests.call(CheckoutOperation.READ_PICKUP_STATIONS) { api.nearest(latitude, longitude, radiusKm, limit, it) }.validated { rows ->
            rows.forEach { row ->
                checkedPickup(BuyerPickupStation(row.id, row.name, row.address, row.latitude, row.longitude, row.region, row.isAvailable))
                check(row.distanceKm == null || row.distanceKm.isFinite() && row.distanceKm >= 0)
            }
            check(rows.map { it.id }.distinct().size == rows.size)
        }
    }
}

/** Bound to one owned order. No new endpoint or Farmer permission is implied. */
internal class BuyerDeliveryTrackingRepository(private val api: DeliveryTrackingApi, sessions: SessionStore, expected: SessionSnapshot,
    private val orderId: Long, private val buyerId: Long) : OrderLogisticsRepository<DeliveryJobDto>,
    DeliveryTrackingRepository<DeliveryJobDto, DeliveryEventDto, DeliveryLocationDto> {
    private val requests = BuyerDeliveryRequests(sessions, expected)
    private fun ownOrder(id: Long) {
        check(orderId > 0 && id == orderId && requests.current(CheckoutOperation.READ_LOGISTICS).auth!!.userId.toLong() == buyerId)
    }
    private fun job(job: DeliveryJobDto) { ownOrder(job.orderId ?: 0); validateDeliveryJob(job, orderId) }
    override suspend fun jobs(orderId: Long): Response<CanonicalApiResponse<List<DeliveryJobDto>>> {
        ownOrder(orderId)
        return requests.call(CheckoutOperation.READ_LOGISTICS) { api.jobs(orderId, it) }.validated { rows ->
            rows.forEach { validateDeliveryJob(it, orderId) }; check(rows.map { it.id }.distinct().size == rows.size)
        }
    }
    override suspend fun events(job: DeliveryJobDto): Response<CanonicalApiResponse<List<DeliveryEventDto>>> {
        job(job)
        return requests.call(CheckoutOperation.READ_LOGISTICS) { api.events(job.id!!, it) }.validated { rows ->
            check(rows.all { it.logisticsJobId == job.id && SupplierDeliveryStatus.parse(it.status) != null })
        }
    }
    override suspend fun location(job: DeliveryJobDto): Response<CanonicalApiResponse<DeliveryLocationDto>> {
        job(job)
        return requests.call(CheckoutOperation.READ_LOGISTICS) { api.location(job.id!!, it) }.validated { validateDeliveryLocation(it, job) }
    }
}
