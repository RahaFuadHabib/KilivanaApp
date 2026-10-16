# Inspector Android handover — Brian

## Start here

This feature belongs to the **Android** repository `https://github.com/RahaFuadHabib/KilivanaApp.git`, not Daniel's backend repository. Handover branch: `feature/inspector-handover`, based on Android commit `231b968` (`feature/auth-farmer-ui`). This adds Inspector work and its required, previously uncommitted canonical authentication/network/image dependencies. The base history already includes Farmer/authentication work; no Supplier or M-Pesa implementation is added here.

After the owner approves and pushes the branch:

```powershell
git clone --branch feature/inspector-handover https://github.com/RahaFuadHabib/KilivanaApp.git
cd KilivanaApp
git switch -c feature/brian-inspector
```

For an existing clone, first preserve your local work, then `git fetch origin`, `git switch --track origin/feature/inspector-handover` (only if no local branch already exists), and `git switch -c feature/brian-inspector`. Do not reset or overwrite an existing branch. Coordinate later pushes/PRs with the repository owner. The initial handover push is intentionally not automatic.

Open the folder in Android Studio, install SDK 36 and the project-selected Gradle/JDK dependencies, select `app` / `debug`, and sync. Set `local.properties` to your own SDK path through Android Studio; never copy another person's credentials/configuration. Use the existing Mapbox setup if required by unrelated app screens; obtain any required development access privately. No credentials are included in this handover.

## Delivered experience

- Canonical `INSPECTOR` login routes to Dashboard, My inspections, Details and read-only Profile. The existing shared login/session architecture remains the only session owner. Canonical `NOT_REQUIRED` verification is supported; the legacy non-canonical verification gate is preserved.
- Dashboard statistics derive from the authenticated Inspector's real assignment list. Assigned to you counts the list; Pending combines ASSIGNED/PENDING; In progress and Completed match exact statuses. Cancelled is shown separately. Recent jobs sort by record creation timestamp then ID, missing dates last.
- Cards/details show only backend target type/ID, real status/result, notes and audit timestamps. No farm name, address or appointment date is invented.
- Existing evidence metadata is retrieved through the assigned-inspection evidence endpoint and displayed with bounded, session-scoped image loading. A missing image or denied/failed metadata request has a visible state. Raw private URLs are not printed or opened in a browser.
- Inspector status changes and findings have review/confirmation dialogs, owned-record preflight reads, session-tagged writes, single-flight submission protection and actual returned-record display. The dashboard/list adopts the returned status rather than assuming success. Automatic write retries/replays are disabled.
- Notes are required for findings, trimmed, and limited to 4000 characters by Android. Notes remain in memory, not a saved-state/disk journal. Do not enter ID numbers, document contents or unrelated sensitive personal information.
- HTTP rejections use safe messages; uncertain network/server/malformed-response outcomes lock further submissions until explicit inspection reload. Repeated identical findings and no-op status updates are rejected before a write. Logout/account switch clears Inspector state; stale completions are discarded.
- Profile shows `/auth/me` identity and returned specialization, assignedArea, background/status and primary/first profile image. These fields remain read-only. Profile images may be badges/certificates, not portraits.

## Architecture by file

All module files are under `app/src/main/java/com/example/farmapp/inspector/`:

| File | Responsibility |
| --- | --- |
| `InspectorApi.kt` | Exact Retrofit GET/PUT contracts. No evidence multipart upload or sensitive-document API is exposed. PUT requests carry the originating SessionSnapshot as an OkHttp tag. |
| `InspectorModels.kt` | Nullable wire DTOs, validated read models, five statuses, three findings, note validation, dashboard counts and evidence privacy-blocker explanation. Findings request `toString()` is redacted. |
| `InspectorRepository.kt` | Derives numeric user ID from current canonical session, verifies origin/role/generation and ownership, parses envelopes, reads assignment-scoped evidence and executes explicit writes. Rechecks ownership using fresh details before mutation; never takes an Inspector ID from the UI. |
| `InspectorViewModel.kt` | Android ViewModel wrapping testable InspectorStateHolder. Combines assignment/profile refresh, details/evidence loading, state reset, single-flight write gate and reconciliation lock. One Inspector VM survives configuration changes; session changes clear its data. |
| `InspectorShell.kt` | Material 3/Kilivana Compose navigation, dashboard, list, details, evidence viewing, status/findings forms, read-only profile, loading/errors and synthetic previews. |

