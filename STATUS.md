# STATUS

Last updated: 2026-11-24

## Current state
- Public repo feerlessleadr/sightline-android is live and secured (secret scanning, push protection, private vulnerability reporting, Dependabot alerts and security updates, `main` ruleset with no deletion or force push).
- Owner approved the stack, GPL-3.0 and HTTPS-only access (DECISIONS.md, 2026-11-24).
- API notes in `docs/blue-iris-api.md`: login, cookie-only stills, `camlist`/`status`/`alertlist` VERIFIED against Blue Iris 6.1.6.1.
- **Milestone 1 code written 2026-11-24, not yet committed:** Gradle project (AGP 9.4.1, Kotlin 2.4.21, Compose, minSdk 26, same versions as Embidextrous), HTTPS-only network config, `BlueIrisClient` (challenge/response login, session cookie only, no redirects, 8 MB reply cap), `camlist` parsing, sign-in screen, camera list with group chips and status dots. `gradlew test assembleDebug lintDebug` passes: 19 unit tests (MockWebServer with invented data), debug APK builds, lint has 0 errors.
- VERIFIED by owner on a phone (2026-11-24): sign-in works against the real server and all cameras list with green status dots. The phone trusts the proxy's certificate (trusted certs, system CAs are enough).
- NOT verified: screen layout and polish beyond that (owner reported no problems), group chips and other status colours on real data.

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Its README is the feature list. Behaviour is the spec; no code is copied.

## Next steps
1. (Done) Owner installed the debug APK, signed in, all cameras listed.
2. Milestone 2: video spike, MediaCodec vs Media3. First verify cookie-only `/video/` and `/thumbs/`, and whether payloads are Annex-B. Then one live camera on screen.
3. Camera grid with live stills (the web app's snapshot mode) before live video.
4. Add `dependabot.yml` (Gradle, GitHub Actions) and a CodeQL workflow plus a CI workflow that runs `gradlew test lintDebug`.
5. Release signing setup before the first published release (same pattern as Embidextrous, key held by the owner).
6. Lint leftovers (warnings only): `mipmap-anydpi-v26` folder (aapt2 failed to find the icon after moving it to `mipmap-anydpi`, so it was put back), `SharedPreferences.edit` KTX suggestion, version bumps (left to Dependabot).

## Owner must do
- Nothing right now.

## Open questions
- Latency and quality targets for live video on mobile data vs Wi-Fi.
- Whether to add a home-screen widget (the web app has none).

## Commands (Windows, PowerShell)
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test assembleDebug lintDebug
```
`local.properties` (git-ignored) needs `sdk.dir` with escaped separators, for example `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. Gradle wrapper is 9.8.1, copied from Embidextrous.
