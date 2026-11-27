# Feature parity with Sightline for Blue Iris (web app)

Source of truth: the web app's `README.md` (Features, Requirements, Everyday comfort, How it works), its `CHANGELOG.md` through v0.16.0 (2026-11-02), and `www/sightline.htm` (screens, Settings panel, colours, polling and stream behaviour). Reviewed in full on 2026-11-24. Behaviour is the spec; no code is copied (GPL, see DECISIONS.md).

Status: **Done**, **Partial**, **Planned (Mn)**, **N/A** (with the reason). "Android" means the owner's choice or a platform fact, recorded in DECISIONS.md.

## Milestones
- **M1 (done):** sign-in, camera list.
- **M2 (built, awaiting phone check):** live view: group live-video grid, groups, tile sizes, reorder, big tiles, snapshots mode, activity borders and AI labels, theme, network banner.
- **M3 (stage 1 built: recorded playback, timeline, controls, go to time):** player: live and recorded video, timeline, controls, quality, zoom, motion boxes, group view, download, flag/protect.
- **M4:** alerts tab: list, filters (manual and auto), ranges, preview, download.
- **M5:** settings and server: Settings screen, server status, system log, activity overview, pop-ups on detection, About.
- **M6:** release hardening: CI, CodeQL, Dependabot, signing, tablets and landscape, accessibility, diagnostics.

## Look and feel (theme, layout)
| Web feature | Status |
|---|---|
| Dark theme only. Colours: background `#0b0d10`, surface `#14181d` / `#1c2229`, line `#262e38`, text `#e8edf2`, muted `#8b97a5`, faint `#5d6875`, accent `#4cc2ff`, live red `#ff4d4d`; alert colours person `#ffb547`, vehicle `#5aa9ff`, animal `#7bd88f`, delivery `#c792ea`, motion `#9aa5b1` | Done (M2 round 2) |
| Header: Sightline logo (blue ring, white centre), "Sightline for Blue Iris" tag above the server name, full-screen and menu buttons (refresh is pull-down on the camera grid instead of a button) | Done (M2 round 2, changed 2026-11-25) |
| Bottom tab bar: Live, Alerts | Done: bar built; the Alerts tab is a placeholder until M4 |
| Bottom sheets for every popup, system Back closes popups then the player | Done for choice popups (M2 round 2); player popups in M3 |
| Sign-in card with logo, username, password, "Stay signed in" checkbox | Done: restyled card (M2 round 2); "Stay signed in" is our fingerprint/PIN "Remember me" (Android) |
| Colour legend sheet | Planned (M5) |
| Add to home screen / install files in `www` / uninstall | N/A: native app |
| Keyboard shortcuts (space, `,` `.`, arrows, `+` `-` `0`, `F`, Esc) | N/A on phones; maybe for hardware keyboards later (M6) |

## Live view
| Web feature | Status |
|---|---|
| Live video in every tile from one stream of the whole group, each tile cutting out its camera's rectangle | Done |
| Snapshot mode (Settings: "Live video in camera tiles" off): stills every 1.5 s, 0.6 s with motion boxes | Done |
| Falls back to snapshots for a group with no layout, a camera not in the group picture, a stream that fails (10 s without a picture, retry after 15 s x failures, max 5 min), or an undecodable codec | Done |
| Stream only while the Live tab is visible and the player is closed; one connection per group; restarts on group or size change | Done |
| Group stream size from grid width x pixel ratio x 1.25, tiers up to 1440p (4 Mbps), never above the group's own size | Done |
| Wait for a key frame before showing | Done |
| Layout from the stream's `X-CamList` / `X-RecList` headers when present, else `camlist` rects | Done |
| Hidden and disabled cameras left out and the grid re-laid out; setting "Show hidden cameras" (disabled never shown) | Partial: filtering done; setting in M2/M5 |
| Auto-fit grid so the whole group fits (2+2, not 3+1); manual Large / Medium / Small, remembered per group | Done |
| Groups as buttons; choose which appear (hidden when only one) | Partial: chips exist; chooser in M5 |
| Drag to reorder cameras, separate order per group | Planned (M2) |
| Per-camera tile size Normal / Large 2x2 / Wide 2x1 / Tall 1x2 by press-and-hold, saved per group; Large and Wide are full width on phones | Done |
| Up to 3 big on-screen tiles get their own camera stream (720p to 1080p tier) | Done |
| Settings "Reset layout for this view" (order and sizes) | Done |
| Status polling every 2 s (motion / trigger), new alerts every 4 s, keepalive 40 s | Done |
| Highlight border: white = motion, red = trigger, colour by AI result (person, vehicle, animal, delivery); setting "Highlight active cameras" | Done |
| AI labels on tiles, e.g. "Dog 88%"; setting "AI labels on cameras"; ONVIF event names read as Person, Vehicle, Animal, Face, Visitor, Intrusion, Line crossed, Alarm input | Done (labels and ONVIF names; M4 reuses the mapping) |
| Camera status dot: online green, triggered red, offline grey; offline tile dark | Done |
| Optional motion boxes on tiles (frame comparison), sensitivity 1 to 10 | Done on tiles (M2 round 2); player in M3 |
| Pop up cameras on detection (People / Vehicles / Animals / custom labels, 20 s, pauses on touch, only cameras in the shown group, one pop-up per alert, not in the player or Alerts tab) | Built (M5): Settings > Pop up on detection; a card over the grid with live video, holding pauses the countdown, tap opens the camera |
| Full screen for the whole grid | Done |
| "Reconnecting" banner on a network blip instead of the login screen; offline card with Try now / Sign in again | Done |

