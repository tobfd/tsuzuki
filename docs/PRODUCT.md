# Product: decisions and scope

Decided with Tobias during planning (2026-09-28). Changing any of these needs his OK.

## Decisions

| # | Topic | Decision | Why |
|---|---|---|---|
| D1 | Distribution | Open source (GPL-3.0), Play-Store-ready. (Changed by Tobias on 2026-10-01; was "private first".) | Adult content off by default, clean naming, no secrets in the repo. |
| D2 | Scope | Useful every day, not overloaded. v1 = the screens below; everything else comes later. | Keep v1 shippable. |
| D3 | Theme | **Material You (dynamic color) by default.** Settings can switch to **AniList blue**. Theme mode system / light / dark. | Dynamic color exists on every supported device (D6). |
| D4 | Navigation | 4 bottom tabs: **Home, Lists, Browse, Profile**. Notifications = bell with badge in the top app bar. No forum tab. | Few tabs keep the app calm; notifications don't need a permanent tab. |
| D5 | Languages | English and German, per-app language selectable. AniList content stays as the API delivers it. | |
| D6 | Android versions | minSdk 31 (Android 12), targetSdk/compileSdk 37 (Android 17). | Every device gets Material You. Newer features (themed icon, predictive back, per-app language) are used where available. |
| D7 | Name | **Tsuzuki** (続き), store listing "Tsuzuki for AniList". | Short, describes the core action: continue with the next episode. |
| D8 | IDs | Package `com.tobfd.tsuzuki`, OAuth redirect `tsuzuki://auth`. | |

## v1 screens

| Screen | Content |
|---|---|
| Login | "Log in with AniList" (Custom Tab, implicit grant). "Browse without an account" enters guest mode (Browse, Detail, character/staff only). |
| Home | "In Progress" carousel with +1, "Up next from Planning", activity feed (Following / Global) with like, Trending row. Bell and avatar in the top bar. |
| Lists | Anime / Manga toggle, status tabs with counts incl. custom lists, sort + filter, +1 per row, auto-complete at the last episode with Undo, list editor bottom sheet (status, progress, score, dates, rewatches, notes, private, custom lists), remove. |
| Browse | Search with filters (format, status, season, year, genres, tags, sort), quick chips (Trending, Top 100, This season, Top movies, Top manhwa), Trending and Newly added rows. |
| Media detail | Banner, cover, score, ranking, list button (opens editor), favourite, share, description, genres and tags, info, where to watch, relations, characters with voice actors, staff, trailer, status and score distribution, following, recommendations. |
| Character / Staff | Image, names, description, appearances (character) or roles and characters voiced (staff). |
| Notifications | Filter chips (All, Airing, Activity, Follows, Media), grouped likes, mark all read. |
| Profile | Own and other users: banner, avatar, stats, activity history heatmap, recent activity, favourites, stats, social. Follow / unfollow on other profiles. |
| Settings | Colors (Material You / AniList blue), theme mode, app language, title language, score format, adult content, account / logout, about. |
| Widgets | Home-screen widgets (Glance): "Currently watching" with +1, "Next episode" with countdown, "Friends' activity". Moved into v1 by Tobias on 2026-09-30. |

## Later (not in v1)

- Replying to activities and writing status posts.
- Forum (read first), reviews (read, then write), voting on recommendations.
- Detailed stats pages, favourites management, full followers/following management.
- Deep links for anilist.co URLs. (Home-screen widgets, including the airing countdown, moved into v1 on 2026-09-30. Local episode notifications and Android notifications for AniList notifications are the "Android notifications" package after v1, asked for by Tobias on 2026-10-01.)
- Tablet layouts beyond the adaptive basics (the basics are required in v1).

## Ideas parked for later

- Shared element transition from cover to detail (nice in v1 if cheap).
- AMOLED black option (now planned in M11, asked for by Tobias on 2026-09-29).
- Full AniList markdown renderer (spoilers, images, embeds) once activity texts, reviews or bios are shown in full.

## Reference

- AniHyou (github.com/axiel7/AniHyou-android) is an open-source AniList client in Compose. Read it for AniList specifics; check its license before reusing code.
