# Buyer checkout and M-Pesa test readiness

29 October 2026. This report supersedes the earlier requirement that GET cart item lines must exist before any checkout integration can proceed. Main project FarmApp, feature/supplier; Brian source master 8f335e23f0d29c2953fe6373b7ff6f2da9e371e6.

## Current connectivity and payment environment

The configured API URL is unchanged: https://hankering-morphing-outclass.ngrok-free.dev/api/v1/ . Fresh read-only /api-docs and /swagger-ui/index.html returned HTTP 404. A single health check returned HTTP 404 with ERR_NGROK_3200. The configured ngrok tunnel is offline. No further retry or URL substitution was performed after this was identified.

No live authenticated cart mutation, checkout, delivery quote, order creation or STK call was made. New integration uses the previously captured deployed OpenAPI contract and synthetic API responses; it is NOT represented as live-verified against the presently unavailable backend.

M-Pesa sandbox versus production cannot be established from an offline tunnel or endpoint names. Actual Buyer STK submission is disabled in the installed build by an explicit production-controller gate. Orders may still be reviewed and payment statuses retrieved when the backend returns. No credentials or Daraja secrets are requested or stored in Android.

Daniel needs to confirm the environment for the active backend, restore the tunnel (or supply a replacement URL for approval), and confirm the intended merchant/sandbox test setup and callback reachability. No secrets are needed. After that confirmation, the gate can be configured with kilivana.mpesa.environment=SANDBOX or PRODUCTION and kilivana.mpesa.confirmedApiBaseUrl set to the EXACT active configured API base URL, followed by a rebuild. Both are required; UNKNOWN or a different URL remains disabled. Current build leaves these unconfirmed. The API URL itself is not changed by these properties.

## Brian's original sequence and compatible integration

Brian's actual ApiCheckoutRepository uploads each local line with POST /cart/items, calls POST /checkout, requests an address-only delivery quote, then creates an order. His payment repository calls the Buyer STK endpoint and reads payment status. FarmApp now reuses that sequence and the actual Brian UI with necessary contract/safety adaptations:

1. Buyer reviews the existing account-scoped local cart, chooses Door Delivery or Pickup, and enters a Kenyan M-Pesa number. No second cart implementation is introduced.
2. Calculate delivery opens a confirmation explaining that selected products/quantities will be uploaded. Only the Buyer's confirmation runs writes. Merely opening a screen does not run them.
3. GET carts/buyer/{authenticated buyerId} validates cart metadata and ownership; item lines are NOT required by this path. A documented cart-not-found response can precede creation by the cart-add API. Existing non-active carts are not overwritten blindly.
4. POST cart/items uploads each desired product and quantity once. A documented 409 Product already in cart is treated as a definite rejection; the supported PUT carts/{cartId}/items/{productId}?quantity=... sets the EXACT desired quantity. The POST is not replayed and quantities are not blindly incremented. Other errors are surfaced with stage and HTTP status; only a bounded, sanitized machine error code may be displayed, never raw bodies.
5. Every write has a durable account/origin-scoped journal marker BEFORE submission. Cart ID and acknowledgement state are checked. A timeout, 5xx, malformed response or uncertain upload stops the sequence. It is never automatically uploaded again. Explicit manual resubmission is allowed only after definite documented rejections. Journal version checks prevent competing controller instances from overwriting each other's checkout state.
6. POST checkout is called once. Its ApiResponse<Void> success is NOT an order or payment. A readback confirms the same owned cart is CHECKOUT_PROCESSING before quote retrieval. If checkout acknowledgement is uncertain but a later read establishes that processing state for the known cart, reconciliation can continue to a quote without repeating POST checkout. An ambiguous item upload cannot be resolved from metadata alone and remains stopped.
7. POST checkout/delivery-quote sends the CURRENT documented contract: {deliveryMethod: DOOR_DELIVERY, addressId: actual saved address ID} or {deliveryMethod: PICKUP_STATION, pickupLocation: user-confirmed one-time location}. The latest verified schema uses pickupLocation TEXT, not pickupStationId. The existing saved-station picker can supply real station information for the Buyer to confirm; IDs are never substituted for addresses.
8. The quote supplies authoritative subtotal, deliveryFee, total, currency and expiry. Local subtotal is only a consistency check for unexpected server items or repricing; it is NOT the payment amount. A mismatch stops order creation and asks for price refresh/server-cart reconciliation. Exact server quote totals are displayed. No client-only KSh 30 charge is added. Quote expiry and changed cart/delivery selection invalidate review. Refreshing an expired quote does not reupload the cart or start checkout again.
9. Buyer explicitly confirms order creation after reviewing the server quote and number. POST checkout/order sends quoteId and normalized phoneNumber. The documented ApiResponse<Long> numeric data is parsed exactly, without fabricated IDs or guessed wrappers.
10. The returned order ID is durably saved BEFORE entering payment. GET orders/{id} then verifies ownership, exact product-ID/quantity set, backend line amounts, subtotal, delivery fee, total and selected delivery location against the reviewed quote. An extra/missing product, quantity mismatch, different location or changed amount prevents payment review. Order-time prices are used; the local estimate is never supplied as an STK amount.
11. Only a verified order reaches the existing BuyerPaymentSession. The server order total and entered normalized phone are displayed; the Buyer can review/change the phone and explicitly confirm ONE STK request. Environment confirmation is additionally required. The STK request contains only real orderId and phoneNumber; the backend determines the amount. Existing durable duplicate protection, owner/status recheck and amount-change checks remain.
12. Pending is displayed while the existing lifecycle polling reads payments/order/{orderId} and payments/{paymentId}/status. HTTP 200 initiation is not success. Backend paid/held/settled states confirm payment; pending, failed, refunded and unverified/unknown states remain distinct. Undocumented cancelled values cannot be promoted to success. Network interruption never automatically retries STK.

