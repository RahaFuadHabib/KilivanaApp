# Correct Buyer master comparison and proposed integration plan

Presentation follow-up: [Buyer screen-by-screen integration plan and screenshot comparison](BUYER_SCREEN_BY_SCREEN_INTEGRATION_PLAN.md) distinguishes behavior integration from actual UI replacement and compares 14 mock-rendered screen pairs. No major UI replacement is performed by that assessment.

29 October 2026. **Assessment only; major integration awaits user approval.** FarmApp remains the main application. No source/module replacement, live account creation, cart writes, checkout/order/payment requests, staging, commit or push occurred.

## 1. Repository identity and inspection boundary

| Item | FarmApp | Correct Buyer reference |
| --- | --- | --- |
| Directory | C:/Users/AppleFi/AndroidStudioProjects/FarmApp | C:/Users/AppleFi/AndroidStudioProjects/Kilivana-buyer-reference |
| Remote | https://github.com/RahaFuadHabib/KilivanaApp.git | https://github.com/brianmuigai2-stack/Kilivana-buyer.git |
| Branch | feature/supplier | master |
| State | Existing extensive tracked/untracked work preserved | Clean; HEAD and freshly fetched origin/master both 8f335e23f0d29c2953fe6373b7ff6f2da9e371e6 |
| Freshness | Current working tree inspected, including prior Buyer work | Safe fetch of master completed; no checkout/pull/merge/reset |

The exact requested reference folder already contained a complete clean clone of the correct remote/master. It was reused and fetched instead of cloning over it. No source files in that clone were changed. Earlier reports examined 1813880 (27 October, ?complete payments and orders?). Current master is 8f335e2 (29 October, ?add otp field?). The diff between these revisions changes only MainActivity.kt, AppViewModels.kt and AuthScreens.kt (29 insertions, 10 deletions), adding OTP-related continuation/password handling. The checkout/payment repository code is unchanged between those revisions. This report is based on the newly verified complete master tree, not an assumption that the old copy was current.

Both whole-project/module/source inventories were inspected (126 FarmApp main Java/Kotlin source files; 22 reference main source files). Deep review focused on Buyer screens, the complete reference data/ViewModel/navigation flow, authentication/session boundaries, API configuration, DTOs, cart/checkout/payment, manifests, dependency catalogs, and test inventory. Other role implementations were inventoried for integration boundaries; this is not a new exhaustive bug audit of every other role. FarmApp AGENTS.md is empty; no tracked reference AGENTS.md was present. No private device stores, credentials, local signing material or actual session values were read.

## 2. Architecture and configuration comparison

| Concern | Current FarmApp | Correct reference master | Proposed decision |
| --- | --- | --- | --- |
| Application | One multi-role :app; Buyer shell under existing authentication routing | One standalone Buyer-only :app | Preserve FarmApp app module/launcher and other roles; no module replacement or Git-history merge |
| Identity | com.example.farmapp namespace; installed debug com.example.farmapp.dev2231918adc4d4b9d | com.example.kilivana_buyer namespace/application ID | Keep FarmApp identity and data; do not install reference over it |
| Navigation | AppNavigation/AuthDestination routes Farmer, Buyer, Supplier, Inspector and canonical Driver; unsupported fallback retained | MainActivity-owned NavHost; Home/Products/Cart/Orders/Account plus auth/OTP/checkout/status routes | Adapt Buyer-only transitions inside BuyerShell; preserve root role checks and bottom navigation |
| State/data | Typed Retrofit/Gson, StateFlow/ViewModels/controllers, session snapshots and repositories | Interfaces/concrete repositories consolidated in data/Repositories.kt; HttpURLConnection + JSONObject; four ViewModels in data/AppViewModels.kt | Keep existing FarmApp transport and session owners; do not add a parallel HTTP stack |
| API base | Configurable /api/v1/ base; debug override; release requires explicit HTTPS config | BuildConfig root origin defaults to temporary ngrok; repositories append /api/v1/... | Translate paths to Retrofit-relative routes; do not copy root URLs or duplicate /api/v1; retain configurable release policy |
| Toolchain | AGP 8.11.2, Kotlin 2.0.21, compile/target 36, min 26, Compose BOM 2024.09.00; Java 11 | AGP 9.4.1, Kotlin 2.2.10, compile/target 37, min 24, Compose BOM 2026.02.01; Java 11 | No toolchain upgrade required for selective integration; do not copy Gradle/version catalog |
| Dependencies | Retrofit/OkHttp/Gson, Compose/navigation/lifecycle/icons, Mapbox, existing image loader | Compose/navigation/lifecycle/icons, Coil 2.7.0; no Retrofit | Reuse installed dependencies; do not add Coil or new maps provider without demonstrated need |
| Persistence | Keystore AES-GCM session in noBackupFilesDir; account/origin-scoped shopping/payment state | Token strings in ordinary kilivana_auth preferences; kilivana_guest_cart shared cart preferences; Saved Items IDs in memory | Preserve encrypted session and scoped persistence; no token/store migration from reference |
| Money | BigDecimal end to end for Buyer/cart/quote/payments | Product/order money as Int; quote/payment as Double; several Double.toInt conversions | Preserve exact decimal values; never import truncation (e.g. a synthetic 90.50 would become 90) |
| Tests | Most recent pre-assessment run: 387 unit passes; mocked Samsung auth/payment/Buyer coverage exists | Only template addition and package-name tests in the tracked tree | Keep FarmApp tests; add full orchestration mocks after contract confirmation; reference tests do not prove live checkout works |

