# PRF and largeBlob

Two WebAuthn extensions, added to the options from your server with `PasskeyExtensions`:

- **PRF** gives you 32 secret bytes that depend on the passkey and a salt you choose. The same passkey and salt
  always give the same bytes, so you can use them as an encryption key. The salt can come from the device, so your
  server never sees it.
- **largeBlob** stores a small amount of data with a passkey, and reads it back during sign-in.

Each helper returns new options JSON. It keeps any other extensions and a `{"publicKey": ...}` wrapper. It throws
`IllegalArgumentException` if you pass the wrong kind of options (for example request options to a registration-only
helper).

```kotlin
// PRF: the same passkey and salt always give the same 32 bytes, so they can derive an encryption key.
val options = PasskeyExtensions.withPrf(api.authenticationOptions(), first = salt) // optional second = salt2
passkeys.signIn(options).onSuccess { assertion ->
    val key = assertion.prf?.first // null if PRF is not supported here
}

// At registration: ask whether the new passkey supports PRF.
passkeys.register(PasskeyExtensions.withPrf(api.registrationOptions()))
    .onSuccess { println(it.prfEnabled) } // true / false / null (not answered)

// largeBlob: create with storage, then read or write while signing in.
passkeys.register(PasskeyExtensions.withLargeBlob(creationOptions, LargeBlobSupport.Preferred))  // .largeBlobSupported
passkeys.signIn(PasskeyExtensions.withLargeBlobWrite(requestOptions, blob))                         // .largeBlobWritten
passkeys.signIn(PasskeyExtensions.withLargeBlobRead(requestOptions))                                // .largeBlob
```

Where the results appear:

| Result | On |
|---|---|
| `prf: PrfResults?` (`first`, `second`) | `PasskeyAssertion`. Also `PasskeyRegistration`, when the credential provider computes PRF during registration |
| `prfEnabled: Boolean?` | `PasskeyRegistration` |
| `largeBlobSupported: Boolean?` | `PasskeyRegistration` |
| `largeBlob: ByteArray?` and `largeBlobWritten: Boolean?` | `PasskeyAssertion` |

The results are also in the response `json` (`clientExtensionResults`) that you send to your server. PRF results and
blobs are secrets, so `PrfResults.toString()` prints only their sizes.

## Platform notes

- **Android:** PassKi passes the options to the credential provider unchanged. Whether the extensions work depends on
  the provider. Google Password Manager returns PRF results.
- **iOS:** PassKi turns the extensions into AuthenticationServices' PRF and largeBlob inputs. On iOS before 18 (PRF)
  or 17 (largeBlob), the extension is left out without an error, and its result is `null`, as in browsers. If you
  give no salt at registration, iOS reports only `prfEnabled`.
- **Writing a large blob** needs the passkey to be named in `allowCredentials`. You can read or write in one request,
  not both: `withLargeBlobWrite` replaces an earlier `withLargeBlobRead`.
- **`FakePasskeys`** computes PRF in a repeatable way (HMAC-SHA256 of a secret per passkey and the salt) and stores
  large blobs, so you can test PRF-based keys without a device. Never use it as a real key source.

---

[← Docs](README.md)
