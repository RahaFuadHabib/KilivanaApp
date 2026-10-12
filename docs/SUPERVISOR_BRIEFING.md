# Kilivana — Supervisor Briefing

## 3–5 Minute Speaking Script

My Android development work on Kilivana has focused on the authentication foundation and the user experience, especially the Farmer area. Kilivana is an agricultural platform with Farmer, Buyer, Supplier, and Inspector roles. The application is still under development. The account and session foundation is implemented, while much of the marketplace experience currently demonstrates the intended interactions using sample data.

The main thing I have completed is the Android flow for signing in and maintaining a session. The app sends the user's entered credentials to the authentication API, validates the response, and uses the role returned by the server to decide which area to show. It handles loading states and errors, restores a saved session when the app starts, and lets the user log out. This gives the other features a consistent way to know who is signed in.

I have also implemented signup in the current working tree. It supports the four public roles and collects the common account details plus the fields needed for each role. Farmers provide a farm name and location, Suppliers provide a business name, and Inspectors provide an employee code. The Android code validates the inputs, prevents repeated submissions while a request is running, and saves the session returned after successful registration. This signup work and the new Inspector routing are implemented but still uncommitted.

Inspector accounts need an additional check. The Android app only shows the Inspector dashboard when the server's session response contains the exact status VERIFIED. Pending, unverified, missing, or unknown status leads to an Awaiting Verification screen with a logout option. This means the signup role selector cannot give someone access to the inspection dashboard. Administrator verification itself belongs to the backend workflow. The app can use the updated status on a fresh login, or when its token-refresh mechanism receives an updated response. There is no polling button on the waiting screen yet.

One technical challenge was keeping sessions securely without saving passwords. I implemented encrypted session storage using Android Keystore. Another was handling several requests discovering an expired access token at the same time. The refresh logic coordinates those requests so they share a refresh result, retries a request only once, and checks that an old response cannot bring back a session after logout. Normal builds use real authentication. Offline demo authentication requires explicit debug opt-in and has separate app storage.

For the Farmer experience, I have built the overview, quick-access actions, crop and order sections, supply listings, crop details, and a Create Crop action with Material 3 styling. These interactions currently operate on local sample data. The active Farmer screen does not yet use the Farmer repository and ViewModel for its displayed data, even though those layers exist. I would describe this as an implemented interactive interface awaiting data integration. I would not describe crop publishing, payments, deliveries, inspection evidence, or notifications as completed backend services.

The saved test reports show 44 JVM tests and 9 connected Android instrumentation tests with zero failures. They cover things such as request contracts, signup validation, role routing, token refresh, logout races, and encrypted storage. The Android tests also exercise the actual authentication screens with the ViewModel and repository using controlled dependencies. These are useful local results, but they are not live Railway integration tests, and I have not rerun them as part of this documentation review.

The main current dependency is the team's Spring Boot backend deployment to Railway. That is an external integration dependency. Once the teammate has completed deployment and we have the final URL and test accounts, I can validate signup and login for each role, verify pending Inspector restrictions, and test the transition after administrator verification. I will also check refresh, logout, restart restoration, and unavailable-network behavior against that environment.

After that, the next Android work is to resolve any contract mismatches and connect the existing role interfaces to agreed backend data and operations. The existing authentication document also needs updating because it predates the signup and Inspector-routing changes. The progress report separates what is implemented, what has local test evidence, and what still needs live integration validation.

## One-Minute Summary

I have built the Android authentication and session foundation for Kilivana: sign-in, secure session storage, restoration, logout, token refresh, and routing based on the server's role. Signup and Inspector verification gating are also implemented in the current working tree, but still uncommitted. Only a VERIFIED Inspector session opens the Inspector dashboard; other states show Awaiting Verification.

I have built an interactive Farmer interface with overview metrics, crop listings, orders, supplies, and creation/detail dialogs. Its displayed data is still local sample data, so marketplace backend integration remains. Saved reports show 44 JVM tests and 9 connected Android tests with zero failures, but those are controlled local tests, not live Railway tests. The next step is to validate the complete account flows once the team deploys the Spring Boot backend, then connect the marketplace features to agreed APIs.

## Likely Supervisor Questions and Answers

### 1. Is the application complete?

**Short answer:** No. The authentication foundation is implemented, and the role interfaces are at different stages of integration.

**If asked to elaborate:** The active marketplace screens use sample or local state. Signup and Inspector routing are implemented but uncommitted. The whole application has not been verified against the final deployed backend.

