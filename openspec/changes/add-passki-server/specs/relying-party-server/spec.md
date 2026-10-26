# Spec Delta

## Purpose

`passki-server` gives JVM backends the relying-party half of passkeys: options, challenges, verification with typed
results and the well-known files, all from one configuration that can't disagree with the app setup.

## ADDED Requirements

### Requirement: One configuration for origins and app association
`PasskiRelyingParty` SHALL be configured with an RP ID, an RP name, Android apps (package name and SHA-256 fingerprints in `AA:BB:…` form), Apple app IDs (`<TeamID>.<bundleID>`) and extra origins, and SHALL accept `https://<rpId>`, one `android:apk-key-hash:` origin per fingerprint and the extra origins. An invalid fingerprint or RP ID MUST be rejected at construction with a message naming the value.

#### Scenario: Android origin derived from a fingerprint
- **WHEN** the configuration lists fingerprint `C9:64:…:03` for `com.example.app`
- **THEN** responses with origin `android:apk-key-hash:<base64url of that fingerprint>` are accepted

#### Scenario: Malformed fingerprint
- **WHEN** a fingerprint has 31 bytes
- **THEN** construction fails with a message naming that fingerprint

### Requirement: Options for passkey creation and sign-in
The relying party SHALL produce creation options JSON (RP, user handle, name, display name, fresh challenge, ES256 and RS256, discoverable credential and user verification required, attestation `none`, the account's existing credentials excluded) and request options JSON (fresh challenge, RP ID, user verification required, no allowed credentials).

#### Scenario: Second passkey for an account
- **WHEN** creation options are requested for an account that already has one credential
- **THEN** that credential appears in `excludeCredentials` and the same user handle is used

#### Scenario: Options accepted by PassKi
- **WHEN** the options JSON is passed to `Passkeys.register` or `Passkeys.signIn` unchanged
- **THEN** PassKi accepts it on every platform

### Requirement: Single-use bound challenges
Every challenge SHALL be random (at least 32 bytes), stored through a `ChallengeStore`, valid once, expire after the configured time-to-live, and be bound to its ceremony and, for creation, to the user it was issued for. The default store MUST be in memory and bounded in size.

#### Scenario: Replayed response
- **WHEN** a registration response is verified twice
- **THEN** the second verification fails with `Expired`

#### Scenario: Response for another account
- **WHEN** a registration challenge issued for user A is verified as user B
- **THEN** verification fails with `WrongUser`

### Requirement: Registration verification with typed results
Verifying a registration response SHALL return either the new credential (ID, public key, sign count, backup eligibility and state, transports, user handle) as a plain value or a typed failure: `Malformed`, `Expired`, `WrongUser`, `BadOrigin`, `BadSignature`, `AlreadyRegistered`. It MUST NOT throw for any input.

#### Scenario: Valid response from the fake authenticator
- **WHEN** a registration from `FakePasskeys("https://example.com")` is verified
- **THEN** the result is a new credential whose ID matches the response

#### Scenario: Garbage input
- **WHEN** the response JSON is `{}` or not JSON
- **THEN** the result is `Malformed`, and no exception escapes

### Requirement: Sign-in verification with typed results
Verifying a sign-in response with a credential lookup SHALL return the matched credential with its updated sign count and backup state, or a typed failure: `Malformed`, `Expired`, `UnknownCredential`, `WrongUser`, `BadOrigin`, `BadSignature`. `UnknownCredential` MUST be distinct so the app can answer 404 and the client can call `signals.unknownCredential`.

#### Scenario: Deleted passkey
- **WHEN** the lookup finds no credential for the response's credential ID
- **THEN** the result is `UnknownCredential`

#### Scenario: User handle mismatch
- **WHEN** the response's user handle differs from the stored credential's
- **THEN** the result is `WrongUser`

### Requirement: Well-known files and server config from the same configuration
The relying party SHALL render `assetlinks.json` (relation `delegate_permission/common.get_login_creds` per Android app with its fingerprints), `apple-app-site-association` (`webcredentials.apps`) and the server-config JSON `{"rpId", "origins", "appleAppIds"}` that `passkiCheck` reads, all from its configuration.

#### Scenario: passkiCheck against the server config
- **WHEN** `passkiCheck` reads the server-config JSON served from this output
- **THEN** SRV-01 and SRV-02 pass for an app configured with the same RP ID and fingerprints

#### Scenario: Files agree with accepted origins
- **WHEN** a fingerprint is added to the configuration
- **THEN** it appears in `assetlinks.json` and its origin is accepted, with no other change
