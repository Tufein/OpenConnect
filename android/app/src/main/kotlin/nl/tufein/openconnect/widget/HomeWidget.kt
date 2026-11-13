package nl.tufein.openconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import nl.tufein.openconnect.MainActivity
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.WidgetSummary
import java.text.NumberFormat
import java.time.LocalDate

/** The home-screen widget: readiness, today's steps and the week's activity goal, tap to open the app. */
class HomeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val summary = runCatching { (context.applicationContext as OpenConnectApp).container.widgetSummary(LocalDate.now()) }.getOrNull()
        for (id in ids) manager.updateAppWidget(id, HomeWidgetViews.build(context, summary))
    }

    companion object {
        /** Redraws every widget on the home screen; called when new data arrived. Does nothing when there is no widget. */
        fun refresh(context: Context, summary: () -> WidgetSummary?) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidget::class.java))
            if (ids.isEmpty()) return
            val views = HomeWidgetViews.build(context, summary())
            for (id in ids) manager.updateAppWidget(id, views)
        }
    }
}

object HomeWidgetViews {
    fun build(context: Context, summary: WidgetSummary?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_home)
        val dash = context.getString(R.string.widget_none)
        views.setTextViewText(R.id.widget_readiness, summary?.readiness?.toString() ?: dash)
        views.setTextViewText(
            R.id.widget_steps,
            if (summary?.steps == null) {
                context.getString(R.string.widget_steps_none)
            } else {
                val steps = NumberFormat.getIntegerInstance().format(summary.steps)
                if (summary.stepsGoal != null) {
                    context.getString(R.string.widget_steps_goal, steps, NumberFormat.getIntegerInstance().format(summary.stepsGoal))
                } else {
                    context.getString(R.string.widget_steps, steps)
                }
            },
        )
        views.setTextViewText(
            R.id.widget_week,
            if (summary == null || summary.weekActivityGoal == 0) {
                context.getString(R.string.widget_week_free, summary?.weekActivities ?: 0)
            } else {
                context.getString(R.string.widget_week, summary.weekActivities, summary.weekActivityGoal)
            },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }
}
