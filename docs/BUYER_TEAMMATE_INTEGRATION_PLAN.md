# Buyer teammate integration assessment and approval plan

Latest approval-first assessment (29 October): [Correct Buyer master comparison and integration plan](BUYER_REFERENCE_MASTER_COMPARISON_AND_INTEGRATION_PLAN.md) verifies the separate Kilivana-buyer-reference clone at master 8f335e2. This older document retains its historical revision and observations; it does not substitute for the latest plan.

Date: 2026-10-28. **Historical assessment and approval plan.** This records the pre-implementation decision, not the current implementation status. Selective integration and subsequent Buyer refinements have since been implemented; see `BUYER_TEAMMATE_INTEGRATION_RESULTS.md` and `BUYER_UI_AND_LOGIC_AUDIT.md`.

Current Buyer cards use a centered + to add one unit to the existing local cart, with stock/minimum checks and rapid-tap protection. Home has no Saved/Cart/Address book shortcuts. Cart stays in shopping navigation; addresses and historical saved IDs remain available through Account. Checkout and payment initiation remain disabled. The original decision tables and offline observations below are retained as historical context. Current backend configuration is described in `BACKEND_URL_REPLACEMENT_20261027.md`; the temporary URL below is not a shared build default.

## Recommendation

Keep FarmApp as the sole Android application. Adapt selected Buyer presentation components and shopping UX from the teammate project into FarmApp's existing Buyer role shell. Retain FarmApp's authentication/session, canonical Retrofit/OkHttp, exact-money models, marketplace isolation, order ownership validation, image delivery and tracking implementations. Reimplement cart/checkout/payment orchestration against Daniel's confirmed deployed contract rather than copying the teammate's network or authentication layer.

Do not merge Git histories, include the standalone app as a second application module, replace the root navigation, or overwrite Gradle/manifest/theme files. A selective source adaptation under a Buyer package is smaller and safer than importing the teammate app or extracting a library now. Library extraction can be considered later if there is a demonstrated shared use.

## Inspection scope and repository state

| Item | FarmApp | Teammate |
| --- | --- | --- |
| Location | `C:\Users\AppleFi\AndroidStudioProjects\FarmApp` | `C:\Users\AppleFi\AndroidStudioProjects\Kilivana-buyer` — already exists; no clone/fetch/pull performed |
| Instructions | Read root AGENTS.md; empty. No parent AGENTS.md found. | No AGENTS.md or README found in inspected tree, including hidden-file search outside generated/Git directories. Image documentation exists under docs. |
| Git state | Numerous tracked modifications and untracked feature files already present; all retained | Clean working tree at `1813880`, subject `feat: complete payments and orders`; origin matches `https://github.com/brianmuigai2-stack/Kilivana-buyer.git` |
| Freshness | Current local working tree assessed, not just HEAD | Local clone assessed; no claim it matches latest remote HEAD |
| Project type | Standalone multi-role application, one `:app` module | Standalone Buyer application, one `:app` module, Android application plugin; not a reusable library |
| Identity | Namespace `com.example.farmapp`; current debug ID `com.example.farmapp.dev2231918adc4d4b9d` | Namespace/application ID `com.example.kilivana_buyer` |
| Activity/navigation | Existing MainActivity/AppNavigation, role routing and session-generation isolation | Own MainActivity, `KilivanaApp`, root NavHost, auth routes, five tabs Home/Products/Cart/Orders/Account |
| Data architecture | Canonical DTOs, repositories, StateFlow/ViewModels, Retrofit/OkHttp, session snapshots | Model package; interfaces and concrete repositories consolidated in data/Repositories.kt; HttpURLConnection and JSONObject; four main ViewModels in data/AppViewModels.kt |

Inspected teammate Gradle/settings/version catalog/manifest, all source areas listed in this plan, model/repository/ViewModel logic, navigation, screen flows, tests and image docs. Did not read/copy signing keys, credentials, local environment files or Git authentication configuration. No backend source was used as evidence of deployed permission restrictions.

## Deployed API verification boundary

