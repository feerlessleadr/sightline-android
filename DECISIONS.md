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
