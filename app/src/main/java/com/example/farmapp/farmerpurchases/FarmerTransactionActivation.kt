package com.example.farmapp.farmerpurchases

/**
 * Phase 4 Batch 3: Backend e-commerce transaction readiness matrix.
 *
 * This file documents the verified safety status of every Farmer purchaser transaction operation.
 * None of the BLOCKED operations are callable from any Android path in this batch.
 * Each entry cites verified source evidence (OpenAPI SHA-256 7CF98CC2…, backend commit a8205b0).
 *
 * Update this file only when backend guarantees listed in [ActivationRequirement] are confirmed
 * by the backend team and a new batch is explicitly approved.
 */

/** Confidence level of each documented fact. */
internal enum class EvidenceLevel {
    /** Directly observed in the deployed OpenAPI, source Java, or confirmed Android wire test. */
    VERIFIED,
    /** Consistent with source code but not confirmed by a live authenticated runtime call. */
    INFERRED,
    /** Not yet determined; operation must remain blocked until resolved. */
    UNVERIFIED
}

/** Current activation readiness of a Farmer purchaser transaction operation. */
internal enum class TransactionReadiness {
    /** All safety requirements are met; this operation may be enabled with approval. */
    SAFE,
    /** One or more backend safety requirements are unresolved; activation is prohibited. */
    BLOCKED,
    /** Insufficient evidence to assess; treat as BLOCKED. */
    UNKNOWN
}

/**
 * A single backend safety requirement that must be satisfied before an operation can be activated.
 * Requirements are written in terms of observable, testable backend behavior — not aspirations.
 */
internal data class ActivationRequirement(
    val id: String,
    val description: String,
    val evidenceLevel: EvidenceLevel
)

/** Full compatibility evidence record for one Farmer purchaser transaction operation. */
internal data class FarmerTransactionEntry(
    val operation: String,
    val httpMethod: String,
    val endpointPath: String,                 // relative to /api/v1/
    val requestDtoFields: String,
    val responseDtoFields: String,
    val authentication: String,
    val authorization: String,
    val sideEffects: String,
    val idempotency: String,
    val errorHandling: String,
    val androidCompatibility: String,
    val readiness: TransactionReadiness,
    val readinessReason: String,
    val blockedRequirements: List<ActivationRequirement> = emptyList()
)

/**
 * Authoritative Farmer transaction compatibility matrix for Phase 4 Batch 3.
 *
 * Evidence sources:
 *  - Deployed OpenAPI: SHA-256 7CF98CC2F340247C5CDB9C7BE8117E6CA9B379768EA67569538FE5770FFD9E31
 *  - Backend source commit: a8205b07a0f0ba93105197c6472b7e4439012025 (Kilivana-Backend)
 *  - Android wire tests: FarmerApiAlignmentTest, FarmerPurchaseRepositoryTest (all passing)
 *  - Batch 2 report: docs/PHASE4_BATCH2_FARMER_PURCHASING_ALIGNMENT.md
 *
 * Distinguish: VERIFIED = confirmed by multiple independent sources; INFERRED = consistent with
 * source but not runtime-confirmed; UNVERIFIED = unknown, blocks activation.
 */
internal object FarmerTransactionMatrix {

    val PRODUCT_BROWSING = FarmerTransactionEntry(
        operation = "Product browsing",
        httpMethod = "GET",
        endpointPath = "products/search?sellerType=SUPPLIER&page=&size= | products/{id} | categories/type/SUPPLIER",
        requestDtoFields = "sellerType=SUPPLIER, page, size (search); productId (detail)",
        responseDtoFields = "Page<ProductResponse>: id, sellerId, sellerType, categoryId, name, unit, price, stockQty, minimumOrderQty, status",
        authentication = "Bearer JWT (authenticated session); public endpoints also available unauthenticated",
        authorization = "No role restriction documented in deployed OpenAPI for product reads. FARMER can read Supplier catalogue.",
        sideEffects = "None — read-only. No cart or state mutation.",
        idempotency = "Fully idempotent. Repeating returns current catalogue state.",
        errorHandling = "HTTP 404 product not found; HTTP 401 on authenticated paths. Existing MarketplaceRepository handles both.",
        androidCompatibility = "Fully reused via MarketplaceRepository. No changes required.",
        readiness = TransactionReadiness.SAFE,
        readinessReason = "VERIFIED: deployed OpenAPI + Android wire tests confirm read-only paths. No side effects.",
        blockedRequirements = emptyList()
    )

