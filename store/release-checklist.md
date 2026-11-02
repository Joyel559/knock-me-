# Release checklist

## 1. Create a keystore (once)

```
keytool -genkey -v -keystore knockme-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias knockme
```

Keep this file and its passwords safe and backed up — losing it means you can never update the
app under the same listing again.

## 2. Fill in keystore.properties

Copy `keystore.properties.example` to `keystore.properties` (project root) and fill in the real
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`. This file is git-ignored — never commit
it.

## 3. Set the version

Edit `gradle.properties`: bump `APP_VERSION_CODE` by 1 from the last published release, and set
`APP_VERSION_NAME` (e.g. `1.6.0`).

## 4. Build the release bundle

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

## 5. Test on a real device

Install the signed APK (or use `bundletool` to generate one from the `.aab`) on a real phone and
run the manual test list in the final report of the v1.6 session (arrival alarm with screen
locked, watchdog, Max reliability steps, offline/permission-revoked behavior).

## 6. Internal testing track

Upload the `.aab` to Play Console's Internal testing track first. Add yourself as a tester,
install via the opt-in link, sanity-check the real signed build.

## 7. Closed testing track

**New personal Play Console developer accounts (created after ~Nov 2023) are currently required
to run a closed test with at least 12 testers opted in for 14 continuous days before production
access is granted.** This number and the exact rule have changed before (it was 20 testers before
Dec 2024) — **verify the current requirement in Play Console → your app → Testing → Closed
testing before relying on these numbers**, since Google updates this policy from time to time.
Organization developer accounts and accounts created before the cutoff are exempt.

## 8. Production

Once closed testing requirements are satisfied (or you're exempt), promote the tested release to
the Production track, fill in the Play Console "Data safety" form (draft in
`store/data-safety.md`), the store listing (draft in `store/listing.md`), and the permission
declarations (draft in `store/permissions-justification.md`), then submit for review.

## Third-party service risk (read before a real public launch)

Knock Me uses three free, public, keyless services by default:

- **OSRM** demo server (`router.project-osrm.org`) — routing
- **Nominatim** (`nominatim.openstreetmap.org`) — search/geocoding
- **Photon** (`photon.komoot.io`) — search/geocoding

All three publish fair-use limits and are explicitly **not** meant to carry traffic from a large
number of app users — they exist for light/occasional use and development. If Knock Me gets real
adoption, these servers will likely start rate-limiting or blocking it, which (by design) only
degrades gracefully — search/routing stop working, the arrival alarm keeps working — but is a
bad user experience at scale.

**Options before a real public launch:**

1. **Self-host.** Run your own OSRM instance (needs a prebuilt OSM extract for your coverage
   area, moderate server resources), and/or your own Nominatim/Photon instance (Nominatim import
   is resource-heavy; Photon is lighter). Point `OSRM_BASE_URL`/`NOMINATIM_BASE_URL`/
   `PHOTON_BASE_URL` in `gradle.properties` at your own servers — the app code already reads
   these from `BuildConfig`, no code changes needed.
2. **Paid hosted provider.** Several companies offer hosted OSRM/Nominatim-compatible routing
   and geocoding (commercial OSM-data hosts, or a different geocoder/router behind the same
   `RoutingProvider`/`GeocodeProvider` interfaces this app already uses). Requires an API key and
   likely a small code change to add the key header — the provider interfaces were kept generic
   specifically so this swap is contained.
3. **Stay on the public servers** for low/personal traffic (the app's original design goal) and
   accept the fair-use risk; the app already handles failure gracefully either way.

## 16 KB page size (Android 15+ requirement)

Checked the debug APK's native libraries: only `libandroidx.graphics.path.so` and
`libdatastore_shared_counter.so` are present (both transitively from AndroidX, not added by this
app). Their ELF `PT_LOAD` segment alignment was inspected directly and is **16384 (16 KB)** —
already compliant. No native code is added by this project itself.
