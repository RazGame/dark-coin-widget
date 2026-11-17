package com.darkcoin.widget.auth

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.darkcoin.widget.api.UserProfile

/** Private app storage. All session/profile transitions are atomic under this object's lock. */
object AuthManager {
    private lateinit var prefs: SharedPreferences
    private var generation = 0L
    private var profileRevision = 0L

    // Deliberately not a data class: toString() must never print credentials.
    class Session internal constructor(
        val token: String, val uuid: String?,
        internal val generation: Long, internal val revision: Long
    )

    fun init(context: Context) = initPreferences(
        context.getSharedPreferences("dark_coin_auth_prefs", Context.MODE_PRIVATE)
    )

    @Synchronized
    internal fun initPreferences(preferences: SharedPreferences) {
        prefs = preferences
        generation++
        profileRevision++
    }

    val isLoggedIn: Boolean get() = session() != null
    val userUuid: String? get() = prefs.getString("user_uuid", null)
    val userName: String get() = prefs.getString("user_name", "Пользователь") ?: "Пользователь"
    val balance: Int get() = prefs.getInt("user_balance", 0)
    val lastSyncTimestamp: Long get() = prefs.getLong("last_sync_timestamp", 0L)

    @Synchronized
    fun session(): Session? = prefs.getString("auth_token", null)?.takeIf { it.isNotBlank() }
        ?.let { Session(it, userUuid, generation, profileRevision) }

    @Synchronized
    fun currentGeneration(): Long = generation

    @Synchronized
    fun isCurrent(session: Session): Boolean = session.generation == generation && isLoggedIn

    @Synchronized
    fun saveSession(expectedGeneration: Long, token: String, profile: UserProfile): Boolean {
        if (generation != expectedGeneration || token.isBlank()) return false
        generation++
        profileRevision++
        writeProfile(prefs.edit().putString("auth_token", token), profile).apply()
        return true
    }

    @Synchronized
    fun updateProfile(session: Session, profile: UserProfile): Boolean {
        if (!isCurrent(session) || session.revision != profileRevision) return false
        writeProfile(prefs.edit(), profile).apply()
        return true
    }

    /** A QR mutation makes any profile request that started earlier obsolete. */
    @Synchronized
    fun invalidateProfile(session: Session): Boolean {
        if (!isCurrent(session)) return false
        profileRevision++
        return true
    }

    @Synchronized
    fun clearSession(expected: Session? = null): Boolean {
        if (expected != null && !isCurrent(expected)) return false
        generation++
        profileRevision++
        prefs.edit().clear().apply()
        return true
    }

    /** Must be called on the main thread; CookieManager completes removal asynchronously. */
    fun clearWebSession(onComplete: () -> Unit = {}) {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete()
        }
    }

    private fun writeProfile(editor: SharedPreferences.Editor, profile: UserProfile) = editor
        .putString("user_uuid", profile.uuid)
        .putString("user_name", profile.name.ifBlank { "Пользователь" })
        .putInt("user_balance", profile.balance)
        .putLong("last_sync_timestamp", System.currentTimeMillis())
}
