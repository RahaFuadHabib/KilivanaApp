package com.example.farmapp.supplier

/** Source-verified enum spellings for future authorized projections. No order/driver API is enabled. */
enum class SupplierOrderStatus(val wire: String) {
    PLACED("placed"), CONFIRMED("confirmed"), IN_TRANSIT("in_transit"), DELIVERED("delivered"),
    COMPLETED("completed"), DISPUTED("disputed"), CANCELLED("cancelled");
    companion object { fun parse(wire: String?) = entries.firstOrNull { it.wire == wire } }
}
enum class SupplierDeliveryStatus(val wire: String) {
    PENDING_ASSIGNMENT("pending_assignment"), ASSIGNED("assigned"), ACCEPTED("accepted"),
    EN_ROUTE_TO_PICKUP("en_route_to_pickup"), ARRIVED_AT_PICKUP("arrived_at_pickup"), PICKED_UP("picked_up"),
    IN_TRANSIT("in_transit"), ARRIVED_AT_DESTINATION("arrived_at_destination"), DELIVERED("delivered"),
    CANCELLED("cancelled"), FAILED("failed");
    companion object { fun parse(wire: String?) = entries.firstOrNull { it.wire == wire } }
}
