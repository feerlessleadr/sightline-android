# STATUS

Last updated: 2026-11-25

## Current state
- Public repo feerlessleadr/sightline-android (secret scanning, push protection, private vulnerability reporting, Dependabot alerts, `main` ruleset). GPL-3.0, HTTPS-only, minSdk 30 (Android 11+). Last pushed commit: ce06287 (live grid, player, settings, lock options). Everything below "M2 round 2" is uncommitted.
- Milestone 1 (sign-in, camera list) done. Fingerprint/PIN unlock, lock after N minutes (default Always), "Never" option: done and owner-verified.
- Live video verified on the owner's phone: single camera and group grid play, start in 0.1-0.9 s (the earlier slow start was not reproduced and its cause is unknown, see DECISIONS.md 2026-11-25). Owner confirmed Never lock, lock-after minutes, quality picker and Live grid/Stills all work.
- **M2 round 2 (built, 71 unit tests pass, lint clean, NOT yet seen on a phone):**
  - Web look: dark-only palette (`ui/Theme.kt`, `object Sl`), header with logo and server name, full-screen button, refresh, menu (opens Settings), bottom tab bar Live/Alerts (Alerts is a placeholder until M4), bottom sheets for popups, restyled sign-in and lock cards.
  - Per-camera tile shape (Normal / Large 2x2 / Wide 2x1 / Tall 1x2) via press-and-hold, drag to reorder (press, hold, drag), saved per group; Settings and the sheet have "Reset layout for this view".
  - Status polling 2 s (camlist), recent alerts 4 s (alertlist, last 2 min), keepalive 40 s; highlight borders (white motion, red trigger, class colour when the AI result is fresh), AI labels on tiles (fresh 45 s, 3 min while triggered), settings switches for both.
  - Big tiles (Large/Wide/Tall, up to 3, on screen, online) get their own camera stream in a TextureView; failures fall back to the shared picture for 30 s x failures (max 5 min).
  - Motion boxes (off by default; "Boxes" button in the group row, sensitivity 1-10 in Settings), the web detector ported; samples from stills (every 0.6 s), the shared live picture (GL off-screen render) and, for big tiles, the shared picture too.
  - "Reconnecting to Blue Iris" banner (Try now, Sign in again), quiet re-sign-in after a session timeout using a password held in memory only after a fingerprint unlock or a "Remember me" sign-in (never for a sign-in the owner did not ask to remember).

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Matrix and milestones: `docs/feature-parity.md`. Behaviour is the spec; no code is copied.

## Next steps
1. Owner tests M2 round 2 on the phone (list below), then commit/push.
2. M3 player (recorded video, timeline, controls, PTZ, zoom, group view, download, flag/protect), M4 alerts tab, M5 settings/server/log/activity/pop-ups/About, M6 CI/CodeQL/Dependabot/signing/tablets/accessibility.
3. `dependabot.yml`, CodeQL and CI workflows (M6). Release signing before the first release.
4. Lint leftovers (warnings only): `SharedPreferences.edit` KTX suggestion, version bumps.

## Owner must do
Install the new debug APK and check:
- Look: dark palette, header, bottom bar, Settings opens from the menu (three dots), full-screen button hides bars (Back returns).
- Press and hold a tile: buzz, then drag to reorder; let go without moving: size sheet (Normal/Large/Wide/Tall, Reset layout). Large and Wide are full width.
- Big tiles, and any tile stretched more than 2x (all tiles at the Large size), use their own stream (up to 4): check Large is sharp now, and that dragging to reorder no longer flickers.
- Walk past a camera: border (white/red/colour) and AI label like "Person 91%" appear and fade.
- Turn "Boxes" on: boxes around movement on tiles; try sensitivity in Settings.
- Turn Wi-Fi off briefly: "Reconnecting" banner, recovers by itself.
- Alerts tab shows "not built yet" (expected).

## Open questions
- Latency and quality targets for live video on mobile data vs Wi-Fi.
- Whether to add a home-screen widget (the web app has none).
- Whether a big tile's motion boxes should come from its own stream (web) rather than the shared picture (ours, simpler, lower resolution).

## Commands (Windows, PowerShell)
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test assembleDebug lintDebug
```
`local.properties` (git-ignored) needs `sdk.dir` with escaped separators, for example `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. Gradle wrapper is 9.8.1, copied from Embidextrous.
