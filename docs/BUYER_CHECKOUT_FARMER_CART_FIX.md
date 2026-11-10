# Buyer checkout and Farmer server-cart fix

## Scope and evidence

The request in the attached `Pasted text.txt` authorizes Android fixes, mocked transaction tests and installation on the connected Samsung. Existing uncommitted changes were preserved. Backend Java, SQL, databases and deployment configuration were untouched in this continuation. Previous local backend implementation remains preserved.

Swagger and `/api-docs` returned HTTP 200 through direct read-only host requests on 9 November 2026. `/v3/api-docs` returned 404. The downloaded specification is preserved as `deployed-openapi-cart-checkout.json`, SHA-256 `7cf98cc2f340247c5cdb9c7be8117e6ca9b379768ea67569538fe5770ffd9e31`. This matches the prior deployed-contract evidence. The browsing tool could not open the tunnel, while PowerShell HTTPS retrieval succeeded. No application revision is exposed by this evidence.

The installed debug package is `com.example.farmapp.dev2231918adc4d4b9d`, with test package `.test`. Its build uses `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`. Both APKs were installed using `adb install -r`, retaining existing app data. MainActivity launch returned `Status: ok`. USB debugging became offline and then unauthorized during verification, and the owner reauthorized it. No app clear/uninstall was performed.

## Confirmed Android defects and corrections

### Farmer Add-to-Cart

The real Supplies route reaches `FarmerPurchasesScreen`. Its button previously invoked `FarmerPurchaseController.add`, which only wrote `local-basket` preferences. There was no Retrofit cart request. The purchaser policy additionally prohibited all cart operations. These are confirmed pre-request Android limitations.

The production model now supplies `FarmerCartRepository` through the normal authenticated Retrofit client. Adding an eligible Supplier item performs principal cart GET, one POST with numeric product ID and integer quantity, then cart metadata readback. Ownership, cart ID and ACTIVE status are validated. The controller publishes success and saves its acknowledged selection only after those checks succeed. Remove uses the documented legacy cart/product DELETE route followed by readback. Loading state serializes rapid taps and feeds the button's enabled state.

A role/API-origin/user-scoped durable pending flag is committed before each cart write. Timeout, ambiguous response, stale session or unverifiable readback retains that flag across restart. No POST/DELETE is automatically repeated. Definite HTTP rejections display status-specific messages. A 409 is surfaced without inventing an item ID or silently replacing a conflicting quantity. Old local-only selection support remains available to existing test fixtures, while the actual production model always provides the server repository.

The quantity input now initializes a decimal-form minimum such as `1.00` as a valid integer quantity. This is a defensive UI correction verified by instrumentation, not evidence that every reported physical failure used a decimal-form minimum.

### Buyer checkout

After successful `POST /checkout`, the shared Android controller previously fetched the cart and required the original ID in CHECKOUT_PROCESSING state. The inspected canonical backend `CartService.getCartByBuyer` selects ACTIVE and creates another ACTIVE cart once checkout transitions the previous one. Thus that GET cannot be used as the status lookup for the processing basket. The Android implementation could suppress the valid quote before requesting it.

The corrected flow proceeds from a successful preparation acknowledgement to the existing quote endpoint. The quote service is responsible for validating the authenticated processing basket. Existing delivery, expiry, server-total and exact owned-order checks remain enforced. Recovery from a durably acknowledged preparation also uses the quote endpoint. An uncertain preparation or order still cannot be blindly repeated.

POST Add-to-Cart may increment an existing line in the inspected Java implementation. Buyer checkout promises exact quantities in its confirmation dialog. After successful add acknowledgement, checkout now explicitly sets the selected quantity through the documented PUT cart/product endpoint before preparing checkout. That PUT has its own persisted QUANTITY_PENDING phase. The known documented duplicate-conflict path remains supported.

These defects are reproduced and fixed with controlled fixtures. The exact failing HTTP request from the owner's genuine authenticated checkout was not captured. No real account cart/order/payment was mutated to obtain a trace, so a deployment-specific failure beyond these defects remains possible.

## Verified contract alignment

All paths below use the configured base `/api/v1/` and global OpenAPI bearerAuth. OpenAPI does not establish runtime role or ownership checks by itself.

