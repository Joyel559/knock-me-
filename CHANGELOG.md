# Changelog

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
