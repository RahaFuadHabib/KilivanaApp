package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.canonical.*
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CanonicalContractTest {
    private fun text(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/canonical/$name.json")
    ) { "Missing fixture" }.bufferedReader().use { it.readText() }

    private inline fun <reified T> decode(json: String): CanonicalApiResponse<T> =
        CanonicalJson.gson.fromJson(json, object : TypeToken<CanonicalApiResponse<T>>() {}.type)

    private fun user() = decode<CanonicalUserDto>(text("register")).requireData()
    private fun product() = decode<List<CanonicalProductDto>>(text("products")).requireData().single()

    private inline fun <reified T : Throwable> rejects(block: () -> Unit): T {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError("Expected ${T::class.java.simpleName}", failure)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    @Test fun registrationMapsLongIdentityAndOptionalAccountFields() {
        val envelope = decode<CanonicalUserDto>(text("register"))
        assertTrue(envelope.success == true)
        assertEquals("2026-10-13T10:20:31", envelope.timestamp)
        val account = envelope.requireData().toAccount()
        assertEquals("9007199254740993", account.userId)
        assertEquals("Fixture Farmer", account.fullName)
        assertEquals("+254700000001", account.phoneNumber)
        assertEquals("F-014", account.referenceCode)
        assertNull(account.username)
        assertEquals("NOT_REQUIRED", account.verificationStatus)
        assertEquals(LocalDateTime.parse("2026-10-13T10:20:30.123"), account.createdAt)
        val profile = user().toFarmerProfile()
        assertEquals(account.userId, profile.userId)
        assertEquals(account.fullName, profile.fullName)
        assertEquals(account.region, profile.region)
    }

    @Test fun loginAndRefreshEnvelopePreserveTokensAndSecondsWithoutMakingASession() {
        // Login and refresh use the same AuthTokenResponse DTO.
        val dto = decode<CanonicalAuthTokenDto>(text("login")).requireData()
        assertEquals("fixture-access", dto.accessToken)
        assertEquals("fixture-refresh", dto.refreshToken)
        assertEquals("Bearer", dto.tokenType)
        assertEquals(900L, dto.expiresIn)
        assertEquals("9007199254740993", dto.user!!.toAccount().userId)
        val noExpiry = decode<CanonicalAuthTokenDto>(
            text("login").replace("\"expiresIn\": 900", "\"expiresIn\": null")
        ).requireData()
        assertNull(noExpiry.expiresIn)
        assertFalse(dto.toString().contains("fixture-access"))
        assertFalse(decode<CanonicalAuthTokenDto>(text("login")).toString().contains("fixture-refresh"))
    }

    @Test fun requestFieldsMatchCanonicalContractsAndRedactSecrets() {
        val gson = CanonicalJson.gson
        val login = CanonicalLoginRequest("farmer@example.test", "fixture-password")
        assertEquals(setOf("email", "password"), JsonParser.parseString(gson.toJson(login)).asJsonObject.keySet())
        val register = CanonicalRegisterRequest("Farmer", "farmer@example.test", "+254700000001",
            "fixture-password", "FARMER", "farmer_1", "Kiambu")
        assertEquals(setOf("name", "email", "phone", "password", "role", "username", "region"),
            JsonParser.parseString(gson.toJson(register)).asJsonObject.keySet())
        val refresh = CanonicalRefreshRequest("fixture-refresh")
        assertEquals(setOf("refreshToken"), JsonParser.parseString(gson.toJson(refresh)).asJsonObject.keySet())
        assertFalse(login.toString().contains("fixture-password"))
        assertFalse(register.toString().contains("fixture-password"))
        assertFalse(refresh.toString().contains("fixture-refresh"))
    }

    @Test fun sellerProductsMapExactIdsMoneyReservationsAndModeration() {
        val dto = product()
        assertEquals(Long.MAX_VALUE, dto.id)
        assertEquals(BigDecimal("123456789012345.123456789"), dto.price)
        val view = dto.toFarmerProduct()
        assertEquals("9223372036854775807", view.productId)
        assertEquals("9007199254740993", view.sellerId)
        assertEquals("42", view.categoryId)
        assertEquals("FARM_PRODUCE", view.sector)
        assertEquals(BigDecimal("20"), view.stockQuantity)
        assertEquals(BigDecimal("3"), view.reservedQuantity)
        assertEquals(BigDecimal("17"), view.availableQuantity)
        assertEquals(BigDecimal("4"), view.soldQuantity)
        assertEquals(BigDecimal.ONE, view.minimumOrderQuantity)
        assertEquals(dto.price, view.unitPrice)
        assertEquals(CanonicalProductStatus.PENDING_APPROVAL, view.status)
        assertEquals("9007199254740995", view.images.single().imageId)
        assertEquals("https://images.example.test/product.png", view.images.single().url)
        assertTrue(view.images.single().isPrimary == true)
        assertNull(view.images.single().assetId)
        assertEquals(LocalDateTime.parse("2026-10-13T09:00:00"), view.createdAt)
        // Round trip remains numeric, exact and never Double.
        val roundTrip = CanonicalJson.gson.fromJson(CanonicalJson.gson.toJson(dto), CanonicalProductDto::class.java)
        assertEquals(dto.id, roundTrip.id)
        assertEquals(dto.price, roundTrip.price)
    }

    @Test fun allCanonicalProductWireStatesAreExplicitAndUnknownStatesFail() {
        listOf("active", "pending", "rejected", "suspended").zip(CanonicalProductStatus.entries).forEach { (wire, state) ->
            assertEquals(state, canonicalProductStatus(wire))
            assertEquals(state, product().copy(status = wire).toFarmerProduct().status)
        }
        listOf<String?>(null, "HIDDEN", "inactive", "ACTIVE", "pending_approval", "future").forEach { wire ->
            rejects<CanonicalContractException> { canonicalProductStatus(wire) }
        }
    }

    @Test fun categoriesMapBothSectorsWithoutFilteringInactiveData() {
        val categories = decode<List<CanonicalCategoryDto>>(text("categories")).requireData().map { it.toCategory() }
        assertEquals("42", categories[0].categoryId)
        assertEquals("FARM_PRODUCE", categories[0].sector)
        assertTrue(categories[0].active)
        assertEquals("AGRI_INPUTS", categories[1].sector)
        assertFalse(categories[1].active)
        rejects<CanonicalContractException> { CanonicalCategoryDto(1L, "Unknown", "BUYER", true).toCategory() }
        rejects<CanonicalContractException> { CanonicalCategoryDto(1L, "Unknown", "FARMER", null).toCategory() }
    }

    @Test fun genericValidationErrorAndVoidSuccessDoNotMasqueradeAsData() {
        val envelope = decode<Map<String, String>>(text("error"))
        assertEquals("Invalid email format", envelope.data!!["email"])
        assertEquals("VALIDATION_ERROR", envelope.error!!.code)
        val failure = rejects<CanonicalResponseFailure> { envelope.requireData() }
        assertEquals("VALIDATION_ERROR", failure.errorCode)
        assertFalse(failure.message!!.contains("Invalid email format"))
        val void = decode<Unit>("""{"success":true,"message":"Logout successful","data":null}""")
        void.requireSuccess()
        rejects<CanonicalContractException> { void.requireData() }
        rejects<CanonicalContractException> { decode<Unit>("{}").requireSuccess() }
        rejects<CanonicalContractException> { decode<Unit>("""{"success":null}""").requireSuccess() }
        rejects<CanonicalResponseFailure> { decode<Unit>("""{"success":false}""").requireSuccess() }
    }

    @Test fun integerBoundariesAndFractionsCannotBeTruncatedDuringDecoding() {
        listOf("9223372036854775808", "-9223372036854775809", "1.5", "\"9007199254740993\"").forEach { bad ->
            rejects<JsonParseException> {
                decode<CanonicalUserDto>(text("register").replace("9007199254740993", bad))
            }
        }
        listOf("2147483648", "-2147483649", "2.5", "\"20\"").forEach { bad ->
            rejects<JsonParseException> {
                decode<List<CanonicalProductDto>>(text("products").replace("\"stockQty\": 20", "\"stockQty\": $bad"))
            }
        }
        val exact = decode<List<CanonicalProductDto>>(text("products").replace("\"stockQty\": 20", "\"stockQty\": 20.0"))
        assertEquals(20, exact.requireData().single().stockQty)
    }

    @Test fun exactQuantityBoundaryRejectsFractionOverflowAndNegativeWithoutRounding() {
        assertEquals(0, canonicalStockQuantity(BigDecimal.ZERO))
        assertEquals(12, canonicalStockQuantity(BigDecimal("12.000")))
        assertEquals(Int.MAX_VALUE, canonicalStockQuantity(BigDecimal("2147483647")))
        listOf("0.1", "12.5", "2147483648", "-1").forEach {
            rejects<CanonicalContractException> { canonicalStockQuantity(BigDecimal(it)) }
        }
    }

    @Test fun invalidRequiredFieldsAndInconsistentReservationsFailClosed() {
        rejects<CanonicalContractException> { CanonicalUserDto().toAccount() }
        rejects<CanonicalContractException> { user().copy(id = 0).toAccount() }
        rejects<CanonicalContractException> { user().copy(role = "UNKNOWN").toAccount() }
        rejects<CanonicalContractException> { user().copy(role = "BUYER").toFarmerProfile() }
        rejects<CanonicalContractException> { user().copy(status = "DISABLED").toAccount() }
        rejects<CanonicalContractException> { user().copy(verificationStatus = "UNVERIFIED").toAccount() }
        rejects<CanonicalContractException> { product().copy(stockQty = null).toFarmerProduct() }
        rejects<CanonicalContractException> { product().copy(reservedQty = 21).toFarmerProduct() }
        rejects<CanonicalContractException> { product().copy(soldQty = -1).toFarmerProduct() }
        rejects<CanonicalContractException> { product().copy(minimumOrderQty = 0).toFarmerProduct() }
        rejects<CanonicalContractException> { product().copy(price = BigDecimal.ZERO).toFarmerProduct() }
        rejects<CanonicalContractException> { product().copy(images = null).toFarmerProduct() }
        rejects<CanonicalContractException> { CanonicalProductImageDto(id = 1).toImageView() }
    }

    @Test fun offsetFreeTimestampsStayLocalAndNoRefreshDeadlineIsInvented() {
        assertNull(user().copy(createdAt = null).toAccount().createdAt)
        rejects<CanonicalContractException> { user().copy(createdAt = "2026-10-13T10:20:30Z").toAccount() }
        rejects<CanonicalContractException> { product().copy(updatedAt = "not-a-date").toFarmerProduct() }
        // Canonical auth DTO has no field that could satisfy active UUID AuthResponse refresh expiry.
        assertFalse(CanonicalAuthTokenDto::class.java.declaredFields.any { it.name == "refreshTokenExpiresAt" })
    }
}
