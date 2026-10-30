# Kilivana Android API integration audit

Audit started 2026-10-26. This is a source audit, not an authenticated production acceptance certificate. Existing uncommitted changes are preserved. No backend edits, commits, pushes, uninstall or data clearing are authorized.

## Evidence and verification

* Android: all production Kotlin source paths inventoried under `app/src/main/java`; navigation traced from `AppNavigation`, `FarmerShell`, `SupplierShell` and `InspectorShell`; repositories, Retrofit interfaces, canonical adapters, demo models and existing tests inspected.
* Deployed `https://either-juvenile-progeny.ngrok-free.dev/api-docs` returned OpenAPI 3.0.1, Kilivana Backend API 1.0.0, 219 paths on 2026-10-26. Snapshot: `docs/kilivana-openapi-audit.json`. No credentials or authenticated writes were used.
* Authorization evidence: read-only sibling `kilivana-supplier-backend/src/main/java/com/kilivana/backend` controllers, DTOs, services and `security/ResourceAuthorization.java`. Multiple sibling backend copies exist; deployed OpenAPI verifies route/schema advertisement, but does not prove deployed service authorization matches this copy. Authorization-sensitive integrations remain gated by that uncertainty.
* CONNECTED means a real Android request path exists; it does not imply that an authenticated device transaction was exercised in this audit. Table initially describes the baseline; phase results below record changes.

## Coverage table

