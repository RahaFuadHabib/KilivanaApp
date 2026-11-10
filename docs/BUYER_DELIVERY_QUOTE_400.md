# Buyer delivery quote HTTP 400 investigation — 10 November 2026

## Confirmed invalid state

Read-only device diagnostics using the genuine Buyer session issued only `GET /api/v1/users/addresses`. It returned HTTP 200. The address referenced by the saved checkout request exists and belongs to the authenticated Buyer. Both `latitudePresent=false` and `longitudePresent=false` were confirmed. A separate preferences-only comparison confirmed `CURRENT_SELECTION_MATCHES_SAVED_QUOTE_ADDRESS=True`. No address text, coordinate values, identifiers, phone, tokens or credentials were emitted. Evidence is `quote-read-probe-device.log`; the probe passed 1/1.

This is a confirmed missing-address-geocoding prerequisite, not evidence of a wrong address ownership mapping or incorrect delivery-mode enum. Android previously permitted any existing address ID and did not check coordinates before requesting the quote. The inspected Java CheckoutService.createDeliveryQuote rejects a Door Delivery address when either coordinate is null, throwing `Delivery address is not geocoded/valid` around line 109. That Java source is supporting evidence, not proof of the running deployment revision.

The historical quote response is known as HTTP 400 / BAD_REQUEST from the owner's observed UI. Its original backend message and error.details are unavailable because CheckoutController.envelope keeps only the error code and closes the body. It is impossible to prove retrospectively that missing coordinates were the sole cause or to quote the actual historical validation message. No live POST was repeated to obtain that evidence. Server logs or a captured response from a separately authorized controlled reproduction would be needed for conclusive attribution of that particular response.

## Exact request and contract

`buyers/data/Contracts.kt` aliases DeliveryQuoteRequest to `checkout/CheckoutModels.kt:45`, CheckoutQuoteRequest. Gson uses the actual field names. A sanitized reconstruction of Door Delivery JSON is below. The symbolic value is not a literal transmitted identifier.

```json
{"deliveryMethod":"DOOR_DELIVERY","addressId":"<selected owned address ID, int64 on wire>"}
```

The actual address ID is serialized as a JSON integer, not a string. Null pickupLocation and pickupStationId are omitted by the existing converter. A fully literal synthetic equivalent covered by serialization tests is `{"deliveryMethod":"DOOR_DELIVERY","addressId":8}`.

The DTO fields are deliveryMethod String, addressId Long?, pickupLocation String?, pickupStationId Long?. There is no cart ID, item list, quantity, subtotal, latitude or longitude field in this request. The server resolves the authenticated processing cart and stored address. Adding coordinates to this request would invent unsupported fields and would not fix the stored address.

The current deployed `/api-docs` was retrieved successfully with HTTP 200. DeliveryQuoteRequest requires deliveryMethod. Documented methods are DOOR_DELIVERY and PICKUP_STATION. addressId is int64, stationId is named pickupStationId/int64, and pickupLocation is at most 500 characters. Android matches these fields. DeliveryQuoteResponse defines quoteId, delivery selection, subtotal, deliveryFee, total, currency and expiry. Existing strict validators and serialization tests remain unchanged. No DTO_MISMATCH was established.

## Request path and correction

- CheckoutScreen Calculate delivery calls BuyerApp.requestPreparation.
- `buyers/BuyerApp.kt:120-127` previously accepted any row with the selected ID. It now calls `buyerDoorDeliveryIssue` before constructing the confirmation/quote request.
- `buyers/BuyerCheckoutContract.kt:29` checks a positive existing address ID, both coordinates, finite values and latitude/longitude ranges. Valid zero coordinates remain accepted.
- The error is surfaced through CheckoutScreen feedback with the specific instruction to edit the address in Manage addresses or select another geocoded address. The invalid selection sends no quote/preparation/order request.
- Once a valid address is selected, the existing path remains BrianCheckoutController.prepare -> CheckoutController.prepare/refreshQuote -> BuyerCheckoutRepository.deliveryQuote -> BrianCheckoutApi.quote -> POST checkout/delivery-quote through the normal authenticated client.
- ACK recovery remains quote-only. No journal is cleared and no cart write, preparation or order is automatically repeated. The original quote expiry, totals, ownership and uncertain-order checks remain enforced.

