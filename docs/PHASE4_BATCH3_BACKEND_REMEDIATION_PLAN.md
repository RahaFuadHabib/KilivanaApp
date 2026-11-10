# Phase 4 Batch 3 backend remediation approval plan

**Baseline correction after upstream verification.** Read `PHASE4_BATCH3_FINAL_COMPATIBILITY_VERIFICATION.md` before implementation. Upstream main a8205b0 already permits Farmer purchasers and implements quote/purchaser-payment routes absent from the older local integration branch. Its durable payment/cart/quote gaps remain. The supplied Angular main source was obtained and a missing-cart 404 is confirmed incompatible with its current checkout preparation. The branch-specific authorization work below must not be applied to main as duplicate remediation.

Prepared 8 November 2026. Status is proposed, awaiting backend implementation approval. Only this planning document is added by this task. No backend, Android production, configuration, migration or transaction activation changes are authorized by this plan.

Android evidence remains 627/627 previously verified JVM tests, 164/164 latest independent device tests and 20/20 Supplier tests. The genuine authenticated Buyer smoke is blocked and excluded from those counts. See `PHASE4_BATCH3_ACCEPTANCE_AUDIT.md` for the test evidence and historical uncertainties.

## Evidence boundary and implementation order

Java references below are relative to `C:/Users/AppleFi/AndroidStudioProjects/kilivana-shared-backend`. The inspected sibling supplier backend has the same BUYER-only ownership restriction. The recorded shared HEAD is `7296119`, supplier HEAD `2d4e045`, and historical captured contract references `a8205b0`. These identities are historical audit evidence, not a verified deployment mapping. Exact implementation paths in another deployment require comparison before approval is executed.

Java package paths such as `security/ResourceAuthorization.java` expand beneath `src/main/java/com/kilivana/backend/`. Test package paths expand beneath `src/test/java/com/kilivana/backend/`. Resource, Maven and migration paths are relative to the repository root. Line references describe the source inspected for this plan. This documentation-only task runs no application tests or network transactions.

1. Identify and retrieve the authoritative deployed build/source and schema history. Reconcile the Java differences before editing.
2. Add focused purchaser authorization and make cart reads safe under a documented client contract.
3. Implement durable payment intent and uncertain-outcome recovery with a fake provider.
4. Verify compatibility in an authorized sandbox and submit activation as a separate decision.

The inspected `CartService.checkout` at lines 147–151 currently throws ServiceUnavailableException pending transactional placement and approved delivery pricing. Searching these checkouts found neither `/checkout/delivery-quote`, `/checkout/order` nor `/payments/stk-push/buyer` implementations. Captured OpenAPI advertises those operations. They must not be substituted with direct order creation or seller STK routes.

## 1. Farmer purchaser authorization

### Confirmed source behavior

| Exact source and method | Current behavior |
|---|---|
| `security/ResourceAuthorization.java`, `buyer(Long)` lines 39–43 | ADMIN or authenticated active BUYER whose user ID matches. FARMER excluded. |
| Same class, `order`, `payment`, `cart` lines 58–59, 79–86 | Owner access delegates to buyer, propagating the exclusion to owned orders, payments and carts. Mixed-order seller participation intentionally does not grant entire-order access. |
| `ecommerce/service/CartService.java`, `getCartByBuyer`, `addItemToCart` lines 40–64 | Method security invokes buyer. Item mutations use cart ownership. |
| `ecommerce/service/OrderService.java`, `getOrdersByBuyer`, `searchOrders` lines 76–84 | Method security invokes buyer. |
| Same class, `createOrder` lines 97–103 | ADMIN-only method and explicit `existsByIdAndRole(buyerId, BUYER)` validation. Adding FARMER to a URL matcher cannot overcome either restriction. |
| `ecommerce/controller/PaymentController.java`, `initiateStkPushFarmer`, `initiateStkPushSupplier`; `ecommerce/service/PaymentService.java`, `initiateStkPush` lines 91–99 | Seller routes pass CurrentUser.id and expected seller role. Service requires sellerPayment and an owned seller line. Farmer seller route is not Farmer purchasing. |
| `security/ResourceAuthorization.java`, `sellerPayment` lines 71–76 | Actor ID and actual role must match supplied seller identity and own a seller item. |
| `security/JwtAuthenticationFilter.java`, authentication construction lines 62–73 | Principal is numeric user ID and authority is `ROLE_` plus resolved role. |
| `config/SecurityConfig.java` lines 30, 94–126 | Stateless bearer authentication and enabled method security. Remaining routes require authentication. Explicit order status/cancel/delete and administrative powers retain ADMIN restrictions. |

