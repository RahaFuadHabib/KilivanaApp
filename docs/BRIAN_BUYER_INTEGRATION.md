# Brian Buyer integration into unified FarmApp

> Current authentication presentation: all four roles use the shared Kilivana Welcome/Sign In/Sign Up/OTP flow. The Buyer-only authentication redirect and visible diagnostics shortcut were removed. Brian shopping/dashboard routes remain integrated. See [UNIFIED_AUTH_UI_CLEANUP.md](UNIFIED_AUTH_UI_CLEANUP.md) for current validation and installation status; earlier separate-Brian-auth descriptions below are historical.


> Latest checkout update: the guarded Brian cart-upload/quote/order-readback flow is now implemented without requiring GET cart lines on the happy path. See [BUYER_CHECKOUT_TEST_READINESS.md](BUYER_CHECKOUT_TEST_READINESS.md). Earlier GET-only checkout/blocker descriptions below are historical. The current tunnel returns ERR_NGROK_3200, and STK submission remains disabled until Daniel confirms sandbox/production for the active backend.


Date: 29 October 2026. Source: `Kilivana-buyer-reference`, clean `master` revision `8f335e23f0d29c2953fe6373b7ff6f2da9e371e6` from Brian's complete repository. Destination: FarmApp, `feature/supplier`.

## Implemented production integration

This is production-source integration, not the earlier androidTest preview/comparison harness. The BUYER branch in `AppNavigation` now opens `brian/BrianBuyerApp`; the old BuyerShell is no longer routed. Other role shells remain in place. There is one application and the installed debug application ID remains `com.example.farmapp.dev2231918adc4d4b9d`.

Brian's actual Kotlin screens and their theme/resources were copied under `app/src/main/java/com/example/farmapp/brian`, with source provenance comments:

- HomeScreen.kt (including original home header, banner, sections and category layout)
- ProductsScreen.kt
- ProductDetailScreen.kt
- SearchScreen.kt
- CartScreen.kt
- SavedItemsScreen.kt
- OrdersScreen.kt
- OrderDetailScreen.kt
- CheckoutScreen.kt
- OrderConfirmationScreen.kt (including original payment-status layout)
- AccountScreen.kt
- AddressBookScreen.kt
- AuthScreens.kt (login, signup and OTP forms)
- AuthenticationRequiredScreen.kt
- SharedComponents.kt (product grid, cards and common layout helpers)
- model/Models.kt
- ui/theme/Color.kt, Theme.kt and Type.kt

Brian's 19 JPEG drawable assets (3 banners and 16 produce images) were copied without overwriting conflicting existing assets. Product rows use real backend image URLs, or a neutral placeholder; the produce assets are not assigned to arbitrary backend products. Some original resource assets are unused, as they were in the source. No source repository Git metadata, separate manifest/application, standalone launcher or plaintext session preferences were imported.

The original five-tab navigation (Home, Products, Cart, Orders, Account) and nested product/search/saved/address/checkout/order/payment screens now live inside FarmApp's BUYER role. New Navigation Compose dependency: 2.8.6. Shared Farmer/Supplier/Driver/Inspector role routes were not replaced.

## Required adaptations

