# Phase 3 shared checkout compatibility report

Date: 6 November 2026. Scope: final audit of Batches 1–5 and Batch 6 compatibility verification. The working tree includes the user's existing uncommitted work. No commit, reset, stash, cleanup, backend change or live transaction was performed.

## Acceptance scope

Phase 3 provides reusable checkout infrastructure with Buyer compatibility adapters. It does **not** authorize or implement Farmer purchasing transactions. `UnverifiedFarmerPurchaserPolicy.permits()` remains false for every operation. Farmer catalogue browsing does not establish purchaser permissions.

This is client compatibility evidence from source inspection, JVM tests, and synthetic Android tests. No fresh authenticated backend, Daraja callback, live fee, GPS, or production payment verification is claimed.

**Final result:** Phase 3 shared infrastructure is ready for acceptance: 556 JVM tests and 57 selected Samsung instrumentation tests passed. There are no remaining failed tests in the final runs. Farmer transactions and live M-Pesa initiation remain disabled pending approval and backend confirmation.

## Completed architecture

| Batch / component | Implementation | Compatibility boundary |
|---|---|---|
| 1: shared models | `checkout/CheckoutModels.kt`: immutable cart intent, delivery alternatives, quote/request, generic checkout state | Existing quote field names and Buyer type aliases retained; station ID means platform station |
| 1: policies | `checkout/CheckoutPolicy.kt`: Buyer eligibility, disabled Farmer policy, session guard | Checks user, role, origin, generation, contract, demo/logout state; no inferred Farmer permission |
| 2: checkout repository | `checkout/CheckoutRepository.kt`, `buyers/BuyerCheckoutRepository.kt` | Generic cart/order types; existing Buyer Retrofit responses and error evidence retained |
| 2: journal | `checkout/CheckoutJournal.kt`, `CheckoutJournalJson.kt`, `buyers/BuyerCheckoutPreferences.kt` | Atomic version checks, synchronous commits, original fields/phases/keys; journal-only product map adapter |
| 3: checkout controller | `checkout/CheckoutController.kt`, `CheckoutContract.kt`, `buyers/BuyerCheckoutController.kt`, `BuyerCheckoutContract.kt` | Shared workflow; thin Brian adapter retains constructors, state and callbacks; Buyer validators retain exact order checks |
| 4: delivery repositories | `checkout/DeliveryRepositories.kt`, `buyers/BuyerDeliveryRepositories.kt` | Shared address/station/nearest/logistics/tracking interfaces; Buyer DTOs and permissions stay in adapters |
| 4: address/station controllers | `buyer/BuyerAddresses.kt`, `BuyerPickupStations.kt`, `buyers/BuyerAddressRepository.kt` | Existing ownership checks, selections, mutexes, durable write markers and reconciliation stay in place |
| 5: payment rules | `payments/MpesaRules.kt`, `orders/BuyerMpesaEnvironment.kt` | Buyer helpers delegate unchanged phone/status/environment rules; no configuration change |
| 5: payment attempts | `payments/PaymentAttempts.kt`, `orders/BuyerPayment.kt` | Shared reservation algorithm with the existing cross-instance lock and boolean preference records |
| 5: payment workflow | `payments/PurchaserPaymentContract.kt`, `PurchaserPaymentController.kt`, `orders/BuyerPaymentRepository.kt` | Shared controller/repository boundaries; Buyer order, amount and payment validators remain role-specific |
| 6: integrated verification | `buyers/SharedBuyerJourneyTest.kt` under `androidTest` | Actual screens, compatibility controllers, saved records and order routes connected to in-process fake APIs |

Actual flow: Compose screen → Buyer compatibility controller → shared checkout/payment workflow → Buyer repository → existing Retrofit API → OkHttp/session authentication → backend. Delivery controllers use the shared repository interfaces while retaining their existing write-recovery logic. `StateFlow` publishes controller state to Compose; lifecycle polling still belongs to the existing Buyer UI.

The generic parameters preserve different cart, order, payment and initiation DTOs. They do not imply interchangeable role permissions. Retrofit/envelope types remain at repository boundaries deliberately, so extraction does not reinterpret HTTP errors or discard conflict evidence.

