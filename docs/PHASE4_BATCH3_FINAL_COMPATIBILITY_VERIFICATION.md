# Final pre-implementation compatibility verification

8 November 2026. Read-only source/network review. Backend implementation is not approved. Only documentation and an ignored build-directory revision marker were written. No production code, SQL, deployment configuration, authentication data or transaction state changed.

## Pinned source evidence

| Source | Verified identity |
|---|---|
| Supplied Angular repository | `https://github.com/Sennyruth/KILIVANA`, default branch main, HEAD `99aa0a14f0e953d99405b45d6f34dcf726b787e5`. Primary application is `Client/src`, excluding the separate checked-in `.kilo/worktrees` copy. Client/AGENTS.md was read. Deployment identity remains unknown. |
| Backend upstream | `https://github.com/danielkamweru/Kilivana-Backend`, default main HEAD `a8205b07a0f0ba93105197c6472b7e4439012025`, verified through GitHub API and raw source at that commit. This matches the historical captured contract's source revision. It does not prove the running deployment uses it. |
| Local shared checkout | `../kilivana-shared-backend`, origin matches upstream, branch `feature/android-backend-integration`, HEAD `72961190a86e1cb69b2eb766db13b07c85142ecc`. Untracked integration plans/scripts and ProductRepository.java.bak remain present. |
| Local supplier checkout | `../kilivana-supplier-backend`, same origin, branch `feature/supplier-api-updates`, HEAD `2d4e045`; inspected status clean. Upstream branch now points to `bb48bdc340799adfd382d8b10347013678dd461b`, so the local branch is not current upstream evidence. |

No fetch, checkout, reset, merge, commit or push was performed. Remote files were read at pinned revisions. Existing working-tree modifications were preserved. There is no confirmed deployed checkout yet. Upstream main is the canonical repository baseline for this comparison, while repository selection for actual implementation still needs approval.

## Angular cart, checkout, orders and payments

All following Angular paths are relative to `Client/` at the pinned revision.

`src/app/core/services/cart.ts` lines 27–47 loads its localStorage basket and optionally reloads product images. Lines 50–122 modify the local basket. Missing local storage yields an empty local basket with `mock-cart` identity at lines 172–183. This display behavior does not establish a server missing-cart contract.

`Cart.syncMockCartForCheckout` lines 124–156 explicitly GETs ApiEndpoints.CART.GET, requires success/data and a string/number ID, DELETEs the server cart by ID, then serially POSTs local items. A missing object or ID throws. HTTP failure propagates. `src/app/core/services/http.ts` delegates requests to HttpClient; `src/app/core/interceptors/auth-interceptor.ts` lines 49–60 handles 401 refresh and rethrows other HTTP errors. There is no inspected 404-to-empty translation on this path.

`src/app/shared/api.config.ts` lines 39–51 maps GET `/cart`, DELETE `/carts/{id}`, POST `/cart/items`, POST `/checkout`, POST `/checkout/delivery-quote` and POST `/checkout/order`. `src/app/core/services/cart.spec.ts` lines 164–195 expects successful GET data `{id:17}`, followed by clear and add. It supplies no absent-server-cart acceptance coverage.

`src/app/features/orders/checkout/checkout.ts` lines 259–303 runs cart synchronization, starts checkout, creates a delivery quote, submits quoteId/phoneNumber and expects a numeric order ID. It then clears the local basket and calls purchaser payment automatically at lines 289–293. Cart-preparation errors terminate this chain. `src/app/core/services/order.ts` exposes separate direct order creation at line 146 onward. Its presence does not replace the quote-based primary checkout sequence.

`src/app/core/services/payment.ts` lines 27–46 initiates the selected STK route; lines 49–68 retrieve status/order payments; lines 81–106 poll with a bounded attempt count. `checkout.ts` lines 351–361 permits payment retry using component pendingOrderId and starts the buyer route again. No durable client idempotency key or persisted uncertain-attempt journal was observed in those inspected paths. This is not evidence that retry is safe.

**Compatibility finding is confirmed.** Replacing missing server-cart GET creation with HTTP 404 breaks the inspected Angular checkout synchronization, even while the local cart display appears normal. A coordinated frontend correction must distinguish only the approved missing-cart code, create through explicit first mutation, and avoid clearing an unknown cart. Retain ordinary failures as failures. Approve and test first-add/empty-cart behavior, cart identity and retry/recovery before server rollout. No frontend or backend correction was implemented and no Angular tests were executed during this read-only review.

