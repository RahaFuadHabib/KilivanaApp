# Kilivana offline review demo

> **Archived instructions — superseded by the approved recovery/separation.**
> The original `com.example.farmapp` app no longer exposes offline demo entry points,
> and demo-only Kotlin sources are excluded from its builds. Do not follow the
> original-package installation instructions below. The independent Review project
> is at `C:\Users\AppleFi\Documents\Kilivana-Review\FarmApp`, uses application ID
> `com.example.farmapp.review`, and is labeled **Kilivana Review**.
> Current instructions are in that project's `docs/REVIEW_LAUNCH.md`.
> Installation of either new APK remains pending user approval.

## Launch on the Samsung Galaxy A15

1. Install/update `app/build/outputs/apk/debug/app-debug.apk`, then open **Kilivana**. For USB installation, run `adb install -r app/build/outputs/apk/debug/app-debug.apk` from this project folder. This build uses package `com.example.farmapp`; it does not enable the legacy mock-auth build flag.
2. Tap **Continue with Demo Data** on the welcome or Sign In screen. Scroll down if needed.
3. No credentials or internet connection are required. **Demo Mode** remains visible in the toolbar.
4. If a real session is already open, use its existing Logout action to reach Sign In before entering the demo.
5. To leave the demo, tap the toolbar exit icon or **Profile → Exit Demo Mode / Logout**. This returns to normal Sign In.

The demo uses a separate presentation route and local repository. It does not create `AuthResponse` objects, authenticate, generate tokens, write to secure session storage, or send farmer API requests. Existing real login, registration, refresh, logout, API configuration and backend repositories are preserved.

## Review walkthrough

| Destination | Review behavior |
| --- | --- |
| Home | Mary Wanjiku, Wanjiku Family Farm near Njoro in Nakuru County; Maize, Tomatoes, Potatoes and Beans; KES prices; crop/order statistics; recent sample and local activity. Toolbar search filters crop names/descriptions and order numbers/products. Tap a crop for existing details/editor and related orders. |
| Orders | Four deterministic sample orders: PLACED, CONFIRMED, IN_TRANSIT and DELIVERED, dated 5–8 October 2026. View details shows buyer, quantities, item prices and total. The placed order permits **Simulate confirmation**. |
| Add | Enter crop name, category, quantity, unit, KES price and description, then **Save demo crop**. Validation rejects incomplete/invalid inputs. New listings update Home and its statistics immediately. Demo photos/uploads are omitted; real camera/gallery publishing is preserved. |
| Map | Local illustrative farm/market location diagram and selectable coordinate cards. No network, location permissions or Mapbox tile downloads are needed for this preview. The real Farmer Map screen retains Mapbox and foreground location controls. |
| Supplies | Honest coming-soon preview; no invented operational supplier inventory or purchasing flow. |
| Profile | Sample farmer details, shared About dialog, shared theme toggle, and an explicit exit action. |

Try adding **Sorghum**, category **Cereals**, quantity **450**, unit **kg**, price **70**, description **Clean dry sorghum from Nakuru.** Search for Sorghum afterward. Crop edits/unlisting and order confirmation are local simulations. Unlisting preserves sample orders. Confirmation does not reserve stock, settle payment, book transport or notify a buyer.

Demo changes survive normal configuration changes through saved instance state. They reset on exit. This is not durable app storage: a fresh launch without saved state starts from the deterministic seed data. Demo data is never mixed with real crops, orders, drafts or account storage.

## Build fix and integration limits

The reported `FarmerMapScreen.kt:163:29 Unresolved reference 'location'` reproduced alongside unresolved Mapbox classes/imports. The version catalog already defined Mapbox 11.32.0 and Settings already configured its repository, but the app did not include the SDK dependencies. Restoring `implementation(libs.mapbox.maps)` and `implementation(libs.mapbox.compose)` fixed Kotlin compilation without removing or rewriting the Map screen.

Real authentication, live crop/category/order APIs, photo uploads and other server-backed operations still require the configured backend. Live Mapbox tiles need connectivity and a valid existing access token; no offline tile pack is configured. No real map-tile availability was established by the demo tests.

The nearby `kilivana-shared-backend` README, product contract, order statuses and order controller were inspected read-only. Its newer numeric IDs/envelopes differ from this Android client's existing contract. No API migration or new backend integration is claimed or implemented by this demo.

## Files changed for this task

- `app/build.gradle.kts`: restore existing Mapbox dependencies; preserve backend configuration.
- `app/src/main/java/com/example/farmapp/demo/DemoRepository.kt`: typed seed/profile/location data and isolated local mutations/snapshot restoration.
- `app/src/main/java/com/example/farmapp/ui/navigation/AppNavigation.kt`: presentation-only demo route and exit to Sign In.
- `app/src/main/java/com/example/farmapp/ui/screens/LoginScreen.kt`: wire demo entry callback.
- `app/src/main/java/com/example/farmapp/ui/screens/auth/AuthWelcomeScreen.kt`: welcome demo button.
- `app/src/main/java/com/example/farmapp/ui/screens/auth/SignInScreen.kt`: Sign In demo button.
- `app/src/main/java/com/example/farmapp/ui/screens/DemoFarmerShell.kt`: all six offline destinations and local actions.
- `app/src/main/java/com/example/farmapp/ui/screens/FarmerShell.kt`: share existing navigation, profile display/actions and feature empty state with the isolated demo.
- `app/src/main/java/com/example/farmapp/ui/screens/FarmerScreen.kt`: expose existing overview presentation for reuse.
- `app/src/main/java/com/example/farmapp/ui/screens/AddCropScreen.kt`: expose existing unit selector for reuse.
- `app/src/main/java/com/example/farmapp/ui/screens/CropDetailScreen.kt`: optional demo order-detail callback and local-edit explanatory text; real defaults preserved.
- `app/src/test/java/com/example/farmapp/demo/DemoRepositoryTest.kt`: deterministic data, totals, validation, isolation, edit/unlist/confirm and restoration tests.
- `app/src/androidTest/java/com/example/farmapp/ui/screens/DemoModePresentationTest.kt`: demo entry/exit, all destinations, local addition/search, saved-state recovery, auth isolation and real login transport checks; review screenshots.
- `docs/OFFLINE_DEMO_REVIEW.md`: this walkthrough and change report.

Existing uncommitted work was preserved. No backend, database, migrations, account data, commit or push operations were performed.

## Verification

- `:app:compileDebugKotlin`: passed after restoring the Mapbox dependencies.
- `:app:assembleDebug`: passed; output is `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:testDebugUnitTest`: **122 passed**, including all six new `DemoRepositoryTest` cases.
- Focused `:app:connectedDebugAndroidTest` on **Samsung Galaxy A15, SM-A155F, Android 16**: **11 passed**, zero failures. This covered `DemoModePresentationTest`, existing `FarmerShellPresentationTest` (320dp and larger text), existing `CropSavePresentationTest`, and existing `AuthFlowIntegrationTest`.
- The demo UI tests prove entry/actions/exit make zero authentication transport calls and zero session writes, all six destinations remain available, order details/confirmation work locally, addition and search update Home, saved instance state retains additions, exit resets the demo, and real sign-in still invokes its existing transport.
- `git diff --check`: passed. Existing line-ending conversion notices are not whitespace errors.
- The final APK was installed successfully with `adb install -r` on the Galaxy A15, and `com.example.farmapp/.MainActivity` was launched. The pre-existing legacy `com.example.farmapp.demo` package was left intact.
