# Buyer delivery selection implementation

Date: 2026-10-28. FarmApp Android only. Existing cart, role features, uncommitted changes and installed package are preserved. No staging, commit, push, backend edit, live order/payment or automated live address creation.

## Implemented navigation and UI

Cart Checkout now navigates to a full screen titled **Select delivery method**, replacing the cart-read popup. Navigation alone never creates an order or sends STK Push. Flow currently supported: Cart -> Select delivery method -> Add/select a saved Door Delivery address -> Review Order. The later new-order/payment/confirmation stages remain blocked for the exact contract reasons below; existing-order M-Pesa functionality is preserved.

- Back arrow and Android back return from substeps to delivery selection, then to cart. Cart product IDs, quantities and exact totals are never cleared by delivery navigation. Cart remains the selected shopping-navigation tab during delivery flow.
- Pickup Stations and Door Delivery are Material tabs with the existing Kilivana color/selection indicator.
- Pickup shows the requested "No saved pickup station yet!" text, supporting explanation and Add New Pickup Station button. That button opens a separate **Pickup stations unavailable** screen explaining that the current service exposes no selectable stations. It offers Door Delivery; no invented stations, saved station IDs or pickup-to-address substitution.
- Door Delivery fetches actual account addresses, shows loading/error/verified-empty states, and renders a radio selection plus Selected label for the chosen location. An empty account shows "No saved delivery address yet!" and Add New Address.
- Add New Address is a scrollable form: optional label, full address and optional paired latitude/longitude. Full address can include county, town/area, street/landmark and instructions. Separate recipient/phone/county/town/instructions JSON fields are not advertised, so no fields are silently submitted or discarded. Coordinates are never fabricated or defaulted to zero.
- LazyColumn, flexible text, full-width actions, 48dp back target, IME padding and a tested 280dp-wide/420dp-high viewport keep controls reachable on small devices. No full-height fixed empty-state artwork or new dependency/provider.
- Review shows the selected server address, real cart item subtotal, and honest unconfirmed delivery fee/final total. Change delivery location returns to selection. Place order remains disabled without a verified cart, valid quote and authoritative final total. No delivery fee or order success is invented.

## Save, select and reuse locations

Reused BuyerAddressController, BuyerAddressApi and BuyerPreferences instead of adding a second address book/session stack. POST users/addresses uses the existing authenticated ApiClient and session tag. The request contains only addressText, label and optional coordinates. No userId is taken from the UI; the own-address route attaches the authenticated owner.

The created address must have a valid server ID, expected buyer ownership, nonempty address text and valid optional coordinates before it is shown or selected. Save failure never creates a local address or displays false success. A single-flight flag prevents duplicate taps. A persistent pre-send marker prevents repeating an ambiguous timed-out save; the user is directed to refresh existing addresses and select a confirmed result. Definite 400/401/403/404/422 rejection clears the marker for a subsequent explicit corrected save. Ambiguous outcomes require reconciliation/support; no automatic POST retry.

Only the selected server address ID and unresolved-save flag are added to the existing account/origin-scoped preference file. No private address snapshot or fabricated server record is persisted. Selected address ID is restored/revalidated against GET users/addresses, and cleared if it no longer exists. A failed/loading refresh cannot enable review with stale rows. The chosen method stays in the session's saved UI state; changing tabs/backward navigation retains the address ID. After a cold start, Door Delivery reuses the revalidated ID; the UI does not claim a pickup station exists.

## Teammate review and component reuse

Inspected Kilivana-buyer AddressBookScreen.kt, CheckoutScreen.kt and ApiCheckoutRepository. The teammate uses saved address list/editor, selectedAddressId, quote invalidation on address change and quote.addressId matching. Those patterns are adapted to FarmApp's existing typed controller and Compose theme. No pickup station listing/selection was found in its purchasing flow.

The teammate serializes full address text/label/coordinates and userId, and its UI tries Geocoder/manual coordinates. FarmApp uses the own-address endpoint without a caller-specified userId and accepts the backend's optional-coordinate contract. No guessed geocoding or zero-coordinate fallback. FarmApp FarmerMapScreen is a foreground map/location viewer, not a reusable selectable-address picker with a return callback. Embedding it would not produce a verified delivery point, so no map provider/permission flow was duplicated. Existing Mapbox dependencies and map behavior are unchanged.

## Current deployed evidence and checkout sequence

Read-only GET https://hankering-morphing-outclass.ngrok-free.dev/api-docs returned HTTP 200 for this follow-up. Raw capture is ignored under app/build/buyer-delivery-openapi.json. SHA-256: 14841e7e9937466fc6a63bb5b76ad33421a7a7d9b0c106a777125537e6e3bb58.