| Role | Screen/Feature | API Endpoint | Current Status | Required Work | Backend Gap |
| --- | --- | --- | --- | --- | --- |
| All | Welcome/sign in/session recovery | POST auth/login, auth/refresh; GET auth/me | CONNECTED | Preserve working Supplier login and session-origin guards | Deployed POST auth/logout exists but inspected implementation is a no-op; canonical adapter signs out locally without claiming JWT revocation |
| Supplier/Farmer | Single-page registration and explicit Supplier recovery | POST auth/register; POST/GET profiles/suppliers/{userId}; POST profiles/farmers/{userId} | CONNECTED | Integrate email challenge without replaying registration | Verification may be required before login; deployed behavior needs acceptance |
| All | Password recovery | POST auth/forgot-password?email=; POST auth/reset-password | NOT CONNECTED | Existing PasswordRecoveryScreen is explicitly labelled a local preview; replace separately using reset-email link contract | Deployed reset DTO is token/newPassword; do not confuse recovery tokens with email OTP; Android app-link handling is unverified |
| All | Email verification | POST auth/email-verification/request, /verify | NOT CONNECTED | Challenge UUID, six digits, resend/expiry UI; no OTP logs | No token issuance from verify; already-verified request deliberately generic |
| Supplier | Dashboard, counts and owned catalogue | GET auth/me; products/seller/{id}; categories/type/SUPPLIER | CONNECTED | Pull-to-refresh; keep stale records on error | None for read path |
| Supplier | Search/category/status filters | Owned catalogue above | PARTIALLY CONNECTED | Local filtering exists; no paged owner feed | Owner list is advertised as array |
| Supplier | Create and pending edit | POST products; PUT products/{id} | CONNECTED | Make post-create photo workflow discoverable | No idempotency key/ETag; edits do not establish reapproval policy |
| Supplier | Product photographs | GET/POST products/{id}/images | PARTIALLY CONNECTED | Picker exists only in saved detail; expose during edit, preview/loading/retry; retain uncertain-write gate | Multipart image, sortOrder/isPrimary query; deployed upload limits not specified |
| Supplier | Image primary/delete/reorder | PUT products/{productId}/images/{imageId}/primary; DELETE .../{imageId}; PUT .../images/reorder | NOT CONNECTED | Add owned pending gallery management only after exact reorder payload review | Requested PATCH primary incompatible with deployed PUT |
| Supplier | Business/account profile | GET profiles/suppliers/{id}; auth/me; PUT users/me; PUT profiles/suppliers/{id} | PARTIALLY CONNECTED | Separate account and business editors; update session display data | Username absent from update DTO; verificationInfo must not be treated as editable account status |
| Supplier | Profile photographs | POST/GET profiles/suppliers/{userId}/images | PARTIALLY CONNECTED | Existing returned profile image displayed; separate profile picker/upload | Separate image collection; POST returns list, unlike product upload |
| Supplier | Incoming orders/fulfillment | orders, orders/{id}, orders/{id}/status | MISSING BACKEND API | Keep explicit unavailable state | Inspected source whole-order reads buyer/admin; no seller-safe line/feed projection |
| Supplier | Delivery discovery | logistics/jobs/order/{orderId} | MISSING BACKEND API | Need authorized seller-order-job relationship | jobRead excludes Supplier; location alone cannot discover jobs |
| Supplier/Farmer | Seller STK collection | POST payments/stk-push/supplier or /farmer | NEEDS VERIFICATION | Preserve backend payment flow; no automatic replay | Seller initiation exists, status reads buyer/admin; secure seller order amounts/feed missing |
| Farmer | My Crops/create/edit/detail/gallery/lifecycle | products/seller/{id}; POST/PUT products; GET/POST images; PUT products/{id}/status; DELETE products/{id} | CONNECTED | Preserve CropPublisher uncertainty checkpoints and reservation handling | Moderation/stock concurrency still server responsibilities |
| Farmer | My Crops/detail pull-to-refresh | Same product routes | CONNECTED | Reuse existing Material 3 implementation | None |
| Farmer | Profile/phone/settings/About | GET auth/me; PUT users/me | PARTIALLY CONNECTED | Broaden supported account fields; add version to About; replace profile refresh icon | Username not mutable through documented DTO |
| Farmer | Supplies marketplace | GET products/search?sellerType=supplier; products/{id} | NOT CONNECTED | Real paged supplier feed, categories, search/details/images | Approval must remain server controlled |
| Farmer | Seller order history/crop orders | Legacy orders/mine; canonical explicitly unavailable | MISSING BACKEND API | Do not substitute buyer order list for seller orders | Seller-scoped projection missing |
| Farmer | Purchasing supplies | POST orders; buyer history/cart/address routes | MISSING BACKEND API | Need Farmer-as-purchaser authorization | ResourceAuthorization.buyer accepts BUYER only; createOrder admin-only in inspected source |
| Farmer | Tracking/map | tracking/{jobId}/location; logistics/jobs/order/{orderId} | PARTIALLY CONNECTED | Device-location map exists; no delivery job integration | Farmer purchase order access not supported in inspected source |
| Buyer | Marketplace/search/details | GET products/search?sellerType=farmer; products/{id}; categories/type/FARMER | NOT CONNECTED | Replace live mockProducts path with real shared repository | ProductResponse exposes sellerId, not seller name; do not invent seller name/county |
| Buyer | Checkout/payment | POST orders; payments/stk-push/buyer | NOT CONNECTED | Remove mock success/PIN prompt from live path; wire only confirmed authorized order creation | Inspected createOrder admin-only; advertised buyer STK requires deployed implementation comparison |
| Buyer | Orders/timeline/cancel/receipt | GET orders/buyer/{id}, orders/{id}, .../timeline; POST .../cancel, .../confirm-receipt | NOT CONNECTED | Replace mockOrders; retain records on error; show only permitted actions | Cancellation admin-only in inspected source; receipt buyer/admin |
| Buyer | Delivery dialog | logistics/jobs/order/{id}; .../{jobId}/tracking; tracking/{jobId}/location; logistics/tracking-events/job/{jobId} | NOT CONNECTED | Replace fabricated driver/OTP/timeline with real linked job/events | Validate deployed event access; no guessed coordinates |
| Buyer | Account profile modal | PUT users/me | NOT CONNECTED | Remove role-generated mock business/profile information from live path | Mutable DTO name/email/phone/region only |
| Inspector | Dashboard/assignments/detail | inspections/inspector/{id}; inspections/{id} | CONNECTED | Replace toolbar reload with pull gesture; retain data on failed refresh | None for assigned reads |
| Inspector | Status/findings | Existing InspectorApi status/result routes | CONNECTED | Preserve ownership and workflow guards | Evidence privacy remains separate |
| Inspector | Evidence upload | Inspection evidence routes | MISSING BACKEND API | Disabled action is intentional; retain privacy explanation | Existing documented privacy blocker; product images are not evidence storage |
| Inspector | Profile | auth/me; profiles/inspectors/{id} | CONNECTED | Read-only role assignment; optional common account editing | Administrative specialization/status must stay restricted |
| Driver | Navigation/dashboard/jobs/location reporting | logistics/jobs/driver/{id}; POST accept; PUT status; POST tracking/{jobId}/location | NOT CONNECTED | No Driver destination/session support currently; add only with full authorization and foreground consent | End-to-end both purchasing roles blocked by order authorization |
| Admin/other | Navigation | Administrative APIs | NOT CONNECTED | App intentionally rejects unsupported roles | Do not expose admin actions to supported roles |
| Legacy/demo | SupplierScreen, InspectorScreen, demo Farmer paths, CheckoutBottomSheet, OrderTrackingDialog | None (mock models/local state) | NOT CONNECTED | Keep out of live role navigation; audit fake success and inert filters | These are not functioning API integrations |

