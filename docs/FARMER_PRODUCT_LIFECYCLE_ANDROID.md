# Farmer product lifecycle: Android integration and isolated validation

## Findings

Android previously displayed an Unlist button but `CanonicalFarmService.unlistProduct` deliberately rejected the operation. Archive, delete and resubmit controls were absent. Canonical status parsing recognized only the four moderation states, and the Farmer display model omitted seller ID and reserved quantity.

The running `http://127.0.0.1:8080/api-docs` advertises only `active`, `pending`, `rejected`, `suspended` for product status. The supplied context identifies that server as `kilivana-shared-backend` (HEAD `7296119` when inspected). The updated `kilivana-supplier-backend` worktree (HEAD `bf61030`, with uncommitted lifecycle and other changes) has the seven-state lifecycle. A Git HEAD alone therefore does not identify the updated implementation. Neither backend was edited, started, stopped or switched during this Android task.

Contract read: `../kilivana-supplier-backend/docs/integration/FARMER_PRODUCT_ADMIN_FARM_API_CHANGES.md`, supported by its ProductService and ProductStatus source. No authenticated live product write was used to identify the mismatch.

## Android behavior

| Current state | Actions for authenticated Farmer owner |
| --- | --- |
| ACTIVE | Unlist, Archive |
| PENDING_APPROVAL / REJECTED | Archive |
| UNLISTED | Archive, Submit for Admin approval |
| ARCHIVED | Submit for Admin approval; Delete only with confirmed zero reserved stock |
| SUSPENDED / DELETED / unknown owner | None |

Status calls are bodyless `PUT products/{id}/status` with uppercase `UNLISTED`, `ARCHIVED`, or `PENDING_APPROVAL` query values. Delete uses `DELETE products/{id}` and validates the successful envelope with nullable data. No client request can choose ACTIVE, moderate a crop or hard-delete historical rows. The server implements terminal soft deletion and photo cleanup. Android never removes orders/payments or calls storage deletion directly.

The canonical adapter checks protected `auth/me`, re-reads current owned product state and reservation metadata, and tags reads/writes with the originating session snapshot. It checks session identity again after responses. The server still authorizes ownership and applies its locked lifecycle rules; preflight cannot eliminate concurrent changes. Canonical writes are sent once through the existing interceptor and are not refreshed/replayed automatically.

The detail UI confirms every action, consumes confirmation once, and blocks competing mutations during execution. After a confirmed status change it reloads owner detail and My Crops. Confirmed deletion closes detail and reloads the list without fetching the now-deleted detail. Failed/uncertain operations leave a clear message and require a successful explicit refresh before another lifecycle action. A confirmed mutation followed by a failed read is reported as a refresh failure rather than prompting another write. HTTP 400, 401, 403 and 404 retain their response codes through ProductRepository and show its managed error feedback. A 401 directs sign-in; 403 indicates permission denial; 404 indicates an unavailable crop. No automatic retries occur.

The existing Farmer screen/controller architecture is reused. `FarmerViewModel` manages the legacy farmer roster, not these crop screens. Existing editing, publishing/reconciliation and reference/demo behavior remain in their existing paths. Canonical stock-edit copy now correctly states that editing stock does not change approval status. New lowercase wire values are recognized additively. Product image IDs and URLs are retained; private images continue using the existing session-scoped authenticated image client with bounded origin trust. No image API behavior was changed.

The local debug URL remains `http://127.0.0.1:8080/api/v1/`, canonical auth enabled, mock auth disabled. Existing local settings and the pending build-script change from the prior task are preserved. No backend migration was executed.

## Separate safe plan for updated-backend testing (not executed)

