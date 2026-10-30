# Farmer purchases, payments and incoming orders: contract investigation

## Superseding deployed-only assessment

Daniel's deployed OpenAPI and actual deployed authorization are authoritative. Earlier statements below about restrictions in local repositories are historical source comparisons only; **they are not evidence that Daniel's deployed backend denies Farmer purchasing, checkout, payment, or seller order access**. The prior conclusion that these deployed workflows are blocked by those local restrictions is withdrawn. Their current status is unverified.

A fresh public-only verification after this clarification returned:

| Request | Result |
| --- | --- |
| GET https://hankering-morphing-outclass.ngrok-free.dev/api-docs | HTTP 503; live specification unavailable |
| GET https://hankering-morphing-outclass.ngrok-free.dev/api/v1/health | HTTP 404, ERR_NGROK_3200 (ngrok tunnel offline) |

No new OpenAPI snapshot was saved from these failures. No credentials, cart/order requests, payments or backend changes were used. No currently supported commerce operation or runtime role permission can be confirmed from these failed checks. Previously downloaded OpenAPI is historical evidence only; it advertises sellerId order filtering and buyer STK, but cannot establish today's contract or authorization behavior.

Unconfirmed deployed permissions: FARMER cart/checkout eligibility, Farmer-as-purchaser order placement and payment, purchaser history/status reads, Farmer seller-scoped list/detail/timeline/payment projection, seller state transitions, and order-notification linkage. Do not label them denied based on local source, or supported merely because routes appear in an earlier specification.

Android integration locations once the live contract is available:

1. `marketplace/Marketplace.kt`: quantity/product selection and cart/review navigation, retaining uppercase seller filters and live prices/images.
2. `orders/` and `data/remote/ApiClient.kt`: separate purchaser and incoming-seller DTO/API/repository/ViewModel flows using confirmed schemas, principal-bound ownership, scoped pagination/detail/timeline and session tags. Do not remove the existing Buyer role guard to reuse BuyerOrdersModel for Farmers.
3. `ui/screens/FarmerShell.kt`: separate purchase history from incoming produce orders; connect only the confirmed seller-scoped read flow instead of the existing unavailable canonical Farmer orders adapter.
4. `payments/`: server-total review, duplicate-safe initiation and reconciliation, authorized status reads and lifecycle refresh. Use only a confirmed purchaser payment contract; do not infer payer semantics from a role-named endpoint. No PIN UI or fabricated payment success.
5. Mocked tests: exact requests/envelopes after schema confirmation, buyer/seller ownership, duplicate submissions, uncertain outcomes, stock/quote changes, status mappings, API failures, and empty/loading states. No live mutations.

Exact new Retrofit signatures/DTO fields cannot responsibly be finalized while the live specification is unavailable. No application implementation changes were made for this deployed-only reassessment.

Questions for Daniel after reachability is restored (only where the live documentation does not answer them): Does FARMER act as purchaser of SUPPLIER listings, including the purchaser STK flow? What scope does sellerId impose on list/detail/timeline/payment data, especially mixed-seller orders? What are checkout and STK idempotency/reconciliation guarantees and permitted seller transitions? The immediate requirement is a reachable deployment/OpenAPI or an authoritative export of its current specification; no account status change is indicated.

The sections below retain the earlier investigation history and must be read subject to this superseding assessment.

2026-10-27. No live login, cart, order, notification mutation, callback or payment request was sent. No backend code was edited.

## Findings presented before implementation

Both requested workflows have critical authorization/contract dependencies. They must not be enabled by guessing role permissions or downloading all orders and hiding other sellers' rows in Android.

The configured origin remains `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`. A fresh GET `/api-docs` failed with `ERR_NGROK_3200`; a separate GET `/api/v1/health` returned HTTP 404 with the same tunnel-offline identifier. This is an ngrok availability result, not an application-level order response. Current deployment reachability and authorization cannot be confirmed. The successful health check earlier today is historical, not evidence of current health.

Used the previously captured `kilivana-replacement-openapi-20261027.json` for exact advertised contracts. It advertises **sellerId order filters and a buyer STK endpoint** that are absent from the local `kilivana-supplier-backend` and `kilivana-shared-backend` implementations inspected here. Therefore those local source trees are older/different and cannot prove the deployed authorization rules. The separate `kilivana-backend` / `kilivana-backend-android-integration` implementations use UUID-based order/customer-order and checkout models; they are not drop-in implementations of the deployed numeric-ID ApiResponse contract.

### Feature 1: Farmer buys Supplier supplies

