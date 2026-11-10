# Phase 4 Batch 3 transaction design and owner decisions

8 November 2026. Status is DESIGN PROPOSED. No implementation or activation is approved. Evidence baseline is `PHASE4_BATCH3_FINAL_COMPATIBILITY_VERIFICATION.md`, with prior acceptance and implementation checklists retained. This task changes documentation only.

## 1. Confirmed baseline

| Finding | Evidence / limit |
|---|---|
| Backend canonical source baseline | `danielkamweru/Kilivana-Backend`, main `a8205b07a0f0ba93105197c6472b7e4439012025`. Read-only GitHub branch query reconfirmed this revision today. It is the proposed implementation base, pending owner designation. |
| Local integration checkout | `feature/android-backend-integration` at `7296119`, with existing untracked work. It is a divergent older branch. No branch switch, merge or reset is proposed by this design. |
| Farmer purchaser permission | Main ResourceAuthorization.buyer and buyerPayment already permit owning FARMER accounts. Do not port the older branch's restrictive authorization or add duplicate purchaser routes. Runtime permissions and product eligibility still need authorized verification. |
| Angular source | `Sennyruth/KILIVANA` main `99aa0a14f0e953d99405b45d6f34dcf726b787e5`. Cart.syncMockCartForCheckout requires GET cart success plus real ID, then clears that server cart and uploads local items. A missing-cart 404 currently breaks checkout. |
| Quote storage | Main CheckoutService uses ConcurrentHashMap, a 15-minute expiry and separate check/mark-used operations. Durable or concurrent atomic consumption is not established. |
| Payment ordering | Main PaymentService dispatches Daraja before persisting its Payment row. Pending map state is process-local. Unknown callbacks are acknowledged without demonstrated durable replay. |
| Deployment | Fresh read-only `/api-docs` and `/actuator/info` returned HTTP 404 with ERR_NGROK_3200. Tunnel is offline. Running commit, schema and OpenAPI are UNKNOWN. |
| Schema access | Previous noninteractive read-only psql connection was refused for absent credentials. Source migration definitions are evidence of intent, not proof of installed constraints. |

No backend running revision can presently be designated as matching local or upstream source. Owner must designate the implementation repository/base and supply deployment identity before rollout approval. Main is a verified source baseline, not a verified deployed baseline.

## 2. Coordinated cart contract

### Proposed steady-state contract

Preserve GET `/cart` and `/carts/buyer/{buyerId}` aliases and existing ApiResponse<CartResponse> for present ACTIVE carts. Lookups perform zero writes. Absent cart returns HTTP 404 with a stable `CART_NOT_FOUND` error code in the existing error envelope. This code is proposed and requires approval. No fabricated cart ID or implicit create is returned.

POST `/cart/items` remains an explicit mutation and atomically finds/creates the caller's ACTIVE cart. Repeated reads cannot create state. Set quantity and clear continue to enforce owned cart/line identity. One ACTIVE cart per purchaser and one line per cart/product are database-enforced. The server returns or exposes authoritative exact cart identity and lines through the approved existing cart contract, with additive DTO fields only after consumer verification.

### Angular coordination without changing Buyer product behavior

1. First release a cart-service adapter that accepts the current successful-ID response and the specifically approved missing-cart code. Preserve the same basket, checkout controls, order/payment navigation and user-visible errors.
2. For a present cart, preserve existing explicit clear/upload semantics unless separate synchronization redesign is approved. For absent cart, skip DELETE and start explicit item upload. Never clear an unknown ID or recover from arbitrary 404, 401, 403, 500 or network failure by assuming empty.
3. Empty local baskets do not initiate checkout. A partial upload fails visibly and does not automatically start checkout or repeat additive item mutations. Existing incomplete-upload recovery remains a separate contract to verify.
4. Test the frontend against both old and proposed backend responses before changing the backend. Deploy read-only missing-cart semantics only after all supported clients can handle them. Cached web builds and other clients count toward this gate.

This staged proposal preserves intended Buyer UX but changes internal synchronization handling. It cannot preserve the behavior of an unmodified legacy client that requires GET to create an ID. If old clients must remain supported, retain their current server contract temporarily and defer side-effect-free activation, or separately approve a negotiated/versioned compatibility boundary on the existing routes. Such a boundary is not assumed or implemented here. Do not silently ship 404 or add an alternative checkout endpoint.

