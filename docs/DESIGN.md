# Design

The design follows Material 3 with M3 Expressive touches. Exact values live in `design/tokens.json`; this file explains how to use them and specifies every v1 screen. Screen sizes below refer to a 390 × 844 dp phone (Pixel 9 class).

The clickable prototype (private to Tobias): https://claude.ai/artifact/6Ek7UyxL3Eh388ZqzPNPR8

## Foundations

### Color

- **Default: dynamic color** from the wallpaper (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
- **AniList blue** (setting): full light and dark schemes in `tokens.json → color.anilistBlue`, generated with material-color-utilities `SchemeFidelity` from seed `#3DB4F2`. Map every key 1:1 onto `ColorScheme` (the key names match Compose's parameter names). The roles the tokens leave out are derived the way material-color-utilities does: `background` = `surface`, `onBackground` = `onSurface`, `surfaceVariant` = `surfaceContainerHighest`, `surfaceTint` = `primary`.
- **Status colors** (`tokens.json → color.status`): current, planning, completed, paused, dropped, repeating, each with `color`, `container`, `onContainer` for light and dark. Expose them as `TsuzukiTheme.statusColors` through a `CompositionLocal`. They stay the same in both color sources.
- Use `surfaceContainer*` roles for cards and sheets, `secondaryContainer` for selected chips and the active nav indicator, `primary` for the +1 button and progress bars, `inverseSurface` for the episode badge on covers.
- Cover placeholder color = AniList `coverImage.color` (fallback `surfaceContainerHigh`).

### Type

Font: **Google Sans Flex** (Google Fonts, OFL), bundled as a variable font. Scale `[size sp, line height sp, weight]`:

| Style | Value | Used for |
|---|---|---|
| displaySmall | 36 / 44 / 600 | Score on detail header |
| headlineMedium | 28 / 36 / 600 | Profile name, detail title |
| headlineSmall | 24 / 32 / 500 | Login title |
| titleLarge | 22 / 28 / 500 | Top app bar, section headers |
| titleMedium | 16 / 24 / 500 | Card and row titles |
| bodyLarge | 16 / 24 / 400 | Descriptions |
| bodyMedium | 14 / 20 / 400 | Meta lines, activity text |
| labelLarge | 14 / 20 / 500 | Buttons, chips, tabs |
| labelMedium | 12 / 16 / 500 | Nav labels, small badges |
| labelSmall | 11 / 16 / 500 | Episode badge on covers |

### Shape and spacing

- Shapes: extraSmall 4, small 8, medium 12, large 16, extraLarge 28, full = pill/circle.
- Usage: progress bar extraSmall; chips small; covers medium; cards and list rows large; bottom sheets extraLarge (top corners); buttons full.
- Spacing grid 4 dp. Screen margin 16, gap between cards 12, gap between sections 24 (28 on Home), chip padding 8.
- Sizes: covers 2:3; In Progress cover 144 × 204; list thumbnail 48 × 68; bottom nav 80; top app bar 64; +1 button 48 × 48 (40 × 40 inside dense cards).

### Large screens (Tablet package)

Phones in portrait look exactly as specified below; wider panes adapt to the space they get, so a list pane next to an open detail pane stays a single column.

- **Adaptive grids:** list rows (Lists, other users' lists, Browse results) in columns of at least 360 dp; the Home feed in a staggered grid of at least 320 dp per column (two columns from about 650 dp). Horizontal rows (In Progress, Trending, Newly added) still scroll from edge to edge.
- **Reading width:** running text (descriptions, bios) at most 640 dp wide; the login actions at most 480 dp, centred.
- **List-detail:** Lists, Browse, the Profile tab, other users' profiles and their lists are list panes; media, character and staff pages open in the detail pane beside them. Before anything is opened the detail pane shows a placeholder (続 tile, "Nothing open yet").
- **Detail page in two columns** from 840 dp of available width: the header (banner, cover, score, title, list button) in a 400 dp column on the left, the anchored tabs and sections on the right.
- **Sheets as dialogs** on expanded windows: the list editor and the Browse filters open as a dialog up to 560 dp wide (extraLarge corners, surfaceContainerLow) instead of a bottom sheet.
- Checked on the Pixel Tablet size (2560 × 1600, 320 dpi, landscape) and an open foldable (2076 × 2152, 390 dpi), simulated on the phone emulator with `adb shell wm size` / `wm density`.

### Motion and feel

- **+1 button** (signature component): filled `primary`, label "+1". At rest it is a rounded square (shape medium, 12 dp); while pressed it morphs to a circle (full) with a spring (`Spring.DampingRatioMediumBouncy`, stiffness medium-low), then back. Light haptic tick on tap; confirm haptic when the entry completes. When progress reaches the total, the button is replaced by a "Completed" chip in completed-container colors.
- **Screen transitions** (`TsuzukiTransitions`, M3 motion tokens, emphasized easing):
  - Opening a screen: shared axis X, 450 ms (long1). The new screen slides in from 30 dp to the right while the old one moves 30 dp to the left; the old one fades out in the first 35 %, the new one fades in during the rest (emphasized accelerate / decelerate).
  - Back with the arrow or the back button: the mirror image.
  - Back gesture: the system's back animation between activities, with the values from AOSP (`CrossActivityBackAnimation` / `DefaultCrossActivityBackAnimation` in WMShell, Android 15/16; Navigation 3, Material 3 and navigationevent ship no screen animation of their own). While the finger moves (progress through the back gesture curve, cubic 0.1, 0.1, 0, 1): the current screen shrinks to 90 % and moves until its right edge is 8 dp from the window edge (a swipe from the right edge shrinks it into the middle); the previous screen starts 96 dp to the left and shrinks to 90 % in sync; both follow the finger vertically (decelerated, up to 8 dp from the top or bottom). Both get the display's corner radius. A black scrim lies between them: 20 % in the light theme, 80 % in the dark theme. After release (450 ms, emphasized path easing): the current screen fades out within the first 20 % while moving on 96 dp to the right, the previous screen grows back into place, the scrim fades out linearly, and both shrink slightly with the release speed and spring back (stiffness 200, damping 0.75). Cancelling springs back (stiffness 1500). With animations turned off in the system settings nothing moves. Which screen closes and which one comes back is taken from the back stack (its top and the screen below), never from the running transition: back (the gesture or the back key) can start while a screen is still opening, and the screen fading out of that transition is the one back brings back.
  - Switching tabs: fade through, 300 ms (medium2). The old content fades out in the first 35 %, the new one fades in and scales from 92 % to 100 %.
- Default M3 Expressive motion scheme for everything else. Sheets and dialogs follow predictive back.
  - Not possible yet: material3 1.4.0 (Compose BOM 2026.09.00) keeps `MotionScheme` and `MaterialExpressiveTheme` internal, so the theme uses the standard motion scheme. Tobias decided to stay on stable; expressive motion follows in M12 once material3 1.5 is stable. The +1 spring does not depend on it.

### Status wording

List status (the user's entry, `MediaListStatus`) and release status (the series, `MediaStatus`) never share a word, so a "Beendet" series is never confused with a "Gesehen" entry. English follows AniList. Decided by Tobias; `StatusWordingTest` in `core/ui` guards the no-overlap rule.

| `MediaListStatus` | English anime / manga | German anime | German manga |
|---|---|---|---|
| CURRENT | Watching / Reading | Am Schauen | Am Lesen |
| PLANNING | Planning | Geplant | Geplant |
| COMPLETED | Completed | Gesehen | Gelesen |
| PAUSED | Paused | Pausiert | Pausiert |
| DROPPED | Dropped | Abgebrochen | Abgebrochen |
| REPEATING | Rewatching / Rereading | Erneut am Schauen | Erneut am Lesen |

| `MediaStatus` | English | German |
|---|---|---|
| RELEASING | Releasing | Läuft |
| FINISHED | Finished | Beendet |
| NOT_YET_RELEASED | Not yet released | Angekündigt |
| CANCELLED | Cancelled | Abgesetzt |
| HIATUS | Hiatus | Unterbrochen |

### Iconography

Material Symbols Rounded, 24 dp, weight 400. Icon-only buttons are 48 dp touch targets. The icons are vector drawables in `core/designsystem`, exposed through `TsuzukiIcons`.

## Components

| Component | Spec |
|---|---|
| `TsuzukiTopBar` | 64 dp, title in titleLarge on the left. Actions: bell (badge dot/number in `error` when unread > 0), avatar 32 dp circle (initial on primaryContainer as placeholder). |
| Bottom navigation | M3 `NavigationBar` via `NavigationSuiteScaffold`, 4 items: Home (house), Lists (list), Browse (explore), Profile (person); the selected item uses the filled icon. Active item: pill indicator in secondaryContainer. From medium width on, a `NavigationRail` instead, also on phones in landscape. German labels: Start, Listen, Entdecken, Profil. |
| `MediaCover` | 2:3, shape medium, placeholder color, optional top-left badge (inverseSurface, labelSmall, e.g. "EP 18 / 28" or a status), optional 4 dp progress bar at the bottom (primary on surfaceContainerHighest). |
| `MediaListRow` | Row in a large-shaped surfaceContainerLow card: thumbnail 48 × 68, title (titleMedium, 1 line, ellipsis), meta (bodyMedium, onSurfaceVariant: "TV · 2023 · ★ 9.0"), progress "18 / 28", trailing +1 (current/repeating) or "Start" tonal button (planning). Tap opens the list editor; long press opens detail. |
| `StatusChip` | Filter-chip style; selected = status container + onContainer, unselected = outline with a 8 dp status dot. |
| `ScoreText` | Renders per score format: POINT_100 "85", POINT_10_DECIMAL "8.5", POINT_10 "8", POINT_5 "★★★★☆", POINT_3 smileys. "–" when 0. |
| `SectionHeader` | titleLarge + optional "See all" text button (primary). |
| `ProgressButton` | Tonal button that shows a small spinner (20 dp) in place of its label while loading, keeping its size; taps wait until done ("Load more" on Home). |
| `ActivityCard` | surfaceContainerLow, large shape: avatar 40, "user" bold + text ("watched episode 18 of") + media title in primary, 40 × 56 cover on the right, relative time, like button with count (heart, primary when liked), reply count. |
| `MediaResultRow` | Browse results: the `MediaListRow` card without +1; title (2 lines), "TV · 2023 · 91%", and the viewer's list status (status dot + label) when the media is on the list. Tap opens detail. |
| `PersonCoverCard` | A character or staff member as a 2:3 card (120 dp wide) with name and one more line (e.g. the media a character is from); next to `MediaCoverCard` in grids and rows. |
| `ActivityHeatmap` | Profile activity history: up to a year of weeks × 7 days (Monday on top), starting at the oldest day AniList keeps (about half a year, at least 12 weeks), scrolling sideways when wider than the screen and opening at today, 12 dp cells with 3 dp gaps, empty days in surfaceContainerHighest, 4 strengths of primary relative to the busiest day shown, month labels above the week a month starts in. TalkBack reads the total. |
| Empty / error states | Centered: 56 dp icon tile (surfaceContainerHigh, 16 dp radius), titleLarge headline, bodyMedium text, optional tonal action button. |
| Snackbar | M3 snackbar with action ("Undo"). |
| Bottom sheets | `ModalBottomSheet`, drag handle, extraLarge top corners, surfaceContainerLow. |

## Screens

### Login

Centered stack on surface: 続 logo tile (primaryContainer, 28 dp radius), "Tsuzuki" (headlineSmall), tagline "Keep track of what comes next. Your AniList, made for Android." Bottom: primary full-width pill button "Log in with AniList" (56 dp), text button "Browse without an account", footnote "Unofficial app for AniList. Not affiliated with AniList."

### Home (tab)

Top bar "Home" + bell + avatar. Scrolling column:
1. **In Progress**: section header with "See all" (→ Lists). Horizontal row of filled cards (M3 filled card, `surfaceContainerHighest`, shape large, 8 dp inner padding) around a 144 dp cover with its progress bar, title (2 lines, fixed 48 dp height), then a row with "x / y" and the +1 button (or "Completed" chip). No badge on the cover: it repeated "x / y" (Tobias chose the filled card and dropped the badge, 2026-10-01).
2. **Up next from Planning**: up to 3 rows (40 × 56 cover, title, meta like "TV · 12 episodes · Airing"), trailing tonal "Start". Hidden when Planning is empty.
3. **Trending now**: horizontal row of 120 dp covers with title and meta.
4. **Activity**: header with a Following / Global segmented toggle (guests: Global only, no toggle), then 25 `ActivityCard`s and below them a tonal "Load more" button that adds the next 25; while loading it shows a small spinner in place of its label (`ProgressButton`). Nothing loads on its own while scrolling. A failed "Load more" or refresh keeps the cards and says why in the snackbar; pull to refresh goes back to the first 25. Likes change at once and go back if AniList refuses.
- Tapping an In Progress card opens the list editor; +1 reaching the total offers Undo like on Lists.

### Lists (tab)

- Top bar "Lists" with search and sort actions. Below: Anime / Manga segmented toggle (full width), then scrollable status tabs with counts ("Watching 1", "Planning 3", "Completed", "Paused", "Dropped", "Rewatching", then custom lists). Manga uses Reading / Rereading. Status tabs leave out entries hidden from status lists; custom list tabs show them.
- Swipe left or right to change the tab (M12): the pages move with the finger, the tab indicator follows, and each tab keeps its scroll position. The Anime / Manga toggle stays a toggle (a second horizontal swipe would fight the tabs).
- Search opens a pill field below the toggle; while it has text, the tabs hide and the results come from the whole list (every title language). Sort menu: Title, Score, Progress, Last updated (default), Start date; the current one has a check mark.
- Changes that wait longer than 3 seconds to be sent (e.g. offline) show a quiet hint under the tabs: cloud icon and "2 changes wait to be sent".
- Content: `MediaListRow`s. Planning rows show "Start" instead of +1.
- +1 reaching the total → entry moves to Completed, snackbar "Frieren marked as completed" with Undo (German: "als gesehen / gelesen markiert").
- A change AniList rejects is rolled back and explained in a snackbar: "Couldn't save Frieren: <AniList's reason>", "Frieren is no longer on your list." (removed elsewhere), or "Couldn't save Frieren. Your change was undone."
- Empty tab: icon, "Nothing here yet", text, tonal button "Browse" (→ Browse tab).
- **List editor** (bottom sheet, opened by tapping a row, the detail list button, or "Add to list"): header with cover, title, "Details" link (→ detail). Sections: Status (chips for all six statuses with status colors), Episode progress (− value / total +; manga adds volumes), Score (control per score format; slider 0–10 step 0.5 for POINT_10_DECIMAL, labeled "Not scored" at 0), then collapsible "More" with start/finish dates, rewatches, notes, private, hide from status lists, custom lists. Footer: text button "Remove" (error color, confirm dialog) and filled "Save".
  - Score controls: slider 0–100 (POINT_100), 0–10 in whole points (POINT_10), 0–10 in half points (POINT_10_DECIMAL), five stars (POINT_5), three smileys (POINT_3); tapping the selected star or smiley clears the score. An unchanged score keeps its exact raw value.
  - Like AniList, choosing Completed fills in the total and today's finish date, and starting a planned entry sets today's start date, where none is set.
  - Progress above the total shows "Can't be more than 28" and disables Save; AniList's own field errors show under the matching field.

### Media detail

- Header: banner (16:9-ish, 200 dp, scrim at the bottom), floating back and share buttons (surfaceContainerHigh circles). Cover 112 × 160 overlapping the banner, next to it: format · episodes · status line, average score big ("91%") with the top ranking ("#1 highest rated all time").
- Title (headlineMedium) + romaji · native subtitle.
- Actions row: big tonal/filled list button showing the entry ("Watching · 18 / 28", status colors; "Add to list" when absent), favourite toggle (heart), more menu.
- Anchor tabs (scrollable): Overview, Characters, Stats, Social, Recommendations. Tapping scrolls to the section; scrolling updates the selected tab.
- Overview: description (4 lines + "Read more"), genre chips, tag chips with rank % ("+25 tags" expands; spoiler tags hidden until revealed), info grid (2 columns: Format, Episodes, Duration, Status, Start, End, Season, Studio, Source, Popularity, Favourites), "Where to watch" buttons (streaming links), Relations (horizontal cards with relation type label, title, format · status).
- Characters: rows with character image + name + role on the left, Japanese VA on the right; "See all".
- Stats: status distribution as a stacked bar + legend with user counts (status colors), score distribution bars.
- Social: Following list (avatar, name, status, score).
- Recommendations: horizontal covers with "+votes".
- Once the header scrolls away, the tab row stays pinned at the top and takes the back and share buttons.
- On expanded widths (tablets, phones in landscape) the detail page opens beside Lists or Browse.
- **Share as image**: the share button opens a preview dialog with a Story (9:16) / Square (1:1) toggle. The card (always rendered at 360 dp width, fixed dark colors): gradient from the cover color to near black, cover, title (bold, centered), "Watching · 18 / 28" in a light cover tint, the viewer's score in their format, then avatar + name and the 続 logo with "Tsuzuki". It is shared as a PNG with the AniList link as text. Guests and media not on the list share the link only.

### Browse (tab)

- Search field at the top (pill, leading search icon, trailing filter button with active-count badge). Anime / Manga toggle. Quick chips row: Trending, Top 100, This season, Top movies, Top manhwa.
- Idle: "Trending now" and "Newly added" horizontal rows with "See all".
- Typing or filters active: result list (`MediaListRow`-like rows without +1, showing format · year · score), result label ("12 results for 'fri'" is not possible with only `hasNextPage`; show "Results" instead).
- Filter sheet: chip groups Format, Status, Season, Genres, Sort; Year stepper; footer "Reset" and "Show results".

### Profile (tab)

- Banner (140 dp) with top-right bell and settings buttons (own profile), avatar 88 dp overlapping, name (headlineMedium).
- Stats row (3 columns): Total anime, Episodes watched, Days watched.
- Tabs: Overview, Favourites, Stats, Social, swipeable (M12). Banner, name, stats and list buttons scroll away above the tabs, then the tabs stay at the top and each tab scrolls on its own, keeping its position.
- Overview: "Activity history" heatmap (up to a year × 7 days, as far back as AniList keeps (about 185 days), 12 dp cells, 4 intensity levels of primary, month labels), "Recent activity" list.
- Favourites: grid of covers; empty state "No favourites yet · Tap the heart on any anime, manga, character or staff page."
- Stats: key numbers + "Anime by status" bars in status colors.
- Social: user rows with "Follows you" label; follow button on other profiles.

### Notifications

- Top bar: back, "Notifications", "Mark all as read" action.
- Filter chips: All, Airing, Activity, Follows, Media. Swiping the list changes the filter (M12); the chip follows and stays in view, and a filter loads only when its page shows.
- Groups by time ("This week", "Last week", "Earlier"); weeks start on the locale's first day of the week.
- Row: stacked avatars (up to 3, 16 dp offset), bold names + text ("Himmel, Eisen and 3 others liked your activity"), subline with context ("Watched episodes 17 – 18 of Frieren"), relative time, unread dot (primary) and surfaceContainerHigh background when unread.
- Per-item "read" is not supported by the API; unread items are the first `unreadNotificationCount` items at open time.
- Empty states per filter (Airing: "You get one here when a new episode of something you are watching airs.").
- Built (M10): the row is `NotificationRow` in `core/ui` (32 dp avatars in a 64 dp leading slot, the cover for airing and media changes); taps open the media (airing, media changes, activities about a list update) or the user (follows, messages, other activities). Rows are capped at the reading width on large screens. Pull to refresh.

### Settings

- Top bar: back, "Settings".
- **Appearance:** "Colors" as two selectable cards side by side (Material You with wallpaper swatches, AniList blue swatch), Theme segmented button (System / Light / Dark), "Pure black" switch ("Black background in the dark theme"). The cards are `ColorChoiceCard` in `core/designsystem`; the whole app recolors at once, which is the live preview.
- **Language:** App language row ("System default"; a dialog with System default / English / Deutsch; Android 13+ only, hidden on Android 12, which has no per-app languages), Title language segmented (Romaji / English / Native) with "Synced with your AniList settings" and a live example ("Sousou no Frieren").
- **Lists and content:** Score format row ("10 point decimal (8.5/10)") opening a picker, "Show adult content" switch ("Hidden by default"; when on: "Shown in search, trending and lists").
- **Notifications** (logged in only): one row "New episodes, activity, follows" / "Choose in Android's settings" that opens the app's Android notification settings; the channels there are the only switches.
- **Account:** avatar, name, "Logged in with AniList", outlined "Log out" with a confirm dialog. Guests see "Log in with AniList" instead, and no title language or "Lists and content" (AniList options).
- **About:** "Tsuzuki for AniList · 0.1.0", "Unofficial app. Not affiliated with AniList. Data from the AniList API.", "Check for updates" (supporting text: the result, "Up to date" or "v1.1.0 available · tap to download", which then opens the release page) and the switch "Check for updates automatically" ("At most once a day when the app starts"), both only in builds with the GitHub update check; Open-source licenses (a screen listing the shipped libraries by license, each license text expandable from the bundled assets).
- **Home update card** (GitHub update check): a `secondaryContainer` card above In Progress, "Tsuzuki 1.1.0 is available" / "Get the new version on GitHub.", a "Download" text button (opens the release page in the browser) and a close icon button; gone for that version once closed or used.
- Pure black (AMOLED): in the dark theme `background`, `surface`, `surfaceDim` and `surfaceContainerLowest` are black and the other surface containers move towards black, keeping 60 % of their color (`design/tokens.json`, `color.pureBlack`). Works with both color sources.
- The settings column is capped at the reading width on large screens.

### Android notifications (not in the prototype)

- Channels "New episodes" / "Neue Folgen", "Activity" / "Aktivität", "Follows", "Other" / "Sonstiges" (low importance). Small icon: the 続 glyph (`ic_stat_tsuzuki`), the system tints it.
- New episode: title = media title, text "Episode 5 is out" / "Folge 5 ist da", time = airing time; tap opens the detail page. AniList notifications: the same text as the notifications screen, without styling, the subline (list update or reason) as text; tap opens the profile (follow), the detail page (media) or the notifications screen (activity).
- One group per channel with a summary, so several fold into one.
- Hint after the login (an `AlertDialog` with the glyph as icon): "Stay up to date?" with what Tsuzuki would announce and that each kind can be switched in Android's settings, "Allow notifications" / "Not now", then Android's own permission dialog. Only once.

### Widgets (not in the prototype)

Three Glance widgets (`feature/widgets`): "In Progress" / "Aktuell dabei", "Next episode" / "Nächste Folge", "Friends' activity" / "Freunde-Aktivität". 4 × 2 by default, resizable down to 2 × 1.

- **Theme:** the app's appearance settings. Material You with the system theme mode uses Glance's dynamic colors, which follow wallpaper changes by themselves; AniList blue, a forced light or dark mode and pure black use the app's schemes (`colorScheme(context, ...)` in `core/designsystem`). Background `surfaceContainer` (Glance's dynamic default for Material You), text `onSurface` / `onSurfaceVariant`, accents `primary`. Glance draws the system font; sizes and weights come from `TsuzukiTypography`.
- **Layouts** (`WidgetLayout`): *row* for low widgets (2 × 1, 4 × 1): one item, cover from 180 dp width; *single* for narrow ones (2 × 2): one item with a small cover, title and a large value at the bottom; *list* from 200 × 130 dp: a title bar (続 glyph + name, opens the tab) and the items.
- **Rows:** 40 × 60 cover with the cover color while loading (small radius), title (titleSmall, 2 lines), meta line (labelMedium). Tapping a row opens the media (In Progress and Next episode in the Lists tab, so tablets get list-detail; Friends' activity in the Home tab, status posts open the author's profile).
- **In Progress:** Home's In Progress (watching, reading, repeating; last changed first), progress "x / y", the +1 button (primary, 48 dp, medium radius; RemoteViews can't morph). No +1 at the total, and the +1 on the last episode opens the app's list editor instead of completing the entry (the widget can't offer Undo; decided by Tobias, 2026-10-01). A footer "1 change waiting to be sent" while changes are queued. The single layout adds a progress bar.
- **Next episode:** soonest first; meta "Episode 6 · Fri 17:30 · 2 behind" (aired episodes not watched yet). Within a day a live countdown ("4:57:12", a `Chronometer`, so it ticks without redraws), further away "in 2 days", after airing "Aired" until the next update. No scrolling list (chronometers in widget collections don't tick): it shows as many whole rows as fit. Footer when the last update failed ("Offline · times may have changed").
- **Friends' activity:** avatar, the activity as plain text ("Fern watched episode 5 of Frieren"; status posts as "Name: text" with spoilers replaced), its time (clock time today, weekday this week, else the date: never stale like "3 hours ago"), the cover on the right from 180 dp. Footer "Updated 14:05" / "Offline · updated 14:05".
- **States:** logged out or guest: "Log in to Tsuzuki" with a line per widget, tap opens the app. Empty: "Nothing in progress", "No episodes coming up", "Nothing new yet". Before the first fetch: "Loading…".
- **Picker previews:** Android 15+ shows previews the widgets draw themselves with sample data (`providePreview`, published once per installed version); Android 12 to 14 show static layouts (`res/layout/widget_preview_*`) in the system's Material You palette.

### Character / Staff (not in the prototype)

Follow the detail screen's language: header with 112 × 160 image, name (headlineMedium), native name, favourite toggle; description with "Read more" and spoilers; then a grid of media covers with role labels (character) or a list of characters voiced + production roles (staff).
