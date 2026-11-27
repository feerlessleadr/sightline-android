# Sightline for Android

[![CI](https://github.com/feerlessleadr/sightline-android/actions/workflows/ci.yml/badge.svg)](https://github.com/feerlessleadr/sightline-android/actions/workflows/ci.yml)

An Android app for viewing [Blue Iris](https://blueirissoftware.com/) live cameras, alerts and playback. It aims to match the features and interface of the web app [Sightline for Blue Iris](https://github.com/rw377/Sightline-for-Blue-Iris) as closely as Android allows.

> **Unofficial.** Not affiliated with, endorsed by or supported by Perspective Software, the maker of Blue Iris, or by the author of the web Sightline. Blue Iris is a trademark of its owner.

**Status:** early. No app code yet. See [STATUS.md](STATUS.md) and [DECISIONS.md](DECISIONS.md).

## Principles
- Talks only to your Blue Iris server, over HTTPS only. No accounts, cloud services, analytics or tracking.
- View-only: recording and camera setup stay in Blue Iris.
- Written from scratch against the Blue Iris API. No code is copied from the web project.

## Requirements (planned)
- Blue Iris 5 or 6 with its web server on, reachable over HTTPS (for example through a reverse proxy).
- A Blue Iris user account. A dedicated non-admin account is recommended.
- Android 11 (API 30) or newer.

## License
GNU General Public License v3.0. See [LICENSE](LICENSE).
