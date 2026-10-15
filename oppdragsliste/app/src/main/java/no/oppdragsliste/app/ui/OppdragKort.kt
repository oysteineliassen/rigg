package no.oppdragsliste.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.NotificationsNone
import no.oppdragsliste.app.data.Paminnelse
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.dagerSiden
import no.oppdragsliste.app.logikk.erForfalt
import no.oppdragsliste.app.logikk.formaterDato
import no.oppdragsliste.app.logikk.formaterDatoTid
import no.oppdragsliste.app.logikk.formaterTelefon
import no.oppdragsliste.app.logikk.lagtInnTekst
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OppdragKort(
    o: Oppdrag,
    na: LocalDateTime,
    utvidet: Boolean,
    onKlikk: () -> Unit,
    onFerdig: (Boolean) -> Unit,
    onEndre: () -> Unit,
    onSlett: () -> Unit,
    modifier: Modifier = Modifier,
    avstand: String? = null,
    visPrioritet: Boolean = false,
    paminnelser: List<Paminnelse> = emptyList(),
    onPaminnelse: (() -> Unit)? = null,
    notat: String? = null,
) {
    val context = LocalContext.current
    val idag = na.toLocalDate()
    val forfalt = erForfalt(o, na)
    var bekreftSlett by remember(o.id) { mutableStateOf(false) }
    LaunchedEffect(utvidet) { if (!utvidet) bekreftSlett = false }

    Card(
        onClick = onKlikk,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(if (o.ferdig) MaterialTheme.colorScheme.outlineVariant else o.prioritet.farge()),
            )
            Column(
                Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.tittel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (utvidet) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (avstand != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(avstand, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (visPrioritet) {
                    Text(o.prioritet.tittel, style = MaterialTheme.typography.labelMedium, color = o.prioritet.farge())
                }
                // Kunde står i overskriften når beskrivelsen er tom.
                val kundeLinje = listOf(o.kunde.takeIf { o.beskrivelse.isNotBlank() }.orEmpty(), o.anlegg)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (kundeLinje.isNotBlank()) Linje(Icons.Outlined.Person, kundeLinje)
                if (o.stedTekst.isNotBlank()) Linje(Icons.Outlined.Place, o.stedTekst)
                val kontakt = listOf(o.kontaktperson, formaterTelefon(o.telefon)).filter { it.isNotBlank() }.joinToString(" · ")
                if (kontakt.isNotBlank()) Linje(Icons.Outlined.Phone, kontakt)
                formaterDatoTid(o.dato, o.tid, idag, o.sluttid)?.let { tekst ->
                    Linje(
                        Icons.Outlined.Event,
                        if (forfalt) "$tekst · forfalt" else tekst,
                        farge = if (forfalt) forfaltFarge else null,
                        fet = forfalt || o.prioritet == Prioritet.AVTALE,
                    )
                }
                when {
                    o.ferdig -> o.ferdigDato?.let {
                        Linje(Icons.Outlined.Check, "Utført " + formaterDato(it.atZone(ZoneId.systemDefault()).toLocalDate(), idag).lowercase())
                    }
                    o.prioritet == Prioritet.OMRADE || o.prioritet == Prioritet.HASTER ->
                        Linje(Icons.Outlined.Schedule, lagtInnTekst(dagerSiden(o.opprettet, idag)))
                    else -> {}
                }

                // Notat for kunden/anlegget (f.eks. adgangskode), hele når kortet er utvidet.
                if (!notat.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.AutoMirrored.Outlined.StickyNote2, null, Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            notat,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = if (utvidet) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Påminnelser koblet til oppdraget (de to første).
                paminnelser.take(2).forEach { p ->
                    val nar = formaterDatoTid(p.dato, p.tid, idag)
                    Linje(
                        Icons.Outlined.NotificationsNone,
                        p.tekst.lines().first() + (nar?.let { " · $it" } ?: ""),
                        farge = if (erForfalt(p, na)) forfaltFarge else MaterialTheme.colorScheme.primary,
                    )
                }
                if (paminnelser.size > 2) Linje(Icons.Outlined.NotificationsNone, "+${paminnelser.size - 2} påminnelser til")

                AnimatedVisibility(visible = utvidet) {
                    Column(Modifier.padding(top = 10.dp)) {
                        if (bekreftSlett) {
                            Text("Slette oppdraget for godt?", style = MaterialTheme.typography.bodyMedium)
                            Row(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = { bekreftSlett = false }) { Text("Avbryt") }
                                Button(
                                    onClick = onSlett,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) { Text("Slett") }
                            }
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (!o.ferdig) {
                                    if (o.telefon.isNotBlank()) {
                                        Handling(Icons.Outlined.Call, "Ring") { ring(context, o.telefon) }
                                    }
                                    if (o.stedTekst.isNotBlank() || o.harKoordinater) {
                                        Handling(Icons.Outlined.Map, "Vis i kart") { visIKart(context, o) }
                                    }
                                    Handling(Icons.Outlined.Check, "Ferdig", fremhevet = true) { onFerdig(true) }
                                    Handling(Icons.Outlined.Edit, "Endre", onClick = onEndre)
                                    if (onPaminnelse != null) Handling(Icons.Outlined.AlarmAdd, "Påminnelse", onClick = onPaminnelse)
                                } else {
                                    Handling(Icons.AutoMirrored.Outlined.Undo, "Gjenåpne") { onFerdig(false) }
                                }
                                Handling(Icons.Outlined.Delete, "Slett") { bekreftSlett = true }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Linje(ikon: ImageVector, tekst: String, farge: Color? = null, fet: Boolean = false) {
    val f = farge ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ikon, contentDescription = null, tint = f, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            tekst,
            style = MaterialTheme.typography.bodyMedium,
            color = f,
            fontWeight = if (fet) FontWeight.SemiBold else null,
        )
    }
}

@Composable
private fun Handling(ikon: ImageVector, tekst: String, fremhevet: Boolean = false, onClick: () -> Unit) {
    val innhold: @Composable () -> Unit = {
        Icon(ikon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(tekst)
    }
    if (fremhevet) {
        Button(onClick = onClick) { innhold() }
    } else {
        FilledTonalButton(onClick = onClick) { innhold() }
    }
}

/** Viser avstand som «850 m» eller «12,3 km». */
fun formaterAvstand(meter: Float): String = when {
    meter < 1000 -> "${(meter / 10).toInt() * 10} m"
    meter < 10_000 -> String.format(no.oppdragsliste.app.logikk.norsk, "%.1f km", meter / 1000)
    else -> "${(meter / 1000).toInt()} km"
}
