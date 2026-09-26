package no.rigg.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Enkel widget: viser programnavn og ukens fremgang, åpner appen ved trykk.
 * Programmets dager er ikke bundet til ukedager, så widgeten viser "X av Y økter denne uka" fremfor "dagens økt".
 */
class WorkoutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WorkoutWidgetProvider::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val store = Store(context)
            val plan = store.loadPlan()
            val logs = store.loadLogs()
            val views = RemoteViews(context.packageName, R.layout.widget_workout)

            if (plan == null) {
                views.setTextViewText(R.id.widget_title, "Rigg")
                views.setTextViewText(R.id.widget_subtitle, "Ingen program ennå")
            } else {
                val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val done = logs.count { runCatching { !LocalDate.parse(it.date).isBefore(weekStart) }.getOrDefault(false) }
                views.setTextViewText(R.id.widget_title, plan.name)
                views.setTextViewText(R.id.widget_subtitle, "Denne uka: $done/${plan.days.size} økter")
            }

            val openIntent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}
