# Blue Iris API notes

Facts the app needs, written in our own words from observing how the web Sightline behaves (reference only, no code copied). **Facts are unverified unless marked `VERIFIED`.** Verified 2026-11-24 with `tools/Sample-BlueIris.ps1` against Blue Iris 6.1.6.1 (non-admin user, over HTTPS through a reverse proxy). Tested upstream on 5.8.1.5. Real samples hold camera names and the server's location, so they stay outside the repo; tests use invented fixtures.

## Transport
- Base URL: the Blue Iris web server root, over HTTPS only in this app (see DECISIONS.md).
- JSON API: `POST {base}/json` with a JSON body, `Content-Type: text/plain`. Every call has a `cmd`. Responses: `{"result":"success"|"fail", "data":{...}, "session":"..."}`. Failure reason is in `data.reason`.
- Every call except `login` carries `"session": "<token>"` in the body.
- Pictures, streams and clips are plain GETs under `/image/`, `/thumbs/`, `/video/`, `/file/clips/`, `/time/`, `/clips/`. The web app passes `session=<token>` in the query string and also sets a `session` cookie.
  - `VERIFIED`: a `session` cookie alone works for `GET /image/{camera}?w=320&q=50` (HTTP 200, `image/jpeg`). So the app can use an OkHttp cookie jar and keep the token out of URLs. Still to check the same way for `/video/`, `/thumbs/`, `/file/clips/` and `/time/`.

## Login (challenge/response)
1. `{"cmd":"login"}` returns a `session` token. If `result` is already `success`, no authentication is required.
2. Compute `response = MD5(user + ":" + session + ":" + password)`, lowercase hex.
3. `{"cmd":"login","session":<token>,"response":<response>}`. Success returns the same session plus `data` with `system name` and `version`.
4. Session expiry: a failed call with a `data.reason` mentioning the session, or `missing response`, means log in again.
5. Check an old session by sending `{"cmd":"login","session":<old>}`. Success means still valid.
6. Keepalive: the web app calls `status` every 40 s while visible.
7. `{"cmd":"logout","session":...}` ends it.

The password is never sent. MD5 here is Blue Iris's protocol, not our choice.

## Commands seen
| cmd | Purpose | Key parameters |
|---|---|---|
| `login`, `logout` | sign in/out | see above |
| `camlist` | cameras and groups | fields used upstream: `optionValue` (camera short name, also the id used in URLs), `isOnline`, `isNoSignal`, `isEnabled`, `isTriggered`, `isAlerting`, `ptz`, `webcast` |
| `status` | server status: CPU, memory, disk, profile, schedule, signal, frame rate | none |
| `alertlist` | alerts | `camera` (`index` = all), `startdate`, `enddate` (Unix seconds), `view: "alerts"` |
| `cliplist` | recordings | `camera`, `startdate`, `enddate`, `view: "all"` |
| `timeline` | recording and alert spans for the scrub bar | `camera`, `startdate`, `enddate`, `msecpp` (milliseconds per pixel) |
| `ptz` | move, zoom, home, presets | camera plus movement fields (see sampling) |
| `update` | flag or protect an alert | `path: "@<id>"`, `flags` |
| `export` | prepare an MP4 download, poll the queue, delete a job | `path`, `format`, `profile`, `audio`, `reencode`, `overlay`, `substream`; `{cmd:"export"}` alone lists jobs |
| `log` | system log | none |

## Pictures and streams
- Still: `GET /image/{camera}?w=..&q=..` (live), `GET /time/{camera}?jpeg&isolate&pos=<ms>` (recorded), `GET /thumbs/@{recordId}` (alert thumbnail).
- Live video: `GET /video/{camera|group}/2.0?audio=0|1&stream=0&w=..&h=..&q=..&kbps=..&extend=2`. A group id gives the combined picture.
- Recorded video: `GET /file/clips/{clipPath}?time=<offset>&speed=<percent>&audio=..&w=..&h=..&q=..&kbps=..&extend=2`, or `GET /time/{camera}?pos=<ms>&speed=<percent>&opaque=..` for the timeline.
- Download: `GET /clips/{exportedPath}` after an `export` job completes.

## Video stream container
The `/video/.../2.0` response is a Blue Iris container, not MP4:
1. Six bytes: ASCII `blue` plus a header length byte at offset 5; then that many header bytes. At offset 16..19 of the header, ASCII `H265` means the stream is H.265, otherwise H.264.
2. Then repeated blocks. Each starts with five bytes: ASCII `Blue` (capital B) and a type byte.
   - type 0, video: 18-byte header (big-endian; bytes 2..5 = a raw-size value, bytes 14..17 = payload length), then the payload, one H.264 access unit.
   - type 1, audio: 4-byte big-endian length, then payload.
   - type 2, status: one length byte, payload size is that value minus 6.
   - type 4: end of stream.
