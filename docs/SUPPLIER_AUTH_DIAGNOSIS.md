# Supplier registration and authentication fixes

## Scope and deployment evidence

Android changes only, on `feature/supplier`. Supplier presentation, Retrofit routes, backend code, database, other installed variants and app data are unchanged. No live registration, profile creation, email dispatch or verification request was submitted during this work.

Phase 1 read-only checks on 2026-10-22 confirmed HTTP 200 from the health endpoint and `/api-docs` at `https://either-juvenile-progeny.ngrok-free.dev`. The installed `com.example.farmapp.dev2231918adc4d4b9d` APK matched the local debug APK by SHA-256. The generated debug configuration selects that origin, canonical authentication, no mock authentication and backend-isolated storage.

The deployed OpenAPI advertises `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/me`, password recovery, and Supplier profile GET/POST routes. Login accepts email/password; registration accepts account fields and returns a user envelope, not tokens. Supplier business name/location remain a separate authenticated profile request.

**Email verification is not a confirmed deployed contract.** The live OpenAPI did not advertise email-verification routes or `emailVerified` / challenge fields. The adjacent local `kilivana-supplier-backend` source instead has an email OTP gate and transactional rollback if initial email delivery fails. That source cannot establish what Daniel deployed. No OTP routes, DTO fields, screens, resend behavior or token assumptions have been added. Email confirmation must not be inferred from the administrator `verificationStatus` field or the Inspector approval screen. A 403 remains an account restriction, not proof that an OTP is required.

The original registration failure cannot be reconstructed from the previous generic message. No user in Daniel's database is consistent with a different request origin, a request that did not create an account, or a rollback. It does not identify the original HTTP response, deployed commit or transaction outcome. In particular, a generic HTTP 503 is not treated as proof of rollback.

## Confirmed Android fixes

### Registration

- Supplier validation/rejection statuses 400, 401, 403, 415, 422 and 429 retain explicit rejection handling and permit a later user-initiated correction. No request is automatically retried.
- Documented error envelopes are read with a 16 KiB limit. Only fixed client strings, recognized error codes, recognized message literals and allowlisted field labels affect the UI. Arbitrary response values, exception messages, tokens and credentials are never displayed or logged.
- Validation can name fields to correct; conflicts can provide known email/phone guidance. A conflict still keeps registration blocked and directs the user toward existing-account recovery.
- Transport failures, malformed/incomplete responses and unknown HTTP outcomes have different safe messages. Unknown statuses (including server errors), lost replies, malformed 2xx envelopes and cancellation keep the existing uncertainty latch. No status outside the explicit rejection set becomes proof that account creation failed.
- Once an owned registration response is confirmed, later login/profile failures say the account was created and setup is incomplete. Recovery does not register again.
- The existing uncertainty latch remains memory-only. After process death, use the existing "Already registered? Finish Supplier setup" flow when account creation was uncertain. No durable registration idempotency guarantee is invented.

### Login

- Canonical errors distinguish validation, invalid credentials, recognized inactive/suspended account restrictions, forbidden access, throttling and unavailable service.
- Known inactive/suspended details are accepted only with the expected unauthorized code/status. Unrecognized server text never becomes UI copy. Unknown-email and wrong-password responses remain indistinguishable.
- Transport errors receive connection guidance. JSON/contract/token-format failures receive response-format guidance. Storage/unexpected failures no longer receive the blanket "Check your connection" message.
- Farmer/Buyer token and session handling is retained. Only canonical Supplier login gains the profile lookup; other roles do not request Supplier profiles. Farmer registration handling is unchanged.

### Supplier business setup

1. Authenticate using the existing email/password endpoint.
2. Before persisting a canonical Supplier session, read the authenticated user's existing Supplier profile using the temporary bearer token.
3. Verify the returned profile ID and owner. Existing valid profiles continue into the unchanged Supplier UI.
4. A 404 opens the existing "Finish Supplier setup" form with Supplier selected and the entered email prefilled. No session/token or password is saved for a partial setup; the user re-enters their existing password and business details.
5. Explicit form submission authenticates again and reads the profile again. Only a fresh 404 permits the existing profile-create request. A profile completed by another client is used without another POST. Registration is never called by recovery.

