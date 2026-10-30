# Mandatory login OTP and marketplace investigation

Reviewed 2026-10-27. This is an investigation, an Android catalog fix, and a proposed backend contract. Mandatory login OTP is **not implemented or enforced** by the current backend contract.

## Evidence and limits

Fetched the configured deployed [OpenAPI](https://either-juvenile-progeny.ngrok-free.dev/api-docs) using an unauthenticated GET with the ngrok warning bypass header. Snapshot: `kilivana-login-marketplace-openapi-20261027.json`; SHA-256 `F7C2820B5CAD5F7ED6424887D184C3AB5502A433ABE87096E4525B54C91DF844`. Title Kilivana Backend API, version 1.0.0. No credentials, OTP emails, authenticated reads, product writes, or production database changes were made. OpenAPI describes a contract; it does not prove deployed runtime enforcement or identify the deployed commit.

Read local `../kilivana-supplier-backend` (closest match to deployed email OTP schema), `../kilivana-shared-backend` (older authentication), and `../kilivana-backend` (different reference implementation). These are distinct implementations and must not be treated as interchangeable. Backend directories were not edited. Android already has substantial uncommitted integration work, including the shared marketplace and email verification UI; those changes were preserved. Subsequent unauthenticated catalog GETs are documented below; no authenticated reads were performed.

## Deliverable summary and implementation gate

This document provides (1) authentication feasibility in section 1, (2) marketplace root cause and response comparison in section 2, (3) endpoint mapping below, (4) the file/test implementation plan below, and (5) backend requirements for Daniel in both sections. Further implementation is paused pending presentation of these findings and confirmation of backend contracts. The small Android query-casing edit and its test predate the user's explicit investigation-first restriction; they remain uncommitted. No subsequent runtime implementation changes were made.

| Product source | Intended customer | Correct marketplace |
| --- | --- | --- |
| Supplier agricultural supplies | Farmer | Farmer marketplace (Supplies) |
| Farmer agricultural produce | Buyer | Buyer marketplace |

### Actual endpoint mapping (existing deployed contract)

All routes below are relative to `/api/v1`. Sharing transport is supported; audience depends on the seller filter and server policy.

| Workflow | Android implementation | Existing API / ownership |
| --- | --- | --- |
| Supplier supply creation | `supplier/SupplierApi.kt`, `SupplierRepository.kt` | POST `products`; backend derives seller user ID and SUPPLIER type from principal; starts pending approval |
| Supplier management | Same | GET `products/seller/{id}`; PUT `products/{id}`; GET/POST `products/{id}/images`; additional gallery routes in `SupplierGalleryApi.kt`; owner/Admin writes |
| Farmer supply browsing | `marketplace/Marketplace.kt` | GET `products/search?sellerType=SUPPLIER`; GET `categories/type/SUPPLIER`; GET `products/{id}`; reject non-Supplier detail/page |
| Farmer produce creation | `repository/ProductRepository.kt` -> `network/canonical/CanonicalFarmService.kt` -> `CanonicalBackendApi.kt` | POST `products`; backend derives FARMER seller type from principal; GET `products/seller/{id}` for own crops |
| Buyer produce browsing | `marketplace/BuyerShell.kt` -> shared marketplace | GET `products/search?sellerType=FARMER`; GET `categories/type/FARMER`; GET `products/{id}`; reject non-Farmer detail/page |
| Approval | Backend `AdminProductController` / `ProductService` | Admin moderation; seller UI cannot approve its own pending listings |

No new endpoint is needed merely to retrieve supplies: the existing uppercase-filtered search demonstrably returns them. Role-specific route names must not be fabricated. Any login-OTP route/response design below is a proposal requiring Daniel's confirmed OpenAPI, not a deployed API to call.

## 1. Authentication findings

| Area | Observed behavior | Consequence |
| --- | --- | --- |
| Deployed login schema | `POST /api/v1/auth/login` returns `ApiResponseAuthTokenResponse`, containing access/refresh tokens, user, optional email-verification fields | No advertised mandatory login challenge contract |
| Deployed email verify | Explicitly confirms email only, does not issue JWTs; success is `ApiResponseVoid` | Cannot complete a two-step authenticated login as requested |
| Local Supplier backend login | `AuthService.login` checks password and account status, then `buildTokenResponse`; only `!user.isEmailVerified()` is forbidden | Previously verified accounts immediately receive usable access and refresh tokens before any fresh OTP |
| Local OTP request | `EmailVerificationTransactions.request` only issues for unverified users; verified/unknown addresses receive generic acknowledgments | Requesting a code for every login would not make verified users receive one |
| Local OTP verify | Rejects already verified users; sets `emailVerified`/timestamp, consumes challenge, issues no tokens | Email ownership verification, not login verification |
| Android login | `CanonicalAuthService.login` opens verification on explicit required flag, unverified user, or recognized verification-required 403; otherwise maps tokens | Verified accounts bypass OTP by design of the existing contract |
| Android verification continuation | `AuthViewModel.completeEmailVerification` retries password login after void verify success | With mandatory challenges this would start another challenge; must change to accepting tokens from login-OTP verify |
| Session save | `AuthRepository.login` validates response, checks Supplier profile, then calls `sessions.establish` | Currently saves password-only sessions because backend grants them |
| Restoration | `AuthRepository.restore` checks contract/origin; `SessionState.initialize` validates stored identity/tokens and refresh eligibility locally | No `/auth/me` or proof of prior login OTP is required before dashboard navigation |
| Refresh | `RefreshCoordinator` uses separate non-recursive transport; local backend validates refresh token type and account status, then builds another pair | Pre-migration password-only refresh tokens can bypass OTP indefinitely unless backend invalidates them |
| Logout | Local canonical controller reports success but does not revoke stateless JWTs; Android intentionally clears locally and skips canonical logout | Stolen/copied tokens remain usable after local logout |
| Roles | Login accepts FARMER, SUPPLIER, BUYER, INSPECTOR, DRIVER, ADMIN; Android routing is session driven; Admin has unsupported dashboard UI | Backend OTP policy must apply before role navigation and include Admin and future roles; unsupported dashboard is not an authentication control |

Older shared backend issues tokens without the newer email ownership check. The separate reference backend has stored refresh-token rotation/revocation, but still issues login tokens without OTP. Neither establishes the required policy for Daniel's deployed service.

### Existing ownership-OTP protections (local Supplier backend only)

`EmailOtpSecurity`, `EmailOtpRequestLimiter`, and `EmailVerificationTransactions` implement:

- Cryptographically random six-digit string, preserving leading zeroes; HMAC-SHA256 storage with shared secret and constant-time comparison.
- Ten-minute expiry; 60-second resend cooldown; five sends/email/hour; 60 sends/IP/hour; 120 verification requests/IP/hour.
- Five wrong attempts/account/hour, preserved across resends; resends rotate challenge UUID; used or expired codes are rejected.
- Binding to account ID, normalized current email, role, challenge UUID, and purpose `EMAIL_VERIFICATION`.
- Account row locking and state reread serialize attempts, issuance, and consumption across instances. Wrong attempts commit despite verification exceptions.

These controls are useful building blocks, but removing the `emailVerified` checks alone would be unsafe: the public email-only request is not proof of successful password verification, and ownership verification must not create an authenticated session. Login challenge purpose, credential binding, session state, and atomic token issuance are required.

### Proposed contract for Daniel (new routes, not currently deployed)

Preserve ownership verification routes for registration/email changes. Use a separate `LOGIN` challenge purpose and these responses inside the existing API envelope:

1. `POST /api/v1/auth/login` with `{email,password}` validates credentials and ACTIVE status for **every role**, creates a fresh challenge, and dispatches the email automatically. Return HTTP 202 with `{status:"OTP_REQUIRED",challengeId,challengeToken,maskedEmail,expiresInSeconds:600,resendAfterSeconds:60}`. No access token, refresh token, or authenticated cookie. `challengeToken` is an opaque, high-entropy, short-lived preauthentication secret, returned only to the password-authenticated caller; store only its hash. It must not be accepted by resource or refresh endpoints.
2. `POST /api/v1/auth/login-otp/verify` with `{challengeId,challengeToken,code}` (code pattern `[0-9]{6}`) atomically validates pending credential-authenticated challenge and rechecks account status, current email, credential/security version, expiry, attempts, and consumption. Consume it and create the server session/token family in one transaction; only then return HTTP 200 `ApiResponseAuthTokenResponse`. Set `emailVerified` if appropriate without changing role, account status, or approval. A previous registration challenge cannot be exchanged for login tokens.
3. `POST /api/v1/auth/login-otp/resend` with `{challengeId,challengeToken}` validates the pending login context and send budgets; rotates code and challenge ID, invalidates old code, preserves attempt budget, and returns the updated challenge metadata. It does not grant tokens. Return authoritative remaining cooldown/expiry and `Retry-After` on 429. Expired or locked challenges require password login again. Do not implement resend using arbitrary email alone.
4. Stable error codes: `LOGIN_OTP_INVALID` (generic invalid/wrong-account/used), `LOGIN_OTP_EXPIRED`, `LOGIN_OTP_LOCKED`, `LOGIN_OTP_RATE_LIMITED`, `LOGIN_OTP_DELIVERY_UNAVAILABLE`. Use 400/410/429/503 consistently, document them in OpenAPI, and retain generic invalid-credential responses before password validation. A queued/failed delivery must never produce a token session.

If existing verification URLs must be reused, introduce a versioned/discriminated LOGIN request and token-returning response with the same credential-bound semantics. Current email-only request and void verify contracts cannot silently serve both purposes.

Backend invariants:

- No role exemptions and no bypass based on `emailVerified`. No registration, recovery, refresh, admin-provisioning, or legacy login path may mint a new authenticated session without the required login verification.
- Store login purpose, account ID, normalized email/version, credential/security version, challenge ID, hashed preauth secret, code HMAC, expiry, failed-attempt window, consumed timestamp, and delivery state. Invalidate older pending login challenges when a new login replaces them; serialize concurrent verify/resend/login operations.
- Reuse the existing expiry/cooldown/attempt budgets as the initial policy, including limits on password login so it cannot bypass resend throttling. Rate limits must be shared across instances, survive resend/restart, and use correctly configured trusted-proxy client identity.
- Create revocable server sessions or refresh families with `otpVerifiedAt`, session ID, security version, and authentication method. Sign access claims server-side; enforce them in resource authorization, `/auth/me`, and refresh. Refresh rotates within a previously OTP-authenticated session; it cannot turn preauthentication into authentication. Detect refresh replay and revoke that family.
- On migration invalidate old access AND refresh credentials without OTP proof (token/session version or signing-key migration). Reject absent proof; do not backfill it from email ownership. Bound access lifetimes, refresh/session absolute lifetimes, and account security-change revocation are required.
- Implement authenticated logout with session/family revocation and invalidate access through a checked session/security version if immediate logout invalidation is required. Android clears locally even if logout fails. Do not claim revocation from the existing no-op endpoint.
- Never log password, code, challenge secret, raw tokens, or email payloads. Persist neither preauth secret nor code on Android.

### Android integration after backend contract lands

Represent signed out, pending login challenge, and authenticated as distinct states. On 202 display the six-digit screen automatically using server metadata, without a separate Verify email link. Keep only the challenge context in memory; discard password after login response. Verify via login-OTP endpoint and pass the resulting token pair through the existing validation, Supplier-profile check, registration-profile continuation, atomic session save, and role routing. Never retry password login after successful login-OTP verification. Cancellation, process death, account change, logout, and superseding login invalidate pending continuations; reject stale responses by flow/session generation. Profile creation must happen after authentication without registering the account again.

Restoration may resume an existing OTP-authenticated session; it is not a new password sign-in. Require server validation of the restored session, reject legacy sessions, and refresh only OTP-backed sessions. If product policy intends OTP on every app launch as well, explicitly expire restoration and require a new login; that is broader than every sign-in. Local proof flags alone do not enforce security. Admin must complete OTP even while its Android dashboard remains unsupported.

No cosmetic client-only login OTP or fabricated session proof was added in this investigation. Android cannot enforce this requirement against the present token-issuing backend.

## 2. Marketplace findings and Android fix

### Actual deployed response comparison (public GETs only)

Evidence: `marketplace-public-read-checks-20261027.json` and `marketplace-public-read-details-20261027.json`. These are reduced projections, not raw API bodies; omitted response fields must not be inferred from projected null placeholders. In particular `productType`, `sellerRole`, `supplierId`, and `farmerId` are **absent** from actual ProductResponse, not returned null fields. `farmId` is an actual nullable field and was null in these samples. The first category capture had an array-projection issue; use the second capture's category rows. Inconclusive image count/host projections were removed; image availability was not verified.

| Read-only request | Result | Implication |
| --- | --- | --- |
| GET `products/search?sellerType=SUPPLIER&page=0&size=20&sort=id,desc` | HTTP 200, five supplier rows, last=true | Existing API retrieves correctly classified supplies |
| Same with lowercase `supplier` | HTTP 200, zero rows, last=true | Confirmed deployed case-sensitive search defect |
| Same with `FARMER` | HTTP 200, six farmer rows, last=true | Correct produce filter excludes supplies in this sample |
| GET `products` | HTTP 200, eleven rows: five supplier and six farmer | Unfiltered endpoint really is a mixed catalog; unsafe as an audience-specific feed |
| GET `categories/type/SUPPLIER` | HTTP 200, eight active supplier categories, IDs 28-35 | Category browsing is available without privileged access |
| GET `products/276` | HTTP 200, supplier, seller 64, category 29, active, stock 2000/reserved 0 | A real supply can be fetched through search AND detail |

| Product ID | Seller user ID | Actual sellerType | Actual status | Category ID/name | Available = stock - reserved |
| --- | --- | --- | --- | --- | --- |
| 276 | 64 | supplier | active | 29 Seeds & Seedlings | 2000 |
| 275 | 64 | supplier | active | 28 Fertilizers | 50 |
| 274 | 48 | supplier | active | 33 Pesticides & Crop Protection | 15 |
| 191 | 48 | supplier | active | 28 Fertilizers | 0 |
| 190 | 48 | supplier | active | 31 Farm Equipment | 2 |
| 277 | 46 | farmer | active | 16 Dairy & Eggs | 10 |
| 189 | 58 | farmer | active | 16 Dairy & Eggs | 0 |
| 133 | 46 | farmer | active | 19 Fruits | 6 |
| 130 | 46 | farmer | active | 19 Fruits | 29 |
| 127 | 46 | farmer | active | 22 Legumes | 5 |
| 126 | 46 | farmer | active | 20 Vegetables | 0 |

The common owner key is `sellerId`, not separate supplier/farmer IDs. The seller's actual account role cannot be independently verified from these product responses; only sellerType and local principal-derived creation logic are available. No privileged user/profile queries were attempted. Observed supplier categories align with input categories; actual rows are correctly classified as supplies in this response sample. This is not a direct database audit.

**Root-cause conclusion:** Missing Farmer supplies are explained by the now reproduced lowercase search filter: there are four active, available supplies retrievable with the uppercase filter, so general approval blockage or missing supply records does not explain their absence. Product 191 is legitimately excluded by Android because all stock is reserved. Pending/rejected items also remain excluded by source policy; their private state was not queried. The reported Buyer leakage is consistent with using the demonstrated mixed `GET products` endpoint in another build/client, but it is not reproduced by current `BuyerShell` code or the FARMER-filtered response. Do not present that hypothesis as a proven installed-app root cause. Compare the affected APK and request URL with these findings before changing additional routing.

**Authorization conclusion:** Both inspected SecurityConfig implementations permit public product/category GETs, and public deployed search/detail succeeded. `ProductVisibility.canRead` permits ACTIVE products without a Buyer-only restriction. No evidence of a Farmer-specific denial on these routes was found. Authenticated Farmer execution was intentionally not performed; malformed/expired bearer handling or a different deployed revision remains outside this verification. Purchase authorization is a separate confirmed gap: local `ResourceAuthorization.buyer` permits BUYER/Admin, and `OrderService.createOrder` is Admin-only. Farmer supply browsing is supported, but Farmer checkout requires Daniel's authorization/ordering contract changes.

**Live-data conclusion:** The current Supplies and Buyer marketplace screens request the live API and contain no mock catalog import, seeded products, disk catalog cache, or failure fallback to fabricated products. The ViewModel keeps prior successfully fetched rows in memory on failure, so stale results can remain visible alongside an error; this is not a substitute catalog. `FarmerShell` passes a non-null real-product list to its home screen, preventing that screen's legacy null-triggered mock fallback. Legacy `BuyerScreen` and standalone/demo Farmer screens still contain mock data, but they are not the current live purchasing route. This source trace does not prove which APK the user observed. Actual image display was not exercised; code uses backend image URLs and shows image errors rather than fabricated photos.

Current working tree routes Farmer Supplies (`FarmerShell` -> `FarmerSuppliesScreen`) and Buyer Marketplace (`AppNavigation` -> `BuyerShell`) to the shared `MarketplaceScreen`. `marketplaceSeller` correctly maps FARMER -> supplier, BUYER -> farmer. Each page/detail checks returned seller type and only projects ACTIVE products with positive `stockQty - reservedQty`. It uses real backend descriptions, prices, units, quantities, and image URLs via `ProductImage`. Missing images are not fabricated. A mixed seller page fails validation rather than silently leaking supplies to Buyers.

**Confirmed source defect:** Android sent lowercase `sellerType=supplier` / `farmer`. In both inspected shared/Supplier backend services the String goes unchanged to a native repository query `p.seller_type = :sellerType`. `Product.sellerType` is `@Enumerated(EnumType.STRING)` (SUPPLIER/FARMER in storage), while Jackson serializes response enums lowercase. This can return empty feeds despite approved stock. OpenAPI merely declares a String and does not document this distinction. Changed the repository to send uppercase enum names with `Locale.ROOT`, preserving lowercase response validation and role separation. Added a regression test exercising repository selection for both purchasing roles. No fallback to an unrestricted catalog was added.

This explains missing results against the inspected backend, **not** the entire reported wrong-marketplace symptom. No authenticated deployed listing sample or installed APK comparison was available. The older `BuyerScreen` accepts untyped `Product` lists and defaults to `mockProducts`, but current live navigation uses `BuyerShell`, not that screen. Older builds or another frontend using unfiltered `GET /products` can show a mixed catalog: that backend method returns all active in-stock seller types. Do not infer a deployed data corruption or routing reversal without checking it.

Supplier listing path:

- Android `SupplierApi`: POST/PUT `products`, owner listing `products/seller/{id}`, SUPPLIER categories, product image routes. `CanonicalProductRequest` has no seller ID/type or approval status to spoof. Supplier repository validates identity, ownership, seller type, categories, and PENDING_APPROVAL creation; editing/photo operations are currently limited to pending listings.
- Local backend derives seller ID from authenticated principal and seller type from account role. New products are PENDING_APPROVAL. Owner/Admin checks protect modifications; Admin moderation activates products. Updates retain ownership/type. Images are stored separately and returned in product responses.
- Category validation gap: `resolveCategoryId(request,sellerType)` accepts an ID without checking category type/active status, and name lookup is not scoped by seller type. Android filtering is useful but server validation is required for direct clients.
- Search filters ACTIVE only, not available stock, so Android filters unavailable rows after pagination; pages can appear empty while later pages contain stock. Backend should filter `stock_qty - reserved_qty > 0` before counting/paging.
- `ProductVisibility` permits any ACTIVE product to be read, independent of purchasing role. UI separation is not backend authorization. Daniel must decide whether cross-catalog public viewing is allowed; purchasing and catalog audience rules must be explicit.
- Farmer purchasing remains disabled in current detail UI pending order authorization confirmation. The current implementation provides browsing, not a completed Supplier-to-Farmer checkout workflow.

### Required backend catalog changes / deployment checks

1. Bind search seller type to validated `SellerType` and normalize to `.name()` before the native query (accept lowercase and uppercase); publish enum values/default/audience semantics in OpenAPI. Reject unknown seller types instead of dropping the filter.
2. Scope marketplace listing/search/detail/category/image access to the defined audience: Farmer -> Supplier inputs; Buyer -> Farmer produce. Preserve owner/Admin management reads. Do not use unrestricted `GET /products` as a Buyer feed. Apply equivalent rules to every catalog route if strict cross-marketplace visibility is required.
3. Validate active category and category type against the principal-derived seller type on creation/update. Restrict selling to Farmer/Supplier; never trust client role, seller ID/type, category label, or status as authority. Keep approval server controlled.
4. Filter approved/available stock in the database before pagination. Include real image URLs, prices, stock/reservations, unit, minimum quantity, and details. Use the same policy for search and direct lookup.
5. Verify order authorization explicitly supports Farmer purchasing Supplier listings and Buyer purchasing Farmer listings, with per-item audience/availability checks and atomic reservations. Do not simply allow every seller/purchaser combination.
6. With authorized read-only access, compare actual rows' seller ID, account role, seller type, category type, moderation status, and available stock; compare deployed revision and installed APK URL/hash to this working tree. Correct proven historical misclassification only through a reviewed migration preserving owners, order history, and audit records. No blind seller-type swap.

### Follow-up 2: Farmer marketplace repository, service, ViewModel, filters, UI

Inspected the current working tree again on 2026-10-27. The purchasing path is distinct from `ProductRepository`/`FarmerViewModel`, which manage the Farmer's own crops. The Supplies path is:

`FarmerScreen` Supplies shortcut / `FarmerShell` SUPPLIES destination -> `FarmerSuppliesScreen` -> shared `MarketplaceScreen` -> `MarketplaceModel` -> `MarketplaceRepository` -> Retrofit `MarketplaceApi` -> `ApiClient` authenticated client.

| Layer | Implementation | Findings |
| --- | --- | --- |
| Service | `marketplace/Marketplace.kt:29`, `data/remote/ApiClient.kt` | Search, typed categories, and product detail GETs; 20 records/page, newest ID first; name/category filters sent to server; canonical Gson and shared auth interceptor. No separate marketplace service class or fallback to the Farmer's own-crop API. |
| Repository | `Marketplace.kt:51` | Canonical non-demo session guard, generation/user/role checks before and after calls; FARMER requests SUPPLIER; trims blank name to null; validates page number, last flag, content, and duplicate IDs. Detail validates requested ID and seller type. |
| Filters | `Marketplace.kt:40` and `:45` | Supplier-only projection for Farmers; ACTIVE and positive stock minus reservations; active SUPPLIER categories only. A wrong seller type aborts the page; it is never silently reclassified. The prior query casing fix remains in place. |
| ViewModel | `Marketplace.kt:102` | StateFlow, viewModelScope requests, single in-flight page load, category failure independent of products, explicit Load more, ID deduplication across pages. Previous data survives list failures. Repository/API/session globals are hardcoded, limiting direct ViewModel tests. |
| UI | `Marketplace.kt:143` | Supplies title for Farmers, explicit search button, category dropdown, pull refresh, retry, load more, list/detail navigation; real prices, available quantities, description, minimum order, and backend images. No cart/checkout; purchasing is explicitly unavailable. |
| Images | `ui/screens/ProductImage.kt` | Real URL fetch, origin allowlist, no cross-session bitmap cache, bounded download and downsampling, loading/error/retry UI. A listing with zero images shows no image placeholder in its card. |

Additional issues found, prepared for a subsequent implementation change:

1. **Failed filter retry loses intent:** `MarketplaceModel.refresh` commits `query`/`category` only on success. If a new search/category request fails, the Retry button calls `refresh()` with the previous successful filters. Store attempted filters separately from the displayed result filters and retry the failed request explicitly; continue identifying old results as stale.
2. **Visible search and applied search can diverge:** the text field uses local `remember` state; category selection, pull refresh, and Retry use the ViewModel's last successful query rather than the currently edited text. This is defensible only with explicit applied-filter semantics. Choose one consistent policy and label stale results; key/reset the draft query when the model/session changes.
3. **Empty-state message can be misleading:** available-stock filtering runs after server pagination, so an empty first page with `last=false` shows “No available published products match” even though later pages may contain supplies. Distinguish an empty current page from exhausted results, and move availability filtering into the backend query before pagination.
4. **Detail cancellation race:** `close()` cancels the old detail job and immediately allows a new `open()`. The old coroutine's unconditional `finally` can later clear `detailBusy` for the newer request. Its error handler is also not guarded by selected ID/request identity. Guard success/error/finally with a request generation and clear/replace jobs consistently.
5. **Duplicate initial load opportunity:** both ViewModel `init` and screen `LaunchedEffect(model)` initiate refresh. The busy guard usually coalesces them, but a fast completed init request permits an unnecessary second load. Make one owner responsible for initial loading and handle screen reentry separately.
6. **Category state validation:** category IDs are not checked for duplicates, and a selected category that becomes inactive remains applied while the dropdown can label it “All categories.” Validate category identity uniqueness and reconcile removed selections explicitly.

Existing repository tests cover role separation, unavailable/unpublished products, wire request parameters, enum casing, invalid pagination/duplicate rows, and late replies after logout. They do not cover the ViewModel filter-retry or cancellation races above. This follow-up was a source inspection; no additional runtime code changes or test runs were made.

## Acceptance checks before release

### Follow-up 3: Buyer marketplace repository, service, ViewModel, filters, UI

Source inspection on 2026-10-27 confirms the active Buyer path:

`authDestination(BUYER)` -> `AppNavigation.kt:155` -> `marketplace/BuyerShell.kt:9` -> Marketplace tab -> `MarketplaceScreen` -> `MarketplaceModel` -> `MarketplaceRepository` -> `MarketplaceApi` using `ApiClient.marketplaceApi`.

There is no separate Buyer catalog repository/service/ViewModel. Buyer and Farmer purchasing screens intentionally share these components, with distinct seller-type selection. `ProductRepository` manages Farmer-owned crops and is not the Buyer catalog source. Buyer Orders is a separate screen/API and does not populate catalog rows.

| Layer | Buyer-specific behavior | Assessment |
| --- | --- | --- |
| Navigation/UI shell | `BuyerShell` offers Marketplace and Orders tabs; `AppNavigation` keys the shell by session generation | Current production source calls this shell, not legacy `BuyerScreen`. Switching tabs does not change seller filter. |
| Service | GET `products/search?sellerType=FARMER`, optional name/categoryId, page/size, sort=id,desc; GET `categories/type/FARMER`; GET `products/{id}` | Existing shared endpoint is appropriate with the explicit FARMER filter. No use of unfiltered GET `products` in this path. |
| Repository | BUYER maps to farmer, search sends uppercase FARMER, categories require farmer type and active=true; checks page/session integrity | Correct catalog request and category scope. Supplier responses fail validation for both list and direct detail. No supplier-to-farmer relabeling. |
| Projection | `marketplaceProduct` checks sellerType, then ACTIVE and available quantity > 0; canonical mapping validates IDs, price, stock/reservations, status and images | Previously captured six Farmer rows yield four visible Buyer rows: 277, 133, 130, 127. Rows 189 and 126 are correctly excluded because fully reserved. Supplies are absent from that API response. |
| ViewModel | Same `MarketplaceModel` as Farmer Supplies; scope/captured session, immutable StateFlow, paging, deduplication, old rows retained on failures | Shared filter-retry, detail cancellation, initial-load duplication and empty-page issues from follow-up 2 also affect Buyers. No separate Buyer-specific ViewModel fetch can override sellerType. |
| Active filters | Explicit server-side product-name search and category ID; fixed newest-first sort | Does not implement price sorting, county/farmer-name search, location hubs, or the legacy promotional category chips. Do not confuse those legacy controls with the active API filters. |
| Active content | Heading “Farmer marketplace” on Buyer account; product name, KES/unit, available stock, seller user ID, backend image URL; detail description/minimum order/gallery | Heading names the source; it may be ambiguous to Buyers but does not reverse routing. No fake seller name, county, price or photo. Checkout is unavailable in shared detail UI. |

Legacy `ui/screens/BuyerScreen.kt:111` remains in source with `products = mockProducts`, mock orders, hardcoded greeting/promotion/hub/category price hints, local text/category filtering and price sorting. Its `filteredProducts` does **not** enforce seller role/type, backend approval, or available stock; “All Fresh” accepts any supplied product. Therefore it must not be reconnected to live mixed catalog results. Repository-wide main-source call search found its declaration and an unused import in `AppNavigation`, but no active call. This is a dormant integration risk, not proof that the current APK uses fabricated data or displays Supplier listings.

Prepared Buyer-specific regressions, pending implementation approval: assert BUYER navigation renders `BuyerShell`; mock a FARMER search response and verify four eligible rows versus reserved-out/pending fixtures; inject a Supplier row and confirm a contract error without rendering it; reject Supplier direct-detail IDs; verify FARMER-only active category menu; test search/category/failed-retry/load-more behavior, tab reentry, loading/error/empty states, logout races, and absence of legacy promotional/demo content. Existing repository role and filter tests already cover part of this behavior; no new runtime code or tests were changed for this follow-up.

### Implementation plan and regression preparation (no further implementation authorized)

| Phase | Exact Android files | Planned change / dependency |
| --- | --- | --- |
| 0: Confirm contracts | This report and captured OpenAPI/public response projections | Daniel confirms mandatory login challenge/verify/resend schemas, status/error codes, credential binding, session migration, logout semantics, category validation, and Farmer purchasing rules. New route names remain proposals only. |
| 1: Catalog compatibility | `app/src/main/java/com/example/farmapp/marketplace/Marketplace.kt`; `app/src/test/java/com/example/farmapp/integration/ApiIntegrationAuditTest.kt` | Review existing uppercase search edit now confirmed by public reads; retain seller ownership/type checks for both audiences. No unfiltered fallback. Resolve filter retry/empty state/cancellation issues described above after agreement on UI behavior. |
| 2: Login transport | `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalAuthDtos.kt`; `network/canonical/CanonicalBackendApi.kt`; `CanonicalAuthService.kt`; `CanonicalAuthErrors.kt`; `AuthLoginDiagnostics.kt` | Model the confirmed challenge distinctly from tokens; add only confirmed routes and error schemas; no session from password-stage response; keep registration ownership OTP separate. Paths in this row after the first share `app/src/main/java/com/example/farmapp/`. |
| 3: Login state/UI | `app/src/main/java/com/example/farmapp/repository/AuthRepository.kt`; `viewmodel/AuthViewModel.kt`; `ui/screens/LoginScreen.kt`; `ui/screens/auth/EmailVerificationScreen.kt` | Dedicated credential-bound login challenge state, automatic screen, server TTL/cooldown, stale-response guards, discard password, session save only from verified token response, preserve profile completion without repeat registration. |
| 4: Session migration | `app/src/main/java/com/example/farmapp/network/TokenManager.kt`; `AuthValidation.kt`; `AuthTokenInterceptor.kt`; `EncryptedSessionPersistence.kt`; `ui/navigation/AppNavigation.kt` | Align restore/refresh/logout with server-verified OTP session version/proof; reject legacy sessions, validate restored identity on server, retain atomic state and origin isolation. Do not invent a client boolean as proof. |
| 5: Tests | Existing test files below; planned `app/src/test/java/com/example/farmapp/marketplace/MarketplaceModelTest.kt`; planned instrumented `app/src/androidTest/java/com/example/farmapp/marketplace/MarketplacePresentationTest.kt` | Inject API/session/clock/dispatchers into marketplace/login state controllers as needed. Use existing JUnit, coroutines, Retrofit/OkHttp mock interceptors and Compose test tooling; no live auth or new third-party dependency presumed. |

All abbreviated paths in phases 3-4 have the same `app/src/main/java/com/example/farmapp/` prefix. Backend implementation is owned by Daniel and requires approval before any local backend code edits. Rollout order: backend enforcement and migration verified in isolated tests -> confirmed OpenAPI -> Android integration -> mocked/Compose tests -> explicitly authorized deployment validation. Until then, mandatory OTP cannot be declared complete.

Prepared synthetic regression matrix (test specifications, not claims of implemented coverage):

| Test file / target | Cases and required assertions |
| --- | --- |
| Existing `network/canonical/FarmerAuthenticationFlowTest.kt` | Current verified/unverified characterization; explicit verification flag across roles; exact ownership-required 403; no session on incomplete challenge. Current verified-role test deliberately confirms bypass and must change when the backend contract changes. Parameterize future mandatory flow over FARMER, SUPPLIER, BUYER, INSPECTOR, DRIVER, ADMIN and each backend-confirmed new role, with emailVerified true and false. Every password response yields pending challenge and zero session writes. |
| Existing `network/canonical/CanonicalAuthRecoveryTest.kt`, `network/AuthRepositoryTest.kt` | Wrong password/no challenge; successful password/challenge but no tokens; malformed/mixed token+challenge response fail closed; verify response identity/role/origin validation; Supplier/Farmer profile continuation; no duplicate registration. Successful login-OTP verification alone causes one atomic persistence/navigation transition. |
| Existing ownership-OTP controller tests in `FarmerAuthenticationFlowTest.kt`; planned dedicated login challenge controller tests after confirmed schema | Six-digit/leading-zero code; invalid/non-ASCII length; rejected, expired, consumed/reused, superseded, wrong-account/context/purpose challenge; 429 cooldown; resend rotates challenge without resetting attempts; verify failure never publishes session; cancellation/process death/new account/stale response cannot continue old flow. Backend stateful mocked tests, not an Android mock alone, must verify atomic consumption and mail enforcement. |
| Existing `network/SessionStateTest.kt`, `network/RefreshCoordinatorTest.kt` | No persistence before verify; restore rejects legacy/non-OTP session after migration; no challenge token accepted as access/refresh; old refresh rejected; rotated pair atomic; changed identity rejected; parallel refresh single-flight; refresh/logout races cannot resurrect sessions; token family replay/revocation backend tests required. Existing legacy-restore tests characterize old behavior and require migration updates. |
| Existing `integration/ApiIntegrationAuditTest.kt`, `supplier/SupplierModuleTest.kt` | Supplier created pending with principal-derived seller ownership; Farmer requests SUPPLIER and receives supply; Buyer requests FARMER and receives produce; same API route has different correct filters; wrong seller/owner rejected; active category type checked; no unrestricted fallback. Synthetic products only. |
| Planned `marketplace/MarketplaceModelTest.kt` | Initial loading -> success/empty/error; categories error does not block products; failed new filter retries same intent; filtered-empty nonfinal page offers more; approved available only; rejected/pending/suspended/unlisted/archived/deleted and reserved-out excluded; pagination/duplicates; API 401/403/404/429/5xx; prior rows explicitly stale on failure; late logout and close/open detail cancellation race; no demo/mock fallback. |
| Planned `marketplace/MarketplacePresentationTest.kt` | Loading/empty/error/retry UI, active filter labels, Supplier-to-Farmer and Farmer-to-Buyer titles/content, category/search/load more, real synthetic image response/error placeholder, unavailable purchase action. Mock all services/image transport; never call deployed login or create listings. |
| Daniel's isolated backend tests | Both email ownership states across every role; password validated before code issuance; no usable access/refresh/cookie before verify; expiry/cooldown/send/attempt limits; concurrent verify/replay; mail failure; challenge binding; session migration/revocation; audience/stock/category/ownership queries and Farmer order authorization. Android mocks cannot prove backend enforcement. |

Test paths above are relative to `app/src/test/java/com/example/farmapp/` unless explicitly labeled instrumented/planned full paths. No new tests for the unconfirmed OTP contract were implemented; these specifications are ready to translate once Daniel confirms it.

Local validation: `:app:testDebugUnitTest` filtered to `ApiIntegrationAuditTest`, `AuthRepositoryTest`, and `CanonicalAuthRecoveryTest` passed: 49 tests, zero failures/errors/skips. This includes the new repository seller-query regression and existing catalog/session/ownership-OTP checks; it does not validate the proposed login-OTP backend. Used Android Studio's bundled JBR because the shell's original JAVA_HOME pointed to a missing JDK. Tracked diff whitespace check passed. No APK was installed or service deployed.

Follow-up validation: existing `FarmerAuthenticationFlowTest` (13), `RefreshCoordinatorTest` (8), `SessionStateTest` (8), and `ApiIntegrationAuditTest` (21) passed: 50 tests, zero failures/errors/skips. These use synthetic/mocked data and characterize current behavior, including verified-email OTP bypass; they do not prove the new mandatory requirement. The repeated integration suite overlaps the earlier 49-test run, so the runs are not 99 distinct tests.

- For every supported role and both previously verified/unverified email: valid password starts a fresh email challenge and yields no usable session. Wrong password sends nothing. Direct resource requests and refresh with preauth context fail.
- Correct six-digit code (including leading zeroes) yields a session once. Wrong, expired, old-resend, replayed, wrong-purpose/account/context codes fail; concurrent verifies yield at most one session. Resend and repeated login cannot reset attempt budgets. Check mail failure and 429 retry metadata.
- Verify pre-migration token rejection, OTP-backed refresh rotation/replay handling, restoration validation, logout revocation, account suspension/email/password changes, cancellation/process death, stale async replies, and role navigation only after verification.
- In mocked or isolated synthetic fixtures, represent one Supplier input and one Farmer produce listing; verify pending items absent; simulate approval and stock; Farmer sees only input and Buyer only produce, including search/category/pagination/detail/image paths. Reserved-out, unlisted, rejected, archived/deleted products remain absent. Try tampered seller/category/status requests and nonowner updates. Do not create live accounts or listings.
- Exercise Supplier-to-Farmer orders only after backend audience authorization is implemented and documented. No live acceptance was attempted in this investigation.
