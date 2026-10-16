# Supplier backend requirements — Daniel

Android Supplier profile/catalogue operations are implemented against reviewed source. The current ngrok health, Supplier categories and OpenAPI checks returned HTTP 404; restore the tunnel and confirm the deployed commit before runtime testing. This document requests backend work; Android does not modify servers, schemas or databases.

## Verified current capabilities

- Role SUPPLIER uses the shared email/password login and token refresh. ProfileService checks self/admin and Supplier role for `GET profiles/suppliers/{userId}`. No forced-reset/activation indicator is present in the inspected auth response.
- ProductService derives sellerId from the authenticated actor, derives sellerType from role, creates PENDING_APPROVAL, and supports JSON create/update plus separate product-image upload. Owner/admin reads include pending products in the reviewed source. Android still independently rejects wrong owners/types.
- Supplier categories are available by `categories/type/SUPPLIER`; Android validates active Supplier membership. Source resolveCategoryId accepts category IDs without fully validating seller-type/activity, so server-side enforcement is still needed against other clients.
- Editing a product does not requeue approval. Android restricts edits/photos to pending listings until reapproval policy is defined. Source inventory uses stockQty/reservedQty/soldQty; there is no invented inventory API.

## Incoming orders and fulfillment — missing safe contract

OrderController exposes general/buyer lists, individual orders/items/timeline and status changes. It does not expose a Supplier/seller incoming-order feed. OrderService's order reads use ResourceAuthorization.order, which permits buyer/admin, not a seller. A whole OrderResponse can include multiple sellers' items and buyer/driver contact/address fields; filtering a general feed on Android is not authorization.

Provide a seller-self API (suggested contract to agree, **not an implemented endpoint**) deriving Supplier identity from the JWT and returning only its permitted order lines. Include stable order/line identifiers, pagination, applicable customer display/contact fields, quantity/unit snapshots, seller totals, explicit currency, authoritative payment state and permitted fulfillment actions. Do not expose other sellers' lines or whole-order financials unless explicitly authorized.

Define server-enforced action transitions and scope: acceptance/rejection, packing/dispatch, cancellation, partial fulfillment, concurrent changes, audit and idempotency. Current status updates are effectively admin-only; Android must not call them as though Supplier acceptance were authorized.

Actual source OrderStatus wire values: placed, confirmed, in_transit, delivered, completed, disputed, cancelled. Controller comments listing PENDING/PROCESSING/SHIPPED are stale; do not implement from those comments. Android currently shows no order metrics, history or customer details because it has no authorized data source.

## Logistics and driver privacy — missing Supplier projection

LogisticsService general lists/assignment are admin-only. Driver lists use driver(self); per-order jobs use order(buyer/admin); job reads use jobRead (assigned driver, buyer or admin). Supplier ownership of an order line does not grant these reads. No Supplier tracking/driver API is verified.

Provide an authorized order-line-to-delivery relationship for the Supplier, allowed driver display information, actual delivery milestones/timestamps and permitted pickup actions. Define mixed-seller pickup privacy and reassignment behavior. Explicitly determine whether driver phone/plate/location may be exposed. A Supplier should never request arbitrary driver IDs or access the buyer's full order to obtain a delivery ID.

Actual DeliveryStatus wire values: pending_assignment, assigned, accepted, en_route_to_pickup, arrived_at_pickup, picked_up, in_transit, arrived_at_destination, delivered, cancelled, failed. These enums alone do not establish Supplier access. Android displays a missing-capability explanation instead of fabricated maps/GPS/history.

## Payments

See [MPESA_INTEGRATION_PLAN.md](MPESA_INTEGRATION_PLAN.md). Supplier STK initiation exists in source but seller payment status access, safe seller-scoped amounts/order retrieval, durable duplicate prevention and callback/reconciliation deployment need resolution before enabling a checkout interface.

## Catalogue reliability and moderation

1. Confirm deployed owner pending-list/image access and product approval behavior. Provide an authorized Supplier fixture and active Supplier categories without asking Android to create admin data.
2. Enforce category seller type/activity and pending-only/reapproval changes server-side. Clarify approved-listing stock-only edits versus content/image changes and suspension/rejection restrictions.
3. Add idempotency keys and creation lookup/reconciliation identifiers for create/upload; define upload deduplication and gallery ordering under concurrency. Filenames/MIME/size are not returned in image DTOs, so Android cannot safely infer upload identity from them.
4. Return version/ETag information and enforce reservation-safe stock writes transactionally. Android re-reads reservations, but a race before PUT cannot be eliminated client-side.
5. Publish deployed file limits and allowed formats, enforce signatures/decoded limits, bind image URLs to the trusted public backend origin (relative `/api/v1/images/...` works), and retain pending-image authorization/no-store delivery.
6. If low-stock metrics are desired, confirm the threshold field/read contract and supported update semantics. Android currently shows out-of-stock from actual available quantity and never chooses an arbitrary low-stock threshold.

## Provisioning and runtime acceptance

Provide safe existing Supplier test credentials privately, account/profile activation rules, login reset requirements if any, and a known deployment version. Do not include passwords or provider keys in Android source, Git, terminal arguments or logs. Confirm server token/header debug logging is removed/disabled.

Acceptance needs two Suppliers to verify cross-account isolation, pending moderation and image access. Remote writes require explicit owner approval and disposable fixtures. No API writes, payment calls, migrations or database changes were performed during this implementation.
