package no.rigg.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ferdigrendrede 3D-animasjoner (laget med tools/3d/lag_app.sh) for øvelsene som har fått dem. Resten bruker
 * strekfigurene i Illustration.kt. Rammene ligger i assets/figur3d/<øvelse>/NN.webp: én hel repetisjon,
 * ramme 0 i startstillingen og den midterste i ytterstillingen.
 */
object Figures3d {
    private val IDS = setOf("goblet_squat", "db_shoulder_press", "db_curl", "rdl_db", "db_lunge", "db_lateral_raise")
    const val FRAMES = 96

    /** Bakgrunnen bildene er rendret på (samme som figurflisene i mørkt tema). */
    val background = Color(0xFF262523)

    fun has(exerciseId: String) = exerciseId in IDS
}

/** Én repetisjon i millisekunder (litt roligere enn strekfigurene, siden figuren er mer detaljert). */
private const val REP_MS_3D = 3000

/** Bildene i full størrelse (px). */
private const val SOURCE_PX = 420

// Dekodede rammer, nøkkel "øvelse/ramme/nedskalering". En animasjon i full størrelse er ca. 34 MB.
private val cache = object : LruCache<String, ImageBitmap>(80 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
}

private fun loadFrame(context: Context, id: String, frame: Int, sample: Int): ImageBitmap? {
    val key = "$id/$frame/$sample"
    cache.get(key)?.let { return it }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565 // bildene er uten gjennomsiktighet: halve minnet
    }
    val bmp = runCatching {
        context.assets.open("figur3d/$id/%02d.webp".format(frame)).use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull() ?: return null
    bmp.prepareToDraw() // lastes opp til grafikkortet i forkant, så første runde ikke hakker
    return bmp.asImageBitmap().also { cache.put(key, it) }
}

/**
 * 3D-figuren for [exerciseId], fyller hele [modifier]-flaten. Stillbildet viser ytterstillingen; med [animate]
 * spilles hele repetisjonen i loop. Rammene dekodes i bakgrunnen, nedskalert til størrelsen de vises i.
 */
@Composable
fun Figure3d(exerciseId: String, modifier: Modifier = Modifier, animate: Boolean = false) {
    val context = LocalContext.current.applicationContext
    BoxWithConstraints(modifier.background(Figures3d.background)) {
        val px = if (constraints.maxWidth == Constraints.Infinity) SOURCE_PX else constraints.maxWidth
        var sample = 1
        while (SOURCE_PX / (sample * 2) >= px) sample *= 2
        // Animasjonen starter i startstillingen; stillbildet (i lister) viser ytterstillingen.
        val first = if (animate) 0 else Figures3d.FRAMES / 2
        val frames by produceState<List<ImageBitmap>>(emptyList(), exerciseId, animate, sample) {
            // Ett bilde først, så figuren vises med en gang; animasjonen starter når alle rammene er lest inn.
            value = withContext(Dispatchers.IO) { listOfNotNull(loadFrame(context, exerciseId, first, sample)) }
            if (animate) {
                value = withContext(Dispatchers.IO) {
                    (0 until Figures3d.FRAMES).mapNotNull { loadFrame(context, exerciseId, it, sample) }
                }
            }
        }
        if (frames.isEmpty()) return@BoxWithConstraints
        // Hvilken ramme som vises. Leses bare i tegnefasen, så figuren tegnes på nytt hver skjermoppdatering
        // uten å bygges opp på nytt.
        val pos: State<Float> = if (animate && frames.size > 1) {
            rememberInfiniteTransition(label = "figur3d").animateFloat(
                initialValue = 0f,
                targetValue = frames.size.toFloat(),
                animationSpec = infiniteRepeatable(tween(REP_MS_3D, easing = LinearEasing), RepeatMode.Restart),
                label = "ramme",
            )
        } else {
            remember { mutableFloatStateOf(0f) }
        }
        // Ingen overtoning mellom rammene: det ga dobbeltbilder når figuren beveger seg raskt. Mange rammer gir flyten.
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            drawFrame(frames[pos.value.toInt() % frames.size])
        }
    }
}

/** Tegner en (kvadratisk) ramme sentrert og beskåret til hele flaten. */
private fun DrawScope.drawFrame(img: ImageBitmap) {
    val side = max(size.width, size.height).roundToInt()
    drawImage(
        img,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(((size.width - side) / 2).roundToInt(), ((size.height - side) / 2).roundToInt()),
        dstSize = IntSize(side, side),
    )
}
