# Security policy

PassKi sits in your sign-in flow, so security reports get priority over everything else.

## Reporting a vulnerability

**Please don't open a public issue.** Report it privately through GitHub's security advisories: on this repository,
**Security → Report a vulnerability**. Include the PassKi version, the platform, and the steps or code that show the
problem.

- You get a first reply within **3 working days**.
- A confirmed issue gets a fix and a release as soon as possible; we agree on a disclosure date with you, at most 90
  days after the report. You're credited in the advisory unless you'd rather not be.

## Supported versions

| Version | Security fixes |
|---|---|
| The latest release (an alpha, before 1.0) | Yes |
| Earlier alphas | No: upgrade to the latest release |

From 1.0, the latest minor version of each supported major version gets fixes.

## What's in scope

PassKi's libraries (`passki`, `passki-compose`, `passki-test`, `passki-demo`, `passki-bom`), the Gradle plugin, and the
hosted demo server and setup dashboard at passki.alirezaiyan.com. PassKi never verifies responses itself; what your
server must check is in the [security model](docs/security-model.md). A weakness in your own server's verification is
out of scope, but if PassKi's docs or examples led you there, please tell us.