### Proposed smallest correction

Add an explicit `purchaser(ownerId)` predicate to ResourceAuthorization for active BUYER or FARMER with exact principal ownership, plus existing ADMIN behavior. Move only audited purchaser operations to it. Preserve `buyer` wherever its BUYER-only policy is intentional. Audit all transitive order/payment/cart uses before changing their predicates. Keep sellerPayment and mixed-order visibility rules separate.

On the authoritative purchaser order route, validate FARMER purchases against actual Supplier products, approved product state, stock, minimum quantities and own-product exclusion. Derive purchaser identity from authenticated principal and reject conflicting supplied IDs. Preserve existing BUYER eligibility and administrative direct creation. Do not broaden the inspected ADMIN createOrder method into a public purchaser entry point or add a duplicate route. If this checkout is authoritative and no purchaser route exists, implementation approval must first resolve the missing API contract.

**Proposed files** are ResourceAuthorization.java, purchaser annotations in CartService.java and OrderService.java, and the authoritative purchaser payment controller/service methods once located. SecurityConfig.java changes are conditional on an actual path restriction in that authoritative build. JwtAuthenticationFilter.java requires regression coverage, with no role impersonation or new authority mapping proposed.

**Database and migration** require no new role, role reassignment or credentials. Retain order buyer-ID foreign keys. Review whether existing database triggers or constraints restrict the BUYER role before proposing any change. Do not rewrite historical seller/buyer identities.

**Tests and acceptance** extend `src/test/java/com/kilivana/backend/security/ServiceAuthorizationTest.java` and `ecommerce/service/OrderServiceTest.java`. Add HTTP integration coverage with genuine Spring Security filters and service proxies for own Farmer success, foreign Farmer denial, BUYER behavior, ADMIN behavior, disabled accounts, wrong-role JWTs, forged buyer IDs and mixed-seller order disclosure. Verify seller STK still rejects unrelated purchasers. Authorization changes pass only after deployed sandbox tests establish the same rules.

**Client risks** include unintended mixed-order disclosure and a widened purchase catalogue for existing Buyers. Retain Buyer envelopes, errors and IDs. Replay captured Angular and Android Buyer contract tests before approving rollout. Source permission alone does not authorize Android Farmer activation.

## 2. Cart GET creates state

### Confirmed source behavior

`ecommerce/controller/CartController.getCartByBuyer` lines 49–51 maps GET `/api/v1/carts/buyer/{buyerId}` to `CartService.getCartByBuyer`. `CartCompatibilityController.getCart` lines 49–51 maps GET `/api/v1/cart`, using AuthenticationPrincipal, to the same method. The service is transactional and finds ACTIVE cart at lines 40–43. Missing cart leads to construction and repository.save at lines 44–49. This is a real write during GET. `addItemToCart` calls the same method at line 70. `ecommerce/dto/CartResponse.java` exposes cart metadata and a non-null entity mapper, without authoritative lines or totals.

### Proposed smallest correction and compatibility gate

Separate read lookup from create-for-mutation inside the existing service. Make both GET aliases side-effect-free and transactionally read-only. Retain creation in explicit add-item mutation, with a concurrency-safe find/create path. Preserve existing cart IDs and DTOs when a cart exists.

