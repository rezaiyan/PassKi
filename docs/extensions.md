# PRF and largeBlob

`PasskeyExtensions` adds typed WebAuthn extensions to the options JSON from your server. Use it when the inputs come
from the device, for example a PRF salt for client-side encryption that the server must never see. Each helper
returns new JSON, keeps other extensions and a `{"publicKey": ...}` envelope, and throws `IllegalArgumentException`
for the wrong kind of options.

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

| Output | On |
|---|---|
| `prf: PrfResults?` (`first`, `second`) | `PasskeyAssertion`; `PasskeyRegistration` when the provider evaluates PRF at registration |
| `prfEnabled: Boolean?` | `PasskeyRegistration` |
| `largeBlobSupported: Boolean?` | `PasskeyRegistration` |
| `largeBlob: ByteArray?` / `largeBlobWritten: Boolean?` | `PasskeyAssertion` |

The outputs are also in the response `json` (`clientExtensionResults`) for your server. PRF results and blobs are
secrets: `PrfResults.toString()` prints only their sizes.

## Platform notes

- **Android:** the options pass through to the credential provider unchanged. Support depends on the provider; Google
  Password Manager returns PRF results.
- **iOS:** PassKi maps the extensions to the AuthenticationServices PRF and largeBlob inputs. Below iOS 18 (PRF) or
  iOS 17 (largeBlob) the extension is left out silently and its output is `null`, as in browsers. Without salts at
  registration, iOS only reports `prfEnabled`.
- **largeBlob writes** need the passkey named in `allowCredentials`. Read and write are exclusive:
  `withLargeBlobWrite` replaces a `withLargeBlobRead` request.
- **`FakePasskeys`** computes PRF deterministically (HMAC-SHA256 of a per-credential secret and the salt) and stores
  large blobs, so you can test PRF-derived keys without a device. Don't use it as a real key source.

---

[← README](../README.md)
