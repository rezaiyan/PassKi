# IOS-02: App listed under webcredentials

Platform: ios · Severity: error (fails the check)

## What and why
`webcredentials.apps` must contain `<TeamID>.<bundleId>` exactly, e.g. `ABCDE12345.com.example.app`.

## Checked by
`passkiCheck`, `passkiVerify`, `PasskeyDoctor`.

## Fix
Add "<teamId>.<bundleId>" to webcredentials.apps in the apple-app-site-association file.

```json
{"webcredentials": {"apps": ["ABCDE12345.com.example.app"]}}
```
`passkiSetup` reads the Team ID and bundle ID from `xcodebuild -showBuildSettings` when they are not set in `passki { apple { } }`.
