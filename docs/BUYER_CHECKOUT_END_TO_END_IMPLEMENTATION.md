# Buyer checkout end-to-end investigation and implementation status

> Latest checkout update: the guarded Brian cart-upload/quote/order-readback flow is now implemented without requiring GET cart lines on the happy path. See [BUYER_CHECKOUT_TEST_READINESS.md](BUYER_CHECKOUT_TEST_READINESS.md). Earlier GET-only checkout/blocker descriptions below are historical. The current tunnel returns ERR_NGROK_3200, and STK submission remains disabled until Daniel confirms sandbox/production for the active backend.


Latest presentation update: the four-screen reference was found and inspected in design-reference/Order Kilivana.png. See [Buyer Orders reference implementation](BUYER_ORDERS_REFERENCE_IMPLEMENTATION.md) for the implemented layouts, real-field mapping, visual review and validation. Earlier notes about the reference being unavailable are historical. Payment/cart safety blockers remain unchanged.

## Payment navigation diagnosis and fix ? 29 October 2026

Exact source trace: BuyerShell CART checkout navigates to DELIVERY. BuyerDeliveryFlow handles method/address selection and REVIEW; it does not create an order. At REVIEW the source contains the disabled `delivery-place-order` button ?Place order unavailable?. The literal ?Payment Unavailable? does not occur in current app source, so the reported label may describe this guarded state or another installed version; it cannot be attributed to an exact matching source string. No order ID exists in this path. Checkout is deliberately blocked pending verified cart lines and safe order creation, not by a missing STK endpoint.

The guard remains. Customer text now explains that server-cart items cannot yet be verified, a new order cannot be placed, and the local cart is saved. ?View existing orders? navigates through BuyerShell's existing Orders destination, preserving cart data and bottom navigation. It does not create or pay for an order.

Existing-order trace: Buyer bottom navigation ? Orders ? selected order ? BuyerPaymentPanel ? existing BuyerPaymentController. Previously the eligible panel immediately displayed phone input and a ?Review M-Pesa payment? button; it did not have a literal ?Pay with M-Pesa? action. It now presents a full-width green **Pay with M-Pesa** button only when the existing authenticated controller reports canInitiate. This opens local M-Pesa phone/review controls with **Send M-Pesa Prompt**, which opens the existing explicit **Confirm STK Push** dialog. Opening either screen/review sends nothing. Orders/Details receive green headings and rounded cards; bottom navigation is unchanged. The four-screen reference image is still unavailable in this context, so exact visual fidelity is not claimed.

Eligibility remains unchanged: owned and validated placed/confirmed order, pending/failed order payment status, positive backend total, no existing payment records and no prior durable initiation reservation. Paid/cancelled/ineligible orders cannot initiate. Existing payments (including failed ones) or an uncertain reserved attempt prevent another send until backend-supported retry semantics are known. No safety guard was removed. Kenyan phone validation, server-total recheck, persistent duplicate protection, foreground polling, timeout/restart recovery and backend-confirmed success remain in the same controller/client.

An eligible real unpaid order on the Samsung has NOT been established from sanitized evidence. Seeing orders alone does not establish payment eligibility. Authenticated controller failures retain a refresh/status explanation; no credential extraction or raw order dumps were used to manufacture eligibility. The current live cart still cannot be read as product lines. Full new-cart checkout remains blocked, and Daraja sandbox/live/callback readiness awaits Daniel's confirmation.

Validation completed: **376 unit tests passed**, zero failures/errors; **lintDebug and both debug APK builds passed**. **10 Samsung UI tests passed** (3 payment, 1 order formatting, 6 delivery). Both APKs were installed using adb install -r, retaining the same application ID and data. Payment tests verify the new Pay with M-Pesa entry, explicit send confirmation/cancellation, pending/failed/unknown/confirmed states; all use synthetic state without live requests. Logs are ignored buyer-validation-payment-navigation.log and buyer-validation-payment-navigation-samsung.log. No live order, payment, STK, cart mutation, backend change, application-ID change, uninstall/data clear or Git mutation was performed.


## Existing-order payment review and mock preparation ? 29 October 2026

Scope: payment readiness only; no live order/payment/cart mutation or backend change. Existing application/payment implementation was preserved. Existing-order eligibility on the signed-in device and Daraja sandbox configuration are still not verified; therefore this is conditional Android readiness, not a proven live demonstration.

Review results:

- Orders validates Buyer ownership, statuses, decimal amount and real item IDs before opening details. Payment eligibility requires placed/confirmed, pending/failed payment state, positive server total, no payment records and no local reserved attempt. No payment starts when merely opening the screen.
- Payment confirmation is a separate dialog. Dismissing Cancel submits nothing. This local cancellation differs from cancellation of an actual STK on the phone. The backend publishes no cancelled payment status; undocumented cancellation remains unverified, never success. A failed callback is displayed failed; a cancelled-phone-specific message awaits Daniel's confirmed mapping.
- An attempt marker is committed before STK dispatch. Busy guarding prevents rapid duplicate taps; the order/payment state and reviewed amount are checked again before dispatch. No retry follows a timeout or app restart. The durable marker plus existing payment reads blocks a second attempt, even if no payment record is yet visible. This intentionally also prevents automatic retry of a definitive failure; retry semantics need backend confirmation.
- Initiation HTTP 200 or even a synthetic PAID initiation status does not mean success: Android displays pending until payment-status GET confirms paid/held/settled. Failed/refunded/unknown do not display confirmed. Read failures display an unverified-status notice with Refresh and do not enable another STK.
- Foreground polling runs every 15 seconds while pending or while no verified result exists, stops on terminal known records and resumes via lifecycle restart/manual refresh. Order/payment IDs are recovered from owned order and payment APIs after restart; local initiation markers remain account/origin scoped. No Android Daraja credentials or direct Safaricom call is introduced.