All paths above have prefix `/api/v1/`. Farmer farm-map location is device location, not delivery tracking. No background driver location collection exists or is authorized by this work.

## Confirmed contract differences and gaps

1. Primary image uses **PUT**, not PATCH. Product upload part is **image**; optional query `sortOrder` integer and `isPrimary` boolean. Product JSON create returns a durable ID before separate upload. Do not recreate a confirmed product after a photo failure. Android currently bounds selected bytes to 5 MiB, decoded pixels to 50 million, and re-encodes JPEG; inspected storage limit is 10 MiB, but OpenAPI does not establish deployed multipart/image-count limits. No invented image-count cap.
2. `PUT users/me` accepts required name/email/phone and optional region. **No username field**. Supplier business update accepts businessName/location and optional businessDetails/verificationInfo; verification references remain read-only in the UI.
3. Deployed order status advertises PUT/POST with query status; logistics status PUT/POST with query status; cancel POST with optional query reason; delivery OTP POST with query otp. Never send guessed PATCH/JSON contracts.
4. Inspected ResourceAuthorization allows buyer(order.buyerId) only for BUYER/admin. Seller order lines do not authorize a whole mixed-seller order. Order creation/cancellation/status are admin-restricted in that source. This blocks the requested Supplier-to-Farmer purchase lifecycle and seller fulfillment independently of a public route existing.
5. STK acceptance is not payment success. Status must come from GET payment status/order payments after callback. Android live Buyer path currently has a simulated STK dialog and locally generated paid orders; this is a release-blocking defect, not a verified working Android STK integration. Do not modify backend Daraja integration.
6. Email OTP request is email; response challengeId UUID, expiresInSeconds, resendAfterSeconds/message. Verify is challengeId/email/code; response is success envelope with nullable data. Request is generic even for unknown/already-verified addresses. Verification does not grant approval or issue tokens.
7. Backend source contains STOMP tracking support plus REST location/events. A WebSocket controller alone does not verify deployed authenticated handshake/subscription rules. Prefer visible-screen lifecycle-aware REST polling until those are confirmed. Driver role is currently unsupported in Android navigation; no silent/background collection.
8. No authenticated fixtures were used, so deployed role authorization, approval fixtures, actual upload constraints, callback delivery and cross-account isolation remain NEEDS VERIFICATION.

## Prioritized implementation plan

1. **Phase 1:** Supplier photo discoverability/edit gallery; keep existing create-then-upload uncertainty handling; Material 3 pull gesture with existing single-flight gate; distinct account/business editing and session display refresh; versioned About. Audit Farmer/Inspector reload patterns and use compatible refresh controls.
2. **Phase 2:** Shared canonical paged marketplace repository and UI. Farmer sees active Supplier products; Buyer sees active Farmer products. Server search/category filters, refreshed details, images and sellable stock. Remove mock Buyer entry path and simulated checkout.
3. **Phase 3:** Verify deployed order/payment methods/DTOs and local authorization. Implement authorized buyer history/receipt/status reads; block create/cancel/seller fulfillment where authorization incompatible. Preserve backend STK/callbacks, never show paid from initiation.
4. **Phase 4:** Explicit email verification challenge screen reachable from auth, respecting expiry/resend and preserving login/recovery. No auto-registration replay, token fabrication or OTP persistence/logging.
5. **Phase 5:** Implement supported buyer-linked job/event/location reads with lifecycle-aware polling. Stop Farmer/Supplier/Driver portions individually where role support or authorization remains unresolved. Document exact gaps.
6. **Phase 6:** Regression: JVM request/serialization/ownership/state tests, Compose compilation and targeted device tests when available. Verify no fake live success, no duplicate writes, stale-data retention, supported navigation. Record actual commands/results, including unrun acceptance tests.

