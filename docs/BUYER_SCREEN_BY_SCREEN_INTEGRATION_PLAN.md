# Buyer screen-by-screen integration plan and visual comparison

> Historical report: the approved production-source Buyer integration now supersedes the UI status described below. See [BRIAN_BUYER_INTEGRATION.md](BRIAN_BUYER_INTEGRATION.md) for the actual copied screens, safety adapters, final validation and live-checkout limitations.


29 October 2026. **Comparison and proposed UI plan only. No production UI was replaced in this assessment.** Correct source: C:/Users/AppleFi/AndroidStudioProjects/Kilivana-buyer-reference, master 8f335e23f0d29c2953fe6373b7ff6f2da9e371e6. FarmApp remains the multi-role application on feature/supplier.

## Why the installed app still looks different

The user is correct: Phase 1 integrated compatible authentication behavior, not the reference Buyer presentation. FarmApp still renders marketplace/BuyerShell.kt and buyer/BuyerScreens.kt for Home, Shop, product details, Cart, Saved Items, Account and address listing. The existing file comment explicitly records **selective** adaptation of older reference shopping layout; these are not wholesale imports of the reference screens.

Login/signup/OTP remain FarmApp's shared auth composables. Orders/details/M-Pesa use FarmApp implementations recently styled against design-reference/Order Kilivana.png. That composite design is **not the current reference repository's actual Orders UI**. The reference master has a much simpler Orders/detail/status presentation. Mixing those two targets explains some earlier confusion.

There is also outer chrome to address: AppNavigation's generic Kilivana/theme/profile/logout TopAppBar wraps the Buyer role. Reference MainActivity owns Buyer-only screen headers instead. Replacing inner Home alone would leave the old toolbar and could create two headers.

## Visual evidence and limitations

**14 paired screen captures (28 images)** were rendered on Samsung RF8WC0K043H from synthetic fixtures. [Local screenshot comparison gallery](../app/build/buyer-ui-comparison/index.html). Each pair labels current FarmApp on the left and reference Buyer on the right.

Pairs: Home, Products, product detail, Cart, Orders, order detail, Account, Saved Items, login, signup, OTP, checkout, payment and addresses. Key comparisons: [Home](../app/build/buyer-ui-comparison/pairs/home.png), [Products](../app/build/buyer-ui-comparison/pairs/products.png), [Cart](../app/build/buyer-ui-comparison/pairs/cart.png), [login](../app/build/buyer-ui-comparison/pairs/login.png), [Orders](../app/build/buyer-ui-comparison/pairs/orders.png), [checkout](../app/build/buyer-ui-comparison/pairs/checkout.png).

These are **source-component previews, not photographs of live authenticated apps**. Reference presentation/theme/model sources were recompiled in an isolated temporary Android-test source set using FarmApp's toolchain. Network repositories, actual auth sessions, account stores and write callbacks were not included. Reference CartViewModel was replaced with inert render-only callbacks; all products/addresses/orders/payment results are explicitly synthetic. Product image URLs are null and the reference image loader is stubbed to its neutral empty surface; its packaged Home banner is the original reference asset loaded from test resources. No live product photos or transactions are represented. Native OS chrome and the debug auth recorder row are excluded; the current FarmApp generic Buyer toolbar and both root-tab bars are reproduced from source with inert callbacks. Material component defaults can differ from the reference's newer standalone toolchain.

All generated source copies, init script, images and gallery are under **ignored app/build/buyer-ui-comparison**. They are not imported into production or proposed Git contents. Original source and screenshot files remain intact. The normal test APK is restored after capture. No reference app was installed and no real account screen/credentials were captured.

## Observed visual differences

