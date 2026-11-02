# Privacy Policy — Knock Me

Knock Me does not have accounts, does not use analytics, and does not show ads.

## Location

Your location is used only on your phone, only while a trip is running, to check your distance
to the destination and your position on the planned route. It is never sent anywhere, never
stored on a server, and never shared with anyone.

If you turn on **Max reliability** in Settings, Knock Me additionally asks for background
("Allow all the time") location and permission to ignore battery optimizations. This is used the
same way — only on your phone, only to keep your trip running reliably — and is entirely optional.

## What's stored on your phone

Trip history, saved places, and settings are stored in a local database on your device, plus a
readable backup file in `Documents/Knock Me Memory/`. None of it leaves your phone unless you
choose to export or share it yourself.

## Network requests

Searching for a place, pasting a link, or asking for a route sends only the text you typed or the
coordinates involved to the keyless public map services that answer that one request:

- **Nominatim** and **Photon** (OpenStreetMap-based) for place search and reverse geocoding.
- **OSRM** for route calculation.

These are free, public, no-API-key services. No account identifier, device ID, or advertising ID
is attached to those requests. If routing or search is unavailable, Knock Me falls back to the
arrival alarm alone — it never blocks or crashes.

## Deleting your data

- **Settings → Clear all history** removes everything Knock Me has stored in its local database
  and rewrites the backup file to match (empty).
- **Uninstalling the app** removes everything else, including the local database. The backup file
  in `Documents/Knock Me Memory/` is on shared storage and may survive uninstall depending on your
  phone — delete it manually from a file manager app if you want it gone too.

## Children

Knock Me is not directed at children and does not knowingly collect data from them (it collects
no data from anyone, regardless of age, beyond what's described above).

## Changes to this policy

If this policy changes, the updated version will be included with the app update and shown in
Settings → About → Privacy policy.

## Contact

Questions about this policy: `contact@example.com` (placeholder — replace with a real contact
address before publishing).
