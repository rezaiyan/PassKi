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
| `PASSKI_IP_SALT` | `passki` outside production | Salt for hashed client IPs (rate limits). **Required in production**: the server refuses to start without it, or with `change-me`. Generate with `openssl rand -hex 32` |
| `PASSKI_IOS_PER_IP_PER_DAY` | `5` | Self-service iOS registrations per IP per day |
| `PASSKI_IOS_MAX_ACTIVE` | `500` | Global cap of active self-service entries |
| `PASSKI_ANDROID_PER_IP_PER_DAY` | `5` | New Android self-service registrations per client IP per day |
| `PASSKI_ANDROID_MAX_ACTIVE` | `500` | Global cap of active Android package/fingerprint entries |

See `.env.example`.

## Demo RP API

| Method | Path | Body → response |
|---|---|---|
| POST | `/api/demo/register/options` | `{username?}` → creation options; `user.id` is the demo user handle |
| POST | `/api/demo/register/verify` | `RegistrationResponseJSON` or `{response, capture: {platform}}` → verdict |
| POST | `/api/demo/login/options` | `{userHandle?}` → request options (`allowCredentials` for that user, else discoverable) |
| POST | `/api/demo/login/verify` | `AuthenticationResponseJSON` → verdict |
| GET | `/api/demo/server-config` | `{rpId, origins}` (includes the origins of registered Android apps) |
| GET | `/api/demo/fixtures?platform=` | captured registration responses (last 20 per platform, 7 days) |
| GET/POST/DELETE | `/api/demo/ios-apps` | list / `{teamId, bundleId}` → `{appId, expiresAt, deleteToken, entitlement}` / `{appId, token}` |
| GET/POST/DELETE | `/api/demo/android-apps` | list / `{packageName, fingerprints[]}` → `{packageName, fingerprints, origins, created, expiresAt, deleteToken?}` (201 new, 200 already listed) / `{packageName, token}` |

`passkiSetup` with `passki { useDemoServer() }` calls both registration endpoints. Self-service entries are listed in
the demo `assetlinks.json` / `apple-app-site-association` for 7 days, and registered Android signing keys are accepted
as origins by the verify endpoints.

Verdict: `{verified, credentialId, signCount, userVerified, backupEligible, backedUp, aaguid?, userHandle, origin, rpId}`. The challenge is looked up from `clientDataJSON`, so no cookies are needed. Challenges expire after 5 minutes and can be used once.

## Security notes

- **SSRF:** every outbound fetch goes through `lib/net/fetch.ts`. It allows only HTTPS on port 443 to a validated public domain (no IP literals, `localhost`, `.local`, `.internal` or other reserved names). The custom `lookup` resolves A/AAAA and refuses the request if any address is private or reserved, and the socket connects only to the addresses it validated (pinned, so DNS rebinding can't slip in). Redirects are never followed, only reported. Requests time out after 10 s and bodies are capped at 1 MiB (64 KiB for SRV endpoints). User-supplied domains can only reach `/.well-known/{assetlinks.json,apple-app-site-association}`. Google DAL and the Apple CDN use fixed URL templates.
- **Rate limits:** per hashed client IP (first `X-Forwarded-For` entry, as set by Caddy): verify 20/min, generate 30/min, server-check 10/min, reports 10/h, demo register 30/min, demo login 60/min, demo register/login verify 60/min each, iOS and Android self-service 5 new registrations/day each (an Android re-registration of listed keys is free).
- **Reports** are re-validated before storage. Only catalog IDs and known statuses are kept, and titles, severities and docs links come from the catalog. `/api/verify` signs each report (HMAC keyed by `PASSKI_REPORT_SECRET`, else `PASSKI_IP_SALT`), and `/api/reports` stores only reports with a valid signature, so nobody can publish their own text under this domain. Stored reports are capped at 64 KB each and 2000 in total.
- **Storage caps:** at most 5000 live demo users and 10000 live challenges; past that the demo answers 503 until rows expire, so many IPs together can't fill the disk.
- **Demo `assetlinks.json`** grants only `get_login_creds`, never `handle_all_urls`: anyone can list an app there (and the sample's debug key is public), so App Links would let any app open links to this domain.
- **Headers:** HSTS, a Content-Security-Policy (`default-src 'self'`, no framing, no plugins), a Permissions-Policy, `nosniff`, `X-Frame-Options: DENY` and a referrer policy on every response.
- **Cleanup:** expired rows are purged on startup (`instrumentation.ts`) and lazily on every access.

## Deploy

Live at https://passki.alirezaiyan.com.

- **Container:** `passki-passki-web-1`, defined in `/var/www/passki-deploy/docker-compose.yml` on the VPS (compose project `passki`).
- **Env:** `/var/www/passki-deploy/.env.production` (mode 600, not in git); see `.env.example` for the variables.
- **Caddy:** the `passki.alirezaiyan.com` block in `/etc/caddy/Caddyfile` reverse-proxies to the container.
- **Deploy:** run `ali passki` from the MacBook. It does a `git push`, a `git pull` on the VPS, then `docker compose -p passki up -d --build`.
- **Smoke test:** `https://passki.alirezaiyan.com/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` return 200 `application/json` with no redirect, then run `/verify?rpId=passki.alirezaiyan.com`.
