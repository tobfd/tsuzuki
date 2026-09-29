# Roadmap

Work top to bottom. Each milestone ends in a working app that Tobias can install. Tick boxes in the PR that finishes them. "Done when" is the acceptance check; it must hold before the next milestone starts.

From M4 on, milestones ship in packages (decided by Tobias, 2026-09-28), each with one branch, one PR and one phone test:

1. M4
2. M5 + M6
3. M7 + M8 + M9
4. M10 + M11 + M12

Claude may squash-merge a package's PR once CI is green and Tobias has written "passt", then starts the next package from `main` without waiting, unless a decision from Tobias is needed.

Before M0, Tobias does two things by hand:

- [x] Create the AniList API client at https://anilist.co/settings/developer: name "Tsuzuki for AniList", redirect URL `tsuzuki://auth`. Put the client ID in `local.properties` as `anilist.clientId=<id>`.
- [ ] Create the GitHub repository (private) and copy this handoff folder into its root.

---

## M0 · Project setup

- [x] New Android Studio project "Tsuzuki", Empty Compose Activity, package `com.tobfd.tsuzuki`, minSdk 31, target/compile 37, Kotlin DSL.
- [x] `gradle/libs.versions.toml` with the stack from `CLAUDE.md` (latest stable versions, Compose BOM 2026.09.00).
- [x] `build-logic` convention plugins: `tsuzuki.android.application`, `tsuzuki.android.library`, `tsuzuki.android.compose`, `tsuzuki.android.feature`, `tsuzuki.hilt`, `tsuzuki.room`.
- [x] Spotless + ktlint, Android lint with `warningsAsErrors` off but `abortOnError` on.
- [x] Hilt application class, `enableEdgeToEdge()`, splash screen, predictive back flag in the manifest.
- [x] `local.properties` → `BuildConfig.ANILIST_CLIENT_ID`; build fails with a clear message if it is missing.
- [x] Placeholder adaptive app icon + monochrome layer (themed icon). Name "Tsuzuki".
- [x] GitHub Actions workflow: `./gradlew spotlessCheck build` on every PR (uses a dummy client ID).
- [x] `README.md` updated with setup steps.

**Done when:** CI is green, the app installs and shows an empty screen in dynamic color, light and dark.

## M1 · Design system

- [x] `core/designsystem`: `TsuzukiTheme(colorSource, themeMode)` with dynamic color and AniList blue light/dark from `design/tokens.json`.
- [x] Status colors (current, planning, completed, paused, dropped, repeating) as `TsuzukiTheme.statusColors`, harmonized for dynamic color (use `MaterialColors.harmonize` or the tokens as fixed values; tokens are generated against `#3DB4F2`).
- [x] Typography with Google Sans Flex (bundle the variable font in `res/font`, OFL license file in `assets/licenses`), type scale from tokens.
- [x] Shapes and spacing from tokens (`TsuzukiSpacing`).
- [x] Components: `PlusOneButton` (shape morph, haptic), `MediaCover` (2:3, placeholder color, badge slot), `StatusChip`, `ScoreText` (all 5 score formats), `SectionHeader` (title + "See all"), `EmptyState`, `ErrorState` (retry), `TsuzukiTopBar` (bell with badge + avatar), `SegmentedToggle`. (`MediaCover` and `ScoreText` know AniList models, so they live in `core/ui`.)
- [x] Debug-only "Catalog" screen showing every component in both color sources and modes.
- [x] Previews for every component.

**Done when:** the catalog screen matches `docs/DESIGN.md` on a phone in all four theme combinations.

## M2 · Network and login

- [x] Apollo in `core/network`: `service("anilist")` with introspection of `https://graphql.anilist.co` (task `downloadAnilistApolloSchemaFromIntrospection`), codegen package `com.tobfd.tsuzuki.core.network`, normalized cache (memory + SQLite).
- [x] Move `graphql/*.graphql` from the handoff into `core/network/src/main/graphql/...` and make them compile.
- [x] `AuthInterceptor` (Bearer token when logged in), `RateLimitInterceptor` (see `docs/ANILIST_API.md`), `AppError` mapping incl. HTTP-200 errors, 403 "API disabled", 429.
- [x] Encrypted token store (DataStore + Tink/Keystore). JWT payload parser for `sub` and `exp`.
- [x] Login: Custom Tab to the authorize URL, `tsuzuki://auth` intent filter on `MainActivity` (`singleTask`), parse the URL fragment, store token, fetch `Viewer`, cache viewer id, name, avatar and options.
- [x] Login screen as designed; "Browse without an account" = guest mode.
- [x] Logout clears token, Room, Apollo cache and settings that came from AniList. (Room came in M4; logout clears all its tables and stops the list work.)
- [x] Expiry handling: warning banner 14 days before `exp`; on expiry or 401 → login screen with a short explanation.
- [x] Tests: fragment parser, JWT parser, rate limiter, error mapper, login ViewModel.

