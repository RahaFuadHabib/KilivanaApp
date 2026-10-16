# Supplier Android module

## Scope and source control

Implemented on `feature/supplier`, which started with a clean working directory. No Git stash was inspected/applied/dropped/modified, no branch was switched, and no push was performed. The reviewed Supplier implementation is prepared for a local commit. Inspector source and Farmer workflows remain unchanged. Shared changes add Supplier API construction/navigation, disable Supplier self-registration in the existing sign-up UI, and reject session-tagged requests after role/backend-contract changes. The Kotlin build cache is ignored. The configured ngrok URL is unchanged.

This module implements the parts supported safely by the reviewed backend: provisioned-account entry, Dashboard, My supplies, listing creation/details/pending-only editing, product photographs, and read-only business profile. Orders, fulfillment, delivery and payments have explicit unavailable states because safe Supplier contracts are incomplete; no mock orders, customers, drivers or successful payments are shown.

## Screens and authentication

- Existing email/password login, JWT refresh, encrypted persistence, backend-origin binding and role `SUPPLIER` are reused. No Supplier registration or client-side account activation is added. Supplier selection in Sign Up explains administrator provisioning and disables submission. The canonical auth adapter already rejects non-Farmer registration before a request.
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
