# PRD: Knock Me (formerly "TripGuard")

Personal Android app. One owner, one phone. Not for Play Store. Goal: ship a working version fast, not a perfect one.

---

## 0. Instructions for the AI coding agent (read first)

1. **Do not over-research.** This PRD already makes the decisions. Read it, then build. If something is unclear, pick the simplest option in this document and note it in `docs/DECISIONS.md`.
2. **Keep memory in `docs/`.** Create and maintain these files so work survives between sessions:
   - `docs/MEMORY.md`: what the project is, current state, how to build and run, gotchas.
   - `docs/PROGRESS.md`: milestone checklist (Section 14). Tick items as they finish. Write what is next.
   - `docs/DECISIONS.md`: every choice you made that was not spelled out here, one line each, with the reason.
   - Read all three at the start of every session. Update them at the end of every session.
3. **`docs/` must be git-ignored.** Create a `.gitignore` at the project root and make sure it contains the line `docs/` (plus the normal Android entries: `build/`, `.gradle/`, `local.properties`, `.idea/`, `*.iml`, `captures/`, `.DS_Store`). Add `docs/` to `.gitignore` before the first commit. Do not commit anything inside `docs/`.
4. Build in the order of the milestones in Section 14. Do not start a milestone before the previous one compiles and runs.
5. Use whatever Kotlin, Gradle, AGP and Compose versions the Android Studio "Empty Activity (Compose)" template produces. Do not invent version numbers. Pull library versions from the latest stable release when you add them.
6. UI is plain. Do not spend time on design (Section 9).
7. No dependency injection framework, no multi-module setup. Single `app` module, simple packages.

---

## 1. Problem

The owner travels 1 to 2 hours by bus, train, and cab (Uber, Rapido, etc.), often sleeping. Two failures:

1. **Missed stop.** Falls asleep and wakes after the destination.
2. **Wrong route.** In a cab, the driver takes a wrong or unexpected road and the owner does not notice.

No existing app does both, and none runs reliably in the background without draining the battery.

## 2. Goals

| # | Goal |
|---|------|
| G1 | **Arrival alarm:** a loud alarm rings when the phone gets within a chosen radius of the destination (default 3 km). |
| G2 | **Wrong-route alarm:** a loud alarm rings when the phone has left the planned route by more than 500 m. |
| G3 | Works with the screen locked, phone in pocket, silent mode on, and while Uber, Rapido or Google Maps is in the foreground. |
| G4 | Low battery use: GPS polling adapts to distance and mode. |
| G5 | Works with any ride or transport. The app never talks to Uber, Rapido or Maps. It only needs GPS. |

## 3. Non-goals

- iPhone, web or PWA version.
- Integration with the Uber, Rapido or Google Maps apps (not possible, and not needed).
- Accounts, cloud sync, analytics, ads, Play Store release.
- Turn-by-turn navigation.
- Rail routing. For trains the app only does the arrival alarm.
- Polished design.

## 4. Platform and tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, black and white theme |
| Min SDK | 26 (Android 8.0) |
| Target and compile SDK | the latest stable SDK the installed Android Studio provides |
| Build | Gradle (Kotlin DSL), version catalog (`libs.versions.toml`) |
| Location | Google Play services `FusedLocationProviderClient` (`play-services-location`) |
| Background | Foreground Service, type `location`, with a persistent notification |
| Concurrency | Kotlin Coroutines and Flow |
| Map and destination picker | `osmdroid` (OpenStreetMap, no API key). If osmdroid fails to integrate cleanly, switch to MapLibre and record it in `docs/DECISIONS.md`. |
| Place search (geocoding) | Nominatim: `https://nominatim.openstreetmap.org/search?q=<text>&format=json&limit=5`. Search on button press only (no search-as-you-type). Send a custom `User-Agent` header. Max 1 request per second. |
| Routing | OSRM public server: `https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson`. Put it behind a `RoutingProvider` interface so it can be swapped later (OpenRouteService, Google Routes). |
| HTTP and JSON | OkHttp and `kotlinx.serialization` |
| Local storage | Jetpack DataStore (settings and the active trip as JSON). Room is not needed. |
| Navigation | Navigation Compose |
| Tests | JUnit for geometry and trip logic |

No API keys are required anywhere. Do not add Firebase or Google Maps SDK.

## 5. Permissions and manifest

