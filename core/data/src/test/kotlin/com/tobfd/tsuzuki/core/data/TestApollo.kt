package com.tobfd.tsuzuki.core.data

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.composeJsonRequest
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.testing.QueueTestNetworkTransport
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.tobfd.tsuzuki.core.network.cache.Cache
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import okio.Buffer

/** Apollo client with queued fake responses and an in-memory normalized cache. */
internal class TestApollo {
    val queue = QueueTestNetworkTransport()
    val transport = CountingNetworkTransport(queue)
    val client: ApolloClient = with(Cache) {
        ApolloClient.Builder().networkTransport(transport).cache(MemoryCacheFactory()).build()
    }

    /** Requests that reached the network, so tests can assert "no request". */
    val requests: Int get() = transport.requests.get()

    val operations: List<Operation<*>> get() = transport.operations.toList()
}

internal class CountingNetworkTransport(private val delegate: NetworkTransport) : NetworkTransport {
    val requests = AtomicInteger()

    /** Every operation that reached the network, in order. */
    val operations: MutableList<Operation<*>> = Collections.synchronizedList(mutableListOf())

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> {
        requests.incrementAndGet()
        operations += request.operation
        return delegate.execute(request)
    }

    override fun dispose() = delegate.dispose()
}

/** The JSON body Apollo would POST for this operation (query, operationName and variables). */
internal fun Operation<*>.requestJson(): String {
    val buffer = Buffer()
    BufferedSinkJsonWriter(buffer).use { composeJsonRequest(it, CustomScalarAdapters.Empty) }
    return buffer.readUtf8()
}

/** Only the `variables` object of [requestJson]: what AniList filters by. */
internal fun Operation<*>.requestVariables(): String =
    requestJson().substringAfter("\"variables\":").substringBefore(",\"query\"")

/** A clock tests can move forward. */
internal class MutableClock(var now: Instant) : Clock() {
    fun advanceBy(duration: Duration) {
        now += duration
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
