# Buyer authentication Phase 1 implementation

> Current authentication presentation: all four roles use the shared Kilivana Welcome/Sign In/Sign Up/OTP flow. The Buyer-only authentication redirect and visible diagnostics shortcut were removed. Brian shopping/dashboard routes remain integrated. See [UNIFIED_AUTH_UI_CLEANUP.md](UNIFIED_AUTH_UI_CLEANUP.md) for current validation and installation status; earlier separate-Brian-auth descriptions below are historical.


> Historical report: the approved production-source Buyer integration now supersedes the UI status described below. See [BRIAN_BUYER_INTEGRATION.md](BRIAN_BUYER_INTEGRATION.md) for the actual copied screens, safety adapters, final validation and live-checkout limitations.


## Samsung reconnect validation completed ? 29 October 2026

RF8WC0K043H appeared as **device** (authorized). Both successful Phase 1 APKs installed with adb install -r. Installed base APK SHA-256 equals the built app-debug.apk SHA-256. Its generated BuildConfig uses the unchanged intended URL https://hankering-morphing-outclass.ngrok-free.dev/api/v1/ and ENABLE_AUTH_DIAGNOSTICS=true. Package identity remains com.example.farmapp.dev2231918adc4d4b9d; no data clear, uninstall, logout or account replacement occurred.

**All 21 selected Samsung UI tests passed:** 16 in buyer-validation-auth-phase1-samsung.log (2 Buyer OTP/login continuation, 2 Buyer signup, 1 diagnostic privacy, 8 auth/profile/verification, 3 Supplier login/recovery), plus 5 isolated mocked authentication/routing/single-flight cases in buyer-validation-auth-phase1-regression-samsung.log. These used synthetic APIs and isolated sessions only. Previous full validation remains 394 unit passes, successful lint and both APK builds. No code changes were necessary during this reconnect turn.

A read-only query of the protected auth provider returned **No active user-armed authentication report**. This is the expected clean checkpoint after tests, not a failed live request. No live auth status or exception exists to report yet. User can now enable the ten-minute recorder on the auth screen and make one intentional login or signup attempt. After the user says it is finished, retrieve only the enum/status/category trace from the provider; never credentials, personal data, raw requests or bodies. Do not mistake synthetic UI-test HTTP events for a real attempt.

**Ready for manual test. Phase 2 has not started.** Historical disconnected-device results below are superseded by this successful installation and device validation.


29 October 2026. Approved scope is Buyer authentication only. Checkout/M-Pesa Phase 2 has NOT started. Existing FarmApp work, roles, encrypted session storage, payment safeguards and configured URL are preserved. No live registration, sign-in, OTP request/verification, cart, order or payment request was submitted by the agent.

## Reference and live contract

Compared the verified separate reference master 8f335e23f0d29c2953fe6373b7ff6f2da9e371e6 (Repositories.kt, AppViewModels.kt, MainActivity.kt, AuthScreens.kt) against FarmApp's CanonicalAuthService/AuthRepository/AuthViewModel, LoginScreen, signup, OTP and session persistence.

Fresh GET /api-docs returned HTTP 200. SHA-256: afd49841419d610d197cb1a08174d30e0f78fe4c1e7bdc20139c7e1589b71cef. Ignored capture: app/build/buyer-auth-phase1-openapi.json. API URL remains https://hankering-morphing-outclass.ngrok-free.dev/api/v1/ and generated BuildConfig was checked. No new tunnel URL was substituted.

- POST auth/register: canonical name/email/phone/password/role=BUYER and optional username/region; response is wrapped user, not login tokens.
- POST auth/login: email/password; wrapped tokens/user or verification-required gate. Existing strict token/user/role validation remains.
- POST auth/email-verification/request: email; wrapped challengeId UUID, expiresInSeconds and resendAfterSeconds. Current description says a 60-second send cooldown with active challenge reuse and maximum five sends per email/hour. Never loop/retry around this limit.
- POST auth/email-verification/verify: challengeId/email/ASCII six-digit code; successful void envelope confirms email only, not login tokens or role/admin approval.

