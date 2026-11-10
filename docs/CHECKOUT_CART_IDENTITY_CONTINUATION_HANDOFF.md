# Checkout cart-identity continuation and developer handoff

## 1. Build environment resolved

The normal Gradle build succeeded with the existing Android SDK, existing Gradle cache and **JDK 17.0.12**. Commands ran through the permitted elevated execution path with process-local JAVA_HOME and GRADLE_USER_HOME. No filesystem ACLs, compiler wrappers, checkout logic, Gradle scripts or machine-wide settings were changed. The earlier restricted-process toRealPath/accessor-generation failure was not reproduced in this execution environment. This establishes a working local build path, not an OS-wide permission repair.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:GRADLE_USER_HOME='C:\Users\AppleFi\.gradle'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-Pkilivana.apiBaseUrl=https://hankering-morphing-outclass.ngrok-free.dev/api/v1/' '-Pkilivana.debug.apiBaseUrl=https://hankering-morphing-outclass.ngrok-free.dev/api/v1/' --offline --console=plain
```

Result is **663/663 JVM tests passed**, zero failures/errors, and debug app/test APK compilation successful. Evidence is `cart-identity-normal-build.log` plus Gradle XML. This supersedes the prior 86-test direct Kotlin workaround as Android build verification.

## 2. Preserved implementation and files

Backend remains on `audit/checkout-cart-identity`, based on `2270f4719f915c3186e724961da7cbc2e21dc8d1`. The existing patch was inspected and tested, not reimplemented. Android and backend uncommitted changes were preserved. No production Kotlin or Java edit was made in this continuation.

Existing backend patch files, relative to `kilivana-backend-audit/src/main/java/com/kilivana/backend`

- `ecommerce/controller/CartCompatibilityController.java`
- `ecommerce/controller/CheckoutController.java`
- `ecommerce/dto/DeliveryQuoteRequest.java`
- `ecommerce/dto/DeliveryQuoteResponse.java`
- `ecommerce/repository/CartRepository.java`
- `ecommerce/service/CheckoutService.java`

Existing backend regression files, under `src/test/java/com/kilivana/backend`

- `ecommerce/service/CheckoutServiceTest.java`
- `ecommerce/controller/CheckoutCartIdentityControllerTest.java`
- `config/CheckoutCartIdentityOpenApiTest.java`

OpenAPI artifact is `kilivana-openapi.json`.

**Separate compilation prerequisite** is `ecommerce/service/OrderService.java`, cancelOrderLocked around line 427. It replaces the out-of-scope `orderId` expression with `order.getId()` when saving the cancellation event. Its regression is `src/test/java/com/kilivana/backend/ecommerce/service/OrderServiceTest.java`. This pre-existing one-line correction is reviewed separately from quote/cart selection and was not expanded here.

Existing Android patch production files, relative to `app/src/main/java/com/example/farmapp`

- `buyers/BuyerCheckoutRepository.kt`
- `checkout/CheckoutRepository.kt`
- `checkout/CheckoutController.kt`
- `checkout/CheckoutModels.kt`
- `checkout/CheckoutJournalJson.kt`

Existing Android patch regression files

- `app/src/test/java/com/example/farmapp/buyers/BrianCheckoutControllerTest.kt`
- `app/src/test/java/com/example/farmapp/checkout/CheckoutRepositoryContractTest.kt`
- `app/src/test/java/com/example/farmapp/checkout/CheckoutCartIdentityTest.kt`
- `app/src/androidTest/java/com/example/farmapp/buyers/SharedBuyerJourneyTest.kt`

This continuation added the current failure-name inventory `docs/CHECKOUT_CART_IDENTITY_CURRENT_FAILURES.json`, this handoff and verification logs. Earlier detailed implementation evidence remains in the backend `docs/CHECKOUT_CART_IDENTITY_FIX.md`.

## 3. Implementation and OpenAPI review

| Operation | Verified implementation |
|---|---|
| POST checkout preparation | Optional cartId query selects the exact existing cart. Existing CartService ownership/row lock/ACTIVE/nonempty validation applies. Omitted ID retains the legacy active-cart lookup. Empty success envelope remains unchanged. |
| POST delivery quote, explicit ID | Optional positive cartId in DeliveryQuoteRequest. Service locks the exact row, verifies authenticated owner and CHECKOUT_PROCESSING status. Missing/foreign cart uses the same unavailable response. Wrong status returns 409 CHECKOUT_CART_STATUS. |
| POST delivery quote, legacy request | Zero processing carts rejected. Exactly one is locked and revalidated. Multiple processing carts return 409 CHECKOUT_CART_AMBIGUOUS. No arbitrary newest/oldest selection and no data deletion. |
| Quote response | Adds cartId and items with productId int64, quantity int32, unitPrice and lineSubtotal decimals. The returned lines use the same multiplication and accumulation as subtotal. Delivery fee remains separate. |
| Android acceptance | Requires the original journal cart ID, exact distinct selected ID/quantity map, valid line multiplication, line sum and refreshed subtotal. Equal totals from a different basket fail. Missing identity evidence from an old server fails closed. |
| Order creation | Existing quoteId request and numeric order ID response retained. Quote lookup is locked and owned. Consumption uses its stored cart ID, checks expiry/status and records orderId. Used quotes return their saved order ID. No new order/payment API introduced. |
| Saved state | Original cart binding is retained. Mismatched journals/quotes fail closed. An old saved quote lacking line evidence must be re-quoted. Unknown upload/order outcomes remain reserved. Farmer and M-Pesa activation settings remain disabled. |

The targeted runtime OpenAPI test passed. The checked-in schema and runtime contract cover the optional preparation query, positive request cartId, additive quote lines and 409 responses.

**Deployed contract is still older.** The first request timed out. A subsequent read-only `/api-docs` request returned HTTP 200 and confirmed DeliveryQuoteRequest has no cartId and DeliveryQuoteResponse has neither cartId nor items. The earlier timeout is not evidence that fields were absent; the later successful response is the compatibility evidence. No live writes were made to probe deployment.

Android now sends the documented legacy shapes for POST `/checkout` and POST `/checkout/delivery-quote`; it strips cartId from older persisted quote requests as well. If a quote response omits cartId or item lines, the Buyer flow remains at CHECKOUT_ACKNOWLEDGED, displays a backend-contract explanation and blocks order creation. It does not treat an aggregate subtotal as proof of the selected products. The deployed backend must return cart identity and priced item lines before the complete checkout can safely proceed.

Unchanged order-consumption code recalculates prices at order creation. This identity patch is not a new price-locking/whole-transaction-readiness guarantee. Existing Android order readback and total checks remain essential, and earlier payment/deployment acceptance gates still apply.

## 4. Tests and investigation of baseline failures

JDK 17 and the existing cached Maven 3.9.9 distribution ran standard Maven/Surefire without the earlier custom compiler workaround.

```powershell
mvn.cmd -q -o '-Dtest=CheckoutServiceTest,CartServiceTest,OrderServiceTest,CheckoutCartIdentityControllerTest,CheckoutCartIdentityOpenApiTest' test
mvn.cmd -q -o test
```

| Check | Actual result | Evidence |
|---|---|---|
| Full normal Android JVM | 663 passed, zero failures/errors | cart-identity-normal-build.log and Gradle XML |
| Normal debug APK/test APK | Successful | Same log |
| Isolated debug APK/test APK | Successful | cart-identity-isolated-build.log |
| Backend targeted | 39 passed, zero failures/errors/skips | Backend checkout-cart-identity-reverified.log |
| Backend full | 573 tests, 14 failures, 6 errors, 34 skipped | Backend checkout-cart-identity-full-reverified.log |
| Device selected suite | Final checkpoint below | cart-identity-isolated-device.log |

The current failing-method set contains **20**, all included in the recorded 21-method baseline. There are no new failing methods. The baseline LocalProductImageVisibilityTest filesystem case now passes in the working execution environment. Exact current names are retained in `CHECKOUT_CART_IDENTITY_CURRENT_FAILURES.json`; prior baseline comparison remains preserved.

Investigated categories

- AuthControllerTest (3) and EmailVerificationControllerTest (5) receive HTTP 404 on legacy aliases/OTP/registration cases where tests expect 200/202/400/429. These are existing route/fixture/contract issues, not checkout-cart endpoints.
- AuthServiceEmailNormalizationTest (2 errors) dereferences a null verification challenge. The older login mocks do not supply the current challenge path.
- DeliveryOpenApiTest (1 error) cannot create its test context because DeliveryProofPhotoService is missing. The new checkout-specific runtime OpenAPI test supplies its required context and passes.
- DisputeServiceTest (2 errors) stubs findById while current resolution uses findByIdForUpdate, leaving the locked lookup unstubbed and yielding ResourceNotFoundException.
- PaymentServiceTest and PaymentCallbackTest (one failure each) contain old order-status expectations. PaymentService's update path uses locked order lookup; the old fixtures stub findById. The order remains PENDING in the failing fixture where PAID is expected.
- AccountAccessValidatorTest (2 failures), EmailOtpAuthorizationTest (1 failure) and JwtAccountEnforcementTest (1 error) conflict with current account-verification/challenge assumptions. EmailOtpAuthorization expects false where actual is true. These policy/fixture decisions need separate owner review rather than an opportunistic checkout patch.
- ServiceAuthorizationTest (1 failure) expects 403 for an address alias but receives 500. This is an existing alias/error-handling issue and remains unresolved.

These failures remain real release evidence; baseline reproduction does not make the full backend suite green. No assertions were weakened or unrelated authentication/payment policy changed to make it pass.

## 5. Isolated device installation

Because the deployed backend lacks the new fields, the normal new client was **not installed over the working app**.

The separate build used these per-command properties, with the same SDK/JDK/cache and no checked-in configuration change

```powershell
-Pkilivana.apiBaseUrl=https://checkout-identity.example.test/api/v1/
-Pkilivana.debug.apiBaseUrl=https://checkout-identity.example.test/api/v1/
-Pkilivana.debug.applicationIdSuffix=.dev000000000000c117
```

Generated BuildConfig confirms package `com.example.farmapp.dev000000000000c117`, reserved test API origin, M-Pesa UNKNOWN and Farmer transactions false. The app and matching `.test` APK installed successfully on Samsung SM-A155F. All selected device API writes use in-process synthetic fixtures. The isolated package contains no retained production login. The reserved test origin cannot be used as a working deployment endpoint.

Original working package `com.example.farmapp.dev2231918adc4d4b9d` remained installed at the same package path before/after isolated installation. Its app data and checkout journal were not cleared. The current default `app/build/outputs/apk/debug/app-debug.apk` is the **isolated test build**, not a production-endpoint release artifact. A copy of the normal endpoint build was saved as `app/build/outputs/cart-identity-normal-not-for-install.apk`, explicitly withheld from device replacement.

The first isolated selected run failed 41/50 with IllegalStateException/No Compose hierarchies while the notification shade retained focus. The phone was awake. Close/home commands initially failed to restore focus; after the owner cleared it, the launcher and then isolated MainActivity were confirmed focused. The unchanged suite was rerun. No production or test assertions were changed for this issue. Results are retained in cart-identity-isolated-device-first.log and the final checkpoint below.

## 6. Backend deployment prerequisites

No new migration is introduced by this cart-identity patch. The base revision already contains V29 checkout_quotes with cart_id/order_id and monetary columns. The backend owner must still verify the actual deployment migration history and those existing columns. Numbered SQL is not assumed automatically applied by Flyway. No production database was accessed.

Before separately authorized deployment, review the seven production Java files including the separate compile prerequisite, runtime OpenAPI evidence, and baseline test failures. Rehearse with synthetic multiple processing carts, explicit intended identity, foreign/status rejection, legacy exact-one behavior, price/line sums and quote-to-order reuse. Confirm the actual running revision after deployment, all replicas and the served /api-docs contract. Existing unresolved carts must be inspected, not silently deleted or abandoned.

## 7. Backward compatibility and Android release sequence

Old clients retain the unambiguous legacy flow and ignore additive response fields. Multiple processing carts now deliberately return 409. This is a safety correction that may require operator reconciliation for existing users.

New clients require identity/line evidence and cannot be released against the currently observed old backend. Required sequence is backend review/tests, separately authorized backend deployment, read-only contract/revision verification, staging synthetic end-to-end checkout tests, then rebuild/release the normal Android origin/package. Preserve saved journals during upgrade. Do not distribute the reserved-origin isolated APK as a working application. Farmer transactions and payment environment remain independently gated.

## 8. Rollback

Pause the new Android rollout if the backend cannot serve identity evidence. Retain the working legacy app until backend compatibility is established. Do not install an older client and clear journals to escape an ambiguous order. Preserve cart, quote, order and payment records.

Backend rollback must account for new clients already requiring cart evidence. Reverting to arbitrary first-cart selection reintroduces the demonstrated weakness, so prefer holding rollout or disabling affected checkout entry points while preserving records. Treat the unrelated OrderService compile fix separately. A schema rollback/drop is unnecessary for this DTO/service patch and must not be used to remove historical quote evidence.

No deployment, production-data changes, financial transactions, Farmer activation, commit or push occurred. No checkout implementation was reimplemented in this continuation.
## Final verification checkpoint

The unchanged isolated selected suite passed **50/50**, with `cart-identity-isolated-device.log` ending OK (50 tests). This includes the updated shared Buyer journey and preserves quote/cart/line verification in controlled mocks. It is not a genuine-account/live-backend transaction test or the complete project instrumentation suite.

The original working package path remained unchanged from before isolated installation. Both packages are installed separately. The isolated app was stopped and the original working MainActivity relaunched. Temporary stay-awake setting was verified restored to 0. No original app APK/data/journal replacement occurred.

Final totals are Android **663/663 JVM passed**, normal and isolated APK builds successful, selected isolated device **50/50 passed**, backend targeted **39/39 passed**, and backend full **573 tests, 14 failures, 6 errors, 34 skipped**. The current 20 failing methods are a subset of the recorded baseline 21. No new failing method or production source modification was introduced in this continuation. The rollout remains gated on backend compatibility and resolution/owner triage of existing backend failures.