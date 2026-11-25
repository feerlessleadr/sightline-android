# Sightline for Android

Android viewer for Blue Iris live cameras, alerts and playback.

- **Spec:** the web app Sightline for Blue Iris, https://github.com/rw377/Sightline-for-Blue-Iris (GPLv3). Match its features and behaviour as closely as Android allows.
- **Network:** talks only to the owner's Blue Iris server, **HTTPS only** (JSON API, `/image/`, `/thumbs/`, `/video/`, `/file/clips/`, `/clips/`). No cleartext, no redirects, no other hosts, no analytics, no cloud.
- **Licence:** GPL-3.0. Do not copy code from the web project; reimplement behaviour from the Blue Iris API.
- **Public GitHub repo** (feerlessleadr/sightline-android): never put LAN addresses, hostnames, Windows usernames, server names, keys or passwords in this folder. Blue Iris host details belong only in `C:\dev\_docs\WORKSPACE.md`.
- **Stack:** proposed in DECISIONS.md (2026-11-24). Matches Embidextrous where possible.
- **Git:** commit identity `feerlessleadr <github@thecgkg.com>`. Commit or push only when the owner asks.

Docs: `STATUS.md` (living snapshot), `DECISIONS.md` (append-only, newest at bottom).
