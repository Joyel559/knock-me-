# Changelog

## v2.0 — 2026-11-26

- Rebuilt how a shared Google Maps short link (`maps.app.goo.gl/...`) is resolved — it used to
  show "No match" because a short link carries no name and no coordinates by itself.
- New `PlaceInputResolver.resolve(text)`: one entry point for share, paste, clipboard-offer, and
  typed/pasted links in either field. Emits "Reading place…" immediately, then the resolved place
  within 1.5s typical / 4s hard timeout.
- Two short-link resolvers race in parallel: a local redirect-follow (reads the name from the
  final URL, confirmed by a real test that it never carries coordinates) and the public
  mapu.retiolus.net unshortener (usually returns exact coordinates + name in one request) — the
  first valid result wins, the loser is cancelled. A new Settings → Links switch (default ON)
  turns the online resolver off.
- 30-day Room-backed link cache so sharing the same link twice resolves instantly the second time.
- A resolve failure now shows a calm grey card ("Could not read this link.") with Try again /
  View link in Google Maps / Paste coordinates — no red text, no raw link ever shown in a field.
- Settings → Link debug (debug builds only): the last 20 resolves, which resolver won, and how
  long each took.
- Deleted the old `ShareIntentParser`/`RedirectResolver`/`ParsedDestination` entirely — see
  docs/CLEANUP.md.

## v1.8 — 2026-11-26

- Removed every in-app map (Trip Setup, Active Trip, the share confirmation card) and the
  osmdroid dependency. Active Trip is now text-only: distance, status, From, To, radius, mode.
- Added "Choose in Google Maps" for both From and To: opens Google Maps to search, Share back to
  Knock Me fills the field directly, no confirm step, no second map.
- Fixed the real-phone bug where the share confirmation card's own mini map covered its "Use
  this"/"Pick on map instead" buttons.
- Fixed clipped action-row and radius-chip labels (e.g. "Paste") by switching them to a wrapping
  FlowRow instead of a horizontal-scroll row.
- A Trip Setup draft (fields, radius, mode, final alarm) now survives the trip to Google Maps and
  back via a 30-minute-expiry DataStore-backed PendingPick.
- A share with no pending pick fills To and offers "Use as start instead".
- A clipboard-offer sheet appears only while a pick is pending, for a copied Maps link/geo
  URI/coordinates/Plus Code not already offered.
- MainActivity launchMode changed to singleTask, fixing duplicate app instances after a share.
- Added a small "View in Google Maps" button on each place card (view-only pin check).
- Added build info (version, build date, commit) shown in About and Settings, baked into the APK
  at `assets/build_info.txt`.
