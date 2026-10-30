# Backend configuration and historical replacement validation

## Current configuration (2026-10-28)

There is no shared deployed/production backend default. A temporary tunnel must stay in developer-local configuration. Release tasks fail before build/package work unless an explicit HTTPS endpoint is supplied by `-Pkilivana.apiBaseUrl=<url>` or `KILIVANA_API_BASE_URL`. The Gradle property takes precedence. Do not use a transient tunnel for a production release.

Debug endpoint precedence:

1. `-Pkilivana.debug.apiBaseUrl=<url>`.
2. `KILIVANA_DEBUG_API_BASE_URL`.
3. `kilivana.debug.apiBaseUrl` in ignored `local.properties`.
4. Explicit shared endpoint from `kilivana.apiBaseUrl` / `KILIVANA_API_BASE_URL`.
5. `http://10.0.2.2:8081/api/v1/` for an Android emulator's host backend.

URLs must end in `/api/v1/`, contain no credentials/query/fragment, and use HTTPS except supported local development hosts. Debug local settings never supply a release endpoint. Supply the real current Daniel URL privately; no guessed production domain is configured.

Fresh checkouts use `com.example.farmapp.debug`, independent of the backend URL. Release identity stays `com.example.farmapp`. For existing data, retain the original debug suffix in `local.properties`, or provide `-Pkilivana.debug.applicationIdSuffix=.dev<16 lowercase hex digits>`. This machine retains `.dev2231918adc4d4b9d`, and the ignored local Daniel endpoint remains unchanged. No data is copied between packages or backend origins. A changed origin can require a new sign-in and shows a different origin-scoped cart; preserving the package does not disable those protections.

Debug identity precedence: mock `.demo` -> explicit validated legacy `.dev...` pin -> opt-in `kilivana.isolateDevelopmentBackend=true` URL hash -> explicit demo-install `.canonicaldemo` -> stable `.debug`. The URL hash remains available for developers who deliberately want parallel backend installations; it is no longer a shared default. Moving an existing install to `.debug` would create a separate application, so existing installations should retain their pin.

In PowerShell, quote the complete dotted property argument, for example `'-Pkilivana.debug.apiBaseUrl=https://backend.example.test/api/v1/'` (illustrative host only).

For reproducible CI use explicit Gradle properties or environment values rather than copying `local.properties`. That file includes machine-specific SDK/configuration and must never be committed. Use the same signing key when updating an installed package.

## Historical replacement validation (2026-10-27)

The following records the earlier tunnel change, build hashes and device state at that time. Its shared-default and URL-derived-default statements are superseded by the current configuration above; these historical results are not new validation results.

Completed 2026-10-27.

- API base: `https://hankering-morphing-outclass.ngrok-free.dev/api/v1/`.
- Active settings updated in `gradle.properties` and the local debug override in `local.properties`. Historical investigation documents were retained.
- GET `/api/v1/health` returned HTTP 200 with `status: UP`, application `kilivana-backend`, server timestamp `2026-10-27T12:50:48.910179249`. This verifies health at check time, not authenticated workflow acceptance.
- GET `/api-docs` returned HTTP 200. Snapshot: `kilivana-replacement-openapi-20261027.json`. Compared with `kilivana-login-marketplace-openapi-20261027.json`: all 221 paths, component schemas, security requirements and security schemes are unchanged; only top-level servers differ. No advertised breaking integration changes found. Mandatory every-login OTP remains dependent on the previously documented backend changes.
- Marketplace filters preserved: Farmer Supplies uses `SUPPLIER`; Buyer produce uses `FARMER`. Mocked transport test now checks both uppercase values; repository tests retain role separation.
- Login HTTP 403 mapping now distinguishes a recognized unsuccessful backend FORBIDDEN envelope from an unrecognized gateway/non-contract response. The latter displays service unavailable, retains privacy-safe HTTP diagnostics, and establishes no session. Existing recognized permission and ownership-OTP handling remains intact. Added synthetic plain-text/ngrok, HTML, missing/unknown envelope and invalid-success cases.
- Debug identity normally hashes the backend URL. Added a validated local debug install-suffix override and pinned it to `.dev2231918adc4d4b9d` on this machine. This preserves `com.example.farmapp.dev2231918adc4d4b9d` for an in-place update; other machines retain URL-derived isolation unless explicitly configured. Session-origin guards remain unchanged; old-origin credentials are not reused for the replacement origin. No app launch or manual app-data clearing was performed.
- Generated BuildConfig and APK output metadata confirm the new API URL and original application ID; canonical backend true, mock auth false. APK DEX inspection found the replacement URL, zero old-origin strings, and zero `/api/v1/api/v1/` strings. Mocked Retrofit URL checks verify one API prefix.
- Validation: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` completed successfully. 319 unit tests across 38 suites, zero failures/errors/skips. Lint: zero errors, 51 warnings. No unrelated lint cleanup was performed.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `F0AC16EDD507B9B9909CB1ABA55B4D8040342F736A70CB153ADFA7D02F41C210`.
- Installed on Samsung `RF8WC0K043H` with `adb -s RF8WC0K043H install -r app/build/outputs/apk/debug/app-debug.apk`: `Success`. No uninstall or data-clear operation.

No live login, registration, OTP, account changes or product mutations were performed. Backend code and existing uncommitted application work were preserved; no reset, clean, stash, commit or push.
