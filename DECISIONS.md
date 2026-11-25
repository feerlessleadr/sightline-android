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
