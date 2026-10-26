# Spec Delta

## ADDED Requirements

### Requirement: Warning for an RP ID shared with other apps
`passkiSetup` SHALL warn once when the RP ID is a registrable domain (no subdomain) and its live `assetlinks.json` or `apple-app-site-association` already lists apps other than the configured ones. The warning MUST say that the RP ID can't change later without orphaning passkeys and suggest a product subdomain. It MUST NOT fail the task.

#### Scenario: Company apex already used by another app
- **WHEN** `passkiSetup` runs with `rpId = "example.com"` and `https://example.com/.well-known/assetlinks.json` lists `com.example.other`
- **THEN** the task prints the warning naming `com.example.other` and suggesting a subdomain such as `app.example.com`, and succeeds

#### Scenario: Dedicated product domain
- **WHEN** the RP ID is a subdomain, or the live files list only the configured apps
- **THEN** no warning is printed

#### Scenario: Offline setup
- **WHEN** the live files can't be fetched
- **THEN** no warning is printed and the task behaves as before