## Phase execution results

The coverage table above preserves the pre-implementation baseline. The following table records the resulting integration status. Source verification and synthetic tests are separate from authenticated acceptance.

| Role | Screen/Feature | API Endpoint | Current Status | Required Work | Backend Gap |
| --- | --- | --- | --- | --- | --- |
| Supplier | Draft/detail/edit product photos | POST/GET products/{id}/images | CONNECTED | Device picker/upload acceptance with an owned pending product | Actual deployed file/count limits still unverified |
| Supplier | Primary/delete/reorder gallery | PUT .../{imageId}/primary; DELETE .../{imageId}; PUT .../images/reorder | CONNECTED | Device acceptance; published changes remain restricted pending reapproval policy | No image mutation idempotency/version contract |
| Supplier | Business profile photographs | POST/GET profiles/suppliers/{userId}/images | CONNECTED | Device acceptance through Edit profile > Business | No upload idempotency; separate durable uncertainty marker protects retries |
| Supplier/Farmer/Buyer/Inspector/Driver | Account editing and About | GET auth/me; PUT users/me | CONNECTED | Device validation at small sizes/large fonts | Username is not supported by update DTO; email retained unchanged in editor |
| Supplier | Business editing | GET/PUT profiles/suppliers/{userId} | CONNECTED | Device acceptance | Verification reference is preserved from a fresh read, not exposed as an editable field; no ETag for concurrent administrator edits |
| Supplier/Inspector/Farmer | Pull-to-refresh | Existing repositories; role-scoped data routes | CONNECTED | Execute updated gesture instrumentation tests on device | None; Supplier/Inspector single-flight gates retain successful records |
| Farmer | Supplier marketplace | GET products/search; categories/type/SUPPLIER; products/{id} | CONNECTED | Verify an approved Supplier fixture appears for a Farmer | Approval/publication not bypassed; purchase permission remains blocked separately |
| Buyer | Farmer marketplace | GET products/search; categories/type/FARMER; products/{id} | CONNECTED | Verify an approved Farmer fixture appears for a Buyer | Seller name/county absent from product DTO; displays actual seller ID |
| Buyer | Orders, timeline, receipt, payment status | GET orders/buyer/{id}; orders/{id}; timeline; payments/order/{id}; payments/{id}/status; POST confirm-receipt | PARTIALLY CONNECTED | Device acceptance; implement checkout/cancel after authorization resolution | Creation/cancellation incompatible with inspected service authorization |
| Buyer | Checkout/STK initiation | POST orders; POST payments/stk-push/buyer | NEEDS VERIFICATION | Confirm deployed purchaser authorization and safe order creation before enabling new payment requests | Buyer route exists in deployed OpenAPI, absent from inspected Supplier backend source; no cross-version guessing |
| All | Email OTP | POST auth/email-verification/request; /verify | CONNECTED | Real email delivery/expiry/resend acceptance | Request intentionally cannot distinguish unknown/already-verified accounts; verification issues no JWT |
| All | Password recovery email | POST auth/forgot-password?email= | PARTIALLY CONNECTED | Verify delivered recovery email and its hosted reset link | In-app reset-token/app-link handling unverified; no fabricated local reset |
| Buyer | Delivery events/location/map | GET logistics/jobs/order/{id}; jobs/{jobId}/tracking; tracking/{jobId}/location | CONNECTED | Device acceptance with assigned driver and real reports | Events may be absent; Android does not infer milestones |
| Driver | Provisioned canonical login/navigation, assigned jobs, acceptance/status/OTP | GET jobs/driver/{id}, jobs/{id}; POST accept; PUT status; POST otp/verify | CONNECTED | Device acceptance using provisioned Driver; no Driver self-registration enabled | Assignment remains administrator-only; Android never self-assigns |
| Driver | Foreground location reporting | POST tracking/{jobId}/location | CONNECTED | Test allow/deny, fresh GPS fix, reassignment, screen exit, background stop | No background permission/service introduced; report accepted only in verified active states |
| Supplier/Farmer | Seller orders and fulfillment | Existing whole-order routes | MISSING BACKEND API | Authorized seller line/feed projection needed | Buyer/admin whole-order authorization is not seller authorization |
| Farmer | Supply purchasing/history/tracking | Existing order/job routes | MISSING BACKEND API | Purchaser-role authorization and linkage needed | Inspected buyer predicate rejects Farmer; do not impersonate Buyer |
| Supplier | Delivery discovery | logistics/jobs/order/{orderId} | MISSING BACKEND API | Authorized seller-order/job projection needed | A location permission on a known job does not supply job discovery or tracking-history access |

