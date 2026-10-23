# web-setup-tools Specification

## Purpose
The passki-web console tools generate well-known files, check a live domain and a WebAuthn server against the shared check catalog, and share reports, without the user installing anything, while keeping server-side fetches safe.

## Requirements

### Requirement: Well-known file generator
`POST /api/generate {rpId, packageName, fingerprints | android[], teamId, bundleId | ios[]}` SHALL return the generated setup files as JSON, or as a zip download named `passki-setup-<rpId>.zip` when `?format=zip` is given.

#### Scenario: Zip download
- **WHEN** the generator is called with `?format=zip`
- **THEN** the response is `application/zip` with `Content-Disposition: attachment; filename="passki-setup-<rpId>.zip"` and `no-store` caching

### Requirement: Domain verifier
`POST /api/verify {rpId, packageName?, fingerprints?, teamId?, bundleId?}` SHALL check the live domain and return a report of catalog checks `AND-01`..`AND-04` and `IOS-01`..`IOS-03`.

#### Scenario: Missing delegate permission
- **WHEN** the domain's `assetlinks.json` lacks `delegate_permission/common.get_login_creds` for the package
- **THEN** the report marks `AND-02` failed with its catalog fix

### Requirement: Server check
`POST /api/server-check {rpId, optionsUrl?, method?, serverConfigUrl?, packageName?, fingerprints?}` SHALL check `SRV-01`/`SRV-03` against a registration-options endpoint (GET or POST, POST by default) and `SRV-01`/`SRV-02` against a server-config URL, and MUST require at least one of the two URLs.

#### Scenario: No URL given
- **WHEN** neither `optionsUrl` nor `serverConfigUrl` is given
- **THEN** the server answers with an input error asking for one of them

### Requirement: Shareable reports
`POST /api/reports` SHALL store only a report signed by `/api/verify` (at most 64 KB, at most 2000 stored at once) for 7 days and return `{id, url: "/r/<id>", expiresAt}`; `GET /api/reports/<id>` and the page `/r/<id>` SHALL show it read-only, and expired or unknown ids MUST return 404.

#### Scenario: Report not produced by the verifier
- **WHEN** a client posts a report without a valid signature, or with any result text changed
- **THEN** the server answers 400 and stores nothing

#### Scenario: Expired report
- **WHEN** a report link is opened after 7 days
- **THEN** the API answers 404 "report not found or expired"

### Requirement: SSRF-safe fetching
Every server-side fetch to a user-supplied target MUST use HTTPS on port 443 to a syntactically valid public domain whose every resolved address is public, MUST reject internal suffixes (such as `localhost`, `local`, `internal`, `test`, `onion`) and private, loopback and link-local ranges, MUST NOT follow redirects (a 3xx is reported with its target), and MUST cap response size and time.

#### Scenario: DNS points to a private address
- **WHEN** a domain resolves to any private or loopback address
- **THEN** the fetch is refused before connecting

#### Scenario: Redirecting well-known file
- **WHEN** `/.well-known/assetlinks.json` answers with a redirect
- **THEN** the redirect is not followed and the check reports it, because platforms reject redirected association files

### Requirement: Rate-limited tools
The generator, verifier, server check and report endpoints MUST be rate-limited per client IP.

#### Scenario: Server check flood
- **WHEN** one IP exceeds the server-check limit
- **THEN** further requests are refused until the window passes
