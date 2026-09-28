# Tsuzuki

An unofficial, native Android client for [AniList](https://anilist.co), built with Kotlin, Jetpack Compose and Material 3. Not affiliated with AniList.

**Status:** planning done, implementation starts with milestone M0 (see `docs/ROADMAP.md`).

## Getting started

1. Register an API client at https://anilist.co/settings/developer
   - Name: `Tsuzuki for AniList`
   - Redirect URL: `tsuzuki://auth`
2. Add the client ID to `local.properties` (never committed):
   ```properties
   anilist.clientId=12345
   ```
3. Open the project in Android Studio (latest stable) and run the `app` configuration on a device with Android 12 or newer.

## Docs

- `CLAUDE.md`: how the code is organized and the rules for working on it (also read by Claude Code).
- `docs/PRODUCT.md`: decisions and scope.
- `docs/ROADMAP.md`: milestones and tasks.
- `docs/ANILIST_API.md`: how the app talks to AniList.
- `docs/DESIGN.md` and `design/tokens.json`: design system and screen specs.

## Requirements

- minSdk 31 (Android 12), targetSdk 37 (Android 17)
- JDK 17+, Android Studio latest stable

## License

Not decided yet.
