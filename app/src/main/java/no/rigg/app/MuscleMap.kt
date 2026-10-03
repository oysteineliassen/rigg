package no.rigg.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

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

/**
 * Tre tydelige nivåer i stedet for en glidende skala, så det er lett å se forskjell: gul = litt, oransje = middels,
 * rød = mye (i forhold til musklen som ble trent mest). [argb] er fargen i kartet og forklaringen.
 */
enum class MuscleLevel(val argb: Int, val en: String, val no: String) {
    HIGH(0xFFD0312D.toInt(), "A lot", "Mye"),
    MEDIUM(0xFFF07A22.toInt(), "Medium", "Middels"),
    LOW(0xFFE8B746.toInt(), "A little", "Litt"),
    ;

    val label: String get() = tr(en, no)
}

fun muscleLevel(v: Float): MuscleLevel? = when {
    v <= 0f -> null
    v >= 0.75f -> MuscleLevel.HIGH
    v >= 0.4f -> MuscleLevel.MEDIUM
    else -> MuscleLevel.LOW
}

// ---------------------------------------------------------------- TEGNINGEN
//
// Kroppen er et ferdig bilde (res/drawable-nodpi/muscle_body.webp, generert for Rigg) der musklene er grå.
// muscle_labels.webp sier for hver piksel hvilken muskel den hører til og hvor lys den er:
//   R = Muscle.ordinal + 1 (0 = ikke en muskel), G = skygge (0..255 = 0..1,2), B = hvor mye av pikselen som er muskel.
// Trente muskler fylles med fargen for nivået ([MuscleLevel]) med bildets egen skygge. Laget av
// tools/muskelkart/lag_muskelkart.py.

private class MusclePixels(val width: Int, val height: Int, val body: IntArray, val labels: IntArray)

private object MuscleImages {
    @Volatile private var cached: MusclePixels? = null

    fun get(context: Context): MusclePixels = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    private fun load(context: Context): MusclePixels {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val body = BitmapFactory.decodeResource(context.resources, R.drawable.muscle_body, opts)
        val labels = BitmapFactory.decodeResource(context.resources, R.drawable.muscle_labels, opts)
        val w = body.width
        val h = body.height
        val bodyPx = IntArray(w * h).also { body.getPixels(it, 0, w, 0, 0, w, h) }
        val labelPx = IntArray(w * h).also { labels.getPixels(it, 0, w, 0, 0, w, h) }
        return MusclePixels(w, h, bodyPx, labelPx)
    }
}

/** Fargelegger bildet etter belastningen. Rask nok til å gjøres ved visning (et par hundre tusen piksler). */
private fun renderMuscleMap(context: Context, load: Map<Muscle, Float>): Bitmap {
    val px = MuscleImages.get(context)
    val out = px.body.copyOf()
    val muscles = Muscle.values()
    // Fargen per muskel som 0..255-verdier, null = utrent (blir grå som i bildet).
    val fill = muscles.map { m ->
        muscleLevel(load[m] ?: 0f)?.argb?.let { a ->
            floatArrayOf(((a shr 16) and 0xFF).toFloat(), ((a shr 8) and 0xFF).toFloat(), (a and 0xFF).toFloat())
        }
    }
    for (i in out.indices) {
        val l = px.labels[i]
        val id = (l shr 16) and 0xFF
        if (id == 0 || id > muscles.size) continue
        val col = fill[id - 1] ?: continue
        val shade = ((l shr 8) and 0xFF) / 255f * 1.2f * 1.15f
        val k = (l and 0xFF) / 255f
        val b = out[i]
        var c = b and 0xFF000000.toInt()
        for (ch in 0..2) {
            val shift = 16 - 8 * ch
            val target = (col[ch] * shade).coerceIn(0f, 255f)
            val base = ((b shr shift) and 0xFF).toFloat()
            c = c or ((base + (target - base) * k).toInt().coerceIn(0, 255) shl shift)
        }
        out[i] = c
    }
    return Bitmap.createBitmap(out, px.width, px.height, Bitmap.Config.ARGB_8888)
}

/**
 * Muskelkart forfra og bakfra, som i Strava: trente muskler i gult, oransje eller rødt etter hvor mye de ble
 * trent ([MuscleLevel]), resten grå.
 */
@Composable
fun MuscleMap(load: Map<Muscle, Float>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(load) { renderMuscleMap(context, load).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = tr("Muscle map", "Muskelkart"),
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