| Method/path | Request | Response | Android handling |
|---|---|---|---|
| GET `cart` | Authenticated principal, no body | ApiResponse with CartResponse | Farmer checks positive ID, purchaser ID and ACTIVE state |
| POST `cart/items` | productId int64, quantity int32 >=1 | HTTP 201 CartResponse, documented 400/401/403/404/409/422/500 | One attempt, then readback, pending guard on uncertainty |
| PUT `cart/items/{itemId}` | itemId int64, quantity int32 query | CartResponse | Documented, but no item IDs are returned by cart GET, so no invented mapping |
| DELETE `cart/items/{itemId}` | itemId int64 | ApiResponseVoid | Same mapping limitation |
| PUT `carts/{cartId}/items/{productId}` | Two int64 path IDs and int32 quantity query | CartResponse | Existing Buyer adapter, sets exact quantity after acknowledged POST |
| DELETE `carts/{cartId}/items/{productId}` | Two int64 path IDs | ApiResponseVoid | Farmer removal, followed by metadata readback |
| POST `checkout/validate` | No body | CartResponse | Validation/readback, distinct from checkout transition |
| POST `checkout` | No body | ApiResponseVoid | Existing Buyer preparation transition retained |
| POST `checkout/delivery-quote` | deliveryMethod, optional addressId/stationId int64 or pickupLocation string <=500 | quoteId, delivery selection, subtotal/fee/total numbers, KES, expiresAt | Existing strict quote validation retained |
| POST `checkout/order` | quoteId string, optional phoneNumber | ApiResponseLong | Explicit confirmation, exact owned-order readback before payment |
| GET `payments/order/{orderId}` | orderId int64 | List PaymentResponse | Authoritative payment reconciliation reads |
| GET `payments/{id}/status` | payment ID int64 | PaymentResponse | Pending never treated as paid |
| POST `payments/stk-push/buyer` | orderId int64, optional normalized phone | StkPushInitiateResponse | Existing environment/confirmation and durable uncertainty guards retained |
| POST `orders` | buyerId/addressId/items required, optional totals/fee | OrderResponse | Different contract, not substituted for quote consumption |

The deployed OpenAPI contains no `/api/v1/orders/checkout` route. Historical Angular routes are not interchangeable with `/checkout/order`. No new endpoint or speculative fallback was introduced. The deployed request schemas do not advertise durable idempotency keys, unlike the un-deployed local backend proposal. Android retains its existing conservative uncertain-order and payment reservations.

CartResponse advertises only `id`, `buyerId`, `status`, `createdAt` and `updatedAt`. It does **not** contain cart lines, item IDs, quantities or totals. This implementation therefore labels the displayed count as saved selections, shows the verified server cart ID and discloses the line-readback limitation. It does not fabricate authoritative server quantities from local preferences.

Canonical source permits Farmer purchasers through resource ownership rules, and Android sends the existing session-tagged bearer token unchanged. JVM wire tests verify header presence, expected role/session tagging and rejection of foreign/stale ownership. The owner's real JWT authorities and deployed Farmer write permission remain unverified because no authorized test-account mutation was performed.

## Files changed in this continuation

Production Kotlin

- `farmerpurchases/FarmerCartRepository.kt` added documented cart transport, ownership/readback validation, redacted status messages and durable write reservation integration.
- `farmerpurchases/FarmerPurchasePersistence.kt` added a synchronized scoped cart pending flag and a separate server-selection key that preserves historical local-only baskets.
- `farmerpurchases/FarmerPurchaseController.kt` added production server-cart orchestration, acknowledged state publication and remove/readback behavior.
- `farmerpurchases/FarmerPurchasesScreen.kt` wired the authenticated repository, real Add-to-Cart action, integer input, loading and honest cart labels.
- `checkout/FarmerPurchaserPolicy.kt` permits cart reads/writes while preserving transaction activation blocks.
- `checkout/CheckoutController.kt` sets exact quantities, removes the invalid ACTIVE-cart status lookup and recovers acknowledged checkout through quoting.

Paths above are under `app/src/main/java/com/example/farmapp/`.

Tests

- `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerCartRepositoryTest.kt` added seven tests for wire contracts, ownership, conflicts, timeout/restart, removal, rapid taps and acknowledgement publication.
- `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerPurchaseTestFixture.kt` accepts an optional cart repository for the controlled production-controller test.
- `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerPurchaseRepositoryTest.kt` retains blocked legacy-adapter assertions and updates the specifically authorized cart policy expectation.
- `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerTransactionReadinessTest.kt` updates the cart policy expectation while retaining order/payment denials.
- `app/src/test/java/com/example/farmapp/buyers/BrianCheckoutControllerTest.kt` adds the ACTIVE-after-checkout regression.
- `app/src/test/java/com/example/farmapp/checkout/CheckoutControllerTest.kt` checks the corrected exact request sequence, including quantity persistence.
- `app/src/androidTest/java/com/example/farmapp/farmerpurchases/FarmerPurchasesPresentationTest.kt` adds integer-input and production server-cart UI checks.
- `app/src/androidTest/java/com/example/farmapp/buyers/SharedBuyerJourneyTest.kt` validates the new explicit quantity phase in its synthetic API fixture.

No Supplier or Inspector production code was changed.

