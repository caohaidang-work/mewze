package com.caohaidang.edward.music.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle

/**
 * "iPod" widget. Click handling (previous / next / play-pause) is inherited from
 * [CdPlayerWidgetProvider]; only the drawing differs - it is done by [IpodWidgetRenderer].
 */
class IpodWidgetProvider : CdPlayerWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        IpodWidgetRenderer.updateWidgets(context, manager, ids, CdWidgetStore.load(context))
        CdPlayerWidgetUpdater.active?.refresh()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        IpodWidgetRenderer.updateWidgets(context, manager, intArrayOf(id), CdWidgetStore.load(context))
    }
}
