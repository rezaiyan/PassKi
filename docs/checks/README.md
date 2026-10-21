# Setup checks

Stable IDs shared by `./gradlew passkiCheck`, the on-device `PasskeyDoctor` and the setup dashboard. The source of truth is [catalog.json](catalog.json).

| ID | Platform | Severity | Check |
|---|---|---|---|
| [AND-01](AND-01.md) | android | error | assetlinks.json reachable |
| [AND-02](AND-02.md) | android | error | Login credentials delegated to the app |
| [AND-03](AND-03.md) | android | error | Signing certificate listed |
| [AND-04](AND-04.md) | android | warning | Google Digital Asset Links agrees |
| [AND-05](AND-05.md) | android | warning | Play App Signing key listed |
| [AND-06](AND-06.md) | android | error | PassKi initialised in the app |
| [IOS-01](IOS-01.md) | ios | error | apple-app-site-association reachable |
| [IOS-02](IOS-02.md) | ios | error | App listed under webcredentials |
| [IOS-03](IOS-03.md) | ios | warning | Apple CDN has the app |
| [IOS-04](IOS-04.md) | ios | error | Entitlements list webcredentials |
| [IOS-05](IOS-05.md) | ios | error | Entitlements wired into the Xcode build |
| [IOS-06](IOS-06.md) | ios | error | Built app carries the entitlement |
| [SRV-01](SRV-01.md) | server | error | Server RP ID matches |
| [SRV-02](SRV-02.md) | server | error | Server allows all origins |
| [SRV-03](SRV-03.md) | server | error | Server options are valid WebAuthn |
| [CFG-01](CFG-01.md) | config | error | RP ID is a bare domain |
| [DEV-01](DEV-01.md) | device | warning | Android device can store passkeys |
| [DEV-02](DEV-02.md) | device | warning | iOS device can use passkeys |
| [DEV-03](DEV-03.md) | device | error | Platform supports passkeys |
