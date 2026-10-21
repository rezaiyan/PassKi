# AND-04: Google Digital Asset Links agrees

Platform: android · Severity: warning

## What and why
Google's Digital Asset Links API is what Google Play services consult. It can disagree with the file on your server while its cache is stale or when the file is not publicly reachable.

## Checked by
`passkiCheck` queries `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://<rpId>&relation=delegate_permission/common.get_login_creds`.

## Fix
Google's Digital Asset Links API doesn't see the statement yet. Check the file is public and valid; Google may cache for a while.

Open the URL above in a browser and look for your package and fingerprint. If AND-01..03 pass, wait and re-check; the API caches.
