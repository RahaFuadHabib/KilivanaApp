# Phase 4 Batch 3 owner decision record

8 November 2026. All decisions below are **PENDING OWNER APPROVAL**. Recommendations are proposals, not recorded approvals. Evidence is in `PHASE4_BATCH3_TRANSACTION_DESIGN.md`, `PHASE4_BATCH3_FINAL_COMPATIBILITY_VERIFICATION.md` and the acceptance/checklist documents. No new investigation or implementation accompanies this record.

## D1 — Canonical implementation base and deployment gate

| Item | Decision record |
|---|---|
| Confirmed behavior | Backend main is pinned to `a8205b0`. Local integration branch `7296119` diverges. Main already permits Farmer purchasers. Running deployment revision is unknown because the configured tunnel is offline. |
| Recommended option | Designate `danielkamweru/Kilivana-Backend` main `a8205b07a0f0ba93105197c6472b7e4439012025` as implementation base in a separately approved isolated branch/check-out. Require artifact digest → source commit, OpenAPI hash and actual schema inventory to agree before deployment approval. |
| Alternatives | Select another explicitly pinned branch after reviewing its differences. Defer implementation until the deployed source is recovered. |
| Buyer impact | Contract regressions must be tested against Angular `99aa0a1` and supported Android Buyer contracts. Older branch restrictions must not be reintroduced. |
| Security/financial risk | An incorrect baseline can remove ownership rules or route payment operations through incompatible services. Repository identity alone does not verify runtime permissions. |
| Acceptance tests | Artifact/source/specification comparison and authorized sandbox Buyer/Farmer own-resource positives, foreign-owner negatives, and ADMIN/seller regressions. |
| Approval required | Owner designates repository, exact base commit, implementation location and the deployment evidence gate. Existing working-tree changes remain preserved. |

## D2 — Missing-cart contract and legacy clients

| Item | Decision record |
|---|---|
| Confirmed behavior | Server GET creates an ACTIVE cart when absent. Angular checkout requires the returned ID, clears that cart, then uploads local items. An absent-cart 404 breaks this checkout revision. |
| Recommended option | Approve HTTP 404 with specific `CART_NOT_FOUND` in the existing error envelope as the eventual read-only contract. Release Angular handling for both current success and this precise absence result first. For absence, skip clear and create through explicit item mutation. Switch backend behavior only after supported/cached clients are compatible. Preserve existing Buyer UX and present-cart responses. |
| Alternatives | Retain legacy behavior temporarily and defer Farmer cart-read activation. Approve an explicit negotiated/versioned compatibility policy for old clients before designing it. |
| Buyer impact | Internal adapter changes are required. Unmodified legacy clients cannot receive the new absence result safely. The owner must define supported versions and retirement/cache policy. |
| Security/financial risk | Blanket 404-to-empty recovery hides authorization/routing errors. Repeated additive uploads can duplicate quantities. Partial upload must not auto-start checkout or blindly retry. |
| Acceptance tests | Old/new server response fixtures, both aliases with zero-write GET evidence, first add, empty local basket, concurrent cart creation, foreign-owner errors, partial upload and refresh/restart. |
| Approval required | Owner approves absence code/status, frontend-first rollout, legacy support boundary and the condition for switching server semantics. |

## D3 — Durable quote and order-consumption policy

| Item | Decision record |
|---|---|
| Confirmed behavior | Quotes live in a ConcurrentHashMap for 15 minutes. Identity/expiry/used checks and marking used are separate operations. The quote-order response is a numeric order ID. |
| Recommended option | Retain 15-minute validity. Persist immutable owner/cart/version, item/price/currency/delivery snapshots and server totals. Reserve stock at order time. Atomically lock/validate quote and cart, revalidate stock and all payable values, create order/reservation, and consume quote in one database transaction. One quote produces one order. Same canonical request returns its existing owned order ID even after quote expiry. Changed inputs require a new quote/review. Optional scoped request key supports quote reuse and broader request reconciliation. |
| Alternatives | Approve a different TTL. Reserve stock at quote time with explicit expiry release and abandoned-reservation handling. Defer quote activation until durable reconciliation is available. |
| Buyer impact | Keep existing quote DTO fields and numeric order envelope. Price/delivery changes can require another review rather than silently replacing the quoted total. Outcome lookup must use an approved compatible representation. |
| Security/financial risk | Foreign quote disclosure, concurrent duplicate orders, stale pricing and overselling. Early stock reservation can hold inventory without purchase. |
| Acceptance tests | Restart, expiry boundary, concurrent consumption, changed payload/cart/price/tariff, foreign owner, stock contention, transaction rollback and lost HTTP response after commit. Verify one order and one stock reservation. |
| Approval required | Owner approves TTL, no quote-time stock reservation, re-quote policy, same-request idempotency, retention and owned outcome-lookup contract. |

## D4 — Payment intent, uncertainty and reconciliation ownership