**Acceptance tests** cover present/absent GET with database write counts, both aliases, first add, empty local basket, foreign cart denial, concurrent first creation, partial upload, refresh/restart and Angular fixtures against both server versions. Cart synchronization does not become idempotent merely because GET becomes read-only. Farmer transaction activation additionally requires safe write recovery.

## 3. Durable checkout quotes

### Proposed storage

Create `checkout_quotes` and `checkout_quote_items` through an approved forward migration. Quote has opaque UUID PK, owner ID, exact cart ID/version, delivery method and exclusive address/station/location identity, delivery snapshot, currency, subtotal/tax/delivery/total, created/expires timestamps, status, snapshot digest, version and optional resulting order ID. Item snapshot has product/seller IDs, sector/type, quantity, unit price, line amount and relevant eligibility/version evidence. All money uses agreed exact NUMERIC precision and explicit currency. The server decides the tax policy from authoritative pricing rules. No inferred tax or tariffs are introduced.

Constraints include owner/cart/order foreign keys as supported by the actual schema, positive quantities, nonnegative financial components, expiry after creation and exclusive delivery targets. Quote-item key prevents duplicate products. An order carries a nullable unique quote ID for legacy compatibility and exactly one linkage for new quote-created orders. Database timestamps and an injected Clock provide consistent expiry tests. Record history instead of deleting consumed quotes needed for reconciliation.

### Quote creation on existing `/checkout/delivery-quote`

Authenticate owner, lock/read exact processing cart with a defined version, verify product eligibility/stock/minimums and owned delivery selection, then calculate totals on the server. Freeze quote snapshots at creation. Proposed validity remains 15 minutes to match current behavior unless the owner changes it. Return the existing DeliveryQuoteResponse fields/envelope, adding fields only after compatibility review.

An optional scoped idempotency key plus canonical request hash may reuse the same unexpired quote. Legacy callers without a key may create separate immutable quotes, with bounded rate/retention policy. Quote creation does not reserve stock or dispatch payment under this proposal. Stock availability must be revalidated at order time.

### Atomic consumption on existing `/checkout/order`

Begin one database transaction, authorize owner before returning any existing result, lock the quote and cart, and inspect consumption. If this quote already produced an order and the same canonical request is repeated, return that order's ID even after quote expiry. A conflicting request returns a documented conflict. If unconsumed, reject expiry, cart version/snapshot mismatch, invalid delivery selection or changed eligibility. Revalidate prices, delivery tariff and stock. Changed payable figures require a new quote and review; do not silently charge a different total.

Acquire product locks in deterministic order across all order-placement entry paths. Reserve stock and create order/lines/history, unique quote linkage and consumed state in the same transaction. Preserve ApiResponse<Long> and never initiate payment inside this transaction. Quote uniqueness plus locking prevents duplicate order creation for repeated consumption of that quote. Optional purchaser request-key uniqueness also covers client operations across quote retries. Direct order APIs require their own approved idempotency policy and cannot bypass stock/order invariants.

Add owner-scoped outcome lookup by the approved existing order/read contract or an explicitly approved additive lookup representation. If exact quote/request linkage cannot be recovered through existing reads, that is an API design gate, not permission to invent an endpoint. Network uncertainty keeps the client pending until authoritative lookup resolves it.

**Implementation locations** are CheckoutService, CheckoutController, Cart/CartRepository, OrderService/OrderRepository and new quote entities/repositories. Remove the in-memory map only when durable reads/writes and migration are ready together. **Tests** cover restart, expiry boundaries, duplicate and concurrent consumption, changed request/cart/price/tariff, foreign owner, rejected transaction rollback and a lost HTTP response after commit. Verify exactly one order and stock reservation.

## 4. Durable payment intent and STK reconciliation

### Proposed entities and invariants

Use `payment_intents`, an order reservation and a durable callback inbox, or equivalent additions if authoritative schema already supplies them. Intent records actor/order, operation type, request key/hash, requested phone reference stored securely, expected provider amount/currency, state/version, dispatch timestamps, provider IDs, result/payment ID and reconciliation metadata. No credential material belongs in intent logs. Keep financial records and access controls consistent with approved retention requirements.