Request in this order with a short on-screen explanation before each one:

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (second prompt, "Allow all the time")
- `POST_NOTIFICATIONS` (Android 13+)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `USE_FULL_SCREEN_INTENT` (check `NotificationManager.canUseFullScreenIntent()` on Android 14+, and send the user to the system setting if denied)
- `WAKE_LOCK`, `VIBRATE`
- `RECEIVE_BOOT_COMPLETED`
- `INTERNET`, `ACCESS_NETWORK_STATE`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (open the system exemption screen from Settings)

Add a **Permissions checklist** section in Settings that shows each one as green or red and has a button to fix each red one.

## 6. Core concepts

- **Trip:** the thing being monitored. Fields: `destination` (lat, lon, label), `radiusMeters`, `mode`, `route` (polyline, optional), `startedAt`, `state`.
- **Mode:**
  - `CAB`: arrival alarm + wrong-route alarm.
  - `BUS_TRAIN`: arrival alarm only (route checking is off).
- **Trip state:** `IDLE`, `ACTIVE`, `ARRIVAL_ALARMING`, `ROUTE_ALARMING`, `FINISHED`.
- Only one trip can be active at a time.

### How "automatic" works on Android (important)

Android does not allow an app to launch itself just because GPS was switched on. So the trip must be **armed with one tap**. To keep that effort tiny, the app provides:

1. **Share-to-app:** the app registers an intent filter so the owner can share a place from Google Maps (or the Uber and Rapido destination screens, if they offer share) to Knock Me. The app extracts coordinates and opens the trip setup with the destination filled in. Support `geo:` URIs and plain text containing a Google Maps URL with coordinates (`@lat,lon` or `?q=lat,lon`). For short links (`maps.app.goo.gl`), follow the redirect once with OkHttp and parse the final URL. If parsing fails, open the setup screen and show "Could not read location, search manually".
2. **GPS watcher during a trip:** while a trip is active, a runtime-registered receiver watches for GPS being switched off (`LocationManager.MODE_CHANGED_ACTION` or `PROVIDERS_CHANGED_ACTION`). If GPS goes off, the app raises a high-priority "GPS is OFF, your alarm cannot work" alert (loud, same alarm channel).
3. **Boot restore:** if the phone reboots during an active trip, `RECEIVE_BOOT_COMPLETED` restarts the service and resumes the trip.
4. **Stretch (v2, only after everything else works):** Activity Recognition `IN_VEHICLE` triggers a notification "Travelling? Set a destination alarm" that opens the app.

## 7. Functional requirements

### 7.1 Trip setup screen
- Destination picker: search box with a Search button (Nominatim), result list, or tap and long-press on the map to drop a pin. Show the pin and the chosen label.
- Radius selector: 1 km, 2 km, 3 km (default), 5 km, and a custom value.
- Mode toggle: Cab (default) / Bus-Train.
- Optional switch: "Final alarm at 300 m" (second, shorter alarm).
- **Start Trip** button. On press:
  1. Check permissions and GPS. If anything is missing, show the checklist and stop.
  2. Get a current location fix.
  3. If the phone is already inside the radius, show a warning "You are already within X km" and let the user choose to continue or cancel. Do not ring instantly.
  4. In Cab mode, fetch the route from the current location to the destination. If this fails, show "Route unavailable, route alarm is off for this trip" and continue with the arrival alarm only.
  5. Save the trip and start the foreground service.

### 7.2 Active trip screen
- Large text: distance remaining (km), status line ("Tracking", "Route OK", "Off route", "GPS weak").
- Small map showing position, destination, route line, and the radius circle.
- **Stop Trip** button (with confirm dialog).
- Shows GPS accuracy and the current polling interval (useful for testing battery).

### 7.3 Arrival alarm (G1)
- Compute the great-circle (haversine) distance from each accepted fix to the destination.
- When `distance <= radiusMeters` for **2 consecutive fixes** (avoids a single jump), trigger the arrival alarm.
- Also register one `Geofence` (`GeofencingClient`, `GEOFENCE_TRANSITION_ENTER`, radius = chosen radius) as a backup trigger in case the location stream is delayed. Whichever fires first raises the alarm. The alarm must not ring twice.
- After the arrival alarm is dismissed, the trip becomes `FINISHED` (unless the 300 m final alarm is enabled, then keep going until that one fires or the trip is stopped).

