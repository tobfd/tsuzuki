# AniList API rules

Source: docs.anilist.co (read 2026-09-28). Official docs: https://docs.anilist.co

## Basics

- One endpoint: `POST https://graphql.anilist.co`, JSON body `{ "query": ..., "variables": ... }`, headers `Content-Type: application/json`, `Accept: application/json`.
- Authenticated requests add `Authorization: Bearer <token>`. Guest requests send no header.
- Schema: download via Apollo introspection (`./gradlew :core:network:downloadAnilistApolloSchemaFromIntrospection`) and commit `schema.graphqls`. Refresh it when a query needs a new field.
- Operations live in `core/network/src/main/graphql/com/tobfd/tsuzuki/core/network/`. The handoff's `graphql/` folder has 22 starting operations (queries, mutations, fragments) that were validated against the schema on 2026-09-28; Apollo codegen re-validates them on every build.

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
- Budget per screen (keep to it): Home 1 (+1 per extra feed page), Lists 0 (Room; sync = 1 per type per chunk), Detail 1, Browse 1 per results page, Profile 1, Notifications 1 per page, Settings 0 (+1 per change).
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
- Several `Page`s in one query are fine with aliases (see `Home`, `BrowseHome`).

## Lists

- `MediaListCollection(userId, type)` returns the whole list grouped into `lists` by status, **including custom lists** (`isCustomList`). User id comes from `Viewer`.
- Use `forceSingleCompletedList: true` so "Completed" isn't split by format.
- Large lists are chunked: request with `chunk` (1, 2, ...) and `perChunk` (max 500) while `hasNextChunk` is true.
- An entry appears in every custom list it belongs to plus its status list: dedupe by entry `id` when writing to Room.
- `score` returns the viewer's score format; request `scoreRaw: score(format: POINT_100)` and store that, convert for display. Save with `scoreRaw` (0-100) so the format never matters when writing.
- `Media.mediaListEntry` needs auth; it is `null` for guests and for media not on the list.

## Mutations used in v1

| Mutation | Use |
|---|---|
| `SaveMediaListEntry` | Create (with `mediaId`) or update (with `id`) an entry: +1, status, score, editor save. Send only changed fields. |
| `DeleteMediaListEntry(id)` | Remove from list. |
| `ToggleLikeV2(id, type: ACTIVITY)` | Like / unlike an activity. |
| `ToggleFavourite(animeId / mangaId / characterId / staffId)` | Heart on detail pages. |
| `ToggleFollow(userId)` | Follow / unfollow. |
| `UpdateUser(titleLanguage, displayAdultContent, scoreFormat)` | Settings synced to AniList. |

All of them are optimistic in the UI and rolled back on error. List mutations go through the offline queue (see `CLAUDE.md`).

## Caching (Apollo normalized cache)

| Data | Fetch policy | Max age |
|---|---|---|
| Viewer | CacheFirst, then network in background | 1 h |
| Media detail | CacheAndNetwork (show cache, refresh) | 1 h (network refresh only if older) |
| Home trending / Browse rows | CacheFirst | 30 min |
| Search results | NetworkFirst | none |
| Genres and tags | CacheFirst | 7 days |
| Notifications, feeds | NetworkFirst, cache as offline fallback | none |

Own lists are not read from Apollo but from Room (offline first).

## Adult content and user options

- Always pass `isAdult: false` unless the user's AniList option `displayAdultContent` is true. Guests never see adult content.
- "Ecchi" is **not** adult on AniList; don't try to reclassify it, just respect the flag.
- User-generated text (activities, bios) may contain anything; show it as text, never auto-load embedded images in v1.
- Title language: use `title.userPreferred` (AniList already applies the user's choice). For guests, use the app setting (Romaji default) and pick `romaji` / `english` / `native` locally.
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
