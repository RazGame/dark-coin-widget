package com.darkcoin.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.darkcoin.widget.R
import com.darkcoin.widget.api.ProfileRepository
import com.darkcoin.widget.auth.AuthManager
import com.darkcoin.widget.scanner.QrScannerActivity
import com.darkcoin.widget.ui.LoginActivity
import com.darkcoin.widget.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Official Material 3 Expressive & Material You Adaptive App Widget Provider.
 * Follows Android Widget Guidelines (Tier 1 Quality):
 * - Official system corner radii (@android:dimen/system_app_widget_background_radius)
 * - Device dynamic color via Theme.DeviceDefault.DayNight & semantic tokens
 * - Responsive canonical sizes (2x2, 4x1, 4x2, tall)
 * - Robust state handling: Logged In, Logged Out, Updating/Loading, Network Error, Out-of-date
 * - Minimum 48x48dp touch targets and TalkBack accessibility
 */
class DarkCoinWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val views = buildResponsiveRemoteViews(context, options, ProfileRepository.isRefreshing)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val views = buildResponsiveRemoteViews(context, newOptions, ProfileRepository.isRefreshing)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            try {
                ProfileRepository.refresh(context.applicationContext, onComplete = { pending.finish() })
            } catch (error: RuntimeException) {
                pending.finish()
                throw error
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.darkcoin.widget.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            val isRefreshing = ProfileRepository.isRefreshing
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DarkCoinWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(id)
                val views = buildResponsiveRemoteViews(context, options, isRefreshing)
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private fun buildResponsiveRemoteViews(context: Context, options: Bundle, isRefreshing: Boolean): RemoteViews {
            // Android 12+ (API 31+) native responsive layout mapping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val viewsMap = WidgetLayoutSelector.sizes(context.resources.configuration.fontScale).associate { size ->
                    SizeF(size.width.toFloat(), size.height.toFloat()) to when (size.layout) {
                        WidgetLayoutSelector.Layout.DASHBOARD -> build4x2Views(context, isRefreshing)
                        WidgetLayoutSelector.Layout.STRIP -> build4x1Views(context)
                        WidgetLayoutSelector.Layout.TALL -> buildTallViews(context, isRefreshing, size.width)
                        WidgetLayoutSelector.Layout.COMPACT -> build2x2Views(context)
                        WidgetLayoutSelector.Layout.SHORT -> buildShortViews(context)
                        WidgetLayoutSelector.Layout.SCAN_ONLY -> buildScanOnlyViews(context)
                    }
                }
                return RemoteViews(viewsMap)
            }

            // Fallback for Android 8 - 11 based on widget width/height options
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240).let { if (it <= 0) 240 else it }
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).let { if (it <= 0) 110 else it }

            // Legacy launcher ranges describe portrait and landscape separately.
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth).coerceAtLeast(minWidth)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight).coerceAtLeast(minHeight)
            fun sized(width: Int, height: Int): RemoteViews = when (WidgetLayoutSelector.select(width, height, context.resources.configuration.fontScale)) {
                WidgetLayoutSelector.Layout.DASHBOARD -> build4x2Views(context, isRefreshing)
                WidgetLayoutSelector.Layout.STRIP -> build4x1Views(context)
                WidgetLayoutSelector.Layout.TALL -> buildTallViews(context, isRefreshing, width)
                WidgetLayoutSelector.Layout.COMPACT -> build2x2Views(context)
                WidgetLayoutSelector.Layout.SHORT -> buildShortViews(context)
                WidgetLayoutSelector.Layout.SCAN_ONLY -> buildScanOnlyViews(context)
            }
            return RemoteViews(sized(maxWidth, minHeight), sized(minWidth, maxHeight))
        }

        private fun buildTallViews(context: Context, isRefreshing: Boolean, availableWidth: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dark_coin_tall)
            views.setViewVisibility(R.id.widget_scan_label_tall,
                if (availableWidth < 220 * context.resources.configuration.fontScale) View.GONE else View.VISIBLE)
            val isLoggedIn = AuthManager.isLoggedIn
            val syncStatus = when {
                !isLoggedIn -> "Требуется авторизация"
                isRefreshing -> "Синхронизация…"
                ProfileRepository.lastError != null -> ProfileRepository.lastError
                AuthManager.lastSyncTimestamp > 0 -> "Обновлено " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(AuthManager.lastSyncTimestamp))
                else -> "Данные ещё не обновлены"
            }
            views.setTextViewText(R.id.widget_last_sync_tall, syncStatus)

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_tall, balanceStr)
                views.setTextViewText(R.id.widget_user_name_tall, AuthManager.userName)
                views.setTextViewText(R.id.widget_coins_label_tall, context.getString(R.string.coins_unit))

                // Loading state feedback
                if (isRefreshing) {
                    views.setViewVisibility(R.id.widget_btn_refresh_tall, View.GONE)
                    views.setViewVisibility(R.id.widget_progress_refresh_tall, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_btn_refresh_tall, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_progress_refresh_tall, View.GONE)
                }

                setupIntents(context, views, R.id.widget_root_tall, R.id.widget_btn_scan_tall, R.id.widget_btn_refresh_tall, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_tall, "—")
                views.setTextViewText(R.id.widget_user_name_tall, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_coins_label_tall, "Вход")
                views.setViewVisibility(R.id.widget_btn_refresh_tall, View.GONE)
                views.setViewVisibility(R.id.widget_progress_refresh_tall, View.GONE)
                setupIntents(context, views, R.id.widget_root_tall, R.id.widget_btn_scan_tall, null, isLoggedIn)
            }

            return views
        }

        private fun build4x2Views(context: Context, isRefreshing: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dark_coin_4x2)
            val isLoggedIn = AuthManager.isLoggedIn

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_4x2, balanceStr)
                views.setTextViewText(R.id.widget_user_name_4x2, AuthManager.userName)
                views.setTextViewText(R.id.widget_brand_label_4x2, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_coins_label_4x2, context.getString(R.string.coins_unit))

                // Loading and sync status feedback
                if (isRefreshing) {
                    views.setViewVisibility(R.id.widget_btn_refresh_4x2, View.GONE)
                    views.setViewVisibility(R.id.widget_progress_refresh_4x2, View.VISIBLE)
                    views.setTextViewText(R.id.widget_last_sync_4x2, "Синхронизация...")
                } else {
                    views.setViewVisibility(R.id.widget_btn_refresh_4x2, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_progress_refresh_4x2, View.GONE)

                    if (ProfileRepository.lastError != null) {
                        views.setTextViewText(R.id.widget_last_sync_4x2, ProfileRepository.lastError)
                    } else if (AuthManager.lastSyncTimestamp > 0) {
                        val age = System.currentTimeMillis() - AuthManager.lastSyncTimestamp
                        val timeFormat = if (age > 24 * 3600 * 1000L) {
                            SimpleDateFormat("dd.MM", Locale.getDefault())
                        } else {
                            SimpleDateFormat("HH:mm", Locale.getDefault())
                        }
                        views.setTextViewText(
                            R.id.widget_last_sync_4x2,
                            "Обновлено в " + timeFormat.format(Date(AuthManager.lastSyncTimestamp))
                        )
                    } else {
                        views.setTextViewText(R.id.widget_last_sync_4x2, "Нажми для быстрого сканирования")
                    }
                }

                setupIntents(context, views, R.id.widget_root_4x2, R.id.widget_btn_scan_4x2, R.id.widget_btn_refresh_4x2, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_4x2, "—")
                views.setTextViewText(R.id.widget_user_name_4x2, context.getString(R.string.not_logged_in))
                views.setTextViewText(R.id.widget_brand_label_4x2, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_coins_label_4x2, "")
                views.setTextViewText(R.id.widget_last_sync_4x2, "Требуется авторизация")
                views.setViewVisibility(R.id.widget_btn_refresh_4x2, View.GONE)
                views.setViewVisibility(R.id.widget_progress_refresh_4x2, View.GONE)
                setupIntents(context, views, R.id.widget_root_4x2, R.id.widget_btn_scan_4x2, null, isLoggedIn)
            }

            return views
        }

        private fun build4x1Views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dark_coin_4x1)
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

        private fun buildShortViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dark_coin_short)
            views.setViewVisibility(R.id.widget_coins_label_short,
                if (context.resources.configuration.fontScale > 1.3f) View.GONE else View.VISIBLE)
            val isLoggedIn = AuthManager.isLoggedIn
            val balance = if (isLoggedIn) String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ') else "—"
            views.setTextViewText(R.id.widget_balance_short, balance)
            views.setTextViewText(R.id.widget_coins_label_short, if (isLoggedIn) context.getString(R.string.coins_unit) else "Вход")
            setupIntents(context, views, R.id.widget_root_short, R.id.widget_btn_scan_short, null, isLoggedIn)
            return views
        }

        private fun buildScanOnlyViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dark_coin_scan_only)
            // The whole small tile starts scanning; its visible circle remains a fixed 48dp.
            setupIntents(context, views, R.id.widget_root_scan_only, R.id.widget_root_scan_only, null, AuthManager.isLoggedIn)
            return views
        }

        private fun build2x2Views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dark_coin_2x2)
            views.setViewVisibility(R.id.widget_scan_label_2x2,
                if (context.resources.configuration.fontScale > 1.5f) View.GONE else View.VISIBLE)
            val isLoggedIn = AuthManager.isLoggedIn

            if (isLoggedIn) {
                val balanceStr = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
                views.setTextViewText(R.id.widget_balance_2x2, balanceStr)
                setupIntents(context, views, R.id.widget_root_2x2, R.id.widget_btn_scan_2x2, null, isLoggedIn)
            } else {
                views.setTextViewText(R.id.widget_balance_2x2, "—")
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
                val refreshIntent = Intent(context, DarkCoinWidgetProvider::class.java).apply {
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