For an absent cart, propose the existing API error envelope with HTTP 404 and a documented stable missing-cart code. Clients must translate this specific result into an empty local display and may create the server cart only through explicit mutation. Other 404/auth/server failures remain errors. Do not fabricate an ID or treat every error as an empty cart. This missing-cart response is a deliberate semantic change that requires contract approval and coordinated client release. A correction cannot both avoid creating a missing cart and guarantee a persisted cart ID on that GET. If Angular cannot handle 404 yet, delay server rollout until its adapter is compatible.

**Proposed files** are CartService.java, both cart controllers, CartRepository.java, CartItemRepository.java and existing exception/error-code definitions as needed. CartResponse.java and OpenAPI annotations document the absent result. Keep both route aliases. Additional line/snapshot readback is a separate prerequisite for Farmer checkout and must be reconciled with the authoritative deployed DTO before implementation.

**Database constraints** should enforce one ACTIVE cart per purchaser and one line per `(cart_id, product_id)`, with positive quantity and foreign keys. Confirm actual DDL first. For PostgreSQL, a partial unique ACTIVE-cart index is a candidate. Explicit mutation must handle a concurrent unique-key race by rereading the winning cart. Deduplicate existing ACTIVE carts/lines only through an approved reconciliation script that preserves processing-cart history and quantities. Do not silently delete conflicting carts. Use a new migration after the authoritative schema head, without editing applied migrations.

**Tests and acceptance** add `ecommerce/service/CartServiceTest.java` and `ecommerce/controller/CartReadIntegrationTest.java` if absent. Assert zero INSERT/UPDATE/DELETE statements for repeated present and absent GETs, stable existing IDs, correct principal/foreign-owner denial and identical behavior of aliases. Run concurrent first-add tests against the real database engine. Verify quantity update, minimum/stock checks and Buyer cart continuity. Document and test exact processing-cart readback separately. Cart GET readiness requires database write-count evidence and client missing-cart contract acceptance.

**Client risks** are Angular initialization expecting a created ID, null entity mapping, cache/retry behavior and a new HTTP result. Existing Android Farmer policy already blocks cart access. It remains blocked until authoritative cart reconciliation is also verified.

## 3. STK before durable intent persistence

### Confirmed source behavior

`ecommerce/service/PaymentService.initiateStkPush` lines 91–172 is transactional. It authorizes seller ownership, subtracts successful payment sums, rounds outstanding to integer KES at line 146, calls `service/DarajaService.initiateStkPush` at line 156, then constructs and saves the Payment at line 172. The provider action occurs before the attempt row is persisted or committed. Pending attempts are not counted by the successful-payment calculation. Decimal outstanding is stored even though rounded integer KES is sent. A rollback cannot undo an STK push.

`DarajaService.java` line 49 stores pending requests in ConcurrentHashMap, which is lost at restart. `service/DarajaCallbackService.processCallback` line 54 is transactional, searches by checkout/merchant request IDs around lines 70–77, and acknowledges unknown requests around lines 79–84. It checks callbackStatus at lines 86–89 and saves RECEIVED at lines 93–95. No repository row lock/version guard or callback amount comparison was established in this path. A callback arriving before the Payment save can be acknowledged without a matching durable attempt.

`ecommerce/repository/PaymentRepository.java` has ordinary lookup methods for provider references and order IDs, without demonstrated locking. `ecommerce/entity/Payment.java` has no observed optimistic version field. `db/migrations/V15__daraja_payment_fields.sql` lines 13–18 defines ordinary provider-ID indexes and lines 26–28 a partial unique receipt index. `V17__reconciled_product_payment_fields.sql` repeats related reconciled schema changes. Neither proves the database actually deployed these constraints. A receipt uniqueness index does not prevent duplicate pending pushes.

### Proposed minimum safe transaction design

