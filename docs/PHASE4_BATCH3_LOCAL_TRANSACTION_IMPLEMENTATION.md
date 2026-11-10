# Phase 4 Batch 3 local transaction implementation

## Acceptance and scope

Batch 3 remains UNACCEPTED. Local code implementation is authorized by the latest owner instructions. Deployment, production migration, financial calls and commits remain outside that authorization. Earlier documentation statements that no implementation exists describe historical checkpoints.

Canonical backend checkout is `backend-transaction-readiness`, branch `main`, baseline `a8205b07a0f0ba93105197c6472b7e4439012025`. Existing Android changes and other backend checkouts were preserved. No Buyer Android production files were changed for this implementation.

## Environment verification

The HTTP backend is offline. Neither port 8080 nor the new loopback test-profile port 18089 had a listener during verification. No HTTP application server was started. Integration verification uses a Spring service/JPA context and real disposable PostgreSQL, with provider transport mocked.

The existing PostgreSQL 18 instance remains available at `127.0.0.1:55439`, bound exclusively to `127.0.0.1`, with synthetic role `commerce_test`. Its verified data directory is `backend-transaction-readiness/target/commerce-test-pg`. No replacement cluster was necessary. Integration tests check database name, user, port and data-directory identity before allowing Hibernate create/drop. They never read application datasource credentials.

`commerce_test` is the disposable integration database. `commerce_migration_final_test` is a separately created synthetic migration-rehearsal database. Production and shared databases were not inspected or modified. The disposable cluster is left running for follow-up testing.

The new `application-commerce-local-test.properties` binds HTTP to loopback port 18089, uses only the disposable database, requires schema validation and sets external dispatch false. Integration tests create/drop their disposable schema, so this profile cannot currently be assumed bootable against that emptied database. A reviewed complete baseline schema must be provisioned before an HTTP startup rehearsal. The minimal migration fixture is deliberately insufficient for full application startup.

## Implemented behavior

### Cart, durable quotes and orders

The existing `POST /checkout/validate` accepts an optional keyed preparation body. Bodyless Angular behavior remains supported. Preparation validates exact product quantities and atomically replaces the authenticated purchaser's active basket. A user lock serializes preparation and cart creation. Existing checkout-processing baskets require reconciliation before another preparation.

Legacy cart GET creation remains intact because Angular requires a real cart ID. Cart changes validate inventory/minimum quantities and prevent mutation of a processing cart. Database constraints additionally reject duplicate live carts and duplicate product lines.

Quotes persist in `commerce_operations`, retaining the existing 15-minute lifetime and existing fee calculations. Price/quantity/delivery snapshots, expiry and request hashes are durable. Optional request IDs replay the same quote and reject conflicting payloads. Server-side order consumption locks the quote, cart and products, rechecks ownership, delivery evidence, price, inventory and totals, then commits the real order and consumed-quote mapping together. Exact repeat consumption returns the same order, including after quote expiry. A changed consumption payload fails.

Farmer authorization already exists in canonical controller/security rules. Additional item validation permits Farmer purchases only from Suppliers, preserving the requested purchasing boundary. No obsolete authorization changes from the older branch were applied. The Farmer flow uses `/checkout/order`. Direct legacy `/orders` creation is not being claimed as globally idempotent.

### Durable payment reservation and reconciliation

The existing seller/purchaser initiation routes now use a shared coordinator. A committed payment row, immutable expected KES amount, intent and unique per-order reservation precede any provider call. Exact keyed retries reuse the intent. Legacy callers without keys reuse the outstanding order reservation. Unresolved legacy payment rows block additional initiation.

`RESERVED -> DISPATCHING` commits before transport. Only an unclaimed RESERVED intent can automatically dispatch. Active account/verification/ownership and payable order state are revalidated at claim. Accepted provider IDs and the actual acceptance response persist before returning. Replays retain Angular's accepted-response contract. An uncertain response never receives fabricated acceptance.

Timeout or persistence failure retains UNKNOWN and its reservation. A worker resumes only RESERVED intents and queries existing provider checkout IDs. It never resends DISPATCHING/UNKNOWN initiation. Stale DISPATCHING becomes UNKNOWN. Provider query errors and unrecognized results remain pending. Confirmed successful query settles once. Confirmed supported terminal failure releases the reservation for a separately requested new-key retry. A success cannot be downgraded by a late failure.