Configured FarmApp base stays `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`. Teammate's default is the **origin only**, `https://hankering-morphing-outclass.ngrok-free.dev`, with literal `/api/v1/...` concatenated in each repository. Copying those paths into FarmApp unchanged would risk duplicate prefixes.

Fresh public GETs on 2026-10-28:

- `/api-docs`: HTTP 404, `ERR_NGROK_3200`, tunnel offline.
- `/api/v1/health`: HTTP 404, same offline identifier.

Therefore live contract freshness and authenticated authorization are unavailable. No login, OTP, cart, checkout, address mutation, order or payment request was made. Existing `docs/kilivana-replacement-openapi-20261027.json` is used only as a **historical deployed schema comparison**; route presence is not proof of current role permissions or correct runtime behavior. Older local backend restrictions are not conclusions about Daniel's deployment.

## Feature-by-feature decision

| Feature | FarmApp currently | Teammate currently | Proposed decision |
| --- | --- | --- | --- |
| Buyer dashboard | Active BuyerShell has Marketplace/Orders; account toolbar opens shared profile | HomeScreen categories/product sections/seller list, cart badge, AccountScreen counts and shopping links | **Combine:** adopt layout/navigation ideas with real scoped data; remove unsupported ranking/freshness claims and inert actions. Counts come from authorized records, with loading/error handling. |
| Marketplace | Shared MarketplaceRepository/API, page size 20, uppercase `FARMER` for Buyer, `SUPPLIER` for Farmer; approval/availability and seller-type checks | Unfiltered GET products; grid cards, stock badge, quick add; categories/active as names | **Retain FarmApp data; adapt teammate grid/card presentation.** Never replace role-separated paged catalog with mixed endpoint. |
| Search/filtering | Server name/categoryId, active role categories, paging, newest-first | In-memory name/category/seller-name search, price sorting, max-price slider, stock toggle, clear filters | **Combine:** adopt controls only with defined paging semantics. Preserve server category IDs; do not imply client sorting/filtering of one page covers the catalog. No unsupported server price parameters. |
| Product detail | GET products/{id}, validates ID/seller/status/stock, gallery, exact price/available quantity/minimum | Detail based on already loaded list, seller card/harvest/stock, favourite/Add/Buy controls; no detail refresh API | **Retain FarmApp fetch and checks; adapt detail layout/saved-item control.** Revalidate availability on purchase; never substitute bundled produce imagery. |
| Saved items | No dedicated active Buyer feature | SavedProductIds in ProductsViewModel memory plus SavedItemsScreen | **Adopt UI and add account/backend-scoped persistence if approved.** No server favourites endpoint assumed; handle removed/unavailable products honestly. |
| Cart | No enabled canonical Buyer cart | Persistent local guest cart IDs/quantities, editable rows and totals; synchronization starts during checkout | **Adapt UX, replace state/orchestration.** Exact-money prices, quantity/minimum/stock checks, principal-scoped cart, stable revisions, backend reconciliation and expiry. Guest browsing/cart is a separate product decision; no duplicate auth flow. |
| Checkout | Not enabled pending confirmed permissions | Address selection, server delivery quote summary, order creation with quoteId, M-Pesa or Cash on delivery | **Adopt presentation conditionally; rebuild integration.** Gate on live quote/order contract, idempotency and allowed methods. Do not assume COD is supported because a radio button exists. |
| M-Pesa | Read-only status mapper; existing order payment reads and status refresh; no Buyer STK action | Buyer STK request after order creation and manual status refresh screen | **Retain FarmApp canonical statuses/session transport; adapt confirmation/status UX.** Add initiation only after durable idempotency and authorization confirmation. No PIN screen/provider secrets or initiation=paid logic. |
| Orders/history | BuyerOrdersModel/API validates buyerId, states and item identities, detail/timeline/payments; manual refresh and lifecycle polling; receipt confirmation | Order filters/cards, timeline and reorder; item reconstruction depends on current catalog, reduced status enum | **Retain FarmApp API/validation; adapt list/filter/detail styling.** Add reorder as a new validated cart draft, not reuse historical totals or auto-place. Preserve snapshots when products disappear. |
| Delivery tracking | Order/job/driver linkage validation, delivery events/location, Mapbox display and lifecycle refresh | Timeline/status only; “Track order” navigates to orders, no location/map transport | **Retain FarmApp.** Teammate timeline styling may be adapted without weakening job/driver ownership checks. |
| Buyer profile | Shared GET auth/me / PUT users/me profile editor and session-aware account state | Account summary and links, self-service address CRUD; no full profile edit | **Combine:** retain shared editor, adapt account hub/address-book UI. Verify address ownership and geocoding/coordinate requirements. Disabled “Soon” payment/notification/support entries are not implemented features. |
| Navigation/auth | One multi-role session-driven root; encrypted session store, refresh coordination, origin checks, ownership OTP UI and privacy-safe diagnostics | Separate root auth/navigation, SharedPreferences tokens, Buyer-only auth repository and refresh | **Retain FarmApp exclusively.** Buyer-scoped destinations only inside BuyerShell. Exclude teammate Activity/auth repositories/ViewModel/screens and root graph. |
| Backend integration | Configured base ends /api/v1/, Retrofit relative routes, shared canonical envelopes and request/session guards | Root URL plus full API paths, repeated manual HTTP code, permissive JSONObject defaults | **Retain FarmApp transport; implement exact typed adapters for approved missing routes.** No raw token parameters or competing HTTP/session systems. |

