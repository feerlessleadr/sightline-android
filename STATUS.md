# STATUS

Last updated: 2026-11-25

## Current state
- Public repo feerlessleadr/sightline-android (secret scanning, push protection, private vulnerability reporting, Dependabot alerts, `main` ruleset). GPL-3.0, HTTPS-only, minSdk 30 (Android 11+). Last pushed commit: c15a207 (M2 complete). Everything under "M3 stage 1" is uncommitted.
- M1 (sign-in, camera list) and M2 (live view: web look, tile layout, status borders and AI labels, big tiles, motion boxes, reconnect) done and owner-verified on a phone, including the reorder and Large-size fixes.
- **M3 stage 1 (built, 76 unit tests pass, lint clean, NOT yet seen on a phone): recorded playback and timeline.** The old live-only screen is now `ui/PlayerScreen.kt` for live and recorded video:
  - Recorded video from Blue Iris's `/time/` stream, paced by the frames' recording times (frame clock VERIFIED on a real capture); a paused or scrubbed recording shows a still of that moment.
  - Timeline bar (`ui/TimelineBar.kt`): recorded spans, coloured alert marks, time labels, playhead; one finger scrubs, two fingers pinch (30 s to 7 days); zoom buttons 2m/15m/1h/6h/24h/48h.
  - Controls: play/pause, -10 s, +10 s, previous/next alert (7 days), speed 1-16x, LIVE, go to date and time (quick choices, or a day plus start time). Screen stays on.
  - Not in this stage: audio, alerts playing from their clip file, calendar with recorded days greyed, frame step, tap-to-pause and double tap, landscape/full screen (details in DECISIONS.md 2026-11-25).

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Matrix and milestones: `docs/feature-parity.md`. Behaviour is the spec; no code is copied.

## Next steps
1. Owner tests M3 stage 1 (list below); fix what the real server shows (timeline reply shape and `/time/` parameters come from the web source and are unverified).
2. M3 stage 2: PTZ pad (last), tap/double-tap and floating controls, full screen and landscape. Stage 3: group view. Stage 4: download as MP4, flag/protect.
3. M4 alerts tab, M5 settings/server/log/activity/pop-ups/About, M6 CI/CodeQL/Dependabot/signing/tablets/accessibility.
4. Lint leftovers (warnings only): `SharedPreferences.edit` KTX suggestion, version bumps.

## Owner must do
Install the new debug APK, open a camera, and check:
- Live still plays as before; the timeline appears under it with blue recorded stretches and coloured alert marks (or a note if the server refused the timeline: send me that note).
- Tap or drag on the timeline: the picture changes to that moment; let go and it plays from there. Tap near the right end: back to live.
- Play/pause, -10 s, +10 s, speed button (1/2/4/8/16x), LIVE button.
- Previous/next alert buttons jump to an alert and play from 2 s before it.
- Calendar (calendar button, bottom row): month grid, days with no recording greyed, dots on days with alerts, arrows change month, pick From and To times and tap Go; quick choices on top.
- Sound: speaker button in the top bar (mute, volume slider up to 200%); on a camera with sound you should hear it live and in recordings at 1x; muting is instant; the setting is remembered.
- Tap the picture: a live camera closes back to the grid, a recording pauses or resumes. Double tap: fit the whole picture or fill the screen (zoomed: back to normal). Full screen button (top bar): bars hide, controls float over the picture and fade after 3 s; tap to bring them back; Back leaves full screen. Rotate the phone to landscape: controls float; the player should keep its place.
- Layout: the picture is centred in the free space with the controls at the bottom (timeline, then 2m..48h with the calendar button, speed, LIVE, then play/pause row). Pinch the picture: it must stay visible, grow into the space above and below, pan with one finger, double tap or the badge resets; in Auto quality it gets sharper after a moment. Pinch the timeline; calendar button = go to date and time.
- If a recording will not play, the message over the picture has a Retry button and the details; send me what it says.

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
