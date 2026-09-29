# Tsuzuki

An unofficial, native Android client for [AniList](https://anilist.co), built with Kotlin, Jetpack Compose and Material 3. Not affiliated with AniList.

**Status:** M4 (lists, offline first) done; next are M5 and M6, Home and the detail page (see `docs/ROADMAP.md`).

## Getting started

1. Register an API client at https://anilist.co/settings/developer
   - Name: `Tsuzuki for AniList`
   - Redirect URL: `tsuzuki://auth`
2. Add the client ID to `local.properties` in the project root (the file is git-ignored and never committed):
   ```properties
   anilist.clientId=12345
   ```
   It reaches the code as `BuildConfig.ANILIST_CLIENT_ID`. Without it, the build stops with an error that says what to add.
3. Open the project in Android Studio (latest stable) and run the `app` configuration on a device with Android 12 or newer, or use the command line:
   ```bash
   ./gradlew installDebug
   ```

## Commands

```bash
./gradlew assembleDebug                 # build the app
./gradlew installDebug                  # install on the connected device / emulator
./gradlew testDebugUnitTest             # unit tests
./gradlew connectedDebugAndroidTest     # instrumented + Compose UI tests (device needed)
./gradlew lint                          # Android lint
./gradlew spotlessApply                 # format Kotlin, Gradle scripts and XML (ktlint)
./gradlew spotlessCheck build           # what CI runs
```

## Project layout

- `app`: the application module.
- `build-logic`: Gradle convention plugins (`tsuzuki.android.application`, `tsuzuki.android.library`, `tsuzuki.android.compose`, `tsuzuki.android.feature`, `tsuzuki.hilt`, `tsuzuki.room`, `tsuzuki.jvm.library`). New modules apply these instead of configuring Android, Kotlin, Compose, Hilt or Room themselves.
- `gradle/libs.versions.toml`: every dependency and plugin version.
- `core/model` (plain Kotlin models), `core/common` (`AppError`, dispatchers), `core/network` (Apollo, AniList schema and operations, auth and rate-limit interceptors), `core/datastore` (encrypted token and session), `core/data` (repositories), `core/designsystem` (theme, tokens, icons, base components), `core/ui` (shared composables that know the models, e.g. `MediaCover`, `ScoreText`), `core/testing` (fakes and test rules), and one module per feature under `feature/` (auth, home, lists, browse, media, people, profile, notifications, settings; most still show placeholders). Navigation is wired in `app` (see `CLAUDE.md`, "Navigation").
- Debug builds add a second launcher entry, **Tsuzuki Catalog**, with every design system component in both color sources, light and dark.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs `./gradlew spotlessCheck build` on every pull request and on pushes to `main`, with a dummy client ID.

## Docs

- `CLAUDE.md`: how the code is organized and the rules for working on it (also read by Claude Code).
- `docs/PRODUCT.md`: decisions and scope.
- `docs/ROADMAP.md`: milestones and tasks.
- `docs/ANILIST_API.md`: how the app talks to AniList.
- `docs/DESIGN.md` and `design/tokens.json`: design system and screen specs.

## Requirements

- minSdk 31 (Android 12), targetSdk and compileSdk 37 (Android 17)
- Android Studio latest stable. Gradle runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`) and downloads it automatically if it is not installed; any JDK 17+ can start `./gradlew`.

## License

Not decided yet.
