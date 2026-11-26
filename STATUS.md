# STATUS

Last updated: 2026-11-25

## Current state
- Public repo feerlessleadr/sightline-android (secret scanning, push protection, private vulnerability reporting, Dependabot alerts, `main` ruleset). GPL-3.0, HTTPS-only, minSdk 30 (Android 11+). Last pushed commit: e8be092 (clip range download, download folder, pull to refresh, size-change restart).
- M1 (sign-in, camera list), M2 (live view) and M3 (player) are done and owner-verified on a phone: recorded playback and timeline, calendar, alert clip files, audio, tap/double tap/full screen/landscape, swipe between cameras, frame step, motion boxes, group view, pinch zoom, flag/protect, download an alert as MP4.
- Also done and owner-verified: download a clip with a chosen start and end, Settings > Downloads > Save downloads to (needed a newer Fragment library), pull to refresh instead of the header refresh button.
- Built, NOT yet seen on a phone: the player restarts its decoder when a recording changes picture size part-way (committed).
- **M5: pop-ups pushed (af5ce61). Server status screen built, NOT committed, not yet seen on a phone (see checklist at the end).** M4b deep links built and verified on the phone (live, alert, alerts tab, URI button), NOT committed.** Format in notifications: `deep-link://sightline://live/<camera>` etc. (connection 17 in WORKSPACE.md). Next: change the HA automations (ha session), then M5.
- Docs: `docs/feature-parity.md` (matrix), `docs/blue-iris-api.md` (API notes), `DECISIONS.md`.

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Behaviour is the spec; no code is copied.

## Next steps
1. M3 is finished (the older-Blue-Iris fallback and PTZ were dropped: the owner uses Blue Iris 6+ and has no PTZ cameras).
2. M4 alerts tab: slice 1 built (see below); slice 2 = custom labels, Auto mode and label scan, filter settings sheet, custom start/end time, colour legend. Then M4b deep links from Home Assistant notifications (then edit the HA automations in the ha project), M5 settings/server/log/activity/pop-ups/About, M6 CI/CodeQL/Dependabot/signing/tablets/accessibility.
3. Lint leftovers (warnings only): `SharedPreferences.edit` KTX suggestion, version bumps.

## Owner must do
- Picture-size change: nothing to do unless a recording from a camera that switches between its sub and main stream shows a corner of the picture or freezes at the switch; tell me the camera and time.
- Range download across a recording-file boundary is untested (set aside by the owner).
- Time range (new): time button > Pick a day and time; set Date, From, To; try To earlier than From (runs into next day).
- Alert filters (done): on the Alerts tab tap Edit at the end of the filter row. Try Auto (it scans the last 3 weeks, so give it a moment), the count chips, turning built-in buttons and labels on and off, adding a label of your own (e.g. a name Blue Iris reports), and check the row changes and a selected button that you hide stops filtering. Quit and reopen: choices should stay.
- Alerts tab (slice 1, done): alerts load newest first with a thumbnail, tags, camera, time, duration and flag/lock icons under Today/Yesterday headings, older ones loading as you scroll. Try the buttons (tap one; long-press to combine; All clears), the camera menu, the time button (last hour, today, yesterday, last night, pick a day), pull down to reload, long-press an alert for Flagged/Protected, tap an alert (the player opens at it; Back returns to the same place in the list). Tell me what looks or works wrong.
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

## Deep links test (M4b)
Install the new APK, then from a PC send a test notification from Home Assistant Developer Tools > Actions (notify service for your phone) with `data: {clickAction: "sightline://live/FrontDoor"}` (use one of your camera short names). Tap it: the app should open that camera live (after the fingerprint if locked). Then try `sightline://alerts`, and an alert link `sightline://alert/FrontDoor/@<a real alert id>.bvr` (the id is the `alert_db` from the MQTT message; on the Alerts tab the ids are not shown, so use a live alert). With the app already open on another screen, tapping should still switch to the link. If tapping does nothing, tell me what the notification app did (opened a browser? nothing?).

## Pop-ups test (M5)
Settings > Pop up on detection: use the top switch to turn pop-ups on or off (choices are kept when off); turn on People (and maybe Vehicles). Go to the Live tab and wait for a detection (or walk in front of a camera; a person alert needs to be under 30 s old). A card should pop up over the grid: coloured by what was seen, with the camera name, time, live video, a bar counting down 20 s. Check: holding your finger on it pauses the bar; tapping it opens that camera; Close closes it; a second detection restarts it; it doesn't pop up on the Alerts tab, in the player or for a camera in another group; leaving the app closes it. Also try a label of your own (e.g. a name Blue Iris reports) and "Turn all off".

## Server status test (M5)
Settings > Server > Server status. Check: tiles fill in within a couple of seconds and update every 2 s (CPU, memory, recordings %, frame rate, uptime, connections, warnings, profile), each disk shows its bar and legend, and every camera lists fps and Mbps (a disabled or offline camera says so). Compare a few numbers with Blue Iris's own console. The little dot by the title goes grey if Blue Iris can't be reached. Back returns to Settings.
