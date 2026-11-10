# Backend remediation implementation review

Use `PHASE4_BATCH3_TRANSACTION_DESIGN.md` for the proposed steady-state contracts, durable quote/intent state models, migration gates and owner decisions. All implementation and deployment items remain subject to separate approval.

**Superseding compatibility evidence.** `PHASE4_BATCH3_FINAL_COMPATIBILITY_VERIFICATION.md` pins actual Angular and backend main source. Main already includes Farmer purchaser authorization and checkout routes absent from the local integration branch. Angular checkout requires a successful server cart ID, so the missing-cart 404 change is incompatible until coordinated frontend adaptation. Main also contains additional and duplicate-numbered migration scripts. Reconcile the canonical baseline and target catalog before applying the branch-specific checklist below.

8 November 2026. Review outcome is **conditionally scoped, not approved for implementation**. This document refines `PHASE4_BATCH3_BACKEND_REMEDIATION_PLAN.md`. Batch 3 remains UNACCEPTED. Farmer orders and M-Pesa remain disabled.

## Current baseline and change protection

Read both the remediation plan and acceptance audit, then reinspected Spring source, repositories, SQL scripts and local migration reports. Shared backend HEAD is `7296119`; supplier backend HEAD is `2d4e045`. Shared backend has untracked integration documents, schema-review scripts and `ProductRepository.java.bak`. Supplier backend status was clean. Android has substantial pre-existing tracked and untracked changes, including the audit, tests and planning work. Those files were preserved. No backend, schema, application configuration or Android production file changed during this review. Only this checklist is added. No tests, migrations or network/payment operations were executed.

All Java paths in this document expand under `../kilivana-shared-backend/src/main/java/com/kilivana/backend/`, and test paths under its `src/test/java/com/kilivana/backend/`. SQL/resource paths are relative to that backend root. Changes must target the subsequently verified authoritative repository, not both sibling copies automatically.

## 1. Resolve baseline before implementation

- [ ] Obtain deployed artifact digest, Git revision, active origin/context path and read-only schema inventory. Current local source is not proven to match deployment. Historical OpenAPI references `a8205b0`.
- [ ] Locate the Angular source revision actually deployed. Searches in sibling AndroidStudioProjects, temporary Kilivana locations, source, Desktop, Documents, Downloads and git_session did not locate current Angular cart TypeScript. The old audit's source descriptions cannot verify present compatibility.
- [ ] Resolve why captured `/checkout/delivery-quote`, `/checkout/order` and `/payments/stk-push/buyer` contracts are absent from the inspected Java tree. Do not invent implementations, substitute routes or enable the ADMIN direct-order path for purchasers.
- [ ] Record baseline Git status/file hashes in the chosen repositories and preserve uncommitted work. Re-read any applicable AGENTS instructions there before edits.

## 2. Exact Farmer authorization change set

Confirmed `security/ResourceAuthorization.buyer(Long)` lines 39–43 permits matching BUYER or ADMIN. `order`, `payment` and `cart` transitively inherit it. `ecommerce/service/CartService.getCartByBuyer` and `addItemToCart` use buyer at lines 41 and 63. `ecommerce/service/OrderService.getOrdersByBuyer` and `searchOrders` use buyer at lines 76 and 81. `OrderService.createOrder` lines 98–103 additionally requires ADMIN and validates a BUYER entity. The source-wide buyer-predicate/BUYER-validation search identified those direct call sites.

