package com.tobfd.tsuzuki.core.common

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** The tab a screen opened from outside the app is shown in. */
enum class AppTab {
    Home,
    Lists
}

/** A screen that the home-screen widgets and the Android notifications open in the app. */
sealed interface AppDestination {
    data class Tab(val tab: AppTab) : AppDestination

    data class Media(val id: Int, val tab: AppTab) : AppDestination

    /** The list editor of a media on the viewer's list, over the Lists tab. */
    data class ListEditor(val mediaId: Int) : AppDestination

    data class User(val id: Int, val name: String) : AppDestination

    /** The notifications screen, over the Home tab. */
    data object Notifications : AppDestination
}

/**
 * `tsuzuki://open/...` links for [AppDestination]s: widgets and Android notifications start `MainActivity`
 * with one, restricted to the app's own package, and the app shell navigates there.
 */
object AppLink {
    const val SCHEME = "tsuzuki"
    const val HOST = "open"

    fun uri(destination: AppDestination): String {
        val path = when (destination) {
            is AppDestination.Tab -> "tab/${destination.tab.path}"

            is AppDestination.Media -> "media/${destination.id}?tab=${destination.tab.path}"

            is AppDestination.ListEditor -> "editor/${destination.mediaId}"

            is AppDestination.User -> "user/${destination.id}?name=${URLEncoder.encode(
                destination.name,
                Charsets.UTF_8
            )}"

            AppDestination.Notifications -> "notifications"
        }
        return "$SCHEME://$HOST/$path"
    }

    /** The destination of a link from [uri]; null for anything else, including the login redirect. */
    fun parse(uri: String): AppDestination? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.scheme != SCHEME || parsed.host != HOST) return null
        val segments = parsed.rawPath.orEmpty().trim('/').split('/')
        val query = parsed.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }
        return when (segments.firstOrNull()) {
            "tab" -> tabOf(segments.getOrNull(1))?.let { AppDestination.Tab(it) }

            "media" -> segments.getOrNull(1)?.toIntOrNull()?.let {
                AppDestination.Media(it, tabOf(query["tab"]) ?: AppTab.Home)
            }

            "editor" -> segments.getOrNull(1)?.toIntOrNull()?.let { AppDestination.ListEditor(it) }

            "user" -> {
                val id = segments.getOrNull(1)?.toIntOrNull()
                val name = query["name"]
                if (id != null && name != null) AppDestination.User(id, name) else null
            }

            "notifications" -> AppDestination.Notifications

            else -> null
        }
    }

    private val AppTab.path: String get() = name.lowercase()

    private fun tabOf(path: String?): AppTab? = AppTab.entries.firstOrNull { it.path == path }
}