New mocked tests cover pending ? network failure ? restored failed result without resend, unknown cancellation/foreign-payment rejection, and a synthetic intended cart/delivery method/validated quote ? supplied created-order DTO ? explicit initiation ? pending ? backend-confirmed held result. The supplied order DTO is an explicit test seam, not a mocked replacement for nonexistent production order orchestration. UI tests now exercise failed, unknown cancellation and confirmed display in addition to existing pending and explicit-confirm/cancel tests.

A complete executable cart-to-order end-to-end test remains blocked by the missing concrete line contract and order timeout-recovery contract. Existing delivery/cart/quote/payment component tests are available; no test-only workflow is presented as production functionality. Once Daniel answers, Android changes are confined to: typed cart-line DTO and validation; Retrofit cart-write methods; minimal reconciliation/journal integrated into BuyerShoppingModel's existing cart owner; snapshot verification/quote invalidation state in BuyerCheckoutReviewController; confirmed checkout/order DTOs and idempotent recovery; delivery-review confirmation wiring into the existing BuyerPaymentController. No second cart or payment controller is needed.

Validation completed: **376 unit tests passed, zero failures/errors**; lintDebug succeeded (zero errors; existing 57 warnings/1 hint); assembleDebug and assembleDebugAndroidTest succeeded. Updated test APK installed with adb install -r; application ID/data unchanged. **22 Samsung synthetic UI tests passed** (13 shopping, 6 delivery, 3 payment), including confirmation cancellation and failed/unknown/confirmed result display. No live API was called by these UI tests. Logs: ignored buyer-validation-payment-readiness.log and buyer-validation-payment-readiness-samsung.log. Only tests and this report changed; all prior source work remains intact. No reset, stash, staging, commit or push.


## Cart synchronization design and demonstration readiness ? 29 October 2026

The Samsung sanitized report was retrieved again and confirms usable Buyer session, HTTP 200, metadata-only data object, zero local rows/units and unknown server contents. No network request was initiated by the cache read. No server/local equality can be asserted.

### Existing Android and server behavior

BuyerShoppingController.add/setQuantity/remove/clearCart call the same local save function. BuyerPreferences persists the productId-to-quantity map with SharedPreferences.commit in the account/origin namespace. These actions do not invoke server cart mutation endpoints. Catalog refreshes are separate read-only requests. No production Android Retrofit cart-write implementation or order-create execution exists at this point; the new-cart review remains deliberately guarded. Existing quote DTOs/validation, address/pickup UI and existing-order M-Pesa controller remain reusable.

The most recently fetched live OpenAPI documents POST /cart/items and POST /carts/buyer/{buyerId}/items with productId and quantity. PUT /carts/{cartId}/items/{productId}?quantity=... explicitly describes quantity as the **new quantity**. PUT /cart/items/{itemId} and both DELETE variants also exist. These contracts were inspected without executing them. Only GET /cart and GET /carts/buyer/{buyerId} are documented cart-summary reads; both resolve to metadata-only CartResponse. No documented GET cart-items endpoint was found. Actual authenticated buyer-cart response also lacks lines.

### Safe synchronization design (not enabled, no live writes)

1. Resolve Buyer identity from the current authenticated session and bind every operation to its account/origin/generation. Capture a versioned immutable intended product/quantity snapshot. Reject an empty cart. Preserve local state on every failure or navigation change.
2. Read the authoritative cart plus all lines. Require owned cart, known checkout state, valid unique product/line IDs, positive integer quantities and precise decimal amounts. Missing/ambiguous data fails closed; never treat absent items as an empty list. Duplicate lines require confirmed backend aggregation semantics, not silent summation or deletion.
3. Present reconciliation differences, especially server-only products. Do not silently delete them. Obtain user approval before any live cart writes. Choose one API family, not both, and do not create a second Android cart state owner.
4. Derive a minimal plan: matching quantities need no write; known existing products use absolute-quantity PUT; genuinely absent products use POST only with confirmed add semantics. Any removal requires explicit approval. Serialize operations behind a busy guard and reject a changed local snapshot/session.
5. Persist a scoped non-secret operation journal before dispatch. On timeout or restart, GET the server lines before deciding the next action. Never automatically repeat an uncertain additive POST. Read back after changes and require exact product/quantity equality with the approved snapshot. Revalidate stock/prices with the server; surface differences for review.
6. Only a verified equality result for the unchanged snapshot unlocks POST /checkout. Read/reconcile its resulting processing state before retrying an uncertain checkout. Do not call it while cart verification is missing.
7. Request a quote for exactly one validated location and method. Display server subtotal, fee, final total, currency and expiry. Never append client estimate KSh 30. Invalidate quote when items/method/location change; refresh an expired quote before order confirmation.
8. Require explicit order confirmation. Persist a pending-order attempt before POST /checkout/order; persist returned order ID before any payment UI. On timeout, resolve by documented idempotency/quote-to-order lookup before permitting retry. If no such mechanism exists, freeze that attempt and request backend reconciliation rather than risk duplicate orders.
9. Reuse BuyerPaymentController for the resulting owned order. Refresh authoritative order/payment state, require separate explicit payment confirmation, normalize Kenyan phone, reserve its existing durable attempt marker, then initiate once. Poll payment ID; only backend paid/held/settled confirms payment. Unknown states/network failures stay unverified. Preserve IDs for recovery, never auto-resend after timeout.

