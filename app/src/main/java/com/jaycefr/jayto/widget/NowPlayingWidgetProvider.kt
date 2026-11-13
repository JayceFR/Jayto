package com.jaycefr.jayto.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entry point the launcher talks to. Holds no logic: live updates are pushed by
 * [NowPlayingWidgetUpdater] from the player, this only answers the launcher's refresh requests.
 */
@AndroidEntryPoint
class NowPlayingWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var updater: NowPlayingWidgetUpdater

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updater.refresh(goAsync())
    }

    /** Below Android 12 the layout is chosen from these options, so a resize needs a re-render. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Android 12+ switches between the responsive layouts on its own.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) updater.refresh(goAsync())
    }
}
