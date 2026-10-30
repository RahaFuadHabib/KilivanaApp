# Unified Kilivana authentication UI cleanup

29 October 2026. FarmApp, feature/supplier.

## Current Samsung validation — completed

Samsung RF8WC0K043H is authorized (`device`). The latest debug APK was installed successfully using `adb install -r`, retaining package `com.example.farmapp.dev2231918adc4d4b9d` and the existing signed-in Buyer session. No uninstall, clear-data or logout was performed. The configured backend URL remains `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`.

- Samsung mocked authentication/navigation suite: **26 passed**, including shared Buyer signup/login/OTP, removal of both unwanted controls, role routing, Supplier recovery and authentication/profile regressions. These tests submit only mocked requests.
- Installed-session read-only smoke test: **1 passed**. `InstalledBuyerHomeSmokeTest` launches the actual app and activates only Home. The sanitized marker confirms `Brian master 8f335e2`, `Integrated Buyer active: true`, `Screen: HOME`. Relaunching after instrumentation confirmed HOME again. No account details or credentials were dumped.
- Latest debug and UI-test APK builds passed. The final UI-test APK/lint run passed in 1m 10s. Full unit results remain 424 passed; lint reports 0 errors, 79 warnings and 1 hint.
- Installed main APK SHA-256 matched the built APK: `d310201baa58730eea96d5d7e50ab3ef8ece1c245fa0d4bd945d80d02398a531`.
- Buyer uses the shared Welcome, Sign In, Sign Up and OTP presentation. The two extra controls are absent in the exercised authentication states. Successful mocked Buyer authentication routes through the shared session boundary; the retained real Buyer session opens Brian's integrated Home. No new live login/signup was submitted to verify this.
- Farmer, Supplier and Inspector dashboard destinations remain unchanged; automated routing regressions passed. Inspector self-registration remains administrator-provisioned as before.

Additional validation file: `app/src/androidTest/java/com/example/farmapp/brian/InstalledBuyerHomeSmokeTest.kt`. No further production authentication changes were needed during this device-validation follow-up. The earlier disconnected-device entries below are historical and are superseded by these successful results.

No live cart mutations, orders, payment requests or STK pushes were performed. No staging, commit, push, reset, clean or stash was performed.

## Completed

- Removed the visible safe-auth-diagnostics button and Buyer sign-in/create-account shortcut from LoginScreen. They no longer occupy space above Welcome, Sign In, Sign Up, Forgot Password or OTP screens, even in a diagnostics-enabled debug build.
- Removed the Buyer-only presentation flag and conditional Brian authentication/OTP routing. LoginScreen now uses the existing AuthWelcomeScreen, SignInScreen, SignUpScreen, PasswordRecoveryScreen and EmailVerificationScreen for every role.
- Removed the signup dropdown callback that redirected Buyer away from the shared form. Buyer was already present in SignupRole; selecting it now retains the normal signup fields, confirmation password, validation, submission latch and OTP continuation.
- Removed the Buyer-specific logout/sign-in presentation hint. Explicit logout for every role returns to the shared Sign In screen.
- Preserved the original screen implementations, artwork, theme and field layouts. AuthPage retains responsibility for safe screen/keyboard insets; the extra debug/header wrapper was removed.
- Kept secure session persistence, AuthViewModel, AuthRepository, canonical API adapters, OTP challenge reuse, rate limits/error mapping and backend-driven role navigation unchanged.
- Kept BUYER -> BrianBuyerApp routing. FarmerShell, SupplierShell and InspectorShell destinations remain unchanged. Brian's marketplace, cart, saved items, addresses, checkout, orders, M-Pesa safeguards and cart diagnostic were not altered by this cleanup.
- The reference Brian authentication components remain retained source without a production navigation entry; no second authentication/session system is introduced.

## Live backend contract verification

A fresh read-only GET /api-docs returned HTTP 200 at the existing configured backend URL. No login, signup, OTP, checkout or payment submission was made against the live backend.

- UserRegistrationRequest.role and UserResponse.role enumerate BUYER (uppercase), alongside FARMER, SUPPLIER and INSPECTOR (plus other backend roles). No new role identifier was invented.
- POST /api/v1/auth/register accepts UserRegistrationRequest; POST /api/v1/auth/login accepts AuthLoginRequest.
- AuthLoginRequest contains only email and password. Therefore real sign-in remains one shared credential form: the authenticated backend response determines the dashboard. A client role choice must not override that role or be added as an unsupported login field.
- The shared signup Account type dropdown includes Farmer, Buyer, Supplier and Inspector. Existing Inspector administrator-provisioned account policy remains: its option explains that assigned credentials should be used, and self-registration remains disabled. Farmer/Buyer/Supplier retain their existing supported registration behavior.
- Email-verification endpoints confirm email without issuing JWTs or changing role/administrator approval. The existing shared OTP controller continues sign-in/session establishment only after verification succeeds.

## Diagnostics preserved without a customer UI

The debug-only sanitized recorder, ten-minute limit, bounded events, safe interceptor and read-only report provider remain. No recording is automatically enabled.

When separately requested by the user, a developer with adb shell's DUMP permission can explicitly opt in using:

    adb -s RF8WC0K043H shell content call --uri content://com.example.farmapp.dev2231918adc4d4b9d.auth-diagnostics --method arm10Minutes

