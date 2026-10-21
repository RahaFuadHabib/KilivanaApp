# Supplier Android module

## Supplier self-registration (2026-10-19)

Supplier signup now uses the verified canonical backend workflow: `POST auth/register` with account fields only, `POST auth/login`, then authenticated `POST profiles/suppliers/{userId}` with `businessName` and physical `location`. Android now requires a supported Kenyan county/region, town/sub-county and detailed physical address; username remains optional for Supplier. No approval/Inspector-verification state is invented. Backend-returned status/verification remain authoritative. Existing Farmer register/login/farm-profile sequencing and Inspector authentication are preserved; Inspector self-registration is disabled in the UI and canonical service.

### Single-page signup and validation

Supplier and Farmer signup use one vertically scrollable form, with personal information, location/business and account-security sections. The shared layout applies keyboard insets; fields offer Next navigation and confirmation offers Done submission. Supplier county selection uses the 47 canonical names from the read-only backend `KenyaCounty` source, mirrored in `KenyanCounties.kt`. The account request sends the canonical county as `region`; only the existing Supplier profile `location` receives the normalized `Town, detailed address` string. Business name stays separate. No new DTO fields, endpoints or location permissions are introduced.

`SignupFormValidation.kt` provides field-specific errors before any signup/recovery callback. County, town and detailed address are mandatory; county or town alone is rejected as the detailed address. Password confirmation is mandatory and must match exactly, with independent accessible visibility toggles. Confirmation never enters `SignupRequest` or the backend payload. Passwords remain in memory rather than saved instance state. Other entered fields survive configuration changes, and validation failures preserve input. Local submission gating and existing repository/session protections remain in effect. Explicit Supplier recovery retains its existing login/read/create-if-missing sequence and accepts the existing password, with local confirmation.

Current JVM validation: **247 tests passed across 33 suites**, zero failures/errors/skips. Both debug app and instrumentation APKs built with `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain`. The signup UI class now contains 14 tests (including county/town/address requirements, confirmation, visibility, one-page fields and Farmer payload regression); existing Supplier and shared-auth coverage is retained. All automated registration tests use synthetic callbacks or mock HTTP servers, never live accounts.

Samsung device validation: both APKs installed in place with `adb install -r`, preserving app data. The first 30-test run had six missing-Compose-hierarchy failures while the phone was locked (five shared-auth tests and one signup test); the other 24 passed. After waking and dismissing the keyguard, a complete fresh run passed **OK (30 tests)**: 14 signup, 11 Supplier module and five shared-auth tests, zero failures. No implementation change was required for the locked-device failures. Transcripts are ignored build artifacts: `app/build/reports/supplier-single-page-device-tests.txt` and `app/build/reports/supplier-single-page-device-tests-rerun.txt`. No live backend writes were made.

`network/canonical/SupplierSignup.kt` isolates the Supplier sequence and explicit recovery. Registered and authenticated IDs, role and email are checked before the profile write; returned profile ID/userId are checked before session establishment. Temporary bearer credentials are used only for that authenticated Supplier. `AuthRepository` checks the originating anonymous session before and after each step, rejects account switches/late replies and uses an atomic setup gate. The validated session is persisted only after business setup is confirmed, then existing navigation opens SupplierShell.

After an uncertain registration response, conflict, cancellation, login failure or profile failure, another Supplier registration is blocked in the current AuthRepository instance. The UI switches to **Sign in and finish setup**. Explicit recovery performs login and GET of the authenticated Supplier profile; it creates a profile only after a 404, and never calls registration or overwrites an existing profile. A forbidden/failed lookup does not trigger a write. Definite account-validation rejections allow correcting the original form. Passwords and temporary tokens are not saved with form state; recovery accepts the existing account password instead of applying new-password rules.

The recovery latch is memory-only, not a durable registration idempotency key. After process death/restart, select Supplier and **Already registered? Finish Supplier setup**, re-enter credentials and business details, and continue without registering again. Lost credentials, prolonged backend failures, delayed server completion or concurrent profile creation can still require administrator assistance. No automatic registration/profile retry or product-operation-marker reuse is implemented. If already signed in through ordinary login with a missing profile, use administrator assistance or return to the explicit setup flow after signing out. This is separate from the preserved durable product-submission recovery.

