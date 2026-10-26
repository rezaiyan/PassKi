# Verifying passkeys on your server

PassKi never verifies anything. It hands your server the WebAuthn response JSON, and your server decides whether to
trust it. Use a WebAuthn server library for that; don't parse CBOR or check signatures yourself.

This page shows two JVM libraries, [webauthn4j](https://github.com/webauthn4j/webauthn4j) and Yubico's
[java-webauthn-server](https://github.com/Yubico/java-webauthn-server), each with a test that runs a real register and
sign-in round trip through `FakePasskeys`. Both examples are compiled and their tests run in PassKi's CI, against
webauthn4j 0.30.0 and java-webauthn-server 2.7.0. For other stacks, [SimpleWebAuthn](https://simplewebauthn.dev)
(Node) and [py_webauthn](https://github.com/duo-labs/py_webauthn) (Python) accept PassKi's responses too.

## What your server must check

The library checks the signature, the challenge, the origin and the RP ID for you, if you give it the right inputs:

| Input | What to pass | Why |
|---|---|---|
| RP ID | Your RP ID, such as `example.com` ([choosing one](choosing-rp-id.md)) | A passkey for another domain must not count |
| Allowed origins | `https://example.com` (and any web subdomain you sign in from), plus one `android:apk-key-hash:<base64url SHA-256>` per Android signing key | Android apps have no web origin; Credential Manager reports this one instead ([how to compute it](../setup.md#android-assetlinksjson-and-the-origin)). iOS apps report `https://<RP ID>` |
| Challenge | The one your server issued for *this* ceremony, used once | Stops replayed responses |
| User | For registration, the account the challenge was issued for | Stops one account's response from adding a passkey to another account |
| User verification | Required for sign-in, unless you have a reason not to | The device checked a fingerprint, face or screen lock |

Store per passkey: the credential ID, the public key, the sign count, the user handle and the transports. Keep the
challenge in a short-lived store (minutes), keyed by its value, together with the ceremony and the user it belongs to.

`passkiSetup` prints your RP ID and every allowed origin, and writes this configuration for each library to
`build/passki/server-snippets/`. Apps without the plugin can compute the Android origin with
`./gradlew passkiOrigin --sha256=<fingerprint>`.

## webauthn4j

webauthn4j verifies responses; your server builds the options JSON itself. One detail matters: malformed JSON such as
`{"id":"x"}` makes webauthn4j throw `NullPointerException`, not only `DataConversionException`, so catch
`RuntimeException` around parsing and answer 400.

```kotlin server
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty

class Webauthn4jVerifier(private val rpId: String, origins: Set<String>) {
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val origins = origins.map(Origin::create).toSet()

    private fun serverProperty(challenge: ByteArray) =
        ServerProperty.builder().origins(origins).rpId(rpId).challenge(DefaultChallenge(challenge)).build()

    /** The new passkey's record to store, or null when the response doesn't verify. */
    fun verifyRegistration(responseJson: String, challenge: ByteArray): CredentialRecord? = try {
        val registration = manager.parseRegistrationResponseJSON(responseJson)
        manager.verify(registration, RegistrationParameters(serverProperty(challenge), null, true, true))
        CredentialRecordImpl(
            registration.attestationObject!!,
            registration.collectedClientData,
            registration.clientExtensions,
            registration.transports,
        )
    } catch (e: RuntimeException) { // includes webauthn4j's VerificationException
        null
    }

    /** The new sign count, or null when the response doesn't verify against [record]. */
    fun verifySignIn(responseJson: String, challenge: ByteArray, record: CredentialRecord): Long? = try {
        val assertion = manager.parseAuthenticationResponseJSON(responseJson)
        manager.verify(assertion, AuthenticationParameters(serverProperty(challenge), record, null, true, true))
        assertion.authenticatorData!!.signCount
    } catch (e: RuntimeException) {
        null
    }
}
```

The test plays the device with `FakePasskeys`. `TestOptions` builds options in the same shape your server sends; in
your own test, call your options endpoint instead.

```kotlin server
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

class Webauthn4jVerifierTest {
    private val verifier = Webauthn4jVerifier("example.com", setOf("https://example.com"))
    private val device = FakePasskeys(origin = "https://example.com")

    @Test
    fun registerThenSignIn(): Unit = runBlocking {
        val registrationChallenge = Random.nextBytes(32)
        val created = device.register(
            TestOptions.creation("example.com", challenge = base64Url(registrationChallenge)),
        ).getOrThrow()
        val record = assertNotNull(verifier.verifyRegistration(created.json, registrationChallenge))

        val signInChallenge = Random.nextBytes(32)
        val assertion = device.signIn(TestOptions.request("example.com", challenge = base64Url(signInChallenge)))
            .getOrThrow()
        assertEquals(1L, verifier.verifySignIn(assertion.json, signInChallenge, record))
        // A response is good for its own challenge only.
        assertNull(verifier.verifySignIn(assertion.json, Random.nextBytes(32), record))
    }
}
```

## java-webauthn-server (Yubico)

java-webauthn-server builds the options too, and reads stored passkeys through a `CredentialRepository` you implement.
`startRegistration` and `startAssertion` return objects you keep with the session (as JSON, via `toJson()`) until the
response comes back; `toCredentialsCreateJson()` and `toCredentialsGetJson()` are the options to send to the app, and
PassKi accepts their `{"publicKey": ...}` wrapper as is.

```kotlin server
import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.ByteArray as Bytes
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import com.yubico.webauthn.data.RelyingPartyIdentity
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import java.util.Optional

/** Passkeys kept in memory; a real server keeps them in its database. */
class InMemoryCredentials : CredentialRepository {
    val passkeys = mutableListOf<RegisteredCredential>()
    val users = mutableMapOf<String, Bytes>() // username → user handle

    override fun getCredentialIdsForUsername(username: String): Set<PublicKeyCredentialDescriptor> =
        passkeys.filter { it.userHandle == users[username] }
            .map { PublicKeyCredentialDescriptor.builder().id(it.credentialId).build() }.toSet()

    override fun getUserHandleForUsername(username: String): Optional<Bytes> = Optional.ofNullable(users[username])

    override fun getUsernameForUserHandle(userHandle: Bytes): Optional<String> =
        Optional.ofNullable(users.entries.firstOrNull { it.value == userHandle }?.key)

    override fun lookup(credentialId: Bytes, userHandle: Bytes): Optional<RegisteredCredential> =
        Optional.ofNullable(passkeys.firstOrNull { it.credentialId == credentialId && it.userHandle == userHandle })

    override fun lookupAll(credentialId: Bytes): Set<RegisteredCredential> =
        passkeys.filter { it.credentialId == credentialId }.toSet()
}

fun relyingParty(credentials: CredentialRepository): RelyingParty = RelyingParty.builder()
    .identity(RelyingPartyIdentity.builder().id("example.com").name("Example").build())
    .credentialRepository(credentials)
    .origins(setOf("https://example.com")) // plus android:apk-key-hash:... per signing key
    .build()

fun startRegistration(rp: RelyingParty, user: UserIdentity) = rp.startRegistration(
    StartRegistrationOptions.builder()
        .user(user)
        .authenticatorSelection(AuthenticatorSelectionCriteria.builder().residentKey(ResidentKeyRequirement.REQUIRED).build())
        .build(),
)
```

```kotlin server
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.data.ByteArray as Bytes
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.UserIdentity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YubicoRelyingPartyTest {
    private val credentials = InMemoryCredentials()
    private val rp = relyingParty(credentials)
    private val device = FakePasskeys(origin = "https://example.com")

    @Test
    fun registerThenSignIn(): Unit = runBlocking {
        val handle = Bytes(Random.nextBytes(32)) // random per account, never the account ID
        credentials.users["alice@example.com"] = handle
        val user = UserIdentity.builder().name("alice@example.com").displayName("Alice").id(handle).build()

        val creation = startRegistration(rp, user)
        val created = device.register(creation.toCredentialsCreateJson()).getOrThrow()
        val registration = rp.finishRegistration(
            FinishRegistrationOptions.builder()
                .request(creation)
                .response(PublicKeyCredential.parseRegistrationResponseJson(created.json))
                .build(),
        )
        credentials.passkeys += RegisteredCredential.builder()
            .credentialId(registration.keyId.id)
            .userHandle(handle)
            .publicKeyCose(registration.publicKeyCose)
            .signatureCount(registration.signatureCount)
            .build()

        val request = rp.startAssertion(StartAssertionOptions.builder().build()) // any passkey on the device
        val assertion = device.signIn(request.toCredentialsGetJson()).getOrThrow()
        val result = rp.finishAssertion(
            FinishAssertionOptions.builder()
                .request(request)
                .response(PublicKeyCredential.parseAssertionResponseJson(assertion.json))
                .build(),
        )
        assertTrue(result.isSuccess)
        assertEquals("alice@example.com", result.username)
    }
}
```

`finishRegistration` and `finishAssertion` throw `RegistrationFailedException` and `AssertionFailedException` when a
response doesn't verify; answer 400 (or 404 when the passkey is unknown, see [Recommended server API](server-api.md)).

## Test your own endpoints

The same pattern works one level up: point `FakePasskeys` at your real endpoints in a server test, so the test covers
your options, your challenge store and your database mapping, not only the library. See
[Testing](../testing.md#test-your-server).
