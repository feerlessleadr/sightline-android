# DECISIONS

Append-only, newest at the bottom. Never rewrite history; reverse a decision with a new entry that cites the old one.

### 2026-11-24, Project registered: Sightline for Android
- **Context:** Owner wants an Android app to view Blue Iris cameras, copying the features and interface of the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris, GPL-3.0, HTML/JS, single file). Owner will publish it on GitHub under feerlessleadr.
- **Options considered:** (a) separate new project (chosen); (b) a tab inside Embidextrous. Embidextrous is an admin tool for Emby; Sightline is a camera viewer with its own UI and a different server.
- **Decision:** New standalone project, folder `C:\dev\sightline-android`. Registered in WORKSPACE.md (table, connections #16, diagram). Connects only to Blue Iris.
- **Rationale:** Different server, different users and different UI. Keeping it separate avoids coupling it to Emby plugin releases.
- **Impact:** WORKSPACE.md section 4 (table), section 5 (connection 16, diagram). No other project changes.

### 2026-11-24, Feature and interface parity target: the web Sightline
- **Context:** Owner asked for the features and interface to mirror the web app "exactly, or as exact as possible with Android". The web app's README lists its features: live tiles from one group stream, camera grid sizing, drag reorder, large/wide/tall tiles, motion and trigger borders, AI labels, popups on detection, alert filters by AI label, date ranges, activity heat map, flip-book alert preview, flag and protect alerts, alert download as MP4, scrubbable timeline with zoom from 30 s to 7 days, go-to-date calendar, playback up to 16x, digital zoom to 8x, PTZ with presets, quality picker, server status, disk usage, system log, "stay signed in".
- **Options considered:** Full parity; or a phone-first subset. Owner asked for full parity.
- **Decision:** Full parity is the target. The web README is the feature list, and behaviour is checked against the web app running on a real Blue Iris server. Android-specific differences (no hover preview on phones, Back button, home-screen widget where useful) are recorded here as they arise.
- **Rationale:** Owner's explicit request. The web app has already solved most UI questions, so the Android app copies behaviour, not code.
- **Impact:** STATUS.md feature list. Any deviation from the web app gets its own entry.

### 2026-11-24, No code copied from the GPL-3.0 web project
- **Context:** The web app is GPL-3.0. Copying its HTML/JS into an Android app would make the Android app GPL-3.0 as a derived work.
- **Options considered:** (a) reimplement behaviour from the Blue Iris API and the README (chosen); (b) fork the web code into Android WebView.
- **Decision:** Reimplement. Read the web app for behaviour and for protocol facts only, and write our own code. Do not paste from it.
- **Rationale:** Keeps licence choice open. A WebView wrapper would be a weak "Android app" and would not feel native.
- **Impact:** Anyone working on this project follows the same rule.

### 2026-11-24, Proposed stack (pending owner OK)
- **Context:** Owner asked for a suitable stack for the feature list above. Target: a native phone app that plays live and recorded H.264 video from Blue Iris, with a camera grid, timeline and alerts UI.
- **Options considered:**
  - WebView around the web app: rejected, against the no-copy rule and weak native feel.
  - Flutter or React Native: rejected, owner's other Android work is Kotlin + Compose, and video decode needs native code either way.
  - Kotlin + Jetpack Compose (Material 3) with OkHttp, kotlinx.serialization, Coil, DataStore, and MediaCodec for decode: chosen.
  - Video: Media3/ExoPlayer with a custom raw H.264 extractor is the alternative to MediaCodec. Not decided; spike both in the first milestone and pick by measured latency and code size.
- **Decision (proposed):**
  - Language: Kotlin 2.x, JVM 17. Same toolchain as Embidextrous (AGP 9.x, Gradle wrapper).
  - UI: Jetpack Compose + Material 3. Tile grid, timeline and popups are custom Compose layouts.
  - Networking: OkHttp 5, kotlinx.serialization for the Blue Iris JSON API (`login`, `camlist`, `alertlist`, `timeline`, `status`, `ptz`, `export`, `update`, `log`, `logout`).
  - Login: Blue Iris's challenge/response, not a plain password. Verify the exact hash steps against the Blue Iris API docs during the build.
  - Video: MediaCodec hardware decode of the Blue Iris `/video/` H.264 stream, rendered into a SurfaceView or TextureView. Spike against Media3 first.
  - Stills, thumbnails: Coil 3.
  - Settings and per-group layout: Jetpack DataStore.
  - "Stay signed in" credentials: encrypted with an Android Keystore key, stored in DataStore. Better than the web app's obfuscation; record this as an intentional improvement.
  - Tests: JUnit 5 and kotlin.test for logic. MockWebServer for the API. Compose UI tests for the grid and filters. Live checks against a real Blue Iris server, which this PC does not have.
  - minSdk 26, same as Embidextrous.
- **Rationale:** Same toolchain as the owner's existing Android app means fewer new tools to maintain. Native decode is needed for smooth video on phones either way.
- **Impact:** Owner must approve before the first build. Needs a Blue Iris server or the trial to test against (see STATUS.md).

### 2026-11-24, Cleartext HTTP allowed to the owner's Blue Iris only
- **Context:** Blue Iris on a LAN is usually plain HTTP. Embidextrous is HTTPS-only in release builds, a workspace rule.
- **Options considered:** (a) HTTPS only (blocks the usual LAN setup); (b) allow cleartext for user-entered hosts only, with HTTPS preferred and the Blue Iris password never placed in a URL (chosen).
- **Decision:** Allow cleartext for the one user-entered Blue Iris host, via a network security config that does not allow any other host. Passwords and session tokens never go in URLs.
- **Rationale:** The web app works over plain http too (README shows `http://…:81`). The deviation from Embidextrous is deliberate and limited to this app.
- **Impact:** Document it in the app's privacy notes. Differs from the Embidextrous rule on purpose.

### 2026-11-24, Open: app licence
- **Context:** The web app is GPL-3.0. This app is separate and no code is copied, so the licence is the owner's choice.
- **Options considered:** GPL-3.0 (matches the reference project); MIT or Apache-2.0 (more permissive).
- **Decision:** Not decided. Owner to choose before the first public release. Until then, no LICENSE file.
- **Impact:** LICENSE file and README when the repo is created.

### 2026-11-24, App licence: GPL-3.0 (closes the open licence entry above)
- **Context:** Owner reviewed the open licence question and chose GPL.
- **Options considered:** GPL-3.0, MIT, Apache-2.0.
- **Decision:** GPL-3.0 (GNU GPL version 3). `LICENSE` added to the repo.
- **Rationale:** Matches the reference web project and keeps derivatives open. The no-copy rule still stands, so the choice is not forced by the web project's licence.
- **Impact:** `LICENSE`, README. Third-party libraries must have GPL-compatible licences.

### 2026-11-24, HTTPS only (reverses the cleartext entry above)
- **Context:** Owner's Blue Iris is reached only through a Caddy reverse proxy over HTTPS. Owner wants access to the server to be HTTPS only. The earlier entry allowed cleartext HTTP to the user-entered host.
- **Options considered:** Keep cleartext as an opt-in; or HTTPS only.
- **Decision:** HTTPS only. The app refuses `http://` addresses, and the network security config sets cleartext traffic to not permitted. Redirects are not followed. This matches the Embidextrous rule. Passwords and session tokens never go in URLs.
- **Rationale:** Owner's explicit choice and the safer default. It also keeps the workspace-wide rule uniform.
- **Impact:** The "Cleartext HTTP allowed" decision above no longer applies. The sign-in screen only accepts `https://` addresses. The real server address lives only in `C:\dev\_docs\WORKSPACE.md`, never in this public repo.

### 2026-11-24, Owner approved the proposed stack and the other decisions
- **Context:** Owner reviewed the entries above.
- **Decision:** Approved: stack (Kotlin, Compose, OkHttp, MediaCodec with a Media3 spike), no code copied from the GPL web project, full feature parity, and creating the public GitHub repo. The cleartext decision was replaced by HTTPS only (entry above).
- **Impact:** Stack is no longer "proposed". Video decoder choice (MediaCodec vs Media3) is still open until the spike.

### 2026-11-24, Test credentials: owner runs the login, not Claude
- **Context:** Owner created a dedicated non-admin Blue Iris test user and asked how to share the password. The Blue Iris server is the owner's live server, not a development build of this app.
- **Options considered:** Paste the password in chat (rejected: it lands in transcripts); a password file Claude reads (rejected: Claude should not authenticate to the live server with a real password); an owner-run script that logs in and saves redacted samples (chosen).
- **Decision:** The owner runs `tools/Sample-BlueIris.ps1`, which prompts for the password with a hidden input, never saves it, and writes sample JSON (session token redacted) to a folder outside the repo. Claude reads those samples to write models and tests. Passwords are never stored in the repo, WORKSPACE.md or chat. Later, the owner types the password into the app's own sign-in screen when testing.
- **Rationale:** Keeps the secret with the owner and still gives real response shapes.
- **Impact:** `tools/Sample-BlueIris.ps1`, `docs/blue-iris-api.md` (marks facts as unverified until sampled). Sample files stay out of git.

### 2026-11-24, Finding: session token in URLs, and the video container (leans toward MediaCodec)
- **Context:** Reading how the reference web app talks to Blue Iris (behaviour only) showed it passes `session=` in image and video URLs, and also sets a session cookie. It also showed that the live stream is a Blue Iris container ("blue" header, typed blocks) wrapping H.264 or H.265 payloads, not MP4 or MPEG-TS.
- **Options considered:** Token in URL (as the web app does) vs cookie-only; MediaCodec with our own block parser vs a Media3 custom extractor.
- **Decision:** Try cookie-only (OkHttp cookie jar) first to honor the no-tokens-in-URLs rule; if Blue Iris refuses, record an exception here. For video, MediaCodec is now the leading choice because a Media3 extractor would need the same block parser anyway. The Media3 spike is still allowed but should be short.
- **Rationale:** Smaller code and lower latency with direct decode. The cookie approach keeps tokens out of logs and the proxy's access log.
- **Impact:** `docs/blue-iris-api.md` lists both as items to verify with real data. Reverses nothing; refines the stack entry.

### 2026-11-24, Verified against a live server: login, cookie-only session, response shapes
- **Context:** Owner ran `tools/Sample-BlueIris.ps1` against their Blue Iris 6.1.6.1 server as a non-admin user.
- **Decision:** (1) The MD5 challenge/response login works as documented in `docs/blue-iris-api.md`. (2) A session cookie alone is enough for `/image/`, so the app uses an OkHttp cookie jar and keeps tokens out of URLs; the earlier "exception" fallback is not needed for stills (video and clips still to check). (3) `camlist`, `status` and `alertlist` shapes are recorded in the API doc. (4) The `log` command is denied to a non-admin user ("Access denied"), so the system-log screen must handle that, as the web app also notes.
- **Rationale:** Confirms the plan with real data before any app code is written.
- **Impact:** `docs/blue-iris-api.md`. Real samples contain camera names and the server's coordinates, so they stay outside the repo and tests use invented fixtures. The coordinates field in the login reply must never be logged.

### 2026-11-24, Milestone 1 implementation choices
- **Context:** First app code: sign-in and camera list.
- **Decision:**
  - Same toolchain and versions as Embidextrous (AGP 9.4.1, Kotlin 2.4.21, Gradle wrapper copied from it, compileSdk 37, targetSdk 36, minSdk 26, JVM 17). Package `com.feerlessleadr.sightline`.
  - `ServerAddress.parse` is the only way to build the base URL. It refuses `http://` and any other scheme, URLs with user info, query or fragment. A bare host gets `https://`. A reverse-proxy sub-path is kept.
  - The password is never stored or logged: it lives in one text field, is cleared on tap, and is used only to compute the MD5 response. The app remembers only the server address and user name (SharedPreferences, excluded from backup). Sessions are in memory only; "stay signed in" comes later with Keystore encryption.
  - Session goes in the request body and a `session` cookie for the one server host. Never in a URL. Blue Iris's own Set-Cookie headers are ignored.
  - No redirects followed, 15 s timeouts, 8 MB reply cap, friendly errors that never include server text beyond Blue Iris's own `reason`.
  - The login reply's location fields are dropped at parse time (`LoginInfo` omits them).
  - No cleartext, system certificate authorities only (network security config). If the proxy uses a private CA, that must be handled deliberately; it is not allowed silently.
  - Unit tests use only invented data. Real samples stay outside the repo.
- **Impact:** `app/`, Gradle files. Release signing, CI and Dependabot config still to add.

### 2026-11-24, Milestone 2 part 1: live-still grid
- **Context**: Sign-in and camera list verified on a phone. Next is a camera grid; the web app's snapshot mode is the first step before live video.
- **Options considered**: Coil or another image library; plain `BitmapFactory` decode of JPEG bytes fetched by our own client.
- **Decision**: `BlueIrisClient.still()` fetches `/image/{camera}?w=&q=` using the session cookie only (no token in the URL), accepts only `image/*` replies up to 6 MB, treats redirects, 401 and 403 as an expired session. The grid (`LazyVerticalGrid`, adaptive 170 dp tiles) refreshes each tile every 2 s only while the tile is on screen and the app is started.
- **Rationale**: Reuses the hardened HTTP client (HTTPS only, no redirects, size caps) and adds no dependency or second network stack.
- **Impact**: `data/BlueIrisClient.kt`, `ui/CameraListScreen.kt`, `ui/AppViewModel.kt`. `tools/Sample-BlueIris.ps1` now also probes cookie-only `/thumbs/` and `/video/` and prints the first stream bytes, to decide MediaCodec vs Media3 in the next step.

### 2026-11-24, Stay signed in by saving the session token (not the password)
- **Context**: Owner reported having to sign in again every time the app is closed. Earlier decision: the password is never stored.
- **Options considered**: (a) keep signing in each launch; (b) save the Blue Iris session token, encrypted; (c) save the password, encrypted, and sign in silently each launch, optionally behind a biometric prompt.
- **Decision**: (b). `SessionStore` encrypts the token (plus server address and non-secret login details) with an AES-256-GCM key in the Android Keystore; it is deleted on sign-out, on an expired session, or on any decryption error. On launch the app resumes with the saved token and the server decides if it is still valid. The password is still never stored.
- **Rationale**: Keeps the "no password at rest" rule. A stolen token is limited to the session's lifetime and can be killed by signing out or from Blue Iris, unlike a password. Option (c) can be added later if the server's session lifetime turns out to be too short.
- **Impact**: `data/SessionStore.kt`, `data/BlueIrisClient.kt` (`restore`, `sessionToken`), `ui/AppViewModel.kt`. Backups already exclude all shared preferences. Reinstalling the app still signs out, because app data is deleted by design.

### 2026-11-24, Live video container verified: H.264 Annex-B, cookie-only
- **Context**: Owner ran the extended probe against Blue Iris 6.1.6.1.
- **Decision**: `/video/{cam}/2.0` and `/thumbs/` accept the session cookie alone. The stream starts `blue`, a small header with width, height and the ASCII codec name `H264`, then typed blocks; start codes `00 00 00 01` are present, so payloads are Annex-B. This supports a MediaCodec decoder fed by our own block parser (no Media3 needed for H.264). H.265 is still unverified.
- **Impact**: `docs/blue-iris-api.md` updated. Next code step is the container parser (with unit tests on invented byte data) and a single live-video screen.

### 2026-11-24, Fingerprint unlock with saved password (replaces the saved session token)
- **Context**: Owner does not want to sign in again after closing the app, and accepts saving the password if the app sits behind a fingerprint check. The token-only approach was built an hour earlier but kept the app open to anyone holding the phone.
- **Options considered**: saved token (no lock); saved password with fingerprint; keeping both.
- **Decision**: Reverses the "password never stored" rule from the Milestone 1 entry, with limits. On the sign-in form, "Remember me and unlock with my fingerprint" (shown only if the phone has a strong biometric) saves `{address, user, password}` encrypted with an AES-256-GCM Keystore key that needs a strong biometric for every use and is destroyed when fingerprints change. At launch (and after a sign-out or an expired session) the lock screen asks for the fingerprint, decrypts, and signs in silently. "Use password instead" and "Forget saved sign-in" are on the lock screen. If Blue Iris refuses the saved password, it is deleted. The session token is not saved; `SessionStore` and the client's `restore` were removed.
- **Rationale**: The password never leaves the secure hardware unauthenticated, nothing is readable without the fingerprint, and a sign-in each launch means session lifetime on the server no longer matters.
- **Impact**: `data/CredentialStore.kt`, `ui/LockScreen.kt`, `ui/LoginScreen.kt`, `ui/AppViewModel.kt`, `MainActivity.kt` (now a `FragmentActivity`), new dependency `androidx.biometric:biometric:1.1.0`. Backups still exclude all preferences. Fingerprints only (no device PIN fallback) because Keystore crypto with a PIN needs Android 11+ and minSdk is 26.

### 2026-11-24, Minimum Android raised to 11 (API 30)
- **Context**: Owner does not need Android 8 to Android 10. Supersedes minSdk 26 in the earlier stack entries.
- **Decision**: `minSdk = 30`. The Keystore key now always uses `setUserAuthenticationParameters`, so the version check was removed. The launcher icon moved back to `mipmap-anydpi` (the earlier aapt2 failure was caused by minSdk 26).
- **Impact**: `app/build.gradle.kts`, `README.md`, `CredentialStore.kt`, `res/mipmap-anydpi`. Android 11 also allows a device PIN or pattern as a fallback for the Keystore key if the owner ever wants it; not added.

### 2026-11-24, PIN/pattern fallback and live video (first version)
- **Context**: Owner asked for the phone's PIN or pattern as a fallback to the fingerprint, then live video straight away.
- **Decision (unlock)**: The Keystore key (now alias `..._v2`) accepts a strong biometric or the device credential, and the prompt allows both. The older biometric-only key cannot be changed, so a sign-in saved before this build is dropped once and must be saved again. "Remember me" is offered when the phone has a strong biometric or a screen lock.
- **Decision (video)**: MediaCodec, no Media3. `video/BlueStreamReader` parses the Blue Iris container (header, video/audio/status/end blocks) from an okio source; `video/LivePlayer` feeds access units to MediaCodec (`video/avc`, or `video/hevc` if the header says H265) rendering to a `SurfaceView`, dropping frames if the decoder is behind. `BlueIrisClient.openVideo` opens `/video/{cam}/2.0?audio=0&stream=0&extend=2` with the cookie only, no overall call timeout, 20 s stall timeout. Tapping a camera tile opens `LiveVideoScreen`; the stream runs only while the surface exists, so it stops in the background and restarts on return.
- **Unverified**: the byte order of the video block length (the reader checks the next `Blue` marker and adopts whichever order fits, and reports the 18 header bytes if neither does), H.265 playback, audio (not played), and latency on real hardware.
- **Impact**: `data/CredentialStore.kt`, `data/BlueIrisClient.kt`, `video/*`, `ui/LiveVideoScreen.kt`, `ui/CameraListScreen.kt`, `MainActivity.kt`, `ui/AppViewModel.kt`.

### 2026-11-24, Correction: the web app's grid is live video, from one group stream
- **Context**: Owner asked how the web app handles live video in the grid. I had said its grid used snapshots (wrong: snapshots are only the opt-out and the fallback).
- **Finding**: see docs/blue-iris-api.md ("How the web app shows live video in the camera grid"). One group stream, tiles cut out of it with the group's `rects`; up to 3 big tiles get their own stream.
- **Impact**: To match the web app, the Android grid should decode one group stream and draw each camera's rectangle into its tile. The current 2 s stills grid stays as the fallback and as the "snapshots" setting.

### 2026-11-24, Feature audit, live group grid, video fixes
- **Context**: Owner reported (1) saved sign-in did not survive a reinstall, (2) single-camera live video showed only black, (3) asked for a full audit of web-app features against our plan, then the live grid built like the web app's.
- **Audit**: `docs/feature-parity.md` lists every feature from the web app's README, CHANGELOG (to v0.16.0) and `sightline.htm` (screens, Settings panel, colours, polling, streams), each with a status and milestone M1 to M6. Found extras not in the README: screen wake lock, haptic buzz, `?grpdebug` / `?cliprewind` switches, the dark-only palette. Theme is dark only (`#0b0d10` background); our Material theme must be replaced by those tokens (M2).
- **Reinstall**: an uninstall deletes the app's data and its Keystore keys, so a saved sign-in cannot survive it by design (no cloud, no backup). Installing an update over the old app keeps it, except the one-time key change to v2. Saving failures are now shown on screen with the reason instead of failing silently.
- **Black video (cause not yet confirmed)**: likely candidates fixed: MediaCodec input buffers were too small for key frames (now `KEY_MAX_INPUT_SIZE` 4 MB), frames were fed before a key frame (now waits for one), and no `w/h/q/kbps` were sent (now uses the web app's tier table). On-screen diagnostics show frames received, key frames and frames drawn. `tools/Sample-BlueIris.ps1` now also saves 2 MB captures of a camera stream and the group stream (with `X-` headers) to the sample folder so the parser and decoder can be tested on real bytes.
- **Live grid**: one stream of the whole group (`/video/{group}/2.0` with the grid-width tier, 720p to 1440p), decoded once by MediaCodec into a `SurfaceTexture`; `GroupGlView` (OpenGL ES 2) draws each camera's rectangle into its tile, so many tiles cost one connection and one decoder. Layout comes from the stream's `X-CamList`/`X-RecList` headers when present, else the camera list. Fallbacks as in the web app: stills for tiles not in the picture, and for all tiles if the stream fails (10 s watchdog, retry after 15 s x failures up to 5 min). Stops with the screen or app. Auto tile size fits the whole group on screen (2 + 2, not 3 + 1), with Large / Medium / Small columns (1/2/3), saved per group. "Live"/"Stills" toggle (will move into Settings in M5).
- **Not yet done in M2**: per-camera Large/Wide/Tall tiles, drag reorder, own streams for up to 3 big tiles, status polling with activity borders and AI labels, motion boxes, web palette and header, full screen, reconnect banner.
- **Impact**: `video/*` (new `GroupGlView`, `StreamSizing`), `ui/LiveGrid.kt`, `ui/TileLayout.kt`, `ui/CameraListScreen.kt`, `ui/AppViewModel.kt`, `data/CredentialStore.kt`, `docs/feature-parity.md`.

### 2026-11-24, Single-camera video works; player stream size follows the web app's Auto
- **Context**: Owner's phone played a single camera (1920x1080 H.264) and "Remember me" works. Start-up was slow. Real captures confirmed the parser end to end (docs/blue-iris-api.md).
- **Decision**: the one-camera player now asks for what the web app's Auto asks for (`playerStreamRequest`: pixels covered with the pixel ratio capped at 2, plus 25%, rounded up to a tier, at least 720p), so a phone gets the 720p tier at 1 Mbps instead of 1080p at 2 Mbps; less transcoding for Blue Iris and less data. The diagnostics line shows connect time and time to first picture, to measure start-up. `RealCaptureTest` runs the parser over the owner's local captures when present (they hold camera pictures and never enter the repo).
- **Impact**: `video/StreamSizing.kt`, `ui/LiveVideoScreen.kt`, `video/RealCaptureTest.kt`.

### 2026-11-25, Lock after N minutes (default Always), Settings screen, quality picker, instant still
- **Context**: Owner wants the fingerprint required every time by default but changeable in settings; and found one-camera video slow to open. Phone measurements (diagnostics line): wired PoE camera, asked 1280x720 at 1 Mbps: connected 2.4 s, first picture 3.5 s. Wireless camera, asked 960x720 but the stream header said 2560x1920: connected 3.2 s, first picture 7.3 s, 36 of 50 frames drawn.
- **Reading of the numbers**: most of the wait is Blue Iris starting the stream (2.4 to 3.2 s before it even answers), and on the wireless camera another 4 s before a picture, which looks like Blue Iris passing the camera's own stream through (size request ignored) and waiting for a key frame. Resolution is not the bottleneck; a bigger picture would not start sooner and would cost data and decoder work. Not proven, so the quality is now the owner's choice.
- **Decisions**: (1) New Settings screen (Live view, Video, Security, Account) from the Live screen's top bar; Sign out moved there. (2) "Lock the app": Always (default), 1, 5, 15 or 60 minutes away. Always asks for the fingerprint every time the app comes back from the background or starts; a time keeps the session for that long: the Blue Iris session token (never the password) is saved encrypted with a Keystore key that needs no fingerprint, only when a time other than Always is chosen and a sign-in is saved; it is deleted on lock, sign-out, an expired session or switching back to Always. A screen rotation does not count as leaving. (3) Video quality Auto / 480p / 720p / 1080p / 1440p / 4K (the web app's tiers) in Settings and as a button in the player; Auto matches the screen like the web app. (4) The one-camera view shows a current still immediately while the video connects, removed at the first video frame.
- **Impact**: `data/Settings.kt`, `data/SessionStore.kt`, `ui/SettingsScreen.kt`, `ui/AppViewModel.kt`, `ui/LiveVideoScreen.kt`, `MainActivity.kt`, `video/StreamSizing.kt`. docs/feature-parity.md updated (quality picker partial, Settings partial).

### 2026-11-25, "Never" lock choice (no fingerprint ever)
- **Context**: Owner had read "Always" as "always stay signed in". What they want is an option that never asks for the fingerprint. Default stays Always (as stated), changeable in Settings -> Security -> Lock the app.
- **Decision**: new `LockAfter.Never`. The saved sign-in is then kept encrypted with a second Keystore key that needs no fingerprint (`CredentialStore.saveOpen/loadOpen`); at launch, and whenever Blue Iris ends the session, the app signs in by itself ("Signing in to Blue Iris..." screen with Try again, Use password instead, Forget saved sign-in). Switching to Never takes one fingerprint check to move the saved password to the open form; switching back takes one to protect it again. Signing in with "Remember me" while Never is chosen saves it straight away. Sign out under Never also forgets the saved sign-in (otherwise it would sign straight back in). A password Blue Iris refuses is deleted.
- **Trade-off**: with Never anyone who can open the unlocked phone can open the cameras; other apps and backups still cannot read the password. Settings says so under the option.
- **Impact**: `data/Settings.kt`, `data/CredentialStore.kt`, `ui/AppViewModel.kt`, `ui/LockScreen.kt`, `ui/LoginScreen.kt` (checkbox now "Remember me on this phone"), `ui/SettingsScreen.kt`, `MainActivity.kt`. Supersedes the "Always" wording in the 2026-11-25 lock entry.

### 2026-11-25, Slow video start: fixes and a measuring tool (cause not yet found)
- **Context**: Owner measured the one-camera view on their phone: Blue Iris answered after 1.5 to 7.5 s and the first picture came 4.8 to 18.8 s after the request, while the web app on the same phone starts almost at once.
- **What the web app sends** (read from its source): the same URL and size/quality settings, but with `session=<token>` in the address as well as the cookie; fetch with no caching. We send the token only in the cookie (design rule: no token in URLs). Blue Iris's own stream profiles are `CBR ... gop=300` (a key frame every 300 frames), seen in the login reply.
- **Fixed (real inefficiencies, not shown to be the main cause)**: (1) `BlueStreamReader` waited for the start of the NEXT block before handing over the first frame (byte-order detection); the length is big-endian (verified), so frames are now delivered as soon as they arrive. (2) The "first picture" figure and the removal of the placeholder still were only updated once a second; they now happen at the first drawn frame, and the on-screen line splits the time into connection, header, first frame and first drawn frame.
- **New tool**: Settings -> Developer -> "Test video start" opens one camera six ways (as the app does, no compression, session in the address like the web app, HTTP/1.1 only, no size/quality settings, browser-like headers) plus a repeat, and prints how long Blue Iris takes to answer, to send the header and to deliver the first frame (no decoding), so the slow part is found by measurement. Output shows only timings, protocol and sizes.
- **Next**: act on the measured result (for example add the session to the address if that is what Blue Iris is quicker with, which would be a decision to weigh against keeping tokens out of URLs and Caddy logs).
- **Impact**: `video/BlueStreamReader.kt`, `video/LivePlayer.kt`, `data/BlueIrisClient.kt` (`probeVideo`), `data/Settings.kt` (`ProbeVariant`), `ui/SettingsScreen.kt`, `ui/LiveVideoScreen.kt`.

### 2026-11-25, Slow video start: not reproduced on the retest, no protocol change
- **Context**: Owner reported video much slower than the web app (Blue Iris answered after 1.5-7.5 s, first picture 3-19 s). After the reader/timing fixes and the new "Test video start" tool, the owner re-ran it on the phone.
- **Measured**: all six ways of opening a stream (cookie, no compression, session in the address, HTTP/1.1, no size/quality settings, browser-like headers) plus a repeat answered in 129-211 ms with the first frame at 140-225 ms. Single cameras: kitchen camera first picture 218 ms after the request; front door (2560x1920 source, asked 960x720) 865 ms, mostly decoder start.
- **Decision**: do NOT put the session token in the address; the cookie alone is as fast. No change to how streams are opened. Keep the test tool for future diagnosis.
- **Rationale**: no variant was faster than another, so the web app's token-in-URL is not what matters. The earlier multi-second delay is unexplained: it was in Blue Iris's own answer time, which the app cannot affect, so likely server-side load or encoder start-up at that time (it was night then) or the first-block wait fixed since. Not proven.
- **Impact**: none in code. If slowness returns, run the Developer test and compare the "answered" time first.

### 2026-11-25, M2 round 2: web look, tile layout, live status, big tiles, motion boxes, quiet re-sign-in
- **Context**: owner asked for the rest of M2, starting with the look. Behaviour was read from the web app's source (`sightline.htm`: `refreshStatus`, `pollRecentAlerts`, `tileLabels`, `ownSync`/`ownSize`, `mbDetect`) rather than guessed.
- **Decisions**:
  - Dark only with the web's colour tokens (`ui/Theme.kt`, `object Sl`); no dynamic/light theme. Hand-drawn glyphs instead of an icon library.
  - Tile shapes and order are per group, stored in SharedPreferences (`tile_order_<group>`, `tile_shapes_<group>`). Large/Wide are full width on phones (width under 600 dp); Large keeps 16:9 by taking as many rows as columns. Press-and-hold then drag reorders; releasing without moving opens the shape sheet (our choice; the web uses press-and-hold for the size menu).
  - Status: `camlist` every 2 s and `alertlist` (camera `index`, last 120 s) every 4 s only while the grid is on screen and the app in front, as the web does; with highlights and labels both off a slow 8 s check keeps dots and the banner honest. AI result is fresh for 45 s, or 3 min while the camera is triggered; border is class colour when fresh, else red (trigger) or white 55% (motion). Rules: `data/AlertTags.kt` (ONVIF aliases, classify, flag bits 26 person / 27 vehicle / 31 wildlife from the web source).
  - Big tiles: up to 3 non-Normal tiles that are on screen and online open the camera's own stream (720p-1080p tier from the tile size, pixel ratio capped at 2) in a TextureView inside the tile; on failure or 10 s without a picture the tile uses the shared group picture for 30 s x failures (max 5 min), same as the web.
  - Motion boxes: the web detector ported to Kotlin (`video/MotionBoxes.kt`, tested). Samples: stills decoded every 0.6 s, and the shared live picture rendered per tile into a small off-screen buffer every 0.6 s. **Deviation**: a big tile showing its own stream is sampled from the shared picture (lower resolution, same tile crop), not from its own stream. Off by default like the web.
  - **Password in memory (security trade-off)**: to stay signed in through Blue Iris session timeouts while the app is open (the web's "stay signed in"), the password is kept in memory after a fingerprint unlock or a sign-in with "Remember me". Never kept for a sign-in the owner did not ask to remember. Cleared on sign-out, lock and "Forget saved sign-in". Not written anywhere new.
  - Keepalive `status` every 40 s while in front; resume/poll failures show a "Reconnecting" banner instead of the login screen.
- **Impact**: new `ui/Chrome.kt`, `ui/TileGestures.kt`, `ui/OwnVideo.kt`, `data/AlertTags.kt`, `video/MotionBoxes.kt`; changed `LiveGrid`, `CameraListScreen`, `AppViewModel`, `GroupGlView`, `SettingsScreen`, `TileLayout`. No contract with other projects changed.

### 2026-11-25, Reorder flicker and pixelated Large size (owner phone test)
- **Context**: owner tested M2 round 2: everything works, but (1) tiles jumped back and forth while dragging to reorder, (2) with the Large size the live tiles were very pixelated while Medium and Small and the single-camera player looked fine.
- **Cause 1**: every swap was saved through the ViewModel and the grid re-laid out; the dragged tile's centre then sat in a different slot (and the swap used stale positions until the next draw), so it swapped again, over and over.
- **Cause 2**: the shared group picture holds every camera in one small picture, so one camera gets only a small piece (for example about 320 px wide); a Large tile shows it at the full screen width (about 1000 px), stretched 3x. Only Normal-size tiles at Medium/Small were sharp enough.
- **Decision**: (1) the new order is held locally while dragging and saved once on release; a swap needs the dragged tile's centre inside the middle 60% of the other tile, and only one swap per layout pass. (2) A tile also gets its own stream when the shared picture would have to be stretched more than 2x to fill it (besides Large/Wide/Tall tiles); the limit of simultaneous own streams goes from 3 to 4 (the web's 3 comes from browser connection limits, which Android does not have).
- **Impact**: `ui/LiveGrid.kt` only. Not yet re-tested on the phone: if Large still looks soft, raise the own-stream limit or lower the stretch limit (`STRETCH_LIMIT`).

### 2026-11-25, Reorder: tiles were bound to grid positions, not cameras
- **Context**: after the first fix the owner still saw flicker near the drop spot and could not drag a tile back to where it started while still holding it.
- **Cause**: the tile composables were created by position without a key, so when the order changed each position's state (the finger-hold gesture, own stream surface, still) stayed with the position and ended up on a different camera: the held gesture then moved the wrong tile. Also the GL video positions were updated by a `LaunchedEffect`, one frame after the overlays.
- **Decision**: wrap each tile in `key(camera id)` so state follows the camera, and push the GL tile positions with `SideEffect` (same frame).
- **Impact**: `ui/LiveGrid.kt` only.

### 2026-11-25, M3 stage 1: recorded playback and timeline
- **Context**: owner approved the M3 order (recorded playback and timeline, then PTZ and zoom, then group view, then download). Behaviour read from the web source (`recVideoUrl`, `loadTimeline`, `drawScrub`, `jumpAlert`, `goWindow`), not guessed.
- **Decisions**:
  - One player screen (`ui/PlayerScreen.kt`, replaces `LiveVideoScreen`) for live and recorded video. Recorded video: `GET /time/<camera>?opaque=&pos=<ms>&speed=<percent>&audio=0&stream=0&w=&h=&q=&kbps=&extend=2`, same container as live. A paused or scrubbed recording shows a still from `/time/<camera>?jpeg&isolate&pos=<ms>`.
  - Each video frame carries its recording time (bytes 6..13 of the 18-byte block header, little-endian, 32-bit low part and 21-bit high part). VERIFIED on the real live capture (the first frame decoded to a time 16 s before the file was written). The player shows the position from the frame times.
  - Pacing: recorded frames are handed to the decoder on the schedule of their recording times divided by the speed (never faster than Blue Iris sends); if far behind or far ahead it carries on from where it is. Reason: the web app paces on the client (durations from the timestamps) and a late or early burst must not drop frames.
  - Timeline: `timeline` command (`startdate`, `enddate` in seconds, `msecpp`, `camera`); spans come back as `x1`/`x2` step numbers (clips, alerts with `type` flag bits) counted from the start second. Window math is the web's (`video/TimelineMath.kt`): ~500 steps across, 4% past now, 2m/15m/1h/6h/24h/48h buttons, pinch 30 s to 7 days, newest 15 s not playable (KEEP_OUT).
  - Controls: play/pause, -10 s, +10 s, previous/next alert (7 days of `alertlist` for the camera, 2 s before the alert), speed 1/2/4/8/16x, LIVE, go to date and time (quick choices as the web; a custom day and start time shows one hour). Screen stays on while the player is open.
- **Not in this stage (deliberately)**: audio and volume, alerts playing from their clip file (`/file/clips/`, cookie-only not yet verified), calendar with recorded days greyed and alert dots, frame step, old-Blue-Iris clip-list fallback, tap-to-pause, double tap, floating controls, landscape/full screen, PTZ, zoom, group view, download, flag/protect.
- **Unverified until the owner tests**: the `timeline` reply shape (read from the web source), `/time/` streaming with these parameters, `jpeg&isolate` stills, and that Blue Iris paces `speed` itself (our pacing makes this safe either way).
- **Impact**: new `ui/PlayerScreen.kt`, `ui/TimelineBar.kt`, `video/TimelineMath.kt`; `data/BlueIrisClient.kt` (`openRecorded`, `recordedStill`, `timeline`, `cameraAlerts`), `data/Models.kt` (Timeline, AlertItem), `video/BlueStreamReader.kt` (frame time), `video/LivePlayer.kt` (pacing, `StreamEndedException`).

### 2026-11-25, Pinch zoom on the player's video
- **Context**: owner asked for pinch to zoom on the video itself (web: digital zoom to 8x).
- **Decision**: two fingers zoom around the point between them (1x to 8x), one finger pans while zoomed in (when not zoomed, one finger is left to the page so it still scrolls), double tap or the "2.5x reset" badge resets. The picture and the paused still share one zoom transform. In Auto quality, once the zoom has settled for 0.9 s the stream is asked for a bigger size (boost 1.6 from 1.4x, 2.5 from 2.2x, as the web app) and paused stills fetch 1920 px; the stream restarts only if the asked-for size really changes. Zoom resets when another camera opens.
- **Change of view type**: the player now draws video in a TextureView (it was a SurfaceView) because a SurfaceView cannot be scaled and clipped reliably. Same decoder code.
- **Impact**: `ui/PlayerScreen.kt` only. Not yet tried on a phone.

### 2026-11-25, Player layout like the web app; zoom no longer blacks out the video
- **Context**: owner tested zoom: the video went black as soon as it was pinched (even with a fixed quality, so not a stream restart), and the picture sat in a small 16:9 box at the top with empty space below, so a zoomed picture had nowhere to grow. The web app centres the picture on the screen, puts the controls at the very bottom and lets the zoomed picture fill the whole area.
- **Cause of the black video**: the zoom was applied by Compose's `graphicsLayer` around a TextureView, which does not redraw a video view reliably.
- **Decision**: the video area (the "stage") now takes all the room between the header and the controls. The TextureView fills the stage and its own `setTransform` matrix fits the picture (centred, aspect kept) and applies zoom and pan, so the layout system never scales the video. The paused still uses the same transform. Pan is clamped so the picture never leaves a gap, but a zoomed picture smaller than the stage stays centred and grows into the free space (`video/ZoomMath.kt`, tested).
- **Controls** moved to the bottom, as in the web app: timeline, then one row (2m..48h with a calendar button for "go to date and time", speed, LIVE), then the transport buttons. The time ("Live" or the date and time) and the zoom badge are small chips on the picture; messages, errors and Retry also sit over the picture so they do not move the controls. The grey diagnostics line is shown only with an error.
- **Impact**: `ui/PlayerScreen.kt`, `ui/Chrome.kt` (calendar glyph), new `video/ZoomMath.kt`.

### 2026-11-25, Zoom black after letting go: a "not a number" pan
- **Context**: the owner saw the zoomed video play (choppy) while the fingers were down and go black the moment they lifted; it came back only after zooming out.
- **Cause**: the last event of a gesture has no finger down, so the centre point between the fingers is undefined (NaN). The pan was computed from it, became NaN, and a transform with NaN draws nothing. Zooming out reset the pan to 0, which is why that fixed it.
- **Decision**: ignore events with no finger down or no centre, and make `ZoomMath.clampAxis` return 0 for any NaN or infinite offset (tested).
- **Not addressed yet**: choppy playback while pinching (suspected: the whole stage recomposes on every finger movement). To check after this fix.

### 2026-11-25, M3: calendar in "Go to date and time"
- **Context**: owner chose the next order: calendar showing recordings, then alerts playing from their clip, then the rest, PTZ last (no PTZ cameras).
- **Decision** (rules read from the web source, `calLoad`/`calRender`): the go-to sheet now has the quick choices, a month calendar and From/To times. Per camera and month it asks `timeline` for the whole month at one-hour steps (current month up to now), and marks every day a recorded stretch touches. Days in the future, and days with no recordings once the answer is in, are greyed out and cannot be picked; days with alerts get a dot; today has a ring. Answers are kept while the player is open: 5 minutes for the current month, an hour for older ones. Sunday-first grid like the web app. A "To" time at or before "From" runs into the next day; the end covers the whole last minute.
- **Impact**: new `ui/GoToSheet.kt`, `video/CalendarMath.kt` (tested); `ui/PlayerScreen.kt` (uses the sheet, keeps the month cache). The earlier date and time picker dialogs are gone.

### 2026-11-25, M3: alerts play from their own recording file
- **Context**: web app plays an alert from its recording file (`/file/clips/`) because on some cameras the timeline shows an alert cropped to a magnified corner; outside the alert, or after the file ends, playback carries on along the timeline. Rules read from its source (`clipPlay`, `recVideoUrl`, `vsEnded`, `durMs`).
- **Decision**: a jump to an alert (previous/next alert) remembers its file (the alert's `clip`, or its `path` when `clip` is a `@-1.` placeholder; only `.bvr` files), its start (alert time minus `offset`) and its end (alert time plus the length read from the `filesize` text, 10 s if unreadable). While the position is inside that stretch the stream is `GET /file/clips/<file>?speed=&time=<ms into the file>&audio=0&stream=0&w&h&q&kbps&extend=2`. At the end of the file the player continues along the timeline from where it ended. If the file cannot be opened (not verified yet that the session cookie alone works for `/file/clips/`) the file is marked broken for this player and the timeline is used, so nothing depends on that guess.
- **Frame times**: clip files may carry no date, so a frame's time is the position the file was opened at plus the millisecond counter (bytes 2..5 of the block header, big-endian) since the first frame; used when the block's own clock is not a real date.
- **Not changed**: paused stills still come from the timeline (the web only uses the file for stills after a timeline failure).
- **Impact**: `data/AlertClips.kt` (new, tested), `data/BlueIrisClient.kt` (`openClip`), `data/Models.kt` (alert file size), `video/BlueStreamReader.kt` (`rawMillis`), `video/LivePlayer.kt` (`clipBaseMs`), `ui/PlayerScreen.kt`, `ui/AppViewModel.kt`.

### 2026-11-25, M3: audio and volume in the player
- **Context**: next item after calendar and alert files. Behaviour read from the web source (`vsAudioFrame`, `ensureAudio`, `applySound`, the volume pop-up).
- **Decision**: the stream header's sound format (a WAVEFORMATEX after the picture header: tag, channels, rate) is parsed; only 8-bit mu-law mono is played (what Blue Iris sends; the web app does the same). Audio blocks (4-byte big-endian size, then samples) are decoded with the standard mu-law table and played through an `AudioTrack` fed by a thread of its own (queue, about 0.25 s cushion, drop when more than 1.2 s behind), so sound can never hold up the video. Streams are asked for `audio=1` only after sound has been on (live; recordings and alert files only at 1x, as the web app). Turning sound on mid-stream reopens the stream so Blue Iris starts sending it; muting afterwards just sets the volume to 0 (instant, no reopen). Volume 0 to 200% (above 100% scales the samples with clipping; up to 100% is the track's own volume). On/muted and volume are remembered on the phone (`sound_on`, `sound_volume`), default on at 100%.
- **UI**: speaker button in the player's top bar opens a small menu with a mute button, the volume slider and the percentage, plus a note if the camera is not sending sound.
- **Not done**: audio in the grid tiles (the web app has none), two-way audio (not in the web app either).
- **Impact**: new `video/MuLawPlayer.kt` (`MuLaw`, `MuLawPlayer`), `video/BlueStreamReader.kt` (audio format and samples), `video/LivePlayer.kt` (`audio`, `onHasAudio`), `data/BlueIrisClient.kt` (`audio` parameter), `ui/PlayerScreen.kt`, `ui/AppViewModel.kt`, `ui/Chrome.kt` (speaker glyphs), `MainActivity.kt`. Not yet heard on a phone.

### 2026-11-25, M3: tap, double tap, floating controls, full screen and landscape
- **Context**: next player batch. Rules read from the web source (`stageTap`, the double-tap handler, `fsIdle`, `overlayCtl`, the landscape and full-screen CSS).
- **Decisions**:
  - A single tap on the picture (after waiting to see it is not a double tap) closes a live camera back to the grid and pauses or resumes a recording. When the controls float over the picture (full screen, or a landscape phone) a tap shows or hides them instead.
  - A double tap resets the zoom if zoomed; otherwise it switches between fitting the whole picture and filling the screen (edges cropped), and resets the zoom (`ZoomMath.fit` has a `cover` mode).
  - Full screen (new button in the top bar, Back leaves it) hides the system bars. In full screen and on a landscape phone (screen at most 540 dp high) the top bar and the controls float over a full-screen picture on gradients and fade in and out (200 ms). In full screen they fade out after 3 s of watching unless something needs them (volume menu, quality or go-to sheet, scrubbing, a paused recording); on a landscape phone they stay until tapped away.
  - The activity no longer restarts when the phone is rotated (`configChanges` in the manifest), so the player keeps its place and the stream restarts only because the video view is rebuilt.
  - The player layout is built from shared parts (stage, controls, buttons) used by both the normal screen and the floating one.
- **Not done**: swiping sideways to the next or previous camera (a separate item in the list), hardware-keyboard shortcuts, a landscape layout that folds the transport row into the speed row like the web app (ours floats the same controls).
- **Impact**: `ui/PlayerScreen.kt`, `video/ZoomMath.kt` (tested), `AndroidManifest.xml`.

### 2026-11-25, Player gestures: a drag is never a tap, and a filled picture can be panned
- **Context**: owner tested: double tap (which switches to fill) looked like zooming in but the picture could not be moved, and dragging it counted as a single tap and closed the player.
- **Cause**: panning was only allowed after a pinch zoom (zoom above 1), not in fill mode; and Compose's tap detector counts any press lifted inside the picture as a tap, even after a drag.
- **Decision**: one gesture handler for tap, double tap, pan and pinch (the earlier two handlers are gone). A press is a tap only if it never leaves the touch slop, never used two fingers and ends within 0.4 s; a second tap within 0.3 s makes it a double tap, otherwise the single tap fires after 0.3 s. One finger pans when zoomed in or in fill mode (`ZoomMath.clampAxis` keeps the picture covering the stage); pinching out in fill mode does not drop back below the fill.
- **Impact**: `ui/PlayerScreen.kt` only.

### 2026-11-25, M3: swipe between cameras in the player
- **Context**: next item. Rules read from the web source (`switchCam`, `changeCam`, the swipe test in the picture's pointer-up handler).
- **Decision**: a quick sideways swipe on the picture (more than 60 dp across, clearly more sideways than up or down, under 0.6 s) switches to the next camera (swipe left) or the previous one (swipe right), wrapping round, but only when the picture is neither zoomed nor filling the screen (a drag then pans it). The cameras are the ones shown in the grid's group in the grid's order, including any order the owner made, or all shown cameras if the current one is not among them (`playerCameras`, tested). Switching keeps the moment: live stays live, a recording keeps its time, play state and speed; zoom, the alert file, timeline and alerts belong to the camera and start fresh. The web app's previous and next buttons only appear for a mouse (hidden on touch screens), so there are none.
- **Impact**: `ui/PlayerScreen.kt` (gesture, new `cameras` and `onSwitchCamera` parameters), `ui/AppViewModel.kt` (`playerCameras`), `MainActivity.kt`.

### 2026-11-25, Smoother camera swipe: the picture follows the finger and the next camera slides in
- **Context**: owner asked for a smoother, more professional swipe transition.
- **Decision**: a clearly sideways drag (decided at the first real movement) now moves the current picture with the finger and slides the neighbouring camera's picture in from that side. On release, a drag past 40% of the width, or a quick one of at least 60 dp, slides the rest of the way (180 ms), then switches camera with the new picture already on screen until its video starts; anything shorter springs back (160 ms). A second finger joining cancels the swipe. Swipes are ignored while one is settling.
- **Limit**: the sliding neighbour is a still frame (640 px wide) of that camera: live ones are fetched a moment after a camera opens and refreshed when a swipe starts; for a recording, the neighbour's frame at the same time is fetched when the swipe starts and may take a moment to arrive (until then the neighbour shows as a dark panel with its name). The new camera's live video cannot be shown moving because its stream only starts once chosen.
- **Impact**: `ui/PlayerScreen.kt` only (the still is no longer reset by the camera, so the swipe can pass one on).

### 2026-11-25, Camera swipe: both pictures are stills while sliding
- **Context**: owner saw the leaving picture (still live) jerk while the incoming still was smooth. Moving a live video view re-layers it on every frame; a still drawn by Compose moves smoothly.
- **Decision**: when a swipe locks on, a snapshot of the current frame (`TextureView.getBitmap`, half the stage size, taken before anything moves) is shown in its place and the live video view is hidden (alpha 0, the decoder keeps running). The snapshot slides with the finger and out; the neighbour is a still as before. If the swipe springs back the snapshot is dropped and the live video, still running, is shown again at the same place; if it completes, the new camera's still is up before the switch. Only done when the video view is what is on screen (not when a still already covers it).
- **Unverified**: that `getBitmap` returns the picture with its fit transform (not stretched over the whole view); if the snapshot looks stretched, the fix is to draw it through the same fit rectangle.
- **Impact**: `ui/PlayerScreen.kt` only.

### 2026-11-25, Swipe snapshot was stretched: `getBitmap` ignores the fit transform
- **Context**: the owner's screenshot showed the leaving snapshot stretched over the whole video area. This was the open question in the entry above.
- **Finding**: `TextureView.getBitmap` returns the raw video stretched to the size asked for; the view's own `setTransform` (which fits the picture) is not applied to it.
- **Decision**: ask for the snapshot at the picture's own proportions (the fitted rectangle, half size) and draw it in that rectangle (offset and size from `ZoomMath.fit`), then slide it.
- **Impact**: `ui/PlayerScreen.kt` only.

### 2026-11-25, M3: frame step
- **Context**: web app steps one frame with the `,` and `.` keys (`stepFrame`); the feature list asks for on-screen buttons on a phone.
- **Decision**: two buttons beside play/pause (previous frame, next frame). A frame is as long as one frame of the camera's reported rate (`FPS` from the camera list), 10 per second if unknown (the web's rule; `TimelineMath.frameIntervalMs`). Stepping pauses a playing recording on the frame shown, then moves one frame; from live only stepping back works (it pauses on the frame shown first); stepping forward stops at the newest playable moment (never into live; `TimelineMath.stepTarget`). The picture for the new moment is the paused still of that time (as when scrubbing). The -10 s and +10 s buttons were made a little narrower to make room.
- **Not done**: holding a button to keep stepping.
- **Impact**: `ui/PlayerScreen.kt`, `ui/Chrome.kt` (two glyphs), `data/Models.kt` (camera `fps`), `video/TimelineMath.kt` (tested).

### 2026-11-25, M3: motion boxes in the player
- **Context**: the detector already runs on the grid's tiles; the web app uses the same one in the player (`mbTick`, `mbDraw`) with the same on/off setting and sensitivity.
- **Decision**: a boxes button in the player's top bar (accent colour when on) toggles the one shared setting (sensitivity stays in Settings). While on, about every 150 ms the picture on screen is sampled at 128 px wide: the live or recorded video from the video view (only when a new frame has arrived; `getBitmap` at the picture's proportions), or the still of a paused or scrubbed recording (so stepping frame by frame shows what moved between frames). A new stream, camera or seek starts the comparison over and its first 1.5 s only become the reference (the detector's settle rule). Boxes show for 0.9 s (fade from 0.4), drawn through the same fit and zoom as the picture so they follow zooming and panning; hidden while swiping.
- **Impact**: `ui/PlayerScreen.kt`, `ui/Chrome.kt` (boxes glyph, `IconAction` takes a tint), `video/MotionBoxes.kt` (`visible` takes a fade time; tested), `ui/LiveGrid.kt` (shares `graySampleOf`), `MainActivity.kt`.

### 2026-11-25, M3: group view, step 1 (Blue Iris's whole group picture)
- **Context**: "watch all cameras at once" in the player. Rules read from the web source (`vGroup`, `groupFor`, `grpLayout`, `groupCamAt`, `grpStreamLayout`, `switchCam` for groups, `GROUP_ARGS`). Built in two steps so each can be tested; this is step 1.
- **Decisions**:
  - A group is opened in the same player as one more "camera": the player takes a group entry from the camera list (its size, camera names and boxes). Streams, stills and the timeline use the group's id; groups are asked for `flags=1&mask=1` so Blue Iris prints each camera's name on the combined picture. No sound is requested for a group. Frame step uses 30 fps for groups (the web's rule). Jumping to an alert moves to that moment (no single recording file).
  - A group button (top bar, left of the boxes button) opens the grid's group if it holds the camera, else All cameras, else the first group that does (`groupFor`), and from a group goes back to the camera it was opened from, else the group's first shown camera (`cameraToReturnTo`). Only groups Blue Iris gives a layout for are offered.
  - A single tap on a camera in the group picture shows that camera at the same moment (live stays live, a recording keeps its time, play state and speed) (`GroupLayout.cameraAt`, tested). The layout is the stream's own `X-CamList`/`X-Reclist` headers, else the camera list's layout, for live; for a recording the headers are used only when they name the cameras in the camera list's order (Blue Iris lays groups out again for playback, so otherwise there is no layout and a tap does nothing; the web app asks a still of the same moment instead, which is not built yet).
  - Swiping sideways in group view moves between groups that have a layout (as the web app does); the title shows the group's name and camera count.
- **Not done yet (step 2)**: the web app hides cameras that Sightline hides (Hidden in Blue Iris, disabled, no access) by redrawing only the shown cameras into a grid of its own. Step 1 shows Blue Iris's picture as it is, so a camera hidden in the app can appear inside a group's picture.
- **Impact**: `ui/PlayerScreen.kt`, `ui/AppViewModel.kt` (`groupFor`, `cameraToReturnTo`, group swiping), `MainActivity.kt`, `data/BlueIrisClient.kt` (`group` flag on streams and stills), `video/StreamSizing.kt` (`cameraAt`), `ui/Chrome.kt` (glyph).

### 2026-11-25, Player: the camera's name moved onto the picture
- **Context**: with the group, boxes, sound, full-screen and quality buttons the top bar had no room for the camera's name (it showed "IPC ..." or nothing).
- **Decision**: the name now sits in the label on the picture, with the moment: "IPC Family Room · Live" or "... · Wed Nov 25, 1:58:56 PM" (a group shows its name and camera count). The top bar keeps Back on the left and the buttons on the right. The label is at most about 60% of the picture width and the name ellipsizes before the time does.
- **Impact**: `ui/PlayerScreen.kt` only.

### 2026-11-25, Player: a replaced stream can no longer change the screen (previous camera flashing after a swipe)
- **Context**: now and then after a swipe the previous camera's picture showed for about a second before the new camera's video.
- **Cause**: a race. When the camera changes, the old stream is cancelled but can still report once more while it shuts down (the first-picture report, then one about every second). That report set "video is showing" and cleared the new camera's still picture, uncovering the old camera's last frames in the video view.
- **Decision**: each stream start takes a number; its reports (size, picture shown, frame time, sound, errors, end of stream) are ignored unless it is still the newest stream. This also covers seeks and speed changes.
- **Impact**: `ui/PlayerScreen.kt` only.

### 2026-11-25, M3: group view step 2 (cameras the app hides are left out)
- **Context**: step 1 showed Blue Iris's whole group picture, which holds every camera of the group, including ones the app hides (hidden in Blue Iris or disabled). The web app (`rgUpdate`, `rgLayout`, `rgDraw`) redraws only the shown cameras in a grid of its own.
- **Decision**: when a group holds cameras the app does not show, and at least one it does, the player draws a grid of the shown cameras instead of Blue Iris's picture: the stream is decoded by the GL group view (as in the live grid) and each shown camera is cut out of it into a cell that keeps its camera's own shape. The cells come from the web app's layout rule (`video/GroupGrid.kt`, tested): the column count that shows the cameras biggest, cell shape from the typical camera, a short last row centred. The grid is the "picture" for zoom, panning, swiping and tapping (a tap finds the cell). A paused or scrubbed still is cut up the same way, so a hidden camera never shows there either. The title counts shown cameras only.
- **Layout used**: the stream's own headers, else the camera list's layout (live); for a recording with no layout it can trust, Blue Iris's whole picture is shown in one cell (so, as in the web app, a recording can still show hidden cameras in that case).
- **Not in this mode**: motion boxes (they need the video view the grid does not use), and the swipe's leaving picture is a plain move of the cells, not a snapshot. Swiping between groups shows each group's still in full.
- **Impact**: `video/GroupGrid.kt` (new), `ui/PlayerScreen.kt` (GL view, cells, stills, taps), nothing in the grid or data layer.

### 2026-11-25, M3: flag and protect an alert
- **Context**: next M3 item (after group view). Rules read from the web source (`MK`, `markToggle`, `markButton`, `markOpen`) and UI3's behaviour it mirrors.
- **Decision**: Blue Iris keeps both as bits of the alert's flags: flagged is bit 1 (value 2), protected is bit 2 (value 4). The `update` command sets the whole flags value for one record (`{cmd:"update", path:"@<record id>", flags:<number>}`, record id = the number in the alert's path), so only the chosen bit is changed, with 64-bit arithmetic (flags use bit 31 for wildlife) (`data/AlertFlags.kt`, tested). While watching an alert (reached with the previous/next alert buttons) a small chip with a flag (amber when flagged) and a lock (when protected) sits on the picture; tapping it opens a sheet with two switches, "Flagged" and "Protected", with the web app's explanations and the note that it is saved in Blue Iris so UI3 shows it too. A change shows at once and is undone if Blue Iris refuses it; a permission problem reads "This Blue Iris user isn't allowed to change alerts." Messages: Flagged / Flag removed / Protected: Blue Iris will keep this alert / No longer protected.
- **Not yet**: flag and lock icons in the alert list and the Flagged filter (they come with the alerts tab, M4); the web app's phone layout also puts download in this sheet (download is the next item).
- **Unverified**: the `update` command against this server (read from the web source); Blue Iris user needs permission to change alerts.
- **Impact**: `data/AlertFlags.kt` (new), `data/BlueIrisClient.kt` (`setAlertFlags`), `ui/PlayerScreen.kt` (chip, sheet, state), `ui/AppViewModel.kt`, `ui/Chrome.kt` (flag and lock glyphs).

### 2026-11-25, M3: download an alert as an MP4 (step 1: the whole alert)
- **Context**: next M3 item. Rules read from the web source (`exportArgs`, `downloadAlert`, `dlCancelJob`, `exportName`, the download sheet). The range chooser (any start and end on the timeline, found through Blue Iris's `cliplist`) is step 2.
- **Decision**: from the alert's flag sheet ("Download this alert (MP4)...") a sheet asks how Blue Iris should make the file: re-encode to H.264 (default; encoder profile export 0 to 3; optional overlay) or the original recording, and whether to include audio; the choices are remembered. The export is `{cmd:"export", path:<recording file>, format:1, profile, substream:false, audio, reencode, overlay (only when re-encoding), startms, msec}`: an alert whose flags carry bit 16 starts part-way into its file (start = offset, length = the alert's), otherwise it is cut from the file's start to the alert's end. The app then polls `{cmd:"export"}` every 1.5 s (waiting, preparing with a percentage, done, error), giving up only after 3 minutes without progress or an hour in all, fetches `/clips/<uri>` with the session cookie and saves it to the phone's Downloads folder (MediaStore, no storage permission needed) as `Camera 2026-11-25 1-23-05 PM.mp4`; a half-finished file is deleted on failure or cancel. Cancel asks Blue Iris to drop the job and says honestly if this Blue Iris cannot stop an export. The job runs in the view model, so it carries on if the player is closed; signing out or locking stops it. Progress and the result show in a chip on the player.
- **Unverified**: the `export` command and queue, and `/clips/` with the cookie alone, against this server (read from the web source); the Blue Iris user needs permission to export (a refusal reads "Your Blue Iris user isn't allowed to export clips.").
- **Not yet**: choosing a start and end (step 2); download from a group view (not offered, as in the web app); sharing or opening the file after saving.
- **Impact**: `data/Export.kt` (new, tested), `data/BlueIrisClient.kt` (`startExport`, `exportQueue`, `deleteExport`, `downloadExport`), `ui/AppViewModel.kt` (job, options, state), `ui/DownloadSheet.kt` (new), `ui/PlayerScreen.kt`, `MainActivity.kt`.

### 2026-11-25, Export: "Couldn't find the clip in Blue Iris's export queue" (owner phone test)
- **Context**: both a re-encode and an original-recording download failed with that message after 30 s: Blue Iris accepted the export (the start answered), but the job was never found in its queue.
- **Cause**: not known yet. The web app finds the job by an exact match of the `path` or `uri` Blue Iris answered with; on this server either those are empty or differ in form, or the queue is shaped differently.
- **Decision**: (1) the queue is read before the export starts; (2) the job is matched by exact path or uri, else by record number (`Export/@123.mp4`, `123` and `@123.mp4` are the same job), else, if Blue Iris named nothing usable, as the one entry that was not there before (`data/Export.kt`, `ExportQueue`, tested); (3) if it is still not found, the message now says what Blue Iris answered (the start's path and uri and the first queue entries' path, uri and status: record names only) so the real shape can be seen and the match fixed from it.
- **Impact**: `data/Export.kt`, `ui/AppViewModel.kt`.

### 2026-11-25, Export: Blue Iris removes finished jobs from its queue
- **Context**: the owner's second test (with the new message). Original recording: "queue then held 0 item(s)". Re-encode: progress shown up to 91%, then the same error. The start answered `path='@7689'` and `uri='Clipboard\FrontDrive.20261125_193758715Z-193859Z.5.mp4'`.
- **Finding**: on Blue Iris 6.1.6.1 a finished export is taken out of the export queue (a copy of the original recording finishes at once and is never listed). The web app's rule "not in the queue means keep looking" fails here. The file name Blue Iris gives is a Windows path (backslash).
- **Decision**: a job that has left the queue is finished: after it was seen and is gone for two looks, or has never been listed for three, the file named by the start's `uri` is fetched. The fetch is retried (6 times, 2 s apart) in case the file is still being written. The name's backslashes become slashes in the address (`/clips/Clipboard/FrontDrive....mp4`).
- **Unverified**: that `/clips/<that uri>` serves the file with the session cookie alone (the failure message will say if not).
- **Impact**: `ui/AppViewModel.kt`, `data/BlueIrisClient.kt`.

### 2026-11-25, Push notifications from Blue Iris (analysis, not yet decided)
- **Context**: Owner wants motion and doorbell notifications pushed from Blue Iris, never polled by the app.
- **Options considered**: (1) Blue Iris "Web request or MQTT" alert action posts to a self-hosted or public ntfy topic; the phone receives it through the ntfy app as a UnifiedPush distributor, or ntfy's own app. (2) Blue Iris publishes to MQTT; the app keeps a socket open from a foreground service. (3) The official app's cloud push (FCM through Blue Iris's own servers): proprietary registration, not available to a third-party app. (4) FCM through our own relay: needs a Google project and a server we run.
- **Decision**: leaning to option 1 (UnifiedPush, no Google); to be confirmed with the owner. Planned as its own milestone after M5 (needs the settings screen and alert deep links).
- **Rationale**: the only push path that is true push, needs no polling, no always-on socket of ours, and works with the HTTPS-only/no-cloud rule when self-hosted.
- **Impact**: new milestone in STATUS.md; a Blue Iris alert action must be configured by the owner; notification payload must carry camera and alert path only (no secrets); tapping opens the alert in the player.

### 2026-11-25, Notifications stay in Home Assistant; the app only needs deep links (reverses the ntfy proposal above)
- **Context**: The owner already gets motion and doorbell notifications through Home Assistant and its Android app, driven by Blue Iris MQTT alerts (payload has the camera short name, a memo and the alert record name). The notifications' tap action and buttons open the Blue Iris web interface.
- **Options considered**: build our own push channel (ntfy, MQTT, FCM relay); or keep Home Assistant as the sender and make Sightline openable from a notification.
- **Decision**: no push code in this app. Add deep links (custom scheme) that open a live camera, an alert's clip, or the alerts list. Then change the Home Assistant automations' tap action and URI buttons to those links.
- **Rationale**: push already works; the missing piece is where the tap lands. Much smaller and no new server or battery cost.
- **Impact**: new milestone M4b after M4 (alerts tab gives the alert-by-id screen): link handler in MainActivity (needs sign-in/lock handling and a fallback when the app is not installed), links documented in docs/. Home Assistant automation edits happen in the ha project, not here. Supersedes M5b in STATUS.md.

### 2026-11-25, Download a range: set the ends at the playhead, find the file with cliplist
- **Context**: Step 2 of download: any stretch of a camera's recordings, not only a whole alert (the web app has draggable handles on its timeline).
- **Options considered**: drag handles on the timeline bar; buttons that set the start and end at the playhead, with the band shaded on the timeline.
- **Decision**: buttons ("Set start here", "Set end here") and tapping a time to look at it. The band is shaded on the timeline. The file comes from `cliplist` (or the alert's own file when it holds the start), and the range is cut at the end of that file. Limit 2 hours.
- **Rationale**: the timeline bar already uses one finger to scrub and two to pinch, so handles would fight it; buttons are exact and work on a small screen. Same behaviour as the web app's "set at playhead" path.
- **Impact**: handle dragging can be added later if wanted. `Camera.bps` added (tolerant of text). Group view has no download.

### 2026-11-25, Download location is a setting (phone folder picker)
- **Context**: The owner wants to choose where downloaded MP4s go. Until now they always went to the Downloads folder.
- **Options considered**: a typed path (not possible: Android blocks direct file paths); the system folder picker with a kept permission (Storage Access Framework); a fixed sub-folder of Downloads.
- **Decision**: Settings > Downloads > "Save downloads to" opens the system folder picker; the app keeps permission to that folder and saves there. "Use the Downloads folder again" goes back. Folder permission is checked before Blue Iris is asked to make a clip, so a lost folder fails at once with a message instead of after a long export.
- **Rationale**: the only supported way for an app to write to a folder the owner picks, no storage permission needed. Android refuses the top of storage and the Download folder itself, so Settings says to make a folder inside it.
- **Impact**: `AppViewModel.setDownloadFolder`, `saveToFolder`; pref `download_folder` holds the folder's address (stays on the phone). The saved message names the folder.

### 2026-11-25, The folder picker must not lock the app
- **Context**: Owner reported the app crashing on "Save downloads to". No crash log was available (no phone connected over adb). The first suspect was that opening the picker stops the app, and coming back with "Lock the app: Always" locked it and dropped the session.
- **Decision**: the app notes when it opens the picker and ignores that one return (within 10 minutes) for the lock check. Opening the picker is also guarded: a failure shows a message naming the error instead of crashing.
- **Impact**: `AppViewModel.openingPicker`; if it still crashes the log is needed (`adb logcat -d AndroidRuntime:E`).

### 2026-11-25, Newer Fragment library: the folder picker failed on request codes
- **Context**: "Save downloads to" failed with `IllegalArgumentException: Can only use lower 16 bits for requestCode`. `androidx.biometric` 1.1.0 pulled in Fragment 1.2.5, whose `FragmentActivity` rejects the request codes of the activity result API (used by `rememberLauncherForActivityResult`).
- **Decision**: depend on `androidx.fragment:fragment` 1.8.9 directly, so the newer one wins.
- **Impact**: `libs.versions.toml`, `app/build.gradle.kts`. Any later use of the activity result API (pickers, permissions) works. The folder-picker failure message now shows the error text, which is kept as a diagnostic.

### 2026-11-25, Pull to refresh replaces the header's refresh button
- **Context**: The header had a Refresh button that reloads the camera list. The list already updates by itself (status every 2 s), so the button was rarely needed and unclear.
- **Decision**: remove it and pull down from the top of the camera grid instead (Material `PullToRefreshBox`). It reloads the camera list, same as before. The empty "No cameras" screen scrolls too, so it can be pulled as well.
- **Impact**: `SightlineHeader` lost its refresh parameter; `Glyph.Refresh` is now unused. Differs from the web app, which has a button.

### 2026-11-25, Restart the decoder when a recording changes picture size
- **Context**: Some cameras record a small sub stream until something happens and the full one after, so a recording can change picture size part-way (the web app starts a fresh player for this). Our decoder was set up once from the stream header.
- **Decision**: on each key frame the sequence parameter set (`sequenceParameterSet`) is compared with the last one; when it differs, the decoder is stopped and a new one started on the same surface and connection. The new picture size comes from the decoder's output format (crop-aware) and is passed to the player (`onSize`), but only after a restart, since at the start the header already gives it and some decoders report a padded height.
- **Impact**: `video/LivePlayer.kt`, `video/StreamSizing.kt`. Untested on a real size change (the owner's cameras may not do it).

### 2026-11-25, Older Blue Iris: rewind from recording files
- **Context**: Blue Iris versions without the `timeline` command cannot play or show recordings by camera and time. The web app falls back to the recording-file list (`cliplist`) and plays files (`/file/clips/<file>?time=`). The owner's Blue Iris (6.1.6.1) has the timeline, so a switch is needed to test it.
- **Decision**: a failed `timeline` reply (a new `CommandRefusedException`, told apart from a lost session or a denial) switches the session to file rewind; Settings > Developer > "Rewind from recording files" forces it. In file mode: the timeline bar and the calendar come from the files plus the camera's alerts (`RecordingIndex.timeline`); playback finds the file for the moment (or the next one across a gap) and plays it like an alert clip; at a file's end it carries on with the next file; pictures while paused come from `/file/clips/<file>?time=&w=&q=` (`clipStill`). Groups still use the timeline stream (a group has no files).
- **Rationale**: same behaviour as the web app; the switch lets the owner see it on a server that has the timeline.
- **Impact**: `data/ClipList.kt` (`RecordingIndex`), `BlueIrisClient` (`clipStill`, `CommandRefusedException`), `AppViewModel` (`timelineFor`, `fileAt`, `recordedStill`), `PlayerScreen` (file lookup in the stream effect). Unverified on a real old server; the `/file/clips/` still parameters (`w`, `q`, no `h`) come from the web app and are unconfirmed.

### 2026-11-25, Dropped: file rewind for older Blue Iris, and PTZ (reverses the entry above)
- **Context**: The owner will not use a Blue Iris older than version 6, which has the timeline command, so rewinding from recording files is never needed. The owner also has no PTZ cameras.
- **Decision**: the file-rewind fallback was removed (it was never committed; the code and tests were reverted, nothing of it is in git history). PTZ is skipped. Both stay in the parity matrix as "Dropped", in case a later owner needs them. This ends M3.
- **Impact**: none on shipped code. The Developer switch "Rewind from recording files" no longer exists. The range download (which uses `cliplist`) is unaffected. The "PTZ" label on camera tiles is just Blue Iris's own flag.

### 2026-11-25, M4 in two slices: the list first, custom labels and Auto mode after
- **Context**: The web app's Alerts tab is large (filters from settings, Auto mode with a 3-week label scan, ranges, camera filter, flags). The owner asked to start M4.
- **Decision**: slice 1 (built): the list (thumbnails from `/thumbs/@<id>`, tags, camera, time, duration, flag and lock icons, day headings), built-in filter buttons (All, People, Vehicles, Animals, Flagged; long-press to combine; filtered on the phone from the full list, as the web app does), camera or group menu, time range (last hour, today, yesterday, last night, one day), loads a day at a time going back with the web app's rules (100-alert cap continues from the oldest alert; "Look further back" after a quiet week; one retry after a refused list). Tap plays the alert in the player (the player starts at the alert); long-press flags or protects. Slice 2: custom labels, Auto mode and the label scan, the Alert filters settings sheet, a custom start and end time, and the colour legend.
- **Rationale**: slice 1 is what is used every day and is testable now; slice 2 needs more screens and a background scan.
- **Impact**: `AlertFilters`, `AlertsFeed` (state kept in the view model so the list and scroll position survive opening the player), `AlertsScreen`, `BlueIrisClient.thumbnail`; the home tab and the list's scroll state moved up into `MainActivity`; `PlayerScreen` takes an `initialAlert`. The Alerts tab shares flag changes with the player. Android-specific: pull down on the list to reload.

### 2026-11-25, M4 slice 2: Alert filters (Choose and Auto), labels and the 3-week scan
- **Context**: Slice 1 had fixed filter buttons. The web app lets the owner pick the buttons, add labels, or switch to Auto (All, always-shown pins, then the most common detections of the last 3 weeks).
- **Decision**: built the same rules. `AlertFilterConfig` (mode, shown, pins, count 0 = fit or 1 to 8, typed labels, last scan result) is saved as one JSON string in the app preferences (`alert_filters`). `AlertFilterSettings` runs the scan: 21 days, one request at a time, a refused day is skipped, hidden cameras ignored, labels counted by normalised word (so "UPS", "ups" and "Dogs/dog" are one), ONVIF names read as People/Vehicles/Animals or their own label, motion ignored, AI flag bits 26/27 count as People/Vehicles. Auto refreshes the scan after 6 hours when the Alerts tab opens; the sheet rescans if the last scan is over 10 minutes old. An "Edit" button at the end of the filter row opens the sheet (Choose/Auto, built-ins, labels with counts, add/remove own label). A selected button that stops being shown is dropped from the selection. The first-run tip is skipped.
- **Android differences**: "Fit screen" in Auto means up to 5 recent buttons (the row scrolls on a narrow phone) instead of measuring the width. The default set stays All, People, Vehicles, Animals, Flagged (the web default has no Flagged).
- **Note**: the plural rule strips a trailing "s" from three-letter words, so "ups" and "up" are the same label; this matches the web app.
- **Impact**: `data/AlertFilterSettings.kt` (new), `ui/AlertFiltersSheet.kt` (new), `AlertsFeed.retainFilters`, `AlertFilters.vehicleWord`, `AlertsActions` (+`retainFilters`, `filterSettings`), view model (`alertFilters`, cleared on sign-out). Still to do in M4: custom start/end time and the colour legend.

### 2026-11-25, M4: custom start and end time for the alert range
- **Context**: Slice 1 only offered whole days. The web app has a date with From and To times; a To before From runs past midnight (10 PM to 2 AM).
- **Decision**: "Pick a day and time…" in the time sheet opens a dialog with Date (calendar, nothing later than today), From and To (clock pickers, 12 or 24 hour as the phone is set). `AlertRange.window` builds the range (From at the start of its minute, To at the end of its minute, +1 day if To is not after From). Full day = the old "pick a day".
- **Impact**: `AlertsScreen.kt` (`CustomRangeDialog`), `AlertFilters.kt` (`AlertRange.window`). Only the colour legend is left in M4.

### 2026-11-25, M4b: deep links for notification buttons
- **Context**: The owner's Home Assistant notifications open the Blue Iris web page (`ui3.htm?maximize=1&cam=...`). They want them to open this app instead. HTTPS app links would need a verification file on the Blue Iris server's host, which the owner does not control that way.
- **Decision**: a custom scheme, `sightline://`. `sightline://live/<camera>` (live view), `sightline://alert/<camera>/<alert id>` (that alert in the player), `sightline://alerts` (Alerts tab), `sightline://live` (camera grid). The parser (`DeepLink`) only accepts those, with letters, digits, space, dot, dash, underscore and `@` in the parts, and carries no address or credentials, so a link can only choose a screen. The activity is `singleTask`, so a link reaches the running app (`onNewIntent`). A link waits until the app is unlocked and the camera list has loaded, then opens; an alert is found by asking Blue Iris for that camera's alerts from the last 3 days and matching the record id; if it is not found (or the camera is unknown) a message appears and the live view (or the camera grid) shows instead. The player is keyed on camera and alert, so a link to another alert on the same camera restarts it.
- **Unverified**: that the Home Assistant companion app launches a custom-scheme `clickAction`/`URI` as an intent (believed yes). The owner tests one notification before any automation is changed. Alerts older than 3 days open as the live view.
- **Impact**: `data/DeepLink.kt`, `MainActivity` (`pendingLink`, `onNewIntent`), `AppViewModel.findAlert`, manifest. Interface listed as connection 17 in `C:\dev\_docs\WORKSPACE.md`. The HA automations still use the web links; changing them is the next step and belongs in an ha session.

### 2026-11-25, Deep links: Home Assistant needs the `deep-link://` wrapper
- **Context**: First test with `clickAction: "sightline://live/FrontDoor"`: the HA app read it as a page inside Home Assistant and showed "Unable to connect". `intent://...` did nothing (tested on the old APK), `app://<package>` opened the app at the grid.
- **Decision**: in notifications write `deep-link://sightline://live/<camera>` (the HA companion app's documented wrapper for Android). Verified: on the new APK it opened the FrontDoor live view. The first failures of the `deep-link://` and `intent://` forms were because the phone still had a build without the link handling. Not yet verified: the alert link, the Alerts-tab link and the `URI` action button (expected to take the same form).
- **Impact**: connection 17 in `C:\dev\_docs\WORKSPACE.md` now states the wrapped form. The HA automations should use `deep-link://sightline://...` and keep the web link on the second Android phone and iPhones (do that in an ha session). The app code needed no change.

### 2026-11-25, Deep link fix: do not clear the link before the alert lookup
- **Context**: The alert link only opened the app. The link handler cleared `pendingLink` first; that changed the effect's key, restarted the effect and cancelled the suspended `findAlert` call. The live link has no suspend call, so it finished in time.
- **Decision**: clear the pending link only after the screen has been chosen.
- **Impact**: `MainActivity.kt` only. Lesson: do not change an effect's own key from inside it before suspending work is done.

### 2026-11-25, M5: pop up cameras on detection
- **Context**: The web app can pop a camera up large, with live video, when it detects People, Vehicles, Animals or one of the owner's labels. It is opt-in, lasts 20 s, pauses while pointed at, opens the camera on a click, and only works for cameras in the group on screen while the grid is showing.
- **Decision**: same rules. `PopupConfig` (kinds person/vehicle/animal plus own labels, JSON in preferences `popup_config`) and `PopupTracker` (data layer, tested): an alert counts once (by its record path), only if under 30 s old, only for a camera in the group shown, never lets an older alert replace a newer one that is showing, and the newest of one poll wins. Plain motion never pops up. The grid's existing 4-second recent-alert poll now also runs when pop-ups are on. `PopupBox` overlays the grid: coloured by the alert class, live video (when Live video in tiles is on) over a picture refreshed every second, a countdown bar from 20 s, holding a finger on it pauses the countdown (the touch version of the web app's pointing), a tap or "Open camera" opens the camera, "Close", a newer detection on the camera restarts the 20 s, and it closes when the app goes to the background. Settings > Live view > "Pop up on detection" opens a sheet (People, Vehicles, Animals switches, labels from the same 3-week scan as Alert filters with counts, add your own label).
- **Differences**: no sound or vibration (the web app has none); the pop-up is a card over the grid, not a full overlay.
- **Impact**: `data/Popup.kt` (new), `ui/PopupBox.kt`, `ui/PopupSheet.kt`, `RecentAlert.path`, `SettingsScreen`, `CameraListScreen` (LiveTab), view model (`setPopup`, session field `popup`). Untested on a phone.

### 2026-11-25, Pop-ups: a master on/off switch that keeps the choices
- **Context**: The owner wanted a plain way to turn pop-ups on and off. The web app turns them off by clearing the choices.
- **Decision**: `PopupConfig.enabled` (default on, so a choice saved before this stays on). The top switch in the sheet ("Pop up cameras") turns pop-ups off without losing the chosen kinds and labels, and the settings row reads "Off (People, UPS)". Choosing a kind or label while off turns pop-ups on again. The old "Turn all off" button was removed.
- **Impact**: `data/Popup.kt`, `ui/PopupSheet.kt`, `PopupTest`.

### 2026-11-26, M5: Server status screen (without "footage kept" for now)
- **Context**: The web app's Server status shows Blue Iris's CPU, memory, recording space, frame rate, uptime, web connections, warnings and profile, each disk, and per-camera frame rate, bit rate and how far back each camera's recordings go.
- **Decision**: Settings > Server > Server status opens a full screen refreshed every 2 s while it is in front (the `status` command plus the camera list; one request each). Tiles: CPU (and GPU), Memory (and the computer's load), Recordings (percent of what Blue Iris may use, with a bar that turns amber at 75% and red at 90%), Frame rate (total fps, camera count, Mbps), Uptime (and since when), Connections, Warnings, Profile (schedule, held, signal colour). Each disk is drawn the way Blue Iris does (recordings, over allocation, allocated but free, other files, free) with a note when over allocation or short of free space. A camera table lists fps and Mbps per camera (BPS is bytes per second, so x8), hiding groups and hidden cameras, and shows "disabled" or "no signal". The numbers follow the web app (`ServerNumbers`: sizes, uptime) and are unit tested against the real `status` sample.
- **Left for later**: the "Footage kept" tile and per-camera "Stored" column (a long timeline request per camera, at most every 10 minutes), and tapping Warnings to open the system log (the log screen is the next item).
- **Impact**: `data/ServerStatus.kt`, `ui/ServerStatusScreen.kt`, `BlueIrisClient.serverStatus`, `AppViewModel.serverView`, `SettingsScreen` (new Server section), `MainActivity` (`showServer`). Untested on a phone.

### 2026-11-26, M5: System log screen
- **Context**: The web app shows Blue Iris's own log (`log` command, newest first, level icons, repeat counts, filters, search). Blue Iris keeps it to admin users: the `test` user gets "Access denied" (seen in the sampled reply).
- **Decision**: Settings > Server > System log, and the Warnings tile on Server status (opens on "Warnings & errors"). Same rules as the web app: sorted newest first; warnings, errors, alerts and questions count as problems; filter buttons show their counts; search looks in the source and the message; at most the newest 600 lines are drawn; a repeat count (x3) shows when over 1; today's lines show the time only. A refused log shows Blue Iris's reason and says it may be limited to admin users (an `AccessDeniedException` becomes `LogResult.Denied`, other refusals `Failed`). Level icons are coloured dots with a glyph (i, !, x, check, ?), not the web app's drawn icons.
- **Impact**: `data/SystemLog.kt`, `ui/LogScreen.kt`, `BlueIrisClient.systemLog`, `AppViewModel.loadLog`, Settings and Server status wiring, `MainActivity` (`showLog`). Only checked against the denied reply: the owner must try it with an admin Blue Iris user to see real lines.

### 2026-11-26, M5: Activity overview
- **Context**: The web app's Activity sheet shows when and where alerts happen: alerts per day stacked by kind for 7, 14 or 30 days, a camera by hour-of-day map, People/Vehicles/Animals filter, tap a camera to see its alerts.
- **Decision**: Settings > Server > Activity, a full screen with the same pieces. `ActivityFeed` reads the alert list one day at a time, four days at a time, keeps the result for 10 minutes per choice of days, skips a day Blue Iris refuses, ignores hidden cameras and counts each alert once (by its first label, via `AlertTags`). `ActivityReport` builds the per-day stacks (today last) and the camera-by-hour counts, sorted by busiest camera; the summary names the busiest camera, busiest and quietest hour. Tapping a camera (or "All cameras") closes Settings, opens the Alerts tab on that camera and the chosen type.
- **Difference (an improvement)**: a day that comes back with 100 alerts, the most Blue Iris sends at once, is read on from its oldest alert; the web app counts only those 100.
- **Impact**: `data/Activity.kt`, `ui/ActivityScreen.kt`, `AppViewModel.activity` (cleared on sign-out), Settings row, `MainActivity` (`showActivity`). Untested on a phone.

### 2026-11-26, M5: About, and "Open Sightline on the web" (not UI3)
- **Context**: The web app's Settings has "Open full Blue Iris UI (UI3)" and an About sheet. The owner asked that the Android link open the Sightline page on the web, not UI3.
- **Decision**: Settings > Account has "Open Sightline on the web", which opens the sign-in address plus a file name in the phone's browser; the file name is a setting ("Web page file name", default `sightline.htm`, because the web Sightline is a single html file the owner hosts on the Blue Iris server; the owner must check the real name). `WebLink` accepts only a plain relative path (letters, digits, dot, dash, underscore, slash; no `..`, `//`, `?`, `#`, scheme), so the link can never leave the server or carry data. Nothing is sent but the address; the owner signs in on the web page itself. No UI3 link is offered. About (Settings > Account) shows versions, the Blue Iris server and version, camera count, signed-in name, saved sign-in, links (this project, releases, issues, the web project it follows), "Copy diagnostics" (app and Blue Iris versions, Android version and phone model, camera counts, settings; no names, addresses or passwords, tested), and the unofficial / private / view-only / free software / credits text adapted for Android.
- **Left out**: "recent errors" in the diagnostics (the app keeps no error list yet).
- **Impact**: `data/WebLink.kt` (`WebLink`, `Diagnostics`), `ui/AboutScreen.kt`, Settings rows and dialog, `AppViewModel` (`webPage`, `setWebPage`, `webPageUrl`), `MainActivity` (`openInBrowser`, `showAbout`). Untested on a phone.

### 2026-11-26, M5: Colour legend
- **Context**: The web app has a Color legend sheet explaining the alert colours, timeline colours, camera dots and tile borders.
- **Decision**: Settings > Live view > Colour legend, a plain screen with the same four groups and wording (Alerts, Player timeline, Cameras, Camera tiles), drawn with the app's own colours (alert class colours from the theme, the recorded-video blue of the timeline, the online green and trigger red of the tiles). The sample for "Motion box" says "on camera tiles" since the app draws the boxes there.
- **Impact**: `ui/ColorLegendScreen.kt`, Settings row, `MainActivity` (`showLegend`). Closes the M4 leftover. Untested on a phone.

### 2026-11-26, M5: Show hidden cameras and Groups on Live screen; Alert preview panel left to M6
- **Context**: The rest of the web Settings sheet: "Show hidden cameras", "Groups on Live screen" and "Alert preview panel".
- **Decision**: "Show hidden cameras" (Settings > Live view): a switch that makes cameras marked Hidden in Blue Iris appear on the grid, in the Alerts tab and its camera menu, in the player's camera list and in pop-ups and counts (disabled cameras never show). Turning it on or off forgets the loaded alerts, label counts and activity so they are read again for the new set of cameras. "Groups on Live screen": a sheet with a switch for each Blue Iris group; the hidden ones are stored (so groups added later show by default), at least one stays on, and with one shown the button row is hidden. "Alert preview panel" is the web app's side panel for wide screens with a mouse (it is hidden on phones even there), so it moves to the M6 tablet work.
- **Impact**: `Camera list` helpers (`shownGroups`), `SessionState.showHidden/hiddenGroups`, `GroupsSheet`, Settings rows, `CameraListScreen`, `AppViewModel` (`setShowHidden`, `toggleGroup`). Untested on a phone.

### 2026-11-26, M5: Footage kept on Server status (finishes M5)
- **Context**: The web app's Server status also shows how far back each camera's recordings go, from one wide, coarse timeline request per camera. This was left out of the first Server status build.
- **Decision**: same method. `RetentionFeed` asks `timeline` for each running camera in turn (400 days, one hour per step), keeps the answer 10 minutes, skips a camera Blue Iris will not answer for. `Retention.of` counts back from the newest recording until a gap of more than 2 days (so an old protected clip does not make a camera look as if it keeps months) and marks cameras that also have older recordings after a gap with `*`. Server status gets a "Footage kept" tile (the range across cameras, in hours or days) and a "Stored" column in the camera table, with the same explanatory note.
- **Impact**: `data/Retention.kt` (new, tested), `ServerStatusScreen`, `AppViewModel.retention` (cleared on sign-out and when Show hidden cameras changes). Untested on a phone; reading a few hundred days of recordings per camera may take a moment on a big server.

### 2026-11-26, Alerts list scrolled to the end of the day after a filter change (fix)
- **Context**: Tapping People, Vehicles and so on made the Alerts list land on the oldest alert of today instead of the newest. Cause: the list's bottom "footer" item had a fixed key. While the list was empty after the filter change it was the only item, so Compose held on to it, and alerts arriving above it pushed the view down to the end of the loaded day.
- **Decision**: the footer has no key now, and the list goes back to its top whenever it is started again (a new filter, camera or time range; `AlertsUi.reloads` counts these).
- **Impact**: `ui/AlertsScreen.kt`, `data/AlertsFeed.kt`. Needs checking on the phone (a unit test covers the counter, not the scrolling).

### 2026-11-26, M6 start: CI, CodeQL and Dependabot
- **Context**: The repo is public and had no automation. M6 is release hardening.
- **Decision**: three GitHub files, all read-only for the code. `ci.yml`: on pushes to main and pull requests, validates the Gradle wrapper, sets up Java 17 and the Android SDK licences, runs `./gradlew test assembleDebug lintDebug`, keeps the reports for 7 days only if it fails; permission `contents: read`. `codeql.yml`: Kotlin/Java code scanning on pushes to main, pull requests and weekly (Monday), built manually with `assembleDebug` (tests and lint skipped there); permissions `contents: read`, `security-events: write`, `actions: read`. `dependabot.yml`: weekly Monday pull requests for the Gradle version catalog (minor and patch grouped, major separate) and for the GitHub Actions themselves, at most 5 open each; nothing merges itself.
- **Not done**: actions are pinned by version tag (`@v4`, `@v3`), not by full commit hash, because I cannot look up hashes from here; the Dependabot actions update will keep the tags current, and pinning by hash can be done later. No branch protection or required checks yet (the owner sets those on GitHub).
- **Unverified**: the workflows have not run (they only run once pushed). The first run may need a fix, for example if the runner lacks the SDK platform 37 the build asks for.
- **Impact**: `.github/` (new); README badge. Release signing, tablets and accessibility are still to do in M6.

### 2026-11-27, First CI and CodeQL runs
- **Context**: Pushed a5f88e3 (CI, CodeQL, Dependabot). Both workflows failed in about 20 s.
- **What failed and why**: `android-actions/setup-android@v3` accepts the SDK licences and then installs its default package list, `tools platform-tools`; the package `tools` no longer exists, so the step exited with an error. Fix (49628d1): ask for `platform-tools` only, in both workflows.
- **Result**: after the fix CI passed (about 5 minutes: wrapper check, tests, debug build, lint) and CodeQL passed (the Kotlin analysis ran and uploaded). The SDK platform 37 the build needs was fetched by Gradle without trouble.
- **Warnings seen**: Node 20 deprecation for the actions (they run on Node 24 anyway); `setup-java@v4` is deprecated in favour of v5; CodeQL Action v3 is deprecated from December 2026 in favour of v4. Dependabot's first pull request for the actions group (#1) proposes these bumps.
- **Dependabot**: it opened two pull requests at once: #1 (GitHub Actions, 5 updates) and #2 (Gradle minor and patch, 7 updates). #2's checks failed only because its branch was cut before the setup fix; it needs a rebase onto main. Neither is merged: each needs a look first (a Gradle bump can change the build or app behaviour and must be tried on the phone).

### 2026-11-27, M6: release signing and the release workflow
- **Context**: Releases need a signed APK. The release build type was shrunk with R8 but had no signing and had never been built. The key must never be in the public repo, and a release is only made when the owner says "publish it".
- **Decision**: (1) `app/build.gradle.kts` signs the release build from environment variables (`SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`) or from a git-ignored `keystore.properties`; without them the release build stays unsigned. `-PreleaseDebugSigned=true` signs a release build with the debug key so the shrunk build can be tried on a phone (testing only). (2) `.github/workflows/release.yml` runs when a `v*` tag is pushed: checks the four secrets exist, decodes the key to a temporary file, runs the tests, builds the signed release with the version from the tag (`-PappVersion`), checks the signature with `apksigner`, and creates the GitHub release with the APK and a SHA-256 file using the `gh` CLI (no third-party release action); only that job has write access. (3) `docs/release.md` tells the owner how to make the key (outside the folder, backed up), set the four secrets, and publish. Claude never creates or reads the key.
- **Checked**: the release build compiles with R8 (4.6 MB); a release build signed with a throwaway key via the environment variables verified with `apksigner` and took its version from `-PappVersion`; the debug-signed test build was produced. The throwaway key was deleted. **Not checked**: the release workflow itself (it needs the owner's key and a tag), and the shrunk release build running on a phone, which is the main risk before a first release.
- **Impact**: `app/build.gradle.kts`, `.github/workflows/release.yml`, `docs/release.md`. Still to do in M6: tablet layouts (with the Alert preview panel), accessibility, the two hidden developer switches.

### 2026-11-27, Release signing brought in line with dockge-for-android
- **Context**: The owner asked how dockge-for-android handles signing keys and wanted the same. Its method (read from its `release.yml`, `app/build.gradle.kts` and DECISIONS; no key or secret was opened): one PKCS12 key (RSA 4096, about 100 years) made locally outside the repo and backed up offline, kept as four GitHub secrets, signed in CI only on version tags, and every release checked against a pinned certificate fingerprint.
- **Decision**: adopted what Sightline lacked. The release workflow now: only runs for tags like `v1.2.3`; refuses a tag that is not on main, or without `docs/releases/<tag>.md` notes; deletes the key from the runner even on failure; checks with `apksigner` for a v2 or v3 signature and that the signer certificate SHA-256 equals `EXPECTED_CERT_SHA256` pinned in the workflow (it fails while that is empty, so the owner pins it once the key exists); checks with `aapt2` that package, version name and version code match the tag; publishes the notes plus the APK and certificate checksums. A release build signed with the debug key (`-PreleaseDebugSigned=true`) gets the id `com.feerlessleadr.sightline.rtest` and a `-rtest` version, so it installs beside the debug build and can never stop the real release installing (this fixes a hazard in my first version, where it used the real id). CI now also runs `assembleRelease`, so R8 problems show on every push. Rule: a tag that produced a public release is never moved or deleted; a bad release is replaced by the next patch version.
- **Kept from before**: signing also reads a git-ignored `keystore.properties` for local use (dockge reads only the environment); the version code is `major*1000000 + minor*1000 + patch` (dockge uses 10000/100).
- **Unverified**: the release workflow has not run. First real run needs: the key made, four secrets set, the fingerprint pinned, `docs/releases/v0.1.0.md` written, and the owner's "publish it".
- **Impact**: `.github/workflows/release.yml`, `.github/workflows/ci.yml`, `app/build.gradle.kts`, `docs/release.md`, `docs/releases/README.md`.

### 2026-11-27, Release key made and its certificate pinned
- **Context**: The owner made the Sightline release key (`C:\keystore\sightline-android.jks`, alias `sightline`, PKCS12, RSA 4096) and set the four `SIGNING_*` GitHub secrets (all four confirmed listed). Claude saw none of them.
- **Decision**: the certificate SHA-256 the owner read from the key, `f35be2138ec75521a0d9a76a1f6c3cb6b36a37be36263823ad3ade394fb00d8b` (public), is pinned as `EXPECTED_CERT_SHA256` in `.github/workflows/release.yml`. The workflow fails for any APK signed with another certificate.
- **Impact**: `release.yml`. Still needed before the first release: `docs/releases/v0.1.0.md` (written when the owner says publish it), the owner's check of the shrunk `.rtest` build on the phone, and the explicit "publish it".

### 2026-11-27, v0.1.0 published
- **Context**: The owner said "commit and push, then publish it". CI on main (including the shrunk release build) had passed first; the owner had not yet tried the `.rtest` build on a phone and chose to publish anyway.
- **Decision**: pushed the signing work (0b834ed) and the notes `docs/releases/v0.1.0.md` (3f57737), then tagged `v0.1.0` on 3f57737. The Release workflow passed every check (key present, pinned certificate, v2 or v3 signature, package, version name `0.1.0`, version code `1000`) and published the release with `sightline-android-v0.1.0.apk` (4.6 MB) and its `.sha256`. APK SHA-256 `54387b91a3e45ca853a131464c3b94868dfdba473b7d643b116d02cf3860de0f`; signing certificate `f35be2138ec75521a0d9a76a1f6c3cb6b36a37be36263823ad3ade394fb00d8b`.
- **Rule from now**: the `v0.1.0` tag made a public release, so it is never moved or deleted; a problem is fixed in `v0.1.1`.
- **Unverified**: the signed release APK on a phone. If R8 broke something that debug does not show, it will show now; fix it in 0.1.1.

### 2026-11-27, M6: developer switch, accessibility, large screens and the alert preview panel
- **Context**: The last M6 bullets. The web app's `?grpdebug=1` and `?cliprewind` switches, a TalkBack pass, and the tablet work including the web app's alert preview panel (which the web app shows only on wide screens with a mouse).
- **Decisions**: (1) Developer switch: Settings > Developer > "Show group layout" shows Blue Iris's group picture as it is in the player's group view with each camera outlined (green shown, red hidden or disabled) and a line of sizes; it follows zoom and pan. `cliprewind` is not needed since the file-rewind fallback was dropped. (2) Accessibility: screen-reader descriptions, roles, selected states and actions on camera tiles, alert cards, filter buttons, the timeline (step actions instead of dragging), pop-up, Activity map and chart, and the legend. Not changed: no screen-reader way to reorder tiles; the faintest grey text is below the usual contrast target. (3) Large screens: text pages (Settings, Server status, System log, Activity, About, Legend) are held to 720 dp and centred. (4) Alert preview panel: at 840 dp or wider, the Alerts tab is a list plus a preview panel; the panel plays eight still pictures spread over the alert (loaded one at a time, kept for the last six alerts, shown 220 ms each with a pause at the end), from the recorded-still request the player uses. A mouse pointer entering an alert chooses it (after the web app's mouse-over), and a tap chooses it on touch; tapping the picture or Open opens the player; on phones tapping still plays at once. Setting: Alert preview panel (shown only on wide screens, on by default). The web app's "Smooth" video preview is not built.
- **Impact**: `PlayerScreen` (`groupDebug`), `AlertPreviewPanel.kt`, `AlertsScreen`, `Layout.kt` (`readable()`), semantics in `LiveGrid`, `TimelineBar`, `PopupBox`, `ActivityScreen`, `ColorLegendScreen`, Settings rows, view model. All untested on devices (no tablet or TalkBack run yet).

### 2026-11-27, v0.1.1 published
- **Context**: The owner said "commit, push and publish if needed". Since 0.1.0 the app had gained user-visible work (screen reader support, wide-screen layouts with the alert preview panel, the developer group-layout switch), so a release was needed.
- **Decision**: pushed the M6 code (49f2ef1) and the notes `docs/releases/v0.1.1.md` (8960918); CI on main passed first (tests, debug build, shrunk release build, lint). Tagged `v0.1.1` on 8960918. The Release workflow passed every check and published `sightline-android-v0.1.1.apk` (4.6 MB) with its checksum; APK SHA-256 `87b6a64b9c74488dbac622bff7ee3e28c989a617ad54fe342a180197eb3530df`, same pinned signing certificate as 0.1.0, so it installs over 0.1.0.
- **Not merged**: the two Dependabot pull requests (1 for the GitHub Actions, CI green; 2 for Gradle libraries, needs a rebase and a phone check). Waiting for the owner's go-ahead.
- **Unverified**: TalkBack and tablet behaviour on real devices.

### 2026-11-27, New launcher logo: the lens
- **Context**: The first logo was a plain eye outline. The owner asked for a redesign and was shown six options (lens, eye with play, playhead, viewfinder, S monogram, sightline beam).
- **Options considered**: All six, drawn on the existing dark navy background.
- **Decision**: Option 1, the lens: two cyan rings, a red live dot and a small highlight. A separate single-colour version (`ic_launcher_monochrome.xml`) is used for themed icons.
- **Rationale**: Reads clearly at small sizes and the red dot matches the app's live colour.
- **Impact**: `ic_launcher_foreground.xml`, new `ic_launcher_monochrome.xml`, `mipmap-anydpi/ic_launcher.xml`. Shipped in v0.1.2.

### 2026-11-27, Repository security hardening
- **Context**: Owner asked to make sure every sensible protection is applied before going further.
- **Decision**: Added a `protect-release-tags` ruleset (no delete, update or force-move of `v*` tags); turned on immutable releases (the release workflow already attaches files at creation); fork pull request workflows need approval for all outside contributors; pinned every GitHub Action to a commit hash (Dependabot keeps them current) and turned on "require actions pinned to a full commit hash".
- **Options considered, not done**: requiring pull requests or passing CI on `main` (would block the owner's direct pushes); secret scanning generic patterns and validity checks (would not enable through the API, possibly a plan limit).
- **Impact**: `.github/workflows/*`, repo settings. New third-party actions must be added by commit hash.

### 2026-11-27, Header logo and text colour (v0.1.3)
- **Context**: The in-app header still drew the old ring-and-dot logo, and the server name was black on the dark header because text outside a Surface has no content colour.
- **Decision**: `SlLogo` now draws the lens (same as the launcher icon). `SightlineTheme` provides `LocalContentColor = Sl.Text` so uncoloured text is light everywhere; the header's server name also sets its colour.
- **Impact**: `Chrome.kt`, `Theme.kt`. Released as v0.1.3.

### 2026-11-27, Security review fixes (v0.1.4)
- **Context**: The owner asked for a full security review. No critical or high findings. Two were fixed; the owner chose to fix only these.
- **Decision**: (1) Removed the `QueryToken` probe variant from "Test video start": it put the session token in the URL, which proxy and server logs keep, against the rule that tokens never go in URLs. (2) Added `requireSafePath` in `BlueIrisClient`: camera names and file paths from Blue Iris (live, recorded, still, clip file, export download) are refused if any `/` or `\` separated part is `.` or `..`, because OkHttp resolves those and a hostile reply could redirect the authenticated request to another path on the same host. Tests added.
- **Not done (owner's call)**: hide camera pictures in the Recents card and screenshots (FLAG_SECURE or `setRecentsScreenshotEnabled(false)`); wording check on the "Never lock" warning; certificate pinning; check of the stream header length limit at `BlueStreamReader.kt:43`; dependency vulnerability check.
- **Impact**: `Settings.kt`, `BlueIrisClient.kt`, `BlueIrisClientTest.kt`, `docs/releases/v0.1.4.md`. Released as v0.1.4.
