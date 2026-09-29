package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.media.MediaRepository
import com.tobfd.tsuzuki.core.model.MediaDetail
import com.tobfd.tsuzuki.core.model.MediaInfo
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/** In-memory [MediaRepository]; set [detail] to what the page should show. */
class FakeMediaRepository(detail: Result<MediaDetail>? = null) : MediaRepository {
    val detail = MutableStateFlow(detail)
    var favouriteResult: Result<Unit> = Result.success(Unit)
    val favouriteToggles = mutableListOf<Int>()
    var observeCalls = 0
        private set

    override fun observeDetail(mediaId: Int): Flow<Result<MediaDetail>> {
        observeCalls++
        return detail.filterNotNull()
    }

    override suspend fun toggleFavourite(mediaId: Int, type: MediaType): Result<Unit> {
        favouriteToggles += mediaId
        return favouriteResult
    }

    companion object {
        /** A detail page with nothing but [media]. */
        fun detailOf(media: MediaLite, isFavourite: Boolean = false) = MediaDetail(
            media = media,
            bannerUrl = null,
            coverUrl = null,
            descriptionHtml = null,
            genres = emptyList(),
            tags = emptyList(),
            info = MediaInfo(null, null, null, null, null, emptyList(), null, null, null),
            isFavourite = isFavourite,
            siteUrl = "https://anilist.co/anime/${media.id}",
            rankings = emptyList(),
            streamingLinks = emptyList(),
            trailer = null,
            relations = emptyList(),
            characters = emptyList(),
            staff = emptyList(),
            statusDistribution = emptyMap(),
            scoreDistribution = emptyList(),
            recommendations = emptyList(),
            following = emptyList()
        )
    }
}
