package no.oppdragsliste.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import no.oppdragsliste.app.MainActivity
import no.oppdragsliste.app.R
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.Radtype
import no.oppdragsliste.app.logikk.WidgetRad
import no.oppdragsliste.app.logikk.Widgetinnhold
import no.oppdragsliste.app.logikk.widgetinnhold
import java.time.LocalDateTime

/**
 * Hjemskjerm-widget: oppsummering, knapper for nytt oppdrag og ny påminnelse, og listen over det som
 * haster i dag. Liten utgave (2×1) viser bare oppsummeringen og pluss-knappen.
 */
class OppdragWidget : GlanceAppWidget() {
    companion object {
        private val LITEN = DpSize(110.dp, 48.dp)
        private val STOR = DpSize(250.dp, 110.dp)

        /** Oppdaterer alle widgeter (kalles etter hver endring i appen). */
        suspend fun oppdater(context: Context) = runCatching { OppdragWidget().updateAll(context) }
    }

    override val sizeMode = SizeMode.Responsive(setOf(LITEN, STOR))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.app.repository
        val innhold = widgetinnhold(repo.alle.first(), repo.paminnelser.first(), LocalDateTime.now())
        provideContent { GlanceTheme { Innhold(context, innhold) } }
    }
}

class OppdragWidgetMottaker : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OppdragWidget()
}

private fun apne(context: Context, handling: String? = null) =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            handling?.let { action = it }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
    )

// Prioritetsfarger (lys, mørk) – samme som i appen.
private fun farge(type: Radtype, forfalt: Boolean) = when {
    forfalt -> ColorProvider(Color(0xFFC62828), Color(0xFFFF8A80))
    else -> when (type) {
        Radtype.AKUTT -> ColorProvider(Color(0xFFD32F2F), Color(0xFFFF6B6B))
        Radtype.AVTALE -> ColorProvider(Color(0xFF1976D2), Color(0xFF64B5F6))
        Radtype.HASTER -> ColorProvider(Color(0xFFEF6C00), Color(0xFFFFB74D))
        Radtype.PAMINNELSE -> ColorProvider(Color(0xFF7B1FA2), Color(0xFFCE93D8))
    }
}

@Composable
private fun Innhold(context: Context, innhold: Widgetinnhold) {
    val liten = LocalSize.current.height < 100.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(apne(context)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "Oppdrag",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    innhold.oppsummering,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = if (liten) 1 else 2,
                )
            }
            if (!liten) {
                Knapp(R.drawable.ic_widget_paminnelse, "Ny påminnelse", apne(context, MainActivity.HANDLING_NY_PAMINNELSE), primar = false)
                Spacer(GlanceModifier.width(8.dp))
            }
            Knapp(R.drawable.ic_widget_legg_til, "Nytt oppdrag", apne(context, MainActivity.HANDLING_NYTT), primar = true)
        }
        if (!liten) {
            Spacer(GlanceModifier.height(8.dp))
            if (innhold.rader.isEmpty()) {
                Text(
                    "Ingenting som haster i dag.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                    items(innhold.rader) { rad -> Rad(context, rad) }
                }
            }
        }
    }
}

@Composable
private fun Knapp(ikon: Int, beskrivelse: String, handling: androidx.glance.action.Action, primar: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .background(if (primar) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .clickable(handling),
    ) {
        Image(
            provider = ImageProvider(ikon),
            contentDescription = beskrivelse,
            colorFilter = ColorFilter.tint(if (primar) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

@Composable
private fun Rad(context: Context, rad: WidgetRad) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(apne(context)),
        ) {
            // Fargekant som i appen (høyere når anlegget står under).
            Box(
                modifier = GlanceModifier.width(5.dp).height(if (rad.under != null) 48.dp else 36.dp).background(farge(rad.type, false)),
            ) {}
            Column(modifier = GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                Text(
                    (if (rad.type == Radtype.PAMINNELSE) "🔔 " else "") + rad.tittel,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                rad.under?.let {
                    Text(
                        it,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                rad.nar,
                style = TextStyle(
                    color = if (rad.forfalt) farge(rad.type, true) else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (rad.forfalt) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
                modifier = GlanceModifier.padding(horizontal = 8.dp),
            )
        }
    }
}