Enforce unique `(actor_id, operation, request_key)`, unique non-null provider checkout ID, unique non-null receipt and one unresolved reservation per order across seller and purchaser initiation paths. A reservation table with order ID as PK avoids ambiguity in partial-index state predicates. Intent foreign keys and version/row locks serialize state transitions. An inbox stores callback evidence and processing result with retry-safe provider-event deduplication. Do not discard distinct contradictory evidence using only checkout ID as a blanket deduplication key.

### Proposed states

| State | Entry and permitted next step |
|---|---|
| RESERVED | Short transaction validates owned payable order, exact expected amount, request hash and no competing reservation, then commits intent/reservation. No provider call yet. |
| DISPATCHING | Durable compare-and-set claim committed before the network call. One dispatcher may claim. Lost lease/crash does not prove the provider was not contacted. |
| PENDING | Provider acceptance and IDs persisted. Existing Payment/PENDING response fields remain available for compatible consumers. Await callback/status evidence. |
| UNKNOWN | Timeout, lost response or crash around dispatch. Retain reservation. No automatic new STK or automatic lease redispatch. |
| SUCCEEDED | Verified authoritative provider success and matching expected amount applied once to payment/order. Never downgrade on a later failure callback. |
| FAILED_CONFIRMED | Evidence proves rejection/cancellation/failure without settlement. Release reservation only after reconciliation rules prove a new attempt safe. |
| CANCELLED_BEFORE_DISPATCH | Atomic cancellation while still unclaimed RESERVED. No provider action occurred; release reservation. |

States are proposed internal intent states. They do not redefine existing public payment-status meanings. Callback/result HTTP contracts must preserve Angular decoding of paymentId/orderId/status/amount and normal pending review.

### Transaction boundaries

PaymentIntentService reserves under an order lock in an independently committed short transaction. PaymentInitiationCoordinator claims intent, commits DISPATCHING, then calls Daraja outside the database transaction. A short completion transaction saves provider IDs and Payment linkage. Avoid Spring self-invocation of transactional methods. The existing whole-method transaction around provider I/O must not be retained as a supposed durability solution.

A queue/outbox can schedule RESERVED work, but it must never blindly redeliver DISPATCHING/UNKNOWN work. Without a provider idempotency guarantee, the gap between acceptance and provider-ID persistence cannot be made exactly-once by local SQL. Retain explicit uncertainty and manual resolution if safe provider correlation/query is unavailable. Request-key retry returns the existing result/pending outcome, while changed payload conflicts. Legacy no-key requests are still blocked by the order reservation from creating competing attempts.

Durably persist callbacks before acknowledging them. Validate provider-supported authenticity controls, checkout/merchant IDs and exact expected amount/currency. Unmatched callback remains queued for later correlation. Reconcile under intent/payment locks in one transaction and apply settlement exactly once. Never correlate solely by phone, amount or untrusted callback claims. Unknown or invalid evidence is quarantined and does not mark an order paid.

Provider query is permitted only by its supported identifier/correlation protocol. A timeout or app termination does not release the reservation. Recovery after restart reads durable intent/inbox state. Mobile status/order reads must expose an understandable pending/reconciliation outcome through the approved compatible contract. Angular's existing retryPayment path must receive the same pending attempt rather than trigger another push.

**Owner financial decision** is fractional KES. Current source rounds provider amount but retains decimal outstanding. Proposed conservative behavior rejects unsupported noninteger charges until a policy is approved. A different approved rounding/balance policy must use the same expected amount in request, persistence, callback validation and remaining balance. Never silently mark a decimal balance settled from mismatched provider amount.

**Implementation locations** are PaymentService, PaymentController, Payment/PaymentRepository, DarajaService, DarajaCallbackService, DarajaCallbackController and new intent/coordinator/inbox repositories. Preserve seller/purchaser authorization on existing paths. **Tests** use a fake provider and real database transactions, covering competing initiators, response loss, database failure, crash windows, duplicate callbacks, callback-before-response, wrong amount, restart, contradictory late callbacks and uncertain dispatch. Assert committed intent visible from a separate connection before provider call and zero blind resends.

## 5. Authorized schema review and migration

