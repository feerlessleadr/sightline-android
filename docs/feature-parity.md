# Feature parity with Sightline for Blue Iris (web app)

Source of truth: the web app's `README.md` (Features, Requirements, Everyday comfort, How it works), its `CHANGELOG.md` through v0.16.0 (2026-11-02), and `www/sightline.htm` (screens, Settings panel, colours, polling and stream behaviour). Reviewed in full on 2026-11-24. Behaviour is the spec; no code is copied (GPL, see DECISIONS.md).

Status: **Done**, **Partial**, **Planned (Mn)**, **N/A** (with the reason). "Android" means the owner's choice or a platform fact, recorded in DECISIONS.md.

## Milestones
- **M1 (done):** sign-in, camera list.
- **M2 (in progress):** live view: group live-video grid, groups, tile sizes, reorder, big tiles, snapshots mode, activity borders and AI labels, theme, network banner.
- **M3:** player: live and recorded video, timeline, controls, PTZ, quality, zoom, motion boxes, group view, download, flag/protect.
- **M4:** alerts tab: list, filters (manual and auto), ranges, preview, download.
- **M5:** settings and server: Settings screen, server status, system log, activity overview, pop-ups on detection, About.
- **M6:** release hardening: CI, CodeQL, Dependabot, signing, tablets and landscape, accessibility, diagnostics.

## Look and feel (theme, layout)
| Web feature | Status |
|---|---|
| Dark theme only. Colours: background `#0b0d10`, surface `#14181d` / `#1c2229`, line `#262e38`, text `#e8edf2`, muted `#8b97a5`, faint `#5d6875`, accent `#4cc2ff`, live red `#ff4d4d`; alert colours person `#ffb547`, vehicle `#5aa9ff`, animal `#7bd88f`, delivery `#c792ea`, motion `#9aa5b1` | Planned (M2): replace the current Material theme with these tokens |
| Header: Sightline logo (blue ring, white centre), "Sightline for Blue Iris" tag above the server name, full-screen, refresh and menu buttons | Planned (M2) |
| Bottom tab bar: Live, Alerts | Planned (M2 bar, M4 Alerts) |
| Bottom sheets for every popup, system Back closes popups then the player | Planned (M2, M3) |
| Sign-in card with logo, username, password, "Stay signed in" checkbox | Partial: sign-in screen exists, restyle in M2; "Stay signed in" is our fingerprint/PIN "Remember me" (Android) |
| Colour legend sheet | Planned (M5) |
| Add to home screen / install files in `www` / uninstall | N/A: native app |
| Keyboard shortcuts (space, `,` `.`, arrows, `+` `-` `0`, `F`, Esc) | N/A on phones; maybe for hardware keyboards later (M6) |

