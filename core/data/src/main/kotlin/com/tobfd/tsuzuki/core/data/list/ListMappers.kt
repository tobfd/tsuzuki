package com.tobfd.tsuzuki.core.data.list

import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.database.entity.CustomListNames
import com.tobfd.tsuzuki.core.database.entity.EntryWithMedia
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.model.DateChange
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaFormat
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaTitle
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.network.SaveMediaListEntryMutation
import com.tobfd.tsuzuki.core.network.fragment.MediaCard
import com.tobfd.tsuzuki.core.network.fragment.MediaListEntryFull
import com.tobfd.tsuzuki.core.network.type.FuzzyDateInput
import com.tobfd.tsuzuki.core.network.type.MediaListStatus as NetworkMediaListStatus
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { value -> enumValues<E>().firstOrNull { it.name == value } }

/** An entry from `MediaListCollection` or `SaveMediaListEntry` as Room rows; null without media. */
internal fun MediaListEntryFull.toEntities(syncRun: Long): Pair<MediaListEntryEntity, MediaLiteEntity>? {
    val card = media?.mediaCard ?: return null
    val type = card.type?.rawValue ?: return null
    val core = mediaListEntryCore
    val entry = MediaListEntryEntity(
        id = core.id,
        mediaId = core.mediaId,
        type = type,
        status = core.status?.rawValue ?: return null,
        scoreRaw = core.scoreRaw?.toInt() ?: 0,
        progress = core.progress ?: 0,
        progressVolumes = core.progressVolumes ?: 0,
        repeat = core.repeat ?: 0,
        isPrivate = core.`private` ?: false,
        notes = core.notes.orEmpty(),
        hiddenFromStatusLists = core.hiddenFromStatusLists ?: false,
        customLists = CustomListNames.encode(customListsOf(core.customLists).filterValues { it }.keys),
        startedYear = core.startedAt?.year,
        startedMonth = core.startedAt?.month,
        startedDay = core.startedAt?.day,
        completedYear = core.completedAt?.year,
        completedMonth = core.completedAt?.month,
        completedDay = core.completedAt?.day,
        updatedAt = core.updatedAt?.toLong(),
        syncRun = syncRun
    )
    return entry to card.toEntity(type)
}

/**
 * `customLists(asArray: true)`: every custom list of the list type with whether the entry is on it,
 * in the viewer's order, e.g. `[{"name": "Favs", "enabled": true}]`.
 */
internal fun customListsOf(json: Any?): Map<String, Boolean> {
    val items = json as? List<*> ?: return emptyMap()
    return buildMap {
        items.forEach { item ->
            val map = item as? Map<*, *> ?: return@forEach
            val name = map["name"] as? String ?: return@forEach
            put(name, map["enabled"] as? Boolean ?: false)
        }
    }
}

private fun MediaCard.toEntity(type: String) = MediaLiteEntity(
    id = id,
    type = type,
    format = format?.rawValue,
    status = status?.rawValue,
    episodes = episodes,
    chapters = chapters,
    volumes = volumes,
    titleUserPreferred = title?.userPreferred ?: title?.romaji ?: title?.english ?: title?.native ?: "",
    titleRomaji = title?.romaji,
    titleEnglish = title?.english,
    titleNative = title?.native,
    coverUrl = coverImage?.large ?: coverImage?.medium,
    coverColor = coverImage?.color,
    year = seasonYear ?: startDate?.year,
    averageScore = averageScore,
    nextAiringEpisode = nextAiringEpisode?.episode,
    isAdult = isAdult ?: false,
    nextAiringAt = nextAiringEpisode?.airingAt?.toLong()
)

internal fun EntryWithMedia.toModel(): MediaListEntry? {
    val media = media?.toModel() ?: return null
    return entry.toModel(media)
}