The address book already supports editing Latitude and Longitude in `buyers/ui/screens/AddressBookScreen.kt` around lines 119-125. The actual stored address must be corrected using genuine verified coordinates, or a different complete address must be selected. Android does not fabricate coordinates or update the production record during diagnosis. No backend source change is required merely to reject an ungeocoded address. Backend geocoding may be added by its owner if that is the intended product workflow.

## Remaining unknowns and backend handoff

Endpoint is POST `/api/v1/checkout/delivery-quote` on `https://hankering-morphing-outclass.ngrok-free.dev`. Request is DOOR_DELIVERY plus the owned selected address ID. Observed response is HTTP 400, error code BAD_REQUEST. Exact message/details/field errors were not retained. The associated current address is missing both stored coordinates.

The backend developer should confirm the historical rejection using a redacted request correlation/server trace, validate the running revision and confirm whether this condition produces that BAD_REQUEST in deployment. Expected behavior is a quote for a valid owned geocoded address and a structured actionable validation code for an incomplete address. No validation bypass is proposed. An additive stable code such as ADDRESS_COORDINATES_REQUIRED would let Android distinguish the business error without displaying arbitrary backend messages.

Current server cart line/quantity and CHECKOUT_PROCESSING state were not fetched or mutated during diagnosis. Cart GET is known to create state on some versions, so it was not treated as a safe inspection endpoint. Existing ACK journal evidence confirms prior preparation acknowledgement, not current database contents. These remain UNKNOWN if a valid geocoded address is subsequently rejected. The live backend revision is not established solely by OpenAPI availability.

## Files changed

- `app/src/main/java/com/example/farmapp/buyers/BuyerApp.kt` — checks selected address prerequisites and displays the precise local validation message.
- `app/src/main/java/com/example/farmapp/buyers/BuyerCheckoutContract.kt` — pure Door Delivery address validator.
- `app/src/test/java/com/example/farmapp/buyers/BuyerDoorDeliveryValidationTest.kt` — three regression tests for missing coordinates, valid zero coordinates and invalid/nonfinite/range/missing-address input.
- `app/src/androidTest/java/com/example/farmapp/buyers/QuoteReadOnlyDiagnosticsTest.kt` — explicitly credential-dependent genuine-session read-only probe, marked with the existing RequiresAuthenticatedBuyer classification. It never refreshes tokens, sends writes or fabricates authentication. Normal independent suites continue to exclude that annotation.

No Retrofit fields, backend code, SQL, database, payment flags or transaction safeguards were modified.

## Verification and installation

Build command used process-local Android Studio JBR and explicit verified URL properties because an earlier shell had failed URL configuration validation.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-Pkilivana.apiBaseUrl=https://hankering-morphing-outclass.ngrok-free.dev/api/v1/' '-Pkilivana.debug.apiBaseUrl=https://hankering-morphing-outclass.ngrok-free.dev/api/v1/' --offline --console=plain
```

- Compilation and debug/test APK builds passed. JVM tests passed **649/649**, zero failures/errors. Evidence is `quote-validation-build.log` and Gradle XML.
- Existing CheckoutModelsTest checks literal Door Delivery serialization. CheckoutRepositoryContractTest and checkout controller tests cover transport, valid/invalid quote payloads, expiry/totals and protected recovery.
- Selected mocked device regressions passed **49/49**, log `quote-validation-device.log` ending OK (49 tests). This is not the complete instrumentation suite.
- Genuine-session GET-only diagnostic passed **1/1**, with ownership and missing-coordinate flags in `quote-read-probe-device.log`. It is separate from the 49 mocked regressions and must not be confused with a live quote test.
- Updated app and test APKs installed successfully with `adb install -r`, retaining app data and the checkout journal. Package is `com.example.farmapp.dev2231918adc4d4b9d`. MainActivity cold launch returned Status ok.
- The device regression run saved its output locally on the phone, protecting results from transient USB disconnections. Its script restores the temporary stay-awake setting afterward.
- A valid quote is reached under existing controlled mock tests. A valid live quote has **not** been verified, since the real selected address is still incomplete and no quote POST was performed during this investigation.

M-Pesa remains UNKNOWN and Farmer order activation remains disabled. No real order, quote/preparation write, STK Push, payment, production-data change, commit or push occurred. All pre-existing uncommitted work was preserved.