### Dashboard and UI qualifications

HomeScreen's “Recommended for you”, “Fresh this week” and “Top sellers” are slices/distinct values from the fetched list, not a recommendation, freshness or ranking service. Rename to neutral catalog headings unless server semantics support those labels. Notification icon is currently a no-op and delivery-location header is not a working selector. Product loading/error StateFlows exist but MainActivity does not pass them to Home/Products, so initial failure can look like an empty catalog. Retain FarmApp's explicit loading/error/retry states throughout adapted layouts.

ProductsScreen initializes priceLimit with `remember` from possibly empty products (default 1) and does not automatically reset it when asynchronous data arrives; this can hide nearly all products. Reconcile filter ranges with new data without losing explicit user choices. Retain/adapt controls only after tests cover initial load, filters, paging and failed retries.

## Source defects and conflicts to resolve before reuse

1. **Catalog separation:** ApiProductRepository.getProducts calls GET `/api/v1/products` without sellerType; its Product model discards sellerId/sellerType and product status. getCategories uses `/categories/active` and keeps only names. FarmApp must keep canonical ownership/type/category identities and Buyer=FARMER / Farmer=SUPPLIER checks. UI hiding alone is insufficient.
2. **Money and stock:** teammate Product.pricePerUnit and Order totals are Int; JSON prices/totals use `optDouble(...).toInt()`, losing cents. DeliveryQuote and payment amount use Double. Replace these with FarmApp BigDecimal projections, exact ID handling and real available quantities. StockStatus alone cannot enforce quantity caps; default add=1 can violate minimumOrder, and increment does not cap stock.
3. **Historical order loss:** ApiOrderRepository.toOrder joins historical items to the current products list, dropping missing products and rendering present-day product price/name rather than the order snapshot. Product deletion/unlisting/price changes must not alter purchase history. Use OrderItemResponse productName/unit/quantity/unitPrice/subtotal directly.
4. **Order states:** substring mapping defaults unknown statuses to Pending; completed/disputed are not represented by the five-value model. Keep FarmApp exact wire enums and reject unsupported values without inventing a transition or success.
5. **Payment interpretation:** teammate does request backend status and does not have a fake PIN/paid timer. However it uses COMPLETED/SUCCESS as stop-check values rather than canonical pending/paid/held/settled/failed/refunded, and response models lack order association for validating a status reply. Retain backend order/payment binding, distinguish escrow, show unknown safely, and use bounded lifecycle refresh.
6. **Submission recovery:** checkoutLocked/checkoutInProgress are in-memory flags, not persistent idempotency. submitCheckout has no early single-flight guard and creates order then STK. Repeated calls or uncertain success can duplicate orders/prompts. Adding cart lines sequentially before checkout can partially succeed; retry can duplicate quantities. Once checkoutLocked is true, further cart edits do not reset it or reliably invalidate the quote. Bind quotes to server cart revision/address/expiry; reconcile after unknown outcomes before resubmission. Server idempotency is required.
7. **Session/privacy:** teammate AuthRepository stores access and refresh tokens in ordinary SharedPreferences, saves token data before profile role validation, and derives logged-in state from token/ID presence. Cart preference name is global guest cart; order/selected-order/address/saved-item states are not cleared or partitioned by session generation. Do not import these stores or any existing private data. Use FarmApp's session flow, encrypted persistence and account/origin boundaries.
8. **OTP/auth:** teammate supports registration email verification but does not route login's verification-required contract to that screen. It discards verification expiry in the navigation reconstruction. Preserve FarmApp OTP handling and never implement mandatory login OTP client-only. Broad exception catches in teammate repositories/ViewModels may swallow coroutine cancellation; rethrow CancellationException in adapted code.
9. **HTTP/error behavior:** repeated HttpURLConnection code does not apply FarmApp session tags, coordinated refresh, redirect/origin restrictions or privacy-safe typed failures. JSONObject opt defaults allow missing success/amount/identity fields to look valid; arbitrary backend messages can reach UI. Preserve typed envelope validation and fixed safe error mapping, including gateway 403 handling.
10. **Images:** teammate Coil AsyncImage enables disk and memory caching, has no account-scoped protected-image transport or backend allowlist equivalent, and image URL fallback can fan out one extra request per product. Keep FarmApp ProductImage/ApiClient image pipeline. The bundled photos are banners/illustrations, not evidence of each real listing's image. Missing actual photos must show unavailable/retry, not a different produce photo.
11. **Navigation/lifecycle:** root MainActivity owns all ViewModels across routes; signout does not reset all private screen states. Add namespaced Buyer destinations under session-generation scope, no nested duplicate login, shell toolbar duplication or second root theme. Preserve shared profile/role destinations and current unsupported Admin behavior.
12. **Placeholder completeness:** account Payment methods, Notifications, Help and About are disabled “Soon”; no live delivery map, no server saved-item repository, and no complete profile editor. These should not be advertised as features adopted from the teammate module.