### Work Android can implement independently

Once a concrete readable line schema is supplied, add its typed DTO/parser and synthetic fixture tests; implement a pure reconciliation planner and operation journal behind the existing cart owner; bind verified snapshot state to existing delivery/quote review; add the confirmed order-create DTO/execution with timeout-recovery contract. Mock tests can cover empty/missing/duplicate lines, quantity drift, server-only products, partial writes, expired quote, changed session/snapshot, unknown write result, order timeout, pending/failed payment and restart. These should not invent live line-field names or imply unimplemented checkout is complete. No speculative production parser or live mutation was added today.

### Exact information Daniel must provide

- Which authenticated GET returns the owned cart's complete product lines? Return an explicit items array (including [] when empty), line ID if required by item APIs, productId, integer quantity, decimal unitPrice/lineSubtotal and cart subtotal; document wrapper, stock/availability and duplicate rules. Either extend the existing response or identify the actual deployed endpoint.
- Confirm whether POST of an existing product increments or replaces quantity, and whether the documented absolute-quantity PUT is supported for Buyers. Confirm cart behavior after CHECKOUT_PROCESSING and recovery after interrupted checkout.
- Provide idempotency or an authenticated quoteId-to-order lookup so a timed-out order POST cannot produce duplicate orders. Confirm quote reuse/consumption behavior and how to resolve an uncertain attempt.
- Confirm Daraja sandbox vs live configuration, reachable callback and status reconciliation. Confirm customer cancellation mapping (published status enum has failed, not cancelled). A live/sandbox money test still needs separate user approval with exact amount and phone; credentials stay on backend.

### Existing-order demonstration checkpoint

The existing Android Orders screen reads /orders/buyer/{sessionBuyerId}, validates ownership and loads individual order/payment status. BuyerPaymentController permits initiation only for placed/confirmed orders with pending/failed payment state, positive authoritative total, no payment records and no reserved local initiation marker. Thus not every unpaid order is eligible. Failed/uncertain previous attempts do not silently unlock another STK request.

No eligible existing order has been proven on this device. The user was asked to open Orders, inspect an existing order and report whether Pay with M-Pesa is offered, without confirming payment. This uses the existing authenticated client and avoids extracting credentials. An existing-order demonstration remains conditional and does not demonstrate cart synchronization or new order creation. No orders/payments were created or modified.

No source changes were necessary for this design/report checkpoint. Previous test results remain historical: 373 unit and 27 Samsung Buyer UI tests passed, both APKs built, lint zero errors. No new full-checkout test is claimed. Existing work, application data and Git state are preserved; no staging/commit/push occurred.


## Authenticated cart diagnostic observed ? 29 October 2026

The user explicitly ran the existing debug-only GET diagnostic on Samsung RF8WC0K043H. The cached sanitized report was retrieved through its existing read-only provider; no new API request was triggered by retrieval.

Sanitized observations:

- Buyer session usable: yes. HTTP status: 200. Response type: object.
- Local cart at diagnostic time: 0 product rows, 0 units.
- Root fields/types: success (boolean), message (string), data (object), timestamp (string), error (null).
- data fields/types: id (number), buyerId (number), status (string), createdAt (string), updatedAt (string).
- Wrapper: data. Status field present. Candidate item list: absent.
- Server item count, product identifiers, quantities, prices and totals: not observable.
- Local/server contents match: unverified. Whether server cart is populated: unknown.

This is actual authenticated response evidence agreeing with the metadata-only documented schema. It does NOT establish that a populated server cart omits lines, or that the server cart is empty. No IDs, personal data, credentials or raw bodies were retrieved. Local counts are a snapshot at diagnostic time, not a fresh inventory after later navigation.

Immediate checkout blockers: the intended local purchase was empty at diagnostic time; the backend provides no readable cart lines in this observed response, so Android cannot compare intended products/quantities against server state. Existing POST /cart/items supports productId and quantity and is described as adding a product, creating a cart if needed. No cart writes were executed. Uploading/retrying blindly would risk unknown existing quantities and duplicates. A safe implementation needs a readable line summary and confirmed add/update semantics. New order creation also needs confirmed timeout reconciliation (idempotency or quote-to-order lookup); OpenAPI advertises no order idempotency header.

