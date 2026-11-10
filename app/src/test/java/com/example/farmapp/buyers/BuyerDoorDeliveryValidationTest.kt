package com.example.farmapp.buyers

import com.example.farmapp.buyer.BuyerAddressDto
import org.junit.Assert.*
import org.junit.Test

class BuyerDoorDeliveryValidationTest {
    @Test fun existingAddressWithoutCoordinatesCanBeQuoted() {
        assertNull(buyerDoorDeliveryIssue(BuyerAddressDto(id=8,userId=42)))
        assertNull(buyerDoorDeliveryIssue(BuyerAddressDto(id=8,latitude=1.0)))
        assertNull(buyerDoorDeliveryIssue(BuyerAddressDto(id=8,longitude=1.0)))
    }
    @Test fun validCoordinatesIncludingZeroAreAccepted() {
        assertNull(buyerDoorDeliveryIssue(BuyerAddressDto(id=8,latitude=0.0,longitude=0.0)))
    }
    @Test fun missingOrInvalidSavedAddressIdIsRejected() {
        assertNotNull(buyerDoorDeliveryIssue(null))
        assertNotNull(buyerDoorDeliveryIssue(BuyerAddressDto(id=0)))
        assertNotNull(buyerDoorDeliveryIssue(BuyerAddressDto(id=-1)))
    }
}
