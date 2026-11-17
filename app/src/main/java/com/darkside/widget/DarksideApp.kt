package com.darkside.widget

import android.app.Application
import com.darkside.widget.auth.AuthManager

class DarksideApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AuthManager.init(this)
    }

    companion object {
        lateinit var instance: DarksideApp
            private set
    }
}
