# AniList API rules

Source: docs.anilist.co (read 2026-09-28). Official docs: https://docs.anilist.co

## Basics

- One endpoint: `POST https://graphql.anilist.co`, JSON body `{ "query": ..., "variables": ... }`, headers `Content-Type: application/json`, `Accept: application/json`.
- Authenticated requests add `Authorization: Bearer <token>`. Guest requests send no header.
- Schema: download via Apollo introspection (`./gradlew :core:network:downloadAnilistApolloSchemaFromIntrospection`) and commit `schema.graphqls`. Refresh it when a query needs a new field.
- Operations live in `core/network/src/main/graphql/com/tobfd/tsuzuki/core/network/` (moved there from the handoff's `graphql/` folder in M2), next to `schema.graphqls` and `extra.graphqls`. Apollo codegen validates them on every build. Known warning: `User.stats` (activity history in `UserProfile`) is deprecated in the schema; it stays until M9, which checks for a replacement (roadmap item).

## Login (OAuth implicit grant)

1. Tobias registers the client once at https://anilist.co/settings/developer: name "Tsuzuki for AniList", redirect `tsuzuki://auth`. The client ID is not secret, but it lives in `local.properties` → `BuildConfig.ANILIST_CLIENT_ID`.
2. "Log in with AniList" opens a Custom Tab with
   `https://anilist.co/api/v2/oauth/authorize?client_id=<ID>&response_type=token`
3. The user approves; AniList redirects to `tsuzuki://auth#access_token=<JWT>&token_type=Bearer&expires_in=<seconds>`. The token is in the **fragment**, not the query string.
4. `MainActivity` (`launchMode="singleTask"`) has an intent filter for scheme `tsuzuki`, host `auth` (`BROWSABLE`, `DEFAULT`). Handle it in `onCreate` and `onNewIntent`, parse `Uri.fragment`, store the token, then call the `Viewer` query.
5. The token is a JWT: payload `sub` = user id, `exp` = expiry (unix seconds). Tokens last about one year. **There is no refresh token and no scopes.** Warn 14 days before `exp`; after expiry, or on any 401, clear the session and show login.
6. Store the token encrypted (DataStore + Tink AEAD, Keystore master key). Never log it, never put it in crash reports.

## Rate limit

- Normal limit 90 requests/min. **Currently degraded to 30 requests/min.** On top of that a burst limiter blocks many requests in a very short time.
- Response headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`. On 429: `Retry-After` (seconds) and `X-RateLimit-Reset` (unix time), plus a GraphQL error "Too Many Requests." with `status: 429`. Exceeding the limit means a one-minute timeout.
- `RateLimitInterceptor` (OkHttp, app-wide singleton):
  - Token bucket sized from the last `X-RateLimit-Limit` (start at 30), refilled per minute; requests wait for a token instead of failing.
  - At most 2 requests in flight and at least ~300 ms between request starts (burst limiter).
  - On 429: block all requests until `Retry-After` has passed, then retry the request once. Surface `AppError.RateLimited` so the UI can show "AniList is busy, retrying in 30 s".
  - Log remaining quota in debug builds.
- Budget per screen (keep to it): Home 1 (+1 per extra feed page), Lists 0 (Room; sync = 1 per type per chunk), Detail 1, Browse 1 per results page, Profile 1, Notifications 1 per page, Settings 0 (+1 per change). Widgets: see Widgets below.
- Search: debounce 400 ms, min 2 characters, cancel in-flight searches when the text changes.

## Errors

- **Errors can arrive with HTTP 200.** Always check `response.errors` (Apollo: `ApolloResponse.errors` / `exception`).
- Error shape: `{ "message": ..., "status": <http code>, "locations": [...] }`; validation errors add a `validation` map with field messages.
- Map to `AppError`:
  - no network / timeout → `Offline`
  - 429 → `RateLimited(retryAfterSec)`
  - 403 with "API has been temporarily disabled" → `ApiUnavailable` (show a banner, keep working from cache)
  - 401, or "Invalid token" → `Unauthorized` (session ends)
  - 404 / "Not Found." → `NotFound`
  - `validation` present → `Validation(fields)` (shown inline in the list editor)
  - anything else → `Unknown(message)`

## Pagination

- `Page(page, perPage)` wraps list fields; one list field per `Page`. `perPage` max 50 (larger values are capped).
- **Only `pageInfo.hasNextPage` is reliable.** Do not request or show `total`, `lastPage` or "page X of Y".
- Paging 3 key = page number, start at 1, next key = page + 1 while `hasNextPage`.
- The Home feed does not use Paging 3: 25 per page (`Home` for page 1, `ActivityFeed` after that), and the next page only when the user taps "Load more". Activities already shown are left out when a page repeats them (new activities push older ones onto the next page).
- Several `Page`s in one query are fine with aliases (see `Home`, `BrowseHome`).

## Lists

- `MediaListCollection(userId, type)` returns the whole list grouped into `lists` by status, **including custom lists** (`isCustomList`). User id comes from `Viewer`.
- Use `forceSingleCompletedList: true` so "Completed" isn't split by format.
- Large lists are chunked: request with `chunk` (1, 2, ...) and `perChunk` (max 500) while `hasNextChunk` is true.
- An entry appears in every custom list it belongs to plus its status list: dedupe by entry `id` when writing to Room.
- `score` returns the viewer's score format; request `scoreRaw: score(format: POINT_100)` and store that, convert for display. Save with `scoreRaw` (0-100) so the format never matters when writing.
- `Media.mediaListEntry` needs auth; it is `null` for guests and for media not on the list.
- `customLists(asArray: true)` lists **every** custom list of the list type with `enabled` for this entry, in the viewer's order: it gives both the membership and the names for the editor. Saving sends the names of the lists the entry should be on.
- Scores in other formats are derived from `scoreRaw` the way AniList shows them: POINT_10_DECIMAL = raw / 10, POINT_10 and POINT_5 rounded (raw / 10, raw / 20), POINT_3 = 1 up to 35, 2 up to 60, else 3. Writing: stars × 20, smileys 35 / 60 / 85.
- The list sync runs with `doNotStore(true)`: the lists live in Room only, not also in the Apollo cache.
- A removed date is sent as a `FuzzyDateInput` with all parts `null`.
- "Add to list" (detail page) is the one list change not queued: `SaveMediaListEntry(mediaId, status: PLANNING)` goes out at once, because only AniList hands out the new entry's id. Offline it fails with a message.

## Mutations used in v1

| Mutation | Use |
|---|---|
| `SaveMediaListEntry` | Create (with `mediaId`) or update (with `id`) an entry: +1, status, score, editor save. Send only changed fields. |
| `DeleteMediaListEntry(id)` | Remove from list. |
| `ToggleLikeV2(id, type: ACTIVITY)` | Like / unlike an activity. |
| `ToggleFavourite(animeId / mangaId / characterId / staffId)` | Heart on detail pages. |
| `ToggleFollow(userId)` | Follow / unfollow. |
| `UpdateUser(titleLanguage, displayAdultContent, scoreFormat)` | Settings synced to AniList: one request per change, only the changed field. |

All of them are optimistic in the UI and rolled back on error. List mutations go through the offline queue (see `CLAUDE.md`).

`UpdateUser` is online only: Settings shows the new value while it is saved and goes back if AniList refuses it (one change at a time). On success the cached viewer options are replaced with what AniList returns, the Apollo cache is cleared (cached `userPreferred` titles and scores followed the old options) and the titles in Room are picked again from the stored romaji / English / native titles (`MediaListDao.applyTitleLanguage`, romaji as fallback like AniList), so the lists follow without a sync.

## Notifications

- `Notifications(page, types, reset)`: 25 per page, `type_in` per filter chip (the mapping is in `notifications.graphql`). Types the query has no fragment for (forum, submissions) arrive with only `__typename` and are left out, but still count for the unread position.
- The API has no per-item read state. The first page of each visit to the screen sends `resetNotificationCount: true` and, in the same request, `Viewer { unreadNotificationCount }` before `Page` (`@include(if: $reset)`), so the count from before the reset comes back. The newest that many notifications of the All list are unread; filtered lists highlight the ones All marked. In case AniList resolves the reset first (count 0), the badge count from when the screen opened is used when higher. A page from the cache (offline) doesn't count as reset; the next load sends it again.
- "Mark all as read" is `MarkNotificationsRead` (`perPage: 1`, reset), one request.
- Consecutive `ACTIVITY_LIKE` notifications on the same `activityId` are grouped into one row, also across a page break (a like run at the end of a page waits for the next page unless it is all the page has).

## Widgets

The home-screen widgets (`feature/widgets`, Glance) read Room like the Lists tab. Only two of them make requests, only while one of them is on a home screen, and never in a loop (`WidgetWork`):

- **In Progress:** no request of its own. Its +1 is `ListRepository.plusOne`, so it goes through the mutation queue like the app's.
- **Next episode:** `NextEpisodes(ids)` (`widgets.graphql`): `Page(perPage: 50) { media(id_in: $ids, type: ANIME) { id status episodes nextAiringEpisode { episode airingAt } } }` for the viewer's watched or rewatched anime that are releasing or not yet released, soonest first (more than 50 are left to the list sync). `NetworkOnly` without storing, written into `media_lite` (`next_airing_episode`, `next_airing_at`). The next request goes out 2 minutes after the soonest episode airs, but never sooner than an hour after the last one and never later than 12 hours; temporary errors back off exponentially from an hour. Between requests a request-free redraw runs when an episode airs. The list sync fills the same columns from `MediaCard.nextAiringEpisode`, so the widget is filled before its first request.
- **Friends' activity:** `ActivityFeed(page: 1, isFollowing: true)` every 3 hours (periodic WorkManager work, network required), exponential backoff from 15 minutes on offline, 429 or API errors. The newest 10 are kept in Room (`friend_activity`, replaced as a whole) and shown in between and offline. One extra request right after a login.
- A logout clears the widget data with the rest of the database; the workers then stop at `Unauthorized` and the widgets show the log-in state.
- Images (covers, avatars) come from Coil's cache at their drawn size; they are not AniList API requests.

## Android notifications

The Android notifications (`feature/notifications`, `alerts` package) add at most one small request every 30 minutes, and none when they are switched off (`AlertCoordinator`):

- **New episodes:** no request. A local `AlarmManager` alarm fires at the next `next_airing_at` in Room (anime being watched or rewatched, filled by the list sync and the Next episode widget) and announces every episode that aired since the last plan, at most 6 hours late. Exact when the user allows alarms and reminders, otherwise as close as Android allows. AniList's own `AIRING` notifications are never shown as Android notifications, they would be duplicates.
- **AniList notifications:** periodic WorkManager work about every 30 minutes (network and battery not low required). `UnreadNotificationCount` first; only if the count went up since the last check, one `Notifications(page: 1, reset: false)` (`NetworkOnly`) for the newest page, of which the new ones are shown (consecutive likes on one activity as one). Failures wait for the next period, no retry. The first check of a session only remembers the count, and every count the app showed itself (badge refresh, opening the notifications screen, mark all read) counts as seen, so nothing already seen in the app is announced. Never resets the unread count.
- Before planning an alarm or sending a request the app checks that its notifications and the matching channels are on; otherwise there is no alarm and no periodic work. Logout or guest mode cancels both and removes the posted notifications.

## Caching (Apollo normalized cache)

The cache is the `com.apollographql.cache` library (memory in front of SQLite `apollo.db`). `extra.graphqls` gives `Media`, `MediaList`, `User`, `Character` and `Staff` an `id`-based cache key (`@typePolicy`), so one record per object is shared by every query; the compiler plugin generates the `Cache` object that `NetworkModule` installs. Logout clears the whole cache, and so does a saved change to the AniList options (see Mutations).

| Data | Fetch policy | Max age |
|---|---|---|
| Viewer | CacheFirst, then network in background | 1 h |
| Media detail | CacheAndNetwork (show cache, refresh) | 1 h (network refresh only if older) |
| Home trending / Browse rows | CacheFirst | 30 min |
| Search results | NetworkFirst | none |
| Genres and tags | NetworkFirst once per app run (first filter sheet), then from memory | app run |
| Character / staff page | CacheFirst while fresh, else NetworkFirst | 1 h |
| Notifications, feeds | NetworkFirst, cache as offline fallback | none |

Own lists are not read from Apollo but from Room (offline first).

## Adult content and user options

- Always pass `isAdult: false` unless the user's AniList option `displayAdultContent` is true; then leave the `isAdult` variable out (the queries declare it without a default, so it is not set). Never send `true`, which returns adult media only, and never an explicit `null`: AniList filters on it and returns nothing (checked against the live API, 2026-09-30). Guests never see adult content. Connections without an `isAdult` argument (character appearances, staff roles) are filtered on the client.
- "Ecchi" is **not** adult on AniList; don't try to reclassify it, just respect the flag.
- User-generated text (activities, bios) may contain anything; show it as text, never auto-load embedded images in v1.
- Title language: use `title.userPreferred` (AniList already applies the user's choice). Guests get AniList's default through `userPreferred` (romaji); Settings offers the title language only to logged-in users, since it is an AniList option.
- Staff and character names: `name.userPreferred`.

## Text and HTML

- `description(asHtml: true)` returns a small HTML subset: `<br>`, `<i>`, `<b>`, `<a>`, `<p>`. Convert to `AnnotatedString` with `AnnotatedString.fromHtml` and custom handling for links.
- Spoilers in AniList markdown use `~!spoiler text!~`; in HTML output they come wrapped in a spoiler span (believed to be `<span class='markdown_spoiler'>`; check a real response before relying on it). Render them hidden until tapped.
- Activity text can contain images and embeds (`img(...)`, `youtube(...)`, `webm(...)`). In v1 render them as links, not inline media.

## Terms that affect the app

- Free, non-commercial use is fine. No ads or paid features without talking to AniList first.
- Don't store data beyond what the app needs (no crawling or bulk prefetching).
- Name: "Tsuzuki" or "Tsuzuki for AniList", never just "AniList". Say it is unofficial in About and on the login screen.
- If the API is disabled or limits drop further, the app must keep working from cache and say so, not crash or hammer retries.

## Useful ids for manual testing

- Frieren: Beyond Journey's End: media id `154587`
- Tobias: user `tobfd`