## 3. Exactly how reference authentication works

ApiAuthRepository.signIn POSTs /api/v1/auth/login with email/password, saves access/refresh tokens, then GETs /api/v1/auth/me and requires role BUYER to resolve identity. On failure it clears its preference session and returns exception/server message. Startup refreshSession first checks /auth/me; on any failure it attempts /auth/refresh then /auth/me, clearing credentials if unsuccessful. These are real HTTP operations in source, not a mock login implementation, but none was executed in this assessment.

Registration POSTs /api/v1/auth/register with name/email/phone/password/role=BUYER/username/region. MainActivity then separately requests /auth/email-verification/request, navigates to OTP verification, and the latest master retains pendingPassword in AuthViewModel memory to sign in after verification. No alternative Buyer registration endpoint or hidden payment credentials were found in the inspected implementation. It does not remove the backend's duplicate-account or rate-limit decisions.

FarmApp already sends the same canonical registration fields and supports email-verification continuation, using the creation challenge when available. Its latest Buyer fixes preserve Retry-After, avoid asserting a generic conflict is definitely an account duplicate, and guard same-frame/ViewModel/repository submissions. Preserve these rather than replace authentication with the reference's raw message handling. Adapt the desired OTP/return-to-shopping experience where needed, not the plaintext token preferences, indefinite pendingPassword field, broad cancellation catches or redundant challenge request. Reference OTP marks verified before onLoginSuccess returns; do not import a UI that can say signed in when the subsequent sign-in failed.