Callbacks are durably stored before acknowledgement, deduplicated by payload hash, matched by provider identifiers and reconciled with authenticated provider query results. Mismatched callback merchant/amount evidence is quarantined. Callback receipt IDs and checkout IDs have database uniqueness safeguards. Duplicate callback/query settlement is covered by tests.

An UNKNOWN attempt with no durable provider checkout ID remains reserved for operator/provider investigation. The provider protocol cannot prove non-dispatch after loss of its acceptance response. Automatic re-initiation in that state is deliberately unavailable. Operational reconciliation and authorized provider evidence remain activation prerequisites.

Exact BigDecimal totals are preserved. STK requires a positive exact whole-KES integer within the Java/provider request range. Fractional KES is rejected without rounding or changing an order total. This conservative implementation choice requires owner review before activation. Existing fractional orders would remain payable only after an approved provider-compatible policy.

### Farmer Android flow

The Farmer controller now prepares the server basket, obtains a quote, uses existing delivery selections, submits the quote order, initiates backend-mediated payment and displays pending/confirmed/failed states. Order and STK each require separate confirmation. Saved records are scoped by authenticated account/API origin, use compare-and-set persistence and preserve exact Long product IDs. No phone or credential is written into the transaction journal.

The original keys persist before writes. An ambiguous order response replays the same durable quote. Payment uncertainty triggers read-only polling, including when the initiation response is lost. Process restart restores the saved operation, reads authoritative order/payment state and does not repeat STK. Confirmed payment failure permits a separately requested retry. Session changes stop requests while refresh-safe token rotation remains supported. Rapid taps are serialized.

Android transaction UI remains default-disabled. Local opt-in additionally requires DEBUG, `kilivana.farmerTransactions=true`, SANDBOX configuration and the existing environment readiness check. Backend initiation and its worker default to `kilivana.payments.external-enabled=false`. These gates were never enabled against an external service. Backend dispatch gating also affects existing Buyer initiation and must be coordinated during deployment.

## Exact implementation files

Backend paths below are relative to `backend-transaction-readiness`.

New production files

- `src/main/java/com/kilivana/backend/ecommerce/dto/CheckoutPreparationRequest.java`
- `src/main/java/com/kilivana/backend/ecommerce/entity/CommerceOperation.java`
- `src/main/java/com/kilivana/backend/ecommerce/entity/PaymentIntent.java`
- `src/main/java/com/kilivana/backend/ecommerce/entity/PaymentReservation.java`
- `src/main/java/com/kilivana/backend/ecommerce/entity/PaymentCallbackInbox.java`
- `src/main/java/com/kilivana/backend/ecommerce/repository/CommerceOperationRepository.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/CommerceJournal.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/CheckoutPreparationService.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/PaymentIntentService.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/PaymentInitiationCoordinator.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/PaymentReconciliationStore.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/PaymentReconciler.java`
- `src/main/resources/application-commerce-local-test.properties`

Modified production files

- `src/main/java/com/kilivana/backend/config/DarajaProperties.java`
- `src/main/java/com/kilivana/backend/dto/DarajaCallbackRequest.java`
- `src/main/java/com/kilivana/backend/dto/StkPushInitiateRequest.java`
- `src/main/java/com/kilivana/backend/ecommerce/controller/CartCompatibilityController.java`
- `src/main/java/com/kilivana/backend/ecommerce/dto/DeliveryQuoteRequest.java`
- `src/main/java/com/kilivana/backend/ecommerce/entity/Payment.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/CartService.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/CheckoutService.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/OrderService.java`
- `src/main/java/com/kilivana/backend/ecommerce/service/PaymentService.java`
- `src/main/java/com/kilivana/backend/service/DarajaCallbackService.java`
- `src/main/java/com/kilivana/backend/service/DarajaService.java`

Database review artifacts

- `db/reviewed/commerce-transactions.sql`
- `src/test/resources/commerce-migration-baseline.sql`
- `src/test/resources/commerce-migration-verify.sql`

Backend test files

- New `ecommerce/service/CommerceDatabaseTest.java`, `PaymentCoordinatorTest.java`, `PaymentUnitFixture.java` under `src/test/java/com/kilivana/backend`
- Modified `ecommerce/service/BuyerStkPushTest.java`, `CheckoutServiceTest.java`, `PaymentServiceTest.java`, `PaymentCallbackTest.java`, `security/ServiceAuthorizationTest.java`, `service/DarajaServiceTest.java` under that same test root

Android files

