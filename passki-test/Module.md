# Module passki-test

Test doubles for apps using PassKi, so passkey flows can be tested without a device or emulator.

[com.alirezaiyan.passki.test.FakePasskeys] returns real ES256 registration and assertion responses that
server libraries such as webauthn4j accept. [com.alirezaiyan.passki.test.TestOptions] builds server-style options JSON.

# Package com.alirezaiyan.passki.test

Fake `Passkeys` implementation and option builders.