The reference separates registration, OTP and sign-in and supplies Accept/application-json plus ngrok-skip-browser-warning. FarmApp now aligns the compatible behavior without importing plaintext token preferences, a second auth repository, permanent pendingPassword field or a standalone app module. FarmApp continues using its validated token-user identity rather than adding a redundant /auth/me request solely to mimic reference structure.

## Changes

1. **Origin-scoped auth gateway headers.** Added Accept: application/json if absent and ngrok-skip-browser-warning: 1 for configured ngrok origins. Preserves the exact API URL, redirect/retry disabling and existing authenticated client. Header copying is scoped to the configured scheme/host/port/API path and is not applied to unrelated origins.
2. **Buyer registration versus session validation.** A successful Buyer registration validates server-issued positive user ID, matching email and BUYER role, then can reuse its issued OTP challenge before demanding a complete login profile. Previously user.toAccount required full name/phone/status/verification metadata before entering OTP. These fields are still required where appropriate by the existing actual login/session validation. No partial registration response can establish an encrypted session. Auto sign-in after a verified/no-OTP registration must match registered Buyer identity. Farmer/Supplier profile paths remain unchanged.
3. **OTP continuation.** Existing issued challenge is reused without a redundant email request; missing challenge follows existing request/verify endpoints once. Verified registration resumes sign-in without repeating register. Credentials remain in a scoped ViewModel continuation only and are cleared on completion/cancellation/destruction, not stored in preferences or saved instance state. Verification alone never displays successful authentication. A missing continuation prompts sign-in instead of silently claiming completion.
4. **OTP error clarity and cooldown.** Fixed the controller swallowing its own specific HTTP failure into generic connection/code wording. Invalid/expired code and used challenge remain distinct; service failures display their HTTP category. Retry-After delta seconds and RFC 1123 dates now govern both verification and resend suppression after 429, without scheduling automatic requests. Normal resend countdown does not block entering a valid code.
5. **Preserved signup controls.** Previous 409 recognized conflict versus generic conflict, 422 field hints, 429 signup Retry-After deadline, same-frame submit latch, ViewModel loading gate and repository single-flight remain. Added safe contract/network diagnostic classification to unconfirmed Buyer registration failures; messages never expose raw response/exception text. Existing login/role validation, encrypted Keystore AES-GCM persistence and other-role routing remain.
6. **Opt-in debug auth trace.** Added a separate debug-only provider/UI/cache behind -Pkilivana.authDiagnostics=true (default false), independent of the existing cart diagnostic. User must tap Enable safe auth diagnostics (debug). The ten-minute volatile cache retains at most 64 typed events: REGISTER/LOGIN/OTP_REQUEST/OTP_VERIFY, request/HTTP/body-read/flow stage, numeric HTTP status, JSON/HTML/OTHER category and DNS/TLS/CONNECT/TIMEOUT/IO category. It cannot accept strings for identities, credentials, OTPs, tokens, raw headers/bodies, URLs or exception text. Release recorder is a no-op, with no provider/UI. Provider is protected by android.permission.DUMP and cannot perform HTTP or read session stores. No HTTP BODY logging was enabled.

## Connection issue diagnosis ? established versus pending

No literal ?Connection issue? exists in the current source tree. The precise user's failure cannot be assigned to DNS/TLS/backend/parse/session-storage without observing their actual attempt. Browser Swagger availability does not establish auth POST or SMTP/OTP latency, Android trust/network path, JSON envelope validity or session save success.

