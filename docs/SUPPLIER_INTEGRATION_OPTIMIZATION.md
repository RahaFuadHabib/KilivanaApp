# Supplier integration and optimization

Stages 1–3 — 30 October 2026. Branch: feature/supplier. Existing uncommitted work retained. Stage 3 validation results are recorded below.

## Audit and contract findings

- Production Supplier navigation uses SupplierShell with SupplierRepository, session-generation and ownership checks, BigDecimal prices and real listing metrics.
- Legacy ui/screens/SupplierScreen.kt contains fabricated requisitions, local payment toggles and inventory based on data/model/SupplyItem.kt. It has no production caller. Shared mock models remain intact for existing consumers.
- Read-only live /api-docs returned HTTP 200 during the audit at the unchanged configured URL. No authenticated Supplier endpoint or write was exercised.
- GET /api/v1/suppliers/{supplierId}/orders advertises ApiResponsePageOrderResponse, with content, number, size, totalElements, totalPages and last. Supplier ID is documented as the user ID; authenticated authorization and multi-seller projection still require verification.
- GET /api/v1/orders/seller/{sellerId} describes pagination but declares a list response. Do not assume these feeds are interchangeable.
- Supplier categories document lowercase supplier; current uppercase route acceptance requires verification.
- Order status POST description and query-parameter declaration disagree. No transitions enabled.
- Payments and disputes require separate Supplier authorization verification; no initiation or administrative actions are part of this work.

## Stage 1 changes

- Clear inventory overview, filtered listing count, bounded product titles and stock icon/zero-stock label, retaining Kilivana's green palette and rounded cards.
- Initial catalogue state shows loading rather than unverified zero metrics or a false empty result.
- Verified empty inventory has a helpful introduction, not a service-unavailable badge.
- Failed refresh keeps repository-backed records and explicitly warns that displayed stock/prices may have changed. First-load failures have a clear heading and retry.
- Removed the technical order-feed explanation from the shopping dashboard; Orders functionality remains in its existing tab pending Stage 2 approval.
- Legacy mock SupplierScreen is deprecated at ERROR level: accidental Kotlin navigation references fail compilation. Source and shared models are preserved.
- Product create/edit/photo confirmations, validation, durable uncertainty markers, authentication and other role destinations are unchanged.

## Files changed for Stage 1

- app/src/main/java/com/example/farmapp/supplier/SupplierShell.kt
- app/src/main/java/com/example/farmapp/supplier/SupplierViewModel.kt (presentation-state predicates only)
- app/src/main/java/com/example/farmapp/ui/screens/SupplierScreen.kt (compile-time legacy-entry guard only)
- app/src/test/java/com/example/farmapp/supplier/SupplierCataloguePresentationTest.kt
- app/src/androidTest/java/com/example/farmapp/supplier/SupplierUiTest.kt
- docs/SUPPLIER_INTEGRATION_OPTIMIZATION.md

## Validation

- Full testDebugUnitTest: **428 passed**, zero failures/errors/skips across 53 suites. Includes Supplier ownership, session/role isolation, product-management and shared authentication/navigation regressions; four new catalogue-state tests were added.
- lintDebug: **0 errors, 79 warnings, 1 hint**. Existing warnings remain.
- assembleDebug and assembleDebugAndroidTest: passed. Combined validation completed successfully in 4m 22s (78 tasks).
- Samsung RF8WC0K043H authorized. Main APK and instrumentation APK installed successfully with adb install -r, retaining the existing application ID and data; no uninstall, logout or clear-data operation.
- Samsung instrumentation: **31 passed** in 84.243 seconds. Classes: SupplierUiTest, SupplierVisualReviewTest, BuyerAuthenticationPhaseOnePresentationTest, RoleNavigationRecoveryTest and SupplierLoginRecoveryUiTest. Fixtures/callbacks are synthetic; no live account registration or Supplier writes were performed.
- Retrieved 13 synthetic visual-test screenshots into ignored app/build/reports/supplier-stage1/supplier-redesign. Reviewed dashboard, inventory and dark 320 dp / 150% text captures. Layouts retain scrolling, readable controls, real-data-shaped placeholders and existing navigation.
- Logs: buyer-validation-supplier-stage1-build.log and buyer-validation-supplier-stage1-samsung.log (ignored validation output).
- Git index remained empty when checked. No files were staged, committed or pushed. Existing uncommitted Supplier and other-role work remains present; the full Git diff includes work predating this stage.

