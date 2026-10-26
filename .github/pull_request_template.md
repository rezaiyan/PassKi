## What and why

<!-- What changes for someone using PassKi, and why. Link the issue or OpenSpec change if there is one. -->

## Checklist

- [ ] Tests for new behaviour; the full suite passes (see [CONTRIBUTING.md](https://github.com/rezaiyan/PassKi/blob/main/CONTRIBUTING.md#checks-before-a-pull-request))
- [ ] Public API changed: `./gradlew updateLegacyAbi` run and the `*/api/` dumps committed, KDoc for new declarations
- [ ] Breaking change: CHANGELOG entry marked **BREAKING** with a "Migrating from" block ([versioning](https://github.com/rezaiyan/PassKi/blob/main/docs/versioning.md))
- [ ] Docs and examples updated (`python3 ci/doc-snippets.py` passes)
- [ ] Platform behaviour (sheets, autofill, signals, PRF) checked on a device: OS version and credential provider below

## Verified on

<!-- Devices, OS versions and credential providers, or "not device-dependent". -->