| Step | Advertised existing contract / source evidence | Decision |
| --- | --- | --- |
| Browse/detail | Existing Supplier-filtered marketplace and GET products/{id} | Working; preserve uppercase SUPPLIER and real backend product/image data |
| Cart | GET carts/buyer/{buyerId}; POST carts/buyer/{buyerId}/items with productId, quantity; PUT carts/{cartId}/items/{productId}?quantity=; DELETE item | Local ResourceAuthorization.buyer accepts BUYER/Admin, not FARMER. A GET may create a cart in local source, so it was not probed. Farmer purchaser permission is unconfirmed. |
| Review | CartResponse contains id, buyerId, status and timestamps; no items or authoritative totals in that response | Need a complete supported review/quote contract, including delivery/address and server totals; do not synthesize a payable quote |
| Checkout | POST carts/{cartId}/checkout -> ApiResponseVoid | Local CartService.checkout always throws service unavailable. Swagger describes order conversion but exposes no order ID in its void response. No safe checkout/recovery can be implemented from that alone. |
| Direct order | POST orders -> ApiResponseOrderResponse | Local OrderService requires Admin caller AND buyerId with BUYER role; FARMER cannot act as purchaser. Snapshot does not document overriding this policy. |
| Farmer STK | POST payments/stk-push/farmer | This is Farmer-as-SELLER collection from the produce buyer, not Farmer-as-purchaser payment to a Supplier. Do not wire it to supply checkout. |
| Buyer STK | POST payments/stk-push/buyer (present in captured deployed OpenAPI) | Describes purchaser initiating payment for their own order. Farmer eligibility, payment ownership, pending-attempt reconciliation and idempotency are not established; older local source lacks this route. Requires Daniel's current implementation/contract confirmation. |
| Status/history | GET payments/{id}/status; GET payments/order/{orderId}; GET orders/buyer/{buyerId}; GET orders/{id} | Local authorization delegates to BUYER/Admin order ownership. A Farmer cannot obtain a purchase history by pretending to be BUYER or using seller IDs as buyer IDs. |

No conclusion about legal/commercial eligibility can be derived from an endpoint name. Technically, the inspected source explicitly excludes FARMER from the purchaser policy; Daniel must confirm and implement the intended Farmer->Supplier business rule.

### Feature 2: Farmer receives Buyer orders

| Existing route | Captured schema | Authorization / privacy issue |
| --- | --- | --- |
| GET orders and orders/search | Optional buyerId, sellerId, status; page/size/sort; ApiResponsePageOrderResponse | Snapshot includes sellerId, older local controller does not. Parameter existence alone does not prove caller binding or seller-only projection. Need current source or explicit guaranteed permission contract. |
| GET orders/{id} | ApiResponseOrderResponse containing all items and buyer/delivery/payment data | Local ResourceAuthorization.order permits BUYER/Admin only. Broadening it to any seller would leak other sellers' lines and whole-order totals. |
| GET orders/{id}/timeline | List of OrderEvent | Same local buyer/Admin ownership; seller-safe timeline and notes need an explicit projection |
| PUT or POST orders/{id}/status?status= | ApiResponseOrderResponse | Local default service permission is Admin; no supported Farmer acceptance/processing mutation. `processing` is not an advertised OrderStatus value. |
| GET ecommerce/notifications?unread= | Authenticated user's NotificationResponse list | Local reads self-scope; useful as a future invalidation signal, not an authorization grant to read order details. Automatic seller order-event delivery/transaction linkage is not proven by the notification creation endpoints. |
| GET notifications/user/{userId}, /unread | Self/Admin in local NotificationService | Must bind to authenticated account and validate returned userId. Do not fetch all notifications or parse order IDs from free-text messages as authority. |

## Exact request and response contracts (captured OpenAPI, not newly verified live)

All paths above are relative to `/api/v1` and envelopes use success/message/data/error.