Synthetic tests cover successful account/login/profile sequencing and payload separation, optional account fields, validation, Inspector rejection, existing Farmer regression, post-registration login/profile failures, existing-profile reconciliation, rejected ownership, unknown/conflict outcomes, cancellation, duplicate actions and account changes. New classes: `SupplierSignupTest` (20 JVM tests) and `SupplierSignupUiTest` (6 Compose tests). The obsolete shared authentication UI test expecting Inspector self-registration is replaced with a disabled-signup expectation; its duplicate-submit fixture uses a Buyer instead of an Inspector.

No live account, profile, product, image or payment request was sent during automated validation. No backend source, Farmer/Inspector implementation, existing IDE changes or Git stashes were modified. No commit or push was made.

| Step | Request | Response expected |
| --- | --- | --- |
| Register | POST `/api/v1/auth/register`: name, email, phone, password, role=SUPPLIER, region=selected county; optional username. No business fields. | ApiResponse containing canonical user ID/role/email/status; no JWTs. |
| Authenticate | POST `/api/v1/auth/login`: email, password. | ApiResponse containing accessToken, refreshToken, tokenType, expiresIn and user. |
| Business setup | POST `/api/v1/profiles/suppliers/{userId}`: businessName, location, temporary bearer. | ApiResponse containing positive profile ID and matching userId. |
| Explicit recovery | GET `/api/v1/profiles/suppliers/{userId}` after login, with bearer. | Existing owned profile, or 404 before explicit missing-profile creation. Other failures block the write. |

Signup validation completed: **237 JVM tests passed, zero failures/errors/skips**, across 32 suites; both debug APKs built. Final device follow-up on the reconnected Samsung (`RF8WC0K043H`) passed **all 17 Supplier Compose tests** (6 signup/recovery and 11 existing Supplier tests) and **all 5 shared authentication tests**, with zero failures. This run includes the existing-password recovery adjustment and the updated Inspector-signup expectation. The final app and instrumentation APKs were installed in place with `adb install -r`; no uninstall or app-data clearing was performed. No implementation/test fix was required during this follow-up. Transcripts: `app/build/reports/supplier-signup-device-tests.txt` and `app/build/reports/shared-auth-device-tests.txt` (ignored build artifacts). The build command completed successfully using current up-to-date outputs; JVM results were reused by Gradle, while all 22 requested device tests were freshly executed.

The device runs were explicitly filtered to synthetic classes:

```powershell
adb -s RF8WC0K043H shell am instrument -w -r -e class com.example.farmapp.supplier.SupplierUiTest,com.example.farmapp.supplier.SupplierSignupUiTest com.example.farmapp.dev2231918adc4d4b9d.test/androidx.test.runner.AndroidJUnitRunner
adb -s RF8WC0K043H shell am instrument -w -r -e class com.example.farmapp.network.AuthFlowIntegrationTest com.example.farmapp.dev2231918adc4d4b9d.test/androidx.test.runner.AndroidJUnitRunner
```

## Phase 2: authenticated loading, refresh and forms (2026-10-19)

The existing module from commit `88ac05a` is extended on `feature/supplier`; it has not been rebuilt or replaced. No backend source, Farmer implementation, Inspector implementation, stashes or pre-existing IDE changes are modified. No remote catalogue writes, payment requests, commits or pushes are performed in this phase.