Precise request for Daniel: ?The authenticated GET /api/v1/carts/buyer/{buyerId} returns a success envelope whose data contains only id, buyerId, status, createdAt and updatedAt. Which authenticated read returns this cart's actual product lines? Please expose/identify stable line ID, productId, quantity, unitPrice, line subtotal and cart subtotal, including an explicit empty items array for empty carts. Confirm whether POST /cart/items increments or replaces an existing product quantity, how duplicates are prevented, and how Android can recover an order ID after POST /checkout/order times out, preferably using a unique quoteId lookup or idempotency key. Please also confirm the backend Daraja sandbox/live mode and callback readiness before a paid demonstration.?

For tomorrow's review, the existing verified-existing-order payment flow is the shortest conditional route: use an eligible real order already owned by this Buyer, read its authoritative amount/payment status, then require explicit approval before any STK request. No eligible order or sandbox readiness has yet been verified in this checkpoint. This does not demonstrate the full local-cart-to-order journey and must not be described as doing so. Do not bypass cart verification, fabricate an order or use mock success in production.

No source changes, orders, payments, quotes, cart synchronization or Git operations occurred. Tests/builds were not repeated for this documentation-only checkpoint; previous mock/device results remain 373 unit and 27 Samsung Buyer UI passes.


## Urgent payment-readiness checkpoint ? 29 October 2026

Fresh OpenAPI GET returned HTTP 200. The authenticated Samsung diagnostic cache still reports no user-triggered sanitized report. The user has been asked to run the existing in-app read-only diagnostic; no automatic diagnostic or credential extraction occurred.

Supported cart write contracts exist: POST `/api/v1/cart/items` and POST `/api/v1/carts/buyer/{buyerId}/items`, accepting productId (Long) and quantity (integer >= 1). PUT/DELETE variants also exist. These were inspected only, not executed. Every cart retrieval still advertises the metadata-only CartResponse, including GET `/cart` and GET `/carts/buyer/{buyerId}`. A write endpoint alone is insufficient for safe reconciliation: existing products, quantities, duplicate behavior and post-timeout state cannot yet be verified. The local Android cart is not assumed synchronized.

The six checkout/payment routes are present and bearer-authenticated. Checkout POST returns a wrapped void; delivery quote accepts method plus its matching location ID and returns authoritative decimal totals/expiry; order accepts quoteId and optional phoneNumber and returns wrapped Long. Buyer STK accepts orderId and optional Kenyan phone, returning paymentId/orderId/amount/status. Payment status GET returns wrapped PaymentResponse with advertised statuses pending, paid, held, settled, failed, refunded. Cancellation is not a distinct documented payment status: Daniel must confirm how customer STK cancellation is represented (for example failed and any exposed reason); Android must not invent a cancelled success/result. No sandbox/test-environment readiness was established by these schemas.

Existing Android payment handling already normalizes Kenyan phones, requires explicit confirmation, persists a pre-send per-order marker, reconciles order/payment reads, polls backend status and treats only paid/held/settled as confirmed. It is available for verified eligible existing orders, but no live STK request has been made. The guarded new-cart flow does not yet execute checkout or create orders. KSh 30 remains estimate-only and is excluded from backend payable amounts.

Minimum evidence needed now: run the sanitized cart diagnostic. If actual lines are absent, Daniel must provide a readable owned-cart line summary containing stable line/product IDs, integer quantities, decimal unit prices/subtotals and totals; confirm whether POST adds or replaces existing quantities and how duplicates are represented. Order-creation timeout recovery additionally needs a documented idempotency key or reliable quote-to-order lookup to avoid duplicate orders. Do not substitute an unverified order-list guess.

No Android code changed at this checkpoint. Prior tests remain 373 unit and 27 Samsung Buyer UI passes; no new test/build or live payment was represented as completed. Full new-cart live demonstration is blocked by cart verification and safe order-timeout reconciliation, not by an absent STK endpoint. No live cart writes, checkout, quotes, order creation, STK, staging, commit or push occurred.


## Successful connectivity retest ? 29 October 2026

Fresh read-only checks of the unchanged configured origin returned: `/api-docs` **200**, `/swagger-ui/index.html` **200**, `/api/v1/pickup-stations/availability` **401**, `/api/v1/pickup-stations/available` **401**. No ngrok error identifiers were returned; ERR_NGROK_8012 is resolved. Pickup 401 responses are credential-free authorization boundaries, not authenticated listing results.

Live OpenAPI SHA-256: `a8abb79b791e85455374cb1ebdf48c1af777d715d317acfa28aaed8418890453`. All eight pickup operations are advertised: availability, available, region, detail, saved list/save, saved removal and default update. Existing Android Retrofit paths, wrapped DTO field types and request fields agree. Optional station/saved timestamps are intentionally unused. Saved-record IDs remain distinct from platform station IDs. No live station writes were made.

Both DOOR_DELIVERY and PICKUP_STATION are documented in DeliveryQuoteRequest/Response. Android correctly sends exactly the applicable addressId or pickupStationId with deliveryMethod. Quote subtotal/deliveryFee/total/currency/expiry fields match. No service-charge field or authoritative fixed-fee rule was added to the schema; the 150 example is not proof of a fee rule, and pickup 50 remains unverified. No quote POST was made.