The provider explicitly enforces DUMP permission for this call, requires a diagnostics-enabled debug build, accepts no payload and performs no HTTP request. The existing /report and /buyer-ui sanitized query paths remain. This command was NOT executed during this cleanup; the instrumentation privacy test uses only a synthetic in-process event and clears its test cache.

## Files changed in this cleanup

| File | Change |
| --- | --- |
| app/src/main/java/com/example/farmapp/ui/screens/LoginScreen.kt | One shared authentication/OTP presentation; both extra controls removed. |
| app/src/main/java/com/example/farmapp/ui/screens/auth/SignUpScreen.kt | Buyer remains in the shared role-selection form; no redirect callback. |
| app/src/main/java/com/example/farmapp/ui/screens/auth/SignupFormValidation.kt | Invalid-role guidance includes Buyer. |
| app/src/main/java/com/example/farmapp/ui/navigation/AppNavigation.kt | Removed Buyer-only sign-in hint; preserved all authenticated dashboard routes. |
| app/src/debug/java/com/example/farmapp/ui/screens/auth/AuthDiagnosticEntry.kt | Removed visible diagnostic UI; retained provider/recorder and protected explicit developer opt-in. |
| app/src/androidTest/java/com/example/farmapp/buyer/BuyerAuthenticationPhaseOnePresentationTest.kt | Shared Buyer credential/signup/OTP tests; role choices and no-extra-controls assertions across auth screens. |
| app/src/androidTestDebug/java/com/example/farmapp/network/canonical/AuthDiagnosticPresentationTest.kt | Underlying sanitized recorder/provider test no longer depends on removed UI. |
| docs/UNIFIED_AUTH_UI_CLEANUP.md | This implementation and validation report. |
| Historical authentication/integration reports | Pointer to the current shared authentication implementation. |

## Validation and installation status

- Full unit suite: 424 passed; 0 failures, 0 errors. Includes existing four-role login/signup/session-restoration routing, canonical authentication, validation and payment regressions.
- lintDebug: passed; 0 errors, 79 warnings, 1 hint.
- assembleDebug: passed.
- assembleDebugAndroidTest: passed.
- An initial UI-test compilation referenced the removed diagnostic button. That obsolete test was adapted to the retained recorder/provider and the complete build then passed.
- Initial physical-device check was blocked by disconnection; this is now resolved. See the current Samsung validation above for successful installation and 27 passing device checks.
- The prepared APK retains com.example.farmapp.dev2231918adc4d4b9d and the existing configured API URL. Installation must use adb install -r after reconnection, with no uninstall, logout, clear-data or identity change.

No live accounts, orders or STK pushes were created. No reset, clean, stash, stage, commit or push. All unrelated work is preserved.
## Samsung reconnect follow-up

The user reported reconnecting RF8WC0K043H. Windows read-only device inspection detects SAMSUNG Mobile USB Composite Device and SAMSUNG Android ADB Interface with Status=OK. However, both the PATH scrcpy ADB client (34.0.3) and Android Studio SDK ADB client (36.0.0) list no devices; explicit get-state for RF8WC0K043H returns device not found. The SDK ADB server was refreshed once (host service only, no phone/app restart or data operation), with no transport recovered. A bounded one-minute read-only reconnect check still found no authorized transport. The user was asked to unlock/replug/accept USB debugging.

Current-source validation rerun: testDebugUnitTest, lintDebug, assembleDebug and assembleDebugAndroidTest succeeded in 2s (77 tasks up-to-date, 1 executed). The unchanged 424-pass unit suite and lint results above remain current. No production code changes were needed for this follow-up.

Prepared main APK SHA-256: d310201baa58730eea96d5d7e50ab3ef8ece1c245fa0d4bd945d80d02398a531. Generated configuration confirms the original application ID and API URL, USE_MOCK_AUTH=false. No files are staged.

Because there is no ADB transport, installation, launch and current Samsung UI tests remain NOT PERFORMED. Visible-control absence and Buyer/Farmer/Supplier/Inspector routing are confirmed in source and existing unit checks, not newly verified on the physical phone. No installation success or authenticated Buyer-home result is claimed. No credentials, private app storage or authentication secrets were accessed; no orders, STK pushes, commits or pushes occurred.
## Second Samsung reconnect check

The fresh SDK ADB device check again returned no devices and RF8WC0K043H get-state returned device not found. Windows PnP parent/instance identity confirms the physically connected Samsung is RF8WC0K043H. Its composite service and WINUSB ADB interface both report Status OK and problem code 0. The SDK server had no one-device exclusion filter. A host-only compatibility check with the older scrcpy ADB server also found no transport; mDNS advertised no wireless ADB service. The standard SDK server was restored, and a bounded one-minute reconnect watch still found no transport. No unsupported USB backend was enabled and no drivers, authorization keys, phone settings or app data were changed.

The user was asked to toggle USB debugging off/on on the unlocked phone, reconnect, and accept the authorization prompt without revoking authorizations. Physical USB detection is confirmed, but ADB authorization, UI tests, installation and app launch remain unverified/not performed. The prepared APK and existing build/test results remain unchanged. No orders, STK, Git reset/clean/stash/commit/push occurred.