- Packages and R imports use FarmApp. Brian's original typography/colors/spacing/card implementations are retained rather than recreating the old FarmApp Buyer UI.
- BrianAuthentication bridges the actual reference forms to the existing AuthViewModel, canonical backend API and encrypted session. Registration and login use the existing OTP challenge and typed error mapping. The reference plaintext token store is not imported. The multi-role entry retains a Buyer sign-in/create-account link, and choosing BUYER registration opens Brian's form. Buyer logout returns to Brian's sign-in presentation.
- BrianVerification uses Brian's actual form with the existing verification controller, issued challenge, resend/429 cooldown and authenticated-session completion. Email verification is not treated as proof that subsequent sign-in succeeded. Passwords are not saved into instance state or logs.
- Brian's cart ViewModel API is a small presentation facade over the ONE existing account/origin-scoped BuyerShoppingController and persistent preferences. Existing cart and saved IDs are retained. Quantity/stock/minimum-order checks, decimal totals and rapid-add protection remain in that controller. No second cart/store is created.
- Marketplace fetching remains the canonical paginated FARMER catalog. Brian's search/category controls trigger fresh backend searches; All clears the category. Load-more controls preserve pagination. Pull-to-refresh uses the existing controller. The reference price slider is corrected so products arriving after initial empty state are not hidden by an initial maximum price of one.
- BigDecimal replaces the reference integer/Double monetary calculations. Historical orders are mapped from their own order lines and backend subtotal/delivery/total fields, independently of currently listed products. Missing information remains unavailable/omitted. Backend line subtotals are retained. Disputed orders do not appear as pending.
- Brian's address-book screen uses authenticated backend CRUD, owner validation, nullable optional coordinates and readback. A durable marker prevents blind retry after an uncertain write. Earlier unresolved address saves remain guarded. No automatic geocoding or invented coordinates. Saved selection stays in the original account-scoped delivery preferences.
- Existing pickup station management is retained and accessible from checkout under Brian's theme; Brian's source does not include an equivalent station-management implementation. Selecting an actual station can populate editable one-time pickup-location text for the current API contract. No station is fabricated.
- Existing-order Pay with M-Pesa actions use the original BuyerPaymentSession/controller. It verifies ownership/status/amount, uses the current authenticated client, validates Kenyan numbers, requires a separate confirmation, durably reserves attempts before STK, and reconciles/polls after submission. Neither selection nor opening a screen submits a payment. Brian's payment-status layout is adapted to controller-provided states; no access token is passed to a screen. Initiation HTTP 200 is not payment confirmation. Unknown/cancelled undocumented statuses remain unconfirmed and do not enable another STK attempt.

## Live contract findings and remaining checkout gap

Fresh read-only OpenAPI retrieval returned HTTP 200. Its SHA-256 was `4d69607f1435fc1394a5986ffd4baf9a8ca5f1b7bae05feccd57d76f08b4c367`. The configured URL was preserved: `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`.

- `GET /carts/buyer/{buyerId}` still documents only id, buyerId, status, createdAt, updatedAt. The prior user-triggered sanitized Samsung diagnostic also returned metadata only; that observation did not prove a populated server cart existed. The diagnostic remains debug-only and user-triggered; this integration does not run it automatically.
- `GET /cart` advertises items/totals in prose but points at the same metadata-only CartResponse. `/checkout/validate` also returns that schema. No authenticated undocumented response is invented.
- Both `/cart/items` and `/carts/buyer/{buyerId}/items` mutation routes are documented. Their incremental/replacement semantics, duplicate/retry reconciliation and authoritative line readback remain unresolved. No live cart upload or synchronization was executed.
- Current deployed DeliveryQuoteRequest requires deliveryMethod and uses addressId for DOOR_DELIVERY or one-time pickupLocation text (maximum 500) for PICKUP_STATION. It no longer lists pickupStationId. The imported Buyer contract is adapted accordingly, with mutually exclusive location fields and tested quote amount/expiry validation. The previous station-ID quote DTO belongs to the preserved legacy flow and is not called by BrianBuyerApp.
- Station listing/saved/default endpoints remain documented independently. No real saved station was modified during automated testing.
- New-cart checkout presents Brian's checkout screen and location selection, but performs only the guarded read-only server-cart verification. It does not call checkout, quote, order or STK from this path while intended quantities cannot be read back. This is an explicit unresolved backend prerequisite, not a claim that Brian's original checkout is safely compatible.
- Brian's original blind cart-upload loop and immediate order-to-STK submission were not copied: they can duplicate an existing server cart and skip explicit payment confirmation/recovery. The compatible solution retains the actual UI and existing guarded controller, and requires authoritative cart lines plus safe order reconciliation before enabling order creation.
- No client-only KSh 30 fee is added to a payable amount. No delivery fee or payment success is fabricated.

Daniel must supply an authenticated authoritative cart-item shape (product ID, quantity, stable line identity and monetary fields), confirm add/update semantics and retry reconciliation, and confirm order idempotency or a reliable quote-to-order recovery lookup. Until then, newly created cart orders cannot be demonstrated safely. A real eligible existing unpaid Buyer order can use the guarded M-Pesa screen, but eligibility and any live STK demonstration require actual authenticated verification and separate payment approval.