The confirmed sequence remains advertised: buyer-cart GET (wrapped CartResponse), checkout POST (wrapped void), quote POST, order POST (quoteId and optional phoneNumber; wrapped Long), Buyer STK POST and payment-status GET. CartResponse still declares only id, buyerId, status, createdAt and updatedAt; no items/quantities/prices. No checkout/order/payment operation was executed.

Own-address GET `/api/v1/users/addresses` returned 401 without credentials. Its wrapped list and existing Android address DTOs agree with OpenAPI. This does not establish whether the signed-in address error is resolved; authenticated retrieval through the app is still needed. No credentials were accessed, and addresses/selection were not modified.

Samsung RF8WC0K043H is connected. The existing read-only cache provider returned **No user-triggered sanitized report is available**. The diagnostic was not automatically triggered. User checkpoint: FarmApp ? Cart ? Inspect server cart (debug, read-only) ? Run read-only GET. Only its sanitized report may then be inspected. Server line presence and local/server matching remain unknown; order creation remains guarded.

No Android code changes were required by this comparison. Tests/builds/install were not repeated; previous results remain 373 unit tests, 27 Samsung Buyer UI tests, zero lint errors (57 warnings/1 hint), and both APK builds successful. Those are mock/device results, distinct from authenticated live verification. Raw OpenAPI remains under ignored app/build only. No staging, commit, push, data clearing or URL change occurred.


## Live verification checkpoint — 29 October 2026

Active configuration remains `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`. Read-only GET checks of `/api-docs`, `/swagger-ui/index.html`, `/api/v1/pickup-stations/availability` and `/api/v1/pickup-stations/available` all returned HTTP 502 with **ERR_NGROK_8012**. The tunnel is reachable but its configured local upstream is unavailable. This differs from the earlier offline-tunnel ERR_NGROK_3200 and does not establish missing endpoints or an older backend build. Daniel needs to check the running server and ngrok's upstream port/protocol. No replacement URL was assumed.

Samsung RF8WC0K043H is connected. Reading only the debug provider's cached sanitized report returned “No user-triggered sanitized report is available.” This query does not initiate a network request. The diagnostic was not triggered automatically. After upstream connectivity is restored, the user must run Cart → Inspect server cart (debug, read-only) → Run read-only GET before cart structure/counts can be inspected.

Pickup contracts, both quote methods, checkout/order/payment contracts and authenticated saved-address retrieval could not be freshly verified while the upstream is unavailable. No address-list request or write was executed; selected address and application data remain untouched. Server cart contents and local/server equality remain unknown, and new order creation remains guarded. No synchronization, checkout, quotes, orders or payment requests were issued.

No Android code changed at this checkpoint, so tests/builds/install were not repeated. Previous validation remains 373 unit tests and 27 Samsung Buyer UI tests passed, lint with zero errors (57 warnings, 1 hint), and both debug APK builds successful. These do not prove current authenticated backend behavior. All prior work and the configured URL are preserved; nothing was staged, committed or pushed.

Current follow-up: a debug-only in-app diagnostic is installed for the user-confirmed Samsung Buyer session. It is awaiting the user's explicit Run button; authenticated response structure and session usability are not yet observed. Section 11 supersedes the earlier session-availability limitation.

Date: 2026-10-28. This follows Daniel's confirmed sequence and the user's final instruction to investigate read-only before enabling cart mutation/order creation. No live cart mutation, checkout POST, order creation, STK Push, login or backend edit occurred. Existing application data and all prior source work were preserved.

## Finding and current conclusion

**Full new-cart checkout cannot yet be safely enabled from the evidence available in this session.** The exact deployed buyer-specific endpoint still advertises a metadata-only cart response. This proves a published-contract gap, not that the live populated response necessarily omits undocumented fields. No authorized existing Buyer test session was confirmed/available to perform the authenticated runtime comparison. Do not present the schema as a captured empty or populated cart response.

One Android issue was confirmed and fixed: checkout review used GET cart instead of Daniel's requested GET carts/buyer/{buyerId}. It now uses the buyer-specific route, derives buyerId from the current authenticated server-issued session, rejects mismatched/invalid owner IDs and preserves additional response properties for structural inspection. It never substitutes an arbitrary user-entered buyer ID.

## 1. Exact deployed OpenAPI inspection

Read-only GET https://hankering-morphing-outclass.ngrok-free.dev/api-docs returned HTTP 200. Raw public capture remains ignored at app/build/buyer-checkout-investigation-openapi.json.

SHA-256: 14841e7e9937466fc6a63bb5b76ad33421a7a7d9b0c106a777125537e6e3bb58. This is identical to the earlier checkout and delivery captures; the published document has not changed between these inspections.

GET /api/v1/carts/buyer/{buyerId}:

- operationId: getCartByBuyer; summary: Get cart by buyer.
- Description: Retrieves the active shopping cart for a specific buyer.
- Required path parameter buyerId: integer/int64.
- Both documented 200 and 404 bodies reference #/components/schemas/ApiResponseCartResponse.
- ApiResponseCartResponse has success, message, data, timestamp, error; data references #/components/schemas/CartResponse directly.
- No allOf/oneOf, page content, nested cart wrapper, item-list property, alternate populated-cart schema or item-summary response is declared along this reference chain. Global bearerAuth is advertised; an authenticated account's actual permission is not proven by that declaration.

CartResponse's **published properties and types** (not a fabricated runtime response):