### Phase 1 result

* Product Photo Picker is exposed during creation, with a bounded JPEG preview. Creation still happens once through the existing durable product marker; the returned product ID is opened before the separate, explicit upload confirmation. Selected draft photo bytes stay in memory. After rotation/process death, select the photograph again on the existing saved product; do not recreate it.
* Edit screens show both the uploaded gallery and the uploader. Upload/gallery completion no longer closes an unsaved text edit draft. Primary image changes, move-up reorder and deletion use source/deployment-verified PUT/DELETE contracts. Gallery actions share the existing Supplier mutation gate and durable marker.
* Product image loading now shows progress and offers explicit retry. Upload success requires the returned image ID in a refreshed product gallery; a missing gallery result remains uncertain and cannot silently become a safe upload retry.
* Profile photographs have a separate API, user ID, POST list response, picker, gallery and marker key. No product ID is used. An uncertain profile upload requires reloading/reviewing existing photos and explicit acknowledgment.
* Supplier toolbar reload removed; Material 3 pull gesture uses existing guarded refresh with stale-data retention. Farmer profile and Inspector toolbar reloads replaced too. Existing crop/detail gestures preserved; Farmer product overview also permits refresh. Inspector refresh now coalesces requests and retains successful jobs/profile if reload fails.
* Common account editor preloads full name, phone and region; retains email; leaves username/role/status/verification read-only. Account updates rotate only display fields into the current session while preserving its latest token pair. Supplier business editor separately updates businessName/businessDetails/location and preserves the fresh verification reference. About uses BuildConfig versionName/versionCode and existing project role information, with no invented contacts/policies.
* Initial phase build/test invocation passed. Expanded gallery/profile regression coverage is included in the final 302-test run described below. Device behavior is not yet accepted.

### Phase 2 result

* A shared MarketplaceApi/MarketplaceRepository backs Farmer input and Buyer produce screens. Server `products/search` uses sellerType/name/categoryId with page/size/sort; active categories come from the existing role-type route. Lists page using Load more; filters and detail reloads retain prior records on read failures.
* Products must have the expected seller type, active backend status and positive sellable stock (`stockQty - reservedQty`). Mapping reuses canonical DTO validation and exact decimal money. Unknown/invalid ownership/type/page responses fail closed. No mock products or other sellers' unpublished products are substituted.
* Product detail is fetched again and rechecks status/availability. Backend image URLs use existing bounded, authenticated same-origin image transport. Seller ID is shown because product DTO has no verified seller display-name projection.
* Buyer navigation now opens BuyerShell, not the old mock BuyerScreen/CheckoutBottomSheet/OrderTrackingDialog. The simulated M-Pesa PIN collection and locally generated paid orders are unreachable through the live Buyer path. Backend Daraja was not changed.
* Farmer home Supplies/Orders shortcuts now open the corresponding real shell destinations. Unsupported order/supply counts display Unavailable instead of invented zero metrics. Separate legacy FarmerDashboardScreen sample charts are preview code, not the FarmerShell entry point.