- [ ] Add explicit `purchaser(ownerId)` in ResourceAuthorization with active BUYER/FARMER exact principal ownership and reviewed ADMIN behavior. Retain buyer for intentionally narrower policies.
- [ ] Apply purchaser to the two cart service entry methods and the two purchaser history methods. Audit `order`, `payment`, `cart`, confirmReceipt, timeline and order-item consumers before redirecting their transitive ownership checks. Preserve mixed-order seller isolation and administrative lifecycle restrictions.
- [ ] Resolve whether an ADMIN creating an order for a Farmer is in scope. Change the BUYER-only entity validation only if approved, while preserving ADMIN authorization. Do not merely remove method security.
- [ ] Locate authoritative purchaser order/payment services and enforce own purchaser identity there. Farmer purchases require server-side Supplier/eligible-product/stock/minimum/own-product validation. Do not repurpose `PaymentController.initiateStkPushFarmer` or `ResourceAuthorization.sellerPayment`; those are seller operations.
- [ ] Preserve JWT role construction in `security/JwtAuthenticationFilter` and the ADMIN order lifecycle matchers in `config/SecurityConfig`. Authentication at the URL layer alone is insufficient.
- [ ] Extend `security/ServiceAuthorizationTest.java`, `ecommerce/service/OrderServiceTest.java` and add HTTP integration tests covering real method-security proxies, valid Farmer own reads, foreign-owner denials, disabled account denial, conflicting supplied IDs, mixed-seller disclosure and unchanged Buyer/Admin/seller behavior.

No role-data migration is proposed. The deployment database may have additional role checks/triggers and must be inspected. Approval requires ownership tests to pass against the authoritative source and authorized sandbox.

## 3. Angular missing-cart compatibility gate

Confirmed GET paths remain `CartController.getCartByBuyer` at lines 49–51 and `CartCompatibilityController.getCart` at lines 49–51, both calling transactional `CartService.getCartByBuyer`. The service saves a new ACTIVE cart at lines 43–49. CartResponse supplies metadata and maps an entity, without a missing-cart representation. `CartService.checkout` currently throws ServiceUnavailableException rather than creating an order.

**Angular compatibility is UNKNOWN. The proposed 404 contract is not approved as backward-compatible.** No server missing-cart response change may ship before these checks pass.

- [ ] Inspect actual Angular cart service, global HTTP interceptors, cart store, route resolvers, add/update/remove actions and checkout services. Record exact paths and envelopes, whether cart ID is required before first mutation, handling of 404/data:null, line ID versus product ID usage and automatic retry/fallback behavior.
- [ ] Add Angular HTTP tests for present cart, absent cart, first add without a pre-created cart, repeated GET, foreign-owner denial, offline/500 and return from order placement. Only the exact documented missing-cart outcome may render empty. Assert no add or order side effect from read/retry.
- [ ] Select and approve the absent-cart contract jointly. 404 plus stable missing-cart code is the plan's candidate, not a verified current-client capability. Reject fake IDs or blanket error-to-empty conversion.
- [ ] Split read-only lookup from mutation-only create in CartService. Update both existing controllers, `CartRepository`, `CartItemRepository`, error code mapping and OpenAPI annotations together. Retain existing IDs/envelopes for present carts and both route aliases.
- [ ] Require one ACTIVE cart per purchaser and one line per cart/product with positive quantity. Preflight existing duplicates. Resolve them explicitly without deleting processing history. Handle concurrent first-add uniqueness races transactionally.
- [ ] Add `ecommerce/service/CartServiceTest.java` and `ecommerce/controller/CartReadIntegrationTest.java`. Assert actual zero database writes for both GET aliases, absent/present paths, stable identity, authorization and concurrent first-add behavior.

**Rollout risk** is breaking old Angular cart initialization or confusing line IDs. Release compatible client handling before the server behavior changes, with a defined minimum client contract. If rollout is halted, disable affected checkout capability through an approved operational control. Do not blindly restore GET-side writes merely to hide a contract error. Keep newly created cart history and constraints intact until reconciled.

## 4. Payment transaction and reconciliation review

Confirmed `PaymentService.initiateStkPush` lines 91–172 performs provider dispatch at line 156, then saves the Payment at line 172, all within one transaction. Flushing earlier in that transaction does not create a durable committed intent. The successful-payment balance calculation ignores pending attempts. The integer provider amount is rounded while stored outstanding is decimal. `DarajaService` uses a process-local ConcurrentHashMap for pending requests.

