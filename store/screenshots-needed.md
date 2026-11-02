# Screenshots needed for the Play Store listing

Play requires at minimum 2 phone screenshots (JPEG/PNG, 16:9 or 9:16, each side 320–3840px).
Recommended set for Knock Me:

1. **Home screen** — shows the app's purpose at a glance (New Trip button, saved places/recent
   trips if any exist). 1080x2400 or similar tall phone aspect.
2. **Trip Setup screen** — From/To fields, map with radius circle, showing the search/paste
   features.
3. **Active Trip screen** — live distance-to-destination and tracking status, to show the core
   "watching your trip" feature.
4. **Arrival alarm screen** — the full-screen alarm UI (capture from `AlarmActivity`), shows the
   "WAKE UP" moment that's the whole point of the app.
5. **Settings / Reliability screen** — shows the permission transparency and Max reliability
   option, useful for building trust in the listing.

Optional:

6. **Feature graphic** (1024x500) — a simple black/white banner with the app name and tagline,
   matching the in-app theme.
7. **App icon** — already exists at `app/src/main/res/mipmap-*/ic_launcher.png` (512x512 version
   needed separately for the Play Store listing itself, not just the launcher).

Tablet screenshots are optional unless targeting tablets specifically; this app's UI isn't
tablet-optimized, so can be skipped for v1.6.