1. Authenticate and validate principal/order ownership and payable state. Under an order lock, compute server amount and reserve one unresolved intent using a client request key plus request hash.
2. Commit the durable intent before any provider call. A local save or flush inside the original transaction is insufficient.
3. Dispatch outside that transaction through a dedicated orchestrator and short transactional intent service. Avoid Spring self-invocation of REQUIRES_NEW. Persist intent states before and after dispatch. A second request returns the same resolved result, an explicit pending outcome or a payload conflict, while preserving the legacy response envelope for a confirmed initiation.
4. A provider timeout or crash during dispatch yields UNKNOWN and retains the order reservation. Automatic resend is forbidden unless provider evidence proves no action occurred. Provider idempotency cannot be assumed. A database intent alone cannot close the crash gap between provider acceptance and provider-ID persistence.
5. Reconcile through supported provider status retrieval when a provider ID exists. If no safe correlation/query is available after an uncertain dispatch, retain an explicit manual-reconciliation state. Do not release the reservation or claim exactly-once delivery without evidence.
6. Persist incoming callback evidence in a durable inbox before acknowledging it. Match known IDs and retry unmatched callbacks after provider metadata persistence. Serialize payment transitions, verify merchant/checkout identity and expected provider amount, and prevent terminal success from being downgraded. Never link callbacks using phone or rounded amount alone.

**Proposed exact existing files** are PaymentService.java, PaymentRepository.java, Payment.java, DarajaService.java, DarajaCallbackService.java, PaymentController.java and `ecommerce/controller/DarajaCallbackController.java`. Proposed new files are `ecommerce/service/PaymentIntentService.java` for short transactions, `ecommerce/service/PaymentInitiationCoordinator.java` for dispatch/recovery, and callback-inbox entity/repository files only if an existing durable inbox is absent in the authoritative build. Reuse existing payment status routes for lookup and reconciliation, extending their documented state representation compatibly. Do not introduce an alternative checkout or automatic payment flow.

**Constraints and migration** require unique `(actor_id, request_key)` with payload hash, unique non-null provider checkout ID, existing unique receipt, and an order-scoped unresolved reservation serialized by an order lock or dedicated unique reservation row. Decide whether legacy seller/purchaser initiators share one reservation for an order. They must not produce independent competing pushes. Add optimistic versioning or row locks, retry-safe callback-inbox deduplication and audit timestamps. If PaymentStatus cannot represent pre-dispatch/UNKNOWN compatibly, use a separate intent-state field/table rather than changing existing Buyer payment meanings. Audit duplicate provider IDs and unresolved legacy records first. Add nullable fields, backfill and reconcile, then constrain. New uniqueness must not fail midway against unreviewed production data.

The expected provider amount must be stored separately from outstanding balance if they differ. The smallest conservative interim policy rejects initiation requiring unsupported fractional KES rather than silently rounding and marking the full decimal order settled. The approved rounding/settlement policy must be shared by charge, callback verification and remaining balance calculations. Bound amounts and use exact conversion to prevent integer overflow. This policy is an explicit financial compatibility decision for existing Buyers.

**Tests and acceptance** extend `service/DarajaServiceTest.java` and add `ecommerce/service/PaymentIntentServiceTest.java`, `service/DarajaCallbackServiceTest.java` and database-backed `ecommerce/PaymentRecoveryIntegrationTest.java`. A fake provider must verify that a committed intent is visible from another connection before dispatch. Cover concurrent requests from both seller and purchaser routes, same key/same body, key/body conflict, pending/UNKNOWN reservation, provider timeout after acceptance, crash before provider-ID save, failed database commit, restart with empty in-memory map, callback-before-response, unknown callback replay, duplicate/concurrent callbacks, foreign IDs, wrong merchant/amount and late failure after success. Assert at most one dispatch under concurrency and zero blind resends under uncertainty. Use real database locking/constraints in integration tests. No live STK is required for this verification.

