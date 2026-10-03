package no.rigg.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val locale: Locale get() = if (Lang.isNorwegian) Locale.forLanguageTag("nb") else Locale.ENGLISH

/**
 * Historikken i full skjerm: alle øktene gruppert på måned. Trykk på en økt for detaljer med muskelkart (som i Strava),
 * tall, øvelser og sett, deling og sletting. [startWith] åpner rett på én økt.
 */
@Composable
fun HistoryScreen(vm: AppViewModel, startWith: SessionLog? = null, onDismiss: () -> Unit) {
    var selectedKey by remember { mutableStateOf(startWith?.let { vm.logKey(it) }) }
    val selected = selectedKey?.let { key -> vm.logs.find { vm.logKey(it) == key } }

    // Egen skjerm i appen (ikke et vindu oppå), så Scaffold i MainActivity holder den unna status- og navigasjonslinja.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (selected != null) {
            // Tilbake fra en økt: til lista, eller lukk hvis historikken ble åpnet rett på økta.
            fun back() {
                if (startWith != null) onDismiss() else selectedKey = null
            }
            BackHandler { back() }
            SessionDetail(vm, selected, onBack = { back() }, onDeleted = { back() })
        } else {
            BackHandler(onBack = onDismiss)
            HistoryList(vm, onBack = onDismiss, onOpen = { selectedKey = vm.logKey(it) })
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            // Pil tilbake: «ned»-pila rotert.
            Icon(RiggIcons.ChevronDown, contentDescription = tr("Back", "Tilbake"), modifier = Modifier.rotate(90f))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- LISTA

@Composable
private fun HistoryList(vm: AppViewModel, onBack: () -> Unit, onOpen: (SessionLog) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val logs = vm.logs.asReversed()
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("History", "Historikk"), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (logs.isEmpty()) {
                Text(tr("No logged workouts yet.", "Ingen loggførte økter ennå."), color = colors.onSurfaceVariant)
                return@Column
            }
            // Oppsummering øverst.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
            ) {
                Stat("${logs.size}", tr("WORKOUTS", "ØKTER"), Modifier.weight(1f))
                Stat("${logs.sumOf { it.durationMin }}", tr("MINUTES", "MINUTTER"), Modifier.weight(1f))
                Stat("${logs.sumOf { l -> l.results.sumOf { it.sets.size } }}", tr("SETS", "SETT"), Modifier.weight(1f))
            }
            var month: YearMonth? = null
            logs.forEach { log ->
                val date = runCatching { LocalDate.parse(log.date) }.getOrNull()
                val ym = date?.let { YearMonth.from(it) }
                if (ym != null && ym != month) {
                    month = ym
                    Spacer(Modifier.height(6.dp))
                    SectionLabel(ym.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)))
                }
                HistoryRow(log, date, onClick = { onOpen(log) })
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        SectionLabel(label)
    }
}

@Composable
private fun HistoryRow(log: SessionLog, date: LocalDate?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sets = log.results.sumOf { it.sets.size }
    val volume = sessionVolumeKg(log)
    val top = remember(log) { topMuscles(muscleLoad(log)) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Dato som en liten kalenderblokk.
        Column(
            Modifier.widthIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(date?.dayOfMonth?.toString() ?: "–", style = MaterialTheme.typography.titleLarge)
            Text(
                date?.dayOfWeek?.getDisplayName(TextStyle.SHORT, locale)?.uppercase()?.trimEnd('.') ?: "",
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(log.dayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    log.durationMin.takeIf { it > 0 }?.let { "$it min" },
                    tr("$sets sets", "$sets sett"),
                    volume.takeIf { it > 0 }?.let { "${formatThousands(it.toLong())} kg" },
                    log.avgHr?.let { tr("HR $it", "puls $it") },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            if (top.isNotEmpty()) {
                Text(top.joinToString(" · ") { it.label }, style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
            }
        }
        if (log.stravaActivityId != null) {
            Text("Strava", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
        }
    }
}

// ---------------------------------------------------------------- ÉN ØKT

@Composable
private fun SessionDetail(vm: AppViewModel, log: SessionLog, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val load = remember(log) { muscleLoad(log) }
    val volume = sessionVolumeKg(log)
    val sets = log.results.sumOf { it.sets.size }
    var confirmDelete by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Exercise?>(null) }
    infoFor?.let { ExerciseInfoDialog(it, onDismiss = { infoFor = null }) }

    Column(Modifier.fillMaxSize()) {
        TopBar(log.dayTitle, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(dateLine(log), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)

            // Tallene.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp)) {
                if (log.durationMin > 0) Stat("${log.durationMin}", tr("MIN", "MIN"), Modifier.weight(1f))
                Stat("$sets", tr("SETS", "SETT"), Modifier.weight(1f))
                if (volume > 0) Stat(formatThousands(volume.toLong()), "KG", Modifier.weight(1f))
                log.avgHr?.let { Stat("$it", tr("AVG HR", "SNITTPULS"), Modifier.weight(1f)) }
            }

            // Muskelkartet.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel(tr("Muscles worked", "Muskler som ble trent"))
                if (load.isEmpty()) {
                    Text(tr("No sets logged.", "Ingen sett logget."), color = colors.onSurfaceVariant)
                } else {
                    MuscleMap(load, Modifier.fillMaxWidth().height(360.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Text(tr("Front", "Forfra"), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text(tr("Back", "Bakfra"), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    Text(
                        load.entries.sortedByDescending { it.value }.filter { it.value > 0f }
                            .joinToString(" · ") { it.key.label },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Øvelsene.
            SectionLabel(tr("Exercises", "Øvelser"))
            log.results.filter { it.sets.isNotEmpty() }.forEach { r ->
                val ex = ExerciseLibrary.get(r.exerciseId)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainer)
                        .clickable(enabled = ex != null) { infoFor = ex }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (ex != null) ExerciseTile(ex, size = 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ex?.name ?: r.exerciseId, style = MaterialTheme.typography.titleSmall)
                        Text(setsSummary(r.sets, ex?.timed == true), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        ex?.muscles?.filter { it != Muscle.FULL_BODY }?.takeIf { it.isNotEmpty() }?.let { ms ->
                            Text(ms.joinToString(" · ") { it.label }, style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
        // Handlinger: alltid synlige nederst, over navigasjonslinja.
        Row(
            Modifier.fillMaxWidth().background(colors.surfaceContainerLow).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { vm.shareLog = log }) {
                Icon(RiggIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Share / Strava", "Del / Strava"))
            }
            log.stravaActivityId?.let { id ->
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(StravaClient.activityUrl(id))))
                    }
                }) { Text("Strava") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmDelete = true }) { Text(tr("Delete", "Slett"), color = colors.error) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = tr("Delete the workout?", "Slette økta?"),
            text = tr("“${log.dayTitle}” is removed from the history. This can't be undone.", "«${log.dayTitle}» fjernes fra historikken. Det kan ikke angres."),
            confirmLabel = tr("Delete", "Slett"),
            onConfirm = { vm.deleteLog(log); onDeleted() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** «Fredag 2. oktober 2026 · 20:10», med klokkeslett når det er kjent. */
private fun dateLine(log: SessionLog): String {
    val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return log.date
    val pattern = if (Lang.isNorwegian) "EEEE d. MMMM yyyy" else "EEEE, MMMM d, yyyy"
    val day = date.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.uppercase() }
    val time = log.startedAtMs.takeIf { it > 0 }?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    return listOfNotNull(day, time).joinToString(" · ")
}