### 7.4 Wrong-route alarm (G2), Cab mode only
- Route polyline comes from `RoutingProvider`.
- For each accepted fix, compute the **minimum distance from the point to the polyline** (point to segment distance, using an equirectangular projection around the point; good enough at this scale). Call this `offRouteMeters`.
- **Trigger** when `offRouteMeters > 500` for **3 consecutive accepted fixes**.
- **Accepted fix:** `accuracy <= 100 m`. Fixes with worse accuracy are ignored and do not reset or advance the counter. Status shows "GPS weak" if no accepted fix arrives for more than 90 seconds.
- **Secondary signal:** if the distance to the destination has increased steadily over the last 3 minutes while also `offRouteMeters > 250`, trigger too. (Catches a driver heading away.)
- On alarm, the screen offers three buttons:
  - **Re-route from here:** fetch a new route from the current position to the destination, replace the route, reset the counter. Use this when the detour was intentional.
  - **Ignore for 10 min:** silence and suppress the route alarm for 10 minutes.
  - **Stop trip.**
- After a dismiss without choosing re-route, do not ring again for 5 minutes (cooldown).
- If the route could not be fetched, this whole feature is off and the status says so.

### 7.5 Alarm behaviour (shared)
- A high-importance notification channel `alarm` with a **full-screen intent** that opens `AlarmActivity` over the lock screen (`setShowWhenLocked(true)`, `setTurnScreenOn(true)`, keep screen on).
- Sound: default alarm ringtone (`RingtoneManager.TYPE_ALARM`), looping, `AudioAttributes.USAGE_ALARM`, so it plays even in silent mode and Do Not Disturb (alarm stream). Acquire audio focus. On trigger, raise the alarm stream volume to max and restore the previous level on dismiss.
- Vibration pattern repeating.
- Ring until dismissed, up to 10 minutes. After that, stop the sound and leave a persistent notification.
- Dismiss needs a deliberate action: the large "I'M AWAKE" button (no accidental swipe).
- Distinct text so the owner knows which alarm it is: `WAKE UP - 3 km to destination` vs `WRONG ROUTE - 820 m off your route`.
- If full-screen intent is not permitted, fall back to a heads-up notification plus sound, and show a warning in Settings.

### 7.6 Other alerts (same alarm channel, shorter ring)
- GPS switched off during an active trip.
- No GPS fix for more than 5 minutes during an active trip.
- Battery at or below 10% during a trip (single heads-up, no loud alarm).

### 7.7 Settings screen
- Permissions checklist (Section 5).
- Default radius, default mode.
- Alarm: test button ("Test alarm now"), toggle for max volume, vibrate on/off.
- Route threshold (default 500 m) and consecutive count (default 3), editable for testing.
- Button to open battery optimization exemption.
- Keep it to one scrolling screen.

## 8. Battery strategy (G4)

Use `FusedLocationProviderClient.requestLocationUpdates` and re-issue the request when the distance band changes. Use `setMinUpdateIntervalMillis` and `setWaitForAccurateLocation(false)`.

| Distance to destination | BUS_TRAIN interval, priority | CAB interval, priority |
|---|---|---|
| over 30 km | 60 s, balanced | 15 s, balanced |
| 10 to 30 km | 30 s, balanced | 15 s, balanced |
| 5 to 10 km | 15 s, high accuracy | 10 s, high accuracy |
| under 5 km | 5 s, high accuracy | 5 s, high accuracy |

Cab mode keeps a tighter interval everywhere because wrong-route detection needs frequent fixes. Do not hold a partial wake lock for the whole trip, rely on the foreground service and the location callbacks. Stop all updates and the service when the trip is stopped or finished.

## 9. UI and design

- Pure black background (`#000000`), white text (`#FFFFFF`), grey (`#9E9E9E`) for secondary text, white outlined buttons. Force dark theme, no dynamic color.
- Red (`#FF3B30`) is allowed only on the alarm screen and the Stop button.
- Large, readable text. No images, no custom fonts, no animations.
- Screens: Home, Trip Setup, Active Trip, Alarm (full screen), Settings.
- **Home:** GPS status (on/off), permission status, big "New Trip" button, last trip summary, Settings link. If a trip is active, jump straight to the Active Trip screen.

## 10. Architecture and packages

Single module. Suggested layout under `app/src/main/java/<package>/`:

```
ui/            Compose screens and theme (home, setup, active, alarm, settings)
service/       TripService (foreground service), AlarmController, BootReceiver, GpsStateReceiver
location/      LocationClient wrapper, adaptive interval logic
trip/          Trip model, TripRepository (DataStore), TripEngine (state machine)
geo/           Haversine, point-to-segment, polyline distance (pure Kotlin, unit tested)
routing/       RoutingProvider, OsrmRoutingProvider
search/        NominatimClient
share/         ShareIntentParser (geo: URI and Maps URL parsing)
```

