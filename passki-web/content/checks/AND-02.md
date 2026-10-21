# AND-02: Login credentials delegated to the app

Platform: android · Severity: error (fails the check)

## What and why
The statement for your package must grant the relation `delegate_permission/common.get_login_creds`; `handle_all_urls` alone is for App Links, not credentials.

## Checked by
`passkiCheck`, `passkiVerify`, `PasskeyDoctor`.

## Fix
Add a statement with relation "delegate_permission/common.get_login_creds" for package <package> to assetlinks.json.

```json
[{
  "relation": ["delegate_permission/common.get_login_creds"],
  "target": {"namespace": "android_app", "package_name": "com.example.app",
             "sha256_cert_fingerprints": ["AB:CD:..."]}
}]
```
Every package variant (`applicationIdSuffix`, flavors) needs its own statement; `passkiWellKnown` generates one per package.