## API compatibility matrix

These comparisons are with the **2026-10-27 captured deployed OpenAPI only**. Today's server is offline. Full paths below must become relative paths in FarmApp's `/api/v1/` Retrofit base.

| Teammate call | Historical deployed contract comparison | Required adaptation / live confirmation |
| --- | --- | --- |
| GET /products | Advertised List ProductResponse; mixed seller catalog in earlier public reads | Use existing products/search?sellerType=FARMER with page/size/categoryId/name; preserve server pagination |
| GET /categories/active | Advertised active categories | Use categories/type/FARMER and preserve active/type/id; avoid mixing input categories |
| GET /products/{id}/images | Advertised image records | Prefer existing detail/gallery response and protected image loader; keep same-origin binding |
| GET /orders/buyer/{buyerId}, /orders/{id}, /orders/{id}/timeline | Advertised; structures broadly align with canonical BuyerOrdersApi | Keep FarmApp buyer/order/item validation, BigDecimal and snapshot details; do not weaken permission checks |
| POST /cart/items {productId,quantity} | Advertised, numeric IDs and positive integer quantity | Confirm authenticated cart semantics, add-vs-replace behavior, owner binding, read/update/delete routes and replay rules |
| POST /checkout | Advertised success envelope with void data | Confirm what it does, reservation/cart state and retry/recovery. Void acknowledgement alone is not an order or receipt |
| POST /checkout/delivery-quote {addressId} | Advertised DeliveryQuoteResponse | Exact amounts/currency/address and expiry; confirm quote-to-cart revision, delivery price authority and requote rules |
| POST /checkout/order {quoteId,phoneNumber?} | Advertised ApiResponseLong numeric order ID | Use exact numeric result rather than accepting many guessed response shapes. Confirm whether this also initiates payment before sending a second STK call. Require idempotency/recovery semantics. |
| GET/POST /users/addresses; PUT/DELETE /users/addresses/{addressId} | Advertised; teammate's concrete ID path matches regardless of local placeholder variable spelling | Bind owner to current principal, validate returned ID/user and finite coordinate ranges; no arbitrary userId trust; server must enforce ownership |
| POST /payments/stk-push/buyer {orderId,phoneNumber?} | Advertised buyer-initiated payment; no client amount | Confirm exact permissions, account/order binding, pending attempt reuse, rate limits, idempotency and provider reconciliation |
| GET /payments/{id}/status | Advertised PaymentResponse | Preserve returned paymentId/orderId/amount/status association, exact states, no local success inference |
| Auth login/register/me/refresh/logout and email-verification | Existing canonical endpoints advertised | Do not import a second authentication implementation; use FarmApp's existing services/UI/session behavior |