| Property | Type |
| --- | --- |
| id | integer/int64 |
| buyerId | integer/int64 |
| status | ACTIVE / CHECKOUT_PROCESSING / ABANDONED |
| createdAt | string/date-time |
| updatedAt | string/date-time |

No item ID, product ID, quantity, unit price, line total, cart subtotal, currency or cart revision is declared. GET /api/v1/cart references the identical schema even though its prose says it includes items/totals. Thus switching to the buyer-specific route fixes Android's endpoint choice but does not by itself resolve the published response discrepancy.

## 2. Authorized read-only runtime response

No authorized test credentials/session were supplied or confirmed for this investigation. Checked only the presence (not values) of conventional KILIVANA_TEST_BUYER_TOKEN, KILIVANA_TEST_BUYER_ID, KILIVANA_TEST_ACCESS_TOKEN and KILIVANA_TEST_BUYER_EMAIL environment settings; none were set. An asynchronous question asked whether a Buyer test session was already available on Samsung; no confirmation was received during the work.

No credentials were printed, stored, guessed, obtained from another account or extracted from the phone. The installed app's encrypted session is not a freely accessible test token: EncryptedSessionPersistence.read() can clear its file/key on validation/decryption error. It was not invoked as a diagnostic because that could change application data, and an existing session has not been confirmed as an authorized test account. No new login/refresh was attempted.

Consequently no request with an arbitrary buyerId was issued, and no authenticated cart HTTP 200 body was captured. Empty-versus-populated runtime differences remain **unverified**. Creating/populating a cart to manufacture that comparison is specifically prohibited by this investigation. Only reads of already-existing authorized test carts should be used next.

## 3. Other cart data routes and nesting

Inspected all deployed cart paths and cart-related component schemas:

- GET carts/buyer/{buyerId}, GET cart: both ApiResponseCartResponse -> CartResponse.
- POST carts/buyer/{buyerId}/items and POST cart/items: CartItemRequest(productId, quantity), but returned CartResponse is still metadata-only in the specification.
- PUT/DELETE carts/{cartId}/items/{productId}: mutation routes by product ID; no GET item list.
- PUT/DELETE cart/items/{itemId}: mutation routes by cart-item ID; no GET to obtain those item IDs.
- POST carts/{cartId}/checkout and DELETE carts/{cartId}: mutations, not summary reads.
- CartItemRequest exists, but no declared CartItemResponse/cart-line summary schema was found. OrderItemResponse describes already-created order lines and cannot establish an active cart's intended purchase before creation.

Undeclared fields could still exist at runtime. The previous typed BuyerCartMetadata adapter would ignore them. The confirmed buyer-cart transport now returns JsonObject data and inspects bounded property paths without exporting values. Tests demonstrate preservation of synthetic nested fields; those fixtures are explicitly synthetic and are not evidence that Daniel's endpoint returns those names or values. Unknown fields never authorize order creation or become guessed item DTOs.

## 4. Teammate behavior and Android synchronization

Read-only inspection of Kilivana-buyer commit 1813880: ApiCheckoutRepository.checkout loops local items -> POST cart/items -> POST checkout; quote/order/STK follow. It does not GET cart or GET carts/buyer/{buyerId}, compare server quantities, discover cart-item IDs, read back writes, or prove absence of extra existing server products. CartViewModel's checkoutLocked flag is only in memory. No alternate cart-summary parser/retrieval method was found.

FarmApp's current Buyer cart intentionally persists local product IDs/quantities and refreshes product detail/prices. There is **no active server-cart upload/synchronization implementation**. Before this follow-up its checkout review only read GET cart metadata. This is not evidence of a failed upload request; no upload is attempted. The local cart may therefore differ from a cart previously created by another app/session. The safe fix is reconciliation against a complete owner-scoped server line list, not blindly replaying local additions or clearing remote data.

Implemented in this follow-up:

- GET carts/buyer/{buyerId} in BuyerCheckoutApi, relative to the existing /api/v1/ base.
- Runtime buyer ID comes from current canonical BUYER session. Session generation, origin, role and owner checks remain enforced.
- JsonObject transport preserves undeclared properties until inspection instead of silently dropping them into metadata-only DTOs.
- BuyerCartEvidence records metadata plus property paths, never raw response values. Unknown additional fields produce a distinct unverified-mapping notice; a metadata-only observed response is described as such. No inference that missing schema fields proves missing runtime fields.
- Fractional, zero and foreign cart/buyer identifiers are rejected. No server write/order/payment is enabled by diagnostics.

## 5. Can validate/checkout/quote replace the missing summary?

| Operation | Evidence | Result |
| --- | --- | --- |
| POST checkout/validate | No body; response references the same CartResponse | Its published result adds no item evidence. Not called during this read-only investigation. |
| POST checkout | Daniel confirms validation/transition to CHECKOUT_PROCESSING only, no order or payment; published response is ApiResponseVoid | No line summary. Prohibited cart mutation during this investigation; not called. OpenAPI still documents 409 already checked out/empty; Daniel's requested handling also includes 400. |
| POST checkout/delivery-quote | Requires processing cart and addressId; returns quoteId/addressId/subtotal/deliveryFee/total/currency/expiresAt | Authoritative aggregate totals, but no line items. Equal monetary totals cannot prove matching products/quantities. Quote may create server state; not called. |
| POST checkout/order | quoteId and optional phoneNumber; response ApiResponseLong with data integer/int64 | Daniel's confirmed order-creation step, not a pre-order cart inspection method; not called. |

