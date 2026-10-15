package no.oppdragsliste.app.varsler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import no.oppdragsliste.app.MainActivity
import no.oppdragsliste.app.R

/** Varselkanaler og hjelpefunksjoner for å vise varsler. */
object Varsler {
    const val KANAL_SAMTALE = "samtale"
    const val KANAL_AVTALE = "avtale"
    const val KANAL_MORGEN = "morgen"
    const val KANAL_NAER = "naer"
    const val KANAL_PAMINNELSE = "paminnelse"

    const val ID_MORGEN = 1
    const val ID_NAER = 2
    private const val ID_SAMTALE_BASE = 10_000
    private const val ID_AVTALE_BASE = 1_000_000
    private const val ID_PAMINNELSE_BASE = 3_000_000

    fun opprettKanaler(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(KANAL_SAMTALE, "Etter samtaler", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Spør om du vil registrere et oppdrag etter en telefonsamtale"
                },
                NotificationChannel(KANAL_AVTALE, "Avtaler", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Påminnelse før avtalt tid"
                },
                NotificationChannel(KANAL_MORGEN, "Morgenoversikt", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Oversikt over dagens oppdrag"
                },
                NotificationChannel(KANAL_PAMINNELSE, "Påminnelser", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ting du har bedt om å bli minnet på"
                },
                NotificationChannel(KANAL_NAER, "I nærheten", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Oppdrag i nærheten av der du er"
                },
            ),
        )
    }

    fun kanVarsle(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Åpner appen (eventuelt med en handling, f.eks. nytt oppdrag med ferdig utfylt nummer). */
    fun apneApp(context: Context, foresporsel: Int, intent: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent()
        }
        return PendingIntent.getActivity(
            context, foresporsel, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun bygg(context: Context, kanal: String, tittel: String, tekst: String?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, kanal)
            .setSmallIcon(R.drawable.ic_varsel)
            .setContentTitle(tittel)
            .apply { if (tekst != null) setContentText(tekst).setStyle(NotificationCompat.BigTextStyle().bigText(tekst)) }
            .setAutoCancel(true)

    fun vis(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!kanVarsle(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Tillatelsen ble trukket tilbake mens vi jobbet; ingenting å gjøre.
        }
    }

    fun samtaleId(samtaleId: Long) = ID_SAMTALE_BASE + (samtaleId % 100_000).toInt()
    fun avtaleId(oppdragId: Long) = ID_AVTALE_BASE + (oppdragId % 1_000_000).toInt()
    fun paminnelseId(id: Long) = ID_PAMINNELSE_BASE + (id % 1_000_000).toInt()
}
