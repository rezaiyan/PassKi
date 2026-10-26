# Passkeys as an extra sign-in

The most common way to add passkeys to an app that already has accounts: people keep signing up with Google, Apple or
a password. Once signed in, they add a passkey in Settings, and from then on they can sign in with it. Settings also
lists their passkeys, deletes them and keeps the device's password manager in step.

This page walks through the client side end to end. The server endpoints it calls are described in
[Recommended server API](server-api.md), and verifying the responses in [Verifying on the server](verify-on-server.md).

## The server calls

The app needs six calls. Here they are as one interface; the shapes match [Recommended server API](server-api.md):

```kotlin
interface PasskeyApi {
    // Signed in: add a passkey
    suspend fun registrationOptions(): String                 // POST /passkeys/registration-options
    suspend fun finishRegistration(responseJson: String)      // POST /passkeys

    // Signed out: sign in
    suspend fun signInOptions(): String                       // POST /passkeys/sign-in-options
    suspend fun finishSignIn(responseJson: String): SignInResult // POST /passkeys/sign-in

    // Signed in: Settings
    suspend fun list(): PasskeyList                           // GET /passkeys
    suspend fun delete(credentialId: String)                  // DELETE /passkeys/{credentialId}
}

sealed interface SignInResult {
    data object SignedIn : SignInResult
    data object UnknownPasskey : SignInResult // the server answered 404: it has no such passkey
}

class PasskeyList(
    val rpId: String,
    val userHandle: String,
    val accountName: String,
    val accountDisplayName: String,
    val passkeys: List<SavedPasskey>,
)

class SavedPasskey(val credentialId: String, val label: String, val lastUsed: String?)
```

