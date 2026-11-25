# STATUS

Last updated: 2026-11-24

## Current state
- Project registered in the workspace (2026-11-24). Public GitHub repo feerlessleadr/sightline-android created and secured. No app code yet.
- Owner approved the stack, GPL-3.0, and HTTPS-only access (DECISIONS.md, 2026-11-24).

## Feature target
Parity with the web app Sightline for Blue Iris (https://github.com/rw377/Sightline-for-Blue-Iris). Its README is the feature list. Behaviour is the spec; no code is copied.

## Next steps
1. Write `docs/blue-iris-api.md` from the Blue Iris JSON API behaviour (login challenge/response, camlist, status, alertlist), not from copied code.
2. Milestone 1: Gradle skeleton (Kotlin, Compose, minSdk 26), HTTPS-only network config, sign-in screen, `camlist` shows cameras.
3. Milestone 2: video spike, MediaCodec vs Media3, one live camera on screen.
4. When Gradle files exist: add `dependabot.yml` and a CodeQL workflow (CodeQL fails on a repo with no code).

## Owner must do
- Create a dedicated, non-admin Blue Iris account for testing. Never put its password in this folder.
- Confirm Blue Iris's web server is reachable through the proxy from the test phone or emulator (see WORKSPACE.md for the address).

## Open questions
- Latency and quality targets for live video on mobile data vs Wi-Fi.
- Whether to add a home-screen widget (the web app has none).
