# Daniel's shared-backend development APK

This is a debug development configuration. Android calls the HTTPS Spring Boot API, never PostgreSQL. No remote login, registration, product creation, upload, approval or other write was performed during this configuration task. No credentials were searched for or embedded. Do not run `connectedAndroidTest` for this check: the existing opt-in canonical device test creates remote records.

## Configuration and isolation

`gradle.properties` selects the existing `kilivana.apiBaseUrl` property:

```properties
kilivana.apiBaseUrl=https://either-juvenile-progeny.ngrok-free.dev/api/v1/
kilivana.canonicalBackend=true
kilivana.mockAuth=false
kilivana.isolateDevelopmentBackend=true
```

All Retrofit clients, authentication, refresh and image URL resolution use the generated `BuildConfig.API_BASE_URL`. Gradle requires HTTPS for remote backends; HTTP is retained only for local development hosts. Credentials, query strings and fragments are rejected in the base URL. Standard TLS validation remains enabled. HTTP redirects and connection retries remain disabled; authenticated canonical writes are not automatically replayed after a 401.

For real debug builds, isolation adds `.dev` plus the first 16 hexadecimal SHA-256 characters of the base URL to the package ID. Daniel's APK is **`com.example.farmapp.dev2231918adc4d4b9d`**. A different URL builds a separate Android package with separate app storage, Keystore namespace, encrypted sessions, draft journals and photo files. Do not disable isolation when changing environments. Existing local/demo applications are preserved. This suffix is for debug builds only; this task does not establish a release deployment configuration.

Existing session restoration also deletes a canonical session whose recorded backend differs from the selected URL. Account changes advance the session generation and reset the farmer UI. Downloaded image previews are memory-only, scoped to that generation, and late responses from an earlier session are rejected. Draft journals are keyed by user ID inside the environment-specific app storage.

API bearer credentials are limited to the configured scheme/host/port and API path, excluding login/register/refresh routes. Image credentials are limited to that exact origin's `/api/v1/images/` and `/uploads/` routes. Cloudinary is HTTPS-only and token-free; other external image hosts are blocked. No HTTP logging interceptor is installed.

## Read-only connectivity evidence

Observed on 2026-10-16 using unauthenticated GETs with redirects disabled:

| Route | Actual result |
| --- | --- |
| `/api/v1/health` | 200, JSON `UP` |
| `/actuator/health` | 200, JSON `UP` |
| `/api/v1/categories/type/FARMER` | 200, 14 active farmer categories |
| `/api/v1/products` | 200, 4 products, all wire status `active` and seller type `farmer` |
| `/v3/api-docs` | 404 |
| `/api-docs` | 200, OpenAPI 3.0.1 JSON |

Public product responses included the fields required by the Android mapper, and their image URLs used relative `/api/v1/images/...` paths. A repeated products GET using an OkHttp-style user agent, without the ngrok bypass header, also returned JSON 200. Some subsequent requests failed DNS resolution or timed out; availability is intermittent from this workstation. These transport failures have no HTTP status and are not reported as HTTP responses.

## Contracts and deployment limits

Read-only inspection of the shared backend controllers/DTOs and Daniel's deployed `/api-docs` schema confirms these Android request/response shapes. No authenticated operation was executed:

| Operation | Android request and expected response |
| --- | --- |
| Sign in | `POST auth/login`, JSON `email`, `password`; API envelope with `accessToken`, `refreshToken`, `tokenType`, optional `expiresIn`, `user` |
| Refresh | `POST auth/refresh`, JSON `refreshToken`; same token envelope |
| Categories | `GET categories/type/FARMER`; envelope containing categories with numeric IDs and `type: farmer` |
| Seller products | `GET products/seller/{numericUserId}`; envelope containing a product list; owner should see pending listings |
| Create | `POST products`, JSON `name`, `description`, `unit`, decimal `price`, whole-number `stockQty`, `minimumOrderQty`, numeric `categoryId`; expected 201 envelope with product ID |
| Upload | `POST products/{id}/images?sortOrder=0&isPrimary=true`, multipart **`image`** part; expected 201 envelope with image metadata |

The adapter uses JSON creation followed by separate image uploads; it does not send a client-chosen seller or approval status. The backend derives seller identity from the bearer token. In both reviewed working source and the cached upstream main, creation assigns `PENDING_APPROVAL` (JSON `pending`). Android displays this as `PENDING_APPROVAL`. The pending status must be checked in the actual deployment, not inferred from public GETs. The UI accepts whole quantities and prepares images up to 5 MiB.