**Done when:** Tobias can log in on his Pixel, the app shows "Logged in as tobfd" somewhere temporary, and he stays logged in after the app is killed and restarted.

## M3 · App shell and navigation

- [x] Navigation 3 back stack per tab (Home, Lists, Browse, Profile) with state kept when switching tabs; reselecting a tab pops to its root and scrolls to top.
- [x] `NavigationSuiteScaffold`: bottom bar on compact, rail on medium/expanded. (The rail also shows on phones in landscape; the library default would keep the bar there.)
- [x] Top bar with bell (badge from `Viewer.unreadNotificationCount`) and avatar (→ Profile tab).
- [x] Routes for Media, Character, Staff, User, Notifications, Settings with placeholder screens.
- [x] Guest mode: Lists and Profile tabs show a "Log in to see your lists" state.
- [x] Predictive back works on every screen, including sheets. (Navigation 3 animates it; there are no sheets yet, M4's list editor sheet must keep it working.)

**Done when:** every tab and route is reachable, back behaves correctly, rotating or resizing keeps state.

## M4 · Lists (offline first)

- [x] Room: `media_list_entry`, `media_lite`, `custom_list`, `pending_mutation`; DAOs with Flows. (New module `core/database`, framework SQLite driver as Tobias decided; a small `list_sync` table keeps the 15-minute rule.)
- [x] Sync from `MediaListCollection` (anime and manga) incl. custom lists; refresh rules from `CLAUDE.md`. (On login, app start and every return to the foreground if older than 15 minutes, on pull to refresh, and every 6 hours in the background. The sync skips the Apollo cache and never overwrites entries whose changes are still queued.)
- [x] Lists screen: Anime/Manga toggle, status tabs with counts (Watching/Reading, Planning, Completed, Paused, Dropped, Rewatching/Rereading, then custom lists), sort (title, score, progress, last updated, start date), search within the list. (Search opens a field below the toggle and searches the whole list in every title language.)
- [x] Row: cover, title (user's title language), format + year, progress `x / y` (or `x / ?`), score in the user's format, +1 button (current/repeating) or Start (planning).
- [x] +1: optimistic, haptic, queued mutation; reaching the total sets COMPLETED (and completedAt) and shows a snackbar with Undo.
- [x] Start: sets CURRENT, progress 0, startedAt today.
- [x] List editor bottom sheet: status chips, progress stepper (+ volumes for manga), score control per score format, start/finish date pickers, rewatches, notes, private, hidden from status lists, custom list toggles, Remove (with confirm), Save. Validation errors from the API shown inline. (A route, `ListEditorRoute(mediaId)`, shown as a bottom sheet scene, so the detail page opens it the same way in M6. Online, Save waits up to 10 s for AniList so its errors show inline; offline it closes and the change is sent later.)
- [x] `pending_mutation` worker: in-order sending, retries with backoff, rollback + message on validation errors. (All queued changes of one entry go out as one request; a +1 undone before sending sends nothing; after 5 unexpected errors a change is rolled back and reported.)
- [x] Empty states per tab; pull to refresh.
- [x] Tests: sync mapper, mutation queue, +1/undo logic, score format conversion, editor ViewModel.

**Done when:** Tobias uses the Lists tab instead of the website for a day, including in airplane mode, and nothing gets lost.

## M5 · Home

- [ ] "In Progress" carousel from Room (current entries, sorted by last updated), +1 on each card, "See all" → Lists tab.
- [ ] "Up next from Planning": up to 3 planning entries (prefer already released or airing, then most recently added) with Start.
- [ ] Activity feed with Following / Global toggle (Paging 3, `Page.activities`), list and text activities, like toggle (optimistic), relative time, tap on media → detail, tap on user → profile.
- [ ] Trending row (anime) → detail.
- [ ] One network query for the feed page + trending; the rest comes from Room.

**Done when:** Home loads from cache instantly and refreshes with at most 2 requests.

## M6 · Media detail

- [ ] One `MediaDetail` query per open; normalized cache makes reopening free.
- [ ] Header: banner, cover, title in the user's language + native title, format · episodes · status, average score, top ranking, list button (status + progress, opens editor; "Add to list" when not on the list; login prompt for guests), favourite toggle, share (`siteUrl`).
- [ ] Anchored tabs: Overview, Characters, Stats, Social, Recommendations.
- [ ] Description as rich text (AniList HTML subset: `<br>`, `<i>`, `<b>`, links, `~!spoiler!~`) with "Read more".
- [ ] Genres, tags with rank % (spoiler tags hidden behind "Show spoiler tags"), info grid, where to watch (external links of type STREAMING), trailer (YouTube intent), relations, characters with Japanese VA, staff, status distribution, score distribution, following (friends' status + score), recommendations with rating.
- [ ] Character and staff lists open the people screens (M8).
- [ ] Two-pane layout on expanded widths when opened from Lists or Browse.
- [ ] Shared element transition cover → header if cheap.

**Done when:** the Frieren page (id 154587) matches the design and needs exactly one request.

## M7 · Browse and search

- [ ] Search field (debounce 400 ms, min 2 chars) with results list, Anime/Manga toggle.
- [ ] Filter sheet: format, status, season + year, genres, tags, sort; active filter count on the filter button; Reset / Show results.
- [ ] Quick chips: Trending, Top 100, This season, Top movies, Top manhwa (preset filters).
- [ ] Idle state: Trending now and Newly added rows.
- [ ] Paging 3 on `hasNextPage`, adult filter applied.

**Done when:** searching "frieren" with filters finds the right entry and scrolling pages don't trip the rate limit.

## M8 · Character and staff

- [ ] Character: image, names (incl. native), description (rich text, spoilers), favourite toggle, appearances grid with role.
- [ ] Staff: image, names, occupations, description, characters voiced (paged) and production roles.

**Done when:** tapping a character or voice actor from detail opens a complete page with one request.

## M9 · Profile

- [ ] Own profile (Profile tab) and other users (route `UserRoute(name)`).
- [ ] Banner, avatar, name, stats (total anime, episodes watched, days watched, mean score), tabs Overview / Favourites / Stats / Social.
- [ ] Overview: activity history heatmap (12 weeks, `User.stats.activityHistory`), recent activity.
- [ ] `User.stats` is deprecated in the AniList schema (Apollo warns on `UserProfile` since M2). Check whether the schema offers a replacement for `activityHistory`; if not, keep using it (decided by Tobias, 2026-09-28).
- [ ] Favourites: anime, manga, characters, staff; empty state.
- [ ] Stats: anime by status, score overview.
- [ ] Social: following / followers (first page); follow / unfollow on other profiles.
- [ ] Top bar on own profile: bell and settings.

**Done when:** Tobias's and GeckoTV's profiles render correctly.

## M10 · Notifications

- [ ] `Page.notifications` with `type_in` per filter chip (All, Airing, Activity, Follows, Media), Paging 3.
- [ ] Unread = the first `unreadNotificationCount` items at the moment the screen opens (the API has no per-item read state). Opening with `resetNotificationCount: true` resets the badge; "Mark all as read" does the same explicitly.
- [ ] Group consecutive likes on the same activity ("X, Y and 3 others liked your activity").
- [ ] Tap targets: activity → media or user, airing → media, follow → user.

**Done when:** the badge clears after opening, and grouped likes match the website's list.

## M11 · Settings

- [ ] Colors (Material You / AniList blue) with live preview, theme mode, app language (per-app locale).
- [ ] Title language, score format, adult content: read from and saved to AniList (`UpdateUser`), cached locally.
- [ ] Account: avatar, name, log out (confirm).
- [ ] About: version, "Unofficial app. Not affiliated with AniList. Data from the AniList API.", open-source licenses screen.

**Done when:** changing title language on the phone changes it on anilist.co and in every screen.

## M12 · Polish and release readiness

- [ ] German translation complete and reviewed by Tobias.
- [ ] Accessibility pass: TalkBack on every screen, font scale 200 %, contrast in both color sources.
- [ ] Large screens: tablet + foldable emulator pass, no orientation lock warnings.
- [ ] Performance: Baseline Profile, R8 full mode, no jank in list scroll (check with Macrobenchmark or at least the profiler).
- [ ] M3 Expressive motion: switch the theme to `MaterialExpressiveTheme` / `MotionScheme.expressive()` once material3 1.5 is stable. M1 stays on stable material3 1.4.0, where these APIs are internal (decided by Tobias, 2026-09-28: no alpha).
- [ ] APK size: measure the bundled Google Sans Flex (about 4 MB, unmodified since M1) and decide whether to subset it; a subset is a Modified Version under the font's trademark notes.
- [ ] Final app icon and themed icon.
- [ ] Crash-free run through all screens with airplane mode toggled.
- [ ] Play-ready basics (only if Tobias wants to publish): privacy policy page, data safety answers, store listing "Tsuzuki for AniList", screenshots.

**Done when:** Tobias has used v1 as his only AniList app for a week.
