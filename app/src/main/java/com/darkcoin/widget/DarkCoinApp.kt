package com.darkcoin.widget

import android.app.Application
import com.darkcoin.widget.auth.AuthManager
import com.darkcoin.widget.widget.DarkCoinWidgetProvider
import com.google.android.material.color.DynamicColors

class DarkCoinApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Apply Material You Dynamic Color to all Activities on Android 12+ (API 31+)
        DynamicColors.applyToActivitiesIfAvailable(this)
        AuthManager.init(this)
        // Replace launcher-cached loading views after process restart; loading is never persisted.
        DarkCoinWidgetProvider.updateAllWidgets(this)
    }

    companion object {
        lateinit var instance: DarkCoinApp
            private set
    }
}