    val CART_READBACK = FarmerTransactionEntry(
        operation = "Cart readback",
        httpMethod = "GET",
        endpointPath = "carts/buyer/{buyerId} | cart (principal alias)",
        requestDtoFields = "buyerId (path parameter, int64)",
        responseDtoFields = "CartResponse: id (int64), buyerId (int64), status (string). " +
            "IMPORTANT: CartResponse does NOT include items, quantities, or total fields. " +
            "These are metadata only — not line evidence.",
        authentication = "Bearer JWT required. 401 on missing/expired token.",
        authorization = "INFERRED: BUYER role confirmed in source; FARMER purchaser role not runtime-verified on deployed build.",
        sideEffects = "CRITICAL: GET /cart (principal alias) can CREATE a new ACTIVE cart if none exists. " +
            "GET carts/buyer/{buyerId} behavior on missing cart is not fully documented in OpenAPI. " +
            "Either path may mutate server state on a bare GET call.",
        idempotency = "NOT IDEMPOTENT: a GET can create a new cart — this is a confirmed side effect.",
        errorHandling = "HTTP 404 documented for explicit cart endpoint. HTTP 403/401 on auth failure.",
        androidCompatibility = "BrianCheckoutApi.cart() and FarmerCheckoutRepository.readCart() exist but are blocked in FarmerPurchaserPolicy.",
        readiness = TransactionReadiness.BLOCKED,
        readinessReason = "BLOCKED: GET cart can create a new ACTIVE cart (confirmed side effect). " +
            "CartResponse contains no line evidence — cannot verify server basket contents. " +
            "Farmer purchaser role not runtime-confirmed on deployed build.",
        blockedRequirements = listOf(
            ActivationRequirement(
                "CART-1",
                "Confirm that GET carts/buyer/{buyerId} never creates a new cart when one does not exist — or document the creation behavior and provide a Farmer-safe alternative.",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "CART-2",
                "Provide an authoritative cart line endpoint that returns actual productId, quantity, unitPrice per item — not just cart metadata — for Farmer basket reconciliation.",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "CART-3",
                "Confirm runtime Farmer purchaser role authorization on the deployed build (not only source code).",
                EvidenceLevel.UNVERIFIED
            )
        )
    )

