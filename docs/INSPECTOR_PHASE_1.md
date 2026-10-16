# Inspector Android Phase 1

Historical Phase 1 baseline. The current status/findings workflow, evidence privacy restrictions and Brian's development instructions are documented in [INSPECTOR_HANDOVER_BRIAN.md](INSPECTOR_HANDOVER_BRIAN.md). Statements below about GET-only/read-only inspection workflows describe the original phase, not the handover implementation.

Phase 1 is read-only: authenticated Dashboard, My inspections, inspection details and Profile. It uses the existing canonical API URL, encrypted session owner, OkHttp bearer interceptor, refresh coordinator and session-aware image loader. Farmer, Buyer and Supplier routing is unchanged. The previous mock Inspector screen remains on disk but is no longer used by authenticated navigation. No remote personal records, credentials or writes were used for implementation/testing.

## Verified contracts

The reviewed backend source and an unauthenticated HTTPS GET of the deployed `/api-docs` (HTTP 200, OpenAPI 3.0.1) on 2026-10-16 agree on:

- Role: `INSPECTOR`. Canonical accounts route by this role, including `verificationStatus=NOT_REQUIRED`; the backend authorizes by authenticated role/identity, not Android's legacy verification gate. The legacy non-canonical verification gate remains unchanged. A non-canonical Inspector sees a sign-in/configuration error instead of synthetic jobs.
- `GET /api/v1/auth/me`: existing `CanonicalUserDto`: `id`, `name`, `email`, `phone`, `username`, `referenceCode`, `region`, `role`, `status`, `verificationStatus`, `createdAt`. Identity and role are checked against the session before displaying a profile.
- `GET /api/v1/profiles/inspectors/{userId}`: `id`, `userId`, `inspectorDetails`, `specialization`, `assignedArea`, `status`, `createdAt`, `updatedAt`, `images`. Status is a free string in this DTO. Images contain `id`, `url`, `publicId`, `assetId`, `sortOrder`, `isPrimary`, `createdAt`, `updatedAt`. These can be badges/certificates, not necessarily portraits; the UI labels them Profile image and uses primary/first returned image through the existing secure loader.
- `GET /api/v1/inspections/inspector/{inspectorId}`: list envelope of `InspectionResponse`.
- `GET /api/v1/inspections/{id}`: single inspection envelope.
- `InspectionResponse`: `id`, `inspectorId`, `targetType`, `targetId`, `status`, `result`, `notes`, `evidenceUrls`, `inspectedAt`, `createdAt`, `updatedAt`.
- Inspection statuses: `ASSIGNED`, `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. Result enum: `APPROVED`, `REJECTED`, `CHANGES_REQUIRED`. The OpenAPI example `PASSED` is inconsistent with its enum; Android follows the actual enum, not the example.
- `GET /api/v1/categories` exists in source/deployed schema. It is not needed or called in Phase 1 because specialization is already a profile string; no category or region relationship is fabricated.

Only those four Inspector GET operations are exposed in `InspectorApi`; no mutation methods are present. Every response uses the existing `CanonicalApiResponse<T>` envelope and strict canonical integer parsing. No report submission, status update, admin action, upload or document capture was added.

## Authorization and isolation

Source verification: `InspectionService.getInspectionsByInspector` requires `resourceAuthorization.inspector(inspectorId)` (the authenticated Inspector themselves, or admin); `getInspectionById` requires `resourceAuthorization.inspection(id)` (assigned Inspector or admin). The default service policy is admin-only for general lists. `ProfileService.getInspectorProfile` checks ownership/admin and the target user's Inspector role. API routes require authentication. OpenAPI advertises bearer authentication but does not prove deployed record-level enforcement. Daniel must confirm these reviewed guards are deployed; no probing with another real inspector's ID was performed.

Android obtains the numeric user ID only from its active canonical Inspector session. Returned jobs, individual details, profile and `/auth/me` identity must match it; an unexpected owner rejects the response instead of silently filtering it. Detail selection is limited to IDs in the loaded assigned list. Duplicate IDs and unsupported statuses fail closed. No UI accepts arbitrary Inspector IDs or provides another-inspector fallback.

State is held in a single Inspector ViewModel, with no Inspector disk cache. Logout, account changes and session invalidation clear private state. Every completion checks session identity/generation, so old responses cannot populate a later account. The UI also hides state whose generation differs immediately. Token rotation preserves the current generation. Image previews retain existing account/session isolation. Existing debug package separation isolates backend environments.

Errors display fixed Android messages rather than server bodies/URLs or exception details. HTTP 401 uses the existing refresh flow; persistent authentication failures return to the existing sign-in architecture. HTTP 403 requests admin attention. A profile 404 shows the authenticated account with a missing-profile message. List errors do not display zero-count success metrics.

## Dashboard semantics and data limits

Assigned to you is the size of the authenticated Inspector's returned assignment list, including completed/cancelled records. Pending combines `ASSIGNED` and `PENDING`; In progress, Completed and Cancelled use exact corresponding statuses. Recent assignments sort by `createdAt`, then ID, with missing dates last, and show at most five jobs. My inspections shows all returned assignments.

There are no farm names, target addresses, coordinates, crop details or scheduled/appointment dates in `InspectionResponse`. Cards show the returned target type/ID and explicitly labelled record creation date. Details show status/result/notes and real audit timestamps. No target data is inferred from IDs and no additional farmer/profile lookup is made. Evidence URLs are not opened or downloaded in Phase 1.

Specialization and assigned area are displayed exactly as returned, with Not provided for missing values; account region is labelled separately. All profile fields are read-only. Loading, empty, profile-not-assigned and retry/error states are implemented. Material components, Kilivana spacing, Inspector accents, text status labels, scrollable layouts and existing light/dark themes are reused.

## Android Studio Preview and emulator testing

No physical phone is required. The workstation had no connected device or configured AVD during implementation; emulator UI tests were compiled but not executed here.

1. Sync Gradle and select `app` / `debug`. Open `inspector/InspectorShell.kt` in Design or Split mode and Build & Refresh. `InspectorDashboardPreview`, `InspectorEmptyPreview` and `InspectorErrorPreview` use clearly labelled synthetic examples. They call no backend and require no sign-in. Preview works without creating an emulator.
2. To run the synthetic UI tests, create an Android 8/API 26 or newer virtual device through Android Studio Device Manager (API 35/36 is suitable), download its system image if needed, then start it. No backend account is required for these tests.
3. From the project root in PowerShell, run only the Inspector UI test class:

   ```powershell
   $env:JAVA_HOME='C:\Program Files\Java\jdk-21'
   .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.farmapp.inspector.InspectorUiTest" --console=plain
   ```

   These three tests render synthetic state, verify dashboard-to-details/back navigation, read-only specialization/area, and error/retry/empty states. They create no sessions and make no network calls. Do not run unfiltered connected tests for this check: an existing opt-in canonical device test can create remote records.
4. To validate the real deployment on an emulator, Daniel must supply an authorized existing Inspector account and assigned jobs, confirm safe server logging and deployed access guards. Install/run the normal debug app and enter credentials only in its sign-in UI. Inspect Dashboard, My inspections, Details and Profile; refresh; toggle theme; sign out; verify no previous data remains. A second authorized Inspector account can validate isolation using its own assignments. No mutation controls exist in this module. Never place passwords/tokens in command-line arguments or logs.

Local verification command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

Result: **167 JVM unit tests passed, zero failures/errors**, including 15 new Inspector tests. The debug app and Android-test APKs both assembled successfully. The three synthetic Compose UI tests compiled; execution awaits an AVD. Final BuildConfig retains the ngrok API URL, canonical backend enabled, mock auth disabled, and application ID `com.example.farmapp.dev2231918adc4d4b9d`.

## Daniel's remaining items

- Confirm deployment of self/assignment authorization rather than relying on OpenAPI authentication declarations. PR #2 deployment remains unverified.
- Provision Inspector accounts/profiles, specialization, assigned area and jobs using the existing administration workflow; provide test access privately. Confirm server bearer-token debug logging is disabled/removed before credential testing.
- Add farm/target display summaries and explicit scheduled dates if desired; the current response supports only target IDs/type and audit timestamps.
- Clarify profile-image semantics if a portrait is required separately from badge/certificate images.
- Fix the inconsistent `InspectionResult` documentation example (`PASSED` versus actual enum values) when convenient. Android follows the real enum.

No backend, database, migration or Admin Portal changes are part of this Android implementation.
