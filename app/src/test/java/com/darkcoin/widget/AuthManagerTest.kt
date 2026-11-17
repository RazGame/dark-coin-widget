package com.darkcoin.widget

import android.content.SharedPreferences
import com.darkcoin.widget.api.UserProfile
import com.darkcoin.widget.auth.AuthManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class AuthManagerTest {
    @Before fun setup() { AuthManager.initPreferences(memoryPreferences()) }
    private fun login(token: String = "secret-A", balance: Int = 42) {
        assertTrue(AuthManager.saveSession(AuthManager.currentGeneration(), token, UserProfile("Alice", balance, "uuid")))
    }

    @Test fun logoutRejectsLateProfileAndRemovesAllPersonalData() {
        login()
        val request = AuthManager.session()!!
        AuthManager.clearSession()
        assertFalse(AuthManager.updateProfile(request, UserProfile("Alice", 100, "uuid")))
        assertFalse(AuthManager.isLoggedIn)
        assertNull(AuthManager.userUuid)
        assertEquals(0, AuthManager.balance)
        assertEquals(0L, AuthManager.lastSyncTimestamp)
        assertEquals("Пользователь", AuthManager.userName)
    }

    @Test fun oldUnauthorizedReplyCannotClearNewAccountEvenWithSameToken() {
        login()
        val request = AuthManager.session()!!
        AuthManager.clearSession()
        login()
        assertFalse(AuthManager.clearSession(request))
        assertTrue(AuthManager.isLoggedIn)
    }

    @Test fun qrMutationRejectsProfileStartedBeforeIt() {
        login()
        val old = AuthManager.session()!!
        assertTrue(AuthManager.invalidateProfile(old))
        assertFalse(AuthManager.updateProfile(old, UserProfile("Alice", 1, "uuid")))
        assertEquals(42, AuthManager.balance)
        assertTrue(AuthManager.updateProfile(AuthManager.session()!!, UserProfile("Alice", 142, "uuid")))
        assertEquals(142, AuthManager.balance)
    }

    @Test fun loginStartedBeforeLogoutCannotResurrectSession() {
        val generation = AuthManager.currentGeneration()
        AuthManager.clearSession()
        assertFalse(AuthManager.saveSession(generation, "secret", UserProfile("Alice", 42, "uuid")))
        assertFalse(AuthManager.isLoggedIn)
    }

    @Test fun diagnosticsDoNotIncludeCredential() {
        login("sensitive-value")
        assertFalse(AuthManager.session().toString().contains("sensitive-value"))
    }

    /** Small JVM fake of the Android storage boundary; no Android runtime or mocking framework. */
    private fun memoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "clear" -> { values.clear(); editor }
                "remove" -> { values.remove(args!![0]); editor }
                "apply" -> null
                "commit" -> true
                else -> { values[args!![0] as String] = args[1]; editor }
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0])
                else -> values[args!![0]] ?: args[1]
            }
        } as SharedPreferences
    }
}
