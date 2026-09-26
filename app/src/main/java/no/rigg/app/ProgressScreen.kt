package no.rigg.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("d. MMM", Locale.forLanguageTag("no"))

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ids = remember(vm.logs) { loggedExerciseIds(vm.logs) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fremgang", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (ids.isEmpty()) {
            Text(
                "Ingen loggførte økter ennå.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Logg en økt med reps og vekt på Program-fanen, så vises fremgangen din her.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ids) { id ->
                    val ex = ExerciseLibrary.get(id)
                    val history = ex?.let { historyFor(vm.logs, it) } ?: emptyList()
                    if (ex != null && history.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth().clickable { selectedId = id }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ExerciseThumbnail(ex, size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name, fontWeight = FontWeight.Medium)
                                    val last = history.last()
                                    Text(
                                        "${history.size} økter logget · sist ${formatWeight(last.value)} ${last.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val id = selectedId
    if (id != null) {
        val ex = ExerciseLibrary.get(id)
        val history = ex?.let { historyFor(vm.logs, it) } ?: emptyList()
        Dialog(onDismissRequest = { selectedId = null }) {
            Card {
                Column(Modifier.padding(20.dp)) {
                    if (ex != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseThumbnail(ex, size = 36.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        ExerciseProgressChart(history)
                    } else {
                        Text(id)
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { selectedId = null }, modifier = Modifier.align(Alignment.End)) { Text("Lukk") }
                }
            }
        }
    }
}

@Composable
private fun ExerciseProgressChart(history: List<ProgressPoint>) {
    if (history.isEmpty()) return
    val last = history.last()
    val best = history.maxByOrNull { it.value } ?: last

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Siste", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatWeight(last.value)} ${last.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Beste", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatWeight(best.value)} ${best.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(12.dp))

    if (history.size < 2) {
        Text(
            "Logg minst to økter med denne øvelsen for å se en graf.",
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
        "${history.size} loggførte økter",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LineChart(points: List<ProgressPoint>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
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
