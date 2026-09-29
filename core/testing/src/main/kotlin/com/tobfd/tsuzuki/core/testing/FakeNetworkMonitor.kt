package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.list.NetworkMonitor

/** A [NetworkMonitor] tests switch on and off. */
class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}