Owner must provide an approved read-only connection/role or sanitized catalog output for the exact target. Credentials must be supplied through existing secure tooling, not chat, source or command arguments. Verify database/schema identity, table columns/types/nullability, valid indexes/constraints, applied-script checksums and aggregate duplicate/unresolved intent counts. Do not export personal rows or provider phone/account data.

Existing local process is reviewed psql SQL, not proven Flyway. `apply-reviewed-schema.psql` uses ON_ERROR_STOP, a bounded-lock transaction, identity/preflight/preservation checks and postcondition verification. Main includes additional scripts and duplicate V19 names. Reserve new filenames only after reconciling the actual target ledger and approved driver. Do not edit or rerun V15/V17/V18 or assume a numeric sequence establishes applied state.

Stage additive schema first, then reconciliation/backfill where meaningful, then constraints and durable-service rollout. Do not invent historical request keys or successful payment evidence. Stop migration on conflicting provider IDs, orphan records or unresolved duplicate reservations. For large tables, separately approve nontransactional concurrent-index phases rather than place them inside the existing single-transaction driver. Hibernate update is not the uniqueness/migration mechanism.

Rehearse on a disposable target-shaped database and retain schema/data-preservation evidence. Rollback stops new dispatch while preserving unresolved reservations and callback reconciliation. Do not roll back to a binary that ignores those reservations with payment initiation enabled. Do not drop intent/inbox evidence or assume a database restore reverses external payment activity.

## 6. Deployment identity and OpenAPI verification

Obtain the approved origin and artifact/container digest linked to a Git commit from the deployment operator. Prefer existing restricted build-info/release records. Any new revision endpoint or actuator exposure needs separate approval. Reconnect the documented specification endpoint, retrieve OpenAPI read-only, record UTC timestamp/hash/server URLs and compare cart/quote/order/payment DTOs, HTTP codes, security and route aliases against that pinned artifact. Verify actual database schema separately.

Confirm the Angular deployed artifact is built from the reviewed revision or perform a fresh comparison. Run authenticated ownership positives and foreign-owner negatives using authorized sandbox accounts. Use mocked provider behavior for transaction fault testing. A health response, public specification or matching repository main alone cannot prove live ownership permissions or database constraints.

## 7. Readiness and approval gates

| Capability | Confirmed source fact | Current gate |
|---|---|---|
| Farmer ownership permissions | Main permits purchaser Farmer roles | Runtime deployment/account/product constraints UNKNOWN; no duplicate authorization patch. |
| Missing-cart read | GET currently creates state; Angular needs returned ID | BLOCKED until coordinated client contract and legacy support decision. |
| Durable quotes | Current map and 15-minute TTL | BLOCKED until persistent snapshots, atomic consume and restart/concurrency tests. |
| Order submission | Existing numeric quote-order contract | BLOCKED pending atomic quote/order/stock idempotency and outcome lookup. Android remains disabled. |
| STK initiation | Provider call precedes durable attempt | BLOCKED pending committed intent, reservation, amount policy and reconciliation. Android remains disabled. |
| Payment review | Existing status and order-payment reads | Deployment permissions/schema UNKNOWN; uncertainty representation needs compatibility tests. |
| Deployment parity | Offline configured tunnel | UNKNOWN until artifact/OpenAPI/schema identities agree. |
| Android independent verification | 164/164 passed; Supplier 20/20 | Genuine retained Buyer smoke separately BLOCKED and excluded from passes. |

### Decisions requiring owner approval

1. Designate canonical implementation base from backend main a8205b0 and target deployment. Resolve local branch divergence without discarding work.
2. Approve missing-cart error code, coordinated Angular adapter and supported legacy-client retirement or negotiated compatibility policy. No backend behavior switch before this gate.
3. Approve durable quote schema, 15-minute TTL, no stock reservation at quote time, conflict/new-quote policy, idempotency and outcome-lookup representation.
4. Approve intent/reservation/inbox design, pending response compatibility, fractional-KES policy and manual reconciliation ownership/operating procedure.
5. Authorize read-only database inspection and subsequent migration rehearsal against the identified baseline. Deployment and activation remain separate approvals.

Implementation authorization must specify the repositories and these contracts. Acceptance additionally requires Angular/Android contract tests, database concurrency/restart/failure-injection tests and identified deployment parity. Independent test success alone does not close these gates. Batch 3 remains UNACCEPTED. No orders, STK requests, deployment changes, commits or pushes were performed.