Daniel's confirmed intended sequence is retained: verified/reconciled server cart -> checkout processing -> valid address-bound quote -> explicit order confirmation -> persist numeric order ID -> explicit existing Buyer STK confirmation -> authoritative payment status. The missing initial verification cannot be replaced by a successful status code, aggregate total or an order created first and checked afterward.

## 6. Minimum change required

If runtime GET carts/buyer/{buyerId} already has complete lines, the minimum backend-side work may be **documentation only**: supply its exact redacted current JSON/path/field semantics, then Android can map those confirmed fields and implement read/diff/write/read-back reconciliation. No backend change should be assumed necessary until that response is inspected.

If the runtime response genuinely contains only the five published fields, Daniel must expose an owner-scoped line-list read (preferably by extending the same summary response) containing cart/item IDs, product IDs, exact quantities and prices/totals. A separate existing documented read would also work if Daniel identifies it. This app cannot manufacture missing item IDs, infer extra server lines from totals, or inspect backend repositories as evidence of deployment.

After that evidence is available, the minimum Android integration is: validate current owner/cart state; detect duplicate product/item IDs; compare every intended local product and quantity against every server line; explicitly handle extra/missing/changed lines using the confirmed supported mutation semantics; read back and compare again; only then execute Daniel's processing/quote/order flow. Durable pre-send order-attempt state, persisted returned ID and order-history reconciliation are needed for timeouts; the existing Buyer STK controller is reused. Those future steps are not claimed implemented by this investigation.

## 7. Precise question prepared for Daniel (not sent)

> On the current hankering-morphing-outclass deployment, GET /api/v1/carts/buyer/{buyerId} resolves in /api-docs to ApiResponseCartResponse.data -> CartResponse with only id, buyerId, status, createdAt and updatedAt (OpenAPI SHA-256 14841e7e9937466fc6a63bb5b76ad33421a7a7d9b0c106a777125537e6e3bb58). Does the runtime response for a populated, authenticated Buyer-owned cart also return line items? Please provide redacted responses including HTTP status for already-existing empty and populated Buyer carts (or confirm 404 when no active cart exists), without credentials, the exact JSON path and names for cart-item ID, product ID, quantity, unit price, line total and cart subtotal, and whether a cart revision is available. If this endpoint does not return lines, which read-only endpoint does? Please also confirm which returned ID belongs in PUT/DELETE cart/items/{itemId}, how duplicate product lines are represented, and the safe update/read-back semantics. We need that list to verify the intended purchase before POST /checkout; aggregate quote totals alone cannot prove it.

## 8. Delivery and M-Pesa status

Existing tested delivery selection, own-address save/select, small-device scrolling, cart controls, Buyer refresh, Farmer SUPPLIER refresh and other role work remain intact. Pickup remains unavailable without a station/quote contract. Quote DTO/expiry/exact-total validation already exist but are not invoked against an unverified cart.

Existing-order M-Pesa still uses BuyerOrdersApi, explicit confirmation, durable duplicate markers and payment status reads. HTTP initiation success is not paid. No parallel M-Pesa implementation or fake order confirmation was added. New-cart checkout is stopped specifically at unverified cart matching, not because M-Pesa endpoints are presumed unsupported.

## 9. Validation and Samsung

The full current-source run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passed (BUILD SUCCESSFUL, 1m59s). Final totals: 354 unit tests, zero failures/errors/skips; lint zero errors, 55 warnings and one hint. Both APKs built successfully. New mocked tests cover the exact buyer-specific GET path, session-derived buyer ID, foreign/fractional IDs, metadata-only rejection and preservation of unknown nested properties. No mocked JSON is presented as a live backend response.

## 10. Files changed and Git safety

- app/src/main/java/com/example/farmapp/buyer/BuyerCheckout.kt
- app/src/test/java/com/example/farmapp/buyer/BuyerCheckoutTest.kt
- docs/BUYER_CHECKOUT_END_TO_END_IMPLEMENTATION.md

All prior work remains. Target: https://github.com/RahaFuadHabib/KilivanaApp.git, branch feature/supplier. No staged files, commit, push, reset, clean, stash, merge or rebase. Raw public OpenAPI/log/build outputs remain ignored. No application-ID change, uninstall, app-data clear, cart mutation, live order or payment. This report records a partial endpoint/parser correction and an unresolved runtime-evidence requirement, not completed end-to-end checkout.

### Final safety checks

83 pending non-IDE files including the new report; no deleted tracked paths, no staged files, whitespace check passed. Heuristic proposed-file scan found no private keys, provider tokens, JWTs or credential-bearing URLs. Previous 82 reviewed paths remain. Git branch/remote unchanged.

Both APKs installed in place on RF8WC0K043H with `adb install -r`: Success; application ID remains com.example.farmapp.dev2231918adc4d4b9d. No uninstall, application-data clearing or live backend mutation. First device suite: 17 passed, one initial dashboard test failed with "No compose hierarchies found"; the isolated test passed on immediate retry (1.766s). Full-suite retry passed: OK (18 tests), 33.168s. This was not treated as a passing full first run and no product code was altered to hide the failure.