- Modified `app/build.gradle.kts`
- Modified `app/src/main/java/com/example/farmapp/farmerpurchases/FarmerPurchasesScreen.kt`
- New `app/src/main/java/com/example/farmapp/farmerpurchases/FarmerTransactionController.kt`
- New `app/src/main/java/com/example/farmapp/farmerpurchases/FarmerTransactionPreferences.kt`
- New `app/src/main/java/com/example/farmapp/farmerpurchases/FarmerTransactionPanel.kt`
- New `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerTransactionControllerTest.kt`
- New `app/src/androidTest/java/com/example/farmapp/farmerpurchases/FarmerTransactionPersistenceTest.kt`
- New `app/src/androidTest/java/com/example/farmapp/farmerpurchases/FarmerTransactionPresentationTest.kt`

Existing uncommitted Buyer, Supplier and historical audit/test changes remain preserved. The inventory above describes transaction implementation, not every pre-existing working-tree modification.

## Verification commands and results

Java uses Android Studio JBR 21 through process-local JAVA_HOME. Database tests explicitly require `KILIVANA_COMMERCE_TEST_DB=true`.

| Check | Actual result | Evidence |
|---|---|---|
| Android Kotlin, test Kotlin and JVM suite | 637/637 passed, Kotlin compilation successful | `phase4-batch3-final-implementation-jvm.log` |
| Targeted physical-device Farmer tests | 11/11 passed | `phase4-batch3-implementation-device.log` |
| First independent physical-device attempt | 132 executed, 15 failures, process crash before 168 planned cases completed | `phase4-batch3-implementation-independent.log`, preserved `phase4-batch3-implementation-first-independent-results.xml` |
| Awake independent physical-device rerun | 168 executed, 166 passed, 2 failed, zero skipped | `phase4-batch3-implementation-independent-awake.log` |
| Isolated Supplier follow-up | 2/2 passed unchanged | `phase4-batch3-implementation-supplier-isolated.log` |
| Focused backend suite | 83/83 passed, including 12 real-PostgreSQL cases | `backend-transaction-readiness/target-final-focused-tests.log`, Surefire XML |
| Complete backend suite | 568 tests, 12 failures, 4 errors, 34 skipped | `backend-transaction-readiness/target-final-full-tests.log` |
| Untouched canonical backend baseline | 550 tests, 13 failures, 4 errors, 34 skipped | `backend-transaction-readiness/target-canonical-baseline-tests.log` and canonical-baseline Surefire XML |
| Final SQL rehearsal | Passed migration plus negative uniqueness/check assertions | `backend-transaction-readiness/target-migration-final.log` |
| Whitespace checks | Passed with existing line-ending warnings | Root/backend `git diff --check` |

Commands executed

```powershell
# From Android root
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --offline --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.example.farmapp.RequiresAuthenticatedBuyer' --offline --console=plain
# Targeted device classes were the two new FarmerTransaction classes plus
# FarmerPurchasesPresentationTest and FarmerPurchaseRecordCompatibilityTest.

# From backend checkout, disposable database opt-in only
.\mvnw.cmd -q '-Dtest=CommerceDatabaseTest,PaymentCoordinatorTest,DarajaServiceTest,CheckoutServiceTest,BuyerStkPushTest,PaymentServiceTest,PaymentCallbackTest,OrderServiceTest,ProductManagementSecurityTest' test
.\mvnw.cmd -q test

# Fresh disposable DB only, PostgreSQL 18 binaries
createdb -h 127.0.0.1 -p 55439 -U commerce_test commerce_migration_final_test
psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55439 -U commerce_test -d commerce_migration_final_test -f src/test/resources/commerce-migration-baseline.sql -v expected_database=commerce_migration_final_test -f db/reviewed/commerce-transactions.sql -f src/test/resources/commerce-migration-verify.sql
```

The first independent device run omitted the temporary stay-awake setting. Device diagnostics confirmed Dozing during widespread Compose-hierarchy failures. Later it crashed during Supplier composition destruction with an ArrayIndexOutOfBoundsException. This is evidence of an invalid test environment and a teardown failure, not proof of every individual failure's root cause. The corrected rerun keeps the device awake and restores its original setting 0 afterward. No assertions were weakened.

The focused backend reconciliation test initially failed because Mockito returned integer zero for an unstubbed Integer provider result, falsely simulating confirmed success. Explicitly stubbing unresolved status to null fixes the fixture and preserves the assertion that an unresolved query cannot settle payment.