- Supplier-tagged canonical GET/HEAD requests refresh expired, near-expiry (30-second margin) or unknown-expiry access tokens before sending. Refresh failure fails closed instead of accepting an anonymous public catalogue. Shared refresh coordination is reused. Session identity, generation, role, contract and origin are checked after proactive refresh and after a 401 refresh before a read retry. Other modules do not opt into Supplier proactive refresh; canonical writes still are not refreshed/replayed automatically.
- Owned catalogue list/detail reads first verify the authenticated account through protected `auth/me`, including role and user ID. This prevents a successful public product response from being used as proof of owner visibility. Existing repository ownership checks and response validation remain in place. These are the existing endpoints, not new backend contracts.
- Refresh retains successful listings, categories, profile and selected detail while displaying a linear refresh indicator. Errors remain visible until the corresponding read succeeds; failed reads do not replace valid records with empty results. A successful-catalogue flag distinguishes a real empty catalogue from an initial failure. Selected details reload independently, and stale detail completions cannot replace another selection. An atomic refresh gate coalesces repeated actions and excludes mutations/reconciliation while refresh is active.
- Create/edit drafts and their original values are hoisted above lazy-list rows and saved through Compose saved instance state, scoped to the current session generation. They survive configuration changes and are not reset by refreshed product details. They are not stored in SupplierPreferences or used for automatic submission recovery. Navigation back and explicit logout ask before discarding changed fields; session expiry/account switching still clears the private session UI immediately.
- Validation now returns messages for name, description, unit, price, available stock, minimum quantity and category. Fields show errors after review and update them as values change. Keyboard Next moves focus; Done validates/reviews without submitting directly. Empty, loading and unavailable categories have explicit explanations in both create and edit flows. Submission requires available categories and explicit confirmation. Confirmation is consumed once, and the existing atomic mutation gate and durable uncertainty markers remain authoritative.

Synthetic JVM coverage adds expired/unknown-expiry sessions, denied/transient refresh, owner-only pending visibility, account/role changes during requests, no write replay, protected identity failure, retained refresh data/details, duplicate refresh/mutation exclusion and field/category validation. Compose coverage adds saved-state draft restoration, dirty back/logout confirmation, refresh preservation, edit-draft preservation, missing categories, field errors and double-tap confirmation.

Validation command (existing local Gradle/SDK caches, JDK 21):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

Validation completed on 2026-10-19: **217 JVM tests passed, zero failures/errors/skips**, across 31 suites. This includes 41 Supplier JVM tests (26 existing module tests, 3 existing transport tests, 12 new reliability tests). Both `assembleDebug` and `assembleDebugAndroidTest` succeeded. Device follow-up below supersedes the initial compile-only UI validation. No remote endpoint is called by the synthetic tests. Build outputs are `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

### Device validation and phone launch

The full JVM task was explicitly rerun with `:app:testDebugUnitTest --rerun --offline --console=plain` and passed. After the review fixes, the combined build/test command above also passed. A Samsung Galaxy A15 (`SM-A155F`, Android 16), serial `RF8WC0K043H`, was connected and authorized. Both APKs were installed with `adb install -r`; no uninstall or data clearing was performed. The test runner was invoked directly to avoid connected-test cleanup uninstalling an existing app:

```powershell
adb -s RF8WC0K043H install -r app/build/outputs/apk/debug/app-debug.apk
adb -s RF8WC0K043H install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s RF8WC0K043H shell am instrument -w -r -e class com.example.farmapp.supplier.SupplierUiTest com.example.farmapp.dev2231918adc4d4b9d.test/androidx.test.runner.AndroidJUnitRunner
```

**Final device result: OK (11 tests), zero failures.** The initial run passed 10/11: the refresh-preservation test attempted to find an offscreen lazy row before composition. It now scrolls through the lazy-list container to the target and asserts visibility; the entire class passed after rebuilding. Review also corrected a stray question-mark separator in the supply confirmation text. The final synthetic instrumentation transcript is an ignored build artifact at `app/build/reports/supplier-device-tests.txt`.

The configured and generated BuildConfig base URL agree: `https://either-juvenile-progeny.ngrok-free.dev/api/v1/`, canonical backend true, mock auth false. Read-only workstation GET checks returned HTTP 200 JSON for `/api/v1/health` and `/api-docs`; the document identifies Kilivana Backend API, OpenAPI 3.0.1, and lists the expected Supplier auth/profile/category/product routes. This establishes reachability and advertised route compatibility, not authenticated Supplier acceptance or the exact deployed commit. Local ngrok inspection at `127.0.0.1:4040` was unavailable; no local tunnel is needed to use the reachable remote origin. Older 404 observations later in this document are historical.

