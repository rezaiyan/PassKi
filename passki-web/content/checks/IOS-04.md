# IOS-04: Entitlements list webcredentials

Platform: ios · Severity: error (fails the check)

## What and why
The app's `.entitlements` file must list `webcredentials:<rpId>` under `com.apple.developer.associated-domains`.

## Checked by
`passkiCheck`, `passkiVerify`. The doctor can't read entitlements at runtime.

## Fix
Add webcredentials:<rpId> to com.apple.developer.associated-domains in your .entitlements file (./gradlew passkiSetup writes it).

```xml
<key>com.apple.developer.associated-domains</key>
<array>
    <string>webcredentials:example.com</string>
    <string>webcredentials:example.com?mode=developer</string>
</array>
```
`passkiSetup` merges these into the file and keeps your other keys.
