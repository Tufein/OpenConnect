package nl.tufein.openconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import nl.tufein.openconnect.ui.Palette
import nl.tufein.openconnect.ui.colorSchemeFor
import nl.tufein.openconnect.MainActivity
import nl.tufein.openconnect.OpenConnectApp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.WidgetFormat
import nl.tufein.openconnect.core.WidgetFormats
import nl.tufein.openconnect.core.WidgetSummary
import java.text.NumberFormat
import java.time.LocalDate

/**
 * The home-screen widget: readiness, today's steps and the week's activity goal, tap to open the app. Since v1.7.0 it
 * comes in three formats: a small cell shows readiness and steps, the usual card adds the week, and a large card also
 * shows last night's sleep and the resting heart rate.
 */
class HomeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val summary = summaryOf(context)
        for (id in ids) manager.updateAppWidget(id, HomeWidgetViews.build(context, summary, formatOf(manager, id)))
    }

    /** The launcher calls this while the user drags the widget bigger or smaller; the layout follows. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, HomeWidgetViews.build(context, summaryOf(context), formatOf(newOptions)))
    }

    companion object {
        /** Redraws every widget on the home screen; called when new data arrived. Does nothing when there is no widget. */
        fun refresh(context: Context, summary: () -> WidgetSummary?) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidget::class.java))
            if (ids.isEmpty()) return
            val data = summary()
            for (id in ids) manager.updateAppWidget(id, HomeWidgetViews.build(context, data, formatOf(manager, id)))
        }

        private fun summaryOf(context: Context): WidgetSummary? =
            runCatching { (context.applicationContext as OpenConnectApp).container.widgetSummary(LocalDate.now()) }.getOrNull()

        /** The launcher reports the smallest size the widget may take, in dp; older launchers report nothing. */
        private fun formatOf(manager: AppWidgetManager, id: Int): WidgetFormat = formatOf(manager.getAppWidgetOptions(id))

        private fun formatOf(options: Bundle): WidgetFormat = WidgetFormats.forSize(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        )
    }
}

object HomeWidgetViews {
    /** The widget is a RemoteViews, so it cannot read the Compose theme: paint it from the saved palette instead. */
    private fun schemeFor(context: Context): ColorScheme {
        val settings = (context.applicationContext as? OpenConnectApp)?.container?.settings
        val dark = when (settings?.themeMode) {
            "dark" -> true
            "light" -> false
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        return colorSchemeFor(Palette.of(settings?.palette ?: "graphite"), dark)
    }

    fun build(context: Context, summary: WidgetSummary?, format: WidgetFormat = WidgetFormat.MEDIUM): RemoteViews {
        val layout = when (format) {
            WidgetFormat.SMALL -> R.layout.widget_home_small
            WidgetFormat.MEDIUM -> R.layout.widget_home
            WidgetFormat.LARGE -> R.layout.widget_home_large
        }
        val views = RemoteViews(context.packageName, layout)
        val colors = schemeFor(context)
        views.setInt(R.id.widget_root, "setBackgroundColor", colors.background.toArgb())
        views.setInt(R.id.widget_readiness_label, "setTextColor", colors.onSurfaceVariant.toArgb())
        views.setInt(R.id.widget_readiness, "setTextColor", colors.primary.toArgb())
        views.setInt(R.id.widget_steps, "setTextColor", colors.onBackground.toArgb())
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
        if (format != WidgetFormat.SMALL) {
            views.setInt(R.id.widget_week, "setTextColor", colors.onSurfaceVariant.toArgb())
            views.setTextViewText(
                R.id.widget_week,
                if (summary == null || summary.weekActivityGoal == 0) {
                    context.getString(R.string.widget_week_free, summary?.weekActivities ?: 0)
                } else {
                    context.getString(R.string.widget_week, summary.weekActivities, summary.weekActivityGoal)
                },
            )
        }
        if (format == WidgetFormat.LARGE) {
            views.setInt(R.id.widget_sleep, "setTextColor", colors.onBackground.toArgb())
            views.setInt(R.id.widget_rest, "setTextColor", colors.onSurfaceVariant.toArgb())
            val sleep = summary?.sleepMinutes
            views.setTextViewText(
                R.id.widget_sleep,
                if (sleep == null) context.getString(R.string.widget_sleep_none) else context.getString(R.string.widget_sleep, sleep / 60, sleep % 60),
            )
            views.setTextViewText(
                R.id.widget_rest,
                summary?.restingHr?.let { context.getString(R.string.widget_rest, it) } ?: context.getString(R.string.widget_rest_none),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }
}