Integration/shared dependencies:

- `ui/navigation/AppNavigation.kt`, `AuthDestination.kt`: role entry and session-generation composition boundary; other role destinations remain intact.
- `data/remote/ApiClient.kt`: existing URL, canonical adapters, shared authenticated client and image transport; adds InspectorApi construction. No logging interceptor, insecure TLS or redirect following.
- `network/AuthTokenInterceptor.kt`: existing shared refresh; canonical writes do not replay on 401. Originating-session tags reject queued writes after account/backend changes.
- `network/ImageRequestInterceptor.kt`, `ui/screens/ProductImage.kt`: exact-origin image credentials, proactive canonical expiry refresh, bounded memory-only decoding, session/account invalidation, no shared private-image disk cache.
- `network/canonical/*`, `data/model/canonical/*`: existing canonical envelopes, strict numeric parsing, DTOs, auth conversion and shared Farmer adapter. The full adapter dependency is included so the handover builds and existing Farmer calls continue to compile; this is not new Inspector-owned Farmer functionality.
- `data/model/AuthModels.kt`, `network/AuthValidation.kt`, `repository/AuthRepository.kt`: numeric canonical identities, token metadata and origin-bound encrypted-session restoration; canonical sign-out clears locally because backend revocation is unavailable.
- `viewmodel/AuthViewModel.kt`, `ui/screens/LoginScreen.kt`, `ui/screens/auth/SignInScreen.kt`: canonical mode selects the email sign-in label; credentials stay in the existing login UI.
- `data/remote/FarmApiService.kt`, `data/model/FarmerProductModels.kt`: minimal shared adapter compatibility (`canonical` flag, absent image metadata remains nullable). The staged FarmerShell compatibility fix ignores absent image filenames rather than dereferencing null. Remaining Farmer UI/workflow edits are intentionally not swept into this handover.
- `app/build.gradle.kts`, `gradle.properties`: HTTPS ngrok selection, localhost override, canonical mode and environment-specific debug package storage.
- Existing encrypted storage, TokenManager, theme, FileProvider and general role UI remain shared infrastructure; no parallel app/session implementation was introduced.

Tests: `InspectorModuleTest.kt` covers routing, mapping, counts, errors, ownership, profile and session isolation. `InspectorWorkflowTest.kt` covers mutation serialization, fresh ownership, duplicates, timeout reconciliation, late responses and upload privacy gate. `InspectorUiTest.kt` renders synthetic state only, including confirmation and disabled-upload UI. Canonical contract/image tests and sanitized fixtures accompany the required shared dependencies.

## API contracts

All paths are relative to `/api/v1/`; responses use `CanonicalApiResponse<T>` (`success`, `data`, optional `message`, `timestamp`, `error`).

| Method and path | Request / response |
| --- | --- |
| GET `auth/me` | Existing CanonicalUserDto; verify role INSPECTOR and matching user ID. |
| GET `profiles/inspectors/{userId}` | InspectorProfileDto; userId is always current authenticated identity. A 404 displays missing profile alongside account identity. |
| GET `inspections/inspector/{inspectorId}` | List<InspectionDto>; every inspectorId must match. |
| GET `inspections/{id}` | InspectionDto; selected ID and assigned Inspector must match. |
| GET `inspections/{id}/evidence/images` | List<InspectorImageDto>; preflight assignment check first. |
| PUT `inspections/{id}/status?status=ENUM` | No JSON body; returns updated InspectionDto. |
| PUT `inspections/{id}/result?result=ENUM` | JSON inspectorId, targetType, targetId, status, result, notes from the freshly verified record/form. No client evidenceUrls override; returns updated InspectionDto. |

InspectionDto: `id`, `inspectorId`, `targetType`, `targetId`, `status`, `result`, `notes`, `evidenceUrls`, `inspectedAt`, `createdAt`, `updatedAt`. Wire evidenceUrls is not blindly expanded into arbitrary remote requests; metadata comes from the scoped images endpoint.

