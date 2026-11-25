# STATUS

Last updated: 2026-11-25

## Current state
- Public repo feerlessleadr/sightline-android (secret scanning, push protection, private vulnerability reporting, Dependabot alerts, `main` ruleset). GPL-3.0, HTTPS-only, minSdk 30 (Android 11+). Last pushed commit: b298f0e (download an alert as MP4).
- M1 (sign-in, camera list), M2 (live view) and most of M3 are done and owner-verified on a phone: recorded playback and timeline, calendar, alert clip files, audio, tap/double tap/full screen/landscape, swipe between cameras, frame step, motion boxes, group view, pinch zoom, flag/protect, download an alert as MP4.
- **Built and owner-verified, NOT yet committed:** download a clip with a chosen start and end (cliplist finds the file), Settings > Downloads > Save downloads to (folder picker; needed a newer Fragment library, because biometric 1.1.0 pulls in 1.2.5), pull to refresh instead of the header refresh button, Download button text fix.
- **Built, NOT yet seen on a phone:** the player restarts its decoder when a recording changes picture size part-way (new sequence parameter set on a key frame) and reports the new size.
- Docs: `docs/feature-parity.md` (matrix), `docs/blue-iris-api.md` (API notes), `DECISIONS.md`.

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Behaviour is the spec; no code is copied.

## Next steps
1. Commit and push the uncommitted work above when the owner says so.
2. Rest of M3: older-Blue-Iris recording-list fallback (the owner's 6.1.6.1 has the timeline, so it cannot be tested here), PTZ pad last (the owner has no PTZ cameras).
3. M4 alerts tab (list with flag/lock icons, Flagged filter, colour legend later), M4b deep links from Home Assistant notifications (then edit the HA automations in the ha project), M5 settings/server/log/activity/pop-ups/About, M6 CI/CodeQL/Dependabot/signing/tablets/accessibility.
4. Lint leftovers (warnings only): `SharedPreferences.edit` KTX suggestion, version bumps.

## Owner must do
- Picture-size change: nothing to do unless a recording from a camera that switches between its sub and main stream shows a corner of the picture or freezes at the switch; tell me the camera and time.
- Range download across a recording-file boundary is untested (set aside by the owner).
- Everything else listed in earlier versions of this file was checked by the owner.

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
