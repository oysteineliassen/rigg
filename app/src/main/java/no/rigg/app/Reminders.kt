package no.rigg.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private const val CHANNEL_ID = "treningspaminnelser"
private const val EXTRA_DAY = "day_of_week"
private const val REQUEST_CODE_BASE = 5000

/** Ukedagen som tall, samme rekkefølge som java.time.DayOfWeek (1 = mandag .. 7 = søndag). */
fun dayLabel(iso: Int): String = when (iso) {
    1 -> "Man"; 2 -> "Tir"; 3 -> "Ons"; 4 -> "Tor"; 5 -> "Fre"; 6 -> "Lør"; else -> "Søn"
}

/**
 * Planlegger treningspåminnelser med AlarmManager. Bruker upresise alarmer (allowWhileIdle,
 * ikke exact) slik at appen ikke trenger den egne "planlegg nøyaktige alarmer"-tillatelsen.
 * Et par minutters forsinkelse under Doze er greit for en treningspåminnelse.
 */
object ReminderScheduler {

    fun scheduleAll(context: Context, settings: ReminderSettings) {
        cancelAll(context)
        if (!settings.enabled || settings.days.isEmpty()) return
        settings.days.forEach { iso ->
            runCatching { scheduleNext(context, DayOfWeek.of(iso), settings.hour, settings.minute) }
        }
    }

    fun cancelAll(context: Context) {
        DayOfWeek.values().forEach { day -> cancelDay(context, day) }
    }

    fun scheduleNext(context: Context, day: DayOfWeek, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(day)).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntentFor(context, day)) }
    }

    private fun cancelDay(context: Context, day: DayOfWeek) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntentFor(context, day))
    }

    private fun pendingIntentFor(context: Context, day: DayOfWeek): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_DAY, day.value)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + day.value,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Treningspåminnelser", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}

/** Mottar den daglige alarmen: viser et varsel og planlegger samme ukedag neste uke. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dayValue = intent.getIntExtra(EXTRA_DAY, -1)
        ReminderScheduler.ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Tid for trening")
            .setContentText("Dagens økt venter. Åpne appen for å se programmet.")
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(dayValue.coerceAtLeast(0), notification)
            }
        }

        if (dayValue in 1..7) {
            val settings = Store(context).loadReminders()
            if (settings.enabled && dayValue in settings.days) {
                ReminderScheduler.scheduleNext(context, DayOfWeek.of(dayValue), settings.hour, settings.minute)
            }
        }
    }
}

/** Setter alarmene på nytt etter omstart av telefonen, siden AlarmManager glemmer dem da. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context, Store(context).loadReminders())
        }
    }
}
