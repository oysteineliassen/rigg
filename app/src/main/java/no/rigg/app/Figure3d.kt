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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ferdigrendrede 3D-animasjoner (laget med tools/3d/lag_app.sh) for øvelsene som har fått dem. Resten bruker
 * strekfigurene i Illustration.kt. Rammene ligger i assets/figur3d/<øvelse>/NN.webp: én hel repetisjon,
 * ramme 0 i startstillingen og den midterste i ytterstillingen.
 */
object Figures3d {
    private val IDS = setOf("goblet_squat", "db_shoulder_press", "db_curl", "rdl_db", "db_lunge", "db_lateral_raise")
    const val FRAMES = 48

    /** Bakgrunnen bildene er rendret på (samme som figurflisene i mørkt tema). */
    val background = Color(0xFF262523)

    fun has(exerciseId: String) = exerciseId in IDS
}

/** Én repetisjon i millisekunder (litt roligere enn strekfigurene, siden figuren er mer detaljert). */
private const val REP_MS_3D = 2600

/** Bildene i full størrelse (px). */
private const val SOURCE_PX = 480

// Dekodede rammer, nøkkel "øvelse/ramme/nedskalering". Ca. 64 MB holder et par animasjoner i full størrelse.
private val cache = object : LruCache<String, ImageBitmap>(64 * 1024 * 1024) {
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
    return bmp.asImageBitmap().also { cache.put(key, it) }
}

/**
 * 3D-figuren for [exerciseId], fyller hele [modifier]-flaten. Stillbildet viser ytterstillingen; med [animate]
 * spilles hele repetisjonen i loop, med myk overgang mellom rammene. Rammene dekodes i bakgrunnen, nedskalert til størrelsen de vises i.
 */
@Composable
fun Figure3d(exerciseId: String, modifier: Modifier = Modifier, animate: Boolean = false) {
    val context = LocalContext.current.applicationContext
    BoxWithConstraints(modifier.background(Figures3d.background)) {
        val px = if (constraints.maxWidth == Constraints.Infinity) SOURCE_PX else constraints.maxWidth
        var sample = 1
        while (SOURCE_PX / (sample * 2) >= px) sample *= 2
        val wanted = if (animate) (0 until Figures3d.FRAMES).toList() else listOf(Figures3d.FRAMES / 2)
        val frames by produceState<List<ImageBitmap>>(emptyList(), exerciseId, animate, sample) {
            value = withContext(Dispatchers.IO) { wanted.mapNotNull { loadFrame(context, exerciseId, it, sample) } }
        }
        if (frames.isEmpty()) return@BoxWithConstraints
        // Posisjonen i animasjonen som desimaltall: heltallsdelen er rammen, resten tones inn fra neste ramme.
        val pos = if (animate && frames.size > 1) {
            val transition = rememberInfiniteTransition(label = "figur3d")
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = frames.size.toFloat(),
                animationSpec = infiniteRepeatable(tween(REP_MS_3D, easing = LinearEasing), RepeatMode.Restart),
                label = "ramme",
            )
            t
        } else {
            0f
        }
        val index = pos.toInt() % frames.size
        val fade = pos - pos.toInt()
        Image(frames[index], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // Myk overgang: bildene er ugjennomsiktige, så neste ramme oppå med gjennomsiktighet blander de to.
        if (fade > 0.02f) {
            Image(
                frames[(index + 1) % frames.size], contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alpha = fade,
            )
        }
    }
}
