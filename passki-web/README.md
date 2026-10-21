# passki-web: PassKi Setup dashboard + demo relying party

Next.js (App Router) app intended for `https://passki.alirezaiyan.com`, running in its own container:

- **Generator** (`/generator`, `POST /api/generate[?format=zip]`): rpId, packages + SHA-256 fingerprints (paste `./gradlew passkiFingerprints` output), Team ID, bundle ID → `assetlinks.json`, `apple-app-site-association`, entitlements plist, `Config.xcconfig` line, `server-config.json` (`{rpId, origins}`), verifier snippets (SimpleWebAuthn, webauthn4j, java-webauthn-server, py_webauthn), zip download.
- **Verifier** (`/verify`, `POST /api/verify`): fetches the domain's `.well-known` files, Google Digital Asset Links and Apple's CDN server-side and runs **AND-01..04, IOS-01..03** with the same semantics as the Kotlin doctor/plugin. It can re-check live every 10 s.
- **Shareable reports** (`POST /api/reports` → `/r/<id>`, 7-day TTL) and **server checks** (`POST /api/server-check`: SRV-01/03 from a registration-options URL, SRV-01/02 from a `server-config.json` URL).
- **Check docs** (`/checks`, `/checks/<ID>`): rendered from the canonical catalog.
- **Demo RP** (`/api/demo/...`, `/demo`): SimpleWebAuthn registration/login with server-side challenges, anonymous 24 h demo users, opt-in fixture capture, plus the demo `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` with self-service iOS entries (7 days).

## Check catalog

The single source of truth is `../docs/checks/catalog.json` (+ `../docs/checks/<ID>.md`). `npm run sync` (it runs automatically before `dev`, `build` and `test`) mirrors them into `content/checks/`, so the app never reads files outside `passki-web/` at runtime and a Docker build with `passki-web/` as context still works. Commit the mirror after the catalog changes. A test fails if the mirror drifts from the source.

## Local development

```bash
npm ci
npm run dev        # http://localhost:3000
npm test           # vitest
npm run typecheck
npm run build      # standalone output in .next/standalone
```

The SQLite database is created at `PASSKI_DB_PATH` (default `./data/passki.db`, gitignored).

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `PASSKI_RP_ID` | `passki.alirezaiyan.com` | Demo RP ID; `https://<rpId>` is an accepted origin |
| `PASSKI_RP_NAME` | `PassKi demo` | RP name in creation options |
| `PASSKI_ANDROID_PACKAGE` | `com.alirezaiyan.passki` | Package in the demo `assetlinks.json` |
| `PASSKI_ANDROID_FINGERPRINTS` | sample debug key `C9:64:…:49:03` | Comma list; demo assetlinks + `android:apk-key-hash:` origins |
| `PASSKI_APPLE_APP_IDS` | `VFCFJC7Y5J.com.alirezaiyan.passki.PassKi` | Permanent entries in the demo AASA |
| `PASSKI_EXTRA_ORIGINS` | (none) | Extra accepted origins, e.g. for a local browser demo |
| `PASSKI_DB_PATH` | `./data/passki.db` | SQLite file (`/app/data/passki.db` in Docker) |
| `PASSKI_IP_SALT` | `passki` | Salt for hashed client IPs (rate limits); set a random value in production |
| `PASSKI_IOS_PER_IP_PER_DAY` | `5` | Self-service iOS registrations per IP per day |
| `PASSKI_IOS_MAX_ACTIVE` | `500` | Global cap of active self-service entries |

See `.env.example`.

## Demo RP API

| Method | Path | Body → response |
|---|---|---|
| POST | `/api/demo/register/options` | `{username?}` → creation options; `user.id` is the demo user handle |
| POST | `/api/demo/register/verify` | `RegistrationResponseJSON` or `{response, capture: {platform}}` → verdict |
| POST | `/api/demo/login/options` | `{userHandle?}` → request options (`allowCredentials` for that user, else discoverable) |
| POST | `/api/demo/login/verify` | `AuthenticationResponseJSON` → verdict |
| GET | `/api/demo/server-config` | `{rpId, origins}` |
| GET | `/api/demo/fixtures?platform=` | captured registration responses (last 20 per platform, 7 days) |
| GET/POST/DELETE | `/api/demo/ios-apps` | list / `{teamId, bundleId}` → `{appId, expiresAt, deleteToken, entitlement}` / `{appId, token}` |

Verdict: `{verified, credentialId, signCount, userVerified, backupEligible, backedUp, aaguid?, userHandle, origin, rpId}`. The challenge is looked up from `clientDataJSON`, so no cookies are needed. Challenges expire after 5 minutes and can be used once.

## Security notes

- **SSRF:** every outbound fetch goes through `lib/net/fetch.ts`. It allows only HTTPS on port 443 to a validated public domain (no IP literals, `localhost`, `.local`, `.internal` or other reserved names). The custom `lookup` resolves A/AAAA and refuses the request if any address is private or reserved, and the socket connects only to the addresses it validated (pinned, so DNS rebinding can't slip in). Redirects are never followed, only reported. Requests time out after 10 s and bodies are capped at 1 MiB (64 KiB for SRV endpoints). User-supplied domains can only reach `/.well-known/{assetlinks.json,apple-app-site-association}`. Google DAL and the Apple CDN use fixed URL templates.
- **Rate limits:** per hashed client IP (first `X-Forwarded-For` entry, as set by Caddy): verify 20/min, server-check 10/min, reports 30/h, demo register 30/min, demo login 60/min, iOS self-service 5/day.
- **Reports** are re-validated before storage. Only catalog IDs and known statuses are kept, and titles, severities and docs links come from the catalog.
- **Cleanup:** expired rows are purged on startup (`instrumentation.ts`) and lazily on every access.

## Deploy (not done; each step needs explicit confirmation)

1. **DNS:** add an `A` record `passki.alirezaiyan.com → 148.230.109.213` (and `AAAA` if the VPS has IPv6).
2. **Code on the VPS:** get `passki-web/` onto the VPS (e.g. pull the PassKi repo after the commits are pushed).
3. **Env:** create `passki-web/.env.production` from `.env.example` with a random `PASSKI_IP_SALT`. Double-check `PASSKI_ANDROID_FINGERPRINTS` once the sample debug keystore is committed (Phase 2).
4. **Compose:** add the service from `deploy/docker-compose.snippet.yml` (container port 3000 → `127.0.0.1:3100`, named volume for `/app/data`), then `docker compose up -d --build passki-web`.
5. **Caddy:** add the site block from `deploy/Caddyfile.snippet` and reload Caddy (TLS is issued automatically once DNS resolves).
6. **`ali` target (optional):** add a `passki` deploy/logs/restart target to `~/bin/ali`.
7. **Smoke test:** `https://passki.alirezaiyan.com/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` return 200 `application/json` with no redirect, then run `/verify?rpId=passki.alirezaiyan.com`.