Confirmed differences fixed: reference ngrok bypass/Accept headers were missing from FarmApp's plain auth client; Buyer full-profile mapping could reject an otherwise usable registration identity/challenge before OTP; OTP controller flattened specific API failures into generic wording. These are defensible code fixes, not proof that one caused the reported Samsung message. The user-triggered trace will identify the failing operation/status/format/stage without collecting secrets. HTTP 409/422/429 are not relabeled network outages. A timed-out registration may have committed: sign in/check the existing account rather than repeatedly registering.

## Validation

Command: :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest -Pkilivana.cartStructureDiagnostic=true -Pkilivana.authDiagnostics=true.

- **394 unit tests passed; zero failures/errors.** Includes minimal Buyer registration ? issued OTP ? single login ? validated session; incorrect identity rejection; scoped headers; one-call DNS/TLS/timeout traces; HTML classification without body capture; OTP 422/429 and date cooldown; bounded opt-in cache. Previous Farmer/Supplier/signup/payment tests remain included.
- **Lint passed: zero errors; existing 57 warnings and 1 hint.**
- **Debug APK and UI-test APK built successfully.** Application ID unchanged: com.example.farmapp.dev2231918adc4d4b9d.
- Samsung installation attempted with adb install -r, but **device RF8WC0K043H was not found**. Subsequent adb devices showed no devices. No uninstall, data clear or account change occurred. This is a device connection blocker, not an APK installation rejection or a demonstrated auth failure.
- New synthetic Samsung tests are prepared for Buyer registration/OTP/sign-in, verification-gated existing login, and explicit trace arming/provider privacy. They have compiled but have NOT run on Samsung in this checkpoint. Existing authentication suites should run after reconnect; no passing device result is claimed yet.

Logs are ignored buyer-validation-auth-phase1-compile.log and buyer-validation-auth-phase1.log. No raw credentials/request captures were stored. At the earlier disconnected-device checkpoint Phase 1 was build-ready but not yet installed; the successful reconnect validation above supersedes this limitation.

## Manual test checkpoint (after reconnect, installation and UI checks)

1. Reconnect Samsung RF8WC0K043H with USB debugging authorized; agent installs both APKs -r and runs synthetic auth tests. Existing signed-in data is preserved. Agent will not log out the real Buyer.
2. After installation is confirmed, open the correct FarmApp debug application. If already signed in, choose yourself whether/when to sign out for this test; the agent must not clear/replace the account.
3. On the auth screen tap **Enable safe auth diagnostics (debug)** once immediately before the attempt. Arming makes no request. Do not tap again during the attempt, as rearming clears previous trace events.
4. Make one intentional signup attempt only if creating that account is actually intended. Follow the emailed OTP on-device. Do not send OTP/password/email/phone/token to the agent. If the account may already exist or registration times out, use sign-in instead of repeating registration. Respect any cooldown.
5. Tell the agent when the attempt finishes. The agent reads only: content://com.example.farmapp.dev2231918adc4d4b9d.auth-diagnostics/report via the protected debug provider within ten minutes. Querying does not initiate a request. Expired/no-active report requires user rearming, not credential extraction.

Actual sanitized user-attempt findings remain pending. No claim that real Buyer signup/login now succeeds is made before that check.

## Source scope

Main: network/canonical/AuthAttemptTrace.kt (new typed interceptor/event schema), CanonicalAuthService.kt, AuthLoginDiagnostics.kt stage enum, data/remote/ApiClient.kt, repository/AuthRepository.kt safe diagnostic classification, viewmodel/AuthViewModel.kt trace/continuation handling, ui/screens/LoginScreen.kt debug entry placement, ui/screens/auth/EmailVerificationScreen.kt cooldown/error handling. Debug/release AuthAttemptTrace and AuthDiagnosticEntry variants, debug manifest/provider and Gradle opt-in flag. New BuyerAuthenticationPhaseOneTest, AuthAttemptTraceTest, BuyerAuthenticationPhaseOnePresentationTest and AuthDiagnosticPresentationTest. No checkout/payment implementation changes.

Nothing staged, committed, pushed, reset, cleaned or stashed. All previous project work preserved.