3. The web app feeds the video payloads straight to a JavaScript muxer, so payloads appear to be Annex-B H.264 (`UNVERIFIED`; check for start codes on a real capture).

This is why MediaCodec is the leading decoder choice: parse these blocks, feed access units to the codec. A Media3 extractor would need the same parser.

## Timing the web app uses (copy the behaviour)
- Camera and trigger status: every 2 s while the live grid is open.
- New alerts: every 4 s.
- Keepalive: 40 s.
- Request timeout: 15 s. A network failure shows a banner, not the login screen.
- Alert filter labels come from scanning 3 weeks of alerts.

## Verified response shapes (2026-11-24, Blue Iris 6.1.6.1)
- **Login:** `VERIFIED` the MD5 challenge/response above works. `data` has `system name`, `version`, `user`, `admin` (bool), permission flags `ptz`, `audio`, `clips`, `clipcreate`, `lan`, limits (`streamlimit`, `daylimit`, `sessionlimit`, `timelimits`), and `streams` (list of stream profile strings), `profiles`, `schedules`. It also contains the server's latitude and longitude: never log or store it.
- **`camlist`:** `data` is an array of 14 entries in the sample. Three kinds:
  - All-cameras group `optionValue:"index"`: `group` (array of camera short names), `rects` (one `[left, top, right, bottom]` per camera, in the composite picture of size `width` x `height`, grid `xsize` x `ysize`), `newalerts`, `lastalert`, `alertutc`.
  - Cycle entry `optionValue:"@index"` (skip in the UI).
  - Other groups (for example "Main"): same shape as the all-cameras group.
  - Camera: `optionDisplay` (name), `optionValue` (id used in URLs), `isEnabled`, `hidden`, `isOnline`, `isNoSignal`, `isMotion`, `isTriggered`, `isAlerting`, `isRecording`, `ptz`, `audio`, `webcast`, `FPS`, `BPS`, `width`, `height`, `nAlerts`, `nTriggers`, `nClips`, `newalerts`, `lastalert`, `alertutc`, `color`, `profile`, `type`. Cameras have no `group` field.
- **`status`:** `data` has `signal`, `cxns`, `cpu`, `gpu`, `ram`, `bits`, `mem`, `memphys`, `memload`, `profile`, `lock`, `schedule`, `uptime` (`D:HH:MM:SS` string), `clips` (summary string), `time`, `warnings`, `alerts`, `tzone`, and arrays `folders` (`name`, `clips`, `age`, `used`, `allocated` in MiB) and `disks` (`disk`, `folders`, `allocated`, `used`, `free`, `total`, `error`). Several folder entries have an empty name and no data; hide them.
- **`alertlist`:** `data` is an array (214 in the last 24 h in the sample). Each item: `camera` (id), `path` (alert record id like `@123.bvr`, used for thumbs and flag), `clip` (clip file id), `file` (jpg name), `memo` (AI label text, empty when motion only), `date` (Unix seconds), `offset` (ms into the clip file), `msec` (clip length), `triggeroffset`, `flags` (bitfield for flagged/protected and other state; meaning still unmapped), `res`, `zones`, `color`, `filesize` (display string like `20 sec (391KB)`), `newalerts`.
- **AI labels:** `memo` looks like `person:90%` or `vehicle:94%`, so label is the text before `:` and confidence the number after. Empty memo is a plain motion alert.
- **Permissions:** `VERIFIED` the non-admin `test` user gets `{"result":"fail","data":{"reason":"Access denied"}}` for `log`. The app must show that message for the system log, not an error screen.

## Verified 2026-11-24 (Blue Iris 6.1.6.1, owner-run probe)
- A `session` cookie alone works for `/thumbs/{alert path}` (HTTP 200, `image/jpeg`) and `/video/{camera}/2.0` (HTTP 200, `video/mpeg`).
- Live stream starts with ASCII `blue`, then bytes `01 28 28 00 00 00`, then width and height as little-endian 32-bit values (1920 x 1080 in the sample), `01 00 20 00`, the ASCII codec name `H264`, then typed blocks. The first 64 KB held three `00 00 00 01` start codes, so H.264 payloads are Annex-B. (The header layout beyond width, height and codec name is inferred from one sample.)

## Still to verify
1. Cookie-only session for `/file/clips/` and `/time/`.
2. What an `H265` stream looks like (cameras set to H.265).
3. Exact container block framing on a longer capture (an in-app parser test will show this).
3. What each bit in `alertlist.flags` means (flagged, protected, and the rest).
4. `timeline`, `cliplist`, `ptz`, `update`, `export` response shapes.
5. Which of those the non-admin user may call (`ptz`, `clips` and `clipcreate` flags are true for `test`).
