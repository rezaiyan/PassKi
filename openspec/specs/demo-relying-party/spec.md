# demo-relying-party Specification

## Purpose
The hosted demo relying party in `passki-web` (`passki.alirezaiyan.com`) creates WebAuthn challenges, verifies responses with SimpleWebAuthn, serves the shared well-known files, and lets apps register themselves for a limited time so developers can evaluate PassKi without their own domain or server.

## Requirements

### Requirement: Registration ceremony
`POST /api/demo/register/options {username?}` SHALL create an anonymous demo user that expires after 24 hours and return creation options whose `user.id` is the user handle. `POST /api/demo/register/verify` SHALL accept a `RegistrationResponseJSON`, bare or as `{response, capture?}`, verify it, store the credential with the user's expiry and return a verdict.

#### Scenario: Successful registration
- **WHEN** a valid response to a fresh registration challenge is posted
- **THEN** the server returns a verdict with `verified: true`, the credential id, sign count, backup flags, AAGUID, user handle, origin and RP ID

#### Scenario: Expired demo user
- **WHEN** a registration response arrives for a user whose 24 hours have passed
- **THEN** the server rejects it with "demo user expired"

### Requirement: Authentication ceremony
`POST /api/demo/login/options {userHandle?}` SHALL return request options listing that user's credentials in `allowCredentials`, or discoverable options without a handle; `POST /api/demo/login/verify` SHALL verify the assertion and return a verdict with the new sign count.

#### Scenario: Unknown user handle
- **WHEN** login options are requested for an unknown or expired user handle
- **THEN** the server answers 404 "unknown or expired demo user"

#### Scenario: Credential of another user
- **WHEN** an assertion's credential does not belong to the challenged user, or its `userHandle` does not match the credential
- **THEN** verification is rejected

### Requirement: Single-use challenges
Challenges MUST expire after 5 minutes, MUST be usable once and only for their kind (registration or authentication).

#### Scenario: Replayed response
- **WHEN** the same response is verified twice
- **THEN** the second call fails with "unknown, expired or already used challenge"

### Requirement: Shared well-known files
`/.well-known/assetlinks.json` SHALL list the permanent demo package and fingerprints plus every non-expired registered Android app, served as `application/json` with `max-age=300`. `/.well-known/apple-app-site-association` SHALL list the permanent app IDs plus every non-expired registered iOS app under `webcredentials`, served as `application/json` with `max-age=60`.

#### Scenario: Registration expires
- **WHEN** a registered app's 7 days pass
- **THEN** it no longer appears in the well-known file and its origin is no longer accepted

### Requirement: Self-service app registration
`POST /api/demo/android-apps {packageName, fingerprints[]}` and `POST /api/demo/ios-apps {teamId, bundleId}` SHALL list the app for 7 days and return a delete token; `DELETE` with that token SHALL remove it. Registrations MUST be validated, limited per IP and capped globally.

#### Scenario: Invalid input
- **WHEN** the package name does not look like `com.example.app`, there are zero or more than 5 fingerprints, a fingerprint is not SHA-256, the Team ID is not exactly 10 uppercase letters or digits, or the bundle ID is not reverse-DNS
- **THEN** the server answers 400 with a message naming the invalid field

#### Scenario: Limits
- **WHEN** an IP exceeds 5 new registrations per day, or the active entries reach the global cap (500 by default)
- **THEN** the registration is refused
- **AND** both limits are configurable through `PASSKI_ANDROID_PER_IP_PER_DAY`, `PASSKI_IOS_PER_IP_PER_DAY`, `PASSKI_ANDROID_MAX_ACTIVE` and `PASSKI_IOS_MAX_ACTIVE`

#### Scenario: Re-running passkiSetup
- **WHEN** an Android package and fingerprint pair that is already listed is registered again
- **THEN** the server answers 200 reporting it as already listed, does not renew it and does not count it against the rate limit

#### Scenario: Permanent entry
- **WHEN** the permanently listed demo app is registered
- **THEN** the server answers 409 saying it is already permanently listed

#### Scenario: Demo full
- **WHEN** the global cap is reached
- **THEN** the server answers 503 "the demo is full right now; try again later"

### Requirement: Accepted origins
`GET /api/demo/server-config` SHALL return `{rpId, origins}`, where origins are `https://<rpId>` plus one `android:apk-key-hash:` origin per permanent or registered signing key, and verification MUST accept exactly those origins.

#### Scenario: Newly registered Android key
- **WHEN** an Android app is registered
- **THEN** its `android:apk-key-hash:` origin appears in server-config, and its responses verify

### Requirement: Opt-in contract fixtures
A registration verify request with `capture: {platform}` (`android`, `ios`, `web` or `other`) SHALL store the raw response as a contract-test fixture for 7 days, keeping the last 20 per platform and rejecting fixtures over 64 KB; `GET /api/demo/fixtures?platform=` SHALL list them.

#### Scenario: Unknown platform filter
- **WHEN** fixtures are requested with an unknown platform
- **THEN** the server answers 400 "unknown platform"

### Requirement: Abuse limits
Every ceremony endpoint MUST be rate-limited per client IP.

#### Scenario: Too many requests
- **WHEN** a client exceeds an endpoint's rate limit
- **THEN** the server answers with a rate-limit error instead of processing the request