- `TripEngine` is pure logic: input = fix stream and trip, output = events (`ArrivalReached`, `OffRoute`, `GpsLost`...). No Android classes inside it, so it is easy to unit test.
- `TripService` owns the location subscription, feeds `TripEngine`, and forwards events to `AlarmController`.
- UI observes a `StateFlow<TripUiState>` exposed from the service through a small singleton holder or bound service. Keep it simple.

## 11. Data model (sketch)

```kotlin
data class Trip(
  val id: String,
  val destLat: Double, val destLon: Double, val destLabel: String,
  val radiusMeters: Int,
  val mode: Mode,                 // CAB, BUS_TRAIN
  val finalAlarmEnabled: Boolean,
  val route: List<LatLon>?,       // null if unavailable or BUS_TRAIN
  val state: TripState,
  val startedAt: Long
)
```

Persist the active trip in DataStore as JSON so it survives process death and reboot.

## 12. Edge cases to handle

- Process killed by the system: service is `START_STICKY`; on restart, reload the active trip from DataStore and resume.
- No internet during the trip: arrival alarm is unaffected (GPS only). Route alarm continues using the already downloaded route. Re-route needs internet and shows an error if offline.
- Tunnels and underground: no fixes for a while, then a fix far from the last one. Do not count stale fixes (ignore any fix with a timestamp older than 30 s). Do not trigger the route alarm on the very first fix after a gap of over 2 minutes, wait for 2 more.
- Mock locations: accept them (needed for emulator testing).
- Multiple alarms firing together: arrival alarm has priority over route alarm.
- Phone in Doze: foreground service plus the battery-optimization exemption should cover it. Document the result of the real-phone test.
- Time zone and clock changes: use elapsed realtime for intervals, not wall clock.

## 13. Testing

- **Unit tests (required):** haversine distance, point-to-segment distance, polyline distance, `TripEngine` (arrival after 2 fixes, off-route after 3 fixes, GPS-weak ignoring, cooldown, re-route reset).
- **Emulator:** use Extended Controls, Location, Routes, with a GPX or KML playback to simulate a trip. Check both alarms. Note that the emulator cannot judge battery.
- **Real phone, short trip (required before trusting it):** lock the screen, put the phone in a pocket, ride a short distance, and confirm (a) the arrival alarm rings at the radius, (b) a deliberate detour rings the route alarm, (c) the alarm rings with the phone on silent. Record results in `docs/PROGRESS.md`.
- Add a **Simulate** switch in Settings (debug builds only) that replays a hard-coded set of fixes so the logic can be tested without moving.

## 14. Milestones (build in this order)

- [ ] **M1: Project skeleton.** Compose app, black and white theme, navigation between 5 empty screens, `.gitignore` with `docs/`, `docs/MEMORY.md` created.
- [ ] **M2: Permissions flow.** Permissions checklist with fix buttons, GPS status on Home.
- [ ] **M3: Geo and trip engine.** `geo/` functions and `TripEngine` with unit tests passing.
- [ ] **M4: Foreground service and location.** Service with notification, adaptive location updates, live distance remaining on the Active Trip screen.
- [ ] **M5: Arrival alarm.** `AlarmController`, `AlarmActivity` over lock screen, looping alarm sound, dismiss, test alarm button. Geofence backup.
- [ ] **M6: Destination picker.** osmdroid map, Nominatim search, pin drop, trip setup screen, start and stop trip, persistence.
- [ ] **M7: Routing and wrong-route alarm.** OSRM provider, route on the map, off-route detection, Re-route, Ignore, cooldown.
- [ ] **M8: Extra alerts and resilience.** GPS-off alert, no-fix alert, boot restore, low battery alert.
- [ ] **M9: Share-to-app.** Intent filter and `ShareIntentParser` with tests.
- [ ] **M10: Real phone test and polish.** Battery exemption helper, settings screen, field test results written to `docs/PROGRESS.md`.

## 15. Definition of done (v1)

1. With the screen locked and the phone silent, the arrival alarm rings within the chosen radius of the destination.
2. In Cab mode, leaving the planned route by more than 500 m for 3 readings in a row rings a distinct wrong-route alarm, and Re-route works.
3. Switching GPS off during a trip rings an alert.
4. Active trip survives app swipe-away and phone reboot.
5. A one-hour trip uses a modest amount of battery (target: under 8 to 10 percent on a typical phone). Record the actual number.
6. Unit tests pass. `docs/` is git-ignored and contains the memory files.

## 16. Open questions (answer later, not blockers)

