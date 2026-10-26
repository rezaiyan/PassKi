# Spec Delta

## ADDED Requirements

### Requirement: Results carry the RP ID
`PasskeyRegistration` and `PasskeyAssertion` SHALL expose `rpId`: the RP ID the ceremony ran for. It MUST equal the options' RP ID when the options name one; on the web, when the options omit it, it MUST be the effective domain the browser used.

#### Scenario: Sign-in with an RP ID in the options
- **WHEN** `signIn` succeeds with request options whose `rpId` is `example.com`
- **THEN** the assertion's `rpId` is `example.com`

#### Scenario: Web sign-in without an RP ID
- **WHEN** a web page on `app.example.com` signs in with options that omit `rpId`
- **THEN** the assertion's `rpId` is `app.example.com`

#### Scenario: Registration
- **WHEN** `register` succeeds with creation options whose `rp.id` is `example.com`
- **THEN** the registration's `rpId` is `example.com`

#### Scenario: Using the result for signals
- **WHEN** the server answers that the signed-in credential is unknown
- **THEN** the app can call `signals.unknownCredential(assertion.rpId, assertion.credentialId)` without parsing the options
