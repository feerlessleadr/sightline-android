# STATUS

Last updated: 2026-11-24

## Current state
- Project registered in the workspace (2026-11-24). Public GitHub repo feerlessleadr/sightline-android is live with the first commit (f0088de) on `main`. No app code yet.
- Secured 2026-11-24: secret scanning and push protection, private vulnerability reporting, Dependabot alerts and security updates, a `main` ruleset (no deletion, no force push), wiki off, delete-branch-on-merge on.
- Owner approved the stack, GPL-3.0, and HTTPS-only access (DECISIONS.md, 2026-11-24).

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Its README is the feature list. Behaviour is the spec; no code is copied.

## Next steps
1. DONE 2026-11-24: `docs/blue-iris-api.md` and `tools/Sample-BlueIris.ps1`. Owner ran the script; login, cookie-only stills, and `camlist`/`status`/`alertlist` shapes are VERIFIED (Blue Iris 6.1.6.1). Not committed yet. Still to verify: cookie-only video/clips, stream payload format, `flags` bits, `timeline`/`cliplist`/`ptz`/`export` shapes.
2. Milestone 1: Gradle skeleton (Kotlin, Compose, minSdk 26), HTTPS-only network config, sign-in screen, `camlist` shows cameras.
3. Milestone 2: video spike, MediaCodec vs Media3, one live camera on screen.
4. When Gradle files exist: add `dependabot.yml` and a CodeQL workflow (CodeQL fails on a repo with no code).

## Owner must do
- Create a dedicated, non-admin Blue Iris account for testing. Never put its password in this folder.
- Confirm Blue Iris's web server is reachable through the proxy from the test phone or emulator (see WORKSPACE.md for the address).

## Open questions
- Latency and quality targets for live video on mobile data vs Wi-Fi.
- Whether to add a home-screen widget (the web app has none).