`DarajaCallbackService.processCallback` is transactional. It acknowledges an unmatched callback without a durable inbox entry, checks RECEIVED/PROCESSED in memory, saves RECEIVED and later updates payment/order. PaymentRepository declares no locking or optimistic-version behavior. A receipt unique index supplies no concurrent pending-request guarantee. The callback catch block saves FAILED and acknowledges, which also requires failure-injection testing. A pending intent cannot guarantee exactly-once external STK when acceptance occurs before provider IDs are recorded.

- [ ] Introduce an independently committed intent/reservation in proposed `ecommerce/service/PaymentIntentService.java`. Execute provider dispatch outside that transaction via proposed `PaymentInitiationCoordinator.java`. Avoid transactional self-invocation and a long-held order lock during network I/O.
- [ ] Reuse PaymentController entry paths and confirmed DTOs. Coordinate seller and purchaser routes through the same order-level unresolved reservation. Decide legacy no-key behavior and pending/error response contract using Angular/Android fixtures before implementation.
- [ ] Store request key, canonical payload hash, expected provider amount/currency, actor/order identity, state and recovery evidence. Same key/same body reuses the outcome. Different body conflicts. No key may bypass the unresolved-order reservation.
- [ ] A crash or timeout during dispatch retains UNKNOWN. Provider status retrieval is used only where a supported provider ID/correlation exists. Without one, require explicit reconciliation evidence and prohibit automatic resend. A worker/outbox must not blindly redeliver uncertain external requests.
- [ ] Persist callbacks before acknowledgement in an existing or new durable inbox. Serialize state transitions, replay unmatched callbacks after metadata persistence and validate provider IDs, merchant identity and exact expected provider amount. Callback identity checks alone do not establish authenticity. Confirm the provider's supported callback authentication/verification controls before choosing them.
- [ ] Decide fractional-KES and outstanding-balance policy. Reject unsupported amounts conservatively until approved, use bounded exact conversion and avoid marking a decimal balance fully settled from an unchecked rounded payment.
- [ ] Modify exact existing files `ecommerce/service/PaymentService.java`, `ecommerce/entity/Payment.java`, `ecommerce/repository/PaymentRepository.java`, `ecommerce/controller/PaymentController.java`, `service/DarajaService.java`, `service/DarajaCallbackService.java` and `ecommerce/controller/DarajaCallbackController.java`. Add callback inbox entity/repository only if none exists in authoritative source. Keep internal intent states separate from legacy payment status meanings where necessary.
- [ ] Extend `service/DarajaServiceTest.java`; add `ecommerce/service/PaymentIntentServiceTest.java`, `service/DarajaCallbackServiceTest.java` and database-backed `ecommerce/PaymentRecoveryIntegrationTest.java`.

Required tests cover committed intent visible from a second connection before fake-provider dispatch, competing initiators, key conflicts, pending reservations, provider acceptance followed by lost response, crash before ID save, callback before response, duplicate/concurrent callbacks, unknown callback replay, database failure, restart, wrong amount/merchant and late failure after success. Assert no blind resend and no duplicate settlement. Use the target database engine, real transaction proxies and a fake provider.

## 5. Correct migration strategy

The plan requires refinement. `pom.xml` has no observed Flyway/Liquibase integration. `application.properties` line 53 uses Hibernate `ddl-auto=update`. Numbered SQL filenames therefore do not establish an automated applied-version ledger. V17/V18 are guarded review scripts that reject execution outside the approved schema driver. V17 explicitly warns against applying V15 alongside the reconciled baseline. `docs/integration/16-local-migration-backend-verification.md` records an approved local V16–V18 application using `docs/integration/schema-review/apply-reviewed-schema.psql`, with transaction, lock bounds, preflight and preservation checks. This historical local result does not establish deployment schema state.

