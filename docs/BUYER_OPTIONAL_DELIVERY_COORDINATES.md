# Optional Buyer delivery coordinates — 10 November 2026

## Current correction

The obsolete message came from `buyerDoorDeliveryIssue` in `app/src/main/java/com/example/farmapp/buyers/BuyerCheckoutContract.kt`. That helper required non-null latitude/longitude before BuyerApp.requestPreparation could proceed. The mandatory-coordinate branch has been removed. The helper continues to reject a missing address or nonpositive saved ID. Existing loaded-address selection, authenticated session, ownership, delivery-method, quote/total/expiry and uncertain-write safeguards remain unchanged.

Android neither invents coordinates nor substitutes zero. Address creation/update DTOs already permit missing coordinates. Door Delivery still sends the existing numeric saved address ID, not coordinate fields.

## Deployed contract verification

The current `/api-docs` returned HTTP 200. The actual endpoint bindings were checked, not only component names.

| Operation | Request schema | Required fields | Coordinate status |
|---|---|---|---|
| POST `/api/v1/users/addresses` | AddressRequest | addressText | latitude/longitude optional |
| PUT `/api/v1/users/addresses/{addressId}` | AddressRequest | addressText | latitude/longitude optional |
| POST `/api/v1/checkout/delivery-quote` | DeliveryQuoteRequest | deliveryMethod, with documented addressId for Door Delivery | No coordinate fields |
| POST `/api/v1/checkout/order` | CheckoutOrderRequest | quoteId | No coordinate fields |
| POST `/api/v1/orders` | OrderRequest | buyerId, addressId, items | No coordinate fields |

Android BuyerAddressRequest has addressText plus optional label/latitude/longitude. Ownership is principal-based and is not supplied as an arbitrary userId. Existing field/range validation for coordinates actually supplied during address editing remains. Both coordinates can be omitted. CheckoutQuoteRequest sends DOOR_DELIVERY plus addressId, or the distinct documented pickup fields. QuoteOrderRequest sends quoteId and optional phone. No request/response DTO change or invented field was needed.

OpenAPI optionality does not by itself prove every deployed service business rule. This correction also follows the owner's explicit notice that the backend developer removed that rule. The older local Java coordinate check and earlier investigation describe historical behavior and were not used to reinstate the removed requirement. No backend source was changed.

## Exact Kotlin files changed

1. `app/src/main/java/com/example/farmapp/buyers/BuyerCheckoutContract.kt` — removed coordinate presence/range gating from the saved-address checkout prerequisite.
2. `app/src/test/java/com/example/farmapp/buyers/BuyerDoorDeliveryValidationTest.kt` — coordinate-free and partial optional-coordinate saved addresses are accepted. Missing/nonpositive address IDs remain rejected. Explicit valid zero metadata remains accepted without generating any values.
3. `app/src/androidTest/java/com/example/farmapp/buyers/BuyerOptionalCoordinatesTest.kt` — production CheckoutScreen with a saved null-coordinate address, synthetic phone input, enabled Calculate delivery and an asserted selected-address callback. The production guard must return no coordinate error, and order submission is prohibited in the fixture.

BuyerApp still selects from the Buyer's loaded address rows before calling the guard. Backend ownership is not bypassed. Historical checkout journals, Farmer flags and M-Pesa configuration remain unchanged.

## Tests and installation

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-Pkilivana.apiBaseUrl=https://hankering-morphing-outclass.ngrok-free.dev/api/v1/' '-Pkilivana.debug.apiBaseUrl=https://hankering-morphing-outclass.ngrok-free.dev/api/v1/' --offline --console=plain
```

Compilation and APK builds passed. JVM result is **649/649 passed**, zero failures/errors, in `optional-coordinate-build.log` and Gradle XML.

The first 50-case device run failed 16 cases, all reporting No Compose hierarchies. Its output is retained as `optional-coordinate-device-first.log`. No assertion or production workaround was added for those failures. The precise host/lifecycle cause is unproven. After focus/wake checks, the new test passed **1/1** unchanged in isolation (`optional-coordinate-targeted.log`). The complete selected 50-case rerun then passed **50/50**, zero failures, in `optional-coordinate-device.log`. This is a selected regression suite, not every project instrumentation test.

Both updated debug APKs installed successfully with `adb install -r`, retaining app data. Target package is `com.example.farmapp.dev2231918adc4d4b9d` with the corresponding `.test` runner. MainActivity was relaunched after testing. The device-side runner restores temporary stay-awake configuration to 0.

Controlled device verification confirms Calculate delivery is enabled and calls its request handler for an existing address with absent coordinates. Mocked regression tests retain valid quote behavior and protected checkout recovery. No live quote POST, order or payment was submitted. Therefore no new backend coordinate-free rejection was observed or fabricated. If the owner's subsequent live quote still fails, its exact sanitized response is needed to distinguish remaining deployed business rules from this now-removed Android gate. The old generic BAD_REQUEST alone does not identify a new failure.

No backend/database/configuration changes, journal clearing, commit or push occurred. All existing uncommitted work was preserved. Farmer order activation and UNKNOWN M-Pesa safeguards remain disabled.