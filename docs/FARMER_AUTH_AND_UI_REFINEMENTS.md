# Farmer authentication and profile/image refinements

Date: 2026-10-26. No live login, registration, OTP or product submission was sent by the agent. The public OpenAPI document was read without credentials; snapshot: `kilivana-auth-openapi-20261026.json`. Backend source was inspected read-only. Existing uncommitted work and session storage format are preserved.

## Farmer sign-in investigation

The previous message, “Sign-in could not reach or finish reading the service,” was emitted by the catch around `service.login` in AuthRepository. That catch merged request I/O and response-body I/O. It did not contain enough information to identify DNS/connection failure, a timeout before response headers, or a failure reading a response after headers arrived. The original account-specific request was not replayed because the user prohibited live login without approval; **its HTTP status and exact transport cause remain unobserved**. No backend fault is claimed.

| Stage | Farmer behavior | Supplier comparison | Evidence/result |
| --- | --- | --- | --- |
| 1. POST login | JSON email/password to `/api/v1/auth/login` | Same request, transport, origin, timeouts and decoder | No role field or Farmer-only login endpoint exists in the client |
| 2. Read response | Previously buffered/deserialized inside Retrofit with mixed I/O guidance | Same prior ambiguity | New streaming login adapter separates request/headers from bounded response reading and retains actual HTTP status when available |
| 3. JSON decode | Canonical envelope + exact integer DTO codec | Shared | Malformed JSON is distinguished from an incomplete/truncated body and from network failure |
| 4. Validate account/tokens | User identity, declared role, account/approval status, token type and lifetime | Shared | Required email-verification state is now handled before missing-token validation; unknown roles and invalid tokens remain rejected |
| 5. Farmer profile | **No Farmer profile GET is required during ordinary Farmer sign-in** | Supplier deliberately checks its owned business profile before session establishment | The original Farmer login message cannot come from a Farmer profile lookup. Profile completion is used only for a known newly registered account after verification, with GET-before-create |
| 6. Establish/persist session | SessionStore writes encrypted storage before publishing authenticated state | Shared | Storage failures now report SESSION_SAVE rather than a connection problem; format, Keystore alias, origin binding and existing session data remain unchanged |
| 7. Navigation | A valid FARMER session maps to FarmerShell | SUPPLIER maps to SupplierShell after its existing profile check | No Farmer route rewrite or extra login-time dashboard request introduced |

### Confirmed Android defects fixed

1. Generic I/O error handling obscured which part of login failed. New diagnostics distinguish request network/timeout, response-read network/timeout, HTTP error, JSON decode, token contract, role contract, post-login profile and secure session storage.
2. The deployed `AuthTokenResponse` advertises `emailVerificationRequired` and `emailVerificationChallengeId`; `UserResponse` advertises `emailVerified` and registration `emailVerificationChallengeId`. Android previously ignored these and could attempt to decode a verification-required response as a completed token session.
3. Registration previously attempted login/profile creation immediately after creating an unverified account, although reviewed backend AuthService refuses token issuance before email verification. Registration now pauses at verification and resumes without another POST register.
4. The verification screen was reachable only through standalone links. It is now driven by explicit backend state during normal registration/sign-in.

### Privacy-safe diagnostics

The UI displays a fixed diagnostic identifier alongside a friendly error, for example `AUTH_RESPONSE_READ_TIMEOUT · HTTP 200` or `AUTH_JSON_DECODE_MALFORMED · HTTP 200`. HTTP status is retained only when actually observed. A timeout before response headers has no invented status.

LOGIN_REQUEST, RESPONSE_READ, JSON_DECODE, TOKEN_VALIDATION and ROLE_VALIDATION refer to POST `/api/v1/auth/login`. FARMER_PROFILE/SUPPLIER_PROFILE refer to the owned role profile read/setup step. SESSION_RESTORE/SESSION_SAVE/SESSION_CHANGED are local session stages.

Diagnostic objects contain enums and optional HTTP status only. No request/response body, URL/query string, throwable message/stack, password, token, OTP, email, phone or other personal details are logged. No HTTP logger was added. Login keeps the existing 30-second connect/read/write and 60-second call limits, disabled redirects, and disabled automatic connection retry; failures are not hidden by extending timeouts or replaying credentials.

## Normal email verification flow

