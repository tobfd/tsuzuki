# Tsuzuki: Android client for AniList

Tsuzuki (続き, "what comes next") is a native Android client for [AniList](https://anilist.co), built with Kotlin, Jetpack Compose and Material 3 (incl. M3 Expressive). The structure follows anilist.co, adapted to mobile.

- App name: **Tsuzuki**, store listing "Tsuzuki for AniList". Never call the app just "AniList" (AniList API terms).
- Package / applicationId: `com.tobfd.tsuzuki`
- OAuth redirect: `tsuzuki://auth`
- minSdk 31 (Android 12), targetSdk and compileSdk 37 (Android 17)
- Owner: Tobias (GitHub `tobfd`). Private first, but everything is built Play-Store-ready.
- License: GPL-3.0 (`LICENSE`, decided by Tobias 2026-10-01). Third-party licenses are listed in the app (Settings > About > Open-source licenses).

## Read these first

| File | What it is |
|---|---|
| `docs/PRODUCT.md` | Decisions, v1 scope, what is explicitly out of scope. Do not change a decision without asking Tobias. |
| `docs/ROADMAP.md` | Milestones M0 to M12 with tasks and "done when" criteria. **Work through it in order** and tick boxes as you go. |
| `docs/ANILIST_API.md` | API rules: auth, rate limit, errors, pagination, which query each screen uses. |
| `docs/DESIGN.md` | Design system and a spec for every v1 screen. |
| `design/tokens.json` | Colors, type scale, shapes, spacing, component sizes. The single source for theme values. |
| `graphql/` | Starting GraphQL operations, already checked against the AniList schema. They move into `core/network` in M2. |
| `docs/WEAR_OS.md` | Assessment of a possible Wear OS companion (not planned, no code). |
| `docs/images/` | README and store images and how to make them again (`docs/images/README.md`). |

The clickable design prototype lives at https://claude.ai/artifact/6Ek7UyxL3Eh388ZqzPNPR8. It is private to Tobias; `docs/DESIGN.md` describes everything you need, so you never have to open it.

## How to work in this repo

- **Language:** Tobias writes in German, so answer him in German. Everything in the repo is English: code, comments, KDoc, commit messages, PR texts, docs.
- **GitHub:** use the GitHub MCP tools for everything on GitHub (repo, issues, PRs, reviews), not the `gh` CLI. If the GitHub MCP server isn't configured, ask Tobias to add it. Plain `git` for local commits and pushes is fine.
- **Work in packages** (decided by Tobias, 2026-09-28; Tablet and widgets added and reordered 2026-09-30): M4 alone, then M5+M6, then M7+M8+M9, then Tablet (adaptive layouts), then M10+M11, then Widgets (formerly M13, part of v1), then M12 (polish, last before v1, includes the widgets). One branch, one PR and one phone test by Tobias per package. Inside a package, work through the milestones in order: start each session by reading `docs/ROADMAP.md`, pick the first unchecked task, and finish it before starting another. Small tasks, one commit each.
- **Branches and commits:** branch `m<N>/<short-topic>` for a single milestone (e.g. `m4/lists`), `m<N>-m<M>/<short-topic>` for a package (e.g. `m5-m6/home-detail`), `<package>/<short-topic>` for unnumbered packages (e.g. `widgets/glance`). Conventional Commits (`feat(lists): add +1 with undo`).
- **Merging:** Claude may squash-merge a PR itself once CI is green **and** Tobias has written "passt" for it. Without both, Tobias merges.
- **After a merge:** start the next package from the updated `main` right away, without waiting for a new prompt, unless a decision from Tobias is needed.
- **Before saying "done":** `./gradlew spotlessApply` then `./gradlew build` (compiles, unit tests, lint). Report failures honestly with the output.
- **Ask Tobias first** before: adding a dependency not listed below, changing anything in `docs/PRODUCT.md`, changing the module structure, or anything that touches his AniList account in bulk (mass edits, deletes).
- **Keep docs true.** When a decision or behavior changes, update the matching doc in the same PR. Tick roadmap boxes in the PR that finishes them.
- **Licenses screen:** a new runtime dependency (or a new transitive one in `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`) goes into `OpenSourceLibraries` in `feature/settings` (`LicensesScreen.kt`), with its license text in the assets if the license is new.
- **No secrets in git.** The AniList client ID goes in `local.properties` (`anilist.clientId=...`) and reaches code via `BuildConfig`. Never log or print the access token.

## Commands

```bash
./gradlew assembleDebug                 # build the app
./gradlew installDebug                  # install on the connected device / emulator
./gradlew testDebugUnitTest             # unit tests (all modules)
./gradlew connectedDebugAndroidTest     # instrumented + Compose UI tests (device needed)
./gradlew lint                          # Android lint
./gradlew spotlessCheck                 # ktlint formatting check (spotlessApply fixes)
./gradlew build                         # everything above except connected tests
./gradlew :core:network:downloadAnilistApolloSchemaFromIntrospection   # refresh the AniList schema (M2)
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile     # regenerate the Baseline Profile (M12)
ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # startup + scroll benchmarks
```

## Tech stack

Use the latest **stable** version of each library at project start (M0) and pin it in `gradle/libs.versions.toml`. Versions known from the Android docs Tobias provided: Compose BOM `2026.09.00`, Lifecycle `2.11.0`. Alpha/beta versions only when a needed API exists nowhere else, and then say so in the PR.

| Area | Library | Notes |
|---|---|---|
| Language | Kotlin (K2), Kotlin Serialization, Coroutines + Flow | |
| UI | Jetpack Compose (BOM), Material 3, M3 Expressive components, `material3-adaptive`, `material3-adaptive-navigation-suite` | Expressive APIs that are still experimental: `@OptIn` only inside `core:designsystem`. material3 1.4.0 (the version in the BOM) keeps the Expressive APIs (`MaterialExpressiveTheme`, `MotionScheme`, shape morphs) internal; they are public only in the 1.5 alphas. Decided: stay on stable; expressive motion follows in M12 once material3 1.5 is stable. |
| Navigation | **Navigation 3** (`navigation3-runtime`, `navigation3-ui`, `material3-adaptive-navigation3`) | Back stack as state, list-detail scenes on large screens, predictive back. Stable since 1.0 (M0 pins 1.2.0), so no Navigation Compose fallback is needed. |
| GraphQL | Apollo Kotlin 5 + normalized cache library `com.apollographql.cache` (memory + SQLite) | Codegen from the AniList schema, Kotlin models, `responseBased` not needed; default `operationBased`. The cache library replaces Apollo's older built-in `apollo-normalized-cache*` artifacts. |
| HTTP | OkHttp (through Apollo) | Auth + rate-limit interceptors live here. |
| DI | Hilt (KSP) | `hilt-navigation-compose` or the Nav3 ViewModel integration for scoped ViewModels. |
| Local data | Room 3 (`androidx.room3`, KSP) with the framework SQLite driver (`androidx.sqlite:sqlite-framework`, decided by Tobias) | Own lists (offline first) and the pending-mutation queue. Room 3 is the current stable major: package `androidx.room3`, Kotlin codegen only, DAOs are `suspend` or return `Flow`. |
| Settings | DataStore (Preferences) | App settings. |
| Token storage | DataStore + Tink AEAD with an Android Keystore master key | `EncryptedSharedPreferences` is deprecated; do not use it. |
| Background | WorkManager (+ Hilt worker factory) | Flush queued mutations, periodic list sync. |
| Images | Coil 3 (`coil-compose`, `coil-network-okhttp`) | Covers, banners, avatars. |
| Paging | Paging 3 (`paging-compose`) | Search, notifications. The Home feed loads more by button, without Paging. |
| Auth UI | `androidx.browser` Custom Tabs | Login page. |
| Splash | `androidx.core:core-splashscreen` | |
| Widgets | Jetpack Glance (`glance-appwidget`, `glance-material3`) | Approved by Tobias 2026-09-30 for the Widgets package; pinned at 1.2.0 (latest stable) since the Widgets package. Widgets read Room and go through `ListRepository` like the app. |
| Tests | JUnit 4/5, kotlinx-coroutines-test, Turbine, MockK or fakes, Compose UI test, Robolectric where handy, `apollo-testing-support` (test only) | Prefer hand-written fakes over mocks for repositories. Apollo responses come from `QueueTestNetworkTransport`. |
| Performance | Baseline Profile (`androidx.baselineprofile` plugin, `benchmark-macro-junit4`, `uiautomator`, `profileinstaller`) | Since M12. The profiles live in `app/src/release/generated/baselineProfiles` (`baseline-prof.txt` from the whole guest journey, `startup-prof.txt` from the cold start only; marked `linguist-generated` in `.gitattributes`); regenerate them after big UI changes, with network on the emulator. Release builds use R8 full mode (`app/proguard-rules.pro`); the `nonMinifiedRelease` variant the plugin collects from stays unobfuscated. Set `ANDROID_SERIAL` to the emulator so the tasks never run on Tobias's phone. |
| Build | Gradle version catalog, `build-logic` convention plugins, Spotless + ktlint | AGP 9 compiles Kotlin itself (built-in Kotlin): never apply `org.jetbrains.kotlin.android`. The root build pins the Kotlin Gradle plugin version. |

## Architecture

Single activity, Compose everywhere, unidirectional data flow:

```
Screen (stateless composable)  ←  UiState (StateFlow)  ←  ViewModel  ←  Repository  ←  Apollo (remote + normalized cache)
                               →  events (lambdas)     →                            ←  Room (own lists, mutation queue)
                                                                                    ←  DataStore (settings, token)
```

### Modules

Create modules when the milestone that needs them starts; don't scaffold empty ones up front.

```
app                     MainActivity, Application, app-level nav graph, NavigationSuiteScaffold shell
build-logic             convention plugins (android library, compose, feature, hilt, room)
baselineprofile         Baseline Profile generator and macrobenchmarks (com.android.test, since M12)
core/model              plain Kotlin models used across the app (Media, MediaListEntry, User, ScoreFormat, ...)
core/common             dispatchers, Result/AppError, time utils (no Android UI)
core/network            Apollo client, .graphql files, interceptors (auth, rate limit), error mapping, schema
core/database           Room database, entities, DAOs
core/datastore          settings + encrypted token store
core/data               repositories (interface + implementation), mappers GraphQL/Room → core/model, sync + WorkManager
core/designsystem       theme (dynamic color + AniList blue), typography, shapes, status colors, base components
core/ui                 shared composables that know core/model (MediaCover, MediaListRow, ScoreText, ActivityCard, AniListHtmlText)
core/testing            fakes, test rules, sample data
feature/auth            login screen, redirect handling
feature/home            Home tab
feature/lists           Lists tab + list editor sheet
feature/media           media detail
feature/browse          Browse tab, search, filter sheet
feature/people          character and staff pages
feature/profile         Profile tab + other users
feature/notifications   notifications
feature/settings        settings
feature/widgets         home-screen widgets (Glance): In Progress, Next episode, Friends' activity
```

Dependency rules: `feature/*` depends on `core/*` only, never on another feature. Navigation between features goes through route keys defined in each feature (`HomeRoute`, `MediaRoute(id)`) and wired in `app`. `core/designsystem` knows nothing about AniList models; `core/ui` does.

### Rules per layer

- **Composables:** a `XxxRoute` gets the ViewModel and collects state with `collectAsStateWithLifecycle()`; it calls a stateless `XxxScreen(uiState, onEvent...)` that has previews. No business logic, no repository access, no `LaunchedEffect` doing data work in screens.
- **UiState:** one immutable `data class` per screen (or a sealed `Loading / Content / Error` when the whole screen switches). Lists in state are `ImmutableList` (kotlinx.collections.immutable) or annotated `@Immutable` holders.
- **ViewModels:** expose `StateFlow<UiState>` built with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`; one-off effects (snackbar, navigation) go through a `Channel` or state flags that the UI consumes.
- **Repositories:** return `Flow` for observed data and `suspend` functions returning `Result<T>` / throwing `AppError` for actions. They hide whether data came from Apollo or Room.
- **Mappers:** GraphQL and Room types never leave `core/data`. Features only see `core/model`.
- **Errors:** one `AppError` sealed type (`Offline`, `RateLimited(retryAfterSec)`, `ApiUnavailable`, `Unauthorized`, `Validation(fields)`, `NotFound`, `Unknown`). Every screen has an error state with a retry action and uses the snackbar for action errors.

### Navigation (since M3)

- Each feature defines its route keys (`@Serializable ... : NavKey`, e.g. `MediaRoute(id)`) next to its screen composables. Screens get navigation as callbacks (`onOpenMedia: (Int) -> Unit`, `onBack`); they never import another feature.
- `app` wires everything in `AppShell`: one `rememberNavBackStack` per tab (`TopLevelTab`), each decorated with its own saveable state and ViewModel store (`rememberDecoratedNavEntries`), shown as Home's stack plus the current tab's stack in `NavDisplay`. `TopLevelNavigator` holds the logic (navigate, back, tab switch, reselect) and is unit tested.
- Tab roots are wrapped in `TabRootScaffold` (top bar with bell and avatar, expiry banner); pushed screens draw their own `TsuzukiBackTopBar`.
- Navigation 3 keeps entries across recompositions, so entry content must not capture changing values: shell data (viewer, unread count, expiry) comes from `LocalShellChrome`.
- Reselecting the current tab pops it to its root and sets a `ScrollToTopRequest`; root screens with a list call `ScrollToTopOnTabReselect(listState)`.
- Screens not built yet use `PlaceholderContent` from `core/ui`; each feature replaces it in its milestone.
- Bottom sheets that other features open (the list editor, `ListEditorRoute(mediaId)`) are routes with `BottomSheetSceneStrategy.bottomSheet()` metadata. A sheet that closes itself calls `TopLevelNavigator.dismiss(key)`, which never pops more than that sheet.
- A tab root can put its own actions into the tab's top bar through `TabRoot(actions = ...)` (Lists: search and sort).

### Offline first for the user's own lists

- Room holds the viewer's full anime and manga list (entries + a compact media row: id, titles, cover, format, episodes/chapters, status, next airing episode).
- Lists tab and Home "In Progress" read **only** from Room, so they render instantly and offline.
- Every change (+1, status, score, editor save, delete) is written to Room immediately (optimistic), then appended to a `pending_mutation` table. A WorkManager job (network constraint, exponential backoff) sends them in order. On a validation error the local change is rolled back and the user sees why.
- Full list refresh (`MediaListCollection`) on app start if older than 15 minutes, on pull to refresh, and via a periodic worker (every 6 hours, unmetered not required).
- In code (since M4): `ListRepository` in `core/data` is the only entry point. `ListMutationSender` sends the queue oldest first, all queued changes of one entry as one `SaveMediaListEntry` (a +1 undone before sending sends nothing) and rolls rejected ones back from the `previous` snapshot stored with each change. `ListWorkScheduler` runs it through WorkManager (`ListMutationWorker`, `ListSyncWorker`, Hilt worker factory in `TsuzukiApplication`). A sync never overwrites entries whose changes are still queued. Logout clears the whole database (`UserDataCleaner`).

### Widgets (since the Widgets package)

- `feature/widgets` holds the three Glance widgets, their receivers, `WidgetWork` (the only background requests they cause, see `docs/ANILIST_API.md`, Widgets) and `WidgetUpdater`, which the `Application` starts: it redraws placed widgets whenever what they show changes in Room, the session or the appearance settings. Widgets reach Hilt through `WidgetDependencies` (an entry point) and use the repositories in `core/data` like the app: `ListRepository` (+1), `AiringRepository`, `FriendActivityRepository`.
- Taps open the app through `tsuzuki://open/...` links (`AppLink` in `core/common`); `MainActivity` hands them to `AppShell`, which opens the tab and pushes the screen (`TopLevelNavigator.open`).
- Glance pitfalls: a `Chronometer` (through `AndroidRemoteViews`) doesn't tick inside a `LazyColumn`, and put directly into a `Row` it drops the row's other children (wrap it in a `Box`). RemoteViews layouts (also the static picker previews) only allow the RemoteViews view classes (no plain `View`).
- Debug builds have `PinWidgetActivity` (pins a widget through the launcher's dialog) and `WidgetSampleDataActivity` (a made-up session and sample data, emulator only and offline) for checks without an account.

### Settings (since M11)

- The look (colors, theme mode, pure black) is `AppearanceSettings` in its own DataStore (`SettingsStore`, file `settings`), kept on logout; `MainActivity` themes the app from it. The AniList options (title language, score format, adult content) live with the cached viewer and change only through `SettingsRepository.updateAniListOptions` (`UpdateUser`), which also clears the Apollo cache and re-picks the list titles in Room.
- App language is the platform per-app locale (`LocaleManager`, Android 13+, `res/xml/locales_config.xml`); there is no appcompat.

### AniList specifics that must hold everywhere

Details in `docs/ANILIST_API.md`. The short version:

- Debug builds log every AniList request under the logcat tag `AniListRequest` (operation, variables, HTTP status, GraphQL errors; never headers, so never the token): `adb logcat -s AniListRequest`.
- `isAdult`: send `false`, or leave the variable out when the viewer allows adult content. Never `true` (adult only) and never an explicit `null` (AniList returns nothing).
- **Rate limit is 30 requests/min right now** (normally 90) plus a burst limiter. Every request goes through the rate-limit interceptor. Aim for **one query per screen**, debounce search (400 ms, min 2 chars), never prefetch in bulk, never loop over pages in the background.
- GraphQL errors can arrive with HTTP 200. Always check `errors`.
- For pagination only `pageInfo.hasNextPage` is reliable. No "page X of Y", no totals from `Page`.
- Adult content hidden by default (`isAdult: false`), following the user's AniList setting once logged in.
- Respect the user's AniList options: title language, score format, staff name language.
- The token lasts one year and cannot be refreshed. Decode `exp` from the JWT; warn 14 days before expiry and send the user through login again when it has expired or a request returns 401.

## UI rules

- Material 3 components first; custom components only when M3 has nothing (the +1 button and the cover card are custom). Build them in `core/designsystem` / `core/ui`, never inline in a feature.
- **All colors, type, shapes and spacing come from the theme** (`MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`, `TsuzukiTheme.statusColors`, `TsuzukiSpacing`, `TsuzukiSizes`). No hex values or raw `dp` numbers for spacing in features.
- Icons: Material Symbols Rounded vector drawables through `TsuzukiIcons` in `core/designsystem` (no `material-icons-extended`). New icons are added there.
- Every component has previews: `@ThemePreviews` (light + dark) around `TsuzukiPreview { }` (both color sources). The debug-only component catalog (`app/src/debug`, opened by tapping the version in Settings > About 7 times; release builds don't contain it) shows every component in all four theme combinations; add new components to it.
- Default theme: dynamic color (`dynamicLightColorScheme` / `dynamicDarkColorScheme`). Setting "AniList blue" switches to the schemes in `design/tokens.json`. Theme mode: system / light / dark.
- Edge-to-edge everywhere (`enableEdgeToEdge()`), correct `WindowInsets` padding, predictive back enabled (`android:enableOnBackInvokedCallback="true"`).
- Tabs at the top of a screen are swipeable (since M12): `rememberTabPagerState` + `PagerTabRow` + `TabPager` in `core/designsystem`. The indicator follows the finger, each page keeps its scroll position (saveable state per page key), and pages that load from the network compose only when visible or swiped into view; Room-backed pages keep their neighbours ready. With swipeable pages only the visible page calls `ScrollToTopOnTabReselect`. The detail page keeps its anchor tabs.
- Adaptive: `NavigationSuiteScaffold` (bottom bar on phones, rail on larger widths); list-detail (Lists → Detail, Browse → Detail) shows two panes on expanded widths. Never lock orientation.
- Every screen: loading, content, empty and error states; previews for light + dark and both color schemes where color matters.
- Accessibility: content descriptions for icon-only buttons, 48 dp minimum touch targets, text scales up to 200 % without clipping, TalkBack reads list rows as one item with actions (`semantics(mergeDescendants = true)` + custom actions for +1).
- Strings only from resources. English in `values/strings.xml`, German in `values-de/strings.xml`, both complete in every PR that adds UI text. Use plurals for counts. Per-app language via `locales_config.xml`.
- Images: always pass the AniList `coverImage.color` as placeholder color, crossfade, correct `contentScale`. Covers are 2:3.
- Haptics: light tick on +1, confirm haptic when an entry completes.
- `PredictiveBackEntry` takes each entry's `BackStackPosition` (top / previous / other) from `AppShell`; which screen closes during back must come from the back stack, never from the entry's transition state (back can start mid-transition).
- Screen transitions come from `TsuzukiTransitions` in `core/designsystem` (M3 motion tokens, emphasized easing): shared axis X for opening screens and for back via arrow or button, the system's own back animation between activities for the back gesture (`PredictiveBackEntry` entry decorator with the AOSP values in `PredictiveBackTokens`, fed by `PredictiveBackState` from the `NavigationEventDispatcher`; `predictiveBack` itself moves nothing), and fade through for tab switches. Bottom sheets use `ModalBottomSheet`'s own predictive back. `AppShell` applies them to `NavDisplay`, so new screens get them automatically; don't add per-screen transitions.

## Testing

- Unit tests for every ViewModel (state transitions with Turbine), every repository (with fake Apollo responses / in-memory Room), mappers, score format conversion, JWT and redirect parsing, the rate limiter and the mutation queue.
- Compose UI tests for the list editor sheet, +1 with undo, and login redirect handling.
- Test names describe behavior: `plusOne_whenReachingTotal_marksCompletedAndOffersUndo`.
- Room is tested with an in-memory database on Robolectric (`@Config(sdk = [35])`: Robolectric's SDK 36 setup fails on the JDK 25 test runtime). Apollo responses in these tests are parsed from JSON (`ListTestData.kt`), shaped like AniList's.
- Compose UI tests (`connectedDebugAndroidTest`) run on the emulator (API 36) only. On Android 17 (API 37) every one fails inside Espresso 3.7.0 (`NoSuchMethodException: InputManager.getInstance`); as of 2026-09-30 there is no newer stable Espresso / androidx.test release. Update and retry on the Pixel once one ships.
- `core/testing` holds sample data built from real AniList responses (Frieren id 154587 is used across the design), `MainDispatcherRule`, fakes like `FakeSessionRepository`, and `InMemoryDataStore`. Unit tests never use the file-backed DataStore: it can't replace its file on Windows JVMs, so such tests fail locally while CI (Linux) passes.

## Don'ts

- Don't add a forum, review writing or airing reminders before v1 is done (see `docs/PRODUCT.md`, "Later"). Widgets are part of v1 since 2026-09-30.
- Don't hardcode the client ID, colors, strings or spacing.
- Don't call the API from composables or from `init {}` of anything but a ViewModel/repository.
- Don't swallow errors; map them to `AppError` and show them.
- Don't store AniList data beyond what the app needs to work offline (AniList terms: no hoarding).
- Don't copy code from other AniList clients (e.g. AniHyou) without checking its license first; reading for ideas is fine.