- OrderRequest: required buyerId (int64), addressId (int64), items; optional deliveryFee, subtotal, total. OrderItemRequest requires productId (int64), quantity (int32, minimum 1), unitPrice (number, minimum 0.01). No order idempotency key is advertised.
- Local creation locks product rows in ID order, checks ACTIVE products, own-product prohibition, current catalog price equality, minimum quantity and available stock, snapshots product name/unit/price, reserves stock and computes subtotal/total. However deliveryFee is caller-provided and only checked nonnegative; a trusted delivery quote contract is missing. No explicit Supplier->Farmer / Farmer->Buyer pair validation exists in this older creation method beyond requiring a BUYER purchaser.
- OrderResponse: id, code, buyerId, status, subtotal, deliveryFee, total, paymentStatus, addressId, items and timestamps; optional buyer name/phone/county, delivery address, driver/logistics fields, seller display metadata, payment reference/method/paidAt. **No top-level sellerId**. Ownership is item-level sellerId. Do not fabricate a sellerId from the first item or expose full-order totals as a Farmer's own revenue.
- OrderItemResponse: id, productId, sellerId, productName, unit, quantity, unitPrice, subtotal. A seller-safe collection must guarantee only the current seller's authorized items or explicitly scoped suborders, including safe totals and buyer/delivery fields.
- Order status values: placed, confirmed, in_transit, delivered, completed, disputed, cancelled. Do not invent accepted/processing/shipped status values or seller transitions.
- STK request: required orderId; optional phoneNumber, advertised Kenyan phone regex. No client amount, PIN or provider secret. STK response: paymentId, orderId, amount (KES), status, darajaResponse, createdAt. Initiation acceptance is never successful payment.
- PaymentResponse: id, orderId, method, reference, amount, status, paidAt, createdAt, updatedAt. Status values: pending, paid, held, settled, failed, refunded. Cancellation/timeout distinctions are not separate PaymentStatus values. HELD and SETTLED are distinct.
- NotificationResponse: id, userId, type, title, message, readAt, createdAt. It has no structured orderId/paymentId association in this schema. No invented push registration/socket subscription contract is needed or assumed.

## Local payment hazards requiring current-backend confirmation

`PaymentService.initiateStkPush` authorizes a seller owning any line, then calculates the outstanding **whole order** total, not just that seller's subtotal. It uses the buyer's phone unless overridden. This is collection, not paying a supplier. It calls Daraja before persisting the payment and has no durable initiation key, pending-attempt deduplication, or reconciliation-by-client-intent contract. Client single-flight cannot prevent duplicate prompts after lost responses, concurrent requests or process death. It rounds KES to an integer for Daraja while storing the original outstanding amount, requiring explicit rounding/overflow rules.

Local payment status reads delegate to buyer/Admin ownership; a seller who can request collection is not automatically allowed to read its payment status. The local SecurityConfig public callback allowlist also does not list payments/daraja/callback, despite a controller described as public. Daniel must confirm callback reachability/authenticity and reconciliation in the actual deployment. No callback probe or provider request was made.

## Current Android boundaries

- FarmerShell -> ProductRepository.orders -> CanonicalFarmService.farmerOrders deliberately throws until secure seller order access exists. FarmerOrdersRefreshController retains the last authorized snapshot and shows loading/error/empty states. Its generic load-failure message currently loses the more specific contract-unavailable reason; a connectivity retry cannot resolve the missing contract.
- `FarmerOrderResponse` is an older seller-scoped model with top-level sellerId, currency, totalAmount and items. It is not the current OrderResponse. `cropOrders` is a secondary UI filter for already authorized seller projections; it cannot safely adapt a mixed unfiltered backend order feed. There is no item sellerId in that older item model.
- BuyerOrdersApi / BuyerOrdersModel use buyer-scoped list/detail/timeline/payment reads, enforce BUYER session role and buyerId, and must not be reused for Farmers by removing the role guard. Buyer checkout itself is not currently enabled.
- Marketplace details already show real price, stock, description, images and minimum order. No cart or order/payment mutation is exposed. Preserve SUPPLIER and FARMER filters and existing auth/OTP/profile/image code.
- PaymentStatusCard / VerifiedPaymentStatus provide read-only authoritative-status presentation. There is no Android M-Pesa PIN entry, fabricated paid state or Daraja credential.

## Required backend changes/answers for Daniel