- GET/POST /api/v1/users/addresses are documented as List/Create my address for the authenticated user.
- AddressRequest: required addressText; optional label, latitude, longitude and userId. Own route binds to authenticated user. This implementation omits userId. AddressResponse includes id, userId and the address details.
- No pickup/station/hub route was found among deployed OpenAPI paths. DeliveryQuoteRequest has only required addressId; no delivery-method/pickup-station field exists.
- POST checkout/validate has no body and returns CartResponse. POST checkout transitions/completes checkout according to its description. POST checkout/delivery-quote explicitly requires a CHECKOUT_PROCESSING cart and body {addressId}. Response includes quoteId, addressId, subtotal, deliveryFee, total, currency and expiresAt.
- POST checkout/order consumes quoteId (optional phoneNumber) and returns a numeric order ID. The teammate's intended sequence is upload cart -> checkout -> delivery quote -> checkout/order -> separate Buyer STK. Order-creation idempotency/recovery and whether supplying phoneNumber also initiates a payment are not fully specified.

Added exact typed quote request/response mapping and pure validation (same selected address, KES currency, nonnegative BigDecimal amounts, subtotal + fee == total, future expiry). Synthetic tests cover serialization and exact displayed final totals. These types do not unlock an unsafe live call: no quote is requested against an unmatched cart and production review never presents a synthetic quote. No amount from the local subtotal is passed off as an authoritative total including delivery.

## Exact remaining blockers

1. Pickup delivery cannot be selected for order placement because the service advertises neither available station records nor a way to transmit a pickup choice. Await Daniel's supported station/checkout contract; no local placeholder station is accepted.
2. CartResponse still only advertises id, buyerId, status, createdAt and updatedAt. It omits items, item IDs, product quantities/prices and totals. The client cannot reconcile the existing server cart with the persisted local cart or prove that a CHECKOUT_PROCESSING cart contains the displayed products. Blind POST additions or clear-and-rebuild remain unsafe.
3. Consequently quote/order creation is not enabled for new local-cart checkout. The user can save/select a real door address and review the intended order, but cannot place it until cart matching, valid quote, final total and duplicate-safe order recovery are verified. No backend quote endpoint is invoked automatically merely by selecting an address.
4. Authenticated address creation is implemented against the advertised own-address contract and mocked end-to-end; live account permission and geocoded delivery coverage have not been tested. Server rejection/timeout is handled without false success.
5. M-Pesa for eligible existing orders remains the previous explicit review/Confirm STK Push flow. New-cart order confirmation is not shown, and delivery navigation has no payment side effects. A real payment test would require separate explicit approval; none was performed.

## Tests and validation

Final full run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passed (BUILD SUCCESSFUL, 1m24s). 351 unit tests, zero failures/errors/skips. Lint: zero errors, 55 warnings and one hint. Both APK builds passed.

Samsung RF8WC0K043H was connected. Debug APK and UI-test APK installed in place with `adb install -r`: Success. BuyerDeliveryPresentationTest (6), BuyerShoppingPresentationTest (10) and BuyerPaymentPresentationTest (2) passed: OK (18 tests), 34.634s. Synthetic services/callbacks only; no live address/order/payment writes. Package remains `com.example.farmapp.dev2231918adc4d4b9d`, with existing data retained. No uninstall or data clearing.

New regressions cover delivery entry/back/cart preservation, tab switching, unavailable pickup, verified empty addresses, adding/selecting server-confirmed locations, selected ID restoration/removal, stale-selection guards, duplicate/uncertain saves, foreign-owner/logout rejection, optional coordinate validation, exact quote DTO mapping/expiry/totals and responsive small-device scrolling. Existing payment tests retain explicit-confirmation-only submission.

## Files changed in this follow-up

- `app/src/main/java/com/example/farmapp/buyer/BuyerAddresses.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerPersistence.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerCheckout.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerDeliveryScreens.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerScreens.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerShopping.kt`
- `app/src/main/java/com/example/farmapp/marketplace/BuyerShell.kt`
- `app/src/test/java/com/example/farmapp/buyer/BuyerShoppingTest.kt`
- `app/src/test/java/com/example/farmapp/buyer/BuyerCheckoutTest.kt`
- `app/src/test/java/com/example/farmapp/buyer/BuyerDeliveryTest.kt`
- `app/src/androidTest/java/com/example/farmapp/buyer/BuyerDeliveryPresentationTest.kt`
- `docs/BUYER_DELIVERY_SELECTION_IMPLEMENTATION.md`
- `docs/BUYER_CART_MPESA_CHECKOUT_IMPLEMENTATION.md` (points to the current delivery flow)

## Git and safety

Target remains https://github.com/RahaFuadHabib/KilivanaApp.git, branch feature/supplier. All prior reviewed files remain; this follow-up adds four files to the previous 78-path proposal (82 total). Earlier commit lists are historical and require updated approval. No stage/commit/push/reset/clean/stash/rebase, backend changes, application-ID change, app-data clear/uninstall, real order or payment. Final checks: 82 non-IDE proposed files, no deleted tracked paths, whitespace check passed, index empty. Proposed-file scan found no private keys, provider tokens, JWTs, credential URLs or sensitive/generated filenames (heuristic scan). Raw API capture remains ignored under app/build. Machine-specific .idea work remains excluded. No code from the teammate's Git metadata, local config or signing material was copied.
