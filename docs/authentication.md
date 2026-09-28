# Android authentication

The normal debug and release builds use the real backend. Sign In keeps the existing presentation and delegates to AuthViewModel, AuthRepository, and Retrofit. Signup and password recovery remain presentation-only; no signup or recovery contract has been added.

## Contract

The configured base URL already ends in /api/v1/. ApiService uses only auth/login, auth/refresh, and auth/logout.

- Login sends only email and password. The password is not trimmed or persisted.
- Login/refresh responses preserve userId (UUID string), email, fullName, role, accessToken, refreshToken, and refreshTokenExpiresAt (ISO-8601 date-time string).
- Refresh and logout send only refreshToken.
- No /auth/me schema, JWT-claim-based routing, or production credentials are introduced.

The response role controls navigation. FARMER, BUYER, SUPPLIER, and INSPECTOR retain their existing destinations. DRIVER, ADMIN, and unknown roles receive an unsupported-account message and can sign out. Authentication does not supply a phone number; the existing profile receives an explicit unavailable value instead of a fabricated phone.

## Session ownership and persistence

TokenManager remains the single production session owner. Its immutable StateFlow snapshot contains the entire AuthResponse. SessionState is its testable implementation, not another production instance.

EncryptedSessionPersistence is only TokenManager's disk adapter. It uses the platform Android Keystore with a non-exportable AES-256 key and AES/GCM/NoPadding. An AtomicFile in Context.noBackupFilesDir contains the version, fresh IV, and authenticated ciphertext. No security dependency was added. Passwords are never written.

Restoration and writes run on Dispatchers.IO. An invalid, expired, corrupt, or undecryptable session requires sign-in. Logout deletes both the persisted file and its key. Tests use separate file names and key aliases.

## Requests, expiry, and races

- Bearer headers are attached centrally only to requests under the configured API origin and base path.
- Auth endpoints use a separate client with no refresh handling. Both clients disable HTTP logging and redirects; TLS validation is unchanged.
- Access expiry (15 minutes per contract) is handled reactively on HTTP 401, without decoding JWTs. Refresh validity uses the server's refreshTokenExpiresAt, not a guessed client timestamp.
- A Mutex serializes refreshes. Requests from the same old snapshot reuse the updated session, including when a backend rotates only the refresh token.
- The entire new AuthResponse is persisted and published atomically before retrying the original request once. A second 401 ends that session.
- Refresh rejection (400/401/403), invalid responses, and expired refresh tokens clear the session. Transient errors preserve it and apply a five-second refresh cooldown.
- Snapshot identity and session generation checks prevent late requests from restoring logout or overwriting a newer login.
- Logout attempts server revocation with the stored refresh token, then clears locally even on network failure or coroutine cancellation. A server revocation failure cannot guarantee remote revocation, but it does not keep the local session open.

## Explicit offline demo mode

Use this PowerShell command for a separate debug-only demo app:

    .\gradlew.bat :app:assembleDebug '-Pkilivana.mockAuth=true'

The demo app has application ID suffix .demo. Release builds always use real authentication, even if the property is supplied. Demo sessions are memory-only, the existing account dropdown fills the same four demo emails/password, and mock tokens are prevented from reaching production API requests. There is no automatic fallback from real authentication to mock.

Build without the property to return to the real client.

## Verification

JVM tests cover JSON/endpoint contracts, all four existing demo accounts, real-mode failure without fallback, server-authoritative roles, atomic persistence through a test adapter, refresh concurrency, retry limits, token rotation, failure cooldowns, logout races, and cancellation.

Android tests under com.example.farmapp.network use isolated test storage and a test service. They cover actual Keystore encryption/restoration/tamper rejection/deletion and the production Compose-to-ViewModel-to-repository sign-in/logout flow, including an unsupported server role. They do not call the remote backend.

    .\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --offline
    .\gradlew.bat :app:connectedDebugAndroidTest --offline '-Pandroid.testInstrumentationRunnerArguments.package=com.example.farmapp.network'

If Gradle's offline device runner lacks its UTP component, the already built test APKs can be run directly:

    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w -r -e package com.example.farmapp.network com.example.farmapp.test/androidx.test.runner.AndroidJUnitRunner

The six Android tests passed on the connected device using this direct runner. Debug/demo and release compilation also passed, with release mock mode verified false even when the demo property was supplied.

Successful live-backend login/refresh/logout still requires a valid backend test account. Test credentials are not included in this repository.
