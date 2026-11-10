# Phase 4, Batch 2: e-commerce contract alignment and Farmer purchaser integration

Date: 6 November 2026. Scope: this batch only. No backend changes or live transactions.

## Result and permitted behavior

The Farmer Supplies destination now connects the existing Supplier catalogue to a persisted **local basket**, fresh product/quantity review, owned-address and pickup-station selection, purchaser order history/details, read-only payment review and delivery tracking. The Farmer's authenticated **user ID** supplies `buyerId`; Supplier IDs and Farmer seller order IDs never substitute for that identity. The existing Farmer seller Orders destination is unchanged.

The basket is local intent, not evidence of a server cart. Product prices are refreshed for an explicitly labelled estimate. No delivery fee, final payable total, quote, order confirmation or payment confirmation is fabricated. Existing purchase orders and payment statuses are read from the backend and validated. Historical order DTOs do not identify seller type; history therefore says “My purchase orders” rather than claiming every historical order contains Supplier inputs.

**Backend cart access, cart uploads, quote requests, order creation, address/station writes and M-Pesa initiation are disabled.** The UI explains the unresolved backend requirements. The Farmer repository also rejects these operations before transport, even if called outside the UI. Cart GET is blocked because the inspected service can create a new ACTIVE cart during a nominal read. There is no runtime toggle that enables these writes in this batch.

## Evidence and contract resolution

The public deployed `/api-docs` was retrieved again without authentication or commerce calls. Its SHA-256 remains `7CF98CC2F340247C5CDB9C7BE8117E6CA9B379768EA67569538FE5770FFD9E31`, matching the Phase 4 Batch 1 audit. The sanitized contract excerpt in `app/src/test/resources/farmerpurchases/deployed-contract.json` preserves selected paths and DTO schemas from that document, plus provenance. Tests use synthetic payloads shaped to these schemas; they do not execute OpenAPI examples against the server.