| Item | Decision record |
|---|---|
| Confirmed behavior | Daraja dispatch precedes durable Payment persistence. Current pending-request state is process-local. No verified unique unresolved-order reservation or durable unmatched-callback inbox exists in the inspected paths. |
| Recommended option | Commit intent/reservation before dispatch. Enforce unique actor/operation/request key with payload hash, unique non-null provider checkout ID/receipt, and one unresolved reservation per order shared by seller/purchaser routes. Use internal RESERVED → DISPATCHING → PENDING → SUCCEEDED/FAILED_CONFIRMED states, with UNKNOWN for uncertain dispatch and cancellation only before dispatch. Persist callbacks before acknowledgement. Retain UNKNOWN reservation until provider evidence or approved manual reconciliation resolves it. Never blindly resend uncertain dispatch. Preserve existing public payment meanings. |
| Alternatives | Hold initiation disabled while reconciliation capability is incomplete. Use provider-side idempotency only if its actual guarantee is verified. A retry-until-success policy is not an approvable substitute for uncertain-outcome handling. |
| Buyer impact | Same-key retries return the existing outcome. Legacy no-key requests must reuse/block against the order reservation. Angular retry and pending presentation need contract tests and an approved pending response. |
| Security/financial risk | Double charge, callback spoofing/mislinking, duplicate settlement and manual release of a still-live attempt. Local SQL cannot guarantee exactly-once external STK after response loss. |
| Acceptance tests | Concurrent initiators, payload conflict, committed intent visible before fake-provider call, accepted request with lost response, crash before ID persistence, callback-before-response, restart, callback races/wrong amount and late failure after success. Assert zero blind resend. |
| Approval required | Owner approves reservation scope, internal states/public pending contract, manual reconciliation authority and evidence required to release an uncertain attempt. |

## D5 — Fractional-KES and exact monetary policy

| Item | Decision record |
|---|---|
| Confirmed behavior | Existing server order calculations round monetary totals to two decimal places using HALF_UP. STK converts outstanding to integer KES using HALF_UP while storing decimal outstanding. Expected provider payment can therefore differ from the recorded balance. |
| Recommended option | Retain exact BigDecimal arithmetic and existing two-decimal server quote/order calculation boundaries. Persist monetary values as approved exact NUMERIC precision/scale, proposed NUMERIC(19,2), with currency and explicit limits. Do not round again for STK. Permit STK only when the approved outstanding amount is exactly whole KES, using exact bounded integer conversion. Reject fractional-KES initiation visibly until a broader policy is approved. Callback must match the exact expected provider amount. |
| Alternatives | Approve explicit whole-KES checkout repricing, with the adjustment shown before confirmation and the revised amount persisted consistently. Alternatively retain fractional accounting with a verified provider-supported fractional settlement method. Neither option may silently mark an unmatched balance paid. |
| Buyer impact | Recommended interim policy blocks STK for fractional outstanding balances that currently round. A whole-KES repricing option changes payable totals and requires customer-facing review. No financial rounding change is automatically backward-compatible. |
| Security/financial risk | Overcharge, underpayment, residual balance, numeric overflow and inconsistent callback settlement. No binary floating-point calculation may decide server settlement. |
| Acceptance tests | Whole and fractional amounts including `.01`, `.49`, `.50`, `.99`, zero/negative values, certified provider amount limits/overflow, exact decimal round trips, quoted-versus-requested-versus-callback equality and remaining balance. |
| Approval required | Owner chooses reject-fractional versus explicit repricing/provider alternative, approves monetary scale/limits and any adjustment treatment. No implicit tax, fee, write-off or customer credit is authorized. |

## D6 — Database access, migration rehearsal and rollback

| Item | Decision record |
|---|---|
| Confirmed behavior | Live schema inspection lacked authorized authentication. Existing local process uses guarded reviewed psql scripts and preservation checks. Flyway is not established. Main contains additional and duplicate-numbered scripts, so filenames cannot establish deployed history. |
| Recommended option | Authorize least-privilege read-only inspection of the exact target and a disposable target-shaped rehearsal. Review schema/index validity, applied-script evidence and aggregate duplicate/unresolved records. Approve additive forward SQL with identity checks, bounded locks and preservation/postcondition checks. Reconcile duplicates before constraints. Stop dispatch for rollback while retaining intents, reservations and callback processing. |
| Alternatives | Operator supplies sanitized catalog and script-checksum evidence instead of agent connection access. Defer migration. For large tables, approve separately phased concurrent indexes rather than placing them inside the single-transaction driver. |
| Buyer impact | Migration locks/maintenance and application/schema rollout must be coordinated. Old binaries must not resume initiation if they ignore new reservations. |
| Security/financial risk | Wrong-database DDL, data loss, duplicate constraints failing midway, stranded payments and rollback erasing reconciliation evidence. A backup cannot reverse provider actions. |
| Acceptance tests | Target identity rejection, duplicate/orphan preflight, concurrency constraints, interruption/rollback rehearsal, before/after preservation checks, old/new binary schema compatibility and unresolved-intent recovery. |
| Approval required | Owner authorizes target/access method, rehearsal and later migration window separately, approves reconciliation responsibility and rollback restrictions. Credentials stay in approved secure tooling. |

All six decisions remain pending. Implementation approval, deployment approval and Farmer order/M-Pesa activation approval are separate. Farmer order submission and M-Pesa initiation remain disabled. Batch 3 remains UNACCEPTED.
