# Backend/Android source investigation of KES 0.93 discrepancy

## Findings

The audited Java arithmetic cannot turn a current unit price of KES 1.00 and integer quantity 1 into KES 0.07. The exact line is `product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))`, followed by subtotal addition. Delivery is added afterward. No division, scale conversion, rounding, discount, percentage, tax or unit-conversion operation appears in the quote calculation. priceSnapshot is not read by that calculation.

A confirmed source-level weakness can select another processing cart: preparation transitions one cart, while quote creation independently calls an unordered findFirstByBuyerIdAndStatus for CHECKOUT_PROCESSING. The repository explicitly describes this as returning any one cart when duplicates exist. There is no quoted cart identity or priced line readback in the deployed response. This is a reproducible explanation, not proof of the live database cause.

The deployed quote's actual product identity, quantity and cart remain UNKNOWN. It is therefore not possible to establish conclusively which live record produced KES 0.07 under the permitted access. No production credentials/database or live cart/quote/order/payment writes were used in this investigation.

## Repository/revision evidence

- Android branch `feature/supplier`, HEAD `8255cf9a38f5c0f2d8fdffc06491dabaa641d29a`, with existing uncommitted implementation preserved.
- Backend audit directory `C:\Users\AppleFi\AndroidStudioProjects\kilivana-backend-audit`, clean branch `main`, HEAD `2270f4719f915c3186e724961da7cbc2e21dc8d1`.
- Read-only `git ls-remote origin refs/heads/main` returned the same backend revision. Origin is `https://github.com/danielkamweru/Kilivana-Backend.git`.
- No backend file or Git checkout was modified. No fetch, reset, branch switch, commit or push was performed.
- Current deployed `/api-docs` returned HTTP 200. Its cart/quote response fields agree with the audited response DTO structure, including the absence of cart lines. OpenAPI carries no verified running Git SHA. Schema compatibility cannot establish that 2270f47 is the running binary or that its migrations were applied.
- This audit is newer than the previous a8205b0 baseline and includes durable CheckoutQuote persistence and cart stock locking changes. Those changes were inspected as current source, rather than assuming the previous local implementation was deployed.

## Exact calculation and data flow

Java paths below are relative to the backend audit root.

1. `ecommerce/controller/CheckoutController.java:21-29` maps `/api/v1/checkout/delivery-quote`, validates DeliveryQuoteRequest and delegates to CheckoutService.createDeliveryQuote.
2. `ecommerce/service/CheckoutService.java:49` reads CurrentUser identity and validates the delivery method/address/station. Address and pickup affect delivery selection/fee, not product subtotal.
3. Line 101 selects `findFirstByBuyerIdAndStatus(userId, CHECKOUT_PROCESSING)`. It does not take the Android journal cart ID as a request argument and does not assert that only one processing cart exists.
4. Lines 103-113 obtain cart items through CartItemRepository.findByCartId, resolve each item's productId through ProductRepository.findById, and sum current Product.price times stored CartItem.quantity.
5. Line 115 adds deliveryFee to subtotal to form total. Response construction around lines 127-137 puts subtotal and deliveryFee into distinct fields. Returned KES 0.07 is thus a product subtotal, not a KES 1 delivery subtraction in this source.
6. ProductController.getProductById at lines 104-105 calls ProductService.getProductById (line 132), which resolves ProductRepository.findById and maps Product.price directly to ProductResponse.price at line 613. Catalogue and quote use the same entity price field for the same ID at the same instant. They are separate reads, so concurrent price changes remain possible.
7. CartService.getCartByBuyer line 41 selects ACTIVE or creates it. CartCompatibilityController.checkout lines 173-175 obtains that ACTIVE cart and passes its concrete ID to CartService.checkout, line 173. Checkout locks and transitions that specific cart at line 186.
8. CartService.addItemToCart lines 64-94 gets the active cart, locks the selected product, validates stock, increments an existing line with Math.addExact or inserts the requested integer quantity. It records productId, quantity and current priceSnapshot. updateCartItem line 128 sets the exact requested quantity in the specified cart/product line and refreshes priceSnapshot. Neither operation reconciles a different CHECKOUT_PROCESSING cart later selected by quote creation.
9. CartRepository.java:36 declares findFirstByBuyerIdAndStatus with no ordering, uniqueness check or explicit cart ID. The audited migration search found no unique CHECKOUT_PROCESSING-cart index. Source annotations/migration files do not establish deployed database constraint state.

