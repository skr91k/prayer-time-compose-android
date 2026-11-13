# أَذَان – Prayer Time (Compose)

Jetpack Compose rewrite of the legacy `../prayer-time` app, with the reminder / alarm engine of
`alarm_android` (NeverMiss). Same application id (`shakir.swalah`) and keystore, so it installs as an
update. Nothing is imported from the old app - all settings start from their defaults.

```
./gradlew installDebug        # debug, signed with the release key
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # prayer-time / reminder / countdown logic
```

## Code map (`app/src/main/java/shakir/swalah`)

- `com/azan/` – prayer-time astronomy (unchanged from the legacy app)
- `prayer/` – `PrayerCalculator` (per-date UTC offset, adjustments), `HomeTimes` (countdown, midnight / last third), Hijri date, time format
- `data/` – `SettingsStore` (device-protected, JSON), `LegacyCleanup` (removes the old app's notification channels), bundled cities
- `reminder/` – `Reminder` model (fixed time **or** prayer ± minutes, repeat rules), presets, store, sounds
- `alarm/` – scheduler (setAlarmClock + backup), controller (fire / snooze / dismiss), foreground `AlarmService`, `AlarmSound` fallbacks, receivers, reliability checks, event log
- `ui/` – Compose screens: home, reminders + editor, settings, adhan time, iqama, location, month, qibla, ringing screen

## Network

Only the app's own API (`server-code/prayer_api.py`, routes `/prayer/*`):
- servers and secret come from `api.properties` (git-ignored; copy `api.properties.example`). Servers are tried in
  order; plain HTTP is allowed for those hosts only (the network-security config is generated at build time)
- every request sends `X-Api-Key` = AES-256-ECB(unix seconds, SHA-256(secret)), base64url; the server accepts +-3 s, else 404
- `/prayer/ping` (server check), `/prayer/audio` + `/prayer/audio/{file}` (adhan sounds, downloaded only if missing / size differs),
  `/prayer/log` (daily device info)

Play Services (GPS) and the Android Geocoder (city name) do their own networking. Islamic Books / Counter open in the browser.

Event log: `adb shell run-as shakir.swalah cat /data/user_de/0/shakir.swalah/files/events.log`