1. Restore this exact tunnel or provide an approved replacement, and supply the current source revision plus OpenAPI. Reconcile sellerId list filtering and buyer STK additions with the older supplied source. Publish role/ownership guarantees, not just route names.
2. Define FARMER as an allowed purchaser of SUPPLIER products and BUYER as purchaser of FARMER products. Bind purchaser identity to the principal; reject spoofed buyerId/sellerId, self-purchase, wrong audience/category, inactive/unapproved stock and foreign delivery addresses. Apply policy consistently to cart, quote, checkout, direct placement, history, receipt and payment.
3. Provide an atomic, functioning checkout/order placement contract with authoritative product pricing, delivery quote, totals/currency, address ownership, minimum quantity, stock reservations and a returned order ID. Confirm whether existing POST orders is self-service or remains Admin-only. Do not use the disabled void checkout as if an order was placed.
4. Make order placement durably idempotent per account/cart or request key. Provide lookup/reconciliation after uncertain responses so Android retries do not create duplicate orders. Document stock release, quote expiry and race/error semantics.
5. Confirm whether existing payments/stk-push/buyer accepts a FARMER purchaser; otherwise publish an approved purchaser payment contract. Preserve seller collection semantics of /farmer. Authorize by owned purchase/order, calculate payable amount server-side, define payer phone consent/normalization and currency/rounding, prohibit client amount overrides.
6. Persist payment intent before provider calls; serialize balance/attempt creation, deduplicate by idempotency key and existing pending attempt, return/reconcile the same intent after lost responses, and expose authorized status/history by intent/order. Confirm callbacks, amount/reference verification, uniqueness, replay/concurrency handling and missing-callback reconciliation. Document pending, terminal failure/retry and authoritative paid/escrow/settled states.
7. For incoming orders, confirm GET orders?sellerId= (and search) forces the authenticated seller and filters **inside the backend before pagination**. Return seller-only items/suborders, permitted buyer/delivery fields, seller subtotal/fees/payment allocations, status and timeline. Enforce the same scope on direct detail, events, payment and notifications. A Farmer must never download unrelated order lines or private payment details for client-side filtering.
8. Define seller-level acceptance/fulfillment transitions if intended; return allowed actions, version/conflict and duplicate-safe semantics. Mixed-seller orders need seller fulfillment state; one Farmer must not change every seller's order status. Until confirmed, Farmer views must remain read-only.
9. Generate notifications transactionally for relevant sellers/purchasers and document safe structured resource linkage or explicit refresh-only behavior. Notifications never replace order authorization. Keep system-wide/manual notification creation restricted.

## Android implementation plan once confirmed

Add purchaser and seller projections separately in the existing orders package, with Retrofit session tags and account/backend/generation checks. Reuse MarketplaceModel navigation for supply selection, introduce validated quantity/cart/review, use server quote and order creation result, then a single-flight persisted-intent payment controller and bounded lifecycle-aware status refresh. Purchase history and incoming orders must be separate tabs/sections. For incoming orders, adapt only server-scoped lines, preserve server totals semantics, add safe details/timeline/payment/delivery status and explicit retry/empty/loading views. Connect self-scoped notification reads only after resource ownership and linkage are defined.

Files affected after confirmation: marketplace/Marketplace.kt; new orders Farmer purchasing/incoming DTOs, APIs, repositories and ViewModels; data/remote/ApiClient.kt; ui/screens/FarmerShell.kt and FarmerOrdersRefreshController.kt; payments controllers using the existing status mapper. Do not change existing login/OTP/profile/image flows or retrofit the old whole-order model by fabricating fields.

## Validation scope

Add synthetic regression coverage for fail-closed Farmer order access without unfiltered requests, seller ownership filtering, canonical order and payment states, rejected/unknown/accepted STK states, and retained/null snapshots on load errors. Existing mocked refresh and marketplace tests remain applicable. Future checkout/payment tests require the confirmed contract and must cover duplicate taps, response loss, process restart, wrong owner/audience, quote/stock changes, 401/403/409/429/5xx, callback-driven states and mixed-seller privacy. Android mocks cannot prove backend enforcement.

No payment, cart, order or notification mutation implementation is enabled by this task while these critical contracts remain unconfirmed.

### Completed validation and installation

- Added `app/src/test/java/com/example/farmapp/orders/FarmerCommerceBoundaryTest.kt`: four synthetic tests covering no unfiltered-network fallback on unsupported Farmer order reads, existing seller/product scope filtering, canonical order states and buyer ownership, and payment status interpretation without fabricated success. No production application code changed in this task.
- Ran `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: BUILD SUCCESSFUL. 323 tests, zero failures/errors/skips. Lint zero errors, 51 warnings. Existing Farmer order loading/empty/error/single-flight tests and marketplace regressions also passed.
- Generated BuildConfig and APK metadata retain API base `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/` and application ID `com.example.farmapp.dev2231918adc4d4b9d`.
- Reinstalled the validated existing application APK on Samsung `RF8WC0K043H` with `adb install -r`: Success. Application data was not cleared and the app was not launched. This does **not** deliver the blocked purchasing/incoming-order features; changes here are documentation/tests only.
- APK SHA-256 remains `F0AC16EDD507B9B9909CB1ABA55B4D8040342F736A70CB153ADFA7D02F41C210` because no runtime code changed. No backend edits, login/OTP, orders, payments, status changes, commits or destructive Git operations occurred.
