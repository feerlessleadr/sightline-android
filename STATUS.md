# STATUS

Last updated: 2026-11-24

## Current state
- Public repo feerlessleadr/sightline-android is live and secured (secret scanning, push protection, private vulnerability reporting, Dependabot alerts and security updates, `main` ruleset with no deletion or force push).
- Owner approved the stack, GPL-3.0 and HTTPS-only access (DECISIONS.md, 2026-11-24).
- API notes in `docs/blue-iris-api.md`: login, cookie-only stills, `camlist`/`status`/`alertlist` VERIFIED against Blue Iris 6.1.6.1.
- **Milestone 1 (fab181d) plus stills grid, fingerprint unlock and minSdk 30 (14c3c7e), all pushed. Milestone 1 was:** Gradle project (AGP 9.4.1, Kotlin 2.4.21, Compose, minSdk 30 (Android 11+), same versions as Embidextrous), HTTPS-only network config, `BlueIrisClient` (challenge/response login, session cookie only, no redirects, 8 MB reply cap), `camlist` parsing, sign-in screen, camera list with group chips and status dots. `gradlew test assembleDebug lintDebug` passes: 19 unit tests (MockWebServer with invented data), debug APK builds, lint has 0 errors.
- VERIFIED by owner on a phone (2026-11-24): sign-in works against the real server and all cameras list with green status dots. The phone trusts the proxy's certificate (trusted certs, system CAs are enough).
- NOT verified: screen layout and polish beyond that (owner reported no problems), group chips and other status colours on real data.

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Its README is the feature list. Behaviour is the spec; no code is copied.

## Next steps
1. (Done) Owner installed the debug APK, signed in, all cameras listed.
2. Live video: single camera VERIFIED on the owner's phone (plays, start-up slow, now asks for the 720p tier like the web app). The live group grid is built but NOT yet seen on a phone. Full feature list and milestones: `docs/feature-parity.md`. Still to do in M2: per-camera tile sizes, reorder, big-tile own streams, activity borders and AI labels, web palette.
2b. (Done, owner confirmed it works) Fingerprint unlock: "Remember me" saves the password encrypted with a biometric-bound Keystore key; the lock screen at launch signs in silently. Replaces the token-only approach (DECISIONS.md 2026-11-24).
3. (Owner has seen it work) Camera grid with live stills, refreshed every 2 s per visible tile.
4. Add `dependabot.yml` (Gradle, GitHub Actions) and a CodeQL workflow plus a CI workflow that runs `gradlew test lintDebug`.
5. Release signing setup before the first published release (same pattern as Embidextrous, key held by the owner).
6. Lint leftovers (warnings only): `SharedPreferences.edit` KTX suggestion, version bumps (left to Dependabot).

## Owner must do
- Video start measured fast on the phone (about 0.1-0.9 s); the cause of the earlier slowness was not found, see DECISIONS.md 2026-11-25. If it gets slow again, run Settings > Developer > Test video start and send the lines.
- Still to check on the phone: Live grid (plays? Stills mode? sizes), Never lock flow, lock-after minutes, quality picker.

## Open questions
- Latency and quality targets for live video on mobile data vs Wi-Fi.
- Whether to add a home-screen widget (the web app has none).

## Commands (Windows, PowerShell)
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test assembleDebug lintDebug
```
`local.properties` (git-ignored) needs `sdk.dir` with escaped separators, for example `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. Gradle wrapper is 9.8.1, copied from Embidextrous.