`PasskeyList` carries what [signals](#keep-the-password-manager-in-step) need besides the list itself: the RP ID, the
account's user handle and the label the passkey picker shows.

## Add a passkey in Settings

The user is already signed in, so a failure here never locks them out. Show nothing when they close the sheet, a short
notice when the device already has a passkey for this account, and hide the button where passkeys don't work:

```kotlin
class AddPasskey(private val passkeys: Passkeys, private val api: PasskeyApi) {
    suspend fun canAdd(): Boolean = passkeys.capabilities().isSupported

    /** A message to show, or null for none. */
    suspend fun add(): String? = passkeys.register(api.registrationOptions()).fold(
        onSuccess = { api.finishRegistration(it.json); "Passkey added." },
        onFailure = { error ->
            when (error.category) {
                PasskeyErrorCategory.UserCancelled -> null
                PasskeyErrorCategory.AlreadyExists -> "This device already has a passkey for your account."
                PasskeyErrorCategory.Unsupported -> "Passkeys aren't available on this device."
                PasskeyErrorCategory.NoCredential, PasskeyErrorCategory.Setup, PasskeyErrorCategory.Unexpected ->
                    "Couldn't add a passkey. Try again later."
            }
        },
    )
}
```

PassKi logs `Setup` errors (with their check ID and fix) by default, so a misconfigured release build still says why
in logcat or your crash reporter. See [What to show users](what-to-show-users.md) for every error.

## The sign-in button

Most of your users won't have a passkey yet. With the default `SignInMode.Modal`, a device without one doesn't fail:
Android offers "Sign in another way" and iOS shows a QR code for another device. First-time users read that as a bug.
So ask only for passkeys already on this device, and treat "none" as a quiet result:

```kotlin
class PasskeySignIn(private val passkeys: Passkeys, private val api: PasskeyApi) {
    /** A message to show, or null for none; [onSignedIn] runs when the server accepted the passkey. */
    suspend fun signIn(onSignedIn: () -> Unit): String? {
        val mode = if (passkeys.capabilities().immediateSignIn) SignInMode.ImmediateOnly else SignInMode.Modal
        return passkeys.signIn(api.signInOptions(), mode).fold(
            onSuccess = { assertion ->
                when (api.finishSignIn(assertion.json)) {
                    SignInResult.SignedIn -> { onSignedIn(); null }
                    SignInResult.UnknownPasskey -> {
                        // Deleted on the server (or on another device): stop offering it.
                        passkeys.signals.unknownCredential(assertion.rpId, assertion.credentialId)
                        "That passkey was removed. Sign in another way."
                    }
                }
            },
            onFailure = { error ->
                when (error.category) {
                    PasskeyErrorCategory.UserCancelled -> null
                    PasskeyErrorCategory.NoCredential -> "No passkey on this device yet. Sign in another way, then add one in Settings."
                    else -> "Passkey sign-in didn't work. Sign in another way."
                }
            },
        )
    }
}
```

On Android and iOS `immediateSignIn` is true wherever passkeys work. On the web it is false in browsers without
immediate mediation, and the sign-in falls back to the full sheet; if you'd rather not show a QR code there, hide the
button when `immediateSignIn` is false. Details: [platforms](../platforms.md).

Keep the button next to the other sign-in options, not instead of them: someone with a new phone has no passkey on it
yet.

## Keep the password manager in step

When Settings loads the list, tell the device what the server knows. The password manager then hides passkeys the
server deleted elsewhere and shows the current account label:

```kotlin
suspend fun loadPasskeys(passkeys: Passkeys, api: PasskeyApi): List<SavedPasskey> {
    val list = api.list()
    passkeys.signals.syncAccount(
        rpId = list.rpId,
        userHandle = list.userHandle,
        credentialIds = list.passkeys.map { it.credentialId },
        name = list.accountName,
        displayName = list.accountDisplayName,
    ) // best effort: a device that can't apply it returns Success(Skipped), so the result needs no handling
    return list.passkeys
}

suspend fun deletePasskey(passkeys: Passkeys, api: PasskeyApi, rpId: String, credentialId: String) {
    api.delete(credentialId)
    passkeys.signals.unknownCredential(rpId, credentialId)
}
```

Signals need Android 9+ with a credential provider that supports them, iOS 26+, or a browser with the Signal API.
Elsewhere they are skipped, never failed. The user handle is the base64url `user.id` your server put in the creation
options, never your account ID: see [Choosing user name and display name](user-names.md).

## Test it without a device

`FakePasskeys` stands in for the device in common tests, so you can test these classes on the JVM. A tiny in-memory
`PasskeyApi` plays the server here; in a real test it would talk to your server or a fake of it:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class AddPasskeyTest {
    private val device = FakePasskeys(origin = "https://example.com")

    @Test
    fun closingTheSheetShowsNothing(): Unit = runBlocking {
        device.failNextWith(PasskeyError.Cancelled)
        assertEquals(null, AddPasskey(device, FakeApi()).add())
    }

    @Test
    fun aDeviceWithoutPasskeysHidesTheButton(): Unit = runBlocking {
        device.isSupported = false
        assertEquals(false, AddPasskey(device, FakeApi()).canAdd())
    }

    private class FakeApi : PasskeyApi {
        override suspend fun registrationOptions() = TestOptions.creation("example.com")
        override suspend fun finishRegistration(responseJson: String) = Unit
        override suspend fun signInOptions() = TestOptions.request("example.com")
        override suspend fun finishSignIn(responseJson: String): SignInResult = SignInResult.SignedIn
        override suspend fun list() = PasskeyList("example.com", "AQID", "alice@example.com", "Alice", emptyList())
        override suspend fun delete(credentialId: String) = Unit
    }
}
```

## Related

- [Upgrading users](upgrading-users.md): offering a passkey right after a password sign-in, without a sheet.
- [Choosing your RP ID](choosing-rp-id.md): the decision you can't undo later.
- [Compose](../compose.md): `PasskeySignInButton` and `PasskeyUsernameField` if you use Compose Multiplatform.