## Ambiguity, recovery and limits

- Known returned order ID + failed order read: Check checkout status reloads that SAME order, validates it and resumes payment review. No second order is created.
- Timed-out order creation without an ID: the app reads existing orders and reports the number of new matching candidates, but does not automatically adopt one based on similarity. It cannot prove quote correlation. Inspect Orders manually and contact support; creating another order stays blocked. Daniel should provide order idempotency or an authoritative quote-to-order lookup for automatic recovery.
- Ambiguous cart-item writes still need authoritative item reconciliation (or Daniel's documented reconciliation mechanism). Missing GET item lines are no longer the happy-path blocker, but they genuinely limit recovery from an uncertain upload. No blind retry/reset/clear-cart action was added.
- The quoted subtotal must match the fresh intended-items estimate before creating an order. Existing unrelated server products, stale prices or a changed pricing rule require reconciliation, not a fabricated total or destructive server-cart reset.
- A processing checkout whose cart is edited locally is not silently reuploaded. A matching processing snapshot must be recovered or backend-supported cancellation/reconciliation is needed. No undocumented checkout-reset endpoint is invented.
- After an order is returned, the app continues to that existing verified order rather than creating another from the same saved checkout. Cart contents are preserved, and existing cart/payment preferences are not erased.
- The environment remains UNKNOWN, so no real STK prompt can be demonstrated yet. Mock tests do not establish that Daniel's Daraja credentials, callbacks or settlement work.

## Files added/adapted

- brian/BrianCheckoutController.kt: actual authenticated Retrofit operations, upload/check/quote/create/readback orchestration, durable journal and read-only recovery.
- brian/BrianBuyerApp.kt: Brian checkout screen connected to orchestration; explicit cart-upload and order confirmations; verified order navigation.
- brian/ui/screens/CheckoutScreen.kt: current delivery quote handling, expiry, server totals and separate Create order action.
- brian/BrianPaymentReview.kt: entered phone prefill, backend total and disabled submission for unknown environment.
- brian/BrianModels.kt and orders/BuyerOrders.kt: actual addressId/pickupLocation mapping and validation.
- orders/BuyerPayment.kt and BuyerMpesaEnvironment.kt: fail-closed environment policy while retaining the existing one-payment controller and recovery.
- app/build.gradle.kts: non-secret, exact-backend-bound environment confirmation configuration; no application ID/API URL change.
- Synthetic unit/UI tests and this report; historical reports point here.

## Validation

Final validation completed:

- **424 unit tests passed; 0 failures, 0 errors.** Includes 20 checkout orchestration/recovery/lifecycle tests, the M-Pesa environment guard, pending refresh continuity and the existing payment duplicate/timeout/status tests.
- **Android lint passed: 0 errors, 79 warnings, 1 hint.** No lint checks were disabled.
- **assembleDebug and assembleDebugAndroidTest passed.** Final production-source validation completed in 5m 2s. After test-driver stabilization, lint and both APK checks passed again in 1m 38s; the main APK was unchanged.
- Samsung RF8WC0K043H was authorized. The unified main APK and its instrumentation test APK installed successfully using adb install -r. No uninstall, clear-data, logout, account replacement or application-ID change.
- **Final Samsung run: 21/21 mocked UI tests passed** in 48.795 seconds. Coverage includes real Brian checkout quote/expiry display, environment-disabled submission, pending/confirmed/unknown states, explicit payment cancellation, cart controls, signup/login and OTP continuation. No live APIs are used by these synthetic UI tests.
- Earlier device runs timed out on different mocked auth controls (20/21 followed by 3/4 isolated auth cases). Auth state-boundary tests were stabilized by scrolling to an enabled control and issuing one accessibility activation, avoiding motion-sensitive physical taps during possible IME animation. This is a test-driver adjustment, not a claim that live authentication was newly tested or that an IME root cause was proven. Physical checkout/payment button tests remain in the suite. Manual real Buyer login remains a user action.
- Installed public base APK SHA-256 matches the local debug APK: `d19467fed154cd2daa6acc48be312fd3efbbe1bab138be24b1bac3aad0cbb0e6`.
- BuildConfig confirms the unchanged application ID `com.example.farmapp.dev2231918adc4d4b9d` and configured API URL. BUYER_MPESA_ENVIRONMENT=UNKNOWN and BUYER_MPESA_CONFIRMED_API_BASE_URL is empty. Actual STK submission is therefore disabled by both the production controller and the Buyer button.
- New checkout source credential-pattern scan: 0 matches. Git index is empty; logs, local.properties and build/API captures remain ignored. Origin and branch remain the user's KilivanaApp repository and feature/supplier.

An existing pending/ambiguous checkout cannot be reset into another order. After the previous owned order is backend-confirmed paid/refunded or cancelled, an explicit Start another checkout action can reset only the local completed-checkout journal for a future purchase. It performs no cart/order/payment write. Stored payment-attempt safeguards and order history remain. Pending/uncertain payment attempts now remain on the waiting/status screen during refresh and after restart rather than returning to a submission form.

## Manual test path after Daniel restores service

1. Confirm the active backend URL and sandbox/production environment with Daniel. Do not infer environment from ngrok or from the presence of STK routes. Current installation will not send STK until that confirmation is applied to a new build.
2. Sign in manually as Buyer. Add real Farmer products and review quantities in Cart.
3. Checkout: select the actual address or supported pickup location and enter the intended M-Pesa number.
4. Calculate delivery -> Upload cart and calculate. This is a REAL server-cart write performed only by your confirmation; it is not an automated test action.
5. Review the server quote. Create order -> Confirm order is a REAL order-creation action performed only by you. The app verifies the returned order and amount before enabling payment review.
6. Review the backend order amount and normalized number. After environment confirmation is configured, Send M-Pesa prompt -> Confirm STK Push initiates one real/sandbox request as configured. Do not perform this step until the intended environment and transaction are understood.
7. Wait for backend payment status. Unknown or failed requests are not repeated automatically; use status refresh or inspect Orders. Do not create another order or resend STK after an ambiguous response.

Live cart acceptance, order creation, delivery fees, STK delivery, callbacks and settlement are NOT validated by the mocked suite. The immediate live-demo blockers are the offline configured tunnel and unconfirmed payment environment; ambiguous-write reconciliation limitations above remain intentional safeguards.

No live writes or financial testing was performed. Existing work and application data are preserved. No Git reset, stash, clean, stage, commit or push. Other roles were not replaced or unnecessarily modified.