- **Home:** FarmApp has a mint welcome panel, chip categories and large full-width product cards. Reference has a #2E7D32 branded header with cart badge, compact search/filter row, photographic hero, 60dp icon category tiles, recommendation sections and two-column product cards. Its background is #F5F7F5 rather than FarmApp's warm surfaces. Reference Shop products uses a tonal button; some default chip/tab accents are lavender in the recompiled toolkit.
- **Products:** FarmApp has separate Cart/Saved links, explicit Search/Clear buttons, loaded-result sort chips and one large product card per row. Reference has a back/title row, single search field/filter control, category row, product count/sort menu and a two-column grid. This is a substantive screen-layout replacement, not just recoloring.
- **Product detail:** FarmApp reuses its marketplace card above a quantity field. Reference uses a large image header with overlaid back/heart, stock badge, product/price/minimum, seller card and fixed Add to cart/Buy now actions. Reference hides tabs here; FarmApp currently keeps them.
- **Cart:** Reference uses larger rounded cards with category/image/seller metadata, separated quantity/line-total controls, a compact fixed summary and Proceed to checkout. FarmApp has compact image/name rows, right-side quantity/trash controls, a separate estimate card and Checkout. Existing KSh 30 estimate behavior must not silently become payable when adopting the new layout.
- **Account:** FarmApp is mostly four outlined navigation buttons. Reference has avatar/name/contact summary, order statistics and grouped chevron rows, plus logout. Several reference rows are marked Soon and are not implemented features.
- **Auth:** FarmApp login has a large illustrated lock, rounded filled fields and shared role-aware onboarding; reference is a compact Kilivana heading with a white form card and outlined fields. Reference signup is Buyer-only with no role picker and no confirm-password field. FarmApp's signup serves multiple roles and keeps stronger validation. OTP functionality is now compatible but its form styling remains FarmApp's.
- **Orders:** FarmApp follows the user's separate four-screen green app-bar/card design and has verified eligible-order payment actions. Reference uses All/Active/Delivered/Cancelled tabs, simpler cards with order codes/raw dates and View order; its detail screen is plain text plus Reorder. These must not be treated as the same design.
- **Checkout/payment:** FarmApp offers Pickup/Door selection and guarded review, then a separate explicit M-Pesa review screen for an existing order. Reference puts phone/address/quote/payment-method controls on Checkout and immediately creates an order plus STK on its combined action. Its separate payment screen is post-initiation status only. The paired payment images show different stages, not equivalent interchangeable screens.
- **Addresses:** FarmApp account address view is a verified list with add in delivery selection; reference has Add/Edit/Delete controls. The visual shell can be adapted, but edits/deletion need verified owned-address APIs and explicit customer actions. Pickup Stations are not implemented by this reference and must remain a FarmApp extension.

## Screen provenance and proposed action

