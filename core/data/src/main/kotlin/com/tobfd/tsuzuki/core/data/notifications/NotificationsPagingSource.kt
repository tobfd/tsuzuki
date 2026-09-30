package com.tobfd.tsuzuki.core.data.notifications

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.isFromCache
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.core.network.NotificationsQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** The `perPage` of `Notifications`. */
internal const val NOTIFICATIONS_PAGE_SIZE = 25

/**
 * One visit to the notifications screen. The API has no per-item read state, so the newest
 * `unreadNotificationCount` notifications at the moment the screen opens count as unread
 * (docs/ANILIST_API.md, Notifications). The first page loaded during the visit resets the count on AniList.
 */
class NotificationVisit(
    /** The bell's count when the screen opened; used when AniList's own count isn't known. */
    val badgeCountAtOpen: Int
) {
    private val resetClaimed = AtomicBoolean(false)

    /** AniList's count from before the reset, once the first page arrived from the network. */
    @Volatile
    internal var unreadTotal: Int? = null

    /** Unread notifications seen in the All list, so the filtered lists can highlight them too. */
    private val unreadIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** True for the one load that should send `resetNotificationCount`. */
    internal fun claimReset(): Boolean = resetClaimed.compareAndSet(false, true)

    /** The claimed reset didn't reach AniList (error or cached page): the next load sends it again. */
    internal fun releaseReset() = resetClaimed.set(false)

    /** Whether the notification at [index] (0 = newest, counting every type) of [filter]'s list is unread. */
    internal fun isUnread(id: Int, index: Int, filter: NotificationFilter): Boolean {
        if (filter == NotificationFilter.All && index < (unreadTotal ?: badgeCountAtOpen)) {
            unreadIds += id
            return true
        }
        return id in unreadIds
    }
}

/**
 * Pages of `Notifications` for one filter, keyed by page number from 1. Consecutive likes on the same
 * activity become one entry, also across a page break: a like run at the end of a page waits for the next
 * page (unless it is all the page has). Notifications a later page repeats are left out.
 */
internal class NotificationsPagingSource(
    private val apolloClient: ApolloClient,
    private val filter: NotificationFilter,
    private val visit: NotificationVisit,
    /** Called once AniList has reset the unread count. */
    private val onReset: () -> Unit
) : PagingSource<Int, NotificationEntry>() {

    private val seen: MutableSet<Int> = Collections.synchronizedSet(mutableSetOf())

    /** Like runs held back from the end of a page, keyed by the page they are prepended to. */
    private val carried = ConcurrentHashMap<Int, List<NotificationEntry>>()

    override fun getRefreshKey(state: PagingState<Int, NotificationEntry>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, NotificationEntry> {
        val page = params.key ?: 1
        val reset = visit.claimReset()
        val query = NotificationsQuery(
            page = page,
            types = Optional.present(filter.types()),
            reset = Optional.present(reset)
        )
        val response = try {
            apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkFirst).execute()
        } catch (e: ApolloException) {
            if (reset) visit.releaseReset()
            return LoadResult.Error(e.toAppError())
        }
        val data = response.data?.Page
        if (data == null) {
            if (reset) visit.releaseReset()
            return LoadResult.Error(response.appErrorOrNull() ?: AppError.Unknown("AniList returned no notifications"))
        }
        if (reset) {
            if (response.isFromCache) {
                visit.releaseReset()
            } else {
                val before = response.data?.Viewer?.unreadNotificationCount ?: 0
                visit.unreadTotal = maxOf(before, visit.badgeCountAtOpen)
                onReset()
            }
        }

        val offset = (page - 1) * NOTIFICATIONS_PAGE_SIZE
        val entries = data.notifications.orEmpty().mapIndexedNotNull { index, raw ->
            val notification = raw?.toModel() ?: return@mapIndexedNotNull null
            NotificationEntry(notification, visit.isUnread(notification.id, offset + index, filter))
        }.filter { seen.add(it.notification.id) }

        val hasNextPage = data.pageInfo?.hasNextPage == true
        var runs = groupLikes(carried[page].orEmpty() + entries)
        val last = runs.lastOrNull()
        if (hasNextPage && runs.size > 1 && last != null && last.first().notification.isLike()) {
            carried[page + 1] = last
            runs = runs.dropLast(1)
        }
        return LoadResult.Page(
            data = runs.map { it.merged() },
            prevKey = null,
            nextKey = if (hasNextPage) page + 1 else null
        )
    }
}

private fun Notification.isLike(): Boolean = this is Notification.ActivityEvent && kind == ActivityNotificationKind.Like

/**
 * Splits [entries] into runs: consecutive likes on the same activity form one run, every other
 * notification is a run of its own.
 */
internal fun groupLikes(entries: List<NotificationEntry>): List<List<NotificationEntry>> {
    val runs = mutableListOf<MutableList<NotificationEntry>>()
    for (entry in entries) {
        val notification = entry.notification
        val run = runs.lastOrNull()
        val previous = run?.last()?.notification
        if (run != null &&
            notification.isLike() &&
            previous != null &&
            previous.isLike() &&
            (previous as Notification.ActivityEvent).activityId ==
            (notification as Notification.ActivityEvent).activityId
        ) {
            run += entry
        } else {
            runs += mutableListOf(entry)
        }
    }
    return runs
}

/** One entry for a run: the newest notification with every user of the run; unread if any of them is. */
internal fun List<NotificationEntry>.merged(): NotificationEntry {
    val newest = first()
    if (size == 1) return newest
    val event = newest.notification as Notification.ActivityEvent
    val users = flatMap { (it.notification as Notification.ActivityEvent).users }.distinctBy { it.id }
    return NotificationEntry(event.copy(users = users), isUnread = any { it.isUnread })
}
