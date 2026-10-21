# IOS-03: Apple CDN has the app

Platform: ios · Severity: warning

## What and why
Devices read the AASA file from Apple's CDN (`https://app-site-association.cdn-apple.com/a/v1/<rpId>`), not from your server. The CDN can lag up to 24h.

## Checked by
`passkiCheck`, `PasskeyDoctor` (warning only).

## Fix
Apple's CDN can lag up to 24h. While testing use webcredentials:<rpId>?mode=developer and enable Settings > Developer > Associated Domains Development.

While testing add `webcredentials:<rpId>?mode=developer` to the entitlements (`passkiSetup` does this when `apple.developerMode` is true) and enable Settings > Developer > Associated Domains Development on the device.