Source evidence is pinned to [Kilivana-Backend a8205b0](https://github.com/danielkamweru/Kilivana-Backend/tree/a8205b07a0f0ba93105197c6472b7e4439012025), including `CartController`, `CartCompatibilityController`, `OrderController`, `ResourceAuthorization` and payment/checkout services inspected in the completed audit. Angular evidence is the supplied/reference KILIVANA checkout services, `shared/api.config.ts`, `order.spec.ts`, `payment.spec.ts` and cart tests, available in the prior website audit checkout.

Source authorization permits FARMER as an authenticated purchaser for own-resource reads and purchaser STK. **The deployed build revision and authenticated Farmer permissions have not been runtime verified.** HTTP 401/403, missing routes and failed envelopes remain visible failures; they never become empty successful responses.

Paths below are relative to the unchanged `/api/v1/` base. Protected requests use the existing session-tagged authenticated client and bearer interceptor; canonical Gson numeric validation remains unchanged.

| Capability | Verified mapping / envelope | Alignment and current status |
|---|---|---|
| Supplier catalogue | GET `products/search?sellerType=SUPPLIER&page=&size=`, Page<ProductResponse>; GET `products/{id}`; GET `categories/type/SUPPLIER` | Existing MarketplaceRepository reused. Validates seller type, page and product identity. Local basket enforces AGRI_INPUTS, active stock/minimum quantity and no own-product purchases. |
| Principal cart | GET `cart`, ApiResponse<CartResponse> | Angular principal alias; same service as the explicit purchaser alias. Metadata only; can create ACTIVE cart. **Blocked.** |
| Explicit purchaser cart | GET `carts/buyer/{buyerId}`, same envelope | Existing Android contract retained. No automatic alias fallback or invented line readback. **Blocked.** |
| Add product | POST `cart/items`, numeric `{productId,quantity}` → cart metadata | Existing shared transport verified synthetically. Duplicate adds can increment quantities; **blocked**, no retry. |
| Set quantity | PUT `carts/{cartId}/items/{productId}?quantity=` | Product ID route differs from Angular `cart/items/{itemId}`, whose ID is the cart-line ID. Never map a product ID to an unknown line ID. **Blocked.** |
| Start checkout | POST `checkout`, void envelope | Transitions the original cart to processing; not order creation. **Blocked.** |
| Quote | POST `checkout/delivery-quote`, CheckoutQuoteRequest → CheckoutQuote | Existing Phase 3 DTO retained, with exclusive address/platform-station/typed-location fields. **Blocked** until durable, exact cart/quote reconciliation is available. |
| Order from quote | POST `checkout/order`, `{quoteId,phoneNumber}` → **ApiResponse<Long>** | Existing shared engine contract retained. No automatic payment or direct OrderResponse inferred. **Blocked.** |
| Direct order | POST `orders`, OrderRequest → ApiResponse<OrderResponse> | Current Angular api.config CREATE and Java OrderController use this. It is not compatible with quote-ID creation or Angular's old recipient-name/phone/CASH test payload. No fallback implemented. **Blocked.** |
| Stale Angular test route | POST `orders/checkout` | `order.spec.ts` expects it, but deployed OpenAPI and inspected Java do not advertise it. Not added to Android. |
| Purchase history | GET `orders/buyer/{userId}`, **ApiResponse<List<OrderResponse>>** | Connected with authenticated Farmer purchaser ID. No seller/order-mine fallback. |
| Paginated order search | GET `orders/search?buyerId={userId}&page=&size=`, **ApiResponse<Page<OrderResponse>>** | Separate tested repository retains the Page envelope and validates page metadata. Not coerced into a List. UI history uses the verified list route. |
| Purchase details | GET `orders/{id}`, ApiResponse<OrderResponse> | Connected. Exact ID, purchaser ownership, unique lines/products, quantity × price, subtotal, fee, total and exclusive delivery IDs validated. |
| Owned addresses | GET `users/addresses`, ApiResponse<List<AddressResponse>> | Connected read-only. Foreign users, duplicate IDs and invalid coordinates rejected. Address needs both coordinates for selection. |
| Pickup stations | GET `pickup-stations/availability`, `available`, `available/region/{region}`, `{stationId}`, `my-stations` | Existing wire interfaces and shared delivery boundaries reused. Fresh platform station detail verifies selection. Saved association `id=91` is distinct from `pickupStationId=3`. Mutation methods remain blocked. No nearest-location permission or fabricated coordinates added. |
| Logistics / tracking | GET `logistics/jobs/order/{id}`, `logistics/jobs/{jobId}/tracking`, `tracking/{jobId}/location` | Connected reads, preceded by owned-order/job association checks. `tripId` in location DTO means job ID in inspected mapper; order, job and driver must agree. Missing location remains unavailable. |
| Payment review | GET `payments/order/{orderId}`, `payments/{paymentId}/status` | Shared payment controller reused; fresh owned order and bounded amounts/status/IDs verified. Existing reservations remain unresolved rather than retried. Explicit refresh only. |
| Purchaser STK | POST `payments/stk-push/buyer`, `{orderId,phoneNumber}` → StkPushInitiateResponse | Correct source-confirmed Farmer purchaser route; verified only through intercepted synthetic transport. Farmer adapter initiation is unconditionally blocked. Seller `/payments/stk-push/farmer` is never used. |

## Architecture and compatibility

`FarmerPurchasesScreen` → `FarmerPurchaseController` → Farmer purchaser repositories → existing Retrofit interfaces → existing authenticated OkHttp client. The controller composes the Phase 3 `CheckoutController` and `PurchaserPaymentController`, using separate Farmer authorization and the same pure wire validators. It does not copy either workflow state machine or weaken Buyer role guards.

`FarmerPurchaserPolicy` permits only order/payment/address/station/logistics reads. The original `UnverifiedFarmerPurchaserPolicy` still denies every operation. Checkout's READ_CART guard prevents preparation, creation and automatic recovery from entering an unsupported backend cart workflow. An existing unknown journal remains unresolved; no write is retried. Payment initiation has both an always-false shared-controller gate and a rejecting repository boundary.

New Farmer preferences use `farmer-purchases-` + the existing backend/user scope hash. They retain the shared journal JSON codec, `journal`/`version` CAS checks and durable attempt semantics in that separate namespace. Basket key `local-basket` stores only positive product IDs/quantities through the shared strict product-map adapter, never prices. It is deliberately separate from the journal schema, which requires subtotal evidence for nonempty checkout journals. Farmer attempt keys are `payment-{orderId}` in this new namespace. **No Buyer filename, key, phase, version, migration or payment marker changes.** Corrupt Farmer records fail closed without deleting or overwriting them through the UI. Old Buyer compatibility tests remain mandatory.

## Files introduced or changed in this batch

All paths below are beneath `app/src/`, except this report.

| File | Purpose |
|---|---|
| `main/java/com/example/farmapp/checkout/FarmerPurchaserPolicy.kt` | Read-only Farmer purchaser policy and explicit checkout/payment blocker explanations. |
| `main/java/com/example/farmapp/farmerpurchases/FarmerPurchaseRepositories.kt` | Farmer session boundary, existing commerce DTO/validator reuse, read-only checkout/payment adapters, strict purchaser order checks, distinct Page search repository and HTTP/envelope failure handling. |
| `main/java/com/example/farmapp/farmerpurchases/FarmerDeliveryRepositories.kt` | Farmer-owned address/station reads and exact own-order logistics/tracking adapters implementing Phase 3 interfaces; all mutations blocked. |
| `main/java/com/example/farmapp/farmerpurchases/FarmerPurchasePersistence.kt` | Separate Farmer local basket, versioned journal and durable attempt storage. |
| `main/java/com/example/farmapp/farmerpurchases/FarmerBasketJson.kt` | Strict IDs/quantities-only local codec using the existing product-map adapter; rejects malformed, duplicate and trailing data without inventing a journal subtotal. |
| `main/java/com/example/farmapp/farmerpurchases/FarmerPurchaseController.kt` | Local-basket/read-only presentation orchestration, fresh Supplier detail checks and composition of shared checkout/payment controllers. |
| `main/java/com/example/farmapp/farmerpurchases/FarmerPurchasesScreen.kt` | Production wiring and Farmer basket, delivery review, purchase history/details, payment review and tracking UI. Disabled write buttons explain why. |
| `main/java/com/example/farmapp/marketplace/Marketplace.kt` | Optional product-actions composable slot; default behavior for every existing caller is preserved. No catalogue transport or role changes. |
| `main/java/com/example/farmapp/ui/screens/FarmerSuppliesScreen.kt` | Delegates Supplies to the new Farmer host; signature and existing seller Orders navigation remain unchanged. |
| `test/java/com/example/farmapp/farmerpurchases/FarmerPurchaseTestFixture.kt` | Synthetic intercepted HTTP/session/controller fixture; no transport proceeds to the network. |
| `test/java/com/example/farmapp/farmerpurchases/FarmerApiAlignmentTest.kt` | Captured OpenAPI method/path/schema checks and Angular/Java mismatch regression tests, including correct purchaser STK route. |
| `test/java/com/example/farmapp/farmerpurchases/FarmerPurchaseRepositoryTest.kt` | Purchaser/seller identity, auth tags/headers, pagination, strict IDs/money, blocked writes, sessions, ownership, station mapping, tracking and failures. |
| `test/java/com/example/farmapp/farmerpurchases/FarmerPurchaseControllerTest.kt` | Basket restart/revalidation, Supplier eligibility, corrupt records, stale sessions, unknown outcomes, duplicate submission blocking and read-only history/tracking. |
| `test/java/com/example/farmapp/farmerpurchases/FarmerBasketJsonTest.kt` | Production local-basket codec round trips, exact large IDs and malformed/duplicate/invalid-write rejection. |
| `test/resources/farmerpurchases/deployed-contract.json` | Selected fresh deployed OpenAPI paths/schemas with source/date/hash provenance. |
| `androidTest/java/com/example/farmapp/farmerpurchases/FarmerPurchasesPresentationTest.kt` | Synthetic Farmer basket/delivery UI, disabled checkout/payment, purchaser history/details/tracking and stale-session checks. |
| `androidTest/java/com/example/farmapp/farmerpurchases/FarmerPurchaseRecordCompatibilityTest.kt` | Android preference restart, Buyer namespace isolation, corruption preservation, atomic journal versions and payment reservations. |
| `docs/PHASE4_BATCH2_FARMER_PURCHASING_ALIGNMENT.md` | This implementation, endpoint-resolution, compatibility and validation record. |

## Validation

| Final verification | Passed | Failed / errors | Skipped |
|---|---:|---:|---:|
| Complete JVM suite | **589** | **0** | **0** |
| New Farmer Samsung instrumentation | **7** | **0** | **0** |
| Accepted Buyer Samsung compatibility set, complete repeated run | **57** | **0** | **0** |
| Final distinct Samsung cases across those two runs | **64** | **0** | **0** |

The 589 JVM cases retain all 556 previously accepted cases and add 33 Farmer contract/controller/codec cases. `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded. Final build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

The Samsung SM-A155F (`RF8WC0K043H`) was connected, awake and unlocked before testing. Synthetic test activities/APIs were used; the production authenticated app journey and payment endpoints were not exercised. APK replacement preserved app data. Each instrumentation command used `try/finally` to restore `stay_on_while_plugged_in`; the original and final values were both **0**. No timeout, credential, backend URL, M-Pesa or other device-setting changes were made.

Failures were investigated rather than hidden:

- The first targeted test compile found a wrong import in a new test; it was corrected to the existing canonical mapper package. The first full JVM run then passed 583/584 cases, failing a new synthetic fixture that returned `data:null` for a JsonObject cart. The canonical decoder correctly rejected it. Only the new fixture was corrected to return schema-shaped cart/quote/order responses. Subsequent full suites passed 584, then 586 as tests were added.
- The first seven-case Farmer device run passed **3**, failed **4**, skipped **0**. It exposed a real new basket persistence error: the full journal codec correctly rejected nonempty records lacking subtotal evidence. The fix uses only the existing strict product-map adapter for the new IDs/quantities-only basket, without changing journal/API number validation. Two new journal test fixtures also needed valid subtotals. The fourth assertion matched both delivery status and event text; its selector now targets the first status match while retaining the display assertion. Three production-codec JVM regressions were added. Final JVM total is 589/589, and the Farmer device rerun is 7/7.
- The first Buyer 57-case run passed **54**, failed **3**, skipped **0**. `dashboardNavigationUsesRealCallbacks` found no Compose hierarchy; `detailQuantityHonorsMinimumAndStockBeforeAdd` and `downwardGestureTriggersMarketplaceRefresh` reported an already-destroyed ActivityScenario host. The phone was awake/unlocked, `always_finish_activities` was 0 and no application fatal exception appeared in the captured runtime log. The **unchanged** shopping class passed **13/13** in isolation; the **unchanged complete 57-case set** then passed **57/57**. This supports an intermittent test-host lifecycle issue rather than a reproduced application regression; its exact cause is not established. No Buyer implementation, timing or assertion was weakened. That intermittent harness behavior remains a validation risk to monitor.

The Buyer set covers shared synthetic journeys, shopping, delivery/address, pickup stations, order navigation/details, checkout presentation, payment review, tracking and saved checkout/delivery/payment compatibility. Logs are retained in the task's temporary verification directory: `kilivana-phase4-batch2-d948c62e166741a496d5f014001b7757`.

Final integrity review compared SHA-256 hashes against the preimplementation file inventory: **only Marketplace.kt and FarmerSuppliesScreen.kt changed among existing files**. Sixteen batch files were added (including this report). All earlier uncommitted files, shared engines/codecs, Buyer adapters/preferences/tests, seller workflows, Gradle configuration and payment environment configuration remain byte-identical to the baseline. `git diff --check` found no whitespace errors; existing Windows line-ending notices are not source changes. Nothing was committed, reset, stashed or deleted.

## Backend dependencies before transaction activation

1. Confirm deployed revision and authorized non-production Farmer purchaser/foreign-owner negative tests; public OpenAPI does not prove runtime permissions.
2. Provide authoritative cart lines, quantities and totals, exact cart identity and original processing-cart readback. Define duplicate-add semantics and stable line IDs. A replacement ACTIVE cart is not recovery evidence.
3. Provide durable, immutable, ownership-bound quotes with exact item/price/delivery snapshots, expiry and atomic quote consumption/order creation.
4. Supply backend idempotency plus lookup/reconciliation for uncertain cart, checkout and order outcomes. Client journal reservations alone cannot establish whether a timed-out write committed.
5. Persist payment intent before calling Daraja; provide duplicate prevention, authoritative reconciliation, exact currency/amount handling and callback amount verification. Confirm provider/deployment environment separately. No initiation or environment configuration is enabled here.
6. Confirm server enforcement of the intended Farmer→Supplier input eligibility, dispatch warehouses/coordinates and approved delivery tariffs. Historical order DTOs alone cannot establish seller type.

This batch does not authorize any subsequent Farmer transaction enablement or additional batch.