The backend-specific debug package is `com.example.farmapp.dev2231918adc4d4b9d`. Multiple Kilivana variants are installed, so launch this exact component instead of selecting an ambiguous icon:

```powershell
adb -s RF8WC0K043H shell am start -n com.example.farmapp.dev2231918adc4d4b9d/com.example.farmapp.MainActivity
```

Use administrator-provisioned Supplier credentials for manual sign-in, then check profile, own listings/details and refresh. Form validation, rotation and discard prompts can be checked without submitting. Do not press Submit supply, Confirm upload or payment actions in this read-only pilot. No real login, authenticated Supplier read, remote product write or payment was performed during this device validation. Existing app data, IDE changes and Git stashes were preserved; no commit or push was made.

Backend blockers remain deployment/fixtures, provisioning and moderation policy, server idempotency/versioned stock updates, Supplier order/fulfillment and delivery discovery, and safe seller payment status/collection/reconciliation. Current backend source has seller-authorized `GET tracking/{jobId}/location` and `GET tracking/{jobId}/status`; Android still lacks a Supplier-safe delivery/job discovery integration. This qualifies the older delivery-blocker wording below.

## Scope and source control

Implemented on `feature/supplier`, which started with a clean working directory. No Git stash was inspected/applied/dropped/modified, no branch was switched, and no push was performed. The reviewed Supplier implementation is prepared for a local commit. Inspector source and Farmer workflows remain unchanged. Shared changes add Supplier API construction/navigation, disable Supplier self-registration in the existing sign-up UI, and reject session-tagged requests after role/backend-contract changes. The Kotlin build cache is ignored. The configured ngrok URL is unchanged.

This module implements the parts supported safely by the reviewed backend: provisioned-account entry, Dashboard, My supplies, listing creation/details/pending-only editing, product photographs, and read-only business profile. Orders, fulfillment, delivery and payments have explicit unavailable states because safe Supplier contracts are incomplete; no mock orders, customers, drivers or successful payments are shown.

## Screens and authentication

- Existing email/password login, JWT refresh, encrypted persistence, backend-origin binding and role `SUPPLIER` are reused. Supplier accounts may now be self-registered using the sequence above or provisioned by an administrator. No client-side activation/approval state is added. Canonical signup explicitly supports Farmer and Supplier, while Inspector self-registration remains disabled.
- Dashboard shows total listings, active listings, pending approval and out-of-stock counts from the authenticated seller's returned products. Available stock is `stockQty - reservedQty`; no low-stock threshold or order statistics are invented. Recent listings are sorted by ID descending, not labelled as orders.
- My supplies lists only verified own Supplier products. Details display actual status, price, available/reserved stock, minimum quantity and returned image gallery.
- New supply form validates name, description, unit, active Supplier category, price, whole available stock and minimum quantity; a confirmation dialog precedes submission. Creation uses the backend-derived seller and returns a known product ID before separate photo uploads. No status/seller override is sent.
- Editing and photo changes are restricted to **pending** listings. The backend permits editing published content without automatically restarting moderation; Android does not offer that bypass until Daniel defines reapproval. Edits re-read current reservations and write total stock as entered available stock plus reservations, with overflow protection. No unlisting/delete or invented inventory API is exposed.
- Photo Picker selects a single product image. No broad storage permission, camera permission or Supplier disk photo cache is needed. Inputs are bounded to 5 MiB and 50 million decoded pixels, decoded with sampling, then re-encoded as JPEG at a bounded size; original metadata is removed. A preview and explicit confirmation precede upload. MIME/signature/filename/size validation runs before networking. The backend's storage limit is 10 MiB in source, but deployment multipart limits may differ; Android deliberately uses 5 MiB.
- Upload uses only the product-image endpoint with multipart `image`, next gallery sortOrder and isPrimary for the first photo. UI shows indeterminate submission progress and waits for the actual response/refreshed gallery; no percentage or success is fabricated. Interrupted/failed uploads never automatically replay.
- Profile combines `/auth/me` with the current user's Supplier business profile. It displays name, email, phone, account/verification status, region, businessName, businessDetails, location and an available primary/first profile image. Admin-assigned fields are read-only; verificationInfo is not exposed as a document/credential capture flow. A profile 404 clearly requests administrator provisioning rather than creating a profile from Android.
- Orders explains the missing seller feed; Delivery explains the absent Supplier-authorized projection; M-Pesa uses a reusable read-only readiness/status component without payment initiation controls.