### Phase 3 result and precise blockers

* Buyer orders load from the authenticated buyer ID, with independent fresh detail, timeline, order payment records and individual pending-payment status reads. Ownership and enum validation reject foreign/unknown responses. Detail refresh/polling never turns an STK acceptance into a successful payment.
* Only delivered orders offer receipt confirmation, with an explicit confirmation dialog explaining settlement. A failed/uncertain receipt is not automatically replayed; fresh server reads reconcile it. Other seller/admin status transitions are not exposed.
* New order creation, cancellation and seller collection/fulfillment are intentionally not enabled: inspected OrderService is admin-restricted for create/cancel/status, ResourceAuthorization.order is buyer/admin, and sellers have no safe incoming feed. OpenAPI alone does not prove a compatible deployed authorization implementation. No administrator endpoint or client-side filtering is used to evade these restrictions.
* Deployed PaymentResponse statuses are pending/paid/held/settled/failed/refunded. There is no separate cancelled-transaction status or provider result-code field in that DTO; Android cannot reliably distinguish user-cancelled STK from other failures. Backend must expose a supported cancellation reason/code if a distinct cancelled UI is required.
* The working backend STK/callback flow is preserved. No live STK request, callback, paid-order mutation or credential was used in this audit. Full checkout/payment acceptance remains incomplete.

### Phase 4 result

* Verify email/resend is reachable from Sign In and from registration after an account exists. The existing Supplier login and explicit business-setup recovery sequence is preserved; there is no automatic registration replay.
* Request sends email only. Verify sends UUID challengeId/email/six-digit code. The nullable-data success envelope is handled with requireSuccess. Challenge expiry and resend delay use monotonic time; unknown/already-verified accounts receive the generic backend-compatible explanation. The user returns to login after verification; no token or approval is fabricated.
* OTP values remain memory-only and request DTOs redact toString. No HTTP logger or credentials were added. Live mail delivery and rate-limit responses still need fixture acceptance.

### Phase 5 result

* Buyer tracking discovers jobs by the verified owned order, offers job selection when needed, validates job/order IDs, and accepts locations only for the matching job/order/current assigned driver. Actual recorded events are ordered by their backend timestamps; no unrecorded transition is marked completed.
* REST refresh runs sequentially every 15 seconds while the tracking composable is visible/STARTED, backs off to 60 seconds after failures, and stops for terminal jobs. Switching jobs/reassignment clears mismatched location. A 404 means no reported position yet; other failures label any retained position as last known. Mapbox marks actual reported coordinates and displays server timestamp/accuracy. Viewer device location permission is not requested.
* Canonical Driver login has a dedicated shell; legacy unsupported Driver navigation remains unchanged. Assigned jobs are ownership-checked before writes. Acceptance uses POST with driverId query; status follows accepted → en_route_to_pickup → arrived_at_pickup → picked_up → in_transit → arrived_at_destination. Recipient six-digit handover OTP completes delivery through the server. No arbitrary delivered transition, assignment, cancellation or fake OTP is offered.
* Driver location sharing starts only after a visible action and runtime permission. Each report rechecks driver assignment/job state and uses a fresh location fix. Reports are sequential, approximately every 15 seconds, and stop on permission/error, leaving the job screen or app pause. No background location permission, persistent service or undisclosed collection is introduced.
* Source contains STOMP/WebSocket code, but deployed authenticated connection/subscription access is not verified, so Android uses REST polling. Status changes and tracking event history are separate backend data: if an event was not recorded, the UI shows only the actual current job status.
* Supplier/Farmer tracking remains blocked at safe order/job discovery/purchasing authorization, even though known-job location reads may permit sellers. These role gaps are not solved by enabling Driver support.

### Phase 6 validation and remaining work

Validation command (JDK 21, local cached dependencies, offline):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