Endpoint presence and matching schemas do not establish that checkout or STK is currently authorized, idempotent, or operational. No older local backend restriction is used to deny these features. Re-fetch live OpenAPI on restoration and compare required fields, enums, HTTP codes, security declarations and documented role permissions; authenticated runtime tests need approved synthetic credentials/environment and explicit approval before any real order/payment mutation.

## Dependencies and build configuration

| Setting | FarmApp | Teammate | Plan |
| --- | --- | --- | --- |
| AGP | 8.11.2 | 9.4.1 | Retain FarmApp; do not migrate toolchain for screen adoption |
| Gradle wrapper | 8.13 | 9.6.0 | Retain FarmApp wrapper |
| Kotlin Compose plugin | 2.0.21 | 2.2.10 | Retain FarmApp Kotlin/compiler setup; adapt source API usage as needed |
| SDK | compile/target 36, min 26 | compile/target 37, min 24 | Keep FarmApp SDK and manifest; no teammate SDK requirement proven for reusable UI |
| Compose BOM | 2024.09.00 | 2026.02.01 | Compile adapted components against existing BOM; selectively adjust APIs, not blanket upgrade |
| Core KTX | 1.10.1 | 1.19.1 | Retain current dependency unless an actual required API is identified |
| Navigation | Existing role root and Compose state shells | navigation-compose 2.8.6 | Optional Buyer-scoped navigation dependency only if approved; otherwise state destinations, no competing root NavHost |
| Images | Existing authenticated/session-scoped ProductImage | Coil Compose 2.7.0 | Do not introduce Coil just to duplicate a working image path |
| Lifecycle | Existing 2.8.4 explicit ViewModel/Compose dependencies plus catalog entries | Runtime 2.6.1; ViewModel alias issue | Resolve dependencies only in FarmApp; teammate `version = "lifecycleRuntimeKtx"` is a literal catalog version, not `version.ref`, and requires correction if ever building that app separately |
| Java bytecode | 11 | 11 | Compatible target; no reason to copy Gradle |

Teammate AGP uses built-in Kotlin-style configuration and release optimization DSL different from FarmApp's setup. Do not copy plugins, wrapper, release optimizer, manifest backup configuration, launch activity, application ID, icons or local settings. No teammate build was run and version resolution/SDK availability has not been verified; versions above are file declarations, not compatibility certification.

Teammate theme ignores darkTheme and always supplies a light palette. Port component styling into FarmApp's theme/tokens with working dark mode and accessibility; no second global MaterialTheme. Prefix or selectively rename copied drawables/strings/components to avoid resource and class-name collisions (`Product`, `Order`, `AuthViewModel`, `ProductRepository`, `ProductImage`, `OrderStatus`, `PaymentStatus`).

## Reuse/adapt/exclude map and likely changes