| Screen/component | Currently shipped in FarmApp | Reference source/target | Proposed safe action |
| --- | --- | --- | --- |
| App entry/welcome | Shared FarmApp AppNavigation/LoginScreen/AuthWelcome | Reference starts public Home; splash route exists but is not its start destination | Preserve multi-role entry. Do not turn the whole app into a Buyer guest app |
| Buyer login | FarmApp SignInScreen, Phase 1 auth adaptation | AuthScreens.kt AuthScreen(signup=false) | Adapt compact form presentation only for explicit Buyer entry; same AuthViewModel/repository/Keystore |
| Buyer signup | FarmApp SignUpScreen with role selection | AuthScreen(signup=true), fixed BUYER payload | Adapt card/fields; fix Buyer UI hint to BUYER only when explicitly chosen; retain validation, submit latch, confirmation and HTTP mapping |
| Email OTP | FarmApp EmailVerificationScreen/controller | AuthScreens.kt EmailVerificationScreen | Adapt card/title/outlined input; keep issued-challenge reuse, cooldown, one verification and validated login continuation |
| Buyer theme | Shared FarmApp palette plus Orders-specific theme | ui/theme/Color.kt, Type.kt, Theme.kt | Add a Buyer-scoped theme using reference palette/type; never replace global Farmer/Supplier/Inspector themes |
| Buyer shell/tabs | marketplace/BuyerShell; Home/Shop/Cart/Orders/Account; state-based selection | MainActivity NavHost; Home/Products/Cart/Orders/Account | Adapt labels, badges, header ownership and back behavior inside Buyer only; preserve one cart/model |
| Home | BuyerShoppingContent HOME, selectively adapted earlier | HomeScreen.kt, SharedComponents ProductGrid/SectionHeader | Replace the presentation with reference header/search/hero/categories/sections bound to actual catalog and name/location state |
| Marketplace | BuyerShoppingContent SHOP, BuyerProductCard | ProductsScreen.kt, SharedComponents ProductTile/ProductGrid | Replace with reference grid/filter/sort structure; adapt to BigDecimal, FARMER API paging and actual category IDs |
| Search | Embedded query/filter in Shop | SearchScreen.kt plus ProductsScreen search | Add/adapt a Buyer-only search presentation; one query/filter source, debounce/cancel stale reads, preserve paging and seller filter |
| Product detail | BuyerShoppingContent selectedId branch | ProductDetailScreen.kt | Replace presentation, retain verified product IDs/stock/minimum, precise quantity, Saved persistence and existing cart callbacks |
| Saved Items | BuyerShoppingContent SAVED; persisted account/origin IDs | SavedItemsScreen.kt | Adapt grid/header; retain persistence and unavailable-item recovery; do not replace with reference's memory-only IDs |
| Cart | BuyerCartLine/summary in BuyerScreens | CartScreen.kt | Adapt cards/summary/sticky CTA; retain stock/minimum/rapid-tap guards, exact totals, right-side controls/trash and local persistence |
| Orders list | BuyerOrderDesign.kt/BuyerOrders.kt; separate composite design | OrdersScreen.kt | Adapt reference list chrome/filter style if approved; retain readable dates, precise totals, owned history and eligible Pay action |
| Order details/paid details | FarmApp rich sections and confirmed-payment panels | OrderDetailScreen.kt, currently plain text/Reorder only | Recommended: retain richer current detail/payment sections and retheme consistently, rather than remove working payment access/history |
| Account | BuyerShoppingContent ACCOUNT | AccountScreen.kt | Replace landing presentation; use real profile fields and owned-order counts; retain profile edit/logout and only real destinations |
| Profile editor | FarmApp AccountProfilePage | No equivalent standalone profile editor in reference | Preserve existing editor and link it from the new Account layout |
| Address book | FarmApp verified address list/create/select | AddressBookScreen.kt | Adapt list/editor styling; preserve owner/selection validation. Add verified edit/delete only in separately approved scope, no live automated mutation |
| Pickup/Door selection | FarmApp BuyerDeliveryFlow + BuyerPickup screens | Reference supports address-only checkout, no pickup station UI | Preserve both methods and real station management; apply reference section/card styling without removing Pickup |
| Quote/review/new order | FarmApp review deliberately guarded | CheckoutScreen.kt | Adapt the visual summary/phone/review layout; keep order action disabled until server-cart equality, valid quote and order recovery are confirmed |
| M-Pesa review/confirm | FarmApp BuyerPaymentPage/Controller | Reference phone on Checkout; no separate pre-STK confirmation page | Preserve current explicit review/confirm, normalized phone, pre-send marker and backend total; harmonize visual theme only |
| Payment status | FarmApp lifecycle-aware polling/recovery | OrderConfirmationScreen.kt PaymentStatusScreen | Adapt status illustration/layout if useful; retain correct status semantics, lifecycle polling, failure/unknown/cancelled handling and durable recovery |
| Confirmation/tracking | FarmApp real order/payment confirmation and DeliveryTrackingScreen | OrderConfirmationScreen; timeline in OrderDetailScreen | Only show actual created/paid states. Preserve tracking; no success UI based on quote, checkout 200 or STK initiation 200 |

**No currently shipped screen is a direct drop-in of the correct master's MainActivity/CheckoutScreen/CartScreen/AuthScreen.** Shopping contains selectively adapted earlier layout ideas; Phase 1 imported compatible behavior, not appearance; Orders was rebuilt against a different supplied image. Temporary preview copies in app/build are test artifacts, not shipped screens.

