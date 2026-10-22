package com.caohaidang.edward.music.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle

/**
 * "Disc" widget. Click handling (play / pause) is inherited from [CdPlayerWidgetProvider];
 * only the drawing differs - it is done by [DiscWidgetRenderer].
 */
class DiscWidgetProvider : CdPlayerWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        DiscWidgetRenderer.updateWidgets(context, manager, ids, CdWidgetStore.load(context))
        CdPlayerWidgetUpdater.active?.refresh()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        DiscWidgetRenderer.updateWidgets(context, manager, intArrayOf(id), CdWidgetStore.load(context))
    }
}