**Adapt selectively:** ui/screens/HomeScreen.kt, ProductsScreen.kt, SearchScreen.kt, ProductDetailScreen.kt, CartScreen.kt, CheckoutScreen.kt, AddressBookScreen.kt, SavedItemsScreen.kt, AccountScreen.kt, OrdersScreen.kt and OrderDetailScreen.kt. Extract reusable card/header/filter/summary layout from SharedComponents.kt and confirmation/status presentation from OrderConfirmationScreen.kt. Change parameters to FarmApp state/callbacks and canonical view projections. Preserve only factual text/actions.

**Exclude as implementation sources:** teammate MainActivity/KilivanaApp root graph, AuthScreens.kt/AuthenticationRequiredScreen.kt auth ownership, ApiAuthRepository/AuthViewModel, repeated HTTP request helpers, token/cart preferences, numeric/status mappers, Gradle/manifest/toolchain and blanket theme replacement. Their UX can inform small components, but their infrastructure must not run beside FarmApp's.

Expected FarmApp files after approval:

- `app/src/main/java/com/example/farmapp/marketplace/BuyerShell.kt`: Buyer-only destination host, approved tabs and shared navigation callbacks.
- `marketplace/Marketplace.kt`: retain shared API/repository and seller checks; extract/adapt Buyer presentation without changing Farmer Supplies behavior.
- New `buyer/ui/` components: approved dashboard/filter/detail/cart/saved/account/address/review screens; avoid giant copied root file.
- New Buyer cart/address/quote/purchase DTOs, repositories and ViewModels under `buyer/` or the established `orders/` package, with account/origin-scoped persistence and typed requests only for confirmed endpoints.
- `orders/BuyerOrders.kt`: retain service and ownership checks; improve presentation, status filters and optional validated reorder callbacks without dropping history lines.
- `payments/`: confirmed purchaser initiation/intent recovery and backend status UI; preserve existing VerifiedPaymentStatus semantics.
- `data/remote/ApiClient.kt`: add typed confirmed Buyer API services using its shared authenticated client.
- `ui/navigation/AppNavigation.kt`: minimal Buyer-shell wiring; keep role destination/session-generation boundary. No Farmer/Supplier/Driver/Inspector routes changed.
- `profile/ProfileEditor.kt`: reuse existing shared profile navigation; separate address book only if approved.
- Reuse `tracking/DeliveryTrackingScreen.kt`, `network/TokenManager.kt`, `AuthTokenInterceptor.kt`, `EncryptedSessionPersistence.kt`, AuthRepository/AuthViewModel and `ui/screens/ProductImage.kt`; no duplicate replacements planned.
- Resource files only for selected presentation assets/strings with collision review; dependency catalog only for a demonstrated missing dependency. No manifest/application-ID change planned.

All abbreviated source paths above share `app/src/main/java/com/example/farmapp/`. Existing dormant `ui/screens/BuyerScreen.kt` in FarmApp already contains mock-based UI; do not reconnect it or create a third live Buyer path. The current live path is BuyerShell -> shared MarketplaceScreen / BuyerOrdersScreen.

## Assets and provenance

Teammate docs/IMAGE_CREDITS.md records Wikimedia Commons CC0/public-domain sources and docs/IMAGES.md distinguishes product and banner resources. Credit statements were inspected, not independently revalidated. If assets are selected, preserve their source/credit records and verify the relevant license; use decorative banners only as decoration, never as a real listing image. No repository-wide LICENSE was found; confirm teammate/team permission for selective code reuse and retain attribution/provenance in the eventual change. No secrets, signing material, environment files or private preference data will be copied.

## Proposed staged integration (approval required)

1. **Presentation-first Buyer shell:** dashboard/catalog/detail/account/saved-item UI with current live repositories, existing profile/tracking, correct filters and explicit loading/error/empty states. Retain current working order/payment reads and Farmer Supplies untouched.
2. **Cart and address state:** canonical exact-money models, quantity/stock validation, session/origin isolation, address ownership and optional guest-cart policy; add mocked APIs. No checkout/payment mutation until step 3 contracts are confirmed.
3. **Confirmed checkout:** fetch current deployed schema, document cart/quote/order lifecycle, enforce quote expiry/revision and durable order idempotency/reconciliation. Review server totals and permitted delivery/payment methods. Confirmation screen only after authoritative order creation.
4. **Confirmed M-Pesa:** explicit confirmation, server-calculated amount, verified owned order/intent, duplicate-safe initiation, authoritative status refresh/recovery. No fake PIN, callback simulation or client-side paid state.
5. **Regression/review:** full mocked/Compose validation, lint and debug build, verify original application ID/base URL. In-place install only when subsequently authorized; do not uninstall or clear app data. Keep changes reviewable without committing/pushing.