## Navigation comparison and proposed Buyer-only behavior

| Behavior | Current FarmApp | Reference | Proposed target |
| --- | --- | --- | --- |
| Entry before authentication | Shared multi-role auth entry | Public Home/catalog, auth gate at protected features | Preserve current auth boundary; optional guest Buyer browsing requires separate approval |
| Root tabs | Shop label; count appended to Cart text | Products label; numeric badge on cart icon | Match reference tabs/badge, using actual existing cart quantity |
| Header ownership | Generic AppNavigation toolbar plus inner Buyer headings | Buyer screen owns header | Buyer-only removal/replacement of generic toolbar; move profile/logout into working Account actions |
| Product/detail/Saved/address screens | Destination enum/selected ID; tabs generally remain | Separate routes; tabs hidden on nested screens | Typed Buyer-local back stack; hide tabs on nested screens; restore root selection/filter/scroll state |
| Checkout back | Delivery-step logic returns Cart | Checkout route back stack | Keep cart/location state and explicit review; reference visual navigation only, no automatic writes |
| Existing order payment | Orders ? details ? M-Pesa review ? explicit dialog | No existing-order Pay action; new checkout auto-initiates | Preserve FarmApp's working existing-order path and all guards |
| Auth role | Server-issued session role routes app | Buyer-only /auth/me check | UI Buyer hint never authorizes a role; root routing remains server-session driven |

FarmApp currently has **no declared navigation-compose dependency**; its AppNavigation/BuyerShell are state-driven. Do not blindly copy reference NavHost and add dependencies just to match route behavior. A small typed Buyer navigation/back-stack state can reproduce the requested behavior with existing dependencies; using navigation-compose instead would be a separate justified decision. Never put emails, passwords, OTPs or tokens into route strings or saved navigation state.

The minimal root edit is conditional on authenticated BUYER only: let BuyerShell own its toolbar/theme, pass existing logout/profile callbacks safely, and leave every non-Buyer branch and session-generation isolation intact. Moving/removing the generic toolbar must not strand logout/profile functionality or change other roles' chrome.

## Phased implementation proposal, in priority order

### A. Authentication functionality and then Buyer form presentation

Phase 1 code has 394 passing unit tests and 21 passing Samsung synthetic auth tests, and the correct configured APK is installed. A real user-initiated login/signup diagnostic result has not yet been supplied, so live success is not claimed. Complete that one manual test first; do not repeatedly register or bypass 429.

Then adapt reference login/signup/OTP form cards through the existing controllers. Before sign-in the app cannot know a trusted account role: a Buyer visual variant must be selected through an explicit Buyer entry/signup choice, not by replacing all shared sign-in UI or trusting a client role hint. Keep generic/Farmer/Supplier entry available. Keep useful validation/confirmation/error feedback even where reference has fewer fields. No token-store or password-persistence replacement.

Likely files: a small Buyer auth-presentation variant and existing SignInScreen/SignUpScreen/EmailVerificationScreen presentation slots, plus LoginScreen wiring only. Auth services/session code stays as implemented unless the manual trace proves a specific bug.

### B. Buyer theme, shell, Home and marketplace ? highest visible impact