1. Leave the older port-8080 process and existing PostgreSQL instance/database untouched. Inventory the updated worktree's uncommitted changes before building; it also contains email-OTP changes and V20, beyond product lifecycle. Review which complete backend revision/schema set will be tested. Do not reset/stash that worktree or assume HEAD contains its working changes.
2. Provision a disposable PostgreSQL instance on a distinct host port with a new empty volume, database and dedicated credentials. Use synthetic data only. Restrict the backend's database credentials to this instance. Do not copy an existing application's environment file, production URL, database, or credentials. Use isolated filesystem storage as well if selecting local image storage.
3. Create an explicit temporary test profile/environment outside shared configuration. Set a separate server port, e.g. 8082; set datasource URL/user/password explicitly to the disposable instance, select isolated image storage, and disable external mail/payment integrations through documented test settings. Verify resolved configuration privately before starting; do not allow fallback to the shared datasource. Use the backend's documented schema setup and reviewed migrations solely on the disposable database. V19 expands the product status constraint and is needed for these states. The current worktree's other changes may require additional schema such as V20; review them rather than blindly running every SQL file. This project documents manual migrations, not automatic Flyway rollout. Do not replay destructive historical scripts against existing data.
4. Build/start the reviewed updated backend on 8082 only after the disposable target is verified. Check health and API docs, and confirm lifecycle enum support. Seed synthetic Farmer, unrelated seller, Admin, products in each state, and reserved-stock fixtures through the backend's isolated test/fixture setup. No existing live account or product is needed.
5. Only for that isolated test, change the existing ignored local debug override to `http://127.0.0.1:8082/api/v1/`, preserve all other local settings, rebuild and install the resulting backend-specific debug variant, then set `adb reverse tcp:8082 tcp:8082`. The current 8080 reverse mapping and older app variant can remain. This URL/forwarding change has not been made by this task.
6. On Samsung, sign in as the synthetic Farmer and test ACTIVE -> UNLISTED -> PENDING_APPROVAL, eligible -> ARCHIVED, archived-zero-reserved deletion and archived-reserved rejection. Verify the product remains private until Admin approval, owner-only visibility, private photos, list/detail refresh, forbidden cross-owner/role attempts, stale-state 400, expired-session 401, forbidden 403 and deleted/missing 404. Inspect retained order/payment history in disposable fixtures. Never use these mutation checks on shared/live data.
7. Record the exact backend source/build/schema and Android package tested. Stop only the isolated process afterwards and remove only its explicitly identified disposable resources if separately authorized. Restore the local debug override deliberately if returning to the older backend; never coerce new database states into ACTIVE to support a downgrade.

## Validation scope

JVM transport tests exercise real Retrofit serialization and AuthTokenInterceptor with a synthetic terminal interceptor. Controller tests cover confirmation gating, refresh results and uncertain outcomes. Compose tests use synthetic callbacks only. Device execution of those tests does not constitute acceptance against the updated backend. Results are reported separately in the task completion report.

