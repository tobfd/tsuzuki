package com.tobfd.tsuzuki.core.data.session

import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.model.SessionState
import kotlinx.coroutines.flow.first

/** Whether the viewer turned on adult content at AniList; never for guests. */
internal suspend fun SessionRepository.adultContentAllowed(): Boolean =
    (session.first() as? SessionState.LoggedIn)?.viewer?.options?.displayAdultContent == true

/**
 * The `isAdult` argument for media lists (docs/ANILIST_API.md, Adult content): `false` hides adult
 * media; `null` shows everything and is sent only when the viewer turned adult content on at AniList.
 * `true` would show adult media only, so it is never sent.
 */
internal suspend fun SessionRepository.isAdultArgument(): Optional<Boolean?> =
    Optional.present(if (adultContentAllowed()) null else false)