internal fun MediaListEntryEntity.toModel(media: MediaLite): MediaListEntry? = MediaListEntry(
    id = id,
    mediaId = mediaId,
    status = enumOrNull<MediaListStatus>(status) ?: return null,
    scoreRaw = scoreRaw,
    progress = progress,
    progressVolumes = progressVolumes,
    repeat = repeat,
    isPrivate = isPrivate,
    notes = notes,
    hiddenFromStatusLists = hiddenFromStatusLists,
    customLists = CustomListNames.decode(customLists).toSet(),
    startedAt = FuzzyDate.orNull(startedYear, startedMonth, startedDay),
    completedAt = FuzzyDate.orNull(completedYear, completedMonth, completedDay),
    updatedAt = updatedAt?.let(Instant::ofEpochSecond),
    media = media
)

internal fun MediaLiteEntity.toModel(): MediaLite? = MediaLite(
    id = id,
    type = enumOrNull<MediaType>(type) ?: return null,
    format = enumOrNull<MediaFormat>(format),
    status = enumOrNull<MediaStatus>(status),
    episodes = episodes,
    chapters = chapters,
    volumes = volumes,
    title = MediaTitle(titleUserPreferred, titleRomaji, titleEnglish, titleNative),
    coverUrl = coverUrl,
    coverColor = coverColor,
    year = year,
    averageScore = averageScore,
    nextAiringEpisode = nextAiringEpisode,
    isAdult = isAdult,
    nextAiringAt = nextAiringAt?.let(Instant::ofEpochSecond)
)

/** The row for [entry] after a local change; keeps the sync run of the row it replaces. */
internal fun MediaListEntry.toEntity(syncRun: Long): MediaListEntryEntity = MediaListEntryEntity(
    id = id,
    mediaId = mediaId,
    type = media.type.name,
    status = status.name,
    scoreRaw = scoreRaw,
    progress = progress,
    progressVolumes = progressVolumes,
    repeat = repeat,
    isPrivate = isPrivate,
    notes = notes,
    hiddenFromStatusLists = hiddenFromStatusLists,
    customLists = CustomListNames.encode(customLists),
    startedYear = startedAt?.year,
    startedMonth = startedAt?.month,
    startedDay = startedAt?.day,
    completedYear = completedAt?.year,
    completedMonth = completedAt?.month,
    completedDay = completedAt?.day,
    updatedAt = updatedAt?.epochSecond,
    syncRun = syncRun
)

// JSON for the queue: the changed fields of a SAVE, and the entry before the change (for rollback).

@Serializable
internal data class DateJson(val year: Int? = null, val month: Int? = null, val day: Int? = null)

@Serializable
internal data class DateChangeJson(val date: DateJson? = null)

@Serializable
internal data class ChangesJson(
    val status: String? = null,
    val scoreRaw: Int? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val repeat: Int? = null,
    val isPrivate: Boolean? = null,
    val notes: String? = null,
    val hiddenFromStatusLists: Boolean? = null,
    val customLists: List<String>? = null,
    val startedAt: DateChangeJson? = null,
    val completedAt: DateChangeJson? = null
)

@Serializable
internal data class EntrySnapshot(
    val id: Int,
    val mediaId: Int,
    val type: String,
    val status: String,
    val scoreRaw: Int,
    val progress: Int,
    val progressVolumes: Int,
    val repeat: Int,
    val isPrivate: Boolean,
    val notes: String,
    val hiddenFromStatusLists: Boolean,
    val customLists: String,
    val startedAt: DateJson? = null,
    val completedAt: DateJson? = null,
    val updatedAt: Long? = null,
    val syncRun: Long
)

internal val QueueJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun FuzzyDate.toJson() = DateJson(year, month, day)

private fun DateJson.toModel() = FuzzyDate.orNull(year, month, day)

private fun DateChange.toJson() = DateChangeJson(date?.toJson())

private fun DateChangeJson.toModel() = DateChange(date?.toModel())

internal fun EntryChanges.toJson(): ChangesJson = ChangesJson(
    status = status?.name,
    scoreRaw = scoreRaw,
    progress = progress,
    progressVolumes = progressVolumes,
    repeat = repeat,
    isPrivate = isPrivate,
    notes = notes,
    hiddenFromStatusLists = hiddenFromStatusLists,
    customLists = customLists?.toList(),
    startedAt = startedAt?.toJson(),
    completedAt = completedAt?.toJson()
)

