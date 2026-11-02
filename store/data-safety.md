# Play Console "Data safety" form — draft answers

Drafted from the actual code (not guessed) as of v1.6. Re-verify against the live code before
submitting, since this form is legally significant and the app will keep changing.

## Does your app collect or share any of the required user data types?

**Yes** — location.

## Data types collected

### Location

- **Precise location** — collected. Used for: App functionality (arrival/off-route alarms).
- **Approximate location** — collected as a fallback source. Used for: App functionality.
- Is this data shared with third parties? **No**, not shared in the Play Console sense (no SDK/
  server of ours receives it). It *is* sent, by the user's own action, to third-party map
  services — see "Search text and coordinates" below — but that's the user directly querying
  those services through the app, which Play's data-safety model treats as the app "processing"
  data through a service provider, not "sharing" in the advertising/analytics sense.
- Is this data processed ephemerally? No — a trip's start/end location and route are kept in the
  local Room database (trip history) and a JSON backup file, both on-device only, until the user
  clears history or uninstalls.
- Is collection required or optional? Required for the core feature (arrival/off-route alarms)
  but the app never collects location without an active trip the user started.

### Search text and coordinates (not a Play Console "personal data" category by itself, disclose
in the listing description / this doc for transparency)

- When the user searches for a place, pastes a link, or starts a cab-mode trip, the search text
  or the relevant coordinates are sent over HTTPS to:
  - Nominatim (OpenStreetMap) — search/reverse geocoding
  - Photon (komoot.io, OpenStreetMap-based) — search/reverse geocoding
  - OSRM (project-osrm.org) — route calculation
- No account ID, device ID, or advertising ID is attached to these requests by Knock Me.
- These are third-party services Knock Me doesn't operate; see their own privacy policies for
  what they do with an inbound request (typically: answer it, standard web server logs).

## Data NOT collected

- No personal info (name, email, phone, address) — there's no account.
- No financial info.
- No health/fitness data.
- No messages, photos, videos, audio, files/docs (the memory-mirror JSON file is written by the
  app for the user's own backup use, not collected/transmitted anywhere).
- No app activity, app info/performance, or device/other IDs collected for analytics — there is
  no analytics SDK in this app.
- No advertising ID — no ads.

## Is all of the user data collected encrypted in transit?

Yes — `usesCleartextTraffic="false"` plus a `network_security_config.xml` that only trusts
HTTPS; all three third-party services are called over HTTPS.

## Can users request that their data is deleted?

Yes. Settings → "Clear all history" deletes everything in the local database and rewrites the
backup file empty. Uninstalling the app removes the database entirely (the backup JSON file on
shared storage may need manual deletion from a file manager, documented in PRIVACY.md).

## Data sold to third parties

No.