## Verification

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -W -n com.example.farmapp.dev2231918adc4d4b9d/com.example.farmapp.MainActivity
```

JBR is Java 21, not the incorrect JDK 19 mentioned in the request. The project compiles successfully with the installed supported Android toolchain. No JAVA_HOME machine setting was changed.

- Android compilation, test compilation and both APK builds passed.
- JVM suite passed **645/645**, zero failures/errors. This includes seven new Farmer cart tests and the new Buyer regression. Evidence is `buyer-farmer-cart-final-build.log` and Gradle XML.
- Initial compilation exposed a Kotlin trailing-lambda binding error in the new repository. Named/parenthesized action arguments corrected it before the final passing build.
- Initial JVM failures were stale expectations for the removed cart GET and newly authorized cart policy. They were updated to the precise changed contract, retaining transaction denials and request assertions.
- The first 46-case device attempt lost USB connectivity as its first case started. No pass count is claimed for that attempt. Evidence is `buyer-farmer-cart-device.log`.
- The 47-case targeted device rerun uses direct instrumentation against the retained installation, avoiding Gradle UTP uninstall cleanup. The corrected selected suite passed 47/47 before the final storage-isolation refinement. That result is preserved in `buyer-farmer-cart-device-pre-store-isolation.log`. Final verification follows below. Evidence is `buyer-farmer-cart-device-final.log`.
- Tests use synthetic account/session/API fixtures. No real authenticated cart mutation, order creation, Daraja call or STK Push occurred.

## Remaining backend and activation requirements

| Item | Expected correction/evidence | Current status |
|---|---|---|
| Authoritative cart lines | Backend owner to expose owned line IDs, product IDs, quantities and price/total evidence through the existing cart response, preserving existing fields | BLOCKED for full line synchronization |
| Farmer runtime authorization | Authorized non-production Farmer account and Supplier fixture, sanitized POST result and owned readback | UNKNOWN live, Android wired and mock-tested |
| Uncertain cart write | Backend-owned exact line readback or durable operation lookup before deciding retry is safe | Pending flag preserves uncertainty, no blind retry |
| Checkout quote/order guarantees | Confirm deployed revision, expiry and exact replay/uncertain order behavior | Existing conservative Android handling retained |
| Payment activation | Confirm backend reservation/callback guarantees and correct Daraja environment for the exact API origin | BLOCKED, BuildConfig environment UNKNOWN and confirmed URL empty |
| Phone network/authenticated flow | Stable authorized USB plus a specifically authorized test account/environment | Installation verified, real transaction trace unavailable |

Minimal backward-compatible backend cart proposal is additive `items` on existing CartResponse with owned `itemId`, `productId`, positive quantity and server prices/totals. Preserve legacy cart ID and GET creation semantics. Add readback/ownership tests and document the fields in deployed OpenAPI. Without those fields Android cannot reconstruct an existing server basket after a new installation or conclusively reconcile an uncertain cart write. No backend change was implemented.

Buyer quote/order preparation is corrected and mocked verification is available. M-Pesa remains disabled by the existing environment guard. Farmer server cart calls are enabled in the Android UI, while Farmer order and payment activation remain disabled. Batch 3 acceptance is unchanged.
The first completed device run was 46/47. Its sole failure was a Windows-encoded dash in the new test expectation. The source now uses an explicit Unicode escape while preserving the exact text assertion. The corrected server-cart UI test passed 1/1 in isolation. A separate isolated attempt ran while the phone was dozing and failed with no Compose hierarchy. The final selected-suite rerun explicitly wakes the device and keeps it awake, then restores the original setting 0.

## Final current-tree verification

The latest storage-isolated implementation was rebuilt successfully, with **645/645 JVM tests passed**, zero failures/errors. Both latest APKs installed successfully with `adb install -r`. The final current-code targeted device suite passed **47/47**, zero failures, on Samsung SM-A155F. Evidence is `buyer-farmer-cart-device-final.log`, ending `OK (47 tests)`. The server-cart UI case also verifies that a pre-existing local-only basket remains unchanged and is excluded from server-acknowledged selections.

Final debug APK SHA-256 is `5F11AAA63DA04D14D2404942E0C60EA42D51D88F485BA225CEB99867A11D1FDB`. Artifact is `app/build/outputs/apk/debug/app-debug.apk`. APK metadata and installed package/runner checks identify `com.example.farmapp.dev2231918adc4d4b9d` and its `.test` runner target. MainActivity was relaunched after the tests. No UTP uninstall cleanup or app-data clearing was used.

The 47-test set covers Farmer UI/controller/storage, shared Buyer checkout-to-payment navigation, Buyer presentation, delivery selection and shopping presentation. It is a targeted suite, not a rerun of all unrelated instrumentation classes. The genuine credential-dependent retained-session smoke was not counted as passed.

Confirmed functional evidence is authenticated transport under synthetic sessions, real UI action wiring, metadata ownership/readback, durable uncertain-write protection, exact Buyer quantity/quote preparation and pending-payment behavior. Genuine authenticated mutations, real order submission, live M-Pesa and direct phone-to-deployment HTTPS verification remain unperformed. The host can reach the deployed specification. Backend revision and complete authoritative cart-line contracts remain unknown/incomplete respectively.

The Android fixes are implemented and installed. Full cart synchronization still requires additive backend line readback, and M-Pesa requires the backend developer's verified environment and transaction guarantees. Farmer order/STK activation remains disabled. No backend source, database, SQL or deployment changes, financial transactions, commit or push occurred.