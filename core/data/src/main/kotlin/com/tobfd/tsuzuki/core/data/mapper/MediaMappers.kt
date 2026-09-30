package com.tobfd.tsuzuki.core.data.mapper

import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaFormat
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaTitle
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.PersonLite
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.network.fragment.CharacterLite
import com.tobfd.tsuzuki.core.network.fragment.FeedActivity
import com.tobfd.tsuzuki.core.network.fragment.MediaCard
import com.tobfd.tsuzuki.core.network.fragment.StaffLite
import com.tobfd.tsuzuki.core.network.fragment.UserLite as NetworkUserLite
import java.time.Instant

internal inline fun <reified E : Enum<E>> enumNamed(name: String?): E? =
    name?.let { value -> enumValues<E>().firstOrNull { it.name == value } }

/** Null when AniList sent a media without a type (never seen, but the schema allows it). */
internal fun MediaCard.toModel(): MediaLite? = MediaLite(
    id = id,
    type = enumNamed<MediaType>(type?.rawValue) ?: return null,
    format = enumNamed<MediaFormat>(format?.rawValue),
    status = enumNamed<MediaStatus>(status?.rawValue),
    episodes = episodes,
    chapters = chapters,
    volumes = volumes,
    title = MediaTitle(
        userPreferred = title?.userPreferred ?: title?.romaji ?: title?.english ?: title?.native ?: "",
        romaji = title?.romaji,
        english = title?.english,
        native = title?.native
    ),
    coverUrl = coverImage?.large ?: coverImage?.medium,
    coverColor = coverImage?.color,
    year = seasonYear ?: startDate?.year,
    averageScore = averageScore,
    nextAiringEpisode = nextAiringEpisode?.episode,
    isAdult = isAdult ?: false
)

internal fun NetworkUserLite.toModel() = UserLite(id = id, name = name, avatarUrl = avatar?.medium)

internal fun CharacterLite.toPerson() = PersonLite(id, name?.userPreferred.orEmpty(), image?.medium)

internal fun StaffLite.toPerson() = PersonLite(id, name?.userPreferred.orEmpty(), image?.medium)

/** List and text activities; other kinds (and ones missing their user or media) are skipped. */
internal fun FeedActivity.toModel(): Activity? {
    onListActivity?.let { list ->
        return Activity.ListUpdate(
            id = list.id,
            user = list.user?.userLite?.toModel() ?: return null,
            createdAt = Instant.ofEpochSecond(list.createdAt.toLong()),
            likeCount = list.likeCount,
            isLiked = list.isLiked ?: false,
            replyCount = list.replyCount,
            siteUrl = list.siteUrl,
            status = list.status ?: return null,
            progress = list.progress?.takeIf { it.isNotBlank() },
            media = list.media?.mediaCard?.toModel() ?: return null
        )
    }
    onTextActivity?.let { text ->
        return Activity.Text(
            id = text.id,
            user = text.user?.userLite?.toModel() ?: return null,
            createdAt = Instant.ofEpochSecond(text.createdAt.toLong()),
            likeCount = text.likeCount,
            isLiked = text.isLiked ?: false,
            replyCount = text.replyCount,
            siteUrl = text.siteUrl,
            html = text.text.orEmpty()
        )
    }
    return null
}
