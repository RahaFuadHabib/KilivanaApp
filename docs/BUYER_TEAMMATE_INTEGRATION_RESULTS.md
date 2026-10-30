# Selective Buyer integration results

Implemented 2026-10-28 under the approved `BUYER_TEAMMATE_INTEGRATION_PLAN.md`. FarmApp remains the only application. Presentation patterns were selectively adapted from `brianmuigai2-stack/Kilivana-buyer`, inspected at local commit `1813880`; no Git merge, app module import, teammate authentication, environment files, keys, dependencies or bundled product photos were copied.

Current as of the Buyer UI refinement on 2026-10-28. The original integration plan is historical. Current build configuration and package identity are documented in `BACKEND_URL_REPLACEMENT_20261027.md`; no temporary tunnel is a shared production default.

## Features integrated

- Buyer-only Home, Shop, Cart, Orders and Account navigation under the existing `AuthDestination.BUYER` / session-generation boundary. Home includes live produce previews, backend category shortcuts and an orders/tracking link. Saved, Cart and Address book dashboard shortcuts have been removed. Cart remains in shopping navigation and Shop shows its unit count; addresses remain in Account/cart. No fabricated recommendation, freshness, seller ranking or order statistics.
- Shopping uses the existing `MarketplaceRepository` and authenticated Retrofit/OkHttp client. Search/category filters and Load more remain server-backed. Price sorting is explicitly labeled as sorting **loaded results**, with BigDecimal comparisons. Farmer produce only; no unrestricted catalog fallback.
- Product detail reloads and validates the selected ID/availability, shows real description, exact price, available quantity, seller user ID, minimum order, primary image and remaining gallery. Existing ProductImage and origin/session protection are reused; missing images show an honest absence state.
- Detail quantity input enforces positive whole quantities, minimum order and verified available stock. Local cart adds accumulate safely, cap at available stock, prevent integer overflow, support increase/decrease/remove, and show exact decimal estimates. Invalid/unavailable/restored-unresolved lines block subtotal calculation rather than pretending a partial total is complete.
- Product cards now have a centered circular + that adds exactly one unit through the existing local cart, independent of image/detail navigation. A per-product 600ms monotonic guard suppresses rapid successful additions; errors never show false success. A bottom snackbar confirms local writes for 3.5 seconds. Minimum orders still apply; larger minimums must be entered in detail.
- New Save/Unsave card actions are removed. Historical saved IDs and their Account view are retained without deleting user data. Saved/cart preferences are partitioned by SHA-256 of backend origin, BUYER role and user ID. Only numeric product IDs and quantities persist, not tokens, order/payment data, addresses, product snapshots or prices. Restored IDs are reloaded using current product detail; unavailable IDs remain removable without fabricated data; existing available saved rows remain reviewable. Each selection type is capped at 100 entries.
- Local cart is explicitly a shopping list, not a server cart, reservation or submitted order. Estimated item totals exclude delivery and are labeled provisional. No guest cart or second login flow was introduced; Buyer features require the existing Buyer session.
- Read-only address book: GET `users/addresses` through the shared authenticated client with session tag. Validate positive/unique IDs, current account ownership, nonempty address text, and finite/in-range optional coordinates. A malformed/foreign-owner response is rejected atomically. Refresh/loading/empty/error states retain only previously validated addresses, with an explicit stale-data message on refresh failure. No fake address defaults or local address masquerading as a backend record.
- Account hub links to the existing shared account/profile editor, saved items, addresses and orders. Existing BuyerOrdersScreen, order ownership/state validation, payment reads, receipt confirmation and delivery tracking remain in use unchanged.
- New controller uses request identities for detail close/reopen races, ignores late logout/account-switch responses, retains old feed on failed refresh, retries the attempted filters, uses displayed filters for Load more after a failed filter change, supports Clear filters, bounds saved data and prevents overlapping page/selection/address loads. Existing canonical authentication/OTP and all non-Buyer role screens are unchanged.

## Deferred features and safe states

| Feature | Current behavior | Remaining requirement |
| --- | --- | --- |
| Server cart synchronization | Local account-scoped shopping list only | Confirm deployed cart semantics, ownership, read/update/delete and partial-write/retry behavior |
| Delivery quote / checkout / order creation | Disabled Checkout button and explicit “no order or payment submitted” message | Current deployed quote/order permissions, quote-to-cart revision/address/expiry, authoritative totals, returned order identity and durable idempotency/reconciliation |
| Buyer STK initiation | No new initiation API/action/controller; existing read-only order payment display retained | Confirm purchaser order/intent ownership, whether checkout already triggers STK, duplicate-safe initiation/recovery, authoritative status and failure semantics |
| Address create/update/delete | Disabled editing action with permission explanation | Confirm current deployed mutation permissions and account binding; no spoofed userId or guessed coordinate defaults |
| Server favourites | Local saved IDs only | A server favourites contract if cross-device sync is required |
| Whole-catalog price sorting/filtering and seller-name search | Existing name/category search plus clearly labeled loaded-result price sort | Documented server parameters before claiming catalog-wide behavior |
| Guest shopping/login changes | None | Existing authenticated Buyer architecture deliberately retained |
| Notifications, promotions, rankings, COD, paid/delivered confirmation simulation | Not imported | Real supported contracts and product policy; no inert/fake-success features |

