package com.tobfd.tsuzuki.core.data.session

import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.model.SessionState
import kotlinx.coroutines.flow.first

/** Whether the viewer turned on adult content at AniList; never for guests. */
internal suspend fun SessionRepository.adultContentAllowed(): Boolean =
    (session.first() as? SessionState.LoggedIn)?.viewer?.options?.displayAdultContent == true

/**
 * The `isAdult` argument for media lists (docs/ANILIST_API.md, Adult content): `false` hides adult
 * media; with adult content allowed the variable is left out, which means no filter. Never send
 * `true` (adult media only) or an explicit `null` (AniList then returns nothing). The queries that
 * use it declare `$isAdult` without a default, so leaving it out really means "not set".
 */
internal suspend fun SessionRepository.isAdultArgument(): Optional<Boolean?> =
    if (adultContentAllowed()) Optional.Absent else Optional.present(false)