## Live view
| Web feature | Status |
|---|---|
| Live video in every tile from one stream of the whole group, each tile cutting out its camera's rectangle | Planned (M2) |
| Snapshot mode (Settings: "Live video in camera tiles" off): stills every 1.5 s, 0.6 s with motion boxes | Partial: stills at 2 s exist; becomes the setting and the fallback in M2 |
| Falls back to snapshots for a group with no layout, a camera not in the group picture, a stream that fails (10 s without a picture, retry after 15 s x failures, max 5 min), or an undecodable codec | Planned (M2) |
| Stream only while the Live tab is visible and the player is closed; one connection per group; restarts on group or size change | Planned (M2) |
| Group stream size from grid width x pixel ratio x 1.25, tiers up to 1440p (4 Mbps), never above the group's own size | Planned (M2) |
| Wait for a key frame before showing | Planned (M2) |
| Layout from the stream's `X-CamList` / `X-RecList` headers when present, else `camlist` rects | Planned (M2, header layout verified on the first run) |
| Hidden and disabled cameras left out and the grid re-laid out; setting "Show hidden cameras" (disabled never shown) | Partial: filtering done; setting in M2/M5 |
| Auto-fit grid so the whole group fits (2+2, not 3+1); manual Large / Medium / Small, remembered per group | Planned (M2) |
| Groups as buttons; choose which appear (hidden when only one) | Partial: chips exist; chooser in M5 |
| Drag to reorder cameras, separate order per group | Planned (M2) |
| Per-camera tile size Normal / Large 2x2 / Wide 2x1 / Tall 1x2 by press-and-hold, saved per group; Large and Wide are full width on phones | Planned (M2) |
| Up to 3 big on-screen tiles get their own camera stream (720p to 1080p tier) | Planned (M2) |
| Settings "Reset layout for this view" (order and sizes) | Planned (M2/M5) |
| Status polling every 2 s (motion / trigger), new alerts every 4 s, keepalive 40 s | Planned (M2) |
| Highlight border: white = motion, red = trigger, colour by AI result (person, vehicle, animal, delivery); setting "Highlight active cameras" | Planned (M2) |
| AI labels on tiles, e.g. "Dog 88%"; setting "AI labels on cameras"; ONVIF event names read as Person, Vehicle, Animal, Face, Visitor, Intrusion, Line crossed, Alarm input | Planned (M2 labels, mapping shared with M4) |
| Camera status dot: online green, triggered red, offline grey; offline tile dark | Partial: dots exist; colours switch to the web's in M2 |
| Optional motion boxes on tiles (frame comparison), sensitivity 1 to 10 | Planned (M3 player, M2 tiles) |
| Pop up cameras on detection (People / Vehicles / Animals / custom labels, 20 s, pauses on touch, only cameras in the shown group, one pop-up per alert, not in the player or Alerts tab) | Planned (M5) |
| Full screen for the whole grid | Planned (M2) |
| "Reconnecting" banner on a network blip instead of the login screen; offline card with Try now / Sign in again | Planned (M2) |

## Player
| Web feature | Status |
|---|---|
| Live video of one camera with audio, and volume slider / mute | Partial: video first version done (black screen being fixed); audio and volume M3 |
| Recorded video from `/time/` and `/file/clips/`, speed up to 16x | Planned (M3) |
| Scrubbable timeline with colour-coded alert marks, zoom 30 s to 7 d (pinch), buttons 2m 15m 1h 6h 24h 48h, midnight day labels, "now" stays at the right edge live | Planned (M3) |
| Go to date and time: quick choices (Last hour, This morning, Last night, Yesterday, This time yesterday, This time last week) and a calendar with from/to times; days with no recordings greyed, days with alerts dotted | Planned (M3) |
| Previous / next alert, +-10 s, play/pause, speed, one-tap LIVE | Planned (M3) |
| Frame step back/forward | Planned (M3, on-screen buttons; the web uses `,` `.`) |
| Swipe between cameras (and prev/next buttons) | Planned (M3) |
| Watch all cameras at once (group view), live and in recordings, names on pictures, tap a camera to jump to it at that moment, press again to return; hidden/disabled cameras left out; layout rules for recordings | Planned (M3) |
| Tap video: pause/resume a recording, close a live camera back to the grid; double tap fit/fill; floating controls that fade; full screen | Planned (M3) |
| Digital zoom to 8x, pinch and pan, sharper stream while zoomed (Auto quality) | Planned (M3) |
| Quality picker Auto, 480p to 4K with data rates and live rx rate; sizes and bitrates from the tier table | Partial: picker and tiers done (player button and Settings); live receive rate M3 |
| PTZ pad (8 directions, zoom), Home (or preset named Home), preset list, stop on release/leave/30 s/slide-off, retry 30 s; only for PTZ cameras with permission | Planned (M3) |
| Motion boxes in the player with sensitivity | Planned (M3) |
| Rewind without Blue Iris's timeline (older 5.x): clip-list fallback, "Rewinding needs a newer Blue Iris" | Planned (M3) |
| Alerts play from their recording file, carry on from the timeline outside it | Planned (M3) |
| Restart at new size if a recording changes picture size | Planned (M3) |
| Keeps the screen awake while the player is open (wake lock, found in the source only) | Planned (M3) |
| Short haptic buzz on long-press actions (found in the source only) | Planned (M2) |
| Troubleshooting switches `?grpdebug=1` (outline each camera on the group picture) and `?cliprewind=0/1` (force clip-list rewinding) | Planned (M6): hidden developer options |
| Flag / protect an alert (`update` command), flags shown, "Flagged" filter; clear message without permission | Planned (M3 player, M4 list) |
| Download alert or any range as MP4 (export): re-encode H.264 (default) or original, encoder profile 0 to 3, overlay, audio, choose start/end by draggable handles or set-at-playhead, up to 2 h, size estimate, progress, cancel, readable file name, permission message | Planned (M3 range UI, M4 alert entry) |
| Link to open full Blue Iris UI (UI3) | Planned (M5): opens in the browser |

