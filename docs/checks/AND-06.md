# AND-06: PassKi initialised in the app

Platform: android · Severity: error (fails the check)

## What and why
PassKi needs the `Application` to find the foreground Activity and read the app's signature.

## Checked by
On-device `PasskeyDoctor`.

## Fix
Keep PassKi's androidx.startup initializer, or call PasskiAndroid.init(application) in Application.onCreate().

Keep PassKi's androidx.startup initializer in the merged manifest, or call `PasskiAndroid.init(application)` in `Application.onCreate()`.
