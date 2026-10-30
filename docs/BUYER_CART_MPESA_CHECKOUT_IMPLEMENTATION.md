# Buyer cart, refresh and M-Pesa implementation

Current delivery follow-up: `BUYER_DELIVERY_SELECTION_IMPLEMENTATION.md` supersedes this report's cart-entry popup and read-only address descriptions. Checkout now opens delivery selection and supports explicit server-backed address creation/selection before review. Pickup and new order placement remain blocked by the documented deployed contract gaps. The results below describe the earlier cart/M-Pesa stage.

Date: 2026-10-28. FarmApp Android only. Existing uncommitted work preserved; no commit or push. This report supersedes the centered + and fully-disabled payment descriptions in earlier Buyer audits. Checkout from the local cart remains blocked by a specific deployed contract gap; payment for eligible existing Buyer orders is now implemented.

## 1. Marketplace and cart UI

- + is now inside the image at bottom-right with 8dp inset and a 48dp circular touch target. Its event remains separate from image/detail navigation. Existing per-product 600ms successful-add guard, stock/minimum-order checks and persistence remain in use. No Save action was reintroduced.
- Cart rows show the backend photo (or placeholder), name, KSh unit price, quantity, exact line subtotal, separate 48dp minus/plus buttons and Remove. Plus/minus change exactly one with no dialog. Minus is disabled at one or the product minimum, plus at stock/Int maximum. Invalid quantities never remove an item or change persistence. Existing account/origin-scoped cart storage and BigDecimal calculations are retained.
- The bottom review-checkout button shows the actual sum of locally verified item prices/quantities. Delivery is explicitly excluded until an authoritative quote exists. It opens a real server-cart read/review, rather than representing a local cart edit as an order. Unknown/unavailable lines disable review until refreshed. The cart navigation badge counts units.
- Home remains free of Saved/Cart/Address book shortcut sections. Existing Account/address/history navigation is retained. No cart screenshot attachment was available to inspect in this conversation; the supplied row/control specification was used. No sample products or prices were added to runtime UI.

## 2. Pull-to-refresh

Root cause: BuyerShoppingContent rendered a LazyColumn inside a plain Box. A loading bar and Refresh text button existed, but there was no Material3 PullToRefreshBox/nested-scroll gesture owner. The Buyer shell uses this screen, not the old shared marketplace screen.

Fix: wrap the Buyer list with PullToRefreshBox, keep the LazyColumn as its scroll child, bind refreshing to the controller busy state and route the gesture to the existing refresh action. Home/Shop gestures refresh the catalog; detail/cart gestures do not silently request a different operation. Fresh requests use zero-based page 0 (the first page), preserve attempted search/category filters, reject overlapping loads, replace/deduplicate results, retain prior rows on failure, and expose pagination on empty non-final pages. Pull UI test performs a real downward gesture.

Farmer Supplies already used PullToRefreshBox and still requests sellerType=SUPPLIER. Removed the second composition-triggered initial load (init already refreshes). Retry now retains attempted filters; Load more uses displayed filters after a failed filter attempt. Empty state no longer appears during loading/failure. Buyer still requests FARMER. Existing shared transport/filter regression tests are retained; no unrestricted catalog fallback.

## 3. Teammate M-Pesa investigation

Inspected local Kilivana-buyer commit 1813880, including data/Repositories.kt, data/AppViewModels.kt, MainActivity.kt, CheckoutScreen.kt, CartScreen.kt and OrderConfirmationScreen.kt, plus its test sources. Teammate files were read only. No old local backend was consulted.

The implementation is HttpURLConnection plus JSONObject, not Retrofit. ApiCheckoutRepository.checkout POSTs each local line to /api/v1/cart/items, then POSTs /api/v1/checkout. CartViewModel keeps only an in-memory checkoutLocked flag. It then requests /api/v1/checkout/delivery-quote with addressId, /api/v1/checkout/order with quoteId and normalized phoneNumber, and ApiPaymentRepository calls /api/v1/payments/stk-push/buyer with orderId/phoneNumber. Authorization is Bearer token, Accept application/json, JSON content type on bodies, plus ngrok-skip-browser-warning. The teammate base is origin-only and appends /api/v1; FarmApp base already includes /api/v1 and uses relative paths.

Phone normalization strips non-digits, replaces leading 0 with 254, and requires Kenyan 7/1 mobile prefix. FarmApp adapts this with stricter ASCII validation: letters/Unicode digits are rejected, ordinary spaces/brackets/hyphens allowed. No phone numbers or payment tokens are logged.

PaymentStatusScreen GETs /payments/{paymentId}/status once on entry and on manual Check payment status. Despite importing delay, it does not implement ongoing polling. It compares COMPLETED/SUCCESS for its refresh-button behavior, whereas the deployed PaymentResponse enum uses pending/paid/held/settled/failed/refunded. It converts monetary values through Double and sometimes Int; these conversions are not copied. HTTP success parsing does not consistently require envelope.success. It has no durable checkout/payment idempotency marker or timeout reconciliation. Repeated cart POSTs can conflict with existing products. Cart preferences are global guest storage, not user/origin scoped; not copied.

