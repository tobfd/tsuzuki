package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.profile.ProfileRepository
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FollowUser
import com.tobfd.tsuzuki.core.model.UserProfile

/** In-memory [ProfileRepository] that counts requests. */
class FakeProfileRepository : ProfileRepository {
    var profileResult: Result<UserProfile> = Result.failure(IllegalStateException("no profile"))
    var followsResult: Result<ContentPage<FollowUser>> = Result.success(ContentPage(emptyList(), false))
    var followResult: Result<Boolean> = Result.success(true)

    var profileRequests = 0
        private set
    val followRequests = mutableListOf<Boolean>()
    val followToggles = mutableListOf<Int>()

    override suspend fun profile(userId: Int): Result<UserProfile> {
        profileRequests++
        return profileResult
    }

    override suspend fun follows(userId: Int, followers: Boolean): Result<ContentPage<FollowUser>> {
        followRequests += followers
        return followsResult
    }

    override suspend fun toggleFollow(userId: Int): Result<Boolean> {
        followToggles += userId
        return followResult
    }
}