## Player
| Web feature | Status |
|---|---|
| Live video of one camera with audio, and volume slider / mute | Done (M3: speaker button, mute, volume 0-200%, remembered) |
| Recorded video from `/time/` and `/file/clips/`, speed up to 16x | Done: `/time/` and speeds 1-16x; alerts play from their `/file/clips/` file (M3) |
| Scrubbable timeline with colour-coded alert marks, zoom 30 s to 7 d (pinch), buttons 2m 15m 1h 6h 24h 48h, midnight day labels, "now" stays at the right edge live | Done (M3 stage 1; pinch zoom, buttons, day labels, now at the right edge) |
| Go to date and time: quick choices (Last hour, This morning, Last night, Yesterday, This time yesterday, This time last week) and a calendar with from/to times; days with no recordings greyed, days with alerts dotted | Done (M3: quick choices, month calendar with recorded days, alert dots, from/to times) |
| Previous / next alert, +-10 s, play/pause, speed, one-tap LIVE | Done (M3 stage 1) |
| Frame step back/forward | Done (M3: two buttons beside play/pause) |
| Swipe between cameras (and prev/next buttons) | Done: swipe sideways (M3); the web buttons are mouse-only, so none |
| Watch all cameras at once (group view), live and in recordings, names on pictures, tap a camera to jump to it at that moment, press again to return; hidden/disabled cameras left out; layout rules for recordings | Done (M3): group button, live and recordings, names, tap to jump, back, swipe between groups, hidden cameras left out (not when a recording has no known layout) |
| Tap video: pause/resume a recording, close a live camera back to the grid; double tap fit/fill; floating controls that fade; full screen | Done: tap pauses/resumes or closes live, double tap fit/fill, floating controls that fade, full screen (M3) |
| Digital zoom to 8x, pinch and pan, sharper stream while zoomed (Auto quality) | Done (pinch, pan, double tap or badge to reset; sharper stream in Auto) |
| Quality picker Auto, 480p to 4K with data rates and live rx rate; sizes and bitrates from the tier table | Partial: picker and tiers done (player button and Settings); live receive rate M3 |
| PTZ pad (8 directions, zoom), Home (or preset named Home), preset list, stop on release/leave/30 s/slide-off, retry 30 s; only for PTZ cameras with permission | Dropped (2026-11-25): the owner has no PTZ cameras; can be added later if needed |
| Motion boxes in the player with sensitivity | Done (M3: boxes button; sensitivity in Settings) |
| Rewind without Blue Iris's timeline (older 5.x): clip-list fallback, "Rewinding needs a newer Blue Iris" | Dropped (2026-11-25): the owner will not run a Blue Iris older than 6, which has the timeline |
| Alerts play from their recording file, carry on from the timeline outside it | Done (M3; falls back to the timeline if the file cannot be opened) |
| Restart at new size if a recording changes picture size | Built (M3), needs a real size change to confirm |
| Keeps the screen awake while the player is open (wake lock, found in the source only) | Done (M3 stage 1) |
| Short haptic buzz on long-press actions (found in the source only) | Done (M2 round 2) |
| Troubleshooting switches `?grpdebug=1` (outline each camera on the group picture) and `?cliprewind=0/1` (force clip-list rewinding) | Planned (M6): hidden developer options |
| Flag / protect an alert (`update` command), flags shown, "Flagged" filter; clear message without permission | Player done (M3: chip and sheet); list icons and the Flagged filter done in the alerts tab (M4 slice 1) |
| Download alert or any range as MP4 (export): re-encode H.264 (default) or original, encoder profile 0 to 3, overlay, audio, choose start/end by draggable handles or set-at-playhead, up to 2 h, size estimate, progress, cancel, readable file name, permission message | Done (M3): whole alert, options, progress, cancel, saved to Downloads, and a range chosen on the timeline (set start/end at the playhead, shaded band, up to 2 h, size estimate); handle dragging not done (the buttons set the ends); list entry with the alerts tab (M4) |
| Link to open full Blue Iris UI (UI3) | Changed by the owner: opens the Sightline web page on the Blue Iris server (file name is a setting, default sightline.htm), not UI3 (M5 built) |