    val CART_MODIFICATION = FarmerTransactionEntry(
        operation = "Cart modification",
        httpMethod = "POST | PUT | DELETE",
        endpointPath = "cart/items (add) | carts/{cartId}/items/{productId}?quantity= (set) | cart/items/{itemId} (Angular item-ID variant)",
        requestDtoFields = "POST cart/items: {productId: Long, quantity: Int}. PUT carts/{cartId}/items/{productId}?quantity=Int.",
        responseDtoFields = "CartResponse (metadata only: id, buyerId, status). No line snapshot returned.",
        authentication = "Bearer JWT required.",
        authorization = "INFERRED from source: BUYER confirmed. FARMER purchaser not runtime-verified.",
        sideEffects = "POST adds or increments product in cart. POST 409 on duplicate product (documented). " +
            "PUT carts/{cartId}/items/{productId} uses product ID in path — NOT the cart-line ID. " +
            "Angular PUT cart/items/{itemId} uses cart-line ID in path — these are DIFFERENT identifiers " +
            "and must NEVER be interchanged without knowing the line ID.",
        idempotency = "NOT IDEMPOTENT: duplicate POST may increment quantity. No server idempotency key documented.",
        errorHandling = "HTTP 400 (invalid quantity), 404 (product not found), 409 (duplicate product — handled in CheckoutController), 422 (validation).",
        androidCompatibility = "Retrofit interfaces exist in BrianCheckoutApi. CheckoutController has 409 duplicate handling. All blocked in FarmerCheckoutRepository.",
        readiness = TransactionReadiness.BLOCKED,
        readinessReason = "BLOCKED: No line-level snapshot after upload. No idempotency key. Duplicate semantics confirmed but only partially safe. " +
            "Farmer purchaser role not runtime-verified.",
        blockedRequirements = listOf(
            ActivationRequirement("CART-1", "See CART-1 (cart GET side effect).", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("CART-2", "See CART-2 (line evidence).", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("CART-3", "See CART-3 (Farmer role).", EvidenceLevel.UNVERIFIED),
            ActivationRequirement(
                "CART-4",
                "Confirm stable, unique line IDs for each cart item so that PUT carts/{cartId}/items/{productId} can be used without risk of modifying the wrong line when multiple products share similar IDs.",
                EvidenceLevel.UNVERIFIED
            )
        )
    )

    val CHECKOUT_QUOTE = FarmerTransactionEntry(
        operation = "Checkout quote",
        httpMethod = "POST",
        endpointPath = "checkout/delivery-quote",
        requestDtoFields = "DeliveryQuoteRequest: deliveryMethod (DOOR_DELIVERY|PICKUP_STATION), " +
            "addressId? (int64), pickupStationId? (int64), pickupLocation? (string ≤500 chars). " +
            "Exactly one of the three delivery variants must be populated (enforced by CheckoutQuoteRequest init).",
        responseDtoFields = "DeliveryQuoteResponse → CheckoutQuote: quoteId (string), subtotal (BigDecimal), " +
            "deliveryFee (BigDecimal), total (BigDecimal), currency (KES), expiresAt (ISO-8601), " +
            "deliveryMethod, addressId?, pickupStationId?, pickupLocation?.",
        authentication = "Bearer JWT required. Cart must be in CHECKOUT_PROCESSING status.",
        authorization = "INFERRED: requires CHECKOUT_PROCESSING cart belonging to authenticated user. " +
            "Farmer purchaser authorization not runtime-confirmed.",
        sideEffects = "Creates a server-side quote record. Quote is associated with the processing cart. " +
            "Multiple calls may create multiple quote records — no idempotency documented.",
        idempotency = "NOT DOCUMENTED as idempotent. Repeated calls likely create new quote IDs. " +
            "Quote expiry is enforced by expiresAt field (validated in verifiedCheckoutQuote).",
        errorHandling = "HTTP 404 (cart not found or not in CHECKOUT_PROCESSING). No explicit 400/409 documented for quote duplication.",
        androidCompatibility = "BrianCheckoutApi.quote() and CheckoutController.refreshQuote() exist. Blocked in FarmerCheckoutRepository.",
        readiness = TransactionReadiness.BLOCKED,
        readinessReason = "BLOCKED: Quote requires a CHECKOUT_PROCESSING cart — itself blocked pending cart safety resolution. " +
            "Quote idempotency not documented. Farmer role not runtime-verified.",
        blockedRequirements = listOf(
            ActivationRequirement("CART-1", "See CART-1.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("CART-2", "See CART-2.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("CART-3", "See CART-3.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement(
                "QUOTE-1",
                "Provide durable, immutable quotes with ownership binding, exact item/price/delivery snapshot, confirmed expiry enforcement, and atomic quote-to-order consumption.",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "QUOTE-2",
                "Confirm that multiple quote requests for the same cart do not leave orphaned server-side quote records that would affect billing or audit trails.",
                EvidenceLevel.UNVERIFIED
            )
        )
    )

    /**
     * Section D: Checkout endpoint relationship analysis.
     *
     * Three endpoints reference order/checkout creation. They are NOT interchangeable.
     * This documents their relationship based on verified OpenAPI + source evidence.
     */
    val CHECKOUT_ENDPOINT_ANALYSIS = """
        CHECKOUT ENDPOINT RELATIONSHIP ANALYSIS (Phase 4 Batch 3, Evidence-Backed)
        
        1. POST /api/v1/checkout/order  (operationId: createOrder_1)
           - Deployed OpenAPI: YES. Schema: CheckoutOrderRequest { quoteId: string, phoneNumber: string }
           - Response: ApiResponse<Long> (order ID as bare numeric Long)
           - Requires: CHECKOUT_PROCESSING cart with valid, unexpired quote
           - Used by: Android BrianCheckoutApi.order(), CheckoutController.create()
           - Status: SAFE contract — not safe to activate without resolving cart/quote blockers
        
        2. POST /api/v1/orders  (operationId: createOrder)
           - Deployed OpenAPI: YES. Schema: OrderRequest (different DTO — not CheckoutOrderRequest)
           - Response: ApiResponse<OrderResponse> (full order object, not just ID)
           - Used by: Angular api.config.ts CREATE action (current Angular)
           - NOT compatible with quote-ID creation flow
           - NOT used by any Android checkout path
           - Angular order.spec.ts legacy payload (recipientName, phone, CASH) is for a stale test fixture,
             not the current OrderRequest schema
           - Status: INCOMPATIBLE with Android quote engine. Do NOT use as a Farmer checkout fallback.
        
        3. POST /api/v1/orders/checkout  (Angular legacy)
           - Deployed OpenAPI: NO — this path does NOT exist in the deployed API
           - Angular order.spec.ts expects this path in a stale test
           - Status: STALE. Must NOT be added to Android. FarmerApiAlignmentTest asserts absence.
        
        CONCLUSION: Only POST /checkout/order is the correct Android quote-engine endpoint.
        The three paths are distinct operations with incompatible request and response schemas.
    """.trimIndent()

    val ORDER_SUBMISSION = FarmerTransactionEntry(
        operation = "Order submission",
        httpMethod = "POST",
        endpointPath = "checkout/order",
        requestDtoFields = "CheckoutOrderRequest: {quoteId: string, phoneNumber: string}",
        responseDtoFields = "ApiResponse<Long> — the created order ID as a bare numeric Long (not an OrderResponse wrapper). " +
            "VERIFIED: deployed OpenAPI schema is ApiResponseLong. " +
            "Android verifiedCheckoutOrderId() enforces this — no invented wrapper fallback.",
        authentication = "Bearer JWT required. Cart must be in CHECKOUT_PROCESSING status.",
        authorization = "INFERRED: requires cart ownership. Farmer purchaser role not runtime-verified on deployed build.",
        sideEffects = "Creates an order record. Transitions cart to ordered state. " +
            "May trigger downstream notifications or logistics job creation.",
        idempotency = "NOT DOCUMENTED as idempotent. No server-side idempotency key in request schema. " +
            "Network timeout creates ORDER_PENDING uncertainty in the checkout journal — this is the " +
            "primary recovery scenario (CheckoutController.recover()). " +
            "Android-side: beforeOrderIds snapshot + ORDER_RETURNED durable journal phase + " +
            "readOrder() verification chain prevent duplicate payment on a single new order. " +
            "But: a timed-out POST may have committed — no server lookup-by-quote-id is available.",
        errorHandling = "HTTP 400 (invalid payload), 500 (server error). No 409 idempotency conflict documented.",
        androidCompatibility = "BrianCheckoutApi.order() + CheckoutController.create() + beforeOrderIds journal are all implemented. Blocked in FarmerCheckoutRepository.",
        readiness = TransactionReadiness.BLOCKED,
        readinessReason = "BLOCKED: Requires cart (CART-1 through CART-4) and quote (QUOTE-1, QUOTE-2) blockers resolved first. " +
            "Additionally: no server-side idempotency guarantee; network timeout cannot be safely resolved without a quote-ID or " +
            "request-ID lookup endpoint. Farmer role not runtime-verified.",
        blockedRequirements = listOf(
            ActivationRequirement("CART-1", "See CART-1.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("CART-2", "See CART-2.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("CART-3", "See CART-3.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("QUOTE-1", "See QUOTE-1.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("QUOTE-2", "See QUOTE-2.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement(
                "ORDER-1",
                "Provide server-side idempotency for POST /checkout/order (e.g., idempotency-key header, or lookup-by-quoteId endpoint) so that a timed-out submission can be verified without creating a duplicate order.",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "ORDER-2",
                "Confirm runtime Farmer purchaser authorization for POST /checkout/order on the deployed build.",
                EvidenceLevel.UNVERIFIED
            )
        )
    )

    val MPESA_INITIATION = FarmerTransactionEntry(
        operation = "M-Pesa initiation (STK Push)",
        httpMethod = "POST",
        endpointPath = "payments/stk-push/buyer",
        requestDtoFields = "StkPushInitiateRequest: {orderId: Long, phoneNumber: string (normalized Kenyan)}. " +
            "NEVER use payments/stk-push/farmer — that is the seller collection endpoint.",
        responseDtoFields = "StkPushInitiateResponse: {paymentId: Long, orderId: Long, amount: BigDecimal, status: string}. " +
            "VERIFIED in deployed OpenAPI (ApiResponseStkPushInitiateResponse). " +
            "Response amount must be validated against order total (BuyerPaymentContract.pendingInitiation).",
        authentication = "Bearer JWT required.",
        authorization = "VERIFIED in deployed OpenAPI: HTTP 403 documented for 'Caller is not the buyer of this order'. " +
            "INFERRED: FARMER purchaser with same buyerId should qualify — not runtime-confirmed on deployed build.",
        sideEffects = "Initiates a live Daraja STK Push to the buyer's phone. " +
            "Creates a payment record on the backend with status PENDING. " +
            "Final outcome arrives asynchronously via M-Pesa callback — the app is NOT informed synchronously. " +
            "HTTP 503 documented for 'M-PESA service unavailable'.",
        idempotency = "CRITICAL: NOT idempotent. Each POST may trigger a new STK Push and create a new payment record. " +
            "Android-side duplicate prevention: DurablePaymentAttempts.reserve() persists a boolean before the POST. " +
            "If the app is killed between reserve() and the POST, no duplicate is sent. " +
            "If the POST completes but the response is lost, the reservation prevents a retry. " +
            "BACKEND GAP: No duplicate-STK-push-per-order prevention is documented in the OpenAPI. " +
            "Multiple POSTs to payments/stk-push/buyer with the same orderId may create multiple payment records.",
        errorHandling = "HTTP 400 (invalid request / no outstanding balance / invalid phone), " +
            "403 (not the buyer), 404 (order not found), 503 (Daraja unavailable). " +
            "Network timeout: payment may have been initiated — treat as uncertain. No retry.",
        androidCompatibility = "BuyerOrdersApi.initiateBuyerStk() exists. FarmerPaymentRepository.initiate() is blocked (throws FARMER_PAYMENT_BLOCKED). " +
            "PurchaserPaymentController.paymentAllowed = { false } for Farmer. Both gates are independent.",
        readiness = TransactionReadiness.BLOCKED,
        readinessReason = "BLOCKED: Multiple unresolved safety gaps. No order required before this batch. " +
            "Payment environment (sandbox/production) not configured for Farmer. Callback reconciliation not verified.",
        blockedRequirements = listOf(
            ActivationRequirement("ORDER-1", "See ORDER-1.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement("ORDER-2", "See ORDER-2.", EvidenceLevel.UNVERIFIED),
            ActivationRequirement(
                "PAY-1",
                "Confirm that the backend prevents duplicate STK Push calls for the same order (e.g., by rejecting a second initiation when a PENDING payment already exists for that order).",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "PAY-2",
                "Confirm that M-Pesa callback handling reconciles payment amount against order total before marking a payment PAID.",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "PAY-3",
                "Confirm the payment provider environment (SANDBOX vs PRODUCTION) and that the configured BUYER_MPESA_CONFIRMED_API_BASE_URL matches API_BASE_URL for Farmer purchasing.",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "PAY-4",
                "Confirm runtime Farmer purchaser authorization for POST /payments/stk-push/buyer (payments/stk-push/farmer is NEVER used).",
                EvidenceLevel.UNVERIFIED
            ),
            ActivationRequirement(
                "PAY-5",
                "Confirm Farmer eligibility checks: Supplier inputs only, correct product categories, delivery warehouse coordinates, and approved delivery tariffs.",
                EvidenceLevel.UNVERIFIED
            )
        )
    )

    val PAYMENT_STATUS_LOOKUP = FarmerTransactionEntry(
        operation = "Payment status lookup",
        httpMethod = "GET",
        endpointPath = "payments/order/{orderId} | payments/{paymentId}/status",
        requestDtoFields = "orderId (path, int64) | paymentId (path, int64)",
        responseDtoFields = "List<PaymentResponse> | PaymentResponse: {id: int64, orderId: int64, method: string, amount: BigDecimal, status: string, updatedAt: string}. " +
            "VERIFIED in deployed OpenAPI (ApiResponseListPaymentResponse, ApiResponsePaymentResponse).",
        authentication = "Bearer JWT required.",
        authorization = "INFERRED: order ownership required. Farmer purchaser ownership check: " +
            "FarmerPaymentRepository.payments() reads order first to confirm buyerId == purchaserId before reading payments.",
        sideEffects = "None — read-only. No payment state mutation.",
        idempotency = "Fully idempotent. Returns current payment state.",
        errorHandling = "HTTP 404 (payment not found). No auth-specific codes documented in OpenAPI for these paths.",
        androidCompatibility = "BuyerOrdersApi.payments() and payment() exist. FarmerPaymentRepository implements both with explicit ownership verification.",
        readiness = TransactionReadiness.SAFE,
        readinessReason = "SAFE for read-only use. FarmerPaymentRepository enforces order ownership before every payment read. " +
            "Cannot create or mutate payment state. INFERRED that Farmer can read own-order payments; " +
            "HTTP 401/403 will surface as visible failures, not empty successes.",
        blockedRequirements = emptyList()
    )

    val ORDER_TRACKING = FarmerTransactionEntry(
        operation = "Order tracking",
        httpMethod = "GET",
        endpointPath = "logistics/jobs/order/{orderId} | logistics/jobs/{jobId}/tracking | tracking/{jobId}/location",
        requestDtoFields = "orderId / jobId (path, int64)",
        responseDtoFields = "List<LogisticsJob>: {id, orderId, driverId?, status}. " +
            "List<DeliveryEvent>: {id, logisticsJobId, status}. " +
            "DriverLocationResponse: {tripId (= jobId per inspected mapper), orderId, driverId, latitude, longitude}. " +
            "VERIFIED: tracking/{jobId}/location returns HTTP 404 when no fix recorded (documented and handled in FarmerTrackingRepository).",
        authentication = "Bearer JWT required. VERIFIED in deployed OpenAPI.",
        authorization = "VERIFIED in deployed OpenAPI: tracking/{jobId}/location allows 'assigned driver, buyer, any seller on the order, administrator'. " +
            "Farmer as purchaser (buyer role in this context) should qualify. Not runtime-confirmed.",
        sideEffects = "None — read-only. Driver location POST is not used.",
        idempotency = "Fully idempotent. Returns current delivery state.",
        errorHandling = "HTTP 401 (auth), 403 (not authorized to track), 404 (job not found or no location fix). " +
            "FarmerTrackingRepository handles 404-location as 'Driver location unavailable' without crashing.",
        androidCompatibility = "FarmerTrackingRepository fully implemented. Requires own-order readback before every tracking read (ownership enforcement).",
        readiness = TransactionReadiness.SAFE,
        readinessReason = "SAFE for read-only use. Own-order verification is enforced before every tracking call. " +
            "HTTP 401/403/404 surface as visible failures. tripId-equals-jobId mapping is documented.",
        blockedRequirements = emptyList()
    )

    /** Complete list of all entries for structured iteration in tests. */
    val all = listOf(
        PRODUCT_BROWSING, CART_READBACK, CART_MODIFICATION, CHECKOUT_QUOTE,
        ORDER_SUBMISSION, MPESA_INITIATION, PAYMENT_STATUS_LOOKUP, ORDER_TRACKING
    )

    /** All activation requirements across all blocked operations, deduplicated by ID. */
    val allRequirements: List<ActivationRequirement> = all
        .flatMap { it.blockedRequirements }
        .distinctBy { it.id }
        .sortedBy { it.id }
}
