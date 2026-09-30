package com.tobfd.tsuzuki.core.database.entity

/**
 * The next episode of one media from the widgets' airing refresh. [episode] and [airingAt] (epoch
 * seconds) are null once nothing more is scheduled; [status] and [episodes] only overwrite when set.
 */
data class AiringUpdate(
    val mediaId: Int,
    val episode: Int?,
    val airingAt: Long?,
    val status: String?,
    val episodes: Int?
)
