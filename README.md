<p align="center">
  <img src="docs/images/banner.png" alt="Tsuzuki for AniList: keep track of what comes next" width="100%">
</p>

[![Release](https://img.shields.io/github/v/release/tobfd/tsuzuki?style=for-the-badge&label=release)](https://github.com/tobfd/tsuzuki/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/tobfd/tsuzuki/ci.yml?branch=main&style=for-the-badge&label=CI)](https://github.com/tobfd/tsuzuki/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/tobfd/tsuzuki?style=for-the-badge)](LICENSE)
[![Min SDK](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Ftobfd%2Ftsuzuki%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.minSdk&label=Min%20SDK&prefix=API%20&color=3DB4F2&style=for-the-badge)](gradle/libs.versions.toml)

**Tsuzuki** (続き, "what comes next") is a native Android client for [AniList](https://anilist.co): your anime and manga lists, what you are watching right now and what comes next, built with Jetpack Compose and Material 3.

<p align="center">
  <img src="docs/images/framed/home.webp" width="24%" alt="Home">
  <img src="docs/images/framed/lists.webp" width="24%" alt="Lists">
  <img src="docs/images/framed/detail.webp" width="24%" alt="Frieren detail page">
  <img src="docs/images/framed/widgets.webp" width="24%" alt="Home-screen widgets">
</p>

## Features

- **+1 in one tap** from Home, the lists and a home-screen widget, with undo.
- **Offline first:** your lists live on the device; changes are sent when you are back online.
- **Detail pages** with characters, staff, stats, where to watch and a share card.
- **Browse** with search, filters, trending and this season.
- **Notifications** for new episodes and AniList activity, switchable per kind in Android's settings.
- **Material You or AniList blue**, light, dark and pure black, tablets and foldables, English and German.

## Tech stack

| Area | What |
|---|---|
| Language | Kotlin (K2), Coroutines and Flow, kotlinx.serialization, kotlinx.collections.immutable |
| UI | Jetpack Compose, Material 3, `material3-adaptive` and the navigation suite, splash screen API, Google Sans Flex, Material Symbols |
| Navigation | Navigation 3: a back stack per tab, list-detail scenes on large screens, predictive back (`navigationevent`) |
| Network | Apollo Kotlin 5 with the normalized cache (memory + SQLite), OkHttp with auth and rate-limit interceptors; a separate plain OkHttp client for the GitHub update check |
| Login | AniList OAuth (implicit grant) in a Custom Tab (`androidx.browser`) |
| Paging | Paging 3 (search results, notifications) |
| Local data | Room 3 with the framework SQLite driver (offline lists and the queue of pending changes), DataStore, Tink with an Android Keystore key for the token |
| Background | WorkManager with Hilt workers (sends queued changes, syncs lists, updates widgets, checks AniList notifications); AlarmManager for new-episode notifications |
| Widgets | Jetpack Glance |
| Images | Coil 3 (OkHttp network) |
| DI | Hilt (KSP) |
| Build | Gradle version catalog, convention plugins in `build-logic`, KSP, Spotless + ktlint, Android lint, R8 full mode, Baseline Profiles |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric, Room and Paging testing, Apollo test transport, hand-written fakes, Compose UI tests, Macrobenchmark with UI Automator |

## Download

Get the APK from the [latest release](https://github.com/tobfd/tsuzuki/releases/latest) (Android 12 or newer). The app tells you on Home when a newer release is out; Settings > About checks by hand or turns that off.

## Build from source

1. Create an API client at [anilist.co/settings/developer](https://anilist.co/settings/developer) with the redirect URL `tsuzuki://auth`.
2. Put its ID into `local.properties` in the project root (git-ignored):
   ```properties
   anilist.clientId=12345
   ```
3. Run `./gradlew installDebug` with a device or emulator connected.

Any JDK 17+ starts the Gradle wrapper; Gradle fetches the JDK it runs on itself. [`CONTRIBUTING.md`](.github/CONTRIBUTING.md) and [`CLAUDE.md`](CLAUDE.md) explain how the code is organized.

## Disclaimer

Tsuzuki is an unofficial app and not affiliated with, endorsed or sponsored by AniList. Anime and manga data, covers and banners come from the [AniList API](https://docs.anilist.co) and belong to their respective owners.

## License

Copyright (C) 2026 Tobias Schmitt

Tsuzuki is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License](LICENSE) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU General Public License](LICENSE) for more details.

Third-party licenses are listed in the app under Settings > About > Open-source licenses.
