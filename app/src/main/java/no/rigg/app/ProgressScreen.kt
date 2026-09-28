package no.rigg.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern(tr("MMM d", "d. MMM"), Locale.getDefault())

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ids = remember(vm.logs) { loggedExerciseIds(vm.logs) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(tr("Progress", "Fremgang"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    Modifier.weight(1f),
                    buildAnnotatedString { append(sessionsLastDays(vm.logs, today).toString()) },
                    tr("sessions, 30 days", "økter, 30 dager"),
                )
                StatTile(
                    Modifier.weight(1f),
                    buildAnnotatedString { append(weekStreak(vm.logs, today).toString()) },
                    tr("weeks in a row", "uker på rad"),
                    accent = true,
                )
                StatTile(Modifier.weight(1f), totalTimeText(totalMinutes(vm.logs)), tr("trained in total", "trent totalt"))
            }
            Spacer(Modifier.height(2.dp))
        }
        item {
            WeeklyChart(sessionsPerWeek(vm.logs, today), goal = vm.profile.daysPerWeek)
            Spacer(Modifier.height(10.dp))
        }
        item { SectionLabel(tr("Exercises", "Øvelser")) }

        if (ids.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(tr("No logged workouts yet", "Ingen loggførte økter ennå"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr(
                            "Finish a workout or log one on the Plan tab, and each exercise shows up here with its trend.",
                            "Fullfør en økt eller logg den på Program-fanen, så dukker hver øvelse opp her med utviklingen sin.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(ids, key = { it }) { id ->
            val ex = ExerciseLibrary.get(id) ?: return@items
            val history = remember(vm.logs, id) { historyFor(vm.logs, ex) }
            if (history.isEmpty()) return@items
            ExerciseProgressRow(ex, history, lastSetSummary(vm.logs, ex)) { selectedId = id }
        }
    }

    val id = selectedId
    if (id != null) {
        val ex = ExerciseLibrary.get(id)
        val history = ex?.let { historyFor(vm.logs, it) } ?: emptyList()
        Dialog(onDismissRequest = { selectedId = null }) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
            ) {
                if (ex != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseTile(ex, size = 48.dp, animate = true)
                        Spacer(Modifier.width(12.dp))
                        Text(ex.name, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    ExerciseProgressChart(history)
                } else {
                    Text(id)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { selectedId = null }, modifier = Modifier.align(Alignment.End)) { Text(tr("Close", "Lukk")) }
            }
        }
    }
}

@Composable
private fun totalTimeText(minutes: Int): AnnotatedString {
    val small = SpanStyle(fontSize = 18.sp)
    return buildAnnotatedString {
        if (minutes < 60) {
            append(minutes.toString())
            withStyle(small) { append(" min") }
        } else {
            append((minutes / 60).toString())
            withStyle(small) { append(tr("h", "t")) }
            append(" " + "%02d".format(minutes % 60))
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, value: AnnotatedString, label: String, accent: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = if (accent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Søyler for økter per uke. Uker som når målet er rust, denne uka er lys, resten grå. */
@Composable
private fun WeeklyChart(weeks: List<Pair<Int, Int>>, goal: Int) {
    val colors = MaterialTheme.colorScheme
    val top = maxOf(goal, weeks.maxOfOrNull { it.second } ?: 0, 1)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(tr("Sessions per week", "Økter per uke"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(tr("goal $goal", "mål $goal"), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            weeks.forEachIndexed { i, (_, count) ->
                val current = i == weeks.lastIndex
                val color = when {
                    current -> colors.onSurface
                    count >= goal -> colors.secondary
                    else -> colors.outline
                }
                val fraction = if (count == 0) 0.04f else count.toFloat() / top
                Box(
                    Modifier.weight(1f).fillMaxHeight(fraction).clip(RoundedCornerShape(6.dp))
                        .background(if (count == 0) colors.outlineVariant else color),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            weeks.forEachIndexed { i, (week, _) ->
                val current = i == weeks.lastIndex
                Text(
                    week.toString(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (current) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ExerciseProgressRow(ex: Exercise, history: List<ProgressPoint>, last: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseTile(ex, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ex.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (last != null) {
                Text(
                    tr("last $last", "sist $last"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (history.size >= 2) Sparkline(history, Modifier.width(64.dp).height(28.dp))
        Text(
            progressDelta(history),
            Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Sparkline(points: List<ProgressPoint>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    val minV = points.minOf { it.value }
    val range = (points.maxOf { it.value } - minV).let { if (it < 0.01) 1.0 else it }
    Canvas(modifier) {
        val pad = 3.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun x(i: Int) = pad + w * i / (points.size - 1)
        fun y(v: Double) = pad + h * (1f - ((v - minV) / range).toFloat())
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(points.lastIndex), y(points.last().value)))
    }
}

@Composable
private fun ExerciseProgressChart(history: List<ProgressPoint>) {
    if (history.isEmpty()) return
    val last = history.last()
    val best = history.maxByOrNull { it.value } ?: last

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(tr("Latest", "Siste"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatWeight(last.value)} ${last.unit}", style = MaterialTheme.typography.headlineSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(tr("Best", "Beste"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatWeight(best.value)} ${best.unit}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
    Spacer(Modifier.height(12.dp))

    if (history.size < 2) {
        Text(
            tr("Log at least two sessions with this exercise to see a chart.", "Logg minst to økter med denne øvelsen for å se en graf."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LineChart(history, modifier = Modifier.fillMaxWidth().height(160.dp))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(history.first().date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(history.last().date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        tr(
            "${history.size} logged ${if (history.size == 1) "session" else "sessions"}",
            "${history.size} loggførte økter",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LineChart(points: List<ProgressPoint>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minV = points.minOf { it.value }
    val maxV = points.maxOf { it.value }
    val range = (maxV - minV).let { if (it < 0.01) 1.0 else it }

    Canvas(modifier) {
        val padding = 12f
        val plotW = size.width - padding * 2
        val plotH = size.height - padding * 2

        fun xFor(i: Int) = padding + if (points.size == 1) plotW / 2 else plotW * i / (points.size - 1)
        fun yFor(v: Double) = padding + plotH * (1f - ((v - minV) / range).toFloat())

        // Bunnlinje
        drawLine(
            color = axisColor.copy(alpha = 0.25f),
            start = Offset(padding, size.height - padding),
            end = Offset(size.width - padding, size.height - padding),
            strokeWidth = 2f,
        )

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.value)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        points.forEachIndexed { i, p ->
            drawCircle(color = lineColor, radius = 9f, center = Offset(xFor(i), yFor(p.value)))
        }
    }
}