Live authenticated Supplier read behavior, category case acceptance and current server records were not tested. The retained phone session was not replaced with a Supplier account. Mocked rendering validates presentation and error handling, not live Supplier authorization.

## Performance and pending work

No performance gains claimed in Stage 1. Audit found five normal refresh requests including two auth/me calls; open detail adds another identity check/detail read. These are source-derived counts, not measured live traffic. Preview decoding inside composition and repeated gallery sorting remain Stage 3 candidates. No shared image cache or authentication behavior changed.

Stage 3 was subsequently approved; see its results below. No live cart/order/payment/status mutations, backend changes, app identity changes or Git staging/commit/push are permitted.

## Stage 2 — read-only Supplier orders

Approved by the user following Stage 1. Fresh live GET /api-docs succeeded on 30 October 2026 at the unchanged configured backend URL.

### Verified contract and authorization limits

- GET /api/v1/suppliers/{supplierId}/orders: supplierId is explicitly documented as **Supplier user ID**, not the Supplier profile ID. The client derives it from the existing session and verifies the same user ID and SUPPLIER role through protected auth/me before requesting a page.
- Existing shared Retrofit/OkHttp authentication and SessionSnapshot tags are reused. No new authentication implementation or identity input exists.
- Global OpenAPI security declares bearerAuth. The operation advertises Supplier-scoped records, but does not separately document role enforcement. No authenticated Supplier session was exercised: live permission and response-shape acceptance remain pending. A 401/403 stops the request and clears retained order data; there is no buyer, administrator, public or seller-feed fallback.
- Response: ApiResponsePageOrderResponse {success, message, data, timestamp, error}; data contains content, number, size, last, totalPages and totalElements. Standard Pageable page/size query parameters are sent explicitly, starting at page 0 with size 20. Pagination metadata and page identity are validated; inconsistent/missing metadata fails closed.
- Only the Supplier feed is added. GET orders/{id} and orders/{id}/timeline are not called because Supplier permission for those endpoints has not been established. No order status or payment endpoint is added.

### Implementation

- SupplierApi and SupplierRepository now fetch verified pages. Every order must contain at least one line with sellerId equal to the authenticated Supplier user ID. Missing seller IDs, foreign-only orders, invalid owned lines and duplicate IDs reject the page.
- Parsing deliberately excludes buyer/contact/address fields, payment details and whole-order totals. Other sellers' lines are removed before records enter UI state. Supplier subtotal sums only verified owned-line subtotals using BigDecimal; each line must match quantity × unitPrice. This is labelled **Your items subtotal**, never revenue or settlement.
- Orders load on first entry to the existing Orders tab. Explicit Load more uses the next verified page; pull-to-refresh and Retry restart at page 0. Request overlap is gated, cross-page order IDs are deduplicated and account/logout changes cancel requests and discard state.
- The feed displays actual order code where present, readable date, known status (or Status unavailable), owned product names, quantities, unit prices and line subtotals. Missing names use a neutral Product label; no images or payment results are invented.
- Loading, verified-empty, offline/error, retained-record warning, retry and pagination states follow the existing Supplier cards and theme. Existing unavailable fulfillment/tracking/payment panels are replaced with the read-only feed; no previously usable transaction capability was removed.
- Stage 1 catalogue improvements and product management are preserved. Shared role routing and authentication are unchanged.