internal fun ChangesJson.toModel(): EntryChanges = EntryChanges(
    status = enumOrNull<MediaListStatus>(status),
    scoreRaw = scoreRaw,
    progress = progress,
    progressVolumes = progressVolumes,
    repeat = repeat,
    isPrivate = isPrivate,
    notes = notes,
    hiddenFromStatusLists = hiddenFromStatusLists,
    customLists = customLists?.toSet(),
    startedAt = startedAt?.toModel(),
    completedAt = completedAt?.toModel()
)

internal fun MediaListEntryEntity.toSnapshot() = EntrySnapshot(
    id = id,
    mediaId = mediaId,
    type = type,
    status = status,
    scoreRaw = scoreRaw,
    progress = progress,
    progressVolumes = progressVolumes,
    repeat = repeat,
    isPrivate = isPrivate,
    notes = notes,
    hiddenFromStatusLists = hiddenFromStatusLists,
    customLists = customLists,
    startedAt = FuzzyDate.orNull(startedYear, startedMonth, startedDay)?.toJson(),
    completedAt = FuzzyDate.orNull(completedYear, completedMonth, completedDay)?.toJson(),
    updatedAt = updatedAt,
    syncRun = syncRun
)

internal fun EntrySnapshot.toEntity() = MediaListEntryEntity(
    id = id,
    mediaId = mediaId,
    type = type,
    status = status,
    scoreRaw = scoreRaw,
    progress = progress,
    progressVolumes = progressVolumes,
    repeat = repeat,
    isPrivate = isPrivate,
    notes = notes,
    hiddenFromStatusLists = hiddenFromStatusLists,
    customLists = customLists,
    startedYear = startedAt?.year,
    startedMonth = startedAt?.month,
    startedDay = startedAt?.day,
    completedYear = completedAt?.year,
    completedMonth = completedAt?.month,
    completedDay = completedAt?.day,
    updatedAt = updatedAt,
    syncRun = syncRun
)

/** True when these changes would leave the entry as [before], e.g. a +1 followed by its Undo. */
internal fun EntryChanges.isNoOpFor(before: EntrySnapshot): Boolean {
    fun <T> same(change: T?, current: T): Boolean = change == null || change == current
    return same(status?.name, before.status) &&
        same(scoreRaw, before.scoreRaw) &&
        same(progress, before.progress) &&
        same(progressVolumes, before.progressVolumes) &&
        same(repeat, before.repeat) &&
        same(isPrivate, before.isPrivate) &&
        same(notes, before.notes) &&
        same(hiddenFromStatusLists, before.hiddenFromStatusLists) &&
        same(customLists, CustomListNames.decode(before.customLists).toSet()) &&
        (startedAt?.let { it.date == before.startedAt?.toModel() } ?: true) &&
        (completedAt?.let { it.date == before.completedAt?.toModel() } ?: true)
}

/** Only the changed fields become variables; the rest stay absent, so AniList keeps them. */
internal fun EntryChanges.toSaveMutation(entryId: Int): SaveMediaListEntryMutation = SaveMediaListEntryMutation(
    id = Optional.present(entryId),
    status = Optional.presentIfNotNull(status?.let { NetworkMediaListStatus.safeValueOf(it.name) }),
    scoreRaw = Optional.presentIfNotNull(scoreRaw),
    progress = Optional.presentIfNotNull(progress),
    progressVolumes = Optional.presentIfNotNull(progressVolumes),
    repeat = Optional.presentIfNotNull(repeat),
    `private` = Optional.presentIfNotNull(isPrivate),
    notes = Optional.presentIfNotNull(notes),
    hiddenFromStatusLists = Optional.presentIfNotNull(hiddenFromStatusLists),
    customLists = Optional.presentIfNotNull(customLists?.toList()),
    startedAt = Optional.presentIfNotNull(startedAt?.toInput()),
    completedAt = Optional.presentIfNotNull(completedAt?.toInput())
)

/** A removed date is sent with every part null, which is how AniList clears it. */
private fun DateChange.toInput() = FuzzyDateInput(
    year = Optional.present(date?.year),
    month = Optional.present(date?.month),
    day = Optional.present(date?.day)
)
