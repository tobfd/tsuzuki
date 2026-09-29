package com.tobfd.tsuzuki.core.data.list

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the device can reach the internet right now. */
interface NetworkMonitor {
    fun isOnline(): Boolean
}

internal class ConnectivityNetworkMonitor @Inject constructor(@ApplicationContext private val context: Context) :
    NetworkMonitor {
    override fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
