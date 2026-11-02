# Play Console permission declaration justifications (draft text)

## Foreground service — location (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`)

> Knock Me runs a foreground service, with an ongoing notification, only while a user-started
> trip is active. The service tracks the device's location to ring an arrival alarm when the
> user approaches their chosen destination, and (in Cab mode) to detect if the ride has gone
> significantly off the planned route. The service starts only when the user explicitly taps
> "Start Trip" from the app's foreground UI, and stops automatically when the user arrives,
> stops the trip, or a safety timeout is reached (configurable 6–72 hours, default 36).

## Background location (`ACCESS_BACKGROUND_LOCATION`) — only requested in optional "Max
## reliability" mode

> By default, Knock Me only ever requests foreground ("while in use") location, which is
> sufficient for the foreground location service to keep tracking with the screen off — the same
> mechanism used by turn-by-turn navigation apps. Background location is requested only if the
> user explicitly opts in to the optional "Max reliability" setting (Settings → Reliability),
> intended for phones whose manufacturer aggressively kills background apps. In that mode it is
> used to let a geofence (via Google Play services Geofencing API) serve as a backup arrival
> trigger if the main location-update loop is ever interrupted, and to let the app restart
> tracking automatically after a device reboot. Declining or revoking this permission simply
> turns Max reliability off; core alarm functionality is unaffected.

## Full-screen intent (`USE_FULL_SCREEN_INTENT`) — alarm-clock-style app

> Knock Me is a destination/travel alarm app. Its core purpose is to wake the screen and show a
> loud, full-screen alert — the arrival alarm or wrong-route alarm — even when the phone is
> locked, the same category of use case as alarm clock and timer apps. The permission is used
> exclusively for this purpose: it is never used for ads, promotions, or anything unrelated to
> the trip the user is actively running. On Android versions where the system can deny this
> permission, Knock Me asks for it once, with a plain explanation, immediately before the user's
> first trip (never during onboarding), and gracefully falls back to a loud heads-up notification
> plus sound if it's denied or unavailable.

## Post notifications (`POST_NOTIFICATIONS`)

> Required to show the ongoing "trip is being tracked" notification (Android's standard
> requirement for any foreground service) and to ring/display the arrival and wrong-route alarms,
> which are implemented as high-priority notifications with a full-screen intent.

## Boot completed (`RECEIVE_BOOT_COMPLETED`)

> Used only to resume an interrupted trip automatically after a device reboot, and only when the
> user has opted in to "Max reliability" mode (see above). In the default mode, Knock Me never
> acts on this broadcast — the trip instead resumes the next time the user opens the app.

## Ignore battery optimizations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)

> The system intent to request this exemption is only ever shown to the user from the optional
> "Max reliability" screen, which they must navigate to and opt into deliberately; it is never
> requested automatically or during onboarding.