## Alerts
| Web feature | Status |
|---|---|
| Alert list with thumbnails, labels, camera, time; tap to play from the moment | Planned (M4) |
| Filter buttons: All, People, Vehicles, Animals, Flagged, and any custom label; capitalisation and plurals ignored; People / Vehicles use the AI flag or the label | Planned (M4) |
| Combine filters (long-press adds), All clears | Planned (M4) |
| Choose mode (hand-picked) vs Auto mode (always-show pins, then top labels from a 3-week scan, fit-to-screen up to 5 or 1 to 8); counts refreshed every few hours; new users start on Choose with All, People, Vehicles, Animals; one-time tip | Planned (M4) |
| Labels picker from real data (3-week scan with counts) plus typed labels | Planned (M4) |
| Date and time ranges: last hour, today, yesterday, last night (8 PM to 6 AM), custom window | Planned (M4) |
| Filter by camera or group | Planned (M4) |
| Flag/protect icons in the list and long-press menu | Planned (M4) |
| Retry once on "could not load alerts" with Blue Iris's reason; scan sends one request at a time | Planned (M4) |
| Preview panel (flip-book of 8 frames, Smooth mode) | N/A on phones (the web app also hides it there); consider for tablets in M6 |

## Settings, server and account
| Web feature | Status |
|---|---|
| Settings screen started (Live video toggle, Video quality, Lock the app, Forget saved sign-in, Sign out). Web sheet: Live view (Highlight, AI labels, Live video in tiles, Pop up on detection, Show hidden cameras, Reset layout), Interface (Groups on Live screen, Alert filters, Motion box sensitivity, Alert preview panel, Colour legend), Server (Server status, System log, Activity), Account (Stay signed in, UI3 link, Sign out, About) | Planned (M5; individual items arrive with their milestones). Settings are per device, stored locally, never in the repo |
| Server status: CPU (and GPU), memory, recordings vs allowed space, total fps and bitrate, uptime, web connections, warnings, profile; refreshed every 2 s | Planned (M5) |
| Disk usage per disk (recordings, allocated free, other, free) with over-allocation note | Planned (M5) |
| Per-camera frame rate, bitrate, "footage kept" (long timeline request, at most every 10 min) | Planned (M5) |
| System log: newest first, level icons, repeat counts, filters (All / Warnings & errors / Info), search; clear message for non-admin users | Planned (M5) |
| Activity overview: alerts per day for 7/14/30 days stacked by type, camera x hour heat map, People/Vehicles/Animals filters, tap a camera, 4 requests at a time, kept 10 min | Planned (M5) |
| Stay signed in through Blue Iris session timeouts | Android: fingerprint/PIN unlock with saved password (done); auto re-sign-in after a session timeout while the app is open (planned M2: the password stays in memory only after an unlock) |
| About: Sightline and Blue Iris versions, links (project, releases, issues), Copy diagnostics (no names, addresses or passwords), licence note, credits | Planned (M5/M6) |

## Not in the web app either (so not planned)
Push notifications, two-way audio, saving PTZ presets, focus/IR controls, changing recording or AI settings (view-only).

## Our additions (Android only)
HTTPS-only enforcement, fingerprint/PIN unlock with encrypted saved password, no cleartext, no redirects, size-capped replies.