## Frozen Buyer client contracts

Paths below are relative to the existing configured `/api/v1/` base. Contract tests exercise synthetic transport requests, bodies, session tags and authentication. They do not prove that a current deployment authorizes Farmer accounts.

| Area | Existing methods / paths retained |
|---|---|
| Cart / checkout | GET `carts/buyer/{buyerId}`; POST `cart/items`; PUT `carts/{cartId}/items/{productId}` with quantity query; POST `checkout`; POST `checkout/delivery-quote`; POST `checkout/order` |
| Orders | GET `orders/buyer/{id}`, `orders/{id}`, `orders/{id}/timeline`; POST `orders/{id}/confirm-receipt` remains outside shared checkout/payment extraction |
| Own addresses | GET/POST `users/addresses`; PUT/DELETE `users/addresses/{id}` |
| Platform stations | GET `pickup-stations/availability`, `pickup-stations/available`, `pickup-stations/available/region/{region}`, `pickup-stations/{stationId}` |
| Saved station associations | GET/POST `pickup-stations/my-stations`; PUT `pickup-stations/my-stations/{userStationId}/default`; DELETE `pickup-stations/my-stations/{userStationId}` |
| Nearby stations | GET `pickup-stations/nearest` with existing `lat`, `lng`, `radiusKm`, `limit` parameters; no new location permission or automatic lookup |
| Logistics / tracking | GET `logistics/jobs/order/{id}`, `logistics/jobs/{id}/tracking`, `tracking/{id}/location` |
| M-Pesa | POST `payments/stk-push/buyer` with only `orderId` and normalized `phoneNumber`; GET `payments/order/{id}`, `payments/{id}/status` |

No endpoint annotations, authentication interceptor, base URL, canonical numeric adapter or payment environment settings changed in Batch 6. Order creation still returns a documented numeric ID, without invented wrapper fallbacks. STK initiation does not accept a client-calculated amount.

## Persistence and recovery compatibility

All names retain the existing account/backend `buyerScope` hash:

| Store | Retained records / behavior |
|---|---|
| `buyer-checkout-<scope>` | `journal` JSON plus `version` Long; atomic compare-and-increment and synchronous commit |
| Journal schema | `phase`, `items`, `expectedSubtotal`, `cartId`, `quote`, `request`, `orderId`, `beforeOrderIds` |
| Journal phases | NONE, UNRESOLVED, UPLOAD_PENDING, QUANTITY_PENDING, CART_REJECTED, UPLOAD_ACKNOWLEDGED, CHECKOUT_PENDING, CHECKOUT_ACKNOWLEDGED, QUOTE_READY, ORDER_PENDING, ORDER_RETURNED, ORDER_VERIFIED |
| Product maps | Saved `{"7":3}` reads through a field-scoped journal adapter; malformed/duplicate IDs and invalid quantities fail safely; canonical backend number parsing remains strict |
| Corrupt journals | Load as UNRESOLVED; raw saved evidence is not overwritten or silently reset; expired quotes can load for recovery but cannot create another order |
| `buyer-shopping-<scope>` | Existing cart/saved-item records, `delivery-address-id`, `address-save-pending`, `pickup-station-id`, `pickup-write-pending` |
| Station markers | `kind`, `platformId`, `userStationId`, `label`, `isDefault`; platform and association IDs are never substituted for each other |
| `brian-address-<scope>` | Existing `pending` record with kind, address ID, fingerprint and previous IDs; reconciliation remains Buyer-owned |
| `buyer-payment-<scope>` | Order-ID string keys with boolean `true`, synchronous pre-send commit and shared lock; never cleared to force a retry |

Recreating controllers against preferences is verified. This is restart/recreation evidence, not a claim of an OS power-loss or forced-process-kill test. The legacy JSON compatibility test correctly compares object fields/values rather than Android-dependent reflection field order.

## Safety checks reviewed and tested