* Registration: when the response explicitly indicates an unverified email or supplies an initial challenge, route automatically to OTP. A returned challenge is reused because the backend already issued it. No duplicate request is sent simply to obtain another challenge ID.
* Existing sign-in: accept the advertised `emailVerificationRequired=true` or `user.emailVerified=false`, and the source-verified HTTP 403/FORBIDDEN message “Email verification is required before login.” Generic 401/403, administrator approval status, missing optional fields or arbitrary server text do not trigger speculative verification.
* The same gate handling applies to Farmer, Supplier, Buyer, Driver, Inspector and Admin login responses. Android self-registration remains limited to permitted customer roles (Farmer, Buyer, Supplier); staff/Driver provisioning and unsupported dashboards are not enabled by verification.
* If the backend does not provide an existing challenge, request one automatically once on entry. Recomposition and repeated begin calls do not send another request. Returning to sign-in and retrying the same pending flow reuses the in-memory challenge/cooldown. Resend remains an explicit action and honors returned timing/Retry-After.
* Six-digit input is submitted to the verification endpoint. Only a successful verification envelope permits the continuation. A pending/failed/expired verification never establishes a session.
* After verification, resume one login using credentials retained only in AuthViewModel memory; for a newly registered user, complete its known profile using GET-before-create and then establish the session. Registration is not repeated. If the user returns to sign-in later in the same process, the known profile draft can still be completed without retaining its password.
* Verified users sign in normally. The app never requests OTP merely because a role needs administrator approval, on ordinary recomposition, or on app launch. Backend token issuance remains authoritative.
* OTP and continuation passwords are never saved to disk or saved instance state. Cancel removes continuation credentials. Process death returns to sign-in rather than automatically sending a code or replaying registration.

### Remaining backend/acceptance requirements

* The public schema does not include expiry/resend hints with the initial registration/login challenge. Android does not fabricate an expiry for that challenge; it uses server verification for expiry and the documented 60-second resend cooldown. Resend responses provide explicit expiry/cooldown hints. Returning those hints on initial responses would permit a precise initial countdown.
* Reviewed local backend throws the verified 403 gate; deployed OpenAPI additionally advertises a successful verification-required token-response shape. Both are handled, but actual deployed enforcement/mail delivery for every provisioned role has not been tested with live credentials. A stable dedicated verification-required error code would be preferable to the verified legacy exact message.
* Older deployments that omit email state and issue tokens cannot have an email gate inferred from administrator verification status. The backend must enforce email verification before issuing/refreshing tokens. No client-side gate or successful verification is simulated.
* If process death loses a newly registered user's profile draft, it is not reconstructed from invented business/farm details. Supplier's existing explicit setup recovery remains available; missing Farmer setup may require completing the profile with approved details. No new account should be created for that purpose.
* The original Farmer account still needs a manual retry on the updated app (and its safe diagnostic code if it fails), or an approved controlled live login. No live HTTP status or backend incompatibility is claimed for that historical attempt.

## Product image UI

The creation/edit form now contains one “Product images” heading and a 64 dp image-plus button immediately below it, with content description “Add product photo.” The large text chooser and decorative duplicate icon are removed. The same icon opens/reopens Android Photo Picker; selected photos show a 96 dp thumbnail and can be replaced or removed before upload. Removal is disabled while a replacement photo is being prepared to prevent a late import from restoring a removed selection.

New supplies retain the existing save-first, known-product, explicit-upload flow. No automatic upload retry or second product creation is introduced. Existing gallery display, owner/pending authorization, primary/reorder/delete controls and durable uncertain-submission markers remain. Supplier business-profile photo APIs stay separate.

Normal instructions are short (“Save your product, then upload the photo”); implementation descriptions about confirmed IDs, process restarts or JPEG metadata processing are removed from this UI.

## About placement

About is absent from Edit Profile. Supplier, Farmer and Inspector main Profile pages have a standalone bottom row with an information icon and right chevron. Buyer and Driver open a main Profile dialog with Edit Profile and a separate bottom About row. About opens the app-information/version dialog using BuildConfig. Editing account/business details remains a separate action; existing themes and profile data are retained.

## Exact files changed in this refinement task

Paths below are relative to FarmApp; pre-existing unrelated dirty files were not reset or discarded.