Entity Product.price is BigDecimal (`entity/Product.java:58`), CartItem.quantity is Integer and CartItem.priceSnapshot is BigDecimal (`entity/CartItem.java:40-43`). ProductResponse.price and DeliveryQuoteResponse monetary fields are BigDecimal. No custom price scaling serializer appears on those fields. Actual deployed database column types, external triggers or binary differences remain unverified.

## Android comparison

Kotlin paths are under `app/src/main/java/com/example/farmapp`.

- `network/canonical/CanonicalFarmerMapper.kt:77`, toFarmerProduct, maps the canonical numeric product price to the domain BigDecimal unit price. It retains product identity.
- `buyer/BuyerShopping.kt:43` calculates line subtotal as unitPrice multiplied by validated integer quantity. The selected basket is a Long product-ID to Int quantity map, so the selected map itself cannot contain duplicate keys.
- `buyers/BuyerCheckoutRepository.kt:46-57`, refreshedSubtotal, reads each exact selected product ID using MarketplaceRepository.detail, validates identity, active Farmer-product eligibility and quantity, and aggregates exact BigDecimal amounts. It does not include delivery fees.
- BuyerCheckoutRepository's Retrofit methods use POST cart/items with Long productId/Int quantity, then PUT carts/{cartId}/items/{productId}?quantity= for exact preparation quantities. Session tags and bearer identity remain guarded.
- `checkout/CheckoutController.kt:76` preserves the acknowledged journal's item map. A different item map cannot resume it. Quote creation sends delivery selection only. `refreshQuote` compares backend subtotal with the refreshed journal expectation using BigDecimal.compareTo and blocks order submission when different.
- The confirmed selected-product GET and diagnostic quote returned KES 1.00 and KES 0.07 respectively in the previous investigation. The current source explains the arithmetic for each endpoint but the quote response cannot establish that both used the same product ID/cart. Android product identity is verified for its GET; identity equivalence with the product priced by the quote remains UNKNOWN.

## Cause matrix

| Suspected cause | Java source | Kotlin source | Evidence | Status | Proposed correction | Team |
|---|---|---|---|---|---|---|
| Different/older processing cart selected | CheckoutService:101; CartRepository:36; CartService:173-186 | CheckoutController.prepare; journal.cartId; BuyerCheckoutRepository Retrofit quote | Specific preparation cart versus independent unordered first-cart selection; synthetic model reproduces 1.00 vs 0.07 | Source weakness CONFIRMED; deployed occurrence UNCONFIRMED | Reject ambiguous processing-cart sets, expose exact quoted cart/lines, bind quote to acknowledged cart | Backend |
| Different product ID or stored quantity in quoted cart | CheckoutService:103-113; CartItemRepository:13 | refreshedSubtotal:46-57 | Android reads one selected ID/quantity, while quote reads server cart lines that are absent from API response | UNCONFIRMED live | Add owned quoted-cart identity and priced-line evidence before attempting correction | Backend |
| Stale CartItem.priceSnapshot | CartItem:43; CartService.add/update; CheckoutService:112 | BuyerShopping:43 | Quote ignores snapshot and resolves Product.price | Ruled out as a standalone explanation in audited source | No snapshot-based Android workaround | Backend verification |
| Current selected catalogue price stale | ProductService:132,613; CheckoutService:112 | BuyerCheckoutRepository:46-57 | Confirmed fresh subtotal equals saved subtotal 1.00 | Not supported for observed selected product; concurrent price change still UNCONFIRMED | Trace per-product price and quote timestamp/transaction evidence | Backend |
| Duplicate backend lines | CartItemRepository.findByCartId; CheckoutService loop | Selected map unique IDs; order readback validators | Duplicating two positive 1.00 lines yields 2.00, not 0.07; other underlying prices could differ | UNCONFIRMED | Read exact lines, review uniqueness and reconcile existing duplicates before constraints | Backend |
| Decimal scaling/integer division/rounding | CheckoutService:108-115; Product/DTO BigDecimal fields | Exact BigDecimal multiplication/compareTo | No scaling/division in audited calculation; synthetic arithmetic remains exact | No such transformation found in source; deployed binary/schema unknown | Verify deployed build and schema, without changing correct Android arithmetic | Backend |
| Delivery fee mixed into subtotal | CheckoutService:115 and response builder | CheckoutQuote validation / subtotal comparison | Fee 1.00 returned separately; total=sum(subtotal,fee) | Ruled out in audited source | Keep separate fields and existing checks | Neither Android change nor fee subtraction justified |
| Discount/tax/unit conversion | Same calculation and DTOs | BuyerShopping:43, refreshedSubtotal | None in inspected quote path or documented request/response adjustment fields | No source evidence; external deployed behavior UNCONFIRMED | Publish explicit adjustment breakdown if deployment adds such logic | Backend |
| Android wrong ownership/session | CurrentUser and cart buyer filter | CheckoutSessionGuard and authenticated Retrofit tags | Prior genuine-session reads succeed; backend quote filters same authenticated user but may select another cart of that user | No evidence of cross-user confusion | Retain ownership safeguards; expose cart ID to distinguish same-user baskets | Both, safeguards retained |

