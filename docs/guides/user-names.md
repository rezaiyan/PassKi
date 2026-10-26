# Choosing user name and display name

The creation options your server sends have a `user` object with three fields. The password manager stores all
three with the passkey and shows two of them in every sign-in sheet:

```json
"user": { "id": "<user handle>", "name": "alice@example.com", "displayName": "Alice" }
```

## The user handle (`user.id`)

- **Random, at least 16 bytes, base64url.** Create it once when an account adds its first passkey, and store it with
  the account.
- **The same for every passkey of an account.** Password managers group passkeys by user handle: a new handle for a
  second passkey makes it show up as a second account. List the account's existing passkeys in `excludeCredentials`
  too, so a device that already has one reports `AlreadyRegistered`.
- **Never the account ID, an email address or anything else meaningful.** It is stored on the device and synced, and
  some password managers show it. It is also what signals need: `acceptedCredentials`, `currentUserDetails` and
  `syncAccount` take this handle as `userHandle`, and `PasskeyAssertion.userHandle` returns it after a sign-in.

## The name and display name

`name` is what identifies the account in the sheet (usually the email address or username), and `displayName` is a
friendlier label. Pick what a person recognises *as their account in your app*:

- **Hide My Email addresses help nobody.** People who signed up with Apple often have an address such as
  `x7k2…@privaterelay.appleid.com`. Show their name and the sign-in method instead, for example `Alice (Apple ID)`.
- **No email at all** (phone or username accounts): use the username or a masked phone number.
- **Keep it short.** Sheets cut long labels off.

When the label changes (the user changes their email or name), tell the password manager, so passkeys already saved
show the new one: call `signals.currentUserDetails`, or `signals.syncAccount` when Settings loads the account's
passkeys ([example](extra-sign-in.md#keep-the-password-manager-in-step)).

## On the server

```kotlin server
import java.security.SecureRandom
import java.util.Base64

private val random = SecureRandom()

/** A new user handle: 32 random bytes, base64url. Create it once per account and store it with the account. */
fun newUserHandle(): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

class PasskeyLabel(val name: String, val displayName: String)

/** What the passkey sheet shows for an account: never a Hide My Email address. */
fun passkeyLabel(email: String?, fullName: String?, signInMethod: String): PasskeyLabel {
    val relay = email != null && email.endsWith("@privaterelay.appleid.com")
    val person = fullName?.takeIf { it.isNotBlank() }
    return when {
        email != null && !relay -> PasskeyLabel(name = email, displayName = person ?: email)
        person != null -> PasskeyLabel(name = "$person ($signInMethod)", displayName = person)
        else -> PasskeyLabel(name = "Your $signInMethod account", displayName = "Your $signInMethod account")
    }
}
```

```kotlin server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PasskeyLabelTest {
    @Test
    fun hideMyEmailIsReplacedByTheNameAndSignInMethod() {
        val label = passkeyLabel("x7k2q@privaterelay.appleid.com", "Alice", "Apple ID")
        assertEquals("Alice (Apple ID)", label.name)
    }

    @Test
    fun aRealEmailIsShownAsIs() {
        assertEquals("alice@example.com", passkeyLabel("alice@example.com", "Alice", "Google").name)
    }

    @Test
    fun handlesAreRandom() {
        assertNotEquals(newUserHandle(), newUserHandle())
        assertEquals(43, newUserHandle().length) // 32 bytes, base64url without padding
    }
}
```
