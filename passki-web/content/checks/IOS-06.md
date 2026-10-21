# IOS-06: Built app carries the entitlement

Platform: ios · Severity: error (fails the check)

## What and why
The final proof: the signed `.app` carries the entitlement.

## Checked by
`passkiCheck` inspects the last build in the project's DerivedData: `codesign -d --entitlements - --xml` for device builds, `<App>.app-Simulated.xcent` for Simulator builds. Skipped when no build exists.

## Fix
The signed app does not contain the associated-domains entitlement. Rebuild after fixing IOS-05, and check signing (team, provisioning).

Rebuild after fixing IOS-05. If it still fails, check the signing team and provisioning profile include Associated Domains.
