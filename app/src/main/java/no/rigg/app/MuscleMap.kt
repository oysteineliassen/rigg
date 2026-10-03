package no.rigg.app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser

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
//
// Venstre halvdel av hver figur som SVG-baner i et 100 x 215-rom (midtlinje x = 50), speilet til høyre.
// Forhåndsvist i nettleser som SVG før de ble lagt inn her. Musklene følger omrisset av de virkelige musklene:
// skulderhette, bryst, biceps, magerutene og de skrå, lår og legg forfra; trapes, latissimus, korsrygg, triceps,
// sete, baksida lår og legg bakfra. Underarmene er med for helhetens skyld (null = ikke en egen gruppe).

private const val SILHOUETTE = "M50,27 C47.5,27 46,28.5 46,31 L45.5,33 C40,34.5 33,35.5 27.5,37.5 C22,39.5 18.5,44 18,50 C17.5,58 16.5,66 15.5,74 C15,78 14.2,82 13.6,86 C12.5,94 11.6,101 11.2,108 C10.5,112 9.6,116 10.4,119.5 C11.6,122 14.2,121 14.8,117.5 C15.3,113.5 15.4,110 16.2,107 C17.6,100 19.2,92 20.4,85 C21.2,80 22,74 23,68 C24.2,62 25.5,57 26.8,53.5 C27.6,58 28.3,63 28.8,68 C29.5,76 30.8,84 30.6,92 C30.2,98 29.2,104 29,110 C28.7,122 29.6,134 31.5,146 C32.2,150 32.4,154 32,158 C31.2,168 31.8,180 33.6,192 C34.2,197 34.4,201 33.6,205 C32.4,209 34.4,211.5 38,211.5 L44,211.5 C45.6,211.5 45.6,208.5 44.8,205 C44,200 44,194 44.6,188 C45.6,178 46.2,168 45.8,158 C45.6,154 45.6,151 46.2,147 C47.8,136 48.8,124 49.2,114 L50,112 Z"

/** Omrisset uten linja ned midten (den lukker halvdelen, men skal ikke tegnes som strek). */
private const val SILHOUETTE_OUTLINE = "M50,27 C47.5,27 46,28.5 46,31 L45.5,33 C40,34.5 33,35.5 27.5,37.5 C22,39.5 18.5,44 18,50 C17.5,58 16.5,66 15.5,74 C15,78 14.2,82 13.6,86 C12.5,94 11.6,101 11.2,108 C10.5,112 9.6,116 10.4,119.5 C11.6,122 14.2,121 14.8,117.5 C15.3,113.5 15.4,110 16.2,107 C17.6,100 19.2,92 20.4,85 C21.2,80 22,74 23,68 C24.2,62 25.5,57 26.8,53.5 C27.6,58 28.3,63 28.8,68 C29.5,76 30.8,84 30.6,92 C30.2,98 29.2,104 29,110 C28.7,122 29.6,134 31.5,146 C32.2,150 32.4,154 32,158 C31.2,168 31.8,180 33.6,192 C34.2,197 34.4,201 33.6,205 C32.4,209 34.4,211.5 38,211.5 L44,211.5 C45.6,211.5 45.6,208.5 44.8,205 C44,200 44,194 44.6,188 C45.6,178 46.2,168 45.8,158 C45.6,154 45.6,151 46.2,147 C47.8,136 48.8,124 49.2,114 L50,112"

private const val HEAD = "M50,4 C55.5,4 59,8.5 59,15 C59,21.5 55,27 50,27 C45,27 41,21.5 41,15 C41,8.5 44.5,4 50,4 Z"