### 2. How does authentication work?

**Short answer:** The app sends entered credentials to the API and uses the returned identity and role to establish a session.

**If asked to elaborate:** Compose delegates to `AuthViewModel`, then `AuthRepository` and Retrofit. The repository validates `AuthResponse` before saving it through `TokenManager`. Session changes update navigation through StateFlow.

### 3. How are tokens stored?

**Short answer:** They are stored in an encrypted session file using an Android Keystore key; passwords are not persisted.

**If asked to elaborate:** The implementation uses AES-256/GCM and an atomic file in the app's no-backup directory. Logout deletes the file and key. This does not prove hardware-backed security on every device or guarantee server revocation during an outage.

### 4. Is signup real or a mock?

**Short answer:** The Android signup path calls a real API interface, but still needs validation against the final backend.

**If asked to elaborate:** It calls `POST auth/signup`, prepares role-specific fields, validates the returned session, and persists it. Local tests use controlled responses. Demo mode disables signup. The current signup changes are not yet committed.

### 5. How do you prevent unverified Inspector access?

**Short answer:** The dashboard is rendered only when the Inspector session says exactly VERIFIED.

**If asked to elaborate:** `authDestination` routes every other Inspector verification state to Awaiting Verification. The backend must also enforce authorization on inspection endpoints; hiding a dashboard is not a substitute for server-side access control.

### 6. How does an Inspector gain access after verification?

**Short answer:** They can sign out and sign in again after an administrator verifies them.

**If asked to elaborate:** A fresh login must return VERIFIED. A successful token refresh can also update the stored status, but refresh is triggered by a protected request returning 401. The waiting screen has no polling or manual refresh action.

### 7. Why is Railway deployment blocking testing?

**Short answer:** We need the team's final deployed service to validate the real account and verification workflows.

**If asked to elaborate:** Local tests confirm Android behavior with controlled dependencies. They cannot prove the deployed signup contract, administrator verification, refresh rotation, revocation, or backend authorization. Deployment is a team integration dependency.

### 8. What testing has been done?

**Short answer:** Saved reports record 44 JVM tests and 9 connected Android tests with zero failures.

**If asked to elaborate:** Coverage includes contracts, validation, repository behavior, session persistence, refresh concurrency, routing, and Compose authentication flows. These are historical reports from September 29, not a new execution or live Railway evidence. The 9 Android tests include one basic application-context test.

### 9. What does the Farmer screen currently do?

**Short answer:** It provides interactive crop, order, and supply views with an overview and local creation/detail dialogs.

**If asked to elaborate:** `FarmerScreen` initializes sample lists and changes in-memory state. It does not consume `FarmerRepository` or `FarmerViewModel` for displayed data. The greeting is hardcoded and its search field does not yet filter lists. Publishing a crop does not currently send it to the backend.

### 10. What was technically difficult, and what remains?

**Short answer:** Secure persistence and safe refresh/logout behavior needed careful handling; live integration and marketplace data connections remain.

**If asked to elaborate:** A mutex shares refresh results, requests retry once, and session snapshot checks reject stale updates. Next, I need to test all account flows against Railway, resolve contract issues, and integrate the role interfaces with agreed backend operations. Existing documentation also needs updating later.

## Current Blocker

According to the supplied team context, the teammate is completing the Spring Boot backend and its Railway deployment. The repository does not independently prove deployment status. This dependency prevents final-environment validation of real signup/login for Farmer, Buyer, and Supplier; Inspector signup and pending restrictions; administrator verification; and verified Inspector login/refresh and dashboard access.

The Android work can still be reviewed and tested locally. Marketplace integration gaps remain separate Android development work.

## Immediate Next Steps

1. Obtain the deployed HTTPS URL, confirm the API prefix and response contract, and arrange authorized test accounts with the backend team.
2. In a separate implementation task, configure the final environment and use a normal real-authentication build.
3. Test signup, authenticated area, logout, and login for Farmer, Buyer, and Supplier.
4. Test Inspector signup, blocked dashboard access while pending, administrator verification, and fresh login to the verified dashboard.
5. Validate refresh, invalid credentials, duplicate signup details, restart restoration, logout cleanup, and network failures. Record results without credentials or tokens.
6. Resolve verified integration issues, review the pending signup/routing changes for a later commit, and update the outdated authentication document in a separately authorized task.