| Area | Files |
| --- | --- |
| Transport/diagnostics | `app/src/main/java/com/example/farmapp/network/canonical/AuthLoginDiagnostics.kt` (new); `app/src/main/java/com/example/farmapp/network/canonical/CanonicalAuthErrors.kt`; `app/src/main/java/com/example/farmapp/network/canonical/CanonicalAuthService.kt`; `app/src/main/java/com/example/farmapp/data/remote/ApiClient.kt` |
| DTO/API/profile continuation | `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalAuthDtos.kt`; `app/src/main/java/com/example/farmapp/network/canonical/CanonicalBackendApi.kt`; `app/src/main/java/com/example/farmapp/network/canonical/SupplierSignup.kt`; `app/src/main/java/com/example/farmapp/repository/AuthRepository.kt` |
| Auth UI/state | `app/src/main/java/com/example/farmapp/viewmodel/AuthViewModel.kt`; `app/src/main/java/com/example/farmapp/ui/screens/LoginScreen.kt`; `app/src/main/java/com/example/farmapp/ui/screens/auth/SignInScreen.kt`; `app/src/main/java/com/example/farmapp/ui/screens/auth/SignUpScreen.kt`; `app/src/main/java/com/example/farmapp/ui/screens/auth/EmailVerificationScreen.kt` |
| Image form | `app/src/main/java/com/example/farmapp/supplier/SupplierShell.kt` |
| About/profile | `app/src/main/java/com/example/farmapp/profile/ProfileEditor.kt`; `app/src/main/java/com/example/farmapp/ui/navigation/AppNavigation.kt`; `app/src/main/java/com/example/farmapp/ui/screens/FarmerProfileScreen.kt`; `app/src/main/java/com/example/farmapp/inspector/InspectorShell.kt`; `app/src/main/java/com/example/farmapp/tracking/DriverShell.kt` |
| JVM tests | `app/src/test/java/com/example/farmapp/network/canonical/FarmerAuthenticationFlowTest.kt` (new); `app/src/test/java/com/example/farmapp/network/canonical/CanonicalAuthRecoveryTest.kt` |
| Device tests | `app/src/androidTest/java/com/example/farmapp/supplier/AuthAndProfileRefinementUiTest.kt` (new); `app/src/androidTest/java/com/example/farmapp/supplier/SupplierUiTest.kt`; `app/src/androidTest/java/com/example/farmapp/ui/screens/FarmerProfilePresentationTest.kt` (explicit keyboard dismissal before cancellation); `app/src/androidTest/java/com/example/farmapp/ui/screens/FarmerShellPresentationTest.kt` (accessible logout callback avoids transient snackbar touch overlap) |
| Evidence/report | `docs/kilivana-auth-openapi-20261026.json` (new); `docs/FARMER_AUTH_AND_UI_REFINEMENTS.md` (new) |

## Validation

The synchronized build passed **316 JVM tests across 38 suites**, with zero failures/errors. Debug app and instrumentation APKs built successfully. Android lint passed with **zero errors, 51 warnings and one hint**. `git diff --check` passed. All auth API fixtures intercept transport or use private in-memory services; no live login/account creation/OTP submission is performed by these tests.

The connected Samsung received both APKs with `adb install -r`. The initial device run was blocked by the lock screen (`showing=true`/`mIsShowing=true`) and stopped without clearing data. After unlock, two combined runs each passed 47/48: one cancellation touch assertion and then one logout touch assertion were intermittent. The cancellation test now dismisses the keyboard explicitly. The shell callback test uses the accessible logout action to avoid a transient profile snackbar covering its touch coordinate; physical Photo Picker tapping remains tested.

On the final code, the combined run passed **46/48**, including all eight new feature tests, all 18 Supplier UI tests, all 14 signup tests, three Supplier recovery tests and three Farmer shell tests. Its first two Farmer profile tests failed with `No compose hierarchies found`; the precise startup cause was not established. An immediate separate rerun of that entire two-test class passed **2/2**. Thus all 48 focused tests have passing results on the final code, but there was **no clean single combined run**. Logs are `app/build/reports/auth-profile-refinements-device-tests-rerun.txt` and `app/build/reports/auth-profile-refinements-farmer-profile-rerun.txt`; earlier runs are retained beside them.

Device tests verified the image-plus physical click launches the Photo Picker contract, immediate thumbnail/replacement/removal, absence of the large chooser, About on the main Profile page and absent from Edit Profile, removal of standalone verification links, automatic registration/unverified-login OTP routing, verified login without OTP, no automatic resend on retry, and privacy-safe decode-stage diagnostics. Synthetic services are used for authentication; this is not a live Farmer account acceptance test.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

No backend edits, commits, pushes, resets, stashes, app uninstall or app-data clearing are performed. Device updates use `adb install -r` and direct instrumentation, preserving existing application data.