private val FRONT_PATHS: List<Pair<Muscle?, List<String>>> = listOf(
    Muscle.SHOULDERS to listOf(
        "M28,38.5 C23,39.5 19.5,43.5 19,49 C19,51.5 19.5,53.5 20.5,55 C22,51.5 24.5,48.5 28,46.5 C30,44.5 30.5,40.5 28,38.5 Z",
    ),
    Muscle.CHEST to listOf(
        "M48.6,38.6 C42,37.6 34.5,38.2 30.2,41 C28.6,44.5 29,49 31.6,52.6 C35.6,56.4 42.4,57 48.6,54.4 Z",
    ),
    Muscle.BICEPS to listOf(
        "M21.4,56.8 C19.6,61 18.8,66.6 19.4,72 C19.8,74.4 21.2,75.4 22.4,73.8 C24.4,69.6 25.6,64.2 25.6,58.6 C25.2,55.2 22.8,54.4 21.4,56.8 Z",
    ),
    null to listOf(
        "M19.2,78.6 C16.8,85 14.8,92.6 13.8,100.4 C13.6,103 15.2,103.4 16.2,101.4 C18.4,95.8 20,89.6 21,83 C21.4,79.6 20.4,77.6 19.2,78.6 Z",
    ),
    Muscle.ABS to listOf(
        "M42.8,58.2 C42.6,61 42.6,63.6 42.8,66.2 L48.6,66.6 L48.6,58.6 C46.6,57.6 44.6,57.6 42.8,58.2 Z",
        "M42.6,68.4 L42.6,76.4 L48.6,76.6 L48.6,68.6 Z",
        "M42.8,78.6 L42.8,86.6 L48.6,87 L48.6,78.8 Z",
        "M43.2,88.8 C43.6,94 45.2,99.4 48.6,103.4 L48.6,89 Z",
        "M32,57 C33.4,64 34.6,72 35.4,80 C36,85 37.2,89 39.4,92 C40.6,88 40.8,82 40.6,76 C40.4,69 40,62 39.4,56.8 C37,55.6 34.2,55.6 32,57 Z",
    ),
    Muscle.QUADS to listOf(
        "M33.6,101 C31.2,110 30.8,121 32.6,132 C33.8,139 35.4,145 38,149 C40.6,151 43.2,149.6 44.2,146.4 C46,138 47.2,128 47,118 C46.8,111 45.2,105.4 42.4,102.2 C39.6,99.6 35.8,99.2 33.6,101 Z",
    ),
    Muscle.CALVES to listOf(
        "M34.6,160 C33.2,168 33.4,178 35.2,188 C35.8,192 37.2,194 38.2,192 C39.6,184 40.4,174 40.2,165 C40,161 38.8,158.4 37,158 C36,158 35.2,158.6 34.6,160 Z",
        "M44.4,160 C45.4,168 45.2,176 44,183 C43.2,180 42.4,172 42.4,165 C42.4,161 43.2,159 44.4,160 Z",
    ),
)

private val BACK_PATHS: List<Pair<Muscle?, List<String>>> = listOf(
    Muscle.BACK to listOf(
        "M50,28.5 C47.8,32 44,34.6 38,36 C33,37 30,38.2 29,40 C34,42 39,45.4 43,50 C45.6,53 47.6,57 49.4,61 L50,61 Z",
        "M31.4,47.4 C30.4,55 31,63 33,70.6 C34.6,76 37.6,80.6 42,82.4 C45,83.4 47,81 48,77.4 C47,70.6 44.6,63.4 41,57.4 C38.4,53 35,49.6 31.4,47.4 Z",
        "M44.6,84 C43.8,88.8 44,93.4 45.6,97.4 L49.4,98.6 L49.4,82.6 C47.6,82.8 45.8,83.2 44.6,84 Z",
    ),
    Muscle.SHOULDERS to listOf(
        "M28,38.5 C23,39.5 19.5,43.5 19,49 C19,51.5 19.5,53.5 20.5,55 C22,51.5 24.5,48.5 28,46.5 C30,44.5 30.5,40.5 28,38.5 Z",
    ),
    Muscle.TRICEPS to listOf(
        "M21,55.6 C19,60.4 18.4,66.4 19.2,72 C19.6,74.6 21.6,75.2 22.6,73 C24.2,68.6 25.2,63 25.2,57.6 C24.6,54.6 22.4,53.8 21,55.6 Z",
    ),
    null to listOf(
        "M19.2,78.6 C16.8,85 14.8,92.6 13.8,100.4 C13.6,103 15.2,103.4 16.2,101.4 C18.4,95.8 20,89.6 21,83 C21.4,79.6 20.4,77.6 19.2,78.6 Z",
    ),
    Muscle.GLUTES_HAMS to listOf(
        "M32.6,96.4 C30,103 30.4,111 34.6,116.2 C38.4,120.4 44.4,120.8 48.6,117 C49.4,112 49.4,106 48.6,100 C44.8,95.4 37.6,93.6 32.6,96.4 Z",
        "M32.8,123 C31.4,131 32.4,140 35.2,147.4 C36.6,150.4 39.4,151.4 41.2,149.4 C42,144 42,136 41.2,126 C40.6,122.4 38.6,120.8 36.4,121 C34.8,121.2 33.6,121.8 32.8,123 Z",
        "M43,125 C43.8,133 44.2,141 43.6,148 C45.8,145 47.2,138 47.6,130 C47.8,125.6 46.6,122.4 44.8,122.4 C43.8,122.6 43.2,123.6 43,125 Z",
    ),
    Muscle.CALVES to listOf(
        "M33.4,160 C31.6,167 31.8,175 34.2,182 C35.6,186 38.2,187.4 39.6,184.4 C40.6,178 40.6,170 39.8,163 C39.2,159 37.4,157 35.6,157.4 C34.6,157.8 33.8,158.8 33.4,160 Z",
        "M41.8,161 C41,168 41.2,176 42.6,182.6 C44.6,178 45.4,171 45,164 C44.8,160 43.8,158 42.8,158.4 C42.2,158.8 41.9,159.8 41.8,161 Z",
    ),
)

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private class FigurePaths(val muscles: List<Pair<Muscle?, List<Path>>>)

