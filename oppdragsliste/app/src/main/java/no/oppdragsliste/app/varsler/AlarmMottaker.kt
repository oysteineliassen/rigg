package no.oppdragsliste.app.varsler

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.oppdragsliste.app.app
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Paminnelse
import androidx.core.app.NotificationManagerCompat
import no.oppdragsliste.app.logikk.utsatt
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.formaterTid
import no.oppdragsliste.app.logikk.formaterTidsrom
import no.oppdragsliste.app.logikk.morgenTekst
import no.oppdragsliste.app.logikk.sorter
import no.oppdragsliste.app.logikk.varighetTekst
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Felles omfang for korte jobber fra mottakere (sammen med goAsync). */
val mottakerOmfang = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Kjører [jobb] i bakgrunnen og sier fra til Android når den er ferdig. */
fun BroadcastReceiver.asynkront(jobb: suspend () -> Unit) {
    val resultat = goAsync()
    mottakerOmfang.launch {
        try {
            jobb()
        } finally {
            resultat.finish()
        }
    }
}

class AlarmMottaker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            Planlegger.HANDLING_AVTALE -> asynkront {
                val o = ctx.app.repository.hent(intent.getLongExtra(Planlegger.EKSTRA_ID, -1)) ?: return@asynkront
                if (!o.ferdig && ctx.app.innstillinger.na.avtaleVarsel) visAvtale(ctx, o)
            }
            Planlegger.HANDLING_PAMINNELSE -> asynkront {
                val p = ctx.app.repository.hentPaminnelse(intent.getLongExtra(Planlegger.EKSTRA_ID, -1)) ?: return@asynkront
                if (!p.ferdig) visPaminnelse(ctx, p)
            }
            Planlegger.HANDLING_PAMINNELSE_FERDIG -> asynkront {
                val id = intent.getLongExtra(Planlegger.EKSTRA_ID, -1)
                NotificationManagerCompat.from(ctx).cancel(Varsler.paminnelseId(id))
                val p = ctx.app.repository.hentPaminnelse(id) ?: return@asynkront
                ctx.app.repository.settPaminnelseFerdig(p, true)
            }
            Planlegger.HANDLING_PAMINNELSE_UTSETT -> asynkront {
                val id = intent.getLongExtra(Planlegger.EKSTRA_ID, -1)
                NotificationManagerCompat.from(ctx).cancel(Varsler.paminnelseId(id))
                val p = ctx.app.repository.hentPaminnelse(id) ?: return@asynkront
                val ny = utsatt(LocalDateTime.now())
                ctx.app.repository.lagrePaminnelse(p.copy(dato = ny.toLocalDate(), tid = ny.toLocalTime()))
            }
            Planlegger.HANDLING_MORGEN -> asynkront {
                if (ctx.app.innstillinger.na.morgenOversikt) visMorgen(ctx)
                Planlegger.planleggMorgen(ctx)
                // Ny dag: «i dag» i widgeten skal gjelde den nye dagen.
                no.oppdragsliste.app.widget.OppdragWidget.oppdater(ctx)
            }
        }
    }

    private fun visAvtale(ctx: Context, o: Oppdrag) {
        val dato = o.dato ?: return
        val tid = o.tid ?: return
        val start = LocalDateTime.of(dato, tid)
        val minutter = Duration.between(LocalDateTime.now(), start).toMinutes().coerceAtLeast(0).toInt()
        val nar = if (minutter <= 1) "Nå" else "Om ${varighetTekst(((minutter + 2) / 5) * 5)}"
        val tittel = "$nar: ${o.tittel}"
        val tekst = listOfNotNull(
            "kl. ${formaterTidsrom(tid, o.sluttid)}",
            o.kunde.takeIf { it.isNotBlank() && it != o.tittel },
            o.anlegg.takeIf { it.isNotBlank() },
            o.stedTekst.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val id = Varsler.avtaleId(o.id)
        val b = Varsler.bygg(ctx, Varsler.KANAL_AVTALE, tittel, tekst)
            .setContentIntent(Varsler.apneApp(ctx, id))
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
        if (o.telefon.isNotBlank()) {
            val ring = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(o.telefon.filter { it.isDigit() || it == '+' })))
            b.addAction(0, "Ring", PendingIntent.getActivity(ctx, id, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        if (o.stedTekst.isNotBlank() || o.harKoordinater) {
            val uri = if (o.lat != null && o.lng != null) Uri.parse("geo:${o.lat},${o.lng}?q=${o.lat},${o.lng}")
            else Uri.parse("geo:0,0?q=" + Uri.encode(o.stedTekst))
            b.addAction(0, "Vis i kart", PendingIntent.getActivity(ctx, id + 1, Intent(Intent.ACTION_VIEW, uri), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        Varsler.vis(ctx, id, b)
    }

    private suspend fun visPaminnelse(ctx: Context, p: Paminnelse) {
        val oppdrag = p.oppdragId?.let { ctx.app.repository.hent(it) }
        val linjer = p.tekst.trim().lines()
        val tittel = linjer.firstOrNull()?.ifBlank { null } ?: "Påminnelse"
        val tekst = listOfNotNull(
            linjer.drop(1).joinToString("\n").ifBlank { null },
            oppdrag?.let { "Oppdrag: ${it.tittel}" + (it.stedTekst.takeIf { s -> s.isNotBlank() }?.let { s -> " · $s" } ?: "") },
        ).joinToString("\n").ifBlank { null }
        val id = Varsler.paminnelseId(p.id)
        val b = Varsler.bygg(ctx, Varsler.KANAL_PAMINNELSE, tittel, tekst)
            .setContentIntent(Varsler.apneApp(ctx, id))
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Ferdig", Planlegger.handlingIntent(ctx, Planlegger.HANDLING_PAMINNELSE_FERDIG, p.id))
            .addAction(0, "Utsett 1 t", Planlegger.handlingIntent(ctx, Planlegger.HANDLING_PAMINNELSE_UTSETT, p.id))
        if (oppdrag != null && oppdrag.telefon.isNotBlank()) {
            val ring = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(oppdrag.telefon.filter { it.isDigit() || it == '+' })))
            b.addAction(0, "Ring", PendingIntent.getActivity(ctx, id, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        Varsler.vis(ctx, id, b)
    }

    private suspend fun visMorgen(ctx: Context) {
        val idag = LocalDate.now()
        val alle = ctx.app.repository.apne()
        val antallP = ctx.app.repository.apnePaminnelser().count { it.dato == idag }
        val pTekst = when (antallP) {
            0 -> null
            1 -> "1 påminnelse i dag"
            else -> "$antallP påminnelser i dag"
        }
        val tekst = listOfNotNull(morgenTekst(alle, idag), pTekst).joinToString(", ").ifBlank { null } ?: return
        val dagens = sorter(Prioritet.AVTALE, alle.filter { it.prioritet == Prioritet.AVTALE && it.dato == idag })
        val detaljer = buildString {
            append(tekst)
            dagens.forEach { o -> append("\n").append(o.tid?.let { formaterTidsrom(it, o.sluttid) + " " } ?: "").append(o.tittel) }
        }
        val b = Varsler.bygg(ctx, Varsler.KANAL_MORGEN, "Dagens oppdrag", tekst)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(detaljer))
            .setContentIntent(Varsler.apneApp(ctx, Varsler.ID_MORGEN))
        Varsler.vis(ctx, Varsler.ID_MORGEN, b)
    }
}

/** Etter omstart, oppdatering av appen eller endret klokke: planlegg alt på nytt. */
class OppstartMottaker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        asynkront { ctx.app.planleggAlt(tvingGeofencer = true) }
    }
}
