package com.darkcoin.widget.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.darkcoin.widget.auth.AuthManager
import com.darkcoin.widget.widget.DarkCoinWidgetProvider
import java.util.concurrent.Executors

/** One in-flight profile fetch shared by Activity and every widget. No Activity is stored here. */
object ProfileRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val completions = mutableListOf<() -> Unit>()
    @Volatile var isRefreshing = false
        private set
    @Volatile var lastError: String? = null
        private set
    private var refreshAgain = false

    // Registration, publication and refresh scheduling happen only on the main thread.
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun publish(context: Context) {
        DarkCoinWidgetProvider.updateAllWidgets(context.applicationContext)
        listeners.toList().forEach { it() }
    }

    fun sessionChanged(context: Context) {
        lastError = null
        if (AuthManager.isLoggedIn && isRefreshing) refreshAgain = true
        publish(context)
    }

    fun refresh(context: Context, afterMutation: Boolean = false, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        val session = AuthManager.session()
        if (session == null) {
            publish(app)
            onComplete()
            return
        }
        if (isRefreshing) {
            // A receiver joins the current bounded call, never a chained second call.
            completions.add(onComplete)
            if (afterMutation) refreshAgain = true
            return
        }
        isRefreshing = true
        lastError = null
        completions.add(onComplete)
        try {
            publish(app)
            executor.execute {
                val result = runCatching { DarkCoinApi.getProfile(session.token) }
                main.post {
                    try {
                        result.fold(
                            onSuccess = { AuthManager.updateProfile(session, it) },
                            onFailure = { error ->
                                if (AuthManager.isCurrent(session)) {
                                    if (error is ApiException && error.status == 401) {
                                        AuthManager.clearSession(session)
                                        AuthManager.clearWebSession()
                                    }
                                    lastError = userMessage(error)
                                }
                            }
                        )
                    } finally {
                        isRefreshing = false
                        val finished = completions.toList()
                        completions.clear()
                        try {
                            publish(app)
                        } finally {
                            finished.forEach { it() }
                        }
                        if (refreshAgain) {
                            refreshAgain = false
                            refresh(app)
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            isRefreshing = false
            val finished = completions.toList()
            completions.clear()
            lastError = "Не удалось начать обновление"
            finished.forEach { it() }
            publish(app)
        }
    }

    fun userMessage(error: Throwable): String = when {
        error is ApiException && error.status == 401 -> "Сессия истекла. Войдите снова"
        // 403 can mean an account/operation restriction, not necessarily expired credentials.
        error is ApiException && error.status == 403 -> "Доступ запрещён сервером"
        error is ApiException -> "Ошибка сервера. Повторите обновление"
        error is InvalidResponseException -> "Некорректный ответ сервера"
        else -> "Нет связи или истекло время ожидания. Повторите попытку"
    }
}