Validation completed: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` passed, with 262 JVM tests across 35 suites and zero failures/errors/skips. An initial new-fixture failure was corrected to use canonical unknown refresh expiry; the full suite then passed. Both APKs installed in place with `adb install -r` on Samsung `RF8WC0K043H`, without clearing app data. Direct instrumentation filtered to `CropLifecyclePresentationTest` and `CropSavePresentationTest` returned `OK (8 tests)` (six new lifecycle tests and two save regressions). No acceptance mutation against either real backend was performed. The updated backend was not started or tested on Samsung.

## File change inventory

Paths below are relative to the Android project; backend reads are explicitly prefixed.

| Created | Purpose |
| --- | --- |
| `app/src/main/java/com/example/farmapp/network/canonical/FarmerProductLifecycle.kt` | Protected identity/ownership/current-state checks and single-attempt lifecycle adapter |
| `app/src/main/java/com/example/farmapp/ui/screens/CropLifecycleActions.kt` | Eligible actions and consumed confirmation dialogs |
| `app/src/main/java/com/example/farmapp/ui/screens/CropLifecycleController.kt` | Mutation/refresh state and uncertainty feedback |
| `app/src/test/java/com/example/farmapp/network/canonical/FarmerProductLifecycleTest.kt` | Synthetic transport, state, ownership, session, HTTP-error and serialization coverage |
| `app/src/test/java/com/example/farmapp/ui/screens/CropLifecycleControllerTest.kt` | Duplicate gating, confirmed deletion, refresh failure and uncertainty coverage |
| `app/src/androidTest/java/com/example/farmapp/ui/screens/CropLifecyclePresentationTest.kt` | Six synthetic Compose lifecycle tests |
| `docs/FARMER_PRODUCT_LIFECYCLE_ANDROID.md` | Findings, integration notes, validation and separate database-safe test plan |

| Modified | Purpose |
| --- | --- |
| `app/src/main/java/com/example/farmapp/data/model/FarmerProductModels.kt` | Seller/reservation metadata and Farmer action eligibility |
| `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalFarmerModels.kt` | Additive canonical lifecycle states |
| `app/src/main/java/com/example/farmapp/data/remote/FarmApiService.kt` | Detail/lifecycle adapter boundaries with unsupported-backend defaults |
| `app/src/main/java/com/example/farmapp/network/canonical/CanonicalBackendApi.kt` | Existing protected identity/detail/status/delete routes with session tags |
| `app/src/main/java/com/example/farmapp/network/canonical/CanonicalFarmService.kt` | Metadata projection and lifecycle adapter wiring |
| `app/src/main/java/com/example/farmapp/repository/ProductRepository.kt` | Managed detail/mutation responses and distinct authentication/permission feedback |
| `app/src/main/java/com/example/farmapp/ui/screens/CropDetailScreen.kt` | Canonical actions and accurate stock/approval explanation |
| `app/src/main/java/com/example/farmapp/ui/screens/FarmerScreen.kt` | Product status in listing cards |
| `app/src/main/java/com/example/farmapp/ui/screens/FarmerShell.kt` | Controller integration and list/detail refresh |
| `app/src/test/java/com/example/farmapp/network/canonical/CanonicalContractTest.kt` | Seven-state wire mapping regression |
| `app/src/test/java/com/example/farmapp/network/canonical/CanonicalIntegrationAdapterTest.kt` | Replace obsolete unsupported-unlist expectation with new lifecycle coverage |

Other files read/reused: `AGENTS.md` (empty), `app/src/main/java/com/example/farmapp/viewmodel/FarmerViewModel.kt`, `app/src/main/java/com/example/farmapp/ui/screens/CropManagement.kt`, `app/src/main/java/com/example/farmapp/ui/screens/CropSaveController.kt`, `app/src/main/java/com/example/farmapp/ui/screens/ProductImage.kt`, `app/src/main/java/com/example/farmapp/network/AuthTokenInterceptor.kt`, `app/src/main/java/com/example/farmapp/network/ImageRequestInterceptor.kt`, `app/src/main/java/com/example/farmapp/network/TokenManager.kt`, `app/src/main/java/com/example/farmapp/network/canonical/CanonicalFarmerMapper.kt`, `app/src/main/java/com/example/farmapp/network/canonical/CanonicalApiResponse.kt`, `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalProductDtos.kt`, `app/src/main/java/com/example/farmapp/data/model/Product.kt`, `app/src/main/java/com/example/farmapp/ui/navigation/AppNavigation.kt`, `app/src/test/java/com/example/farmapp/network/AuthTestFixtures.kt`, `app/src/androidTest/java/com/example/farmapp/ui/screens/CropSavePresentationTest.kt`; generated BuildConfig and JVM XML reports. Backend reads: `../kilivana-supplier-backend/docs/integration/FARMER_PRODUCT_ADMIN_FARM_API_CHANGES.md`, `../kilivana-supplier-backend/src/main/java/com/kilivana/backend/ecommerce/service/ProductService.java`, `../kilivana-supplier-backend/src/main/java/com/kilivana/backend/common/enums/ProductStatus.java`, `../kilivana-supplier-backend/db/migrations/V19__seller_product_lifecycle.sql`, and `../kilivana-shared-backend/src/main/java/com/kilivana/backend/common/enums/ProductStatus.java`. The updated backend has no root AGENTS.md.

No files were deleted. The pre-existing changes in `app/build.gradle.kts`, `.idea/deviceManager.xml`, `.idea/gradle.xml` and the already-staged `.idea/vcs.xml` were not altered by this lifecycle task. No local configuration or backend source was edited. Branch remained `feature/supplier`, HEAD `5850fcd54ec9a056a2ebdd9a82fdf3de12ec6a40`; no stage/commit/push/reset/stash action was performed.