- [ ] Obtain fresh read-only catalog evidence for target database identity, columns, types, constraints, valid indexes and duplicate/unresolved payment counts. Inspect migration approval/execution records. Do not assume the current database matches the historical empty-order/payment inventory.
- [ ] Use a new reviewed forward SQL script and matching preflight/verification/preservation/rollback rehearsal files under the existing schema-review process. Reserve its numeric name only after confirming authoritative head. V19 is merely the next local candidate, not an approved migration number. Do not edit or rerun V15/V17/V18.
- [ ] Add nullable intent/provider fields or separate intent/reservation tables first. Reconcile legacy rows and duplicate non-null checkout IDs before uniqueness. Never fabricate provider IDs, request keys or receipts for historical rows.
- [ ] Enforce unique scoped request key, unique non-null provider checkout ID, existing non-null receipt uniqueness, and one unresolved order reservation. A dedicated reservation table keyed by order ID is the smallest predictable model when mutable status predicates are unclear. Row locking serializes reserve/release and amount computation. A boolean check in Java alone is insufficient.
- [ ] Add callback inbox deduplication based on verified provider identity/event semantics. Do not discard distinct evidence merely because two callback payloads share a checkout ID. Keep invalid/mismatched callbacks available for review without applying payment state changes.
- [ ] Use explicit check/foreign-key constraints consistent with the authoritative schema and intent-state model. Choose versioning/locking and test concurrent transitions. Preserve receipt index and existing order data.
- [ ] Rehearse on a disposable database matching target structure with duplicates, legacy pending rows and interruption injection. Record before/after data fingerprints and verify all indexes/constraints. Choose bounded transactional index creation for small tables, or separately approved concurrent index phases for large live tables. PostgreSQL concurrent index creation cannot be placed inside the single transaction used by the existing driver.
- [ ] Do not rely on Hibernate update to install partial uniqueness. Any deployment change to schema-management mode requires separate approval and startup compatibility verification.

**Rollback** uses additive schema and app compatibility first. Once real provider work exists, retain intent, reservation and inbox data and keep reconciliation running. Stop new dispatch before rollback. An old binary that ignores reservations cannot safely resume initiation. Reverting code must not drop constraints or release UNKNOWN reservations. Destructive schema reversal is permitted only after proving no new-state records/provider obligations remain and obtaining approval. Backups cannot reverse external provider actions.

## 6. Deployment and final approval checklist

- [ ] Recover authorized deployment access and the documented specification endpoint. Historical 404/ERR_NGROK_3200 establishes an offline tunnel, not endpoint removal. Capture specification hash and source/artifact identity together.
- [ ] Verify new purchaser permissions with real JWT authentication and owned/foreign-resource requests in an authorized sandbox. Keep ADMIN and seller powers intact.
- [ ] Verify cart GET write counts and Angular/Android Buyer contracts against the same build and schema. Preserve distinct quote-order, direct-admin-order and seller-payment operations.
- [ ] Verify migration rehearsal, rollback compatibility and all fake-provider uncertainty tests before any deployment approval.
- [ ] Establish monitoring using redacted intent IDs/states and aggregate unresolved counts, with an operational owner for reconciliation. Define stop conditions for duplicate intents, callback mismatch, unexpected HTTP/DTO errors and unresolved-state growth.
- [ ] Approve backend implementation separately from deployment and separately from Android transaction activation. An accepted implementation plan is not activation approval.

Open approvals are the authoritative repository/build, current Angular source and missing-cart contract, Farmer product eligibility, fractional-KES policy, legacy pending-response semantics and migration execution strategy for the real target. Until these gates are resolved, the implementation scope is a reviewed proposal. Independent Android tests passing does not establish backend transaction readiness, deployed revision parity or genuine Buyer smoke completion. Batch 3 remains UNACCEPTED.