The initial integration assessment found the configured ngrok tunnel offline (`ERR_NGROK_3200`). A subsequent public read on 2026-10-28 succeeded: six Farmer records on one final page, of which four had positive available stock; IDs 189 and 126 were fully reserved and correctly excluded. See `BUYER_UI_AND_LOGIC_AUDIT.md` for details. That public read does not establish authenticated checkout permissions. No live permissions are asserted from that failure or from older local backend repositories. Earlier captured deployed OpenAPI advertises self cart/checkout/quote/order/address/buyer-STK routes, but current permissions/idempotency remain unverified. This integration enables only public/scoped catalog reads, self address reads and local selection edits. It adds **no** order, checkout, payment or address mutation endpoint. No live login, OTP, order, payment, callback or product mutation was used during implementation/automated validation.

## Files changed in this integration

Production files (paths relative to `app/src/main/java/com/example/farmapp/`):

- `marketplace/BuyerShell.kt`: replaces the minimal two-tab Buyer presentation with the Buyer shopping/navigation hub; keeps existing order/profile screens.
- `data/remote/ApiClient.kt`: additive `buyerAddressApi` getter only; existing transport/auth/image/profile setup preserved.
- New `buyer/BuyerShopping.kt`: pure shopping controller, catalog adapter, session checks, exact-money/quantity logic, saved/cart state and persistence interface.
- New `buyer/BuyerPersistence.kt`: account/backend-scoped product selections and existing-architecture ViewModel factory.
- New `buyer/BuyerAddresses.kt`: read-only typed address contract, ownership/coordinate validation and request state controller.
- New `buyer/BuyerScreens.kt`: dashboard, shopping/search/sort, saved items, product detail/quantity, local cart, address book and account presentation.

Tests:

- New `app/src/test/java/com/example/farmapp/buyer/BuyerShoppingTest.kt`: eleven mocked/pure tests for precision/minimum/stock/overflow, persistence/repricing, unavailable item totals/removal, failed filter retry, single-flight/logout, detail race/storage failure, scope keys, address ownership/coordinates and invalid response retention.
- New `app/src/androidTest/java/com/example/farmapp/buyer/BuyerShoppingPresentationTest.kt`: eight isolated Compose tests for dashboard navigation, saved empty state, exact cart subtotal/disabled checkout, address failure/disabled editing, detail quantity validation/add callback, catalog loading/failure/retry. These create synthetic view state; no login/session store or live transport is constructed.

The selective Buyer integration itself did not change Gradle/dependencies, manifest/application ID, auth/session/network interceptors, existing order controller, profile editor or tracking. The full pending Git snapshot also includes earlier coupled role/auth work and the later backend-configuration cleanup; those broader changes are listed separately in the audit commit plan. Existing uncommitted work and teammate clone remain intact.

## Current validation and installation

The Buyer refinement passed 334 unit tests, lint with zero errors (52 warnings and one hint), debug APK and UI-test APK builds. Samsung RF8WC0K043H subsequently connected; both APKs installed in place with `-r`, and all eight isolated Buyer presentation tests passed. No uninstall, data clear, login, order or payment was performed. Backend configuration is being revalidated separately for GitHub preparation; the final current results are in `BUYER_UI_AND_LOGIC_AUDIT.md`.

## Historical initial integration validation and installation

The following counts, APK hash, tunnel URL and disconnected-device observations describe the earlier integration run. They are retained for traceability and superseded by the current results above; do not use the old APK hash for the current artifact.

- Initial full run: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` succeeded. 332 unit tests, zero failures/errors/skips. Includes the existing Farmer/Supplier/Buyer catalog, auth/OTP, order/payment and tracking regressions. Lint zero errors, 52 warnings; the new Buyer warning is the optional SharedPreferences KTX-style suggestion. No dependency added for it.
- Final gallery correction ensures the already-rendered primary photo is excluded by image ID and every remaining photo is retained. Re-ran the full unit suite, lint, debug APK build and UI-test APK build afterward: BUILD SUCCESSFUL. Final results remain 332 tests, zero failures/errors/skips, zero lint errors and 52 warnings.
- Generated BuildConfig/metadata retain application ID `com.example.farmapp.dev2231918adc4d4b9d`, API URL `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`, canonical backend enabled and mock auth disabled. APK DEX inspection found zero old-origin strings and zero duplicate `/api/v1/api/v1/` segments.
- Device checks, including a fresh check after the agent restart and an outside-sandbox check, report no adb devices: Samsung `RF8WC0K043H` is disconnected. User was asked to reconnect. Six instrumented tests are compiled but **not executed**, and in-place installation is **not performed**. No device test or install success is claimed.
- Validated APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `DF727BB7BDB7EE5C7F71AE042F02A5CC67887B12B9D043A2AFDA5D49F68A284D`. Resume with the isolated `BuyerShoppingPresentationTest` class when the device reconnects, then install this validated app with `adb -s RF8WC0K043H install -r app/build/outputs/apk/debug/app-debug.apk`. Do not clear application data. A build/test-APK rebuild is needed only if source changes or a test failure requires correction.
- No uninstall, app-data clear, backend edit, reset, clean, stash, commit or push operation occurred. Existing application data has not been accessed or modified by this task.
