# M-Pesa / Daraja integration plan

## Current Android behavior

No STK request, charge, callback, payment-status poll or live transaction is initiated by this module. The Supplier Orders screen displays an honest payment-readiness explanation. `payments/PaymentStatusCard.kt` provides a reusable **read-only** display for a future authorized backend status; existing Farmer behavior is unchanged. Android holds no Daraja consumer key/secret/passkey and accepts no authoritative amount from a user form.

An accepted STK initiation or phone prompt is not a receipt. Only server-confirmed payment state may be displayed as paid. No Android timer, button, responseCode=0 from initiation or local callback simulation marks a real order paid.

## Source-verified architecture

- POST `/api/v1/payments/stk-push/supplier`: StkPushInitiateRequest `{orderId, phoneNumber?}`. The phone falls back to the buyer's registered number; source expects 2547… or 2541… Kenyan format. There is no client amount field.
- PaymentService authorizes a Supplier owning at least one line, calculates outstanding as whole order total minus paid/held/settled payments, requests Daraja from Spring Boot, then records a pending payment attempt. The response contains paymentId, orderId, amount, status, Daraja response and createdAt. It does not prove payment success.
- GET `/api/v1/payments/{id}/status` and order payment reads exist, but ResourceAuthorization.payment delegates to buyer/admin order access. A Supplier who can initiate is not thereby authorized to read its status. Android must not bypass this through buyer/admin endpoints or arbitrary payment IDs.
- POST `/api/v1/payments/daraja/callback` and DarajaCallbackService correlate checkout/merchant identifiers with saved payment attempts and update payment/order states. Duplicate callbacks are handled in source; successful/settled states are not overwritten by late failure. Failure, cancellation and timeout outcomes map to FAILED. That does not make initiation idempotent.
- PaymentResponse contains id, orderId, method, reference, amount, status, paidAt, createdAt, updatedAt. It does not expose a distinct cancellation status/result-code field suitable for current Android cancellation messaging.
- PaymentStatus wire values: pending, paid, held, settled, failed, refunded. Preserve escrow meanings: HELD is not SETTLED. Unknown/cancelled/initiation-accepted strings must not silently become PAID. The reusable mapper returns unknown for unsupported values.
- Source configuration takes Daraja credentials from environment variables and defaults environment to sandbox. Actual deployed mode/credentials/callback configuration are unverified; Android cannot select sandbox versus production or inspect provider credentials.

The public `/api-docs`, `/api/v1/health` and Supplier-category checks returned HTTP 404 during this task. These are source findings, not a claim that a working Daraja deployment is reachable.

## Blocking issues requiring Daniel

1. **Supplier-safe order/payment reads.** Provide the authenticated seller's permitted lines, collection scope, order-payment association and payment-status endpoint with matching authorization. Never expose other sellers' private lines or transaction details to obtain a payment ID.
2. **Mixed-seller collection policy.** Current authorization requires any owned line but amount calculation uses the whole order's outstanding total. Define who may collect which amount, how delivery fees/partial payments are allocated and who may select/change the buyer phone. Keep amounts and currency authoritative on the server.
3. **Initiation idempotency and concurrency.** Current code makes an external request before saving the payment and does not deduplicate in-flight/pending attempts. Repeated taps, lost responses, simultaneous sellers, DB failure after initiation and process restarts can create duplicate prompts or untracked attempts. Persist an intent/idempotency key before provider initiation, serialize outstanding-balance decisions, return/reconcile the existing attempt and define retry limits. Client single-flight alone is insufficient.
4. **Callback security/reachability mismatch.** DarajaCallbackController documents a public callback, but the reviewed SecurityConfig's public POST allowlist does not include `/api/v1/payments/daraja/callback`; the fallback is authenticated. Verify/fix the actual deployment so Safaricom can reach only the intended callback with appropriate provider validation/rate limits. Do not make all payments endpoints public. No callback request was sent to test this.
5. **Durable reconciliation.** Define authenticated lookup by intent/idempotency key, provider query/reconciliation for missing callbacks, attempt expiry, duplicate callback concurrency and recovery after deployment/restart. In-memory request tracking is not a replacement for durable payment-attempt state. Verify callback amount/phone/reference authenticity and database uniqueness/locking under real retry scenarios.
6. **Money validation.** Review integer-KES rounding/conversion (`setScale(0, HALF_UP).intValue()` in source), overflow and fractional totals, partial payments and outstanding balance. Android must never supply an overriding authoritative amount or correct a backend accounting decision locally.
7. **Minimal status projection.** Expose an allowed status, authoritative amount/currency, order/intent identity and safe error/cancellation reason. Do not return provider tokens, full raw callbacks, private receipt/phone details or secrets to general clients. Distinguish cancelled/timeout/failed only if that detail is provided safely by the backend.
8. **Deployment and logs.** Confirm sandbox mode, safe test fixtures and explicit opt-in for live provider calls. Remove sensitive financial/token logging; configure public HTTPS callbacks and monitoring without emitting credentials, raw phone numbers or receipt contents to Android logs.

## Android work after contracts are complete

1. Add an API/repository that takes a verified authorized order/intent ID, not arbitrary seller or payment IDs. Send no authoritative amount. Use the existing JWT client and session tags; keep redirects/TLS restrictions.
2. Show the backend's amount, currency, buyer-contact consent/phone policy and explicit confirmation before initiation. Persist only the minimum account/backend-scoped attempt identifier needed for recovery, with privacy review.
3. Use a single-flight action and backend idempotency key. After uncertain initiation, reconcile by that key rather than initiating again. Display pending while awaiting provider confirmation; never translate initiation acceptance into paid.
4. Poll only the authorized status API with bounded backoff/cancellation and lifecycle/session isolation. Render pending, paid, held, settled, failed/refunded and supported cancellation detail as returned. Refresh order state from the backend after confirmation.
5. Clear private UI state on logout/account switching, reject stale replies and never log provider responses or private identifiers. Share the read-only status component with Farmer checkout only through a separately reviewed change.

## Validation plan

Use synthetic transports/provider stubs by default. Test duplicate taps, duplicate initiation keys, unknown outcomes, cancellation/timeouts, delayed/duplicate callbacks, partial/mixed-seller payments, stale sessions, forbidden reads, restart recovery and sandbox failures. Current Android tests verify exact status spellings, unknown/cancellation handling, pending versus paid distinctions, and the absence of payment endpoints/actions from SupplierApi. No real transaction is needed or authorized for these tests.

Only after Daniel resolves the blockers and the owner explicitly approves should a disposable **sandbox** account/order be used for end-to-end provider testing. Production transactions require separate explicit authorization and operational readiness review.
