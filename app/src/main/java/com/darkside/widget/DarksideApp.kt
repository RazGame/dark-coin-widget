package com.darkside.widget

import android.app.Application
import com.darkside.widget.auth.AuthManager
import com.google.android.material.color.DynamicColors

class DarksideApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Apply Material You Dynamic Color to all Activities on Android 12+ (API 31+)
        DynamicColors.applyToActivitiesIfAvailable(this)
        AuthManager.init(this)
    }

    companion object {
        lateinit var instance: DarksideApp
            private set
    }
}
