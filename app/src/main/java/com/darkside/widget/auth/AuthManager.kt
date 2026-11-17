package com.darkside.widget.auth

import android.content.Context
import android.content.SharedPreferences
import com.darkside.widget.widget.DarksideWidgetProvider

object AuthManager {

    private const val PREFS_NAME = "darkside_auth_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_UUID = "user_uuid"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_BALANCE = "user_balance"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userUuid: String?
        get() = prefs.getString(KEY_USER_UUID, null)
        set(value) = prefs.edit().putString(KEY_USER_UUID, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "Михаил") ?: "Михаил"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var balance: Int
        get() = prefs.getInt(KEY_BALANCE, 0)
        set(value) {
            prefs.edit().putInt(KEY_BALANCE, value).apply()
        }

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun saveSession(context: Context, token: String, userUuid: String?, name: String?, balance: Int) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_UUID, userUuid)
            .putString(KEY_USER_NAME, name ?: "Михаил")
            .putInt(KEY_BALANCE, balance)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()

        DarksideWidgetProvider.updateAllWidgets(context)
    }

    fun updateBalance(context: Context, newBalance: Int) {
        this.balance = newBalance
        this.lastSyncTimestamp = System.currentTimeMillis()
        DarksideWidgetProvider.updateAllWidgets(context)
    }

    fun clear(context: Context) {
        prefs.edit().clear().apply()
        DarksideWidgetProvider.updateAllWidgets(context)
    }
}
