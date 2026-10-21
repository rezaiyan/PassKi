# Module passki

Passkeys for Kotlin Multiplatform: one API over Android Credential Manager and iOS AuthenticationServices.

Create a [com.alirezaiyan.passki.Passkeys] with `Passkeys()`, pass your server's WebAuthn JSON options to
`register`, `signIn`, `signInOrPassword` or `upgrade`, and send the returned `json` to your server for verification.
Failures are typed [com.alirezaiyan.passki.PasskeyError]s that carry a fix hint. Use
[com.alirezaiyan.passki.PasskeyDoctor] in debug builds to diagnose setup problems.

# Package com.alirezaiyan.passki

The public API: [com.alirezaiyan.passki.Passkeys], results and errors, capabilities, signals and the setup doctor.