The complete backend failure-name set was compared with the untouched canonical baseline XML. Every remaining failing test name already fails at a8205b0. The former cart-unavailable expectation now passes with the functional checkout fixture. Remaining failures are AuthControllerTest (3), EmailVerificationControllerTest (5), AccountAccessValidatorTest (2), EmailOtpAuthorizationTest (1), ServiceAuthorizationTest address alias (1). Errors are AuthServiceEmailNormalizationTest (2), DeliveryOpenApiTest (1) and JwtAccountEnforcementTest (1). Existing unrelated failures concern authentication aliases, OTP mocks/account status, address alias authorization and OpenAPI context setup. They remain release blockers, and the complete backend suite is not green.

The genuine retained-session Buyer smoke remains separately credential-dependent and BLOCKED. It contributes no passing count. No Buyer session or credentials were manufactured. Prior authorized-login and reachable-backend prerequisites remain.

## Migration, deployment and rollback gates

The SQL is an explicitly reviewed manual script, not an automatic Flyway migration. It creates operation/intent/reservation/inbox tables and adds provider checkout/receipt, cart and cart-line uniqueness plus quantity checks. Before any non-disposable execution, inspect the actual engine/schema/history, reconcile duplicates and legacy pending payments, verify column/enum compatibility, take a restorable backup and rehearse against a sanitized representative snapshot. The synthetic rehearsal cannot prove production schema compatibility.

Deploy only after confirming the running revision/build identity and matching deployed OpenAPI paths, security and DTOs. Deployment is currently offline and its revision/schema remain UNKNOWN. Verify all application replicas use the same reservation-aware implementation before enabling provider dispatch. An older binary can bypass the new reservation, so rolling back dispatch code requires first disabling dispatch everywhere and retaining durable payment evidence for reconciliation. Do not drop financial journals as rollback.

Before sandbox activation, review the whole-KES policy, verify sandbox provider credentials and callback routing securely, validate status-query semantics/terminal codes with controlled provider tests, exercise callbacks/restarts/concurrency through HTTP against a full safe schema, confirm Angular unchanged-body/acceptance/polling compatibility, resolve baseline backend failures, and establish an operator process for UNKNOWN attempts without IDs. Production remains separately authorized.

## Readiness matrix

| Operation | Local technical evidence | Activation/deployment status |
|---|---|---|
| Browsing | Existing independent tests | Previously safe, deployed parity unknown |
| Cart read | Legacy ID/create behavior retained | Compatible legacy side effect retained |
| Exact Farmer cart preparation | Atomic keyed preparation tested in PostgreSQL | Android disabled pending deployment verification |
| Quote | Durable 15-minute snapshot/replay/validation tested | Schema not deployed |
| Order submission | Concurrent same-quote creates one real order and reserves stock once in isolated DB | Android disabled, HTTP/deployed compatibility unverified |
| STK initiation | Durable reservation/claim/timeout tests and mocked transport pass | External dispatch disabled, no provider calls |
| Reconciliation | Durable inbox, duplicate/early callback, confirmed query and no-blind-retry tests pass | Provider sandbox/operational proof outstanding |
| Payment status and recovery | Mocked Android flow, persistence, exact-ID and restart tests | Real authenticated HTTP integration outstanding |
| Buyer retained smoke | Preserved genuine authentication requirement | BLOCKED by absent authorized login |

The next safe implementation step is full-schema local HTTP integration with mocked Daraja transport, followed by fixing the independently reproduced canonical backend test defects within an agreed scope. Controlled sandbox deployment/payment verification requires separate explicit authorization and the schema/revision/configuration gates above.
## Final device rerun details

The awake rerun completed all 168 independent cases, with 166 passed and two failed. All four new transaction instrumentation cases passed, along with the other Farmer cases. XML was preserved as `phase4-batch3-implementation-awake-results.xml`.

- `SupplierUiTest.imageGallerySupportsSelectionAndSafeUnavailableImageFallback` failed at line 254 because `Photo unavailable` was absent. Its fixture requests an invalid external image URL and immediately asserts the asynchronous fallback. A timing race is plausible but not proven as the sole cause.
- `SupplierVisualReviewTest.captureSupplierScreensAndDarkSmallLargeTypeStates` failed at line 96 with no Compose hierarchy during the large-type create screen. The trace does not establish a production defect. This remains an instrumentation/lifecycle uncertainty.

Both cases passed unchanged in isolation, **2/2**, in `phase4-batch3-implementation-supplier-isolated.log`. They remain full-suite intermittent failures. This isolated pass does not replace the 166/168 complete-run result. No Supplier or Buyer production changes were made to mask these failures. The device stay-awake setting was restored and verified as 0 after the awake suite.