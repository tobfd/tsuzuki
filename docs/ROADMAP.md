# Roadmap

Work top to bottom. Each milestone ends in a working app that Tobias can install. Tick boxes in the PR that finishes them. "Done when" is the acceptance check; it must hold before the next milestone starts.

From M4 on, milestones ship in packages (decided by Tobias, 2026-09-28), each with one branch, one PR and one phone test:

1. M4
2. M5 + M6
3. M7 + M8 + M9
4. Tablet (added by Tobias, 2026-09-30)
5. M10 + M11
6. Widgets (formerly M13, part of v1 since 2026-09-30)
7. M12 (polish, the last package before v1; covers the widgets too)

Reordered by Tobias on 2026-09-30: M12 moved behind the widgets so its accessibility, large-screen and performance passes include them.

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

- [x] "In Progress" carousel from Room (current entries, sorted by last updated), +1 on each card, "See all" → Lists tab. (Tapping a card opens the list editor.)
- [x] "Up next from Planning": up to 3 planning entries (prefer already released or airing, then most recently added) with Start. (Room keeps no "added" date, so the newest by `updatedAt`, which for planned entries is usually when they were added.)
- [x] Activity feed with Following / Global toggle (`Page.activities`, 25 per page, more only through a "Load more" button), list and text activities, like toggle (optimistic), relative time, tap on media → detail, tap on user → profile. (Guests see the Global feed only.)
- [x] Trending row (anime) → detail. (Above the feed.)
- [x] One network query for the feed page + trending; the rest comes from Room. (The first page and trending show from the Apollo cache at once, then one `Home` request refreshes both.)

**Done when:** Home loads from cache instantly and refreshes with at most 2 requests.

## M6 · Media detail

