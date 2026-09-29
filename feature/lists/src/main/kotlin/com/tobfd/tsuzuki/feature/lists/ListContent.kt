package com.tobfd.tsuzuki.feature.lists

import com.tobfd.tsuzuki.core.data.list.UserList
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import java.text.Collator
import java.util.Locale

/** A tab of the Lists screen: a status list or one of the viewer's custom lists. */
sealed interface ListTabKey {
    data class Status(val status: MediaListStatus) : ListTabKey

    data class Custom(val name: String) : ListTabKey

    /** For saved state: "S:CURRENT" or "C:<name>". */
    fun encode(): String = when (this) {
        is Status -> "S:${status.name}"
        is Custom -> "C:$name"
    }

    companion object {
        fun decode(value: String?): ListTabKey? = when {
            value == null -> null
            value.startsWith("S:") -> MediaListStatus.entries.firstOrNull { it.name == value.drop(2) }?.let(::Status)
            value.startsWith("C:") -> Custom(value.drop(2))
            else -> null
        }
    }
}

data class ListTab(val key: ListTabKey, val count: Int)

enum class ListSort {
    Title,
    Score,
    Progress,
    LastUpdated,
    StartDate
}

/** Status tabs in AniList's order; the Lists tab always shows all six. */
internal val STATUS_TAB_ORDER = listOf(
    MediaListStatus.CURRENT,
    MediaListStatus.PLANNING,
    MediaListStatus.COMPLETED,
    MediaListStatus.PAUSED,
    MediaListStatus.DROPPED,
    MediaListStatus.REPEATING
)

/** All status tabs with their counts, then the custom lists (docs/DESIGN.md, Lists). */
internal fun tabsOf(list: UserList): List<ListTab> {
    val inStatusLists = list.entries.filterNot { it.hiddenFromStatusLists }
    val statusTabs = STATUS_TAB_ORDER.map { status ->
        ListTab(ListTabKey.Status(status), inStatusLists.count { it.status == status })
    }
    val customTabs = list.customLists.map { name ->
        ListTab(ListTabKey.Custom(name), list.entries.count { name in it.customLists })
    }
    return statusTabs + customTabs
}

/**
 * The rows to show: the entries of [tab], or with a [query] the matching entries of the whole
 * list (any title language), sorted by [sort].
 */
internal fun rowsOf(
    list: UserList,
    tab: ListTabKey,
    query: String,
    sort: ListSort,
    locale: Locale
): List<MediaListEntry> {
    val search = query.trim()
    val entries = if (search.isNotEmpty()) {
        list.entries.filter { entry -> entry.media.title.all.any { it.contains(search, ignoreCase = true) } }
    } else {
        when (tab) {
            is ListTabKey.Status -> list.entries.filter { it.status == tab.status && !it.hiddenFromStatusLists }
            is ListTabKey.Custom -> list.entries.filter { tab.name in it.customLists }
        }
    }
    return entries.sortedWith(comparatorFor(sort, locale))
}

private fun comparatorFor(sort: ListSort, locale: Locale): Comparator<MediaListEntry> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val byTitle =
        Comparator<MediaListEntry> { a, b ->
            collator.compare(a.media.title.userPreferred, b.media.title.userPreferred)
        }
    return when (sort) {
        ListSort.Title -> byTitle

        ListSort.Score -> compareByDescending<MediaListEntry> { it.scoreRaw }.then(byTitle)

        ListSort.Progress -> compareByDescending<MediaListEntry> { it.progress }.then(byTitle)

        ListSort.LastUpdated -> compareByDescending<MediaListEntry, java.time.Instant?>(nullsFirst()) { it.updatedAt }
            .then(byTitle)

        ListSort.StartDate -> compareByDescending<MediaListEntry, Int?>(nullsFirst()) { it.startedAt?.sortKey() }
            .then(byTitle)
    }
}

/** yyyymmdd with unknown parts as 0, so "2023" sorts before "2023-05". */
private fun FuzzyDate.sortKey(): Int? {
    val year = year ?: return null
    return year * 10_000 + (month ?: 0) * 100 + (day ?: 0)
}
