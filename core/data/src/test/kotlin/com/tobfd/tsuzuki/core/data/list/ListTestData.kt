package com.tobfd.tsuzuki.core.data.list

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.parseResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.benasher44.uuid.uuid4
import com.tobfd.tsuzuki.core.data.TestApollo
import okio.Buffer

/** A list entry as AniList returns it (`MediaListEntryFull`), shaped like the Frieren entry. */
internal fun entryJson(
    id: Int,
    mediaId: Int = id * 10,
    title: String = "Media $mediaId",
    status: String = "CURRENT",
    progress: Int = 0,
    episodes: Int? = 28,
    scoreRaw: Int = 0,
    customLists: Map<String, Boolean> = emptyMap(),
    type: String = "ANIME"
): String {
    val lists = customLists.entries.joinToString(",") { (name, enabled) -> """{"name":"$name","enabled":$enabled}""" }
    return """
        {"__typename":"MediaList","id":$id,"mediaId":$mediaId,"status":"$status","score":${scoreRaw / 10.0},
         "scoreRaw":$scoreRaw,"progress":$progress,"progressVolumes":0,"repeat":0,"priority":0,"private":false,
         "notes":null,"hiddenFromStatusLists":false,"customLists":[$lists],
         "startedAt":{"__typename":"FuzzyDate","year":2026,"month":1,"day":5},
         "completedAt":{"__typename":"FuzzyDate","year":null,"month":null,"day":null},
         "createdAt":1700000000,"updatedAt":1700000000,
         "media":{"__typename":"Media","id":$mediaId,"type":"$type","format":"TV","status":"FINISHED",
          "episodes":${episodes ?: "null"},"chapters":null,"volumes":null,"isAdult":false,"averageScore":91,
          "seasonYear":2023,"startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
          "title":{"__typename":"MediaTitle","userPreferred":"$title","romaji":"$title","english":null,"native":null},
          "coverImage":{"__typename":"MediaCoverImage","large":"https://img/$mediaId.jpg","medium":null,"color":"#e4a15d"},
          "nextAiringEpisode":null}}
    """.trimIndent()
}

/** A `MediaListCollection` chunk with one group per (name, isCustomList, entries). */
internal fun collectionJson(hasNextChunk: Boolean, vararg groups: Triple<String, Boolean, List<String>>): String {
    val lists = groups.joinToString(",") { (name, custom, entries) ->
        """{"__typename":"MediaListGroup","name":"$name","status":${if (custom) "null" else "\"CURRENT\""},
            "isCustomList":$custom,"isSplitCompletedList":false,"entries":[${entries.joinToString(",")}]}"""
    }
    val collection = """{"__typename":"MediaListCollection","hasNextChunk":$hasNextChunk,"lists":[$lists]}"""
    return """{"data":{"MediaListCollection":$collection}}"""
}

internal fun saveResponseJson(entry: String) = """{"data":{"SaveMediaListEntry":$entry}}"""

internal const val DELETE_RESPONSE_JSON =
    """{"data":{"DeleteMediaListEntry":{"__typename":"Deleted","deleted":true}}}"""

internal fun validationErrorJson(field: String, message: String) =
    """{"errors":[{"message":"validation","status":400,"validation":{"$field":["$message"]}}],"data":null}"""

internal const val NOT_FOUND_JSON = """{"errors":[{"message":"Not Found.","status":404}],"data":null}"""

internal const val SERVER_ERROR_JSON = """{"errors":[{"message":"Internal Server Error","status":500}],"data":null}"""

internal fun <D : Operation.Data> TestApollo.enqueueJson(operation: Operation<D>, json: String) {
    queue.enqueue(operation.parseResponse(Buffer().writeUtf8(json).jsonReader()))
}

internal fun <D : Operation.Data> TestApollo.enqueueOffline(operation: Operation<D>) {
    queue.enqueue(ApolloResponse.Builder(operation, uuid4()).exception(ApolloNetworkException("offline")).build())
}
