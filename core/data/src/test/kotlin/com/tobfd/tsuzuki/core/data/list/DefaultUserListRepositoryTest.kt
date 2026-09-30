package com.tobfd.tsuzuki.core.data.list

import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.network.MediaListCollectionQuery
import com.tobfd.tsuzuki.core.network.type.MediaType as NetworkMediaType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUserListRepositoryTest {

    private val apollo = TestApollo()
    private val repository = DefaultUserListRepository(apollo.client)

    private fun query(chunk: Int) =
        MediaListCollectionQuery(userId = 9, type = NetworkMediaType.ANIME, chunk = Optional.present(chunk))

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun list_loadsEveryChunkAndCountsEachEntryOnce() = runTest {
        val favourite = entryJson(1, customLists = mapOf("Favs" to true))
        apollo.enqueueJson(
            query(1),
            collectionJson(true, Triple("Watching", false, listOf(favourite)), Triple("Favs", true, listOf(favourite)))
        )
        apollo.enqueueJson(
            query(2),
            collectionJson(false, Triple("Completed", false, listOf(entryJson(2, status = "COMPLETED"))))
        )

        val list = (repository.list(9, MediaType.ANIME).getOrThrow() as OtherUserList.Visible).list

        assertEquals(2, apollo.requests)
        assertEquals(listOf(1, 2), list.entries.map { it.id })
        assertEquals(listOf("Favs"), list.customLists)
        assertEquals(setOf("Favs"), list.entries.first().customLists)
        assertEquals(MediaListStatus.COMPLETED, list.entries.last().status)
    }

    @Test
    fun list_ofAPrivateUser_saysSo() = runTest {
        apollo.enqueueJson(
            query(1),
            """{"errors":[{"message":"Private User","status":404}],"data":{"MediaListCollection":null}}"""
        )

        assertEquals(OtherUserList.Private, repository.list(9, MediaType.ANIME).getOrThrow())
    }

    @Test
    fun list_offline_fails() = runTest {
        apollo.enqueueOffline(query(1))

        assertEquals(AppError.Offline, repository.list(9, MediaType.ANIME).exceptionOrNull())
    }
}