PR #2's deployed/merged state has not been established. Local source does not prove Daniel deployed it. Owner pending-product/image access, category permissions, account rules and admin filters may differ until Daniel confirms the deployed commit. Never compensate by publishing an approval status or granting broader access from Android.

**Before entering credentials, ask Daniel to confirm bearer-header/raw-token debug logging is removed or disabled.** The cached upstream main contains those log statements; the reviewed working source removes them. Their presence or activation on Daniel's server was not tested. Also obtain an authorized existing FARMER test account and confirm his Admin Portal uses this same API/database. No valid remote test account was supplied in this task.

## Build and physical-phone test

Validation completed: `:app:testDebugUnitTest :app:assembleDebug` succeeded with **152 unit tests, zero failures/errors**, including six new in-memory tests for deployed request shapes, selected-origin image trust and backend-bound session restoration. The localhost override also successfully generated `API_BASE_URL=http://127.0.0.1:8081/api/v1/` and isolated package `com.example.farmapp.dev998383aa6028f889`. The final generated configuration and APK were restored to Daniel's ngrok URL/package. No device tests were run and no APK was automatically installed.

From the Android project in PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
Get-Content app\build\generated\source\buildConfig\debug\com\example\farmapp\BuildConfig.java
Get-Content app\build\outputs\apk\debug\output-metadata.json
```

Confirm the exact ngrok URL, `USE_CANONICAL_BACKEND=true`, `USE_MOCK_AUTH=false`, build type `debug` and package ID above. Android Studio should select the `app` module and `debug` build variant, then sync Gradle before Run. The Gradle property is compiled into the APK; changing it requires rebuilding/reinstalling.

1. Have Daniel resolve the deployment/logging questions and supply an existing farmer account privately. Keep the ngrok tunnel running. Use a phone with Android 8/API 26 or newer and internet access. No `adb reverse` is needed for ngrok.
2. Enable Developer options and USB debugging, connect USB and authorize the computer. Run `adb devices`. If several devices are listed, use `adb -s <serial>` for every following command.
3. Install the reviewed APK, then launch the exact package to avoid opening a local/demo copy:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.example.farmapp.dev2231918adc4d4b9d/com.example.farmapp.MainActivity
   ```

4. After explicit approval for remote account testing, sign in with the authorized farmer email/password in the phone UI. Do not register a new account. Confirm the expected farmer identity, loaded categories and My Crops list. Do not put credentials in shell commands, screenshots, logs or source.
5. **Stop before Publish until creation of one disposable product is explicitly approved.** Prepare Add Crop with one small non-sensitive gallery image, a unique name such as `ANDROID TEST YYYYMMDD-HHMM`, an existing farmer category, quantity `2`, unit `kg`, price `100.25`, and description `Disposable Android integration test; do not approve or sell`. Tap **Review draft** and check the values.
6. After approval, tap **Publish once**. Record the returned product ID from crop details when available, unique name and creation time. Verify it appears in My Crops as pending approval and the photo preview loads. If creation/upload has an unknown outcome, refresh My Crops and inspect the existing listing before retrying. Do not create a second listing to work around a timeout.
7. Have Daniel open his existing Admin Portal Products screen. Verify in browser Network tools that it calls the ngrok host's `/api/v1/admin/products`. Search by unique name/product ID and farmer identity; use its Pending filter or remove filters if necessary. Verify category, price, stock, image and pending state. Pending JSON is `pending`; the source enum/query name is `PENDING_APPROVAL`. Do not approve, reject, edit or delete the test record without separate authorization.
8. Sign out. Sign in with a second authorized farmer account only if provided and approved; confirm the first farmer's products, draft and private previews are absent. Switch back and confirm only the original farmer's own pending listing returns. Reopen the app to verify secure session restoration and image loading.

If Android sees the listing but the portal does not, first compare backend host, account/role, product ID and portal filters. Daniel should check deployment and admin-query compatibility with PR #2. No database or approval changes are part of troubleshooting here.

## Switch back to localhost

Override the existing property for a build, retaining separate package storage:

```powershell
.\gradlew.bat :app:assembleDebug "-Pkilivana.apiBaseUrl=http://127.0.0.1:8081/api/v1/" --console=plain
```

For a physical phone using an already-running local backend, run `adb reverse tcp:8081 tcp:8081` before using that local APK. Phone loopback otherwise refers to the phone itself. This task does not start, restart or modify the local server/database. Inspect the newly generated BuildConfig and output metadata for the local URL/package before installing. To return to Daniel's APK, run the ordinary ngrok build command above and reinstall its APK. Alternatively change only `kilivana.apiBaseUrl` in `gradle.properties`, sync and rebuild. Omitting the property entirely retains the original localhost fallback in `app/build.gradle.kts`.