Inactive/suspended accounts are rejected by existing backend login. No must-reset-password/activation flag was found in the reviewed login DTO, so no activation workflow is invented. Daniel must provision usable credentials and clarify any deployed onboarding/reset requirements. Existing Farmer and Inspector authentication rules are preserved.

## Integrated source-verified API

All paths are relative to `/api/v1/`, use the existing authenticated OkHttp client, and expect `CanonicalApiResponse<T>`.

| Operation | Contract |
| --- | --- |
| GET `auth/me` | Existing canonical user DTO; account ID and role must match the active Supplier. |
| GET `profiles/suppliers/{id}` | Own user ID only; id, userId, businessName, businessDetails, location, verificationInfo, createdAt, updatedAt, images. |
| GET `categories/type/SUPPLIER` | Canonical category DTO; reject other seller types, invalid/duplicate IDs; expose active categories only. |
| GET `products/seller/{id}` | Own Supplier ID only; reject mixed-owner or Farmer products rather than silently filtering leaks. |
| GET `products/{id}` | Revalidate selected ID, owner and seller type before details/edit/upload. |
| POST `products` | JSON name, description, unit, price, stockQty, minimumOrderQty, categoryId; creation must return pending approval. |
| PUT `products/{id}` | Same JSON shape; fresh reservations preserved. No seller, moderation or approval property. |
| GET `products/{id}/images` | Current gallery for the verified owned product. |
| POST `products/{id}/images` | Multipart image, sortOrder/isPrimary query; actual returned metadata and refreshed gallery validated. |

Canonical product fields reused: id, sellerId, sellerType, categoryId, name, description, unit, price, stockQty, reservedQty, soldQty, minimumOrderQty, status, timestamps and images. Status wire values: active, pending, rejected, suspended. The existing pure canonical validation projection is reused without coupling Supplier UI/state to Farmer repositories/screens.

## Deployment evidence and limitations

The handover's earlier successful OpenAPI inspection is not evidence of today's deployment. In this task, safe unauthenticated GETs returned:

| URL | Actual result |
| --- | --- |
| `https://either-juvenile-progeny.ngrok-free.dev/api-docs` | HTTP 404 |
| `/api/v1/health` on that origin | HTTP 404, text/plain |
| `/api/v1/categories/type/SUPPLIER` on that origin | HTTP 404, text/plain |

Consequently no Supplier API is claimed runtime-verified in the current deployment. Source contracts and permission guards were inspected read-only; authenticated live testing requires Daniel to restore/confirm the tunnel and deployment. No credentials, personal records, API writes, STK prompts or transactions were used during automated validation.

## Architecture and isolation

`supplier/SupplierApi.kt` exposes only supported profile/catalogue routes. `SupplierModels.kt` defines Supplier domain data, validation and derived metrics. `SupplierRepository.kt` verifies canonical role/user/origin/generation, validates complete responses and performs owned operations. All requests carry the originating SessionSnapshot tag, so queued writes cannot acquire another account's token. Existing interceptor refresh handles reads; canonical writes are not replayed on 401 and redirects remain disabled.

`SupplierViewModel.kt` wraps the independently testable SupplierStateHolder. Its StateFlow has independent catalogue/category/profile errors, detail state and one atomic mutation gate. Logout/account changes immediately hide and clear Supplier profile/list/detail data; stale completions are rejected. Token rotation retains the session generation. `SupplierShell.kt` supplies Material 3/Kilivana navigation, confirmation/forms and responsive scrollable layouts. `SupplierPhotoPicker.kt` holds selected/prepared bytes only in memory. Existing session-aware ProductImage is reused for backend/Cloudinary loading, preserving exact-origin bearer restrictions and private-image cache isolation.