## Synthetic reproduction

`docs/reproductions/CheckoutSubtotalReproduction.java` is a standalone Java source-file program in the Android documentation workspace. It models the exact audited multiplication/addition and the first-matching-cart ambiguity, using synthetic products/carts only. It is not a Spring/JPA integration test and does not claim to reproduce database ordering or deployed rows.

Executed command

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' docs/reproductions/CheckoutSubtotalReproduction.java
```

All five checks passed (`subtotal-source-reproduction.log`).

- Current price 1.00, quantity 1 and stale snapshot 0.07 still yield 1.00.
- A first processing cart containing a different product priced 0.07 yields 0.07 while the intended cart yields 1.00.
- A different product priced 0.01 with quantity 7 also yields 0.07.
- Two 1.00 lines yield 2.00, demonstrating that duplication of this selected 1.00 line alone does not explain the lower subtotal.
- Delivery 1.00 changes the 0.07 subtotal to total 1.07 without altering subtotal.

These fixtures demonstrate possible causes and eliminate a snapshot-only arithmetic explanation for this source. They are not evidence that the deployed cart contains either synthetic product/quantity.

## Minimal suggested Java change — proposal only

Do not choose newest or oldest processing cart as a silent fix. That would still risk selecting a different unresolved purchase. A minimal backward-compatible fail-closed patch replaces arbitrary first-cart selection with an exact-one requirement using the existing repository/service path.

```java
// Proposed CartRepository declaration
java.util.List<Cart> findAllByBuyerIdAndStatus(Long buyerId, Cart.CartStatus status);

// Proposed CheckoutService.createDeliveryQuote selection
var processing = cartRepository.findAllByBuyerIdAndStatus(
        userId, Cart.CartStatus.CHECKOUT_PROCESSING);
if (processing.isEmpty()) {
    throw new BadRequestException("No checkout in progress; call POST /api/v1/checkout first");
}
if (processing.size() != 1) {
    throw new BadRequestException("Multiple processing carts require reconciliation");
}
Cart cart = processing.get(0);
```

This preserves the legacy one-processing-cart flow and prevents arbitrary selection when duplicates exist. It does not resolve a single incorrect cart, establish the live cause, repair data or replace the need for an explicit owned cart binding. Before any uniqueness migration, the backend owner must inventory/reconcile existing pending carts and orders and serialize new checkout transitions. No migration was executed here.

Additive response evidence is needed on the existing quote: cartId plus priced lines containing productId, quantity, unitPrice and lineSubtotal. The cart metadata GET currently exposes ACTIVE state and may create a cart. It cannot inspect the processing basket's items. Provide owned read-only processing-cart evidence through existing contracts, and bind any explicit cart reference to the authenticated principal. Preserve legacy clients, with the exact-one fallback only where unambiguous. Do not blindly clear, replace or delete unresolved carts.

Required backend regression tests should cover two processing carts, explicit foreign cart rejection, intended-cart identity, unchanged one-cart Buyer compatibility, current price versus snapshot behavior, exact decimal arithmetic, concurrent preparation and the exact returned line-sum identity. Deployment verification must identify the running SHA and inspect the real selected cart/line/price evidence using owner-authorized redacted diagnostics.

## Outcome and restrictions

No new confirmed Android defect was found in this audit, so no Android production or test source was changed. Existing subtotal validation remains mandatory. Only this report, a standalone synthetic reproduction artifact and its output were added. Backend working tree remained clean before and after inspection. Existing Android uncommitted changes were preserved.

No production database connection, credentials, cart write, quote request, order, payment or deployment operation occurred. The numeric device evidence is reused from the prior confirmed investigation. The final live root cause remains unproven until the backend owner identifies the cart and priced lines used by the KES 0.07 quote. The precise handoff is the ambiguous-cart selection path and missing read-only evidence, rather than a guessed monetary conversion.