| Situation | Preserved outcome / evidence |
|---|---|
| Stale account, role, origin, generation, contract, demo/logout state | Guard rejects requests before transport and rejects late responses after suspension; checkout/delivery/payment repository and controller suites cover this |
| Buyer product eligibility | Active Farmer produce, stock and minimum quantity checks remain in Buyer policy/catalogue/cart code; Supplier catalogue visibility never authorizes Farmer writes |
| Duplicate user actions / concurrent instances | Busy guards, journal version checks, durable order reservations and atomic payment reservations prevent replay |
| Interrupted upload | Original item/subtotal/cart evidence remains; metadata alone cannot promote an uncertain upload to verified checkout |
| Interrupted checkout transition | Read-only recovery uses the original cart ID and delivery request, limited to eligible saved phases |
| Unknown order submission | ORDER_PENDING remains unresolved, even if similar orders appear; no automatic retry or similarity-based adoption |
| Returned order / quote mismatch | Exact ID, owned purchaser, products/quantities, totals, line subtotal sum and address/station/typed location checked before payment review |
| Station selection | Platform ID 3 and saved association ID 1003 remain distinct throughout the new synthetic journey |
| Interrupted payment / missing backend list | Persistent marker prevents another STK after recreation; pending/unknown/failed/refunded outcomes do not erase reservations |
| Payment initiation says PAID | Shared controller still publishes pending; only later authoritative backend status can confirm payment |
| Amount changes / partial payment | Reviewed total is compared with fresh order total before initiation; existing partial-payment/full-confirmation presentation rules remain unchanged |
| Wrong logistics association | Order → job → driver/location associations are validated; foreign jobs and invalid fixes are rejected |
| Missing driver position | HTTP 404 produces an unavailable-location message; no zero-coordinate fallback or fabricated map marker |
| Environment UNKNOWN | Submission remains disabled; order/payment review and status reads remain available |

## Architecture review findings

1. Checkout and payment transaction workflows each have one shared engine. Buyer compatibility adapters preserve the existing Kotlin-facing API. No second Farmer transaction system was introduced.
2. `BuyerCheckoutReviewController` and older delivery review UI remain read-only diagnostic/legacy paths. They do not constitute a competing order-creation controller. The active `BrianBuyerApp` uses `BrianCheckoutController`.
3. Repeated session checks in controllers and repositories are intentional defensive boundaries. Small transport guard helpers and legacy quote projections remain duplicated; consolidating them would broaden this compatibility batch without resolving a demonstrated regression.
4. `CheckoutCartSnapshot` and `PurchaseOrderSnapshot` are available domain models; they do not claim authoritative server cart evidence or grant permissions. The active Buyer adapter preserves backend DTOs instead of forcing an unsafe universal order schema.
5. `PaymentStatusCard.kt` is an existing read-only presentation component. Its presence in the payments package does not enable Supplier/Farmer initiation.
6. Buyer order pages and payment sessions retain their existing lifecycle refresh behavior, including potentially overlapping reads. Poll coalescing is a separate performance task; no payment write is triggered by polling or navigation.
7. The active checkout verifies order creation and navigates directly to payment review. It has no separate registered `confirmation/{id}` route. Batch 6 verifies that actual sequence rather than adding a screen or route.
8. The missing connected UI coverage is now supplied by `SharedBuyerJourneyTest`. Its harness uses actual UI components, controllers/adapters, persisted stores and `buyerOrderDestinations`; it does not start the globally authenticated production app or contact backend services.

## Batch 6 modifications

- `app/src/main/java/com/example/farmapp/tracking/DeliveryTrackingScreen.kt`: retain the original entry point and extract an internal `DeliveryTrackingContent` dependency seam. The wrapper still supplies TokenManager and the existing API. Source comparison confirms identical presentation/polling body after substituting the injected session reference.
- `app/src/androidTest/java/com/example/farmapp/buyers/SharedBuyerJourneyTest.kt`: three tests for the complete disabled-payment journey, explicit synthetic pending→confirmed payment plus restart recovery, and tracking association/coordinate failures. APIs are in-process implementations; synthetic preferences are uniquely scoped and cleaned up.
- `app/src/androidTest/java/com/example/farmapp/buyer/BuyerDeliveryPresentationTest.kt`: scroll the owning lazy list to Save address after text input changes the viewport; all existing assertions retained.
- This report. Other existing uncommitted files are preserved; no unrelated source refactoring.

