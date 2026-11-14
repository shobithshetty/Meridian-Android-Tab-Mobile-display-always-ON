package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.theme.resolveVisualTheme
import com.example.util.TimeAndAlertUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

class WorldClockAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsInternal(context, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = try {
                manager.getAppWidgetIds(
                    ComponentName(appContext, WorldClockAppWidgetProvider::class.java)
                )
            } catch (_: Throwable) {
                IntArray(0)
            }
            if (ids.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetsInternal(appContext, manager, ids)
            }
        }

        suspend fun updateAllWidgetsNow(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = try {
                manager.getAppWidgetIds(
                    ComponentName(appContext, WorldClockAppWidgetProvider::class.java)
                )
            } catch (_: Throwable) {
                IntArray(0)
            }
            if (ids.isEmpty()) return
            updateWidgetsInternal(appContext, manager, ids)
        }

        suspend fun updateSingleWidgetNow(context: Context, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            updateWidgetsInternal(appContext, manager, intArrayOf(appWidgetId))
        }

        private suspend fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val dao = AppDatabase.getDatabase(context).clockDao()
            val settings = dao.getSettingsDirect() ?: ClockSettings()
            val allClocks = dao.getFriendClocksDirect()
            val now = ZonedDateTime.now()
            val sortedClocks = TimeAndAlertUtils.getSortedFriendClocks(
                allClocks,
                settings.clockSortOrder,
                now
            )

            for (appWidgetId in appWidgetIds) {
                val options = try {
                    appWidgetManager.getAppWidgetOptions(appWidgetId)
                } catch (_: Throwable) {
                    null
                }
                val views = buildWidgetRemoteViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    settings = settings,
                    sortedClocks = sortedClocks,
                    now = now,
                    options = options
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun buildWidgetRemoteViews(
            context: Context,
            appWidgetId: Int,
            settings: ClockSettings,
            sortedClocks: List<FriendClock>,
            now: ZonedDateTime = ZonedDateTime.now(),
            options: Bundle? = null
        ): RemoteViews {
            val theme = resolveVisualTheme(settings, localTime = now)
            val views = RemoteViews(context.packageName, R.layout.widget_world_clock)

            val surfaceAlpha = settings.homeWidgetBgAlpha.coerceIn(80, 255)
            views.setInt(R.id.widget_bg_image, "setColorFilter", theme.bgEnd.toArgb())
            views.setInt(R.id.widget_bg_image, "setImageAlpha", surfaceAlpha)

            // Local header & main clock
            val localZoneName = ZoneId.systemDefault().id.substringAfterLast('/').replace('_', ' ')
            views.setTextViewText(R.id.widget_local_zone_label, localZoneName.uppercase())
            views.setTextColor(R.id.widget_local_zone_label, theme.accentColor.toArgb())
            views.setTextColor(R.id.widget_main_date, theme.textSecondary.toArgb())

            val mainFmt12 = if (settings.showSeconds) "hh:mm:ss a" else "hh:mm a"
            val mainFmt24 = if (settings.showSeconds) "HH:mm:ss" else "HH:mm"
            views.setCharSequence(R.id.widget_main_time, "setFormat12Hour", mainFmt12)
            views.setCharSequence(R.id.widget_main_time, "setFormat24Hour", mainFmt24)
            views.setTextColor(R.id.widget_main_time, theme.numberColor.toArgb())

            views.setInt(R.id.widget_config_btn, "setColorFilter", theme.accentColor.toArgb())

            // Populate pinned world cities
            views.removeAllViews(R.id.widget_cities_container)
            val pinnedCities = sortedClocks.filter { it.showInWidget }

            val maxHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200) ?: 200
            val maxCitiesToShow = when {
                maxHeightDp < 120 -> 2
                maxHeightDp < 200 -> 4
                else -> 6
            }

            if (pinnedCities.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                views.setTextColor(R.id.widget_empty_text, theme.textSecondary.toArgb())
            } else {
                views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                val cityFmt12 = if (settings.worldClockShowSeconds) "hh:mm:ss a" else "hh:mm a"
                val cityFmt24 = if (settings.worldClockShowSeconds) "HH:mm:ss" else "HH:mm"

                for (city in pinnedCities.take(maxCitiesToShow)) {
                    val itemViews = RemoteViews(context.packageName, R.layout.widget_world_clock_city_item)
                    itemViews.setInt(R.id.widget_city_card_bg, "setColorFilter", theme.cardBackground.toArgb())
                    itemViews.setInt(
                        R.id.widget_city_card_bg,
                        "setImageAlpha",
                        (surfaceAlpha * 0.9f).toInt().coerceIn(80, 245)
                    )

                    val primaryLabel = city.cityLabel.ifBlank { city.friendName }
                    val offsetText = TimeAndAlertUtils.formatRelativeOffset(now, city.timezoneId)
                    val secondaryLabel = if (settings.worldClockShowOffset) {
                        if (city.friendName.isNotBlank() && city.friendName != primaryLabel) {
                            "${city.friendName} • $offsetText"
                        } else {
                            offsetText
                        }
                    } else {
                        city.timezoneId
                    }

                    itemViews.setTextViewText(R.id.widget_city_name, primaryLabel)
                    itemViews.setTextColor(R.id.widget_city_name, theme.textPrimary.toArgb())

                    itemViews.setTextViewText(R.id.widget_city_offset, secondaryLabel)
                    itemViews.setTextColor(R.id.widget_city_offset, theme.textSecondary.toArgb())

                    itemViews.setString(R.id.widget_city_time, "setTimeZone", city.timezoneId)
                    itemViews.setCharSequence(R.id.widget_city_time, "setFormat12Hour", cityFmt12)
                    itemViews.setCharSequence(R.id.widget_city_time, "setFormat24Hour", cityFmt24)
                    itemViews.setTextColor(R.id.widget_city_time, theme.numberColor.toArgb())

                    views.addView(R.id.widget_cities_container, itemViews)
                }
            }

            // Launch MainActivity on tapping widget body
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Launch Configure Activity on tapping gear button
            val configIntent = Intent(context, WorldClockWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 10_000,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_config_btn, configPendingIntent)

            return views
        }
    }
}
