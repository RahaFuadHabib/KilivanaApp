# Buyer Orders and M-Pesa reference implementation

29 October 2026. Visual target inspected directly: `design-reference/Order Kilivana.png` (the only image in the folder, 1,601,458 bytes). All existing uncommitted work was preserved. No live checkout, cart mutation, order, payment, STK or backend write was performed.

## Implemented screens

- **My Orders:** deep-green app bar/back action, white background, horizontally scrollable All/Pending/Processing/Delivered chips, rounded lightly elevated cards, readable dates, status badges, horizontal neutral thumbnail placeholders, actual line count/total, chevron, full-width eligible payment button or outlined View Details. Existing Buyer bottom-navigation destinations/icons are retained, with white/green styling scoped to Orders.
- **Unpaid details:** white back header, pending/status badge and readable date, rounded Order Items, Delivery Information and Order Summary sections. Items show actual name/unit/quantity/price and backend line subtotal, or precise unit-price ? quantity fallback. A fixed bottom Pay with M-Pesa action is visible only after the existing controller verifies eligibility. Body content scrolls independently.
- **M-Pesa:** separate local screen with back navigation to details, prominent backend order total, payment badge, green instruction card, Kenyan-phone input/validation and full-width Send M-Pesa Prompt action, plus security card. Sending still requires the existing separate Confirm STK Push dialog. Pending, failed, refunded, unknown and network-error notices retain the existing controller semantics and refresh action.
- **Paid details:** green confirmation panel, Paid badge/date, same actual items/delivery/summary, and bottom confirmation card. ?Payment completed via M-Pesa? requires confirmed payment and an actual M-Pesa method. Whole-order success requires confirmed backend order payment status or sufficient confirmed payment records; a partial payment must not mark the whole order paid.

## Data and safeguards

BuyerOrderDto now maps verified OrderResponse fields subtotal, deliveryFee, deliveryAddress, paymentMethod and paidAt. BuyerOrderItemDto maps subtotal. Existing constructors remain source-compatible through appended optional fields. Values absent from backend show a neutral unavailable state, not invented fees/addresses. Order item schema has no image URL; placeholders are intentional. No catalog lookup substitutes a current product image for an order-time image.

The same BuyerPaymentController handles list eligibility, details and payment, keyed by session generation and actual order ID. List card eligibility is checked through existing authenticated order/payment reads; visible payment actions are not inferred from an unpaid label alone. Navigation never submits STK. List eligibility adds read-only requests for visible cards. Existing phone normalization, explicit confirmation, backend-total recheck, durable pre-send reservation, busy guard, timeout/restart recovery and lifecycle-aware status polling remain. Existing Orders tracking, receipt confirmation, timeline and refresh are preserved below the reference sections.

Pending filter maps placed; Processing maps confirmed/in_transit; Delivered maps delivered/completed. All retains cancelled/disputed/other valid orders. Filtering is local to already retrieved owned orders and sends no mutations.

## Intentional differences from the image

Internal order IDs remain hidden, replacing ?Order #1005? with ?Order?/?Order Details?/?Order payment?. Product pictures use neutral inventory placeholders because the order API has no image fields. No invented ?Fresh, local?, home-address label, delivery-change action, fixed sample fee, or promised SMS/automatic order confirmation is shown. The delivery address is displayed exactly when available. Existing bottom navigation and its real labels are retained. The real device font/density/status bar and accessibility size can differ from the composite reference. Security/refresh/error controls and the separate payment confirmation remain even though the reference omits some of them.

## Navigation and remaining backend blocker

Cart ? delivery/address ? review still does not have a created order ID. Its disabled order-placement guard remains because authenticated cart response contains metadata only, not verified lines/quantities. Existing ?View existing orders? navigation is preserved. An existing eligible order can enter payment review without bypassing that guard. No new-cart checkout completion or live payment demonstration is claimed.

## Validation and visual inspection

Final full validation (`buyer-validation-orders-reference-navigation.log`): **380 unit tests passed**, zero failures; **lintDebug passed**, zero errors, 57 warnings/1 hint; **assembleDebug and assembleDebugAndroidTest passed**. Main and test APKs installed using adb install -r on RF8WC0K043H. Application ID remains com.example.farmapp.dev2231918adc4d4b9d; signed-in data was preserved.

The Samsung suite covers 2 new reference/small-screen tests, 3 payment safety/presentation tests, 1 order-formatting test and 6 delivery tests. An earlier combined run had one existing delivery-test activity-hierarchy failure; the full rerun passed all 12. On the final installed build the 6 Orders/payment/reference tests passed. The combined run had an intermittent existing delivery-test timing failure (review click had not yielded the expected review content). Running the unchanged delivery suite separately then passed **all 6 tests**. Thus each selected test passes on the final build, but combined-run delivery-test stability remains a test limitation; no delivery behavior was rewritten to hide it. Logs: buyer-validation-orders-reference-installed-samsung.log and buyer-validation-orders-delivery-isolated.log.

All four synthetic captures were inspected visually. Refinements reduced order-card spacing, made all four filters fit at 360 dp, compacted the paid confirmation panel, corrected security bullets and aligned Orders-only bottom navigation colors. The 320 ? 480 dp test verifies independently scrolling details and a visible bottom payment action. Screenshots are under app/build/buyer-order-visuals-final (and the latest refreshed captures under app/build/buyer-order-visuals/buyer-order-visuals). Summary and payment content remain scrollable rather than shrinking fonts or clipping at smaller device sizes. Visual captures contain synthetic data only and are stored under ignored app/build, never raw account screenshots or credentials. No app data clearing or uninstall is permitted. Nothing is staged, committed or pushed.

## Files

Main presentation: orders/BuyerOrderDesign.kt (new), orders/BuyerOrders.kt, orders/BuyerPayment.kt; existing shell back action: marketplace/BuyerShell.kt. Tests: BuyerOrderDesignTest.kt (new), BuyerOrdersReferencePresentationTest.kt (new), existing BuyerPaymentPresentationTest.kt updated for scrolling. Existing controllers and authenticated services are reused, with no new dependencies.

Final visual inspection confirmed white/green Orders navigation, unclipped chips at 360 dp, readable totals, compact paid confirmation and neutral placeholders. At 320 dp the existing horizontal chips/body scroll instead of clipping; extra tracking/refresh controls remain below the reference sections. No pixel-perfect match is claimed for system fonts/bars, missing product pictures or unsupported sample labels.