**Client risks** include changed latency, pending result interpretation, retries of legacy callers without request keys, and decimal amounts. Preserve confirmed initiation DTO fields and provider metadata mapping. Additive idempotency-key support alone cannot make old retries safe, so the shared unresolved-order reservation must cover legacy clients. Capture Angular/Android Buyer error/status expectations before choosing pending HTTP/envelope semantics. Keep seller endpoints and permissions intact while routing their execution through the same safety service.

## 4. Deployed API and revision verification

The latest audit's requests to `/api-docs`, `/v3/api-docs` and `/swagger-ui.html` returned HTTP 404 with ERR_NGROK_3200. That is evidence of an offline configured tunnel, not a missing Spring endpoint. No fresh deployment request is made by this preparation task.

`src/main/resources/application.properties` lines 70 and 76–79 exposes configurable health/info and documents `/api-docs`, `/swagger-ui.html`, API_DOCS_ENABLED and SWAGGER_ENABLED. Source settings do not prove deployment settings. No public build identity has been verified. Public OpenAPI also cannot prove runtime ownership or database constraints.

**Proposed minimum action** is operational identification before code changes. Obtain the deployed artifact/container digest, source commit, active API origin, context path and sanitized migration ledger from the authorized operator. Restore the documented tunnel or supply its approved replacement. Retrieve the real specification with read-only requests, record timestamp and SHA-256, and compare each relevant path/DTO/security rule with the artifact's source. Confirm route aliases and response envelopes. Reconcile the captured quote/purchaser routes with the absent local implementations.

If existing artifact/build-info or an authorized internal deployment record supplies identity, no application code change is required. Otherwise propose Maven build-info/git metadata and a restricted info endpoint only after approval. Candidate files are `pom.xml` and the existing management configuration. Do not expose environment values, secrets, infrastructure details or unrestricted actuator endpoints.

**Database migration** is unnecessary for identity itself. Obtain an authenticated read-only schema/constraint inventory and migration checksum ledger, including V15/V17 history, before selecting migration names or ordering. **Verification tests** must compare the deployed schema and OpenAPI against the pinned artifact, run authenticated Farmer/Buyer ownership positives and foreign-owner negatives in an authorized sandbox, and verify both Angular and Android contract fixtures. A health response alone is insufficient. **Client risk** is targeting a different origin or API version and invalidating origin-bound sessions. Never silently substitute URLs or disable origin checks.

## Approval scope and acceptance gates

| Gate | Required evidence |
|---|---|
| Authoritative baseline | Deployment artifact/commit/OpenAPI/schema ledger agree, or discrepancies are resolved and recorded before implementation. |
| Authorization | Own Farmer purchaser operations pass and foreign-owner/disabled-account tests deny access through the complete security chain. BUYER, ADMIN and seller regressions pass. |
| Cart | Both GET aliases perform zero writes, missing-cart behavior is approved by both clients, concurrent first mutation creates one ACTIVE cart, and authoritative processing-cart recovery is documented. |
| Payment durability | Committed intent precedes provider dispatch, concurrency constraints prevent competing unresolved attempts, uncertain dispatch never blindly retries, callbacks reconcile durably, and amount verification passes failure-injection tests. |
| Buyer compatibility | Existing Angular and Android payloads, response decoding, order state, errors and payment review remain compatible under approved additive changes. Contract and database integration suites pass. |
| Deployment rollout | Approved migrations pass staging rehearsal, legacy duplicates/unresolved attempts are reconciled, build identity is pinned, fake-provider failure tests pass and rollback plan preserves unresolved intents. |

Approval requested for a subsequent backend implementation against the identified authoritative repository, with the scoped files and tests above. Decisions still required are absent-cart client contract, Farmer product eligibility, fractional-KES policy, legacy pending-response semantics and payment reservation model. Approve those concrete contracts before implementation depends on them. Approval of this plan does not authorize backend deployment, real orders, M-Pesa initiation or Android transaction activation. The blocked genuine Buyer smoke and other Batch 3 acceptance conditions remain recorded separately. Batch 3 remains UNACCEPTED.
