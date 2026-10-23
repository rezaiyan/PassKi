# demo-client Specification

## Purpose
`passki-demo` provides `PasskiDemoServer`, a client for the hosted demo relying party, so developers can try real, server-verified passkeys in their app before they have a domain or a WebAuthn server. For evaluation only.

## Requirements

### Requirement: Hosted demo by default
`PasskiDemoServer(baseUrl)` SHALL target `https://passki.alirezaiyan.com` by default (`URL`), with RP ID `passki.alirezaiyan.com` (`RP_ID`), and SHALL accept another base URL with trailing slashes removed.

#### Scenario: Default client
- **WHEN** an app creates `PasskiDemoServer()`
- **THEN** requests go to `https://passki.alirezaiyan.com/api/demo/...`

### Requirement: Options from the server
`registrationOptions(username?)` SHALL POST to `/api/demo/register/options` (sending `username` only when not blank), and `authenticationOptions(userHandle?)` SHALL POST to `/api/demo/login/options`; both return the server's options JSON ready for `Passkeys.register` / `Passkeys.signIn`.

#### Scenario: Sign-in for a known user
- **WHEN** `authenticationOptions(userHandle)` is called
- **THEN** the options' `allowCredentials` lists that user's passkeys

#### Scenario: Discoverable sign-in
- **WHEN** `authenticationOptions()` is called without a handle
- **THEN** any discoverable passkey for the demo RP is accepted

### Requirement: Server-side verification
`verifyRegistration(json, shareFixtureAs?)` and `verifyAuthentication(json)` SHALL POST the PassKi response to `/api/demo/register/verify` and `/api/demo/login/verify` and return the server's `DemoVerdict` (verified flag, credential id, sign count, user verification, backup state, AAGUID, user handle, origin, RP ID).

#### Scenario: Account tracking
- **WHEN** a registration or sign-in is verified
- **THEN** `account` holds that demo user's handle and the credential ids this client has seen verified, oldest first

#### Scenario: Opt-in fixture sharing
- **WHEN** `verifyRegistration` is called with `shareFixtureAs` set to `android`, `ios`, `web` or `other`
- **THEN** the raw response is also stored on the demo server for 7 days as a contract-test fixture
- **AND** without it, nothing beyond the verification is stored

### Requirement: Server config
`serverConfig()` SHALL GET `/api/demo/server-config` and return the RP ID and every accepted origin, including the origins of registered apps.

#### Scenario: Registered Android app
- **WHEN** an app was registered by `passkiSetup` in demo mode
- **THEN** `serverConfig().origins` includes its `android:apk-key-hash:` origin

### Requirement: Typed server errors
Every call MUST throw `DemoServerException` with the HTTP status and the server's error message when the server answers with an error, and with a null status when it is unreachable or answers with something that is not a JSON object; coroutine cancellation MUST be rethrown unchanged.

#### Scenario: Expired challenge
- **WHEN** a response is verified after its challenge expired
- **THEN** the call throws `DemoServerException` with a 4xx status and the message "unknown, expired or already used challenge"