Add Buyer-only palette/type/spacing matching the reference (#2E7D32 primary, #F5F7F5 background, white rounded surfaces). Adapt HomeScreen, ProductsScreen, ProductDetailScreen, SearchScreen, SavedItemsScreen and SharedComponents into focused presentation files under buyer/ui. Bind them to existing BuyerShoppingState/BuyerActions and canonical products; do not copy Repositories.kt or its Int product model.

Handle the root Buyer toolbar and tab/back behavior together to avoid duplicate headers. Products becomes a two-column lazy grid with actual category IDs, All reset, search, refresh, pagination and state preservation. Keep sellerType=FARMER and Farmer supplies sellerType=SUPPLIER unchanged. Do not implement a whole-catalog price/stock filter by silently filtering only the first loaded page. Where backend query support is absent, label loaded-result sorting/filtering accurately or defer that filter.

Keep actual image URLs and neutral errors; generic banner use needs credits review and must not imply a product photo, discount or free delivery. Header location becomes an actual location action; reference notification no-op is omitted/disabled rather than turned into fake functionality. Retain the previously requested image-corner add/heart actions or explicitly approve the reference's below-image cart-button placement; either must use the existing stock/debounce callbacks.

### C. Cart, Account, addresses and Orders

Adapt CartScreen's image/title/seller/line-total hierarchy and fixed summary, keeping precise values and safe quantity/removal behavior. KSh 30 remains a marked estimate only unless backend quote supports it; unselected delivery remains pending. Preserve cart across all route/rotation/restart transitions.

Adapt Account identity/statistics/grouped rows using real account/history data. Retain profile edit and logout. Omit fake Soon destinations unless clearly noninteractive. Adapt saved/address presentation without deleting or migrating existing IDs or sending write requests in tests.

For Orders, adopt the reference's surrounding palette/spacing/navigation but **retain** the richer existing details/paid confirmation and eligible existing-order Pay action. Reference's raw ISO dates, current-catalog-derived historical items, integer prices and Reorder-only detail would regress the working feature. This intentional hybrid is recommended; literal replacement of Orders/detail requires explicit approval knowing those differences.

### D. Checkout/M-Pesa visual compatibility, guarded functionality later

Reference checkout sections can be adapted visually around existing Pickup/Door selection, actual addresses/stations and quote review. Do not remove Pickup support. Do not copy the address-only quote DTO or the Calculate delivery callback that uploads every cart item and starts checkout.

Keep blocked new-order submission until authoritative owned-cart lines/quantities match the intended purchase, backend quote is valid/unexpired, and duplicate-order timeout recovery is defined. Any eventual order creation needs explicit review. Keep STK as a separately confirmed step after persisting a real order ID, using current authenticated Retrofit, phone validation, durable attempt marker and payment-status polling. No automatic live writes are authorized by approving UI work.

## Acceptance checks after each approved UI batch

- Compare screenshots against the right-hand reference previews using the same synthetic fixtures and small screens; document intentional departures (guards, real-data fields, confirmation, missing images).
- Test tabs/back stack/return targets and retained cart/filter/selected-location state; no duplicate root screens or inaccessible logout/profile.
- Test signup/login/OTP, real error mappings and session isolation with mocks; keep credentials out of logs and route state.
- Test product image/add/heart click separation, decimal totals and stock/minimum boundaries, Saved persistence and cart restart recovery.
- Test payment eligibility and pending/failed/unknown/confirmed UI; no HTTP 200 initiation success claim and no duplicate/automatic order/STK retry.
- Run relevant unit tests, lint, debug/test APK builds and Samsung mocked UI tests, then install -r unchanged app ID/data. Do not use live accounts/orders/payments as automated test fixtures.

## Assessment validation and safety

The temporary preview test APK built successfully and its capture test passed on Samsung, producing all 28 screenshots. An initial preview failed to resolve a test-APK banner resource; the resolver was corrected in the ignored harness and captures rerun successfully. This was a harness issue, not a production app failure. The normal test APK is rebuilt/reinstalled after capture; no preview source is shipped in the production APK. No new production source, Gradle project configuration, dependencies, backend URL or application ID was changed in this comparison. No full unit/lint rerun was necessary for this documentation/test-artifact assessment; previous 394-unit/21-auth-UI passes remain historical, not validation of the proposed future UI replacement.

Only this plan and a pointer from the earlier comparison report are intended persistent changes in this turn. All screenshots, previews and logs are ignored build artifacts. Other roles and existing uncommitted work are preserved. No reset, stash, clean, staging, commit, push, live account creation, cart mutation, order creation or STK initiation occurred.

**Decision requested:** approve the Buyer-only presentation replacement in batches A?C with the recommended retained rich Orders/payment details. Batch D remains a compatibility/guarded UI plan; unverified checkout or STK execution stays disabled. This report does not start major UI integration.