- Real-world accuracy of OSRM routes against what Uber and Rapido drivers actually take (expect some false alarms, tune the 500 m and the 3-reading rule).
- Whether to add a "learn" mode that remembers frequent destinations (home, office, bus stand).
- Whether to add a v2 iPhone version (would be a separate native Swift project).

---

## 17. v1.1 changes ("Knock Me - update 1", milestones N1-N8)

- **N1 — Rename:** display name, notification titles, alarm screen and Settings header, and all
  docs/PRD text now say "Knock Me". Package id (`com.joyel.tripguard`) and internal class/style
  names were kept as-is (no functional reason to rename them).
- **N2 — Reliable current location:** `LocationRepository` (`location/LocationRepository.kt`)
  replaces the old "ask once, fail silently" lookup. It races `lastLocation` (if under 2 minutes
  old) against `getCurrentLocation` (15 s timeout), then falls back to `requestLocationUpdates`
  until an accurate-enough fix arrives, and falls back again to the platform `LocationManager` if
  Play services is unavailable. Exposes `LocationState` (NoPermission/GpsOff/Searching/Ready) that
  Home and Trip Setup observe live, with a calm "Turn on GPS" card using `SettingsClient`'s
  resolution dialog, and Nominatim reverse-geocoding (cached, throttled) for a readable place name.
- **N3 — Onboarding:** first-run, one-permission-per-step flow (`ui/onboarding/`), remembers
  progress, resumes at the first missing step, never shows red/error wording. Missing a critical
  permission when starting a trip opens this flow instead of an error.
- **N4 — No error-style UI:** audited; all network/DB calls were already wrapped safely
  (`runCatching`), and on-screen messages use calm wording ("Route not available, you will still
  get the arrival alarm", "Nothing copied yet", etc.) with no red/error styling outside the alarm
  screen.
- **N5 — Trip Setup upgrade:** destination defaults to current location; added Use current
  location, Paste (parses coordinates/Maps links via the existing `ShareIntentParser`, otherwise
  searches the clipboard text), and inline Saved places / Recent trips pickers.
- **N6 — Local trip history:** Room (with KSP) added — `history/` package — `TripHistory`,
  `RouteAlarmEvent`, `SavedPlace` (+ the N8 notification-log table in the same DB). The active trip
  stays in DataStore as before. `TripHistoryLogic` is pure Kotlin and unit tested. History screen
  (list + detail with repeat/save-as-place/delete), orphaned trips are marked INTERRUPTED at
  startup, Home shows the 3 most recent trips with a "See all" link.
- **N7 — Saved places:** list/rename/delete screen; Home shows them as chips that jump straight
  into Trip Setup with that destination.
- **N8 — Ride notification log (experiment):** `NotificationListenerService`, off unless the user
  grants notification access. Captures title/text/bigText/subText/category/time from Uber, Rapido
  and Google Maps notifications (or all apps, with an explicit "testing" switch) into a local-only
  Room table. A Settings screen shows status and a viewer with copy/clear. Nothing automatic is
  built on top of this yet — see `docs/MEMORY.md` for the plan.

---

## 18. v1.2 changes ("Knock Me - update 2")

- **Stop alarm fix:** Centralized alarm dismissal and complete trip termination in `AlarmController`, `AlarmActivity`, and `TripService`. Notification action buttons ("I'm Awake", "Stop Alarm", "Stop Trip") and broadcast-based activity finishing ensure alarms never get stuck and never restart after being stopped. Unit test added around `TripEngine` confirming stopped state produces zero alarms.
- **Trip Setup screen redesign:** Black background, white text, plain design. Vertical scroll with a fixed-height (~240dp) map card. Top card containing From and To fields with rich trailing action icons (My location, Pick on map, Saved places, Search, Paste), swap button, and max-5-row search dropdown. Below card with horizontal scroll chips for Saved places and Recent trips. Map picker for point selection. Below map: alarm radius chips, mode toggle, and final alarm switch. Pinned Start Trip button at bottom, disabled until To is set. Fixed map height on Home and Active Trip screens to prevent overlap.
- **Saved places & recent trips:** Room-backed trip history and saved places powering recent trip chips and saved place chips on Trip Setup and Home. Visible History button on Home observing Room Flow.
- **Memory file mirroring & export/import:** Automatic debounced JSON mirroring of Room data to `Documents/Knock Me Memory/knock_me_memory.json` via MediaStore (Android 10+) or legacy external storage. Settings integration featuring file path display, Export now, Import from file (SAF picker supporting re-importing trips and saved places after reinstalling), and clear history.