The teammate tests are only template arithmetic/package tests; no payment-specific tests or execution evidence establish that this source completed a real M-Pesa transaction. Its endpoint sequence is useful evidence of intended integration, not proof of current backend runtime behavior or safe retries.

## 4. Deployed API evidence

GET https://hankering-morphing-outclass.ngrok-free.dev/api-docs returned HTTP 200 on 2026-10-28. API title Kilivana Backend API, version 1.0.0. Raw capture stays ignored under app/build/buyer-current-openapi.json. SHA-256: 14841e7e9937466fc6a63bb5b76ad33421a7a7d9b0c106a777125537e6e3bb58.

Anonymous GET /api/v1/cart returned HTTP 401 with UNAUTHORIZED and authentication-required message. This confirms the anonymous boundary, not a successful authenticated Buyer cart read. No live credentials were extracted, no accounts logged in, and no live validation/checkout/order/payment POST was performed. Global security advertises bearerAuth; STK explicitly documents 403 when caller is not the order buyer. Actual account permissions, provider availability and callbacks cannot be certified by schema alone.

| Route | Current advertised contract |
| --- | --- |
| POST cart/items | JSON productId, quantity >=1; 201 CartResponse; 409 product already in cart |
| GET cart | Authenticated current buyer; CartResponse |
| PUT cart/items/{itemId} | itemId path, quantity query, not a JSON quantity body |
| DELETE cart/items/{itemId} | Removes by cart-item ID |
| POST checkout/validate | No request body; CartResponse |
| POST checkout | No request body; void envelope; descriptions say checkout complete and 409 already checked out/empty |
| POST checkout/delivery-quote | JSON addressId; requires CHECKOUT_PROCESSING cart; quoteId, addressId, subtotal, deliveryFee, total, currency, expiresAt |
| POST checkout/order | JSON required quoteId, optional phoneNumber; response data is a numeric Long order ID, not an order object |
| POST payments/stk-push/buyer | JSON required orderId, optional phoneNumber matching `^254(7\d{8}|1\d{8}|11\d{7})$`; amount is outstanding balance determined by server |
| GET payments/{id}/status | id, orderId, method, reference, exact amount, status, paidAt/createdAt/updatedAt; initially pending until callback |
| GET payments/order/{orderId} | Existing payment attempts for reconciliation when an initiation response/ID was lost |
| GET orders/{id}, orders/buyer/{id} | Owner/order identity, totals, items and order/payment states; existing FarmApp order history reused |
| POST payments/daraja/callback | Safaricom-to-server endpoint; duplicate callbacks for CheckoutRequestID described as idempotently ignored; Android never calls it |

The STK response has paymentId, orderId, amount, status, darajaResponse and createdAt. An HTTP 200 or even a paid-looking initiation status is never treated as payment completion. No checkout/STK idempotency header is advertised. The OpenAPI's idempotency mention concerns callback deduplication only.

Critical gap: #/components/schemas/CartResponse advertises only id, buyerId, status, createdAt, updatedAt. It has no items, item IDs, quantities or totals, despite GET cart's prose mentioning them. There is no advertised cart-item read endpoint. Legacy carts/{cartId}/items/{productId} update routes do not reveal extra existing lines. Equal quote subtotals cannot prove equal products/quantities. Therefore the teammate sequence cannot safely reconcile an unknown existing server cart with the persisted local selection. Blind additions, clearing a server cart or guessing fields would risk charging for wrong/stale items. No server cart is cleared or overwritten.

## 5. Integrations implemented

1. Authenticated GET cart through the existing ApiClient/session interceptors, with cart ownership/status validation. Review shows the live metadata or a safe failure and the exact reconciliation blocker. checkout/validate is modeled exactly but not invoked against an unmatched cart and is not presented as successful validation.
2. POST payments/stk-push/buyer added to the existing BuyerOrdersApi, not a second network/payment stack. M-Pesa controls appear in existing order detail for eligible placed/confirmed unpaid orders with no existing payment attempts. Preflight re-fetches owner order and payment records using existing endpoints; amounts use BigDecimal.
3. User enters/normalizes a phone, reviews the verified backend order total, then explicitly confirms STK Push. A fresh preflight rejects changed totals, wrong owners, paid/ineligible orders and existing attempts before any send. The request has no Android-calculated amount or Daraja secrets.
4. A user/origin-scoped durable order-attempt marker commits before network initiation. Concurrent taps/controllers and app restart cannot send another request for the same marked order. Failure to persist prevents the send. Existing canonical transport already disables write retries; connection retries and redirects remain disabled.
5. Response IDs and amount are checked, but displayed as pending regardless of initiation status. Lifecycle-scoped 15s polling/manual refresh reads payments/order and each payment's authoritative status; existing order polling/history remains in use. On reopening after restart, status is reloaded from server, not a persisted success flag. paid/held/settled status reads are confirmed payment outcomes; order completion is not inferred from them.
6. Timeout/unknown outcome keeps the durable marker. No retry is unlocked merely because payment listing is temporarily empty. Existing or failed attempts also do not offer a new STK request until a safe retry contract is confirmed. No marker is silently erased.
7. Cancelling the confirmation dialog sends nothing. A user's cancellation on the actual phone is left to the backend callback/status. PaymentResponse has no cancelled enum; unknown/cancelled/COMPLETED/SUCCESS strings cannot be presented as confirmed. Backend failed/refunded statuses are displayed honestly.

