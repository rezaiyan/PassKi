# Logging

PassKi logs setup problems without any code from you, so a misconfigured release build still says why passkeys
fail: logcat (tag `PassKi`) on Android, `NSLog` on iOS, the browser console on the web, standard error on the JVM.

```kotlin
PassKi.logLevel = if (BuildConfig.DEBUG) PasskiLogLevel.Debug else PasskiLogLevel.Error
```

| Level | What's logged |
|---|---|
| `None` | Nothing |
| `Error` (default) | Failures with a fix hint (`Misconfigured`, `InvalidOptions`) and `Unknown`: the call, check ID, fix and docs link |
| `Warn` | Also `Interrupted` and `NotSupported` |
| `Info` | Also each call's outcome, including the user closing the sheet |
| `Debug` | Also each call's start (mode, RP ID, extensions) and the platform error each failure was mapped from |

A setup error at the default level looks like this in logcat:

```text
E PassKi  : signIn failed: Misconfigured [AND-03]: … Fix: Add the SHA-256 fingerprint … Docs: https://passki.alirezaiyan.com/checks/AND-03
```

## Sending entries elsewhere

Set a handler. It replaces the platform log; `PasskiLogHandler.Platform` restores it.

```kotlin
PassKi.logHandler = PasskiLogHandler { level, tag, message, throwable ->
    when (level) {
        PasskiLogLevel.Error -> Timber.tag(tag).e(throwable, message)
        PasskiLogLevel.Warn -> Timber.tag(tag).w(throwable, message)
        else -> Timber.tag(tag).d(throwable, message)
    }
}
```

```kotlin
// Kermit
PassKi.logHandler = PasskiLogHandler { level, tag, message, throwable ->
    Logger.log(if (level == PasskiLogLevel.Error) Severity.Error else Severity.Debug, tag, throwable, message)
}
// Crashlytics: setup errors end up in the crash report's log
PassKi.logHandler = PasskiLogHandler { _, tag, message, _ -> Firebase.crashlytics.log("$tag $message") }
```

`throwable` is the platform error behind a `PasskeyError.Unknown`, if there is one.

## What's never logged

Challenges, user handles, options or response JSON, passwords, PRF outputs and large-blob data. Credential IDs appear
as their first 8 characters (`duiH5hP-…`) and JSON as its length. A test checks a full debug trace for each of these
([`PasskiLogRedactionTest`](../passki-test/src/commonTest/kotlin/com/alirezaiyan/passki/test/FakePasskeysLoggingTest.kt)).

If your app already logs PassKi errors, delete that code or set `PasskiLogLevel.None` to avoid duplicates.