Statuses: `ASSIGNED`, `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. Findings: `APPROVED`, `REJECTED`, `CHANGES_REQUIRED`. Source/deployed documentation examples such as PASS/PASSED conflict with these actual enums; use the enums.

InspectorProfileDto: `id`, `userId`, `inspectorDetails`, `specialization`, `assignedArea`, `status`, `createdAt`, `updatedAt`, `images`. Image metadata: `id`, `url`, `publicId`, `assetId`, `sortOrder`, `isPrimary`, `createdAt`, `updatedAt`. Account name/email/phone/region/role/status/verification come from `/auth/me`.

Source permissions: `InspectionService` defaults to admin-only but explicitly grants self/assigned-Inspector reads, status/result writes and evidence operations through ResourceAuthorization. ProfileService checks self/admin and Inspector role. Android uses non-admin routes only. OpenAPI authentication declarations do not prove deployed row-level enforcement. Phase 1 previously obtained OpenAPI HTTP 200; the handover check received HTTP 404 from `/api-docs`. Current mutation verification is against read-only source, not authenticated deployed execution. Daniel must confirm the reviewed revision is deployed before live pilot writes.

## Status/result limitations — do not infer rules

The backend directly sets any declared InspectionStatus for an assigned Inspector; it defines no allowed-transition matrix or terminal-state restrictions. Android exposes alternative declared values, rejects a same-state request, and asks for confirmation. This reflects current source, not an invented lifecycle. Daniel must define/enforce a transition policy before relying on stronger business guarantees.

Recording a finding changes result/notes/inspectedAt but does not automatically complete the inspection or approve a product. Android displays the actual response and does not synthesize either action. Version/ETag checks, idempotency keys and exactly-once submission semantics are absent. Single-flight plus preflight/reconciliation reduce accidental duplicates but cannot resolve delayed server completion, concurrent admin edits or process-death ambiguity perfectly. Reopen/refresh a job and inspect returned state before resubmitting after interruption; do not blindly repeat a write.

No live write was made during implementation. Mutations are exercised only through synthetic in-memory transports/fakes. Request a disposable assigned test job and explicit approval before manual live mutation testing.

## Evidence and sensitive-document blocker

**Evidence upload, device selection and camera capture are intentionally unavailable in this handover.** Do not enable a picker that encourages gathering sensitive material before there is a safe destination. The UI explains the privacy blocker and has no active upload/capture controls; no new local evidence files or sensitive-document drafts are created.

Verified but NOT integrated upload contract: POST `inspections/{id}/evidence/images`, multipart part **image**, optional isPrimary query, HTTP 201 List<ImageResponse>. Upload/list operations check inspection assignment. DatabaseImageStorage and LocalImageStorage accept JPEG/PNG/GIF/WebP up to 10 MiB; Spring multipart limits may be tighter in a deployment. Once secure delivery exists, Brian must verify deployed limits, inspect actual file signatures and decoded bounds, preview before explicit confirmation, use Android Photo Picker/TakePicture and scoped FileProvider, stream bounded uploads, distinguish progress from completion, and reconcile uncertain uploads rather than retrying automatically.

The blocker is raw-byte delivery: StoredImageController calls ProductVisibility, which checks product links/folder hints, not inspection-evidence ownership. Non-product evidence with no product link can therefore be served publicly through `/api/v1/images/{id}`. The local `/uploads/**` handler has the same product-oriented guard. Authentication on upload/metadata is not private storage/delivery. No live evidence bytes were probed to demonstrate this source finding.

Farmer national ID numbers/documents appear in registration DTOs and KYC entities. `/api/v1/admin/kyc` is an admin review API; it is not an assigned-Inspector private upload/download lifecycle. Dedicated, suitable Inspector endpoints for national-ID images, title deeds, leases and land-use authorizations were not verified. Do not put these into evidenceUrls, generic images, product images, plaintext preferences, files or logs.

Daniel needs to provide:

1. Authenticated byte delivery tied to inspection assignment/document ownership, covering database AND local providers; remove public aliases/bypasses, support reassignment/revocation, and set no-store.
2. Separate sensitive-document models/endpoints, allowed document types, Inspector access policy, upload/download contracts and server-side validation.
3. Retention/deletion/revocation and audit lifecycle, secure storage controls, safe content disposition and payload/file limits. Decide whether sensitive material may ever be downloaded/offline cached.
4. Defined transitions, concurrency/version protection and idempotency/reconciliation support for findings and evidence.

Existing evidence is viewable only after assignment-scoped metadata reads and image URL policy checks. Tokens go only to the configured origin's image-delivery routes; Cloudinary is HTTPS/token-free and arbitrary hosts are blocked. No URL text or private evidence is logged. This Android behavior does not fix the backend's public-byte vulnerability.

## Running and testing

Development default remains `https://either-juvenile-progeny.ngrok-free.dev/api/v1/`, selected by kilivana.apiBaseUrl. Canonical backend is true, mock auth false. Debug package ID is `com.example.farmapp.dev2231918adc4d4b9d`; storage is isolated by backend URL. Never disable TLS validation or redirects/auth-origin restrictions to work around a tunnel issue.

```powershell
# Use your Android Studio JBR/JDK; this workstation uses JDK 21.
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain

# Localhost override; backend process/database must already be separately authorized and running.
.\gradlew.bat :app:assembleDebug "-Pkilivana.apiBaseUrl=http://127.0.0.1:8081/api/v1/" --console=plain
# For a USB phone with that LOCAL build only:
adb reverse tcp:8081 tcp:8081
```

An emulator can instead build with `-Pkilivana.apiBaseUrl=http://10.0.2.2:8081/api/v1/` (quote the whole argument in PowerShell). Each URL creates a different debug package. Rebuild with no URL override to restore the ngrok APK, inspect generated BuildConfig/output metadata, and install the correct package. Do not start/change a backend or database as part of an Android build.

Previews: open InspectorShell.kt in Android Studio Design/Split; dashboard/empty/error examples are clearly synthetic and need no account/device. For connected tests, start an API 26+ AVD in Device Manager or attach an authorized test device. Run only the synthetic Inspector UI class:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.farmapp.inspector.InspectorUiTest" --console=plain
```

Do not run unfiltered connected tests: a separate pre-existing opt-in canonical test can create remote records. Synthetic Inspector UI tests make no network calls. No connected device was detected during handover; compiled UI tests must not be reported as executed.

Manual deployment testing needs an existing authorized Inspector account, assigned disposable job, safe server logging and confirmation of deployed access guards from Daniel. Enter credentials in the phone/emulator UI only. First exercise reads/sign-out/account switching. Only with separate explicit permission exercise status/findings once, verify returned record and the admin view, and test interruption/reconciliation. Do not upload identity/land documents.

## Commit scope and validation record

The feature commit must include the Inspector module/tests/docs and required canonical/auth/image/build/login dependencies, with only necessary shared Farmer compile/adapter compatibility changes. Local IDE configuration, local.properties, .gradle, build outputs, APKs, keystores, unrelated Farmer UI/draft work and the unrelated live canonical device test remain excluded. See the owner's final report for the actual commit hash and executed test counts; staging is tested as a separate source snapshot so unstaged files cannot mask missing dependencies.

The exact reviewed file list is in [INSPECTOR_HANDOVER_FILES.md](INSPECTOR_HANDOVER_FILES.md). Working-tree validation passed 177 JVM tests and assembled both APKs. The isolated staged snapshot also passed its full suite and assembled both APKs; its test count differs because the unrelated, unstaged CanonicalUploadSafetyTest is excluded. Four synthetic Inspector Compose tests are compiled, not executed: `adb devices` showed no connected device. No automated remote writes occurred.

## Brian's suggested next tasks

1. Coordinate Daniel's private evidence/document delivery and deployment verification before implementing camera/gallery uploads.
2. Run the compiled synthetic UI tests on an AVD; test narrow screens, large text, theme and process recreation. Validate the assigned-job workflow on a disposable, authorized deployment fixture after approval.
3. Agree a server-enforced transition matrix and add idempotency/version-aware mutations, then expand Android rules against those exact contracts.
4. Add richer target summaries/scheduled dates only when returned by authorized backend DTOs; do not fetch arbitrary farmer records based solely on target IDs.
5. Add private evidence capture/upload with bounded validation, cancellation, cleanup and secure lifecycle after item 1 is complete.

Known limitations are the unavailable private uploads/documents, source-only deployment verification, no target names/addresses/scheduled dates, no exactly-once/concurrency guarantee, and UI tests compiled but not run without a device. Supplier development belongs to the repository owner and is outside this handover.