Denied/unavailable/malformed/wrong-owner profile replies do not trigger setup or profile creation. Session changes, cancellation and duplicate-submission guards remain enforced. Already-restored sessions keep their existing behavior; sign out/sign in to exercise the new login preflight.

## Automated verification

`CanonicalAuthRecoveryTest` uses real Retrofit/Gson with an OkHttp interceptor that returns only synthetic responses and never proceeds to a network connection. It covers validation/rejection responses, unknown HTTP statuses, conflicts, transport and parsing failures, malformed success envelopes, error redaction/size limits, post-registration login failures, Farmer/Buyer login, profile existence/ownership, missing-profile recovery, concurrent setup and session switching.

Existing Supplier signup tests retain uncertain-write/cancellation coverage and add duplicate recovery submission coverage. The existing provisioned-Supplier login fixture now supplies the newly required owned-profile read.

`SupplierLoginRecoveryUiTest` uses a fake canonical API and private in-memory persistence. It never constructs ApiClient or SupplierShell. It checks the existing recovery form handoff and explicit profile submission, denied profile lookup, and malformed login response messages.

Build command (JDK 21, existing offline caches):

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='C:/Users/AppleFi/.gradle'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

Build transcript: `app/build/reports/supplier-auth-build.log`. Unit reports: `app/build/reports/tests/testDebugUnitTest/`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Final results: **282 JVM tests passed, zero failures/errors/skips**, including 18 new canonical authentication cases and one new duplicate-recovery case. Debug and instrumentation APK builds passed. One old login fixture initially failed because it did not supply the newly required profile GET; the updated owned-profile fixture and full final suite passed. The three new device UI tests compiled but were **not executed**, because `adb devices` returned no devices. No APK was installed, no app variant was uninstalled and no app data was cleared during this task.

## Files in this change

Paths are relative to the Android project. Pre-existing Supplier redesign and IDE changes were not modified by this task.

- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalAuthErrors.kt` (new)
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalAuthService.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/SupplierSignup.kt`
- `app/src/main/java/com/example/farmapp/repository/AuthRepository.kt`
- `app/src/main/java/com/example/farmapp/viewmodel/AuthViewModel.kt`
- `app/src/main/java/com/example/farmapp/ui/screens/LoginScreen.kt`
- `app/src/main/java/com/example/farmapp/ui/screens/auth/SignUpScreen.kt`
- `app/src/test/java/com/example/farmapp/network/canonical/CanonicalAuthRecoveryTest.kt` (new)
- `app/src/test/java/com/example/farmapp/supplier/SupplierModuleTest.kt`
- `app/src/test/java/com/example/farmapp/supplier/SupplierSignupTest.kt`
- `app/src/androidTest/java/com/example/farmapp/supplier/SupplierLoginRecoveryUiTest.kt` (new)
- `docs/SUPPLIER_AUTH_DIAGNOSIS.md` (new)

## Manual verification

1. Use only `com.example.farmapp.dev2231918adc4d4b9d`, with the configured ngrok origin. If installing the built APK, use in-place `adb install -r`; do not uninstall variants or clear data.
2. With an authorized existing Supplier account and an existing business profile, sign in. Confirm the Supplier dashboard opens normally.
3. With an authorized existing Supplier account whose profile Daniel confirms is missing, sign in. Confirm "Finish Supplier setup" opens, Supplier/email are prefilled, password is blank, and no account-creation action is offered for this recovery mode. Enter existing credentials and business details and submit only when authorized. Verify no register request occurs and the profile is read before creation.
4. Verify incorrect credentials remain a generic credential error. Use approved backend fixtures for restricted-account checks; do not change account states on Daniel's server.
5. To investigate another registration failure, collect only the timestamp, app package/origin, HTTP status, safe error code and correlation ID if available. Never collect passwords, bearer/refresh tokens, OTPs or raw sensitive response bodies.
6. A real new-registration test requires explicit permission, an agreed test account and Daniel's confirmed deployed contract. It has not been performed. This change does not establish that live Supplier registration works.
