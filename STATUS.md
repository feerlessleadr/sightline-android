# STATUS

Last updated: 2026-11-27

## Current state
- Public repo feerlessleadr/sightline-android (secret scanning, push protection, private vulnerability reporting, Dependabot alerts, `main` ruleset, release-tag ruleset, immutable releases, actions pinned to commit hashes). GPL-3.0, HTTPS-only, minSdk 30 (Android 11+). Last pushed commit: ce58d5b (v0.1.3; later commits are docs only if any).
- M1 (sign-in, camera list), M2 (live view) and M3 (player) are done and owner-verified on a phone: recorded playback and timeline, calendar, alert clip files, audio, tap/double tap/full screen/landscape, swipe between cameras, frame step, motion boxes, group view, pinch zoom, flag/protect, download an alert as MP4.
- Also done and owner-verified: download a clip with a chosen start and end, Settings > Downloads > Save downloads to (needed a newer Fragment library), pull to refresh instead of the header refresh button.
- Built, NOT yet seen on a phone: the player restarts its decoder when a recording changes picture size part-way (committed).
- **v0.1.3 published (2026-11-27): header uses the lens logo, server name no longer black. Previous: v0.1.2 (lens launcher icon, library updates). Not yet tried on devices: TalkBack, tablet behaviour, the updated libraries.**
- Docs: `docs/feature-parity.md` (matrix), `docs/blue-iris-api.md` (API notes), `DECISIONS.md`.

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Behaviour is the spec; no code is copied.

## Next steps
1. M1 to M6 are all built and v0.1.3 is published (latest). Nothing is in progress and the working tree is clean.2. Very next action: owner tests on devices what has not been tried (TalkBack, tablet or wide window with the alert preview panel, Show group layout, system log with an admin user, footage kept on a large server, the updated libraries) and reports problems; fixes go into v0.1.4 (tags never move).3. Known gaps: no screen-reader way to reorder camera tiles, faint grey text is below the usual contrast target, no Smooth preview mode.4. Not done on purpose: requiring pull requests or CI on main (would block direct pushes); two secret-scanning extras would not enable (maybe a plan limit).5. Lint leftovers (warnings only): SharedPreferences.edit KTX suggestion.

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

## System log test (M5)
Settings > Server > System log. With your normal (non-admin) Blue Iris user you should see "Blue Iris didn't send its system log (Access denied)... may only show it to admin users". To see real lines, sign in with an admin Blue Iris user (or give yours admin rights), then check: newest first, coloured level dots, "x3" repeat counts, the three filter buttons with counts, search, Refresh. On Server status, tap the Warnings tile: it should open the log on "Warnings & errors".

## Activity test (M5)
Settings > Server > Activity. It reads the alert list a day at a time ("Reading 3 of 7 days"), then shows a summary line, alerts per day as stacked bars (today on the right), and a camera by hour map (brighter = more). Check: 7/14/30 days and the People/Vehicles/Animals buttons change the numbers; a repeat visit within 10 minutes is instant; tapping a camera (or All cameras) opens the Alerts tab on that camera with the chosen type. Compare a camera's count with the Alerts tab.

## About and web page test (M5)
Settings > Account. "Open Sightline on the web" should open your server's Sightline page in the browser: it uses the file name below it ("Web page file name", default sightline.htm). CHECK the real file name of the Sightline page on your Blue Iris server and change it there if different. "About Sightline for Android" shows versions and links; "Copy diagnostics" copies a few lines (paste somewhere to check there are no names or addresses).

## Colour legend test (M5)
Settings > Live view > Colour legend: four groups (Alerts, Player timeline, Cameras, Camera tiles) with a swatch and a line for each. Check the swatches look like the real thing (alert colours, the red and white tile borders, the green and red dots).

## Hidden cameras and groups test (M5)
Settings > Live view: "Show hidden cameras" (turn on: cameras you marked Hidden in Blue Iris appear on the grid and in the Alerts camera menu; off: they go again). "Groups on Live screen": switch group buttons off (at least one stays; with one left the button row disappears) and check the Live tab follows.

## Footage kept test (M5)
Settings > Server > Server status: a "Footage kept" tile (shows ... then a range like 12-30 days) and a Stored column on each camera (a number of hours or days; * means older recordings after a gap). It fills in camera by camera, so give it a minute on a big server. Compare a camera's Stored value with how far back its timeline goes in the player.

## Alerts scroll fix and M6 CI test
1. Alerts tab: tap People, Vehicles, Animals, Flagged and All: the list should start at the NEWEST alert (top) each time, not jump to the oldest of today. Also try the camera menu and time button.
2. GitHub (M6): after "commit and push" I will look at the first CI and CodeQL runs. You do on GitHub (Settings): Security > enable Dependabot alerts and security updates; do NOT also switch on CodeQL "default setup" (the workflow file does the job); optionally add a branch rule that requires the CI check before merging.

## Release build test (M6)
1. Install the shrunk release test build and use the app for a few minutes (live, player, alerts, settings, a download): `.gradlew.bat assembleRelease "-PreleaseDebugSigned=true"`, then install appbuildoutputspkeleasepp-release.apk. It installs as `com.feerlessleadr.sightline.rtest` next to the debug build (you sign in again). Tell me anything that works in debug but not here: that is R8 removing something.
2. v0.1.0 PUBLISHED (tag v0.1.0, commit 3f57737). Release key done. Install the release APK from the GitHub release page and check it works; anything R8 broke is fixed in v0.1.1 (the v0.1.0 tag is never moved).

## M6 tests (TalkBack, tablet, developer switch)
1. TalkBack (Settings > Accessibility): on the Live tab each tile should read its name and state; double-tap opens it; the actions menu has "Tile size and layout". Alerts: each alert reads as one sentence; filter buttons say selected. Player: the timeline reads its position and the actions menu has back/forward steps and Go to live.
2. Tablet or a wide window (the emulator's tablet, 840 dp or more): Alerts shows the list with a preview panel; tapping an alert (or a mouse pointing at it) plays a slideshow of stills; tap the picture or Open to open the player. Settings > Alert preview panel turns it off. Settings, Server status, Log, Activity, About and Legend stay in a centred column.
3. Settings > Developer > Show group layout: open a group in the player and check the outlines (green shown, red hidden).
