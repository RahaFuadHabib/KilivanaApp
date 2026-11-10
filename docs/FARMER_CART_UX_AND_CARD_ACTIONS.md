# Farmer cart visibility and product-card actions

## Root cause

The previous server write and quantity record could succeed, while the user saw only a technical red banner. Product metadata was cleared immediately after adding and before a refresh. The Cart screen then depended on fresh catalogue reads for names. Listing cards had no cart action at all, so adding required opening product details and gave no compact card-level acknowledgement.

## Implemented changes

- Farmer catalogue cards now have a 48dp cart icon on the right of the details area. Existing image, name, price, available quantity and seller label remain. Descriptions are shown up to two lines when the Farmer action slot is supplied.
- Tapping the icon adds the smallest valid integer quantity meeting the product minimum, through the existing authenticated repository. It stays on the listing. The existing details screen still supports choosing a quantity.
- Each card shows Adding, Added with saved quantity, or an error/reconciliation state. Global busy protection prevents rapid duplicate writes. An uncertain durable write disables subsequent card writes and shows Check cart. Definite rejected writes do not create Added state.
- The Cart tab shows the total saved quantity immediately after a verified acknowledgement. Success messages use the primary color, with red reserved for request errors.
- Successfully acknowledged products retain their display metadata in memory. Their names are saved separately from quantities under the existing user/API-origin-scoped server-selection storage. Restart restores these labels. A failed catalogue read preserves the acknowledged entries and labels.
- Cart rows show product name, selected quantity and Added to cart. A short supporting explanation distinguishes these acknowledged selections from full authoritative server lines. A refresh failure does not make acknowledged items disappear.
- Historical local-only basket data, transaction journals and ambiguous-write reservations remain separate and preserved. No backend contracts, payment activation or checkout guarantees were changed.

The saved display name is a label, not a price or stock guarantee. Estimates and checkout still require current product/quote verification. The displayed quantity is the sum of acknowledged selections from this installation and is not presented as a complete cross-device server cart.

## Exact Kotlin files

Production paths are relative to `app/src/main/java/com/example/farmapp`.

| File | Change |
|---|---|
| `marketplace/Marketplace.kt` | Optional card action slot and right-aligned details/action layout. Default callers receive no new action. |
| `farmerpurchases/FarmerCardCartAction.kt` | New cart icon, loading, added and error presentation. |
| `farmerpurchases/FarmerPurchaseController.kt` | Per-product operation/error state, retained acknowledged metadata, restored labels and brief success feedback. |
| `farmerpurchases/FarmerPurchasePersistence.kt` | `server-cart-labels-v1` alongside existing `server-cart-selections-v1`. Historical `local-basket` stays untouched. |
| `farmerpurchases/FarmerPurchasesScreen.kt` | Farmer action wiring, total-quantity badge, success/error colors, visible acknowledged cart rows and details feedback. |

Test files

- `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerPurchaseTestFixture.kt`
- `app/src/test/java/com/example/farmapp/farmerpurchases/FarmerCartRepositoryTest.kt`
- `app/src/androidTest/java/com/example/farmapp/farmerpurchases/FarmerPurchasesPresentationTest.kt`

Existing JVM coverage now also checks that acknowledged names/quantities survive a controller restart and failed catalogue refresh. The device UI test uses the production card layout and action, confirms the parent card was not opened, verifies Added, Cart quantity and named row visibility, and removes the item through the mocked cart API. Additional UI cases cover loading/disabled taps and uncertain failure without fake Added state.

## Verification

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Compilation, both APK builds and **645/645 JVM tests passed**, zero failures/errors. Evidence is `farmer-cart-ux-build.log` and Gradle JVM XML. Whitespace and changed-source UTF-8 validation passed.

Device verification uses direct instrumentation, retaining the installed app data. The selected set contains 49 cases covering Farmer card/cart, storage, transaction safeguards and existing Buyer presentation/navigation/delivery regressions. All cart/order/payment writes in these tests use synthetic API/session fixtures. Actual device test result follows below in the final checkpoint.

The Samsung USB debugging connection was initially offline and was reconnected before installing. The debug package is `com.example.farmapp.dev2231918adc4d4b9d`, with matching `.test` instrumentation target. Installation uses replacement without clearing app data. The device is kept awake during the selected suite and restored afterward.

## Remaining backend limitation

The deployed CartResponse still omits item lines, item IDs, quantities and totals. Android can retain and display its own acknowledged selections, but cannot reconstruct another device's cart or prove server-side line changes from metadata alone. Full authoritative synchronization requires additive backend line readback. This task changes no backend source, SQL, configuration or database.

No live authenticated cart mutation or financial transaction was performed during testing. Farmer order submission and M-Pesa activation remain disabled. Existing uncommitted changes remain preserved, and no commit or push was made.
## Final checkpoint

- Compilation and APK builds passed. JVM tests passed **645/645**, zero failures/errors.
- The final selected device suite passed **49/49** on Samsung SM-A155F. `farmer-cart-ux-device-final.log` ends `OK (49 tests)`. This is a selected regression suite, not the complete project instrumentation suite.
- The first host-streamed run lost USB after five recorded passes. No complete pass is claimed for that attempt. The rerun saved output on the phone through a detached shell runner, and the final log was retrieved after completion.
- Both updated APKs installed successfully using replacement installation. Package is `com.example.farmapp.dev2231918adc4d4b9d`, with matching `.test` target. MainActivity was relaunched after testing. Original stay-awake setting 0 was restored by the device-side test script.
- Debug APK is `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `63C31F45611505D11BDAD8BA0FC4752AFE737B6575356F0623D9164CC44822DE`.
- Existing Buyer regressions in the selected suite passed. Supplier and Inspector production flows were untouched. Historical local-only basket data remains separate.

All add/remove/order/payment writes exercised by automated verification used controlled mocks. Genuine backend cart-line synchronization remains limited by the published metadata-only response. No real financial transactions, backend changes, commits or pushes occurred.