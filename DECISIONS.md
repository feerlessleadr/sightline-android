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