## Questions genuinely requiring confirmation

1. **Approval/scope:** approve selective adaptation into FarmApp, starting with presentation-only improvements, rather than importing the standalone app. Confirm guest cart before sign-in versus authenticated Buyer-only cart; preserve current root login by default.
2. **Daniel:** restore the deployed endpoint or provide a current authoritative OpenAPI export; confirm Buyer cart/checkout/address and payment permissions, exact server workflow and quote/order semantics. Does checkout/order also request payment, or is separate buyer STK mandatory? What are durable idempotency/reconciliation and quote expiry/revision rules? Is Cash on delivery supported? Which contact/address fields are visible to Buyer/seller?
3. **Backend query semantics:** are whole-catalog price filtering/sorting and seller-name search supported, and through which documented parameters? Otherwise limit/label local loaded-result controls or defer them rather than invent routes.
4. **Optional features:** are favourites intended to be local account-scoped bookmarks or server-synchronized? Are recommendation/freshness/ranking and notification feeds real supported features? Omit unsupported claims/actions by default.

No older local repository policy should be presented to Daniel as a deployed restriction. Document actual live permission failures only after approved verification.

## Testing strategy and acceptance criteria

The teammate repository currently contains only template addition/context-package tests, not checkout/payment/security coverage. FarmApp's latest recorded validation was 323 unit tests and lint/build success on 2026-10-27; that historical result is not a test run for this plan. No code was built, installed or executed during this assessment.

After approval:

- Retain existing canonical auth/OTP/session, role navigation, marketplace, product image/profile, order ownership/payment and delivery linkage suites.
- Add mocked Retrofit tests verifying one `/api/v1/` prefix, correct shared origin, Buyer FARMER / Farmer SUPPLIER filters, category identities, pagination, no unfiltered fallback, API envelope/ID/money precision and missing/unknown response rejection.
- Test cart quantities against minimum/available stock, duplicate adds, product changes/unlisting, saved-item removal, account/backend switch, logout/process recreation, cancellation and stale-response isolation; no private cart/orders/images from another account.
- Test historical order snapshots without any current product match, price changes/deletion, all canonical order/payment/delivery states, wrong buyer/order/payment IDs and unexpected statuses. Reorder must revalidate current availability/prices and never automatically place an order.
- Test quote expiry/cart revision/address change, server total changes, partial cart writes, 401/403/409/429/5xx, lost create responses and resume/reconcile, duplicate taps and process death. Backend idempotency must be tested with provider stubs; client flags alone are insufficient.
- Test pending, paid, held, settled, failed and refunded payment responses separately. Initiation acceptance/responseCode=0 cannot mark paid. Status association and failed/unknown outcomes must remain authoritative; never call real Daraja during tests.
- Compose tests: dashboard empty/loading/error/retry, filter slider after asynchronous load, details image errors, quantity validation, address validation, cart/quote review, confirmation only on backend success, back navigation/receipt/tracking, dark mode, font scale, accessibility labels and no inert/fabricated success controls.
- Regression smoke for Farmer, Supplier, Buyer, Driver, Inspector and existing Admin unsupported state; preserve session and image protections. Run unit tests, lint and debug build after changes. Check BuildConfig/manifest identity and APK before any approved `adb install -r`.

## Approval checkpoint

Only this assessment file is added by this task. Existing application files, teammate clone, app data, Git histories and backend code remain unchanged. No live credentials/orders/payments or mutation probes were used. **Stop here for user approval before adapting any application code.**