## Validation and Samsung checkpoint

First full validation: 400 unit tests passed; debug APK and UI-test APK builds passed; lint passed with 0 errors, 78 warnings and 1 hint (including copied unused source assets). An initial lint invocation failed internally when a new test file appeared during analysis; rerunning with the complete source set succeeded.

Both APKs installed successfully on RF8WC0K043H using adb install -r. No uninstall, clear-data, logout or package change was performed. First Samsung run: all 8 new Brian component tests passed; signup/OTP tests passed. One older BuyerPaymentPresentationTest failed with 'No compose hierarchies found' and needs an isolated rerun. No test sent live credentials, cart writes, orders or payments.

After opening the production MainActivity, the device showed the unauthenticated welcome screen. The static debug-only /buyer-ui probe reported Integrated Buyer active: false, Screen: NONE. This does not confirm a signed-in Buyer route; a manual Buyer sign-in was requested. We did not retrieve or replace credentials. After manual login, use the static probe to verify HOME and the Brian implementation marker. Do not claim authenticated Samsung verification from source-level routing alone.

Final validation after the small-screen/status/quote updates:

- Full unit suite: **402 passed, 0 failed, 0 errors**, including 8 new Brian mapping/facade/current-quote tests.
- Android lintDebug: **passed, 0 errors, 78 warnings, 1 hint**. No lint checks were disabled to obtain this result.
- assembleDebug and assembleDebugAndroidTest: **passed**, unified final build completed in 1m 51s.
- Final Samsung mocked regression run: **17/17 passed** in 42.328s. This includes all 8 new actual-Brian component tests, both additional Brian login/signup-to-OTP continuation cases, and the existing Buyer signup/OTP/payment cases. The older transient Compose hierarchy failure did not recur. Test callbacks were mocked; no live STK or order was sent.
- Both final APK installations succeeded with install -r.
- Installed public base APK SHA-256 matched the final local APK: `60b2929e3c9e9843b97673457f2529b6dce5c8571ae395b9df912ae8d98ac6e2`.
- Generated BuildConfig confirms the unchanged package and API URL above.
- Latest static /buyer-ui probe still reported active=false, Screen=NONE at the manual sign-in checkpoint. The device's production MainActivity was returned to the foreground. Manual authenticated Buyer verification is outstanding; this is not recorded as a passed live flow.
- Source credential-pattern scan over the new Buyer Kotlin files found 0 matches. Git index remains empty, no tracked files were deleted, and generated artifacts/local.properties/OpenAPI captures remain ignored.

### Manual verification remaining

Open FarmApp -> Buyer sign in / Create account -> manually sign in. The generic multi-role welcome is intentionally retained before choosing Buyer; the Buyer forms and authenticated Buyer shell use the imported implementation. After login, verify Brian's green home header, original banner, category grid, product cards and the Home/Products/Cart/Orders/Account tabs. The read-only static /buyer-ui probe should then report Brian master 8f335e2, active=true, HOME (or another static route enum). Do not initiate checkout, STK or order creation as a visual verification step.

No real eligible existing order or live payment success was asserted during this session. A user-approved existing-order test is a separate next step. The reference app's original Account options marked Soon remain marked Soon; they were not falsely presented as backend-supported features.

## Git safety

Branch remains feature/supplier. Origin remains https://github.com/RahaFuadHabib/KilivanaApp.git. Existing uncommitted work is preserved. No staging, reset, stash, clean, commit or push. Build outputs, local.properties, raw OpenAPI capture and validation logs remain ignored. No credentials or signing keys were imported. The integration's exact source inventory is the brian directory above, its tests, copied drawable JPEGs, the navigation dependency, Buyer-only route/auth hooks, and the debug static screen probe.

Current checkout validation: 424 unit tests and final 21/21 Samsung mocked UI tests passed; lint 0 errors, 79 warnings; both APK builds and in-place installation passed. See BUYER_CHECKOUT_TEST_READINESS.md for the guarded upload/order-readback flow. GET item lines no longer block its normal path. Actual STK remains disabled: the configured tunnel returned ERR_NGROK_3200 and sandbox/production is unconfirmed.