## Alerts
| Web feature | Status |
|---|---|
| Alert list with thumbnails, labels, camera, time; tap to play from the moment | Built (M4 slice 1): thumbnails, tags, camera, time, duration, flag and lock icons, day headings, loads older alerts as you scroll; tap plays it from the alert |
| Filter buttons: All, People, Vehicles, Animals, Flagged, and any custom label; capitalisation and plurals ignored; People / Vehicles use the AI flag or the label | Built for All, People, Vehicles, Animals, Flagged (M4 slice 1); custom labels via Edit (slice 2) |
| Combine filters (long-press adds), All clears | Built (M4 slice 1): long-press a button to add it to the others, All clears |
| Choose mode (hand-picked) vs Auto mode (always-show pins, then top labels from a 3-week scan, fit-to-screen up to 5 or 1 to 8); counts refreshed every few hours; new users start on Choose with All, People, Vehicles, Animals; one-time tip | Built (M4 slice 2): Edit button opens the Alert filters sheet; Fit screen = up to 5; no one-time tip; Flagged is also in the default set |
| Labels picker from real data (3-week scan with counts) plus typed labels | Built (M4 slice 2): labels seen with counts, add and remove your own |
| Date and time ranges: last hour, today, yesterday, last night (8 PM to 6 AM), custom window | Built (M4): quick ranges plus a date with From and To times; To before From runs past midnight |
| Filter by camera or group | Built (M4 slice 1): a menu of groups and cameras |
| Flag/protect icons in the list and long-press menu | Built (M4 slice 1): icons on each alert; long-press opens the Flagged and Protected switches |
| Retry once on "could not load alerts" with Blue Iris's reason; scan sends one request at a time | Built (M4 slice 1): asks once more after a pause, then shows Blue Iris's reason with Retry |
| Preview panel (flip-book of 8 frames, Smooth mode) | N/A on phones (the web app also hides it there); consider for tablets in M6 |

## Settings, server and account
| Web feature | Status |
|---|---|
| Notification links (Home Assistant clickAction opens the app at a camera, an alert or the alerts list) | Built (M4b, Android-only addition: custom scheme sightline://); not in the web app |
| Settings screen started (Live video toggle, Video quality, Lock the app, Forget saved sign-in, Sign out). Web sheet: Live view (Highlight, AI labels, Live video in tiles, Pop up on detection, Show hidden cameras, Reset layout), Interface (Groups on Live screen, Alert filters, Motion box sensitivity, Alert preview panel, Colour legend), Server (Server status, System log, Activity), Account (Stay signed in, UI3 link, Sign out, About) | Planned (M5; individual items arrive with their milestones). Settings are per device, stored locally, never in the repo |
| Server status: CPU (and GPU), memory, recordings vs allowed space, total fps and bitrate, uptime, web connections, warnings, profile; refreshed every 2 s | Built (M5): Settings > Server status; Warnings tile opens the System log |
| Disk usage per disk (recordings, allocated free, other, free) with over-allocation note | Built (M5) |
| Per-camera frame rate, bitrate, "footage kept" (long timeline request, at most every 10 min) | Frame rate and bitrate built (M5); footage kept still to do |
| System log: newest first, level icons, repeat counts, filters (All / Warnings & errors / Info), search; clear message for non-admin users | Built (M5): Settings > System log and the Warnings tile; coloured glyph dots instead of drawn icons; real lines unverified (needs an admin user) |
| Activity overview: alerts per day for 7/14/30 days stacked by type, camera x hour heat map, People/Vehicles/Animals filters, tap a camera, 4 requests at a time, kept 10 min | Built (M5): Settings > Activity; busy days of 100+ alerts are read in full |
| Stay signed in through Blue Iris session timeouts | Android: fingerprint/PIN unlock with saved password (done); auto re-sign-in after a session timeout while the app is open (done, M2 round 2: the password stays in memory only after an unlock or a Remember me sign-in) |
| About: Sightline and Blue Iris versions, links (project, releases, issues), Copy diagnostics (no names, addresses or passwords), licence note, credits | Built (M5): Settings > About; no recent-errors list yet |

## Not in the web app either (so not planned)
Push notifications, two-way audio, saving PTZ presets, focus/IR controls, changing recording or AI settings (view-only).

## Our additions (Android only)
HTTPS-only enforcement, fingerprint/PIN unlock with encrypted saved password, no cleartext, no redirects, size-capped replies.

