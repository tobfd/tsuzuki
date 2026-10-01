# Wear OS: assessment

An assessment only (asked for by Tobias, 2026-10-01). Nothing here is decided or scheduled; a watch app is not part of v1 (`docs/PRODUCT.md`). Library versions are the latest stable ones as of 2026-10-01.

## What it would do

The watch is for the one thing Tsuzuki is named after: what comes next.

- **In Progress** on the wrist: the viewer's current anime and manga, each with "+1".
- **Tile** "In Progress": the two or three most recent entries with a +1 button each.
- **Complication** "Next episode": a countdown to the next airing episode of a current anime, for any watch face.

Out of scope for the watch: browse, search, detail pages, the feed, notifications, editing anything beyond +1. Those stay on the phone ("Open on phone" where needed).

## Recommended architecture: the phone does the talking

The watch app is a companion that never talks to AniList itself. The phone already has everything that has to be right (token, offline queue, rollback, rate limiter); the watch only shows a snapshot and asks the phone to do +1.

```
Watch                                      Phone (existing app)
-----                                      --------------------
Wear Compose M3 screens, Tile,             WearSyncService (WearableListenerService)
Complication                                 - writes the In Progress snapshot as a DataItem
   ^  reads DataItem "/in-progress"            whenever Room changes (like WidgetUpdater)
   |                                         - on "/plus-one/<uuid>": ListRepository.plusOne(entryId)
   +-- writes DataItem "/plus-one/<uuid>"       -> Room + pending_mutation + ListMutationWorker
       (optimistic +1 shown at once)            -> deletes the DataItem when applied
```

- **Data Layer** (`play-services-wearable` 20.0.1): `DataClient` for the snapshot (survives disconnects, syncs when the devices see each other again, also over the cloud when both are online) and for queued +1 requests. No `MessageClient` needed: a +1 written as a DataItem while the phone is away is delivered later, which gives the watch the same "works offline" feel as the phone.
- **One source of truth:** the phone's Room database. The snapshot is small (id, title, cover URL + color, progress, total, next airing time) and is the same data the In Progress widget already reads, so no new AniList requests at all.
- **Login:** none on the watch. If the phone is logged out, the snapshot says so and the watch shows "Log in on your phone". This also means no token ever leaves the phone.
- **+1 path:** watch shows the new progress immediately and writes `/plus-one/<uuid>` with `entryId` and the progress it expects. The phone applies it through `ListRepository` (so undo-before-send, the completion rule from the widget, and validation rollback all behave as on the phone) and pushes the next snapshot, which also corrects the watch if the phone rejected the change. Reaching the last episode opens the list editor **on the phone** (as the widget does since 2026-10-01), via `RemoteActivityHelper` (`wear-remote-interactions` 1.2.0).

### Alternative: standalone watch app

The watch gets the token from the phone over the Data Layer (Google's recommended "token sharing" pattern), stores it with Tink like the phone, and calls AniList itself. Only worth it for LTE watches used without a phone. Costs: a second client against the 30 requests/min limit, a second mutation queue to keep consistent with the phone's, logout and token expiry on two devices, and the token on one more device. Logging in on the watch itself (`RemoteAuthClient` from `wear-phone-interactions`) would need a second AniList API client with a `wear.googleapis.com/3p_auth/...` redirect, and AniList's implicit grant returns the token in the URL fragment, which that flow is not built for. Not recommended for a first version.

## Modules and libraries

| Piece | What | Library (latest stable) |
|---|---|---|
| `wear` | New application module, **same applicationId** `com.tobfd.tsuzuki` and the same signing key as the phone app (the Data Layer only connects apps that match). Single activity, Wear Compose. | `androidx.wear.compose:compose-material3` 1.7.0, `compose-foundation` 1.7.0, Navigation 3 or `wear-compose-navigation` |
| Tile | `TileService` with ProtoLayout Material 3 (`primaryLayout`, `buttonGroup`); clicks handled in `onTileRequest` through the clicked id, then the tile refreshes from the snapshot. | `androidx.wear.tiles:tiles` 1.6.2, `androidx.wear.protolayout:protolayout-material3` 1.4.2 |
| Complication | `SuspendingComplicationDataSourceService`, `SHORT_TEXT` with `TimeDifferenceComplicationText` so the countdown ticks without updates, `RANGED_VALUE` for progress. | `androidx.wear.watchface:watchface-complications-data-source-ktx` 1.3.0 |
| Phone sync | `WearableListenerService` + a small `WearSync` class next to `WidgetUpdater`, in a new `feature/wear-sync` module (or `core/data`). | `play-services-wearable` 20.0.1 |
| Shared code | `core/model` and `core/common` as they are. `core/designsystem` is phone Material 3 and doesn't fit Wear M3; the watch gets its own small theme built from the same tokens (dynamic color on Wear OS 6, AniList blue otherwise). | |

minSdk for the watch: 33 (Wear OS 4) or 34 (Wear OS 5). Glance for Wear Tiles is still alpha, so the tile uses ProtoLayout directly.

## Effort

About one package the size of the Widgets package: one branch, one PR, one watch test by Tobias.

| Task | Size |
|---|---|
| `wear` module, theme, build-logic convention for it, CI | small |
| Phone side: snapshot writer, +1 receiver, tests with fakes | medium |
| Watch In Progress list + entry screen with +1, logged-out / empty / phone-not-reachable states, EN + DE strings | medium |
| Tile with +1 | medium |
| Next episode complication | small |
| Tests (snapshot mapping, +1 round trip, tile layout), check on the Wear OS emulator and a real watch | medium |
| Play: Wear screenshots, form factor listing, review | small |

## Risks

- **Google Play services:** the Data Layer is part of Play services, which is proprietary. That rules out devices without Play services (and an F-Droid build with this feature) and, since the app is GPL-3.0, needs a look at whether linking it is fine or wants an explicit exception or a build flavor without it. Today the app has no Play services dependency at all.
- **Needs a new dependency and a module structure change**, both of which need Tobias's OK under `CLAUDE.md`.
- **Same package and key:** the watch app has to ship with the phone app's applicationId and signing key; a debug watch app only pairs with a debug phone app.
- **Testing:** needs a Wear OS emulator paired with a phone emulator, and ideally a real watch. Does Tobias have one?
- **Phone required:** with the recommended architecture the watch shows the last snapshot and queues +1 while the phone is away; it can't refresh on its own.
- **Tile and complication update limits:** tiles refresh at most every few seconds on request and complications on the system's schedule, so the snapshot push from the phone has to trigger `TileService.getUpdater(...).requestUpdate(...)` and complication update requests, never polling.
- **Maintenance:** a second UI toolkit (Wear Compose and ProtoLayout) next to phone Compose and Glance.
