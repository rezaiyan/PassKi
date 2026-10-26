# Spec Delta

## ADDED Requirements

### Requirement: Web page served at a well-known path
Every consumer (`PasskeyDoctor`, `passkiCheck`, the passki-web verifier) SHALL detect when `/.well-known/assetlinks.json` or `/.well-known/apple-app-site-association` returns an HTML document (a body starting with `<`, or `Content-Type: text/html` with a body that isn't JSON) and report it under `AND-01` or `IOS-01` with a message that says a web page was served and a fix about excluding `/.well-known/` from the site's catch-all route.

#### Scenario: SPA catch-all answers assetlinks.json
- **WHEN** `https://example.com/.well-known/assetlinks.json` returns status 200 with the app's `index.html`
- **THEN** the consumer reports `AND-01` as failed with the message "served a web page, not assetlinks.json"
- **AND** the fix says to serve the file before the catch-all route (or from the server) and to keep it out of folders a deploy mirrors

#### Scenario: SPA catch-all answers the AASA
- **WHEN** `https://example.com/.well-known/apple-app-site-association` returns an HTML page
- **THEN** the consumer reports `IOS-01` as failed with the same kind of message and fix

#### Scenario: JSON served with the wrong content type
- **WHEN** the file is valid JSON served as `text/html`
- **THEN** it is checked as JSON, not reported as a web page

#### Scenario: Malformed JSON is still reported as before
- **WHEN** the file is served as JSON but has a syntax error
- **THEN** the existing "not valid JSON" message and fix are reported, not the web-page one

#### Scenario: Same result in every consumer
- **WHEN** the same HTML response is checked by `PasskeyDoctor`, `passkiCheck` and the passki-web verifier
- **THEN** all three report the same check ID and the same catalog fix text
