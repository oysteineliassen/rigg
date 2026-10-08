package no.oppdragsliste.app.varsler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.nesteTidspunkt
import no.oppdragsliste.app.logikk.paminnelsesTidspunkt
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Planlegger påminnelser før avtaler og morgenoversikten med AlarmManager. Alt planlegges på nytt
 * etter hver endring, ved oppstart av appen og etter omstart av telefonen.
 */
object Planlegger {
    const val HANDLING_AVTALE = "no.oppdragsliste.app.AVTALE"
    const val HANDLING_MORGEN = "no.oppdragsliste.app.MORGEN"
    const val HANDLING_PAMINNELSE = "no.oppdragsliste.app.PAMINNELSE"
    const val HANDLING_PAMINNELSE_FERDIG = "no.oppdragsliste.app.PAMINNELSE_FERDIG"
    const val HANDLING_PAMINNELSE_UTSETT = "no.oppdragsliste.app.PAMINNELSE_UTSETT"
    const val EKSTRA_ID = "id"
    private const val MORGEN_FORESPORSEL = -1

    fun kanPlanleggeEksakt(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    suspend fun planleggAlle(context: Context) {
        val ctx = context.applicationContext
        val oppsett = ctx.app.innstillinger.na
        val prefs = ctx.getSharedPreferences("alarmer", Context.MODE_PRIVATE)

        // Fjern alle avtalealarmer vi har satt før, og sett de som fortsatt gjelder.
        prefs.getStringSet("avtaler", emptySet())!!.forEach { avbryt(ctx, avtaleIntent(ctx, it.toLong())) }
        val satt = mutableSetOf<String>()
        if (oppsett.avtaleVarsel) {
            val na = LocalDateTime.now()
            ctx.app.repository.apne().forEach { o ->
                val tidspunkt = paminnelsesTidspunkt(o, oppsett.avtaleMinutterFor) ?: return@forEach
                if (tidspunkt.isAfter(na)) {
                    sett(ctx, tidspunkt, avtaleIntent(ctx, o.id))
                    satt += o.id.toString()
                }
            }
        }
        prefs.edit().putStringSet("avtaler", satt).apply()

        // Påminnelser: varsel på tidspunktet (uansett innstillingen for avtaler).
        prefs.getStringSet("paminnelser", emptySet())!!.forEach { avbryt(ctx, paminnelseIntent(ctx, it.toLong())) }
        val satteP = mutableSetOf<String>()
        val naP = LocalDateTime.now()
        ctx.app.repository.apnePaminnelser().forEach { p ->
            val t = p.tidspunkt ?: return@forEach
            if (t.isAfter(naP)) {
                sett(ctx, t, paminnelseIntent(ctx, p.id))
                satteP += p.id.toString()
            }
        }
        prefs.edit().putStringSet("paminnelser", satteP).apply()

        planleggMorgen(ctx)
    }

    fun planleggMorgen(context: Context) {
        val oppsett = context.app.innstillinger.na
        val pi = morgenIntent(context)
        avbryt(context, pi)
        if (oppsett.morgenOversikt) sett(context, nesteTidspunkt(oppsett.morgenTid, LocalDateTime.now()), pi)
    }

    private fun sett(context: Context, tidspunkt: LocalDateTime, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val ms = tidspunkt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (kanPlanleggeEksakt(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun avbryt(context: Context, pi: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pi)
    }

    private fun avtaleIntent(context: Context, id: Long): PendingIntent {
        val i = Intent(context, AlarmMottaker::class.java).setAction(HANDLING_AVTALE).putExtra(EKSTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun paminnelseIntent(context: Context, id: Long): PendingIntent =
        handlingIntent(context, HANDLING_PAMINNELSE, id)

    /** Broadcast til [AlarmMottaker] for en påminnelse (varsel, ferdig eller utsett). */
    fun handlingIntent(context: Context, handling: String, id: Long): PendingIntent {
        val i = Intent(context, AlarmMottaker::class.java).setAction(handling).putExtra(EKSTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun morgenIntent(context: Context): PendingIntent {
        val i = Intent(context, AlarmMottaker::class.java).setAction(HANDLING_MORGEN)
        return PendingIntent.getBroadcast(
            context, MORGEN_FORESPORSEL, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
