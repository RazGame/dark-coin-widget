package com.darkside.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.darkside.widget.R
import com.darkside.widget.api.DarksideApi
import com.darkside.widget.auth.AuthManager
import com.darkside.widget.scanner.QrScannerActivity
import com.darkside.widget.ui.LoginActivity
import com.darkside.widget.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Material 3 Expressive & Material You Adaptive App Widget Provider.
 * Supports Dynamic Colors, official Android 12+ corner radii, responsive sizes,
 * and robust multi-state handling (Logged In, Logged Out, Out of date, etc.).
 */
class DarksideWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val views = buildResponsiveRemoteViews(context, options)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val views = buildResponsiveRemoteViews(context, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val token = AuthManager.token
            if (!token.isNullOrBlank()) {
                thread {
                    try {
                        val profile = DarksideApi.getProfile(token)
                        if (profile != null) {
                            AuthManager.userName = profile.name
                            if (profile.uuid != null) {
                                AuthManager.userUuid = profile.uuid
                            }
                            AuthManager.updateBalance(context, profile.balance)
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.darkside.widget.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DarksideWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(id)
                val views = buildResponsiveRemoteViews(context, options)
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private fun buildResponsiveRemoteViews(context: Context, options: Bundle): RemoteViews {
            // Android 12+ (API 31+) native responsive layout mapping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val viewsMap = mapOf(
                    SizeF(100f, 100f) to build2x2Views(context),
                    SizeF(100f, 170f) to buildTallViews(context),
                    SizeF(265f, 50f) to build4x1Views(context),
                    SizeF(265f, 110f) to build4x2Views(context)
                )
                return RemoteViews(viewsMap)
            }

            // Fallback for Android 8 - 11 based on widget width/height options
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)

            return when {
                minWidth >= 265 && minHeight >= 110 -> build4x2Views(context)
                minWidth >= 265 -> build4x1Views(context)
                minHeight >= 170 -> buildTallViews(context)
                else -> build2x2Views(context)
            }
        }

        private fun buildTallViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_darkside_tall)
            val isLoggedIn = AuthManager.isLoggedIn

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_tall, balanceStr)
                views.setTextViewText(R.id.widget_user_name_tall, AuthManager.userName)
                views.setTextViewText(R.id.widget_coins_label_tall, context.getString(R.string.coins_unit))
                setupIntents(context, views, R.id.widget_root_tall, R.id.widget_btn_scan_tall, R.id.widget_btn_refresh_tall, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_tall, "—")
                views.setTextViewText(R.id.widget_user_name_tall, "Darkside")
                views.setTextViewText(R.id.widget_coins_label_tall, "Вход")
                setupIntents(context, views, R.id.widget_root_tall, R.id.widget_btn_scan_tall, null, isLoggedIn)
            }

            return views
        }

        private fun build4x2Views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_darkside_4x2)
            val isLoggedIn = AuthManager.isLoggedIn

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_4x2, balanceStr)
                views.setTextViewText(R.id.widget_user_name_4x2, AuthManager.userName)
                views.setTextViewText(R.id.widget_coins_label_4x2, context.getString(R.string.coins_unit))
                views.setTextViewText(R.id.widget_status_sub_4x2, "Готов к сканированию")

                if (AuthManager.lastSyncTimestamp > 0) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    views.setTextViewText(
                        R.id.widget_last_sync_4x2,
                        "Обновлено в " + timeFormat.format(Date(AuthManager.lastSyncTimestamp))
                    )
                } else {
                    views.setTextViewText(R.id.widget_last_sync_4x2, "Нажми для быстрого сканирования")
                }
                setupIntents(context, views, R.id.widget_root_4x2, R.id.widget_btn_scan_4x2, R.id.widget_btn_refresh_4x2, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_4x2, "—")
                views.setTextViewText(R.id.widget_user_name_4x2, "Darkside World")
                views.setTextViewText(R.id.widget_coins_label_4x2, "")
                views.setTextViewText(R.id.widget_status_sub_4x2, "Нажмите для входа")
                views.setTextViewText(R.id.widget_last_sync_4x2, "Требуется авторизация")
                setupIntents(context, views, R.id.widget_root_4x2, R.id.widget_btn_scan_4x2, null, isLoggedIn)
            }

            return views
        }

        private fun build4x1Views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_darkside_4x1)
            val isLoggedIn = AuthManager.isLoggedIn

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_4x1, balanceStr)
                views.setTextViewText(R.id.widget_coins_label_4x1, context.getString(R.string.coins_unit))
                setupIntents(context, views, R.id.widget_root_4x1, R.id.widget_btn_scan_4x1, null, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_4x1, "—")
                views.setTextViewText(R.id.widget_coins_label_4x1, "Вход")
                setupIntents(context, views, R.id.widget_root_4x1, R.id.widget_btn_scan_4x1, null, isLoggedIn)
            }

            return views
        }

        private fun build2x2Views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_darkside_2x2)
            val isLoggedIn = AuthManager.isLoggedIn

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_2x2, balanceStr)
                views.setTextViewText(R.id.widget_coins_label_2x2, context.getString(R.string.coins_unit))
                setupIntents(context, views, R.id.widget_root_2x2, R.id.widget_btn_scan_2x2, null, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_2x2, "—")
                views.setTextViewText(R.id.widget_coins_label_2x2, "Вход")
                setupIntents(context, views, R.id.widget_root_2x2, R.id.widget_btn_scan_2x2, null, isLoggedIn)
            }

            return views
        }

        private fun setupIntents(
            context: Context,
            views: RemoteViews,
            rootId: Int,
            scanBtnId: Int,
            refreshBtnId: Int?,
            isLoggedIn: Boolean
        ) {
            // Open Main App on Root Click (or LoginActivity if logged out)
            val targetActivityClass = if (isLoggedIn) MainActivity::class.java else LoginActivity::class.java
            val appIntent = Intent(context, targetActivityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(rootId, appPendingIntent)

            // Open Instant QR Scanner on Scan Button Click (or Login if logged out)
            val scanTargetClass = if (isLoggedIn) QrScannerActivity::class.java else LoginActivity::class.java
            val scanIntent = Intent(context, scanTargetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val scanPendingIntent = PendingIntent.getActivity(
                context,
                1,
                scanIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(scanBtnId, scanPendingIntent)

            // Refresh action
            if (refreshBtnId != null && isLoggedIn) {
                val refreshIntent = Intent(context, DarksideWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    2,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(refreshBtnId, refreshPendingIntent)
            }
        }
    }
}
