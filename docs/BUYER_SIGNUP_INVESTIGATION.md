# Buyer signup investigation and safe fixes

29 October 2026. No live registration/login POST was submitted during this investigation. The signed-in Samsung account/data and all prior work remain intact. Only a read-only OpenAPI request was made to the configured backend. Tests use synthetic responses and local in-memory sessions.

## Findings and confidence

Buyer login works independently of registration. Current trace: SignUpScreen intentional click/IME ? SignupValidation.prepare ? AuthViewModel.submitSignup ? AuthRepository.signup ? CanonicalAuthService.signup ? CanonicalBackendApi.register ? POST /api/v1/auth/register. Successful registration returns a wrapped UserResponse, then email verification or a separate sign-in; it does not return session tokens directly. A post-creation sign-in failure can therefore leave a real created account, and registration must not be blindly repeated.

The user's specific backend failures were not reproduced live; there is no captured real signup status/reason proving why their supposedly new identity conflicts. No account-existence lookup, identity rotation, rate-limit workaround or account creation was used.

Confirmed Android issues:

1. Buyer registration used a generic HTTP mapper instead of the bounded canonical error parser already used by Supplier signup. Any HTTP 409 became an account-may-exist message, even when the backend did not identify an email/phone duplicate. HTTP 422 and validation field hints were lost. This could make a generic conflict look like a confirmed duplicate.
2. CanonicalAuthService rebuilt rejected registration responses using only status/body, losing Retry-After headers. Buyer had no client cooldown enforcement for the returned deadline.
3. The Compose submit lambda checked a captured busy value; two callbacks in the same frame could invoke the UI callback twice before recomposition. The ViewModel synchronous isLoading gate and repository AtomicBoolean already prevented simultaneous backend registrations, so this is not evidence that two HTTP requests occurred on the user's phone. The UI now also checks the current sent flag directly.
4. Buyer names previously had only a nonblank check, while the deployed schema requires 2?100 characters. Form and repository validation now enforce this bound for Buyer, preserving other role behavior.

?Too many signup attempts? was a fixed Android string selected only for an HTTP 429 response, not a local attempt counter. It indicates an upstream rate-limit status but does not prove whether Daniel's application, gateway or another upstream generated it, or what limit/bucket was used. No live signup 429 was deliberately generated. New wording describes temporary registration rate limiting without blaming repeated user attempts.

## Live contract comparison

GET /api-docs returned HTTP 200; SHA-256 `3d163c90a8bb8fafb421b9b1d4176940a48b7c05ebcac8e4c850c18caa0bc521`. POST /api/v1/auth/register accepts UserRegistrationRequest: required name, email, phone, password, role; optional username and region. Android CanonicalRegisterRequest maps exactly these names and sends role BUYER. Signup does not accidentally use a Buyer profile/payment endpoint or the legacy /auth/signup route when canonical mode is enabled.

Name length is 2?100. Schema password minimum is 6; Android's existing 8?72 policy was retained rather than weakened. Existing Buyer UI also requires username and region, though OpenAPI marks them optional; this is documented existing client policy, not a source of false duplicate detection. OpenAPI advertises only a 200 success response for register, leaving failure statuses/reasons and Retry-After undocumented.

Normalization: email, phone, name and region are trimmed; username is lowercased using Locale.ROOT. Email case and phone format are otherwise preserved. Android performs no local account-existence test and does not change the role. Lowercased username can collide with an existing username; a different email does not prove a different phone/username. Backend uniqueness scope, cross-role uniqueness, email case folding and equivalence of phone formats must be confirmed by Daniel. No new identity-normalization assumptions were introduced.

## Fixes

- Buyer now uses the existing bounded canonical parser, exposing only fixed safe strings and allowlisted field labels. Recognized CONFLICT + exact known email/phone duplicate reasons produce specific guidance. Generic 409 says the service reported conflicting details, not that an account certainly exists. No raw server text or identity values are echoed.
- 400/422 show known invalid field labels or neutral validation guidance; 401/403 registration-not-permitted; 415 unsupported request format; 429 temporary rate-limit guidance; 5xx unknown-creation outcome with sign-in-first guidance. Network/timeout/response exceptions do not claim an account exists and warn that registration may have reached the service. Existing explicit post-created sign-in failures remain distinguishable.
- Rejected canonical registration responses preserve their raw HTTP status/headers through the service adapter. Retry-After supports nonnegative delta seconds and RFC 1123 dates. Invalid/past/overflowing values fall back to generic waiting guidance, never raw-header display.
- Buyer repository retains a received future Retry-After deadline and suppresses new registration calls until expiry. It reports remaining seconds on intentional retries; it never schedules automatic retries. This in-memory UX guard does not replace server rate enforcement and does not attempt to bypass limits on restart. No invented cooldown is imposed when no valid deadline exists.
- Existing isLoading/sent button and field disabling, ViewModel gate, repository AtomicBoolean, no registration LaunchedEffect submission, no auth refresh on /auth/register, no redirects and retryOnConnectionFailure(false) remain. AuthLoginDiagnosticInterceptor passes registration through once; it does not log/register/replay it. Login request/error mapping and Supplier recovery behavior are unchanged.

## Daniel's remaining investigation

For an already observed failure (without asking the user to repeatedly resubmit), inspect the server's request correlation/time, actual HTTP status, error.code and non-PII reason/field category for POST /api/v1/auth/register. Confirm whether the conflict is email, phone, username, cross-role uniqueness or another constraint; whether an earlier attempt committed before a lost response/email failure; and why the 429 bucket was exceeded. Confirm rate-limit scope/window, gateway behavior and Retry-After, and document 400/409/422/429/5xx responses in OpenAPI. Do not send passwords/OTPs/tokens or raw identity data into Android logs or this report.

## Validation

New mocked regressions cover explicit and generic conflicts, 400/422 validation, 429 cooldown/expiry and dates, malformed headers, 5xx, network timeout, concurrent repository calls, same-frame form callbacks, pending field/button disabling and recomposition without automatic retry. Final validation: **387 unit tests passed**, zero failures/errors. **lintDebug passed** with zero errors, existing 57 warnings/1 hint. **assembleDebug and assembleDebugAndroidTest passed**. APKs installed in place via adb install -r on Samsung RF8WC0K043H; application ID com.example.farmapp.dev2231918adc4d4b9d and app data preserved.

Samsung: new Buyer signup form tests 2/2 passed; Supplier login/recovery 3/3 passed; auth/profile/verification 8/8 passed. Existing AuthFlowIntegrationTest had one 5-second login timeout in the initial combined run (17 of 18 passed). Its isolated rerun passed all 5 tests. Thus all 18 selected tests have passing results on the installed build, with combined-run test timing instability explicitly recorded. No working-login regression was reproduced in the isolated suite. Logs: ignored buyer-validation-signup.log, buyer-validation-signup-samsung.log and buyer-validation-signup-auth-isolated.log.

No live successful Buyer signup is claimed. The exact account conflict and rate-limit cause still require Daniel's server-side evidence.

## Files and safety

New BuyerSignupErrors.kt and BuyerSignupRegressionTest.kt; AuthRepository.kt Buyer-only mapping/cooldown; CanonicalAuthService.kt registration header preservation; AuthLoginDiagnostics.kt adds a safe registration stage enum; SignupValidation.kt/SignupFormValidation.kt Buyer name bounds; SignUpScreen.kt current submit latch; new BuyerSignupPresentationTest.kt. No secrets, HTTP BODY logging, backend edits or new dependencies. No reset, stash, staging, commit or push.