`SupplierOperationStore.kt` saves a **minimal durable uncertainty marker before a mutation**: CREATE/UPDATE/UPLOAD and optional product ID, keyed by SHA-256 of backend URL plus account ID. It stores no tokens, form contents, descriptions, image bytes, customer data or private URLs. These safety markers intentionally survive logout/process death and are never displayed under a different account/backend; all actual Supplier-private content is cleared. A success or definite rejection clears the marker. Unknown failures keep the account blocked from new mutations. Accepted uploads followed by a failed refresh remain uncertain even when that refresh returns 404.

After an uncertain outcome, inspect My supplies and the relevant gallery, then explicitly review/reload/acknowledge the marker. Acknowledgement performs fresh owned reads, never retries the original write, and shares the atomic submission gate so repeated acknowledgements cannot clear a newer operation marker. Server idempotency is still necessary to eliminate delayed-completion/process-death/concurrent-actor ambiguity completely. A confirmed listing ID remains visible if clearing a safety marker fails. Storage failure before recording the marker prevents the API mutation.

`payments/PaymentStatusCard.kt` is a reusable read-only component for a future authorized server status. `supplier/CommerceContracts.kt` records exact source order/delivery enum spellings for future projections; no order/driver retrieval is wired. Unknown statuses remain unknown; they never become successful payment/delivery states.

## Running and testing

Keep `app` / `debug`, canonical backend true and mock auth false. Existing gradle.properties selects ngrok; BuildConfig is shared across login/refresh/API/image clients. No base URL or TLS changes were made. See existing development documentation for quoted `-Pkilivana.apiBaseUrl` localhost/emulator overrides; never connect Android to PostgreSQL.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21' # or your configured Android Studio JDK
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
# Start an AVD in Device Manager first, then run only the synthetic Supplier UI class:
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.farmapp.supplier.SupplierUiTest" --console=plain
```

Open SupplierShell.kt Design/Split mode for its clearly labelled synthetic preview. Synthetic unit/UI tests make no network or payment writes. Do not run unfiltered connected tests: a separate pre-existing opt-in canonical test can create records. No connected device was found in this task, so compiled UI tests are not reported as executed.

Validation completed on 2026-10-16: **205 JVM tests passed, zero failures/errors**, including 29 new Supplier tests. `assembleDebug` and `assembleDebugAndroidTest` succeeded. Four Supplier Compose tests compiled but were not executed. The suite covers provisioned login/no registration, profile/ownership, category/form/money/stock validation, product multipart uploads, accepted-upload refresh failure, duplicate prevention, durable marker failures/recreation, logout/account/role changes, safe errors and exact source commerce/payment spellings. Generated BuildConfig retains the ngrok URL, canonical backend true and mock auth false. No remote write or financial transaction was performed.

Manual pilot testing requires an administrator-provisioned disposable Supplier account, active Supplier categories and explicit approval for remote writes. First verify profile/list/category reads and account switching; then create one uniquely named disposable pending listing, upload one non-sensitive product photo, edit pending stock while preserving reservations, and verify it in Daniel's portal without approving it. Test timeout reconciliation without blindly repeating submissions. Do not use national IDs, business-license scans or land documents as product photographs. No Supplier order/payment/driver pilot is enabled until the dependencies below are resolved.

## Remaining work and proposed commit scope

See [SUPPLIER_BACKEND_REQUIREMENTS.md](SUPPLIER_BACKEND_REQUIREMENTS.md) and [MPESA_INTEGRATION_PLAN.md](MPESA_INTEGRATION_PLAN.md). Safe Supplier orders, fulfillment, driver tracking and STK initiation/status are blocked by backend contracts/permissions, not substituted with buyer/admin access.

Proposed review: the new Supplier module, reusable payment-status component, Supplier unit/Compose tests and these three documents; shared changes to ApiClient, AppNavigation, SignUpScreen and the request-tag guard in AuthTokenInterceptor, plus ignoring the generated root .kotlin cache. The additional tag check prevents a queued Supplier write from acquiring a Farmer token if refresh changes the role for the same numeric user ID/session generation. No Inspector implementation changes, Farmer stash application, IDE files, local.properties, credentials, Gradle caches, APKs or build outputs belong in that commit. The owner authorized selective staging and a local commit after review and validation; pushing requires separate approval.
