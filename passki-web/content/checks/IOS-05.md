# IOS-05: Entitlements wired into the Xcode build

Platform: ios · Severity: error (fails the check)

## What and why
An entitlements file only counts when the build points at it via `CODE_SIGN_ENTITLEMENTS`. Without it Xcode signs the app with no associated domains and passkeys fail silently, only on Xcode builds.

## Checked by
`passkiCheck` runs `xcodebuild -showBuildSettings -json` and resolves `CODE_SIGN_ENTITLEMENTS`; skipped where `xcodebuild` is unavailable (Linux CI).

## Fix
Set CODE_SIGN_ENTITLEMENTS = <path>.entitlements, e.g. in Configuration/Config.xcconfig (./gradlew passkiSetup does this without touching project.pbxproj).

```
// iosApp/Configuration/Config.xcconfig (the base configuration of the KMP template)
CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements
```
`passkiSetup` writes this line and never touches `project.pbxproj`. Without an xcconfig: Xcode > target > Build Settings > Code Signing Entitlements. A value set on the target in the pbxproj overrides the xcconfig.
