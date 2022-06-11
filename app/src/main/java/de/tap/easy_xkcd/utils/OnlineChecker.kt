package de.tap.easy_xkcd.utils

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

class OnlineChecker(private val context: Context) {
    fun isOnline()
            = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager?)
        ?.activeNetworkInfo?.isConnected ?: false
}