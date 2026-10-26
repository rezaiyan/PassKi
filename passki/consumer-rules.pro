# Keep rules that ship with PassKi's Android library and apply to apps' minified (R8) builds.
# Each rule names what needs it; CI builds ci/consumers/android with R8 and no app-side rules.

# PassKi's debug log and PasskeyError.Unknown.platformCode name the Credential Manager exception a failure came from
# (e.g. "CreateCredentialCancellationException"). Without this, R8 renames them to "hd" and the name tells nothing.
# Names only: the classes are still shrunk and optimised.
-keepnames class androidx.credentials.exceptions.** extends java.lang.Throwable
