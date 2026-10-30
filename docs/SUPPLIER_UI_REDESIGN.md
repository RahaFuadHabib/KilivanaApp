# Supplier UI redesign

Implemented on `feature/supplier`; the authenticated `SupplierShell` is the entry point. The legacy mock SupplierScreen remains disconnected and unchanged.

## Presentation scope

- `SupplierVisuals.kt` supplies a nested Material 3 palette, rounded cards, hero, category icons, status badges, photo placeholders and selectable gallery. No global theme tokens are changed.
- Dashboard uses the existing profile/account greeting and actual listing metrics. No sales, revenue, incoming-order totals or low-stock thresholds are inferred.
- Supplies offers local name/description search and category/status filters over the already loaded owned records. Categories come from SupplierApi. Selecting an unavailable category falls back to all categories.
- Details shows actual price, stock, reservations and minimum order. Pending-only editing and image upload remain unchanged.
- Create/edit preserve drafts, validation, confirmation, request gates and reconciliation. Product photos still follow confirmed product creation.
- Orders, fulfillment, delivery and payments remain explicitly unavailable. Profile remains read-only.
- The existing session-aware ProductImage has optional layout/fallback parameters; default rendering, URL restrictions, session checks, decoding limits and network behavior remain unchanged for existing callers.

## Asset provenance

No licensed farmland photograph was present in the project. The approved fallback is an original Compose linear gradient with procedural contour shapes in SupplierHero, authored for this redesign. It contains no embedded text and requires no network, raster asset or attribution. It is an abstract agricultural background, not a photograph. A future licensed hero can be bundled as an optimized WebP in drawable-nodpi after review.

Category and interface illustrations use the existing Material icon dependency. Category names are matched only to choose a decorative icon; no IDs, product records or categories are invented. Real product images continue to use backend URLs through ProductImage. Missing/unavailable photos are labeled placeholders, never illustrative product photographs.

## Verification

JVM and APK build command (existing cache, JDK 21):

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='C:/Users/AppleFi/.gradle'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

SupplierUiTest retains the existing behavior assertions and adds combined local filtering, published-listing restrictions, StateFlow-driven detail navigation, and gallery selection/unavailable-image coverage. Gallery fixtures use rejected external URLs and require no image-server requests. The standalone editor test now scrolls the larger grouped form before reviewing it. Asynchronous fixture navigation explicitly waits for the rendered destination after StateFlow delivery.

SupplierVisualReviewTest uses explicitly synthetic callbacks/profile/listings, not authenticated sessions or live backend data. It captures Dashboard, Supplies, Details, Edit, Create, Orders, payments and Profile, plus dark mode and a 320 dp / 150% type fixture. Screenshots are saved to the target app's external files directory under supplier-redesign and copied to ignored app/build/reports/supplier-redesign. They demonstrate layouts and placeholder states; they do not prove authenticated image-server availability.

No API, backend, database, authentication or other role business logic changes are part of this redesign. No remote product, photo or payment mutation is required for verification. APK installation uses `adb install -r`, preserving application data.

At larger text scales the bottom bar displays destination icons with accessibility names instead of wrapping labels. Top-bar titles use a single line with ellipsis; form content remains scrollable. Dashboard metric cards in each row share their measured height so wrapped labels remain aligned.

## Final results — 2026-10-22

| Check | Result |
| --- | --- |
| Full JVM suite | 263 passed, zero failures/errors/skips; includes 61 Supplier JVM tests and shared authentication/navigation coverage |
| Debug APK | Built successfully |
| Instrumentation APK | Built successfully |
| Supplier UI + visual review + shared auth/navigation | 23 device tests passed |
| Supplier signup/recovery and Farmer signup regression | 14 device tests passed in a separate run |
| Final narrow-layout toolbar visibility review | Visual review test passed again after adding explicit assertions for refresh, theme and logout visibility |
| Device | Samsung SM-A155F, Android 16 |
| Screenshots | 13 synthetic captures, including 320 dp / 150% type and dark mode |

Earlier exploratory runs found offscreen lazy-row test selectors, missing waits for asynchronous destination rendering, and intermittent missing-Compose-hierarchy failures in existing signup tests. Lazy-row scrolling and new-fixture synchronization were corrected. Final Supplier/auth/navigation and isolated signup runs passed without modifying authentication or signup implementation/tests. Both APKs were installed in place; no uninstall or app-data clearing occurred. The device's original keep-awake setting was restored.

Build/test transcripts are under `app/build/reports/supplier-redesign/logs`. Successful private image loading against a live backend and a manual TalkBack traversal were not performed. Missing/unavailable-image rendering, gallery selection, image-origin/session protections, semantics, touch-target defaults, light/dark layouts and large-type scrolling were checked. No live product, image, profile or payment writes were made.

### Screenshot index

All records below are synthetic test fixtures, not live Supplier account data.

- [Dashboard](../app/build/reports/supplier-redesign/supplier-redesign/01-dashboard.png)
- [Supplies](../app/build/reports/supplier-redesign/supplier-redesign/02-supplies.png)
- [Details](../app/build/reports/supplier-redesign/supplier-redesign/03-details.png)
- [Edit](../app/build/reports/supplier-redesign/supplier-redesign/04-edit.png)
- [Create](../app/build/reports/supplier-redesign/supplier-redesign/05-create.png)
- [Create — stock and review](../app/build/reports/supplier-redesign/supplier-redesign/06-create-stock.png)
- [Orders](../app/build/reports/supplier-redesign/supplier-redesign/07-orders.png)
- [Payments unavailable](../app/build/reports/supplier-redesign/supplier-redesign/08-payments.png)
- [Profile](../app/build/reports/supplier-redesign/supplier-redesign/09-profile.png)
- [Dark profile](../app/build/reports/supplier-redesign/supplier-redesign/10-profile-dark.png)
- [Dark dashboard](../app/build/reports/supplier-redesign/supplier-redesign/11-dashboard-dark.png)
- [Small screen / large type](../app/build/reports/supplier-redesign/supplier-redesign/12-small-large-type.png)
- [Small screen / large-type form](../app/build/reports/supplier-redesign/supplier-redesign/13-small-large-type-create.png)
