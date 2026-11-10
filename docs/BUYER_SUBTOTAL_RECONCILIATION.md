# Buyer subtotal reconciliation — 10 November 2026

## Exact observed discrepancy

One explicit diagnostic quote was requested using the persisted Door Delivery selection. No cart preparation, cart upload, quantity update, order or payment was repeated. The probe requires the genuine Buyer session, expected API origin, CHECKOUT_ACKNOWLEDGED phase and explicit allowQuoteProbe argument. It uses a client with retries/redirects disabled and verifies the selected address ownership by GET before its single quote request.

| Diagnostic | Confirmed value |
|---|---|
| Selected Android entries | 1 |
| Distinct Android product IDs | 1 |
| Selected total quantity | 1 |
| Persisted Android subtotal | KES 1.00 |
| Fresh GET product subtotal | KES 1.00 |
| Backend quote subtotal | KES 0.07 |
| Server minus fresh Android subtotal | KES -0.93 |
| Delivery fee, separately returned | KES 1.00 |
| Quote HTTP status | 200 |
| Resumed phase | CHECKOUT_ACKNOWLEDGED |

Evidence is `subtotal-read-probe.log` for GET-only reads and `subtotal-quote-probe.log` for the single quote. Both probes passed 1/1. Only numeric aggregate diagnostics, booleans, HTTP status and phase were emitted. No tokens, personal addresses, phone numbers, product identifiers or payment details were logged.

## Confirmed cause and limits

The server quote is inconsistent with the selected basket calculated from current product data. Stale current product prices are not supported as the cause in this observed case: both current product read and journal equal KES 1.00. Delivery fee is a separate field and is not included in either subtotal comparison. The difference is exact decimal arithmetic, not display rounding. The Android map has unique IDs, so duplicate Android entries are not the cause.

It is not possible to identify the backend's individual wrong line, quantity, product selection or pricing rule from the exposed response. The quote does not identify its cart or priced lines. The cart response does not expose lines or quantities. Duplicate backend lines, a different processing cart, different backend quantities or undocumented server adjustments remain UNKNOWN. No unsupported line data was invented.

The saved phase confirms a prior checkout acknowledgement. It does not prove the current processing-cart contents or individually reconstruct historical quantity acknowledgements. The original journal predates parts of the newer exact-quantity preparation implementation. This diagnosis did not resend any of those writes.

## Android defects corrected

`checkout/CheckoutController.kt`, refreshQuote, compares quote.subtotal with journal.expectedSubtotal using BigDecimal.compareTo. The comparison remains mandatory. A mismatch still prevents publishing a usable quote, so createOrder cannot proceed.

Two concrete Android issues were corrected.

1. Acknowledged checkout recovery reused its persisted subtotal while ignoring newer price evidence. Preparation now checks the saved basket identity before refreshing and preserves the basket/cart identity while replacing only its price expectation with validated current catalogue reads. It does not restart cart preparation.
2. The mismatch message previously discarded all safe numeric context. It now displays Android subtotal, server subtotal, exact signed difference, selected product count and quantity. It never adopts the mismatching server amount.

Production Buyer preparation obtains fresh prices through the existing MarketplaceRepository.detail -> authenticated GET products/{id} for each selected ID. Active Farmer-product eligibility, minimum/stock quantity, product ID, non-self seller and live session identity are validated. BigDecimal unit price multiplied by integer quantity forms the exact refreshed subtotal. Failed reads stop preparation rather than silently using stale prices. Fresh reads precede any new cart writes.

Unknown upload/quantity/preparation/order phases remain protected and cannot trigger this reconciliation. A mismatched acknowledged item map is rejected before even refreshing prices. Explicit re-quoting retains existing order-idempotency and pending-payment safeguards. The production Buyer model always supplies the catalogue adapter. Other pre-existing adapters and synthetic fixtures retain their optional existing subtotal behavior for compatibility.

This Android correction does not resolve the observed KES 0.07 backend quote: fresh prices still produce KES 1.00. It improves correct reconciliation when catalogue prices actually change and produces actionable evidence when server cart reconciliation is impossible.

## Contracts verified

The deployed `/api-docs` returned HTTP 200. Current CartResponse fields are only id, buyerId, status, createdAt and updatedAt. There are no items, quantities, price snapshots or totals.

- GET products/{id} supplies product price/eligibility through the existing catalogue DTO.
- GET cart returns metadata and can create state, so it was not used as a harmless line inspection.
- POST cart/items accepts productId int64 and quantity int32.
- PUT cart/items/{itemId} requires a line ID unavailable in CartResponse. Android does not invent that mapping.
- The existing documented PUT carts/{cartId}/items/{productId} remains the acknowledged exact-quantity preparation operation. It was not repeated during diagnosis.
- POST checkout prepares the cart. It was not repeated.
- DeliveryQuoteRequest fields remain deliveryMethod, addressId, pickupStationId and pickupLocation. There is no cart reference or item list to pin/reconcile a processing cart through this request.

