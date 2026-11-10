# Buyer subtotal backend source audit — 10 November 2026

## Result

Backend audited at main commit `2270f4719f915c3186e724961da7cbc2e21dc8d1`; working tree was clean. Android already has extensive uncommitted work, which this investigation leaves untouched. No application source changes were justified by the evidence.

There is a concrete backend cart-selection defect capable of producing this discrepancy: delivery quoting selects an arbitrary processing cart rather than the cart acknowledged by checkout. This is a reproduced explanation, **not proof of the exact production incident**. The exposed contract cannot identify the quoted cart or its lines. The historical Android journal also does not prove all server preparation quantities. Production data or safe server diagnostics are required to close that gap.

## Java trace and calculation

`CheckoutController.deliveryQuote` maps POST `/api/v1/checkout/delivery-quote`, validates `DeliveryQuoteRequest`, and delegates to `CheckoutService.createDeliveryQuote`.

That method validates the authenticated user's delivery location, sets deliveryFee to `1.00`, calls `CartRepository.findFirstByBuyerIdAndStatus(userId, CHECKOUT_PROCESSING)`, reads `CartItemRepository.findByCartId(cart.id)`, loads each current Product by item.productId, and computes:

```java
BigDecimal line = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
subtotal = subtotal.add(line);
BigDecimal total = subtotal.add(deliveryFee);
```

No discount, exchange rate, cents conversion, division, or rounding occurs here. `priceSnapshot` is ignored. The computed values and selected cart ID are persisted in CheckoutQuote, but cart ID and priced lines are omitted from DeliveryQuoteResponse. `createOrderFromQuoteLocked` recalculates current prices again, which separately risks changing the reviewed amount if prices move after quoting.

`CartCompatibilityController.checkout` obtains the ACTIVE cart through `CartService.getCartByBuyer`, then `CartService.checkout(cartId)` marks it CHECKOUT_PROCESSING. Neither operation rejects an older processing cart. A later active-cart GET can create another ACTIVE cart. `findFirstByBuyerIdAndStatus` has no ordering or requested-cart predicate; its repository comment explicitly allows duplicates. Thus checkout A can be acknowledged while quote B is returned.

## Android trace and DTO comparison

MarketplaceRepository.detail calls authenticated GET `products/{id}`, checks the product identity, and maps CanonicalProductDto.price (BigDecimal) through toFarmerProduct to the marketplace unit price. BuyerCheckoutRepository.refreshedSubtotal validates the requested quantities and calculates `product.unitPrice.multiply(quantity.toBigDecimal())`. The observed fresh subtotal and saved subtotal are both 1.00.

CheckoutController.prepare reads cart metadata, uploads each product, then sets an exact quantity with PUT `carts/{cartId}/items/{productId}?quantity=...`, because POST add increments existing quantities. It checks buyer/cart metadata, starts checkout, then requests the quote. A previously acknowledged journal refreshes prices and quotes without replaying writes. This is appropriate for uncertain historical state.

Java and Kotlin quote requests have matching deliveryMethod, addressId, pickupStationId and pickupLocation fields; neither carries cartId or products. Java Long corresponds to Kotlin Long; money uses BigDecimal in both. Java Instant is received as an ISO timestamp String and parsed with Instant.parse. Kotlin intentionally ignores Java's additional pickupStation object. Quote monetary fields map by name with no conversion. CartUpload productId/quantity matches CartItemRequest. The quantity PUT uses product identity, not the legacy item-ID endpoint.

CartResponse only exposes id, buyerId, status and timestamps. Android cannot enumerate or remove unrelated stale lines through that response. Such retained positive-price lines can inflate the intended subtotal, but extra copies of the selected 1.00 product cannot alone lower it to 0.07. The original journal predates some exact-quantity protections, so historical quantities remain unknown. CheckoutController.refreshQuote compares BigDecimal values exactly and blocks the observed mismatch; keep that validation.

## Synthetic reproduction

Executed `java FarmApp/docs/reproductions/CheckoutSubtotalReproduction.java` successfully: five assertions passed. This is a standalone model of the source arithmetic and selection, not a Spring/JPA integration test and not a query against production.

| Fixture | Result |
|---|---:|
| Intended product price 1.00, quantity 1, stale snapshot 0.07 | 1.00 |
| Another processing cart with product price 0.07, quantity 1 selected | 0.07 |
| Different product price 0.01, quantity 7 | 0.07 |
| Duplicate selected 1.00 lines | 2.00 |
| Old cart subtotal 0.07 plus delivery 1.00 | total 1.07 |

At unchanged price 1.00, a valid positive integer quantity cannot yield 0.07. Rounding and delivery fees cannot explain the -0.93 difference. The selected backend inputs must differ, or deployed behavior/data must differ from the audited source.

## Deployment contract check

The saved `deployed-openapi-cart-checkout.json` agrees with this commit's relevant quote request/response fields and cart metadata contract. This establishes structural compatibility only, not deployed commit identity or implementation equivalence. A live GET of the saved deployment's `/v3/api-docs` was attempted through web access and directly with Invoke-RestMethod; it was unavailable, and direct access failed DNS resolution. Current deployed contract and deployed SHA therefore remain unverified. No authenticated writes were attempted.

## Precise proposed backend fix

1. Bind checkout preparation and quote to an explicit cart ID. Return the transitioned cart ID from POST checkout; add cartId to DeliveryQuoteRequest. In createDeliveryQuote, use `findByIdForUpdate(request.getCartId())`, require cart.buyerId equals CurrentUser.id and status equals CHECKOUT_PROCESSING, and load only that cart's lines. Persist that same identity in the quote. Do not replace the current lookup with newest/oldest ordering: that still cannot prove intent.
2. During compatibility rollout, legacy requests without cartId must resolve exactly one owned processing cart; return 409 when multiple exist rather than silently choosing one. Serialize preparation per buyer and explicitly handle an existing unresolved checkout. Do not auto-delete or abandon historical carts or unknown orders.
3. Return cartId plus priced items (productId, quantity, unitPrice, lineSubtotal) in the quote. Provide an owned cart line readback. These enable Android to verify the full basket, not merely a coincidentally equal aggregate. Follow with a minimal Android DTO/request change once that contract is available.
4. Add service/integration regressions with two processing carts, foreign buyer IDs, missing/nonprocessing carts, concurrent preparation, stale unrelated lines and decimal prices. The intended cart at 1.00 × 1 must quote 1.00 and total 2.00 regardless of an older 0.07 cart. Reject a missing/ambiguous identity.
5. At order creation, ensure the priced basket still matches the accepted quote; reject changed prices/quantities and require a fresh review instead of silently recalculating a different payable total.

For the incident, a backend developer should read the persisted diagnostic quote's cartId and its cart item product IDs/quantities and compare those with the Android acknowledged cart and selected product. Read current prices and, if available, historical prices at quote time. This distinguishes wrong cart, historical quantity, price change and deployment drift without guessing.

Backend modification is required to fix the demonstrated selection defect. No additional Android-only fix can safely select a hidden processing cart under the present contract. No backend files, production database, deployment, orders, payments, validation, commits or pushes were changed by this investigation.