## 11. Authorized in-app diagnostic follow-up

The user now confirms they are signed in as Buyer on Samsung RF8WC0K043H and authorizes a strictly user-triggered in-app diagnostic. The earlier lack-of-session statement describes the previous investigation, not this current authorization.

### Guardrails implemented

- Diagnostic implementation, streamed GET interface, report UI and report provider exist only in src/debug. src/release has a no-op UI hook and no diagnostic provider/service.
- Opt-in debug build property `kilivana.cartStructureDiagnostic=true`; default false. Normal/release builds do not expose the diagnostic. No application-ID/configured backend change.
- Cart offers an Inspect server cart button; opening it does not make a request. A separate Run read-only GET button must be explicitly pressed. No startup request, effect-triggered request, scheduled request or automatic retry.
- Buyer ID comes only from TokenManager's normal current authenticated session. Diagnostic code never reads access-token/refresh-token/cookie/header fields or reads/restores session storage. Authentication stays inside the existing ApiClient/OkHttp interceptor.
- A request-specific PreserveSessionOnReadFailure tag prevents the shared client's 401 refresh/retry/logout behavior for this read. It surfaces the original authentication failure without refreshing tokens, changing account or clearing storage. Untagged production behavior remains unchanged; regression-tested.
- Exactly one GET carts/buyer/{buyerId}; no diagnostic endpoint for cart writes, validation/checkout, quote, address, order or payment. Existing client's redirects and connection retries remain off.
- Body is parsed only inside the app, capped at 256KiB, never logged/exported/stored. Output uses fixed labels, an allowlist of field names, types, presence flags and counts. Sensitive-key branches are omitted; unknown/dynamic keys are redacted. No primitive JSON values, IDs, product names, personal fields or credentials are output. Candidate arrays are not automatically accepted as a verified cart mapping.
- Comparison reports local product-row count and unit count separately. Missing item list means server item count is unobservable; it never claims metadata proves populated server cart (B) or empty server cart (C).
- Optional local ADB report reader is an opt-in debug-only ContentProvider protected by android.permission.DUMP. It returns only the already-sanitized volatile report after the user's trigger. Its query/onCreate never initiates a request or reads app files/session secrets. Reports expire in ten minutes and clear when the dialog closes/session changes. No third-party service or raw-body report file.

### Current execution status

The opt-in diagnostic build passed 362 unit tests, zero failures/errors/skips, lint with zero errors (55 warnings and one hint), debug APK and UI-test APK builds. APKs installed in place on Samsung, same package and first-install timestamp/data directory. Initial synthetic UI run: 19 passed, one virtualized delivery-list assertion failed; isolated retry passed. Its scroll assertion was corrected to use the LazyColumn's scroll-to-node action; test APK rebuilt/lint passed and full UI rerun passed: OK (20 tests), 42.524s.

The agent has not triggered or observed the live GET. User action is still required. User trigger is still required before any read; no result is inferred from schema or local cart content. Once sanitized evidence is obtained, classify A/B/C/D carefully, implement only an observed field mapping if present, and disable the temporary diagnostic in the installed app afterward.


### User-trigger checkpoint

Installed debug package remains com.example.farmapp.dev2231918adc4d4b9d. The user has been instructed to open Cart -> Inspect server cart (debug, read-only) -> Run read-only GET, then leave the sanitized report open. No user credential is requested. The agent will retrieve only the already-sanitized report through the debug reader after that action.

- Existing Buyer session usable for this GET: pending in-app execution.
- GET HTTP result and structural fields: pending; no live response invented.
- Server populated status: unknown; metadata alone will not establish B or C.
- Local/server match: not established; no cart sync/write performed.
- New order creation/payment: remain disabled during this investigation.
- Temporary diagnostic retirement: pending completion of inspection; shared default remains off and release implementation is absent.
- Validation completed: 362 unit tests, lint (0 errors, 55 warnings, 1 hint), debug/test APKs, successful -r installation and 20 passing synthetic device UI tests. Initial list-test failure and correction are recorded above.


### Awaiting explicit app trigger

After installation and tests, the debug reader was queried twice. Both queries returned only "No user-triggered sanitized report is available." Querying this reader cannot start HTTP or read session storage. No live response is currently available to the agent. A missing report is not an authentication failure and does not prove whether a GET ran and its report was subsequently closed/expired.

The next required user step is Cart -> Inspect server cart (debug, read-only) -> Run read-only GET, leaving that screen open. Once obtained, only sanitized structure/counts will be read; no credentials or response values will be exported. Diagnostic retirement in the installed APK will follow completion, not be falsely reported complete while the user-triggered inspection is pending.


Current checkout validation: 424 unit tests and final 21/21 Samsung mocked UI tests passed; lint 0 errors, 79 warnings; both APK builds and in-place installation passed. See BUYER_CHECKOUT_TEST_READINESS.md for the guarded upload/order-readback flow. GET item lines no longer block its normal path. Actual STK remains disabled: the configured tunnel returned ERR_NGROK_3200 and sandbox/production is unconfirmed.
