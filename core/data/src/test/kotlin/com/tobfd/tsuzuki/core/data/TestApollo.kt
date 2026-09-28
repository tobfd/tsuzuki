package com.tobfd.tsuzuki.core.data

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.testing.QueueTestNetworkTransport
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.tobfd.tsuzuki.core.network.cache.Cache
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow

/** Apollo client with queued fake responses and an in-memory normalized cache. */
internal class TestApollo {
    val queue = QueueTestNetworkTransport()
    val transport = CountingNetworkTransport(queue)
    val client: ApolloClient = with(Cache) {
        ApolloClient.Builder().networkTransport(transport).cache(MemoryCacheFactory()).build()
    }

    /** Requests that reached the network, so tests can assert "no request". */
    val requests: Int get() = transport.requests.get()
}

internal class CountingNetworkTransport(private val delegate: NetworkTransport) : NetworkTransport {
    val requests = AtomicInteger()

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> {
        requests.incrementAndGet()
        return delegate.execute(request)
    }

    override fun dispose() = delegate.dispose()
}

/** A clock tests can move forward. */
internal class MutableClock(var now: Instant) : Clock() {
    fun advanceBy(duration: Duration) {
        now += duration
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