## Exact backend handoff

For the same authenticated Buyer and Door Delivery selection, GET selected-product price times selected quantity gives KES 1.00, but POST checkout/delivery-quote returns HTTP 200 with subtotal KES 0.07 and deliveryFee KES 1.00. No error DTO is returned: this is a successful but inconsistent quote response.

The backend developer must inspect which owned CHECKOUT_PROCESSING cart and priced lines were selected and compare them with the acknowledged preparation/cart identity. The canonical Java CheckoutService uses findFirstByBuyerIdAndStatus for processing-cart lookup; an older processing cart is a possible explanation, not a verified diagnosis of deployment.

Smallest compatible contract evidence required is an additive cartId and priced items on the existing quote response, with productId, quantity, unitPrice and lineSubtotal for the exact quoted cart. Authoritative owned processing-cart line readback is also needed to inspect/correct quantities safely. Existing IDs/fields must remain backward compatible. A reviewed reconciliation operation must distinguish acknowledged from uncertain writes and avoid touching an unknown submitted order. Without this evidence Android cannot safely select/remove/rewrite unknown lines, prove a duplicated backend entry or manufacture a matching basket.

## Exact Kotlin changes

Production paths are under `app/src/main/java/com/example/farmapp/`.

- `checkout/CheckoutRepository.kt` — optional refreshedSubtotal capability for compatible adapters.
- `checkout/CheckoutController.kt` — guarded price refresh, preserved basket identity, updated expectation for ACK recovery, exact numeric mismatch exception while retaining the comparison.
- `buyers/BuyerCheckoutRepository.kt` — current authenticated product reads and exact decimal aggregation.
- `buyers/BuyerCheckoutController.kt` — injects the normal MarketplaceRepository into the production Buyer adapter.

Tests and explicit diagnostics

- `app/src/test/java/com/example/farmapp/checkout/CheckoutControllerTest.kt`
- `app/src/test/java/com/example/farmapp/checkout/CheckoutRepositoryContractTest.kt`
- `app/src/androidTest/java/com/example/farmapp/buyers/QuoteReadOnlyDiagnosticsTest.kt`
- `app/src/androidTest/java/com/example/farmapp/buyers/SubtotalQuoteDiagnosticsTest.kt`

The quote probe is credential-dependent and additionally refuses to run without `allowQuoteProbe=true`. It is not part of normal independent/mock suites. The single live quote produced no cart mutation, order or financial transaction and did not alter the local checkout journal.

## Verification

Build uses process-local Android Studio JBR and explicit current HTTPS canonical/debug URL properties. Command is testDebugUnitTest, assembleDebug and assembleDebugAndroidTest with offline/console=plain. Evidence is `subtotal-reconcile-final-build.log`.

New regressions cover fresh-price reconciliation of ACK state without repeated preparation, mismatched journal identity before reads, an unreconcilable higher subtotal, a lower subtotal that cannot be adopted, and exact authenticated GET price aggregation (3 times KES 0.07 equals KES 0.21). Existing suites cover matching totals, invalid/mismatched order quantities/duplicates, decimal precision and protected unknown outcomes. Duplicate server entries cannot be directly asserted through a metadata-only contract; mismatching totals are blocked regardless of their source.

Final test counts and installation checkpoint follow below. No backend code, SQL, configuration or production database was edited. No cart writes, orders or payment attempts were initiated during diagnosis. Existing uncommitted work and the actual checkout journal remain preserved. No commit or push occurred.
## Final checkpoint

Compilation and both APK builds succeeded. JVM tests passed **654/654**, zero failures/errors. Selected mocked device regressions passed **50/50**, log `subtotal-reconcile-device.log` ending OK (50 tests). Explicit GET-only and single-quote diagnostics passed separately, one case each. The live quote probe is excluded from normal selected/independent suites and was not rerun during the 50 mocked regressions.

The latest app and test APKs installed successfully using `adb install -r`, retaining data. Package is `com.example.farmapp.dev2231918adc4d4b9d`. MainActivity was relaunched and temporary stay-awake configuration restored to 0. Build/test logs are `subtotal-reconcile-final-build.log`, `subtotal-read-probe.log`, `subtotal-quote-probe.log` and `subtotal-reconcile-device.log`.

An initial compilation encountered an optional-constructor argument placement problem for existing synthetic clock callers; the final signature preserves their trailing clock lambdas and all current callers compile. No assertions were removed to obtain the passing result.

The actual backend discrepancy remains blocked. Exact backend subtotal KES 0.07 and current Android subtotal KES 1.00 are confirmed; the specific server line/quantity/cart selection causing it remains unknowable from the current metadata-only APIs. No lower server total was adopted, cart rewritten, journal cleared, order submitted or payment initiated. One non-financial diagnostic quote was created under this task's request to capture the mismatch. No backend code or database administration changes, commits or pushes occurred.