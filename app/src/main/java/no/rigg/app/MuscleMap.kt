package no.rigg.app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp

// ---------------------------------------------------------------- BELASTNING (ren logikk, testes i StatsTest)

/**
 * Hvor mye hver muskelgruppe ble trent i økta, 0..1 (1 = mest trent). Første muskel i øvelsen er hovedmuskelen og
 * teller fullt per sett, de andre halvt. Helkropp fordeles litt på alle.
 */
fun muscleLoad(log: SessionLog): Map<Muscle, Float> {
    val sum = mutableMapOf<Muscle, Float>()
    log.results.forEach { r ->
        val ex = ExerciseLibrary.get(r.exerciseId) ?: return@forEach
        val sets = r.sets.size.toFloat()
        ex.muscles.forEachIndexed { i, m ->
            if (m == Muscle.FULL_BODY) {
                Muscle.values().filter { it != Muscle.FULL_BODY }.forEach { sum[it] = (sum[it] ?: 0f) + sets * 0.3f }
            } else {
                sum[m] = (sum[m] ?: 0f) + sets * (if (i == 0) 1f else 0.5f)
            }
        }
    }
    val max = sum.values.maxOrNull()?.takeIf { it > 0f } ?: return emptyMap()
    return sum.mapValues { it.value / max }
}

/** Muskelgruppene som ble trent mest, sterkest først. */
fun topMuscles(load: Map<Muscle, Float>, n: Int = 3): List<Muscle> =
    load.entries.filter { it.value >= 0.35f }.sortedByDescending { it.value }.take(n).map { it.key }

// ---------------------------------------------------------------- TEGNINGEN

private sealed class Shape {
    class Ell(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Shape()
    class Circle(val cx: Float, val cy: Float, val r: Float) : Shape()
    class RRect(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val r: Float) : Shape()
    class Poly(vararg val pts: Float) : Shape()
    /** Lem: en tykk strek med runde ender. */
    class Capsule(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val w: Float) : Shape()
}

// Én figur i et 100 x 210-rom. Forfra og bakfra står ved siden av hverandre (bakfra flyttet 110 til høyre).
private val BODY = listOf(
    Shape.Circle(50f, 15f, 11f),
    Shape.RRect(45f, 24f, 55f, 34f, 3f),
    Shape.Poly(26f, 33f, 74f, 33f, 70f, 62f, 66f, 96f, 34f, 96f, 30f, 62f),
    Shape.Capsule(24f, 37f, 18f, 70f, 7f), Shape.Capsule(76f, 37f, 82f, 70f, 7f),
    Shape.Capsule(18f, 70f, 14f, 102f, 5.5f), Shape.Capsule(82f, 70f, 86f, 102f, 5.5f),
    Shape.Poly(34f, 92f, 66f, 92f, 64f, 112f, 36f, 112f),
    Shape.Capsule(42f, 108f, 40f, 150f, 10f), Shape.Capsule(58f, 108f, 60f, 150f, 10f),
    Shape.Capsule(40f, 152f, 40f, 196f, 7f), Shape.Capsule(60f, 152f, 60f, 196f, 7f),
)

private val DELTS = listOf(Shape.Ell(27f, 39f, 8f, 7f), Shape.Ell(73f, 39f, 8f, 7f))
private val UPPER_ARMS = listOf(Shape.Ell(21.5f, 55f, 4.5f, 10f), Shape.Ell(78.5f, 55f, 4.5f, 10f))

private val FRONT: Map<Muscle, List<Shape>> = mapOf(
    Muscle.SHOULDERS to DELTS,
    Muscle.CHEST to listOf(Shape.Ell(41f, 46f, 9.5f, 7f), Shape.Ell(59f, 46f, 9.5f, 7f)),
    Muscle.BICEPS to UPPER_ARMS,
    Muscle.ABS to listOf(56f, 68f, 80f).flatMap { y ->
        val y1 = if (y == 80f) 92f else y + 10f
        listOf(Shape.RRect(43f, y, 49.3f, y1, 2f), Shape.RRect(50.7f, y, 57f, y1, 2f))
    },
    Muscle.QUADS to listOf(Shape.Ell(42f, 130f, 8f, 19f), Shape.Ell(58f, 130f, 8f, 19f)),
    Muscle.CALVES to listOf(Shape.Ell(40.5f, 170f, 4.5f, 13f), Shape.Ell(59.5f, 170f, 4.5f, 13f)),
)

private val BACK: Map<Muscle, List<Shape>> = mapOf(
    Muscle.SHOULDERS to DELTS,
    Muscle.BACK to listOf(
        Shape.Poly(40f, 33f, 60f, 33f, 55f, 48f, 45f, 48f), // trapes
        Shape.Ell(39f, 58f, 8.5f, 14f), Shape.Ell(61f, 58f, 8.5f, 14f), // latissimus
        Shape.RRect(45f, 72f, 55f, 92f, 3f), // korsrygg
    ),
    Muscle.TRICEPS to UPPER_ARMS,
    Muscle.GLUTES_HAMS to listOf(
        Shape.Ell(43f, 102f, 8f, 9f), Shape.Ell(57f, 102f, 8f, 9f), // sete
        Shape.Ell(42f, 132f, 7f, 17f), Shape.Ell(58f, 132f, 7f, 17f), // baksida lår
    ),
    Muscle.CALVES to listOf(Shape.Ell(40.5f, 168f, 6f, 14f), Shape.Ell(59.5f, 168f, 6f, 14f)),
)

/**
 * Muskelkart forfra og bakfra, som i Strava: trente muskler i aksentfargen, sterkere jo mer de er trent,
 * resten grå. Tegnet i kode (ingen bilder).
 */
@Composable
fun MuscleMap(load: Map<Muscle, Float>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val body = colors.surfaceContainerHighest
    val idle = lerp(colors.surfaceContainerHighest, colors.onSurface, 0.12f)
    val active = colors.secondary
    Canvas(modifier) {
        val s = minOf(size.width / 210f, size.height / 210f)
        val ox = (size.width - 210f * s) / 2f
        val oy = (size.height - 210f * s) / 2f
        fun color(m: Muscle): Color {
            val v = load[m] ?: 0f
            return if (v <= 0f) idle else lerp(idle, active, 0.35f + 0.65f * v.coerceIn(0f, 1f))
        }
        for ((dx, muscles) in listOf(0f to FRONT, 110f to BACK)) {
            BODY.forEach { drawShape(it, body, s, ox + dx * s, oy) }
            muscles.forEach { (m, shapes) -> shapes.forEach { drawShape(it, color(m), s, ox + dx * s, oy) } }
        }
    }
}

private fun DrawScope.drawShape(shape: Shape, color: Color, s: Float, ox: Float, oy: Float) {
    fun pt(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    when (shape) {
        is Shape.Circle -> drawCircle(color, shape.r * s, pt(shape.cx, shape.cy))
        is Shape.Ell -> drawOval(color, pt(shape.cx - shape.rx, shape.cy - shape.ry), Size(2 * shape.rx * s, 2 * shape.ry * s))
        is Shape.RRect -> drawRoundRect(
            color, pt(shape.x0, shape.y0), Size((shape.x1 - shape.x0) * s, (shape.y1 - shape.y0) * s), CornerRadius(shape.r * s),
        )
        is Shape.Poly -> {
            val path = Path()
            shape.pts.toList().chunked(2).forEachIndexed { i, (x, y) ->
                val p = pt(x, y)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color)
        }
        is Shape.Capsule -> drawLine(color, pt(shape.x0, shape.y0), pt(shape.x1, shape.y1), 2 * shape.w * s, StrokeCap.Round)
    }
}