## Verification results

- Complete JVM suite: **556 passed, 0 failures, 0 errors, 0 skipped**. Includes 34 original Brian checkout regressions, 15 shared checkout controller tests, 14 delivery repository tests, 13 shared payment controller tests and 6 payment repository contract tests, plus the remaining model/policy/journal/authentication/role/UI-mapping suites.
- Instrumentation compilation and app/test APK builds: **passed**. Existing icon deprecation warnings remain; no dependency or build-setting changes.
- New journey tests initially: **3 passed, 0 failed, 0 skipped**.
- First wider device run: **53 passed, 1 failed, 0 skipped**. `addAndSelectConfirmedLocationThenBackPreservesSelection` could not locate Save address after text input. The display stayed awake, and the test passed unchanged in isolation. This was not attributed to the lock screen. The exact timing trigger was not conclusively reproduced; source/layout inspection identified a fragile leaf lookup after the IME changes the lazy-list viewport. Only the test's parent-list scrolling was strengthened.
- Final combined Samsung run: **57 passed, 0 failed, 0 skipped**, 90.536 seconds. Includes all three new connected journey/tracking tests and 54 existing shopping, delivery, pickup, navigation, order presentation, payment review and persisted-record tests. The strengthened address-form test passed in this complete run.
- Phone: Samsung SM-A155F, serial RF8WC0K043H. APKs installed with `adb install -r`; no uninstall/data clearing. Each instrumentation command restores the original stay-awake setting (`0`) in a finally block.
- Final integrity check: only the intended tracking dependency seam and delivery test were modified relative to the Batch 6 baseline; the journey test and this report are new files. Existing checkout/payment code, Retrofit annotations, configuration, persisted formats and unrelated uncommitted work remain intact. `git diff --check` passed.

Commands: `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline --console=plain`; `:app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain`; direct class-selected AndroidJUnitRunner instrumentation. JVM XML reports are under `app/build/test-results/testDebugUnitTest/`. Device logs are in the task's temporary `kilivana-batch6-*.log` files, including initial failures and final results.

## Backend dependencies and remaining risks

These need confirmation before any Farmer implementation or real payment demonstration:

1. Farmer purchaser role/ownership permissions for cart, checkout, address/station access, orders/history, payments/status and logistics; matching deployed backend source or owner confirmation is required. Existing Buyer endpoints are not assumed suitable.
2. Supplier product eligibility, quantity units, stock/reservation behavior, pricing and multi-seller order/logistics rules for Farmer purchases.
3. An authoritative readable cart-line contract. Metadata-only cart reads cannot prove the pre-order server item set. Current acknowledgement/quote/exact-order-readback safeguards remain; they do not replace missing backend evidence.
4. Duplicate-safe backend order creation, idempotency or quote→order lookup, and payment attempt lookup/retry authorization. Unknown client outcomes deliberately require reconciliation/support rather than automatic replay.
5. The semantics of supplying `phoneNumber` to checkout/order, including confirmation that it cannot initiate payment implicitly before separate user confirmation.
6. Confirmed Daraja sandbox/live mode for the exact configured origin, callback readiness, authoritative status transitions and authorized test accounts. BuildConfig remains UNKNOWN with an empty confirmed URL; no live STK was sent.
7. Exact station/address coverage and delivery quote rules, order→logistics assignment timing, multi-job selection, location availability and any additional status/timestamp accuracy rules. No live fees, GPS accuracy, Mapbox tiles or delivery data were verified.
8. UI test stability should continue to be monitored. One lazy-list/IME-sensitive test was strengthened; a clean rerun is compatibility evidence, not proof that all device timing variation is eliminated.

No Farmer purchasing or payment initiation is enabled. Further implementation or enabling requires the user's next approval and verified backend contracts.