## 6. Remaining blockers / exact requirements from Daniel

- New checkout/order creation from the local cart is stopped at review. Daniel needs to expose or confirm a deployed, owner-scoped cart-line representation (IDs, product IDs, quantities/prices/totals) and its mutation/reconciliation semantics. A metadata-only response cannot support exact cart sync or safe itemId-based updates. No fabricated cart fields or destructive clear-and-rebuild fallback was added.
- Confirm the processing/quote/order sequence and whether order creation itself initiates M-Pesa when optional phoneNumber is supplied. Teammate does a separate initiation, but OpenAPI does not explicitly guarantee the absence of an implicit initiation. No guessed combination is run.
- Quote expiration, cart revision binding, authoritative item matching, numeric order ID recovery and order-creation idempotency/reconciliation must be confirmed before adding order writes. The client cannot guarantee exactly-once new checkout without backend support.
- A new payment after failed/unknown initiation remains blocked. Daniel must define safe retry/idempotency/attempt lookup semantics and how to resolve an empty list after timeout. Current implementation favors retaining the payment opportunity as unresolved rather than risking duplicate STK prompts.
- Authenticated authorization and a real provider prompt/callback have not been exercised. A live test would require separate explicit approval specifying actual amount, recipient/payment phone and transaction; none was requested or performed here.
- No new address mutations or Cash on delivery were inferred from the teammate UI. Existing saved addresses remain read-only. No checkout total including delivery is invented.

## 7. Validation

Full current-source run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passed (BUILD SUCCESSFUL, 1m55s). 344 unit tests, zero failures/errors/skips; lint zero errors, 53 warnings and one hint. Both APKs built successfully. Regression coverage includes direct quantity controls/bounds, exact money, persistence/restart, refresh page reset/filter preservation/single-flight/failure, corner-button navigation isolation, explicit payment confirmation/cancel, phone normalization, STK DTO mapping, duplicate prevention, timeout/restart reconciliation, total mismatch, foreign ownership/storage/logout rejection, pending/confirmed/failed/refunded/unrecognized status handling and checkout metadata rejection.

## 8. Samsung

RF8WC0K043H connected. Debug APK and UI-test APK both installed using `adb install -r`: Success. Package remains com.example.farmapp.dev2231918adc4d4b9d. Ran only BuyerShoppingPresentationTest (10 tests) and BuyerPaymentPresentationTest (2 tests): OK (12 tests), 17.088s. These render synthetic product/payment state and fake callbacks; the confirmation test cannot call an API. No app data clear/uninstall, live login, order creation, STK request or real payment. User data is retained in the existing package.

## 9. Files changed in this task

- `app/src/main/java/com/example/farmapp/buyer/BuyerShopping.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerScreens.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerPersistence.kt`
- `app/src/main/java/com/example/farmapp/buyer/BuyerCheckout.kt`
- `app/src/main/java/com/example/farmapp/marketplace/BuyerShell.kt`
- `app/src/main/java/com/example/farmapp/marketplace/Marketplace.kt`
- `app/src/main/java/com/example/farmapp/data/remote/ApiClient.kt`
- `app/src/main/java/com/example/farmapp/orders/BuyerOrders.kt`
- `app/src/main/java/com/example/farmapp/orders/BuyerPayment.kt`
- `app/src/test/java/com/example/farmapp/buyer/BuyerShoppingTest.kt`
- `app/src/test/java/com/example/farmapp/buyer/BuyerCheckoutTest.kt`
- `app/src/test/java/com/example/farmapp/orders/BuyerPaymentTest.kt`
- `app/src/androidTest/java/com/example/farmapp/buyer/BuyerShoppingPresentationTest.kt`
- `app/src/androidTest/java/com/example/farmapp/orders/BuyerPaymentPresentationTest.kt`
- `docs/BUYER_CART_MPESA_CHECKOUT_IMPLEMENTATION.md`
- `docs/BUYER_UI_AND_LOGIC_AUDIT.md` (marks previous commit plan and UI/payment descriptions historical)

## 10. GitHub readiness

Target remains https://github.com/RahaFuadHabib/KilivanaApp.git, branch feature/supplier. Earlier reviewed files and local package/configuration are preserved. This task adds six files to the previous 72-file proposal (78 total); the earlier commit manifest must be expanded and re-approved before any staging/commit/push. No generated build files, raw API captures, local.properties or keys are proposed. Final checks: 78 non-IDE proposed files; no private keys/provider-token/JWT/credential-URL or sensitive/generated filename findings; no deleted tracked paths; whitespace check passed; index empty. Secret scan is heuristic. Raw API captures remain under ignored app/build and are not staged. End-to-end checkout is not claimed ready; backend gaps above remain explicit.
