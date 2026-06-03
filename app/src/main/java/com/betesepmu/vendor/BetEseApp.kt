package com.betesepmu.vendor

import android.app.Application
import android.content.Context
import com.betesepmu.vendor.di.AppContainer

/**
 * Application entry point. Owns the single [AppContainer] that every screen, the spooler
 * service and all integration surfaces resolve their dependencies from.
 */
class BetEseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as BetEseApp).container
    }
}