### Stage 2 files

- supplier/SupplierApi.kt, SupplierRepository.kt, SupplierViewModel.kt, SupplierShell.kt
- supplier/SupplierOrders.kt and SupplierOrderPresentation.kt (new typed projection, validation and UI)
- test supplier/SupplierOrdersTest.kt (new), SupplierTestFixtures.kt, SupplierModuleTest.kt
- androidTest supplier/SupplierOrdersUiTest.kt (new), SupplierUiTest.kt, SupplierVisualReviewTest.kt
- this report

All paths above are under app/src/main/java/com/example/farmapp or their corresponding test/androidTest roots.

### Stage 2 validation

Initial full suite: 438 tests passed; lint and both APK builds succeeded in 2m 13s.

Final validation, including the additional offline/recovery regression and updated visual fixture:

- **439 unit tests passed**, zero failures/errors/skips. Eleven new Supplier order tests cover owned-line projection, precise amounts, missing ownership, invalid quantities/prices, unknown statuses, pagination metadata, request wrapping/path/query mapping, identity rejection, forbidden responses, duplicate requests, page append/reset, late account-switch responses and offline recovery.
- **lintDebug passed:** 0 errors, 79 warnings, 1 hint.
- **assembleDebug and assembleDebugAndroidTest passed.** Final incremental validation completed in 34 seconds.
- **36 Samsung instrumentation tests passed** in 75.317 seconds: SupplierUiTest, SupplierOrdersUiTest, SupplierVisualReviewTest, BuyerAuthenticationPhaseOnePresentationTest, RoleNavigationRecoveryTest and SupplierLoginRecoveryUiTest. These use synthetic responses/callbacks, not live Supplier order data or transactions.
- Samsung RF8WC0K043H authorized. Both main and UI-test APKs installed successfully using adb install -r. Existing package identity, backend URL, account data and other roles are preserved; no uninstall, logout or clear-data operation.
- Logs: buyer-validation-supplier-stage2-build.log, buyer-validation-supplier-stage2-final-build.log and buyer-validation-supplier-stage2-samsung.log (ignored validation output).
- Git index checked empty. No reset, clean, restore, stash, staging, commit, push or backend write was performed.

Stage 2 implementation is contract-backed and mock/device-tested. Live authenticated Supplier authorization and page/size acceptance remain pending; no live Supplier denial or success is claimed. Stage 3 remains paused for explicit user approval.

### Remaining backend verification

Daniel should confirm Supplier self-access enforcement on the scoped feed, page/size binding and Supplier permissions for individual order/timeline reads. No live access denial has been observed or bypassed. Runtime Supplier authorization is unverified, not claimed broken. The previously signed-in Buyer is not impersonated or replaced for testing. Stage 3 requires separate approval after Stage 2 results.

## Stage 3 — measured Supplier optimization

Subsequently approved by the user. Measurements use synthetic API/image fixtures, not live business data. No live state-changing requests were made.

### Before and after

Before modifying refresh behavior, SupplierPerformanceTest executed the original SupplierStateHolder/repository with counting API fixtures. Baseline XML is preserved under ignored app/build/reports/supplier-stage3/baseline-requests.xml. The same scenarios/assertions were then run against the optimized implementation.

| Scenario | Before | After | Measured reduction |
| --- | --- | --- | --- |
| Initial catalogue load | 2 auth/me + 3 resource calls = 5 | 1 auth/me + 3 resource calls = 4 | 20% of API invocations |
| Catalogue refresh | 2 auth/me + 3 resource calls = 5 | 1 auth/me + 3 resource calls = 4 | 20% |
| Refresh with product detail open | 3 auth/me + 4 resource calls = 7 | 1 auth/me + 4 resource calls = 5 | Approximately 29% |

These counts exclude optional token-refresh traffic. They are deterministic mocked invocation counts, not measured production latency or mobile-data savings.

