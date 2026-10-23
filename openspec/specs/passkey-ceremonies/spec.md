# passkey-ceremonies Specification

## Purpose
The `passki` library's common entry point for creating passkeys and signing in with them on Android and iOS, driven by the relying party server's WebAuthn options JSON.

## Requirements

### Requirement: Platform-free entry point
`Passkeys()` SHALL return the implementation for the current platform without requiring an `Activity`, `UIWindow` or other platform handle from the caller.

#### Scenario: Obtaining an instance from common code
- **WHEN** common code calls `Passkeys()`
- **THEN** it receives a `Passkeys` instance usable from `commonMain`
- **AND** on Android the current activity is tracked by PassKi itself (initialised through `androidx.startup`, or `PasskiAndroid.init(application)` when that initializer is removed)

### Requirement: Server options used as-is
Every operation that takes `optionsJson` SHALL accept the server's `PublicKeyCredentialCreationOptionsJSON` or `PublicKeyCredentialRequestOptionsJSON` unchanged, optionally wrapped in a `{"publicKey": ...}` envelope, and SHALL preserve unknown fields.

#### Scenario: Enveloped options
- **WHEN** options are passed as `{"publicKey": {...}}`
- **THEN** the envelope is removed and the inner options are used

#### Scenario: Malformed options
- **WHEN** the options are not JSON, not an object, do not match the WebAuthn JSON format, or carry a binary field (`challenge`, `user.id`, credential descriptor `id`) that is not non-empty base64url
- **THEN** the operation fails with `PasskeyError.InvalidOptions`

#### Scenario: Oversized user handle
- **WHEN** creation options carry a `user.id` longer than 64 bytes
- **THEN** the operation fails with `PasskeyError.InvalidOptions`

### Requirement: Explicit bare-domain RP ID
Options MUST name the RP ID (`rp.id` in creation options, `rpId` in request options) as a bare domain, because apps have no web origin to derive it from.

#### Scenario: Missing RP ID
- **WHEN** the options omit `rp.id` or `rpId`
- **THEN** the operation fails with `PasskeyError.InvalidOptions` whose message says the field is required

#### Scenario: RP ID with scheme, port or path
- **WHEN** the RP ID is empty, contains whitespace, or contains any of `/ : ? # @`
- **THEN** the operation fails with `PasskeyError.InvalidOptions` saying it must be a bare domain such as `example.com`

### Requirement: Register a passkey
`register(optionsJson)` SHALL create a passkey and return a `PasskeyRegistration` whose `json` is the WebAuthn registration response, ready to POST to the server, along with the credential id (base64url), reported transports and authenticator attachment when known.

#### Scenario: Successful registration
- **WHEN** the user completes the system sheet
- **THEN** the result is `Success` with a `PasskeyRegistration`

#### Scenario: Credential already on the device
- **WHEN** `excludeCredentials` matches a passkey on the device (Android, or iOS 17.4+)
- **THEN** the result is `Failure(PasskeyError.AlreadyRegistered)`
- **AND** below iOS 17.4 `excludeCredentials` is ignored, so a second passkey may be created and the server receives it

### Requirement: Sign in with a passkey
`signIn(optionsJson, mode)` SHALL return a `PasskeyAssertion` whose `json` is the WebAuthn assertion response, with the credential id and user handle (base64url) when available. `mode` SHALL default to `SignInMode.Modal`.

#### Scenario: Modal sign-in
- **WHEN** `signIn` is called with `SignInMode.Modal`
- **THEN** the standard system sheet is shown, including hybrid (phone) and security-key options when no local passkey exists

#### Scenario: Immediate-only sign-in with no local passkey
- **WHEN** `signIn` is called with `SignInMode.ImmediateOnly` and no passkey for the request is on this device
- **THEN** it fails fast with `PasskeyError.NoCredentials` without showing a hybrid or security-key flow

### Requirement: Autofill sign-in
`signIn` with `SignInMode.Autofill` SHALL offer passkeys as suggestions on the focused username field (conditional mediation) and SHALL suspend until the user picks one; dismissing the suggestion MUST NOT complete the call.

#### Scenario: User picks a suggestion
- **WHEN** an autofill request is open and the user picks a passkey suggestion
- **THEN** the call returns `Success` with the assertion

#### Scenario: Withdrawing the offer
- **WHEN** the calling coroutine is cancelled (for example the field loses focus)
- **THEN** the offer is withdrawn and the cancellation propagates

#### Scenario: No focused username field on Android
- **WHEN** an autofill sign-in is started on Android without a focused username field
- **THEN** it fails with `PasskeyError.Misconfigured`

#### Scenario: Modal request while autofill is pending on iOS
- **WHEN** a modal request starts on iOS while autofill requests are pending
- **THEN** the pending autofill requests are cancelled so the modal request can be presented

### Requirement: Unified passkey and password sign-in
`signInOrPassword(optionsJson)` SHALL show one system sheet offering passkeys and saved passwords and return a `SignInCredential`: either `Passkey(assertion)` or `Password(id, password)`.

#### Scenario: User picks a saved password
- **WHEN** the user chooses a saved password
- **THEN** the result is `SignInCredential.Password` and its `toString` masks the password as `***`

### Requirement: Silent passkey upgrade
`upgrade(optionsJson)` SHALL attempt conditional creation of a passkey after a password sign-in without showing UI, and SHALL report a provider decline as `PasskeyError.NotSupported` on both platforms.

#### Scenario: Provider declines
- **WHEN** the Android credential provider rejects conditional create, or iOS declines silently without presenting UI
- **THEN** the result is `Failure(PasskeyError.NotSupported)`, never `Cancelled`

### Requirement: Device capabilities
`capabilities()` SHALL report `isSupported`, `immediateSignIn`, `unifiedPasswordSignIn`, `conditionalCreate`, `signals` and `autofill`, so UI can adapt before offering passkey actions. Autofill MUST be reported only on Android 15 (API 35)+ and iOS 16+.

#### Scenario: UI adapts to capabilities
- **WHEN** `capabilities().unifiedPasswordSignIn` is false
- **THEN** the app can hide the combined passkey and password button before calling `signInOrPassword`

### Requirement: Unsupported platforms fail predictably
On JVM desktop and browser (JS/Wasm) targets, `Passkeys()` SHALL return an implementation whose capabilities are all false and whose every operation, including signals, fails with `PasskeyError.NotSupported`.

#### Scenario: Calling register on JVM desktop
- **WHEN** `register` is called on the JVM target
- **THEN** the result is `Failure(PasskeyError.NotSupported)` naming the platform

### Requirement: Binary-compatible public types
Result and capability types (`PasskeyCapabilities`, `PasskeyRegistration`, `PasskeyAssertion`, `SignInCredential` subtypes) MUST have constructors marked `@InternalPasskiApi`, so fields can be added without breaking binaries; new members on `Passkeys` and `PasskeySignals` MUST be added only with default implementations; enums and sealed hierarchies documented as open MAY gain entries in minor releases.

#### Scenario: Adopter implements Passkeys
- **WHEN** an adopter implements `Passkeys` (for example a hand-written fake) and PassKi adds a member in a minor release
- **THEN** the adopter's implementation stays source- and binary-compatible

#### Scenario: Results are safe to log
- **WHEN** a `PasskeyRegistration` or `PasskeyAssertion` is converted to a string
- **THEN** the output shows the credential id and the JSON length, not the JSON itself