* Final synchronized validation: **303 JVM tests, 37 suites, zero failures/errors**. Both debug app and instrumentation APKs built successfully. Android lint passed with **zero errors, 51 warnings and one hint**; warning details remain in `app/build/reports/lint-results-debug.html`.
* Added `ApiIntegrationAuditTest` covers role-separated visibility, pending/reserved-out filtering, page/duplicate validation, late logout replies, account payload restrictions, email challenge/void responses and OTP redaction, gallery methods/reorder schema, profile upload route/response separation, buyer ownership/status checks, receipt/payment methods, job/order/driver coordinate linkage, Driver transition/ownership/OTP preflight and canonical-only Driver routing. Inspector adds single-flight/stale-data regression.
* Updated Supplier, Farmer profile and Farmer shell instrumentation tests for gestures, absent toolbar reloads, photo-picker access and unsaved edit preservation. No device was attached initially; Samsung `RF8WC0K043H` connected during final validation. Both APKs were installed with `adb install -r`, preserving app data. Focused device results are recorded below. No uninstall/data clearing was performed.
* **Final device result: OK (23 tests), zero failures.** Classes: SupplierUiTest (18), FarmerProfilePresentationTest (2), FarmerShellPresentationTest (3). The first run passed 20/23: two new checks needed lazy-list parent scrolling to compose offscreen photo controls; one exposed a real nested image-retry action interfering with thumbnail selection. Fixed the scroll selectors and disabled thumbnail retry buttons while retaining retry on the selected large image; the complete 23-test rerun passed after rebuilding/reinstalling in place. Initial/final transcripts: `app/build/reports/api-audit-device-tests.txt` and `app/build/reports/api-audit-device-tests-rerun.txt` (ignored build artifacts).
* No authenticated production acceptance has been performed. Still verify two Supplier accounts for cross-owner isolation; pending photo upload/retry/restart; approval leading to Farmer marketplace visibility; Farmer publication leading to Buyer visibility; actual email receipt/expiry/resend; authorized buyer history/receipt; Driver accept/status/handover; foreground GPS permission denial/background stop/reassignment; Buyer map timestamps; M-Pesa callback-driven success/failure/cancellation semantics.
* Password-recovery preview has also been replaced: the screen now requests actual backend reset instructions via POST forgot-password with query email. It displays the generic acknowledgment only after a success envelope, and sends the user to the backend's emailed reset link. No demo OTP/new-password simulation remains in that screen. In-app token handling/app links are still unverified; the app does not claim the password changed merely because the email request succeeded.
* Remaining supported-but-unimplemented surfaces: in-app token-based password reset/app links, farm/business-specific fields outside the Supplier editor, and legacy/demo-only local screens. Inspector evidence upload retains its existing privacy blocker. These are not claimed complete or reclassified as missing endpoints.
* Remaining backend/contract decisions: authoritative deployed backend source/version, seller-safe order lines/feed and allowed fulfillment transitions, Farmer purchaser authorization, Supplier order/job discovery, seller payment status access, distinct STK cancellation reason, published-listing reapproval policy, upload deployment limits, durable idempotency/versioned stock writes and authenticated WebSocket policy.
* Lint initially could not run offline because lint-gradle 31.11.2 was not cached; after the approved dependency download, it found one new Compose StateFlow read error and three pre-existing errors. Fixed the new reactive session read, the API-28-only Farmer location-enabled call (now LocationManagerCompat), the API-27 navigation-bar attribute (now values-v27), and FarmerBottomBar indentation. No baseline or error suppression was introduced.
* Existing dirty IDE/auth/Supplier redesign files were preserved. No backend source was modified, and no commit, push, reset or stash operation was performed.

Artifacts: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, JVM report `app/build/reports/tests/testDebugUnitTest/index.html`. Application ID from generated metadata: `com.example.farmapp.dev2231918adc4d4b9d`, version 1.0 (1).

Focused device command (direct instrumentation avoids connected-test cleanup/uninstall):

```powershell
adb -s RF8WC0K043H shell am instrument -w -r -e class com.example.farmapp.supplier.SupplierUiTest,com.example.farmapp.ui.screens.FarmerProfilePresentationTest,com.example.farmapp.ui.screens.FarmerShellPresentationTest com.example.farmapp.dev2231918adc4d4b9d.test/androidx.test.runner.AndroidJUnitRunner
```