private class BodyPaths(val silhouette: Path, val outline: Path, val head: Path, val front: FigurePaths, val back: FigurePaths)

private fun bodyPaths() = BodyPaths(
    silhouette = path(SILHOUETTE),
    outline = path(SILHOUETTE_OUTLINE),
    head = path(HEAD),
    front = FigurePaths(FRONT_PATHS.map { (m, ds) -> m to ds.map(::path) }),
    back = FigurePaths(BACK_PATHS.map { (m, ds) -> m to ds.map(::path) }),
)

/**
 * Muskelkart forfra og bakfra, som i Strava: trente muskler i aksentfargen, sterkere jo mer de er trent,
 * resten grå. Tegnet i kode (ingen bilder).
 */
@Composable
fun MuscleMap(load: Map<Muscle, Float>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val paths = remember { bodyPaths() }
    val body = colors.surfaceContainerHighest
    val outline = lerp(colors.surfaceContainerHighest, colors.onSurface, 0.22f)
    val idle = lerp(colors.surfaceContainerHighest, colors.onSurface, 0.13f)
    val active = colors.secondary
    Canvas(modifier) {
        // To figurer på 100 x 215 med 20 mellomrom.
        val s = minOf(size.width / 220f, size.height / 215f)
        val ox = (size.width - 220f * s) / 2f
        val oy = (size.height - 215f * s) / 2f
        fun color(m: Muscle?): Color {
            val v = m?.let { load[it] } ?: 0f
            return if (v <= 0f) idle else lerp(idle, active, 0.35f + 0.65f * v.coerceIn(0f, 1f))
        }
        for ((dx, figure) in listOf(0f to paths.front, 120f to paths.back)) {
            translate(ox + dx * s, oy) {
                scale(s, s, pivot = Offset.Zero) {
                    // Venstre halvdel, så den samme speilet om midtlinja.
                    for (mirror in listOf(false, true)) {
                        scale(if (mirror) -1f else 1f, 1f, pivot = Offset(50f, 0f)) {
                            drawHalf(paths, figure, body, outline, ::color)
                        }
                    }
                    drawPath(paths.head, body)
                    drawPath(paths.head, outline, style = Stroke(width = 0.6f))
                }
            }
        }
    }
}

private fun DrawScope.drawHalf(paths: BodyPaths, figure: FigurePaths, body: Color, outline: Color, color: (Muscle?) -> Color) {
    drawPath(paths.silhouette, body)
    drawPath(paths.outline, outline, style = Stroke(width = 0.6f))
    figure.muscles.forEach { (m, ps) -> ps.forEach { drawPath(it, color(m)) } }
}
