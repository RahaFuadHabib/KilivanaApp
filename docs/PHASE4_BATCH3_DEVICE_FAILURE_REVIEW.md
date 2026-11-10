# Phase 4 Batch 3 device failure review

8 November 2026. The original three failures are corrected and verified. Full-suite acceptance remains blocked by additional failures described below.

## Recorded failures

Evidence came from `app/build/outputs/androidTest-results/connected/debug/TEST-SM-A155F - 16-_app-.xml` and the corresponding class HTML reports under `app/build/reports/androidTests/connected/debug/`.

| Test | Actual failure and root cause | Correction |
|---|---|---|
| `ExampleInstrumentedTest.useAppContext` | `ComparisonFailure` at line 22. Expected `com.example.farmapp`, actual `com.example.farmapp.dev2231918adc4d4b9d`. Debug application ID configuration uses a backend-specific suffix in `app/build.gradle.kts`. | Compare target context package with `BuildConfig.APPLICATION_ID`. |
| `InspectorUiTest.dashboardOpensOwnInspectionAndBackReturnsToList` | `AssertionError` at line 29. Expected one node containing `Synthetic note`, found two. `InspectorShell.kt` renders saved notes and prepopulates editable findings notes with the same value. | Select the saved text with `hasText` and exclusion of `hasSetTextAction`. The detail and back-navigation assertions remain. |
| `InspectorUiTest.statusSubmissionRequiresConfirmationAndUploadStaysDisabled` | `AssertionError` at line 72. `performScrollTo` could not find the upload node. The upload button is a separate lazy-list item after the workflow and privacy explanation in `InspectorShell.kt`. | Use the lazy container's `performScrollToNode` to compose the item, then assert the upload button is disabled. Confirmation and submission-count assertions remain. |

These fixes modify only `app/src/androidTest/java/com/example/farmapp/ExampleInstrumentedTest.kt` and `app/src/androidTest/java/com/example/farmapp/inspector/InspectorUiTest.kt`. Production code changes were unnecessary for these recorded failures.

## Verification record

User-supplied baseline was 627 JVM tests passing and 165 device cases with three failures. The first corrected targeted run compiled app and instrumentation Kotlin successfully. It executed five cases, passing the context test and failing all four Inspector cases with `IllegalStateException: No compose hierarchies found`. A subsequent device query reported `mWakefulness=Dozing`. The device was awakened and keyguard dismissal requested before the next targeted run. This separate device-state failure prevented that first run from verifying the Inspector fixes.

Commands use a process-local `JAVA_HOME` of `C:\Program Files\Android\Android Studio\jbr` and Gradle's existing cache with approved access.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.farmapp.ExampleInstrumentedTest,com.example.farmapp.inspector.InspectorUiTest' --offline --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --offline --console=plain
```

Logs are `phase4-batch3-targeted-device.log`, `phase4-batch3-targeted-awake.log` and `phase4-batch3-complete-device.log`. Final outcomes must be read from those runs before acceptance. JVM reruns are required if subsequent production changes become necessary.

## Final device outcomes

| Run | Executed | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| Corrected classes, phone initially dozing | 5 | 1 | 4 | 0 |
| Corrected classes after waking phone | 5 | 5 | 0 | 0 |
| Complete device suite | 165 | 161 | 4 | 0 |
| Additional synthetic classes rerun unchanged | 24 | 24 | 0 | 0 |

The complete suite XML was preserved as `phase4-batch3-complete-device-results.xml`. It confirms all five cases in the corrected classes passed, including the three original failures. The complete suite encountered these additional failures.

| Test | Full-suite failure | Isolated result |
|---|---|---|
| `BuyerAuthenticationPhaseOnePresentationTest.sharedBuyerRoleSignupContinuesWithoutDuplicateRegistration` | Missing `Buyer` node during click at line 128. | Class rerun passed. |
| `BuyerDeliveryPresentationTest.selectingExistingAddressSurvivesMethodSwitchAndReviewShowsNoInventedFee` | Missing `delivery-review` node at line 76. | Class rerun passed. |
| `BuyerShoppingPresentationTest.savedEmptyStateHasShoppingAction` | ActivityScenario host already destroyed. | Class rerun passed. |
| `InstalledBuyerHomeSmokeTest.retainedBuyerSessionOpensActualBrianHome` | Brian active/HOME presence assertion failed at line 37. | Requires retained authenticated Buyer session. Runtime reason for failing to reach Home remains unverified. |

The first three additional failures did not reproduce across the unchanged 24-case class rerun. Their precise intermittent causes remain unresolved. No Buyer code or tests were altered to address them. The installed-session smoke failure prevents full-suite acceptance. The earlier progress update grouping the first three as shopping host failures was superseded by the exact XML traces above.

`phase4-batch3-additional-device.log` records the 24-case rerun. `stay_on_while_plugged_in` was read as `0`, temporarily enabled for testing and restored to `0` in a `finally` block. App and instrumentation Kotlin compilation succeeded. No production code changed, so the user-reported 627/627 JVM result was retained without a new JVM run. Git diff whitespace checks passed for the two changed test files. Earlier uncommitted files were preserved. No commit or push occurred.

## Remaining transaction blockers

The Batch 2 guard still blocks Farmer cart access, checkout quotes, order submission and payment initiation. Previously documented backend dependencies remain authoritative cart lines and processing-cart recovery, durable ownership-bound quotes, atomic order idempotency and ambiguous-request reconciliation, and durable payment intent plus callback reconciliation. Deployed revision and authenticated Farmer authorization also require verification. This device test work supplies no additional transaction activation authorization.