Reference evidence: [authentication repository](https://github.com/brianmuigai2-stack/Kilivana-buyer/blob/8f335e23f0d29c2953fe6373b7ff6f2da9e371e6/app/src/main/java/com/example/kilivana_buyer/data/Repositories.kt#L88), [auth navigation](https://github.com/brianmuigai2-stack/Kilivana-buyer/blob/8f335e23f0d29c2953fe6373b7ff6f2da9e371e6/app/src/main/java/com/example/kilivana_buyer/MainActivity.kt#L234).

## 4. Exactly how reference checkout and M-Pesa work

1. CartViewModel holds local CartItem rows, persisted as productId:quantity in a guest-cart preference. Adding/browsing does not synchronize the server.
2. Calculate delivery calls CartViewModel.requestDeliveryQuote. If its in-memory checkoutLocked is false, ApiCheckoutRepository.checkout loops over every local item and POSTs /api/v1/cart/items with productId and quantity, then POSTs /api/v1/checkout. It does not GET an existing server cart or compare its contents. checkoutLocked becomes true only after this entire step succeeds.
3. It POSTs /api/v1/checkout/delivery-quote with **addressId only**. It parses quoteId/subtotal/deliveryFee/total/currency/expiresAt into a Double-based model. CheckoutScreen checks matching address ID but does not validate quote expiry.
4. Create order and pay calls POST /api/v1/checkout/order with quoteId and normalized phoneNumber. The parser accepts numeric envelope data, alternate id/orderId objects and a top-level fallback, rather than validating one exact deployed shape.
5. If M-Pesa is selected, the same submitCheckout coroutine immediately POSTs /api/v1/payments/stk-push/buyer with returned orderId and phoneNumber. It does not call Safaricom directly. CustomerMessage comes from darajaResponse when present. There is no second explicit STK confirmation in this path. Cash on delivery is also offered in UI, without this proving current backend/business support.
6. MainActivity clears the local cart after receiving an order ID, even if STK initiation failed; it routes either to paymentStatus/paymentId/orderId or an order confirmation when payment ID is absent. It reloads Buyer order history.
7. PaymentStatusScreen calls GET /api/v1/payments/{paymentId}/status once on entry and on a manual Check payment status button. An imported delay symbol is unused; **there is no automatic polling loop in this screen**. All status text is colored green, and the refresh-button terminal comparison uses COMPLETED/SUCCESS rather than the deployed paid/held/settled enum. This does not mean it fabricates a successful API response; it means result/recovery presentation should not be copied as authoritative state handling.

This is a real implemented happy-path sequence and can explain the teammate's report of successful payment. It is not evidence that local and server carts match, nor that this current deployed backend accepts its older quote shape. We did not run that transaction and do not claim the report is false or independently proven.

Reference evidence: [cart upload and checkout](https://github.com/brianmuigai2-stack/Kilivana-buyer/blob/8f335e23f0d29c2953fe6373b7ff6f2da9e371e6/app/src/main/java/com/example/kilivana_buyer/data/Repositories.kt#L424), [order and STK orchestration](https://github.com/brianmuigai2-stack/Kilivana-buyer/blob/8f335e23f0d29c2953fe6373b7ff6f2da9e371e6/app/src/main/java/com/example/kilivana_buyer/data/AppViewModels.kt#L109), [payment status screen](https://github.com/brianmuigai2-stack/Kilivana-buyer/blob/8f335e23f0d29c2953fe6373b7ff6f2da9e371e6/app/src/main/java/com/example/kilivana_buyer/ui/screens/OrderConfirmationScreen.kt#L37).

## 5. Live contract comparison and unresolved differences

Fresh read-only GET /api-docs returned HTTP 200. SHA-256: 3d163c90a8bb8fafb421b9b1d4176940a48b7c05ebcac8e4c850c18caa0bc521. No authenticated or transactional API operation was executed. Ignored capture: app/build/buyer-reference-comparison-openapi.json.

| Area | Deployed contract | Reference behavior / conclusion |
| --- | --- | --- |
| Register | Same required name/email/phone/password/role fields | Compatible field names; latest OTP changes do not supply a different uniqueness/rate-limit contract |
| Cart add | POST /cart/items with productId/quantity exists | Correct advertised add route, but repeated-add vs replacement semantics and safe reconciliation remain unresolved |
| Cart summary | GET /carts/buyer/{buyerId}, CartResponse advertises id/buyerId/status/createdAt/updatedAt | Reference has no alternative authoritative cart-line GET/parser. Existing Samsung diagnostic also saw metadata only. The new reference does not resolve this blocker |
| Start checkout | POST /checkout returns void envelope | Reference uses this; missing line verification is not solved by the 200 result |
| Quote | Required deliveryMethod, mutually applicable addressId or pickupStationId; decimal totals/expiry | Reference address-only payload differs; FarmApp's existing method-specific DTO is already closer to current contract. Pickup APIs/quote selection are absent in reference |
| Order | quoteId required, phoneNumber optional; wrapped Long response | Adapt exact wrapped Long response, not speculative parser fallbacks; OpenAPI exposes no order idempotency parameters |
| STK | orderId required, optional Kenyan phone; paymentId/orderId/amount/status response | Same real Buyer endpoint; reuse FarmApp's existing client/controller and explicit confirmation |
| Status | GET /payments/{id}/status; pending/paid/held/settled/failed/refunded | Preserve FarmApp's exact status mapping; unknown/cancelled backend states stay unverified pending Daniel's clarification |

The fixed Door 150/Pickup 50 rules have not been proven by actual quotes, and service charge 30 is not supported by a quote field. No quote/checkout was requested solely to inspect fees. Reference code is not the authority for live backend behavior; an older accepted happy path may differ from the newly deployed request schema.

## 6. Additional source risks relevant to selective integration

- Partial cart upload followed by timeout leaves checkoutLocked false. Repeating Calculate delivery can resend already-added lines. Changing local cart after checkoutLocked true does not inherently reset/verify the processing cart. Lock and checkout progress are in-memory, not a durable idempotency journal.
- submitCheckout sets a UI busy flag but lacks a repository atomic/durable reservation and does not persist an unknown order attempt before POST. Timeout/restart can lose order/payment identity or permit an uncertain new attempt. Do not import this behavior.
- Reference GET /products has no sellerType=FARMER or page traversal; it expects data array. It categorizes stock but does not preserve a numeric maximum quantity in Product. Cart increments have no available-stock cap. FarmApp must preserve FARMER filtering, SUPPLIER supplies isolation, pagination and stock checks.
- Reference order mapping drops any order line whose product is absent from the current catalog and uses current catalog prices. Preserve FarmApp's order-time line names/quantities/unitPrice/subtotal and neutral image placeholders; discontinued products must remain visible in historical orders.
- Reference guest cart is not Buyer-account scoped, and Saved Items IDs are memory-only. Preserve FarmApp's existing account/origin-scoped persistent cart and Saved Items, with no deletion/migration of user data.
- Reference address listing tolerates data.content as well as a data array, silently drops some invalid rows and substitutes zero coordinates. Its save sends userId and its editor can require Geocoder/manual coordinates. FarmApp must retain verified principal ownership, optional actual coordinates and selected location; add a new wrapper only if live evidence supports it.
- Reference standalone OrderDetailScreen offers reorder, not FarmApp's guarded existing-order payment entry. Replacing that screen would regress the already working navigation.

## 7. Proposed import/adapt/preserve decisions

| Reference component | Decision | FarmApp destination / boundary |
| --- | --- | --- |
| Checkout journey and order/payment handoff | Adapt orchestration concepts after contracts are confirmed | Existing buyer/BuyerCheckout.kt plus a focused reconciliation/journal helper, using ApiClient |
| CheckoutOrder numeric response | Adapt as typed wrapped Long | Existing canonical envelope and a BuyerCheckoutOrderRequest DTO |
| STK/status endpoint knowledge | Preserve existing equivalent implementation; no second payment stack | orders/BuyerOrders.kt and BuyerPaymentController |
| Latest OTP continuation UX | Compare/adapt only missing UX, if approved | Existing SignUpScreen/AuthViewModel/email-verification flow; keep encryption, cooldown and single-flight |
| MainActivity/NavHost/standalone app module | Do not import | Existing AppNavigation and marketplace/BuyerShell remain authoritative |
| Repositories.kt/AppViewModels.kt monoliths | Do not wholesale copy | Existing typed services/repositories/controllers remain single owners |
| Product/search presentation | Selectively adapt only agreed gaps | Existing BuyerScreens/Marketplace; preserve recent reference Orders design and cart controls |
| SharedComponents image handling and packaged produce assets | Reuse existing FarmApp image path; no new dependency or fake product photos | Existing ProductImage and verified backend image URLs; check credits if any later asset is explicitly approved |
| Auth preferences, guest-cart persistence, Double/Int money | Do not import | Existing encrypted/scoped persistence and BigDecimal |
| Address edit/delete and reorder | Optional follow-up, separate scope/approval | Do not expand writes while fixing checkout; preserve current address/pickup functionality |
| Pickup Stations | Preserve FarmApp, absent from reference | Existing BuyerPickupStations/BuyerPickupScreens and method-specific quote DTOs |
| Debug cart diagnostic and existing-payment recovery | Preserve | No automatic diagnostics, no token export, no unapproved live writes |

No copying of reference Git metadata, .idea/.kotlin/build outputs, Gradle wrapper/toolchain, manifest, application ID, default temporary tunnel, token stores or root theme is proposed.

## 8. Approval-first implementation phases and likely files

**A ? Contract-backed transport and reconciliation (after plan approval and Daniel's response).** Confirm readable server line schema, duplicate/add/update semantics and idempotency/reconciliation for quote-to-order timeout. Add typed cart line/write/order DTOs and Retrofit methods in buyer/BuyerCheckout.kt or a small adjacent BuyerCartApi.kt; add BuyerCartSync.kt only as orchestration around the existing local cart, not a second state store. Add an account/origin-scoped pending-operation journal to BuyerPersistence.kt. Preserve local cart on failure. Re-read server cart and require exact intended product/quantity equality before checkout. Stop on server-only lines or unknown writes; no silent deletion or blind additive retry.

**B ? Complete the existing checkout UI state machine.** Extend BuyerCheckoutReviewController/BuyerShoppingModel with versioned reviewed snapshot, verified processing state, method/location-bound quote and expiry, explicit order confirmation, persisted order ID and recoverable pending order attempt. Wire BuyerDeliveryScreens and BuyerShell to these states. Use actual backend totals; invalidate quote on cart/location/method changes. Do not put client-only service fee into payable amount.

**C ? Reuse M-Pesa and recovery.** Pass the verified created order ID to the existing BuyerPaymentController/page. Maintain a separate explicit STK confirmation, server-amount recheck, Kenyan phone validation, durable attempt reservation and status polling. Persist/recover order/payment IDs; reconcile uncertain outcomes before retrying. Paid UI still requires backend confirmation. No automatic live transaction is authorized by approving Android code integration.

**D ? Targeted authentication alignment, only if a concrete gap remains.** Keep working login and registration/error/rate-limit fixes. Add only necessary OTP/return-navigation differences from current master, with mocked single-submission/verification/recovery tests. Do not rewrite shared auth for other roles or create test accounts.

**E ? Validation and review.** Mock absent/mismatched/duplicate server lines, partial writes, timeouts, changed session/cart, both delivery methods, quote expiry, order idempotency/recovery, one explicit STK, pending/failed/unknown/confirmed result and restart. Run all unit tests, lint, both APK builds and focused synthetic Samsung Buyer/auth tests. Install -r with unchanged package/data. Live smoke checks remain read-only unless separate exact order/payment approval is granted.

Proposed source scope: buyer/BuyerCheckout.kt, a focused buyer/BuyerCartSync.kt (and DTO/API file if warranted), BuyerPersistence.kt, BuyerDeliveryScreens.kt, marketplace/BuyerShell.kt, existing orders/BuyerPayment.kt only for created-order handoff/recovery. Shared AuthViewModel/CanonicalAuthService edits only if a specific tested OTP discrepancy requires them. Add related buyer/orders/network unit/UI tests and documentation. No other role source changes are proposed.

The phases may be validated together when tightly coupled; no commits will be made without separate explicit authorization.

## 9. What Daniel must confirm, and readiness

1. Which authenticated read returns complete cart lines/quantities/prices, including an explicit empty list? Current schema and Samsung diagnostic do not expose them. The reference supplies no different endpoint.
2. Whether POST adds/increments/replaces an existing product, how duplicates are represented, and whether absolute-quantity PUT and processing-cart recovery are supported for Buyers.
3. Idempotency or quoteId-to-order lookup for a lost order-creation response. We cannot substitute an uncertain order-list guess or repeat an unknown POST.
4. Whether the teammate's successful run used an older quote request or a server default; current spec requires deliveryMethod. Confirm current Door/Pickup quote behavior without inventing fees.
5. M-Pesa sandbox/live configuration, callback reachability and cancellation/reconciliation semantics. Any live test needs separate approval for amount/phone/expected transaction.

Existing eligible-order M-Pesa in FarmApp remains the shortest separately approved demonstration route, conditional on an eligible owned order and confirmed backend environment. No eligible order or real STK outcome was established by this source comparison. Full new-cart checkout remains blocked until authoritative reconciliation and safe order recovery can be implemented; copying a blind upload loop would conceal, not solve, that gap.

## 10. Validation and change status for this assessment

No Android source, Gradle, manifest, application ID, installed APK or device state changed during this assessment. No new build/test/installation was needed or performed for a documentation-only plan. FarmApp's latest prior validation (387 unit passes and focused Samsung synthetic suites) is historical evidence for its current implementation, not evidence that the reference master or future integration has passed those tests. The reference app was not built/installed and no payment/checkout/account flow was run. The reference's two template tests do not establish transactional safety or runtime compatibility.

Main working tree remains on feature/supplier with its existing remote and all prior changes. Reference tree remains clean on master after a metadata-only fetch. The only planned FarmApp modifications in this turn are this report and a pointer from the historical plan. **Await explicit approval before major integration.**