- [x] One `MediaDetail` query per open; normalized cache makes reopening free. (A cached page shows at once and is fetched again only when older than an hour.)
- [x] Header: banner, cover, title in the user's language + native title, format · episodes · status, average score, top ranking, list button (status + progress, opens editor; "Add to list" when not on the list; login prompt for guests), favourite toggle, share (`siteUrl`). (The list button reads the entry from Room. "Add to list" adds the media as Planning right away, online only, and opens the editor.)
- [x] Anchored tabs: Overview, Characters, Stats, Social, Recommendations.
- [x] Description as rich text (AniList HTML subset: `<br>`, `<i>`, `<b>`, links, `~!spoiler!~`) with "Read more".
- [x] Genres, tags with rank % (spoiler tags hidden behind "Show spoiler tags"), info grid, where to watch (external links of type STREAMING), trailer (YouTube intent), relations, characters with Japanese VA, staff, status distribution, score distribution, following (friends' status + score), recommendations with rating.
- [x] Character and staff lists open the people screens (M8).
- [x] Two-pane layout on expanded widths when opened from Lists or Browse. (`ListDetailSceneStrategy` from material3-adaptive-navigation3; phones in landscape count as expanded.)
- [ ] Shared element transition cover → header if cheap. (Not cheap: every tab has its own decorated entries and the back gesture draws its own animation; left for M12.)
- [x] "Share as image" (added by Tobias, 2026-09-29): the share button makes a card image: cover, title in the viewer's title language, list status, progress x / y, own score in the viewer's score format, color accent from the cover, avatar and name, small "Tsuzuki" logo. Rendered with Compose (GraphicsLayer → Bitmap), shared through a FileProvider with `ACTION_SEND` and the `siteUrl` link as text. Formats 9:16 (story) and 1:1, with a preview before sharing. (Guests and media not on the list share the link only.)

**Done when:** the Frieren page (id 154587) matches the design and needs exactly one request.

## M7 · Browse and search

- [x] Search field (debounce 400 ms, min 2 chars) with results list, Anime/Manga toggle. (The field, toggle and chips stay above the scrolling results; a new term cancels the running search.)
- [x] Filter sheet: format, status, season + year, genres, tags, sort; active filter count on the filter button; Reset / Show results. (Changes stay a draft until "Show results". A year filters anime by season year and manga by start date. Tags are searched by name, genres and tags load once per app run; the Hentai genre and adult tags only show with adult content on.)
- [x] Quick chips: Trending, Top 100, This season, Top movies, Top manhwa (preset filters). (Top 100 stops paging at 100; This season, Top movies and Top manhwa switch the type they need; tapping an active chip clears it.)
- [x] Idle state: Trending now and Newly added rows. ("See all" opens Trending, or recently added.)
- [x] Paging 3 on `hasNextPage`, adult filter applied. (20 per page, the next page 5 rows before the end; media a later page repeats are left out. With adult content on, `isAdult` is left out: `true` shows adult media only, the Home trending row had that bug; an explicit `null` returns nothing, which emptied every search on the first phone test.)

**Done when:** searching "frieren" with filters finds the right entry and scrolling pages don't trip the rate limit.

## M8 · Character and staff

- [x] Character: image, names (incl. native), description (rich text, spoilers), favourite toggle, appearances grid with role. (Plus gender, age, birthday, blood type and the Japanese voice actor under each appearance; more appearances with "Load more".)
- [x] Staff: image, names, occupations, description, characters voiced (paged) and production roles. (Both lists page with "Load more", one request per tap; the page itself is one `StaffDetail` request, cached for an hour like the detail page. Adult media are dropped on the client, since these connections have no `isAdult` argument.)

**Done when:** tapping a character or voice actor from detail opens a complete page with one request.

## M9 · Profile

- [x] Own profile (Profile tab) and other users (route `UserRoute(id, name)`: `UserProfile` needs the id for the activity page, and every link to a user knows it; the name titles the page while it loads).
- [x] Banner, avatar, name, stats (total anime, episodes watched, days watched, mean score), tabs Overview / Favourites / Stats / Social. (One `UserProfile` request when the profile first shows and on pull to refresh. "Follows you" under the name; the about text on Overview, images in it shown as links.)
- [x] Overview: activity history heatmap (12 weeks, `User.stats.activityHistory`), recent activity. (4 strengths relative to the busiest day shown, dates in UTC like AniList; likes on the recent activities.)
- [x] `User.stats` is deprecated in the AniList schema (Apollo warns on `UserProfile` since M2). Check whether the schema offers a replacement for `activityHistory`; if not, keep using it (decided by Tobias, 2026-09-28). (Checked 2026-09-30: `statistics` has no activity history, so it stays.)
- [x] Favourites: anime, manga, characters, staff; empty state.
- [x] Stats: anime by status, score overview. (Anime and manga: totals, mean score, standard deviation, and bars by status in the status colors.)
- [x] Social: following / followers (first page); follow / unfollow on other profiles. (Each list loads when first shown; "More on AniList" when there are more. Follow is optimistic; guests get the log-in prompt.)
- [x] Top bar on own profile: bell and settings. (The Profile tab shows settings and the bell instead of the avatar.)
- [x] Other users' lists (added by Tobias, 2026-09-30): "Anime list" / "Manga list" on other profiles open the Lists view read only (`UserListRoute`): `MediaListCollection` by user id, status and custom tabs, sort, search, no +1 or editor, tap → detail. Kept in the Apollo cache only, never in Room. Private lists ("Private User", status 404) show their own state. (Chunks of 500 are loaded one after the other when the list opens, at most 10.)

**Done when:** Tobias's and GeckoTV's profiles render correctly.

## Tablet · Adaptive layouts (added by Tobias, 2026-09-30)

For medium and expanded widths (Pixel Tablet, foldables open, phones in landscape). Phones in portrait stay as they are.

- [x] Grids with more columns: Browse results and idle rows, Home (In Progress, trending, the feed in two columns on expanded), character/staff grids and profile favourites use the width instead of stretching one column. (Adaptive grids follow the pane's real width: list rows from 360 dp per column in Lists, other users' lists and Browse results; the Home feed as a staggered grid from 320 dp, two columns in tablet portrait, three in landscape. Character/staff grids were adaptive already; horizontal rows keep scrolling edge to edge.)
- [x] Maximum reading width for text (descriptions, bios, activity text, settings): long lines are capped and centred. (Descriptions and bios at most 640 dp, left-aligned in their column; activity text is bounded by its grid column; the login actions 480 dp, centred. Since M10/M11 the settings and the notification rows are capped at 640 dp too, centred.)
- [x] List-detail side by side: Lists → detail, Browse → detail, Profile (own and others, incl. their lists) → detail, with the detail pane replacing itself on further taps. (Character and staff pages open in the detail pane too. Before anything is opened, the detail pane shows a placeholder instead of staying empty.)
- [x] Detail page in two columns on expanded widths: header, list button and info on one side, the sections on the other. (From 840 dp of available width: banner, cover, score, title and list button in a 400 dp column; tabs and sections, the info grid included, on the right.)
- [x] Sheets as dialogs where that reads better on large screens (list editor, filter sheet, share preview), keeping predictive back. (On expanded windows; the share preview was a dialog already. The list editor needs a login, so it is checked on a real device.)
- [x] Checked with screenshots on the tablet emulator (Pixel Tablet, portrait and landscape) and a foldable emulator (folded and open). (No tablet or foldable image is installed and there is no avdmanager, so both are simulated on the phone emulator with `adb shell wm size` / `wm density`: 2560 × 1600 and 1600 × 2560 at 320 dpi, 2076 × 2152 at 390 dpi; folded equals the phone.)

**Done when:** every tab, the detail page and the people/profile pages look designed on the Pixel Tablet and an open foldable, with no stretched single column and nothing clipped.

## M10 · Notifications

- [x] `Page.notifications` with `type_in` per filter chip (All, Airing, Activity, Follows, Media), Paging 3. (25 per page; forum and submission notifications are left out. Grouped by This week / Last week / Earlier, pull to refresh.)
- [x] Unread = the first `unreadNotificationCount` items at the moment the screen opens (the API has no per-item read state). Opening with `resetNotificationCount: true` resets the badge; "Mark all as read" does the same explicitly. (The count from before the reset comes in the same request as the first page, `Viewer` before `Page`; the badge count is the fallback. Unread rows are tinted with a dot until the screen closes or "Mark all as read".)
- [x] Group consecutive likes on the same activity ("X, Y and 3 others liked your activity"). (Also across a page break; up to three stacked avatars.)
- [x] Tap targets: activity → media or user, airing → media, follow → user. (Activities about a list update open the media, others the user; media changes open the media.)

**Done when:** the badge clears after opening, and grouped likes match the website's list.

## M11 · Settings

- [x] Colors (Material You / AniList blue) with live preview, theme mode, app language (per-app locale). (Stored in a device DataStore that logout keeps; the splash waits for it. App language through `LocaleManager` and `locales_config.xml`, Android 13+ only.)
- [x] Title language, score format, adult content: read from and saved to AniList (`UpdateUser`), cached locally. (One request per change, online only; the Apollo cache is cleared and the list titles in Room follow without a sync.)
- [x] Account: avatar, name, log out (confirm). (Guests get "Log in with AniList".)
- [x] About: version, "Unofficial app. Not affiliated with AniList. Data from the AniList API.", open-source licenses screen. (Hand-made list by license with the license texts bundled; no new dependency.)
- [x] AMOLED option "Pure black" for the dark theme (added by Tobias, 2026-09-29): background #000000, surfaces raised slightly, works with Material You and AniList blue; shown in the catalog. (Containers keep 60 % of their color, `design/tokens.json`.)

**Done when:** changing title language on the phone changes it on anilist.co and in every screen.

## Widgets (Glance; added by Tobias, 2026-09-30, part of v1)

Formerly M13 after v1; moved into v1 before M12 by Tobias on 2026-09-30. Jetpack Glance (latest stable) is approved and listed in `CLAUDE.md`.

- [x] "Currently watching": In Progress entries from Room (no request of its own), +1 on each goes through the existing mutation queue (`ListRepository` → `ListMutationWorker`), and the widget updates whenever Room changes. (Named "In Progress" / "Aktuell dabei" like Home's row. The +1 on the last episode opens the list editor instead of completing silently (Tobias, 2026-10-01). `WidgetUpdater` watches Room, the session and the appearance while the app process runs, which is where every change happens, and redraws a widget only when what it shows changed.)
- [x] "Next episode": the next airing episodes of the viewer's current anime with a countdown, from `airingSchedule` / `nextAiringEpisode`; refreshed by WorkManager at most once an hour and right after an episode airs, never in a loop. (`nextAiringEpisode` of up to 50 ids in one `NextEpisodes` request, 2 minutes after the soonest episode airs but at least an hour apart and at most 12 hours; a request-free redraw when an episode airs. The airing time is a new Room column, so the list sync fills it too. Live `Chronometer` countdown within a day.)
- [x] "Friends' activity": the newest activities of the people the viewer follows, one request per periodic update (every few hours, backed off on errors and rate limits), shown from the last result in between. (Every 3 hours, the newest 10 kept in Room; one extra request right after a login.)
- [x] Widgets follow the app theme (dynamic color / AniList blue, light/dark), open the matching screen on tap, and show a clear state when logged out or offline. (Pure black too. Taps go through `tsuzuki://open/...` links (`AppLink`), restricted to the app's package. Row, single and list layouts; picker previews drawn by the widgets on Android 15+, static layouts below.)

**Done when:** all three widgets run on Tobias's home screen for a day, stay current, and the request log shows no extra load beyond the planned updates.

## M12 · Polish and release readiness

- [ ] German translation complete and reviewed by Tobias. (Complete: every English string has a German one. Checked by Claude on 2026-10-01; clear inconsistencies fixed (Episoden → Folgen, Season → Saison, Überblick → Übersicht), the open wording questions are in the M12 PR. Tobias's review is still open.)
- [ ] Accessibility pass: TalkBack on every screen and widget, font scale 200 %, contrast in both color sources. (Done on the emulator: a scan of every reachable screen for unnamed buttons and touch targets under 48 dp (one unnamed cover button in the feed fixed), all screens and the widgets at 200 % (names on the detail page now wrap, the Next episode widget counts rows by text size), and `ContrastTest` for AniList blue, pure black and the status colors (dynamic color keeps its contrast by construction). Open: a hands-on TalkBack run on the phone.)
- [x] Large screens: re-check the Tablet package's layouts with M10/M11's new screens and the widgets' sizes (tablet + foldable emulator), no orientation lock warnings. (Notifications, settings and licenses keep their reading width; the widgets fill wide cells. Found and fixed: back from a detail pane also closed the list pane beside it and landed on Home (`BackNavigationBehavior.PopLatest`). No activity locks its orientation.)
- [x] Performance: Baseline Profile, R8 full mode, no jank in list scroll (check with Macrobenchmark or at least the profiler); widget updates stay cheap (no work on the main thread, no updates beyond the planned ones). (Release builds run R8 in full mode (APK 9.2 MB). New `:baselineprofile` module: a guest journey (start, Home, Browse, detail) generates the profile, `AppBenchmarks` measures cold start and scrolling with and without it. Widgets render in Glance's worker and are redrawn only when their data changes; see the M12 PR for the emulator numbers.)
- [ ] M3 Expressive motion: switch the theme to `MaterialExpressiveTheme` / `MotionScheme.expressive()` once material3 1.5 is stable. M1 stays on stable material3 1.4.0, where these APIs are internal (decided by Tobias, 2026-09-28: no alpha).
- [ ] APK size: measure the bundled Google Sans Flex (about 4 MB, unmodified since M1) and decide whether to subset it; a subset is a Modified Version under the font's trademark notes. (Measured 2026-10-01: 4.15 MB, 2.4 MB compressed, about a quarter of the 9.2 MB release APK. Decision open for Tobias.)
- [ ] Final app icon and themed icon. (Three proposals in `design/icon-proposals/`; Tobias picks one.)
- [ ] Crash-free run through all screens and widgets with airplane mode toggled. (Done on the emulator with the minified build as a guest, online and offline, and with the debug build's sample session and widgets offline: no crash. Open: the logged-in run on the phone.)
- [ ] Play-ready basics (only if Tobias wants to publish): privacy policy page, data safety answers, store listing "Tsuzuki for AniList", screenshots.

**Done when:** Tobias has used v1 as his only AniList app for a week.