The uncached ProductImage rendering fixture (one hero, two thumbnails, two distinct URLs) was run on Samsung before enabling reuse: 3 loader executions. The initial attempt reached the count but lost its Compose hierarchy during a final assertion; waking the phone and rerunning passed. After reuse, the same rendering pattern must execute only 2 loaders/decodes; final Samsung results are pending below. The fixture uses synthetic image bytes and never contacts an image server.

### Changes and safety

- SupplierRepository.ReadBatch shares one protected auth/me result only among list/profile/selected-detail reads in one refresh coroutine scope. It is neither a timed cache nor shared across refreshes. Identity failures are shared as failures, not anonymous success; categories can finish independently. Every resource read retains expected-session validation, owner validation and post-response checks. Standalone detail/order/product-management calls retain fresh identity reads.
- Supplier photo preview bounds checking and decoding now run on Dispatchers.Default through supplierPreview. Preview sampling matches the 96 dp display's pixel size instead of allocating the full prepared photo. Picker preparation, original JPEG quality, upload bytes, size limits and upload confirmation are unchanged. Decode failure has a neutral fallback.
- Supplier galleries opt in to ScopedImageMemory through the existing ProductImage infrastructure. The gallery owns an 8 MiB / 8-entry decoded-image LRU and coalesces concurrent requests for the same URL. No disk/global/cross-account image cache is introduced. The limit applies to cached decoded images, not total process heap or temporary network buffers.
- Memory is recreated on gallery content/session changes and cleared/cancelled on disposal. Cache access and late results check the current generation, account, origin, role and logout/demo flags. A cancelled hero consumer does not cancel a thumbnail's shared load; disposal cancels the gallery-owned scope. Failures are not cached. Cached bitmap objects are not manually recycled while the UI may still reference them.
- Existing image origin restrictions, authentication interceptor, no-redirect/no-retry policy, bounded response reads and decode dimensions remain in use. Other roles keep ProductImage's existing uncached default; no payment, checkout, order mutation or authentication interceptor changes were made.
- Gallery sorting and listing metrics are remembered by their input lists. Selection changes or unrelated state updates no longer require repeating those calculations. No whole-app recomposition/FPS improvement is claimed.
- Catalogue endpoint remains an unpaged owner list; no client-side server pagination contract was invented. Existing refresh overlap guards remain. Orders still use explicit size-20 page requests, overlap gating, last-page checks and ID deduplication. Loaded order pages are retained only in account-scoped memory; no arbitrary truncation of user-requested pages was added.

### Stage 3 files

- main supplier/SupplierRepository.kt, SupplierViewModel.kt, SupplierShell.kt, SupplierVisuals.kt
- main supplier/SupplierPhotoPreview.kt (new)
- main ui/screens/ProductImage.kt (same default transport with opt-in scoped reuse and synthetic loader seam)
- main ui/screens/ScopedImageMemory.kt (new bounded gallery memory)
- test supplier/SupplierPerformanceTest.kt, SupplierImageMemoryTest.kt (new)
- androidTest supplier/SupplierImagePerformanceTest.kt (new)
- this report

### Stage 3 validation

- Full unit suite: **447 passed**, zero failures/errors/skips. Includes Supplier performance, image-memory isolation/eviction/cancellation, shared identity denial/account-switch, order pagination, authentication and other role regressions.
- lintDebug: **0 errors, 79 warnings, 1 hint**.
- Debug and UI-test APK builds passed in the combined 3m 4s validation run.
- Final Samsung synthetic load/decode measurements and UI regression results: pending.

### Unmeasured and live-dependent areas

No real-network latency, heap profile, battery consumption, frame-time trace or global recomposition count was collected. The backend's live Supplier authorization, private-image delivery and pagination acceptance remain pending an authorized Supplier session. These optimizations do not claim those integrations have been verified live. No additional feature development is included.