Pinned source links are [cart service](https://github.com/Sennyruth/KILIVANA/blob/99aa0a14f0e953d99405b45d6f34dcf726b787e5/Client/src/app/core/services/cart.ts), [checkout component](https://github.com/Sennyruth/KILIVANA/blob/99aa0a14f0e953d99405b45d6f34dcf726b787e5/Client/src/app/features/orders/checkout/checkout.ts), [payment service](https://github.com/Sennyruth/KILIVANA/blob/99aa0a14f0e953d99405b45d6f34dcf726b787e5/Client/src/app/core/services/payment.ts) and [cart tests](https://github.com/Sennyruth/KILIVANA/blob/99aa0a14f0e953d99405b45d6f34dcf726b787e5/Client/src/app/core/services/cart.spec.ts).

## Canonical Java differs from the older local integration branch

Backend Java package references expand under `src/main/java/com/kilivana/backend/` at upstream main `a8205b0` unless explicitly labeled local.

`security/ResourceAuthorization.buyer` lines 41–45 permits own BUYER, FARMER and SUPPLIER accounts, plus ADMIN. `buyerPayment` lines 102–106 checks actual purchaser identity/role and order buyer ID. `ecommerce/service/PaymentService.initiateStkPushForBuyer` lines 124–135 uses that predicate and rechecks owned order identity. `OrderService.createOrder` overloads at lines 110–149 authorize buyer or admin. These facts supersede any general claim that canonical main excludes Farmer purchasers. The BUYER-only/ADMIN-only findings remain valid for the older local integration branch only. Do not implement duplicate authorization changes against main based on those branch-specific findings. Product eligibility and actual deployed JWT behavior still require verification.

Main contains `ecommerce/controller/CheckoutController`, `ecommerce/service/CheckoutService`, and quote/order DTOs. CheckoutController lines 27–35 define `/delivery-quote` and `/order`, returning the expected quote and numeric-ID envelopes. CheckoutService lines 40–54 sets a 15-minute TTL and stores QuoteRecord objects in ConcurrentHashMap. Lines 127–131 create a new quote record. Lines 161–165 check identity, usage and expiry. Lines 196–202 create the order then mark the map record used. This is process-local state with separate check/use operations; durable restart recovery and atomic concurrent consumption are not established. Database order transaction rollback cannot atomically roll back an independent in-memory map mutation.

`CartService.getCartByBuyer` on main lines 40–49 still creates and saves a missing ACTIVE cart on GET. Therefore the cart-side-effect blocker remains confirmed against main, not merely the old branch.

## Payment persistence, constraints and uncertainty

Main `PaymentService` seller and buyer initiation methods are transactional and converge on common initiation logic. It computes outstanding from successful payments at lines 161–168, rounds to integer KES at line 196, calls Daraja at line 206 and saves the Payment at line 222. Pending attempts do not establish an order-level exclusion guarantee. Provider action still precedes durable payment-attempt commit. A provider success followed by timeout, process failure or failed database commit can leave an external action without durable local evidence. No safe automated resend is established.

Main `Payment` and `PaymentRepository` inspection found callbackStatus and provider-ID lookups without a demonstrated version field, row lock, request-key uniqueness or unresolved-order reservation. `DarajaService` maintains process-local pending requests. `DarajaCallbackService.processCallback` at line 54 is transactional, queries checkout/merchant IDs, acknowledges unknown payments, checks RECEIVED/PROCESSED, writes RECEIVED and later saves final payment/order states. It has no observed expected callback amount comparison. Unknown callback acknowledgement lacks a verified durable replay inbox. Status checks alone do not prove atomic concurrent callback exclusion.

Main `db/migrations/V19__payment_status_check_and_lookup_indexes.sql` widens payment status checks and adds ordinary checkout/merchant/phone lookup indexes and a partial unique non-null receipt index. It does not enforce unique pending attempts or unique client request keys. Main also contains `V19__seller_product_lifecycle.sql`, so numeric migration filenames alone are not a globally unique automatic migration history. Do not reserve V19 for new work based on the old local branch.

An attempted read-only psql transaction against local `127.0.0.1:5432/kilivana`, using existing noninteractive authentication only, was refused with `fe_sendauth: no password supplied`. No password was sought in files, printed or fabricated. Consequently **live local and deployed schema columns, index validity, uniqueness, duplicate rows and migration application remain UNKNOWN**. Source DDL is confirmed; actual database application is not.

## Reviewed SQL and deployment process

The local branch has no observed Flyway/Liquibase integration and configures Hibernate ddl-auto=update. Its reviewed `docs/integration/schema-review/apply-reviewed-schema.psql` uses ON_ERROR_STOP, a transaction, five-second lock timeout, identity/preflight checks, before/after preservation data and explicit verification before COMMIT. Historical `16-local-migration-backend-verification.md` records V16–V18 applied to an approved local database through that driver. V17/V18 have execution guards and V17 warns against combining historical V15 with that reconciled baseline. These records establish the existing local reviewed process, not an automatically runnable deployment pipeline or current production schema.

Required migration preparation is a fresh authorized read-only target catalog and applied-script inventory, then a separately reviewed forward script with preflight/rehearsal/preservation/verification. Reconcile duplicates before unique provider IDs, scoped request keys or unresolved-order reservations. Do not edit historical scripts or rely on Hibernate to enforce partial indexes. Deployment build/release process ownership remains unverified.

## Deployment checks and compatibility matrix

Fresh read-only `/api-docs`, `/swagger-ui.html` and `/actuator/info` requests to the configured ngrok origin each returned HTTP 404 with ERR_NGROK_3200. The tunnel is offline. No running revision or deployed specification can be identified from that error. A matching upstream commit and historical captured OpenAPI hash still do not prove current deployment parity.

| Operation/property | Source compatibility | Readiness / outstanding evidence |
|---|---|---|
| Farmer purchaser authorization | CONFIRMED supported on canonical main; restricted on old local branch | UNKNOWN in deployment. Verify actual artifact, token authority and ownership negatives. |
| Angular local cart display | CONFIRMED localStorage-backed | Does not validate server cart safety. |
| Server cart GET | CONFIRMED creates ACTIVE cart on main | BLOCKED for side-effect-free Farmer read. |
| Missing-cart GET → 404 | CONFIRMED incompatible with current Angular checkout preparation | BLOCKED until coordinated frontend contract/tests are approved. |
| Quote creation/consumption | Wire paths align; 15-minute process-local quotes | BLOCKED for durable/concurrent/restart-safe transactions. |
| Quote order request/result | Angular quoteId/phoneNumber → numeric ID aligns with controller | BLOCKED pending durable quote consumption, idempotency and uncertain-outcome lookup. |
| Purchaser STK | Buyer/Farmer purchaser route exists on canonical main | BLOCKED by provider-before-persistence, uncertainty, duplicate and amount verification gaps. |
| Payment status reads | Angular and canonical service paths exist | Deployed ownership/schema/runtime compatibility UNKNOWN. |
| Payment constraints | Receipt uniqueness appears in source SQL | Actual catalog/duplicate state UNKNOWN. No observed source request-key/pending uniqueness guarantee. |
| Deployed revision | Tunnel unavailable | UNKNOWN. Need artifact digest/commit, OpenAPI and schema evidence together. |

## Exact requirements before authorizing implementation

1. Select the canonical repository/branch/commit for backend implementation. Prefer the reviewed main baseline or an explicitly chosen derivative, and resolve the older local branch divergence without discarding uncommitted work.
2. Confirm which storefront revision is deployed. Approve coordinated Angular missing-cart behavior and tests before changing server GET responses. Preserve current checkout route/DTO distinctions.
3. Provide authorized read-only target schema access or sanitized catalog/index/applied-script evidence. Approve a forward migration strategy against that exact baseline, including duplicate reconciliation and rollback that retains uncertain intents.
4. Approve durable intent/reservation, no-blind-retry UNKNOWN outcomes, callback inbox/reconciliation and exact provider amount policy. Verify via fake-provider concurrency/failure-injection tests before any dispatch activation.
5. Recover an authorized deployment endpoint and build identity, compare its Java/OpenAPI/schema, and verify authenticated Farmer/Buyer ownership in a sandbox. Public source alone is insufficient.

The genuine Buyer smoke remains separately BLOCKED and excluded from the passing 164 independent instrumentation tests. No transaction activation is authorized by this review. Batch 3 remains UNACCEPTED.
