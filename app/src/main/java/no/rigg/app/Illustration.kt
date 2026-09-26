package no.rigg.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Enkle strekfigur-illustrasjoner tegnet i kode (ingen bilder å laste ned eller lisensiere).
 * Hver pose er definert som linjer og punkter i et 0..1-koordinatsystem (x mot høyre, y nedover),
 * stort sett sett fra siden. Gulvet ligger på y = 0.97.
 *
 * Tips ved endringer: figurene er små (44 dp i listene), så hold dem enkle og la armer og bein
 * stikke tydelig ut fra kroppen, ellers smelter de sammen til én strek.
 */
enum class IllustrationType {
    PUSHUP, BENCH_PRESS, FLOOR_PRESS, OVERHEAD_PRESS, FORWARD_PRESS, LATERAL_RAISE, DIP, PUSHDOWN,
    PIKE, OVERHEAD_PRESS_1ARM, LATERAL_RAISE_BAND, TRICEPS_EXT, THRUSTER, PUSHUP_INCLINE, PUSHUP_DECLINE,
    PUSHUP_BAND, PUSHUP_DIAMOND, PUSHUP_ARCHER, ROW, INVERTED_ROW, SEATED_ROW, PULLUP_HANG, PULLDOWN,
    CURL, CURL_BAND, DEAD_HANG, PULLUP_BAND, CHINUP, SQUAT, WALL_SIT, LUNGE, BULGARIAN, STEP_UP,
    LUNGE_DB, BULGARIAN_DB, GOBLET_SQUAT, FRONT_SQUAT, BB_SQUAT, BAND_SQUAT, PISTOL_BOX, BOX_JUMP,
    WALKING_LUNGE, LATERAL_LUNGE, HIP_HINGE, GLUTE_BRIDGE, CALF_RAISE, SUPERMAN, CALF_RAISE_DB, GLUTE_BRIDGE_1LEG,
    HIP_THRUST, KB_SWING, GOOD_MORNING, PULL_THROUGH, SINGLE_LEG_RDL, PLANK, SIDE_PLANK, MOUNTAIN_CLIMBER,
    BIRD_DOG, DEAD_BUG, HOLLOW_HOLD, LEG_RAISE, HANGING_RAISE, SEATED_TWIST, HANGING_LEG_RAISE, BODY_SAW,
    CARDIO_RUN, JUMP, CARRY, JUMP_ROPE, BURPEE,
}

private data class Pose(
    val head: Offset,
    val headRadius: Float = 0.07f,
    /** Kroppen: armer, bein og overkropp. */
    val lines: List<Pair<Offset, Offset>>,
    /** Utstyr og omgivelser (stang, benk, vegg, strikk), tegnet tynnere og lysere enn kroppen. Står stille. */
    val props: List<Pair<Offset, Offset>> = emptyList(),
    /** Vekter (manual/kettlebell), tegnet i aksentfargen. */
    val dots: List<Offset> = emptyList(),
    val ground: Boolean = true,
    /** Linjer (armer) som ligger foran kroppen: tegnes sist, med en luftkant som skiller dem fra det bak. */
    val front: Set<Int> = emptySet(),
    /**
     * For bevegelser som ikke bare går frem og tilbake (f.eks. bokshopp): en rekke nøkkelstillinger som
     * spilles i rekkefølge fra posen over og rundt igjen. Brukes i stedet for [end].
     */
    val sequence: List<Key> = emptyList(),
    /**
     * Den andre ytterstillingen i bevegelsen, for animasjon. Må ha like mange linjer og prikker,
     * i samme rekkefølge, som posen over. Punkter som deles mellom linjer (ledd) må deles her også,
     * ellers faller figuren fra hverandre underveis. null = figuren står stille.
     */
    val end: Frame? = null,
)

private data class Frame(
    val head: Offset,
    val lines: List<Pair<Offset, Offset>>,
    val dots: List<Offset> = emptyList(),
    /** Bare hvis utstyret beveger seg (f.eks. en strikk som følger hånden). Ellers står rekvisittene stille. */
    val props: List<Pair<Offset, Offset>> = emptyList(),
)

/**
 * Én nøkkelstilling i en sekvens: [ms] er tiden det tar å bevege seg fra forrige stilling til denne.
 * [alpha] < 1 toner figuren ut, f.eks. før gående utfall starter forfra på sin plass igjen.
 */
private data class Key(val ms: Int, val frame: Frame, val alpha: Float = 1f)

/** Én repetisjon (ned og opp igjen) i millisekunder. */
private const val REP_MS = 2200

private fun p(x: Float, y: Float) = Offset(x, y)

private val POSES: Map<IllustrationType, Pose> = mapOf(
    // ------------------------------------------------------------ PRESS
    IllustrationType.PUSHUP to Pose(
        // Armhevinger: strak kropp fra hæl til hode, hender og tær i gulvet. A = oppe, B = brystet ned mot gulvet.
        head = p(0.119f, 0.683f),
        lines = listOf(
            p(0.794f, 0.955f) to p(0.623f, 0.873f),
            p(0.623f, 0.873f) to p(0.446f, 0.803f),
            p(0.446f, 0.803f) to p(0.217f, 0.703f),
            p(0.217f, 0.703f) to p(0.25f, 0.839f),
            p(0.25f, 0.839f) to p(0.22f, 0.955f),
        ),
        end = Frame(
            head = p(0.07f, 0.877f),
            lines = listOf(
                p(0.794f, 0.955f) to p(0.607f, 0.919f),
                p(0.607f, 0.919f) to p(0.418f, 0.904f),
                p(0.418f, 0.904f) to p(0.17f, 0.87f),
                p(0.17f, 0.87f) to p(0.31f, 0.876f),
                p(0.31f, 0.876f) to p(0.22f, 0.955f),
            ),
        ),
    ),
    IllustrationType.BENCH_PRESS to Pose(
        head = p(0.12f, 0.7f),
        lines = listOf(
            p(0.2f, 0.74f) to p(0.56f, 0.74f), // overkropp liggende på benken
            p(0.56f, 0.74f) to p(0.74f, 0.72f), // lår
            p(0.74f, 0.72f) to p(0.76f, 0.95f), // legg ned til gulvet
            p(0.3f, 0.74f) to p(0.31f, 0.55f), // overarm
            p(0.31f, 0.55f) to p(0.32f, 0.36f), // underarm, presset helt opp
        ),
        props = listOf(
            p(0.1f, 0.81f) to p(0.62f, 0.81f), // benk
            p(0.18f, 0.81f) to p(0.18f, 0.97f),
            p(0.54f, 0.81f) to p(0.54f, 0.97f),
        ),
        dots = listOf(p(0.32f, 0.32f)),
        // Nede: vekta senkes mot brystet, albuen går ut mot beina.
        end = Frame(
            head = p(0.12f, 0.7f),
            lines = listOf(
                p(0.2f, 0.74f) to p(0.56f, 0.74f),
                p(0.56f, 0.74f) to p(0.74f, 0.72f),
                p(0.74f, 0.72f) to p(0.76f, 0.95f),
                p(0.3f, 0.74f) to p(0.457f, 0.632f),
                p(0.457f, 0.632f) to p(0.32f, 0.5f),
            ),
            dots = listOf(p(0.32f, 0.46f)),
        ),
    ),
    IllustrationType.FLOOR_PRESS to Pose(
        head = p(0.1f, 0.87f),
        lines = listOf(
            p(0.18f, 0.9f) to p(0.54f, 0.9f), // liggende på gulvet
            p(0.54f, 0.9f) to p(0.7f, 0.66f), // bøyde knær
            p(0.7f, 0.66f) to p(0.84f, 0.92f),
            p(0.28f, 0.9f) to p(0.3f, 0.71f), // overarm
            p(0.3f, 0.71f) to p(0.3f, 0.52f), // underarm, presset opp
        ),
        dots = listOf(p(0.3f, 0.48f)),
        // Nede: albuen i gulvet.
        end = Frame(
            head = p(0.1f, 0.87f),
            lines = listOf(
                p(0.18f, 0.9f) to p(0.54f, 0.9f),
                p(0.54f, 0.9f) to p(0.7f, 0.66f),
                p(0.7f, 0.66f) to p(0.84f, 0.92f),
                p(0.28f, 0.9f) to p(0.461f, 0.84f),
                p(0.461f, 0.84f) to p(0.3f, 0.74f),
            ),
            dots = listOf(p(0.3f, 0.7f)),
        ),
    ),
    IllustrationType.OVERHEAD_PRESS to Pose(
        head = p(0.5f, 0.24f),
        lines = listOf(
            p(0.5f, 0.34f) to p(0.5f, 0.62f), // overkropp (sett forfra)
            p(0.5f, 0.62f) to p(0.42f, 0.96f), // bein
            p(0.5f, 0.62f) to p(0.58f, 0.96f),
            p(0.5f, 0.37f) to p(0.3f, 0.35f), // armer i "målstolpe" på vei opp
            p(0.3f, 0.35f) to p(0.28f, 0.12f),
            p(0.5f, 0.37f) to p(0.7f, 0.35f),
            p(0.7f, 0.35f) to p(0.72f, 0.12f),
        ),
        dots = listOf(p(0.28f, 0.09f), p(0.72f, 0.09f)),
        // Nede: vektene ved skuldrene, albuene ned.
        end = Frame(
            head = p(0.5f, 0.24f),
            lines = listOf(
                p(0.5f, 0.34f) to p(0.5f, 0.62f),
                p(0.5f, 0.62f) to p(0.42f, 0.96f),
                p(0.5f, 0.62f) to p(0.58f, 0.96f),
                p(0.5f, 0.37f) to p(0.372f, 0.525f),
                p(0.372f, 0.525f) to p(0.32f, 0.3f),
                p(0.5f, 0.37f) to p(0.628f, 0.525f),
                p(0.628f, 0.525f) to p(0.68f, 0.3f),
            ),
            dots = listOf(p(0.32f, 0.27f), p(0.68f, 0.27f)),
        ),
    ),
    IllustrationType.FORWARD_PRESS to Pose(
        head = p(0.4f, 0.16f),
        lines = listOf(
            p(0.4f, 0.27f) to p(0.4f, 0.58f), // stående, sett fra siden
            p(0.4f, 0.58f) to p(0.34f, 0.96f),
            p(0.4f, 0.58f) to p(0.48f, 0.96f),
            p(0.4f, 0.33f) to p(0.6f, 0.34f), // armer strakt frem
            p(0.6f, 0.34f) to p(0.8f, 0.35f),
        ),
        props = listOf(
            p(0.8f, 0.35f) to p(0.97f, 0.42f), // strikk mot festepunkt
        ),
        // Hendene inn til brystet igjen, strikken følger med.
        end = Frame(
            head = p(0.4f, 0.16f),
            lines = listOf(
                p(0.4f, 0.27f) to p(0.4f, 0.58f),
                p(0.4f, 0.58f) to p(0.34f, 0.96f),
                p(0.4f, 0.58f) to p(0.48f, 0.96f),
                p(0.4f, 0.33f) to p(0.436f, 0.527f),
                p(0.436f, 0.527f) to p(0.56f, 0.37f),
            ),
            props = listOf(
                p(0.56f, 0.37f) to p(0.97f, 0.42f),
            ),
        ),
    ),
    IllustrationType.LATERAL_RAISE to Pose(
        head = p(0.5f, 0.16f),
        lines = listOf(
            p(0.5f, 0.27f) to p(0.5f, 0.56f),
            p(0.5f, 0.56f) to p(0.44f, 0.95f),
            p(0.5f, 0.56f) to p(0.56f, 0.95f),
            p(0.5f, 0.3f) to p(0.24f, 0.28f), // armer løftet ut til siden
            p(0.5f, 0.3f) to p(0.76f, 0.28f),
        ),
        dots = listOf(p(0.22f, 0.28f), p(0.78f, 0.28f)),
        // Armene ned langs siden.
        end = Frame(
            head = p(0.5f, 0.16f),
            lines = listOf(
                p(0.5f, 0.27f) to p(0.5f, 0.56f),
                p(0.5f, 0.56f) to p(0.44f, 0.95f),
                p(0.5f, 0.56f) to p(0.56f, 0.95f),
                p(0.5f, 0.3f) to p(0.4f, 0.54f),
                p(0.5f, 0.3f) to p(0.6f, 0.54f),
            ),
            dots = listOf(p(0.39f, 0.565f), p(0.61f, 0.565f)),
        ),
    ),
    IllustrationType.DIP to Pose(
        head = p(0.37f, 0.18f),
        lines = listOf(
            p(0.36f, 0.28f) to p(0.38f, 0.58f), // overkropp foran stolen
            p(0.36f, 0.3f) to p(0.33f, 0.4f), // arm bak til setet
            p(0.33f, 0.4f) to p(0.3f, 0.5f),
            p(0.38f, 0.58f) to p(0.66f, 0.64f), // lår
            p(0.66f, 0.64f) to p(0.84f, 0.95f), // legg, hælene i gulvet
        ),
        props = listOf(
            p(0.06f, 0.52f) to p(0.32f, 0.52f), // stol/benk
            p(0.1f, 0.52f) to p(0.1f, 0.97f),
        ),
        // Nede: albuene bøyd bakover, hofta senket.
        end = Frame(
            head = p(0.38f, 0.32f),
            lines = listOf(
                p(0.37f, 0.42f) to p(0.39f, 0.72f),
                p(0.37f, 0.44f) to p(0.274f, 0.399f),
                p(0.274f, 0.399f) to p(0.3f, 0.5f),
                p(0.39f, 0.72f) to p(0.664f, 0.638f),
                p(0.664f, 0.638f) to p(0.84f, 0.95f),
            ),
        ),
    ),
    IllustrationType.PUSHDOWN to Pose(
        head = p(0.44f, 0.16f),
        lines = listOf(
            p(0.44f, 0.27f) to p(0.44f, 0.58f),
            p(0.44f, 0.58f) to p(0.38f, 0.96f),
            p(0.44f, 0.58f) to p(0.52f, 0.96f),
            p(0.44f, 0.32f) to p(0.5f, 0.48f), // albuen inntil siden
            p(0.5f, 0.48f) to p(0.6f, 0.64f), // underarm presser ned
        ),
        props = listOf(
            p(0.6f, 0.64f) to p(0.66f, 0.03f), // strikk fra festepunkt oppe
        ),
        front = setOf(3, 4),
        // Underarmen tilbake opp, albuen står i ro.
        end = Frame(
            head = p(0.44f, 0.16f),
            lines = listOf(
                p(0.44f, 0.27f) to p(0.44f, 0.58f),
                p(0.44f, 0.58f) to p(0.38f, 0.96f),
                p(0.44f, 0.58f) to p(0.52f, 0.96f),
                p(0.44f, 0.32f) to p(0.5f, 0.48f),
                p(0.5f, 0.48f) to p(0.68f, 0.42f),
            ),
            props = listOf(
                p(0.68f, 0.42f) to p(0.66f, 0.03f),
            ),
        ),
    ),
    IllustrationType.PIKE to Pose(
        // Pike-armhevinger: hoftene høyt i en omvendt V, strake bein. Albuene bøyes bakover og hodet går ned mot gulvet rett foran hendene. Hender og føtter står i ro.
        head = p(0.577f, 0.832f),
        lines = listOf(
            p(0.26f, 0.955f) to p(0.301f, 0.769f),
            p(0.301f, 0.769f) to p(0.332f, 0.582f),
            p(0.332f, 0.582f) to p(0.503f, 0.765f),
            p(0.503f, 0.765f) to p(0.594f, 0.872f),
            p(0.594f, 0.872f) to p(0.68f, 0.955f),
        ),
        end = Frame(
            head = p(0.616f, 0.843f),
            lines = listOf(
                p(0.26f, 0.955f) to p(0.358f, 0.792f),
                p(0.358f, 0.792f) to p(0.362f, 0.602f),
                p(0.362f, 0.602f) to p(0.539f, 0.779f),
                p(0.539f, 0.779f) to p(0.566f, 0.916f),
                p(0.566f, 0.916f) to p(0.68f, 0.955f),
            ),
        ),
    ),
    IllustrationType.OVERHEAD_PRESS_1ARM to Pose(
        // Enarms press med én kettlebell.
        head = p(0.5f, 0.24f),
        lines = listOf(
            p(0.5f, 0.34f) to p(0.5f, 0.62f), // overkropp (sett forfra)
            p(0.5f, 0.62f) to p(0.42f, 0.96f), // bein
            p(0.5f, 0.62f) to p(0.58f, 0.96f),
            p(0.5f, 0.37f) to p(0.3f, 0.35f), // armer i "målstolpe" på vei opp
            p(0.3f, 0.35f) to p(0.28f, 0.12f),
            p(0.5f, 0.37f) to p(0.6f, 0.5f), // den andre armen henger
            p(0.6f, 0.5f) to p(0.63f, 0.64f),
        ),
        dots = listOf(p(0.28f, 0.09f)),
        // Nede: vektene ved skuldrene, albuene ned.
        end = Frame(
            head = p(0.5f, 0.24f),
            lines = listOf(
                p(0.5f, 0.34f) to p(0.5f, 0.62f),
                p(0.5f, 0.62f) to p(0.42f, 0.96f),
                p(0.5f, 0.62f) to p(0.58f, 0.96f),
                p(0.5f, 0.37f) to p(0.372f, 0.525f),
                p(0.372f, 0.525f) to p(0.32f, 0.3f),
                p(0.5f, 0.37f) to p(0.6f, 0.5f),
                p(0.6f, 0.5f) to p(0.63f, 0.64f),
            ),
            dots = listOf(p(0.32f, 0.27f)),
        ),
    ),
    IllustrationType.LATERAL_RAISE_BAND to Pose(
        // Med strikk i stedet for manualer.
        head = p(0.5f, 0.16f),
        lines = listOf(
            p(0.5f, 0.27f) to p(0.5f, 0.56f),
            p(0.5f, 0.56f) to p(0.44f, 0.95f),
            p(0.5f, 0.56f) to p(0.56f, 0.95f),
            p(0.5f, 0.3f) to p(0.24f, 0.28f), // armer løftet ut til siden
            p(0.5f, 0.3f) to p(0.76f, 0.28f),
        ),
        props = listOf(
            p(0.24f, 0.28f) to p(0.44f, 0.95f), // strikk fra hendene ned under føttene
            p(0.76f, 0.28f) to p(0.56f, 0.95f),
        ),
        // Armene ned langs siden.
        end = Frame(
            head = p(0.5f, 0.16f),
            lines = listOf(
                p(0.5f, 0.27f) to p(0.5f, 0.56f),
                p(0.5f, 0.56f) to p(0.44f, 0.95f),
                p(0.5f, 0.56f) to p(0.56f, 0.95f),
                p(0.5f, 0.3f) to p(0.4f, 0.54f),
                p(0.5f, 0.3f) to p(0.6f, 0.54f),
            ),
            props = listOf(
                p(0.4f, 0.54f) to p(0.44f, 0.95f),
                p(0.6f, 0.54f) to p(0.56f, 0.95f),
            ),
        ),
    ),
    IllustrationType.TRICEPS_EXT to Pose(
        // Triceps-fransk sett fra siden: manualen holdt over hodet med albuene pekende opp. Underarmen senkes bak hodet og strekkes opp igjen. Armen ligger bak hodet i tegningen, så hodet skjuler underarmen nederst.
        head = p(0.498f, 0.302f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.485f, 0.784f),
            p(0.485f, 0.784f) to p(0.47f, 0.613f),
            p(0.47f, 0.613f) to p(0.47f, 0.388f),
            p(0.47f, 0.388f) to p(0.442f, 0.242f),
            p(0.442f, 0.242f) to p(0.455f, 0.117f),
            p(0.47f, 0.613f) to p(0.455f, 0.784f),
            p(0.455f, 0.784f) to p(0.44f, 0.955f),
        ),
        dots = listOf(p(0.455f, 0.117f)),
        end = Frame(
            head = p(0.498f, 0.302f),
            lines = listOf(
                p(0.5f, 0.955f) to p(0.485f, 0.784f),
                p(0.485f, 0.784f) to p(0.47f, 0.613f),
                p(0.47f, 0.613f) to p(0.47f, 0.388f),
                p(0.47f, 0.388f) to p(0.442f, 0.242f),
                p(0.442f, 0.242f) to p(0.407f, 0.363f),
                p(0.47f, 0.613f) to p(0.455f, 0.784f),
                p(0.455f, 0.784f) to p(0.44f, 0.955f),
            ),
            dots = listOf(p(0.407f, 0.363f)),
        ),
    ),
    IllustrationType.THRUSTER to Pose(
        // Thruster: manualene på skuldrene i knebøyen, opp og rett videre til press over hodet, ned igjen.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.642f, 0.703f),
            p(0.642f, 0.703f) to p(0.563f, 0.612f),
        ),
        dots = listOf(p(0.609f, 0.651f)),
        front = setOf(3, 4),
        sequence = listOf(
            // opp fra knebøyen
            Key(450, Frame(
                head = p(0.499f, 0.226f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.507f, 0.765f),
                    p(0.507f, 0.765f) to p(0.489f, 0.576f),
                    p(0.489f, 0.576f) to p(0.496f, 0.326f),
                    p(0.496f, 0.326f) to p(0.633f, 0.354f),
                    p(0.633f, 0.354f) to p(0.518f, 0.32f),
                ),
                dots = listOf(p(0.564f, 0.359f)),
            )),
            // presser over hodet
            Key(300, Frame(
                head = p(0.5f, 0.225f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.5f, 0.765f),
                    p(0.5f, 0.765f) to p(0.5f, 0.575f),
                    p(0.5f, 0.575f) to p(0.5f, 0.325f),
                    p(0.5f, 0.325f) to p(0.534f, 0.189f),
                    p(0.534f, 0.189f) to p(0.555f, 0.071f),
                ),
                dots = listOf(p(0.555f, 0.071f)),
            )),
            // strake armer
            Key(200, Frame(
                head = p(0.5f, 0.225f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.5f, 0.765f),
                    p(0.5f, 0.765f) to p(0.5f, 0.575f),
                    p(0.5f, 0.575f) to p(0.5f, 0.325f),
                    p(0.5f, 0.325f) to p(0.534f, 0.189f),
                    p(0.534f, 0.189f) to p(0.555f, 0.071f),
                ),
                dots = listOf(p(0.555f, 0.071f)),
            )),
            // ned til skuldrene
            Key(350, Frame(
                head = p(0.499f, 0.226f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.507f, 0.765f),
                    p(0.507f, 0.765f) to p(0.489f, 0.576f),
                    p(0.489f, 0.576f) to p(0.496f, 0.326f),
                    p(0.496f, 0.326f) to p(0.633f, 0.354f),
                    p(0.633f, 0.354f) to p(0.518f, 0.32f),
                ),
                dots = listOf(p(0.564f, 0.359f)),
            )),
            // ned i knebøy
            Key(450, Frame(
                head = p(0.597f, 0.523f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.589f, 0.787f),
                    p(0.589f, 0.787f) to p(0.401f, 0.813f),
                    p(0.401f, 0.813f) to p(0.541f, 0.606f),
                    p(0.541f, 0.606f) to p(0.642f, 0.703f),
                    p(0.642f, 0.703f) to p(0.563f, 0.612f),
                ),
                dots = listOf(p(0.609f, 0.651f)),
            )),
            // nederst
            Key(200, Frame(
                head = p(0.597f, 0.523f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.589f, 0.787f),
                    p(0.589f, 0.787f) to p(0.401f, 0.813f),
                    p(0.401f, 0.813f) to p(0.541f, 0.606f),
                    p(0.541f, 0.606f) to p(0.642f, 0.703f),
                    p(0.642f, 0.703f) to p(0.563f, 0.612f),
                ),
                dots = listOf(p(0.609f, 0.651f)),
            )),
        ),
    ),
    IllustrationType.PUSHUP_INCLINE to Pose(
        // Armhevinger med hendene på en benk: lettere variant, kroppen på skrå oppover.
        head = p(0.273f, 0.392f),
        lines = listOf(
            p(0.735f, 0.955f) to p(0.623f, 0.802f),
            p(0.623f, 0.802f) to p(0.502f, 0.655f),
            p(0.502f, 0.655f) to p(0.349f, 0.457f),
            p(0.349f, 0.457f) to p(0.386f, 0.592f),
            p(0.386f, 0.592f) to p(0.36f, 0.709f),
        ),
        props = listOf(
            p(0.22f, 0.77f) to p(0.46f, 0.77f), // benk
            p(0.24f, 0.77f) to p(0.24f, 0.97f),
            p(0.44f, 0.77f) to p(0.44f, 0.97f),
        ),
        end = Frame(
            head = p(0.098f, 0.602f),
            lines = listOf(
                p(0.735f, 0.955f) to p(0.572f, 0.858f),
                p(0.572f, 0.858f) to p(0.408f, 0.761f),
                p(0.408f, 0.761f) to p(0.193f, 0.634f),
                p(0.193f, 0.634f) to p(0.327f, 0.594f),
                p(0.327f, 0.594f) to p(0.36f, 0.709f),
            ),
        ),
    ),
    IllustrationType.PUSHUP_DECLINE to Pose(
        // Armhevinger med føttene på en benk: tyngre variant, hodet lavere enn føttene.
        head = p(0.112f, 0.723f),
        lines = listOf(
            p(0.84f, 0.709f) to p(0.65f, 0.707f),
            p(0.65f, 0.707f) to p(0.46f, 0.705f),
            p(0.46f, 0.705f) to p(0.21f, 0.703f),
            p(0.21f, 0.703f) to p(0.236f, 0.841f),
            p(0.236f, 0.841f) to p(0.2f, 0.955f),
        ),
        props = listOf(
            p(0.72f, 0.77f) to p(0.97f, 0.77f), // benk
            p(0.74f, 0.77f) to p(0.74f, 0.97f),
            p(0.95f, 0.77f) to p(0.95f, 0.97f),
        ),
        end = Frame(
            head = p(0.142f, 0.915f),
            lines = listOf(
                p(0.84f, 0.709f) to p(0.654f, 0.749f),
                p(0.654f, 0.749f) to p(0.473f, 0.806f),
                p(0.473f, 0.806f) to p(0.231f, 0.87f),
                p(0.231f, 0.87f) to p(0.317f, 0.98f),
                p(0.317f, 0.98f) to p(0.2f, 0.955f),
            ),
        ),
    ),
    IllustrationType.PUSHUP_BAND to Pose(
        // Armhevinger med strikk over ryggen: strikken holdes i hendene og går over skulderbladene.
        head = p(0.119f, 0.683f),
        lines = listOf(
            p(0.794f, 0.955f) to p(0.623f, 0.873f),
            p(0.623f, 0.873f) to p(0.446f, 0.803f),
            p(0.446f, 0.803f) to p(0.217f, 0.703f),
            p(0.217f, 0.703f) to p(0.25f, 0.839f),
            p(0.25f, 0.839f) to p(0.22f, 0.955f),
        ),
        props = listOf(
            p(0.22f, 0.955f) to p(0.242f, 0.646f), // strikk fra hånden opp over skulderen
            p(0.242f, 0.646f) to p(0.37f, 0.702f), // og over ryggen
        ),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.07f, 0.877f),
            lines = listOf(
                p(0.794f, 0.955f) to p(0.607f, 0.919f),
                p(0.607f, 0.919f) to p(0.418f, 0.904f),
                p(0.418f, 0.904f) to p(0.17f, 0.87f),
                p(0.17f, 0.87f) to p(0.31f, 0.876f),
                p(0.31f, 0.876f) to p(0.22f, 0.955f),
            ),
            props = listOf(
                p(0.22f, 0.955f) to p(0.178f, 0.809f), // strikk fra hånden opp over skulderen
                p(0.178f, 0.809f) to p(0.317f, 0.828f), // og over ryggen
            ),
        ),
    ),
    IllustrationType.PUSHUP_DIAMOND to Pose(
        // Diamant-armhevinger sett forfra: hendene samlet under brystet (tommel og pekefinger danner en diamant). Albuene går ut og bak når brystet senkes.
        head = p(0.5f, 0.701f),
        lines = listOf(
            p(0.465f, 0.955f) to p(0.435f, 0.824f),
            p(0.435f, 0.824f) to p(0.4f, 0.671f),
            p(0.4f, 0.671f) to p(0.5f, 0.671f),
            p(0.5f, 0.671f) to p(0.6f, 0.671f),
            p(0.6f, 0.671f) to p(0.565f, 0.824f),
            p(0.565f, 0.824f) to p(0.535f, 0.955f),
        ),
        end = Frame(
            head = p(0.5f, 0.855f),
            lines = listOf(
                p(0.465f, 0.955f) to p(0.335f, 0.988f),
                p(0.335f, 0.988f) to p(0.4f, 0.845f),
                p(0.4f, 0.845f) to p(0.5f, 0.845f),
                p(0.5f, 0.845f) to p(0.6f, 0.845f),
                p(0.6f, 0.845f) to p(0.665f, 0.988f),
                p(0.665f, 0.988f) to p(0.535f, 0.955f),
            ),
        ),
    ),
    IllustrationType.PUSHUP_ARCHER to Pose(
        // Bueskytter-armhevinger sett forfra: ned mot den ene hånden med den andre armen strak, så til den andre siden.
        head = p(0.5f, 0.652f),
        lines = listOf(
            p(0.24f, 0.955f) to p(0.309f, 0.84f),
            p(0.309f, 0.84f) to p(0.4f, 0.712f),
            p(0.4f, 0.712f) to p(0.5f, 0.712f),
            p(0.5f, 0.712f) to p(0.6f, 0.712f),
            p(0.6f, 0.712f) to p(0.691f, 0.84f),
            p(0.691f, 0.84f) to p(0.76f, 0.955f),
        ),
        sequence = listOf(
            // ned mot venstre hånd
            Key(700, Frame(
                head = p(0.34f, 0.8f),
                lines = listOf(
                    p(0.24f, 0.955f) to p(0.114f, 0.908f),
                    p(0.114f, 0.908f) to p(0.26f, 0.85f),
                    p(0.26f, 0.85f) to p(0.36f, 0.85f),
                    p(0.36f, 0.85f) to p(0.46f, 0.85f),
                    p(0.46f, 0.85f) to p(0.622f, 0.907f),
                    p(0.622f, 0.907f) to p(0.76f, 0.955f),
                ),
            )),
            // opp
            Key(600, Frame(
                head = p(0.5f, 0.652f),
                lines = listOf(
                    p(0.24f, 0.955f) to p(0.309f, 0.84f),
                    p(0.309f, 0.84f) to p(0.4f, 0.712f),
                    p(0.4f, 0.712f) to p(0.5f, 0.712f),
                    p(0.5f, 0.712f) to p(0.6f, 0.712f),
                    p(0.6f, 0.712f) to p(0.691f, 0.84f),
                    p(0.691f, 0.84f) to p(0.76f, 0.955f),
                ),
            )),
            // kort pause
            Key(150, Frame(
                head = p(0.5f, 0.652f),
                lines = listOf(
                    p(0.24f, 0.955f) to p(0.309f, 0.84f),
                    p(0.309f, 0.84f) to p(0.4f, 0.712f),
                    p(0.4f, 0.712f) to p(0.5f, 0.712f),
                    p(0.5f, 0.712f) to p(0.6f, 0.712f),
                    p(0.6f, 0.712f) to p(0.691f, 0.84f),
                    p(0.691f, 0.84f) to p(0.76f, 0.955f),
                ),
            )),
            // ned mot høyre hånd
            Key(700, Frame(
                head = p(0.66f, 0.8f),
                lines = listOf(
                    p(0.24f, 0.955f) to p(0.378f, 0.907f),
                    p(0.378f, 0.907f) to p(0.54f, 0.85f),
                    p(0.54f, 0.85f) to p(0.64f, 0.85f),
                    p(0.64f, 0.85f) to p(0.74f, 0.85f),
                    p(0.74f, 0.85f) to p(0.886f, 0.908f),
                    p(0.886f, 0.908f) to p(0.76f, 0.955f),
                ),
            )),
            // opp
            Key(600, Frame(
                head = p(0.5f, 0.652f),
                lines = listOf(
                    p(0.24f, 0.955f) to p(0.309f, 0.84f),
                    p(0.309f, 0.84f) to p(0.4f, 0.712f),
                    p(0.4f, 0.712f) to p(0.5f, 0.712f),
                    p(0.5f, 0.712f) to p(0.6f, 0.712f),
                    p(0.6f, 0.712f) to p(0.691f, 0.84f),
                    p(0.691f, 0.84f) to p(0.76f, 0.955f),
                ),
            )),
            // kort pause
            Key(150, Frame(
                head = p(0.5f, 0.652f),
                lines = listOf(
                    p(0.24f, 0.955f) to p(0.309f, 0.84f),
                    p(0.309f, 0.84f) to p(0.4f, 0.712f),
                    p(0.4f, 0.712f) to p(0.5f, 0.712f),
                    p(0.5f, 0.712f) to p(0.6f, 0.712f),
                    p(0.6f, 0.712f) to p(0.691f, 0.84f),
                    p(0.691f, 0.84f) to p(0.76f, 0.955f),
                ),
            )),
        ),
    ),

    // ------------------------------------------------------------ TREKK
    IllustrationType.ROW to Pose(
        head = p(0.86f, 0.34f),
        lines = listOf(
            p(0.78f, 0.41f) to p(0.36f, 0.5f), // flat rygg, fremoverbøyd
            p(0.36f, 0.5f) to p(0.44f, 0.74f), // lett bøyde knær
            p(0.44f, 0.74f) to p(0.38f, 0.97f),
            p(0.76f, 0.42f) to p(0.75f, 0.57f), // armen henger (startposisjon)
            p(0.75f, 0.57f) to p(0.74f, 0.72f),
        ),
        dots = listOf(p(0.74f, 0.75f)),
        front = setOf(3, 4),
        // Trukket opp: albuen over ryggen.
        end = Frame(
            head = p(0.86f, 0.34f),
            lines = listOf(
                p(0.78f, 0.41f) to p(0.36f, 0.5f),
                p(0.36f, 0.5f) to p(0.44f, 0.74f),
                p(0.44f, 0.74f) to p(0.38f, 0.97f),
                p(0.76f, 0.42f) to p(0.64f, 0.33f),
                p(0.64f, 0.33f) to p(0.66f, 0.48f),
            ),
            dots = listOf(p(0.66f, 0.51f)),
        ),
    ),
    IllustrationType.INVERTED_ROW to Pose(
        head = p(0.26f, 0.54f),
        lines = listOf(
            p(0.36f, 0.6f) to p(0.64f, 0.75f), // stiv kropp på skrå under bordet
            p(0.64f, 0.75f) to p(0.93f, 0.93f),
            p(0.36f, 0.6f) to p(0.39f, 0.46f), // armer opp til bordkanten
            p(0.39f, 0.46f) to p(0.42f, 0.32f),
        ),
        props = listOf(
            p(0.03f, 0.3f) to p(0.5f, 0.3f), // bordplate
            p(0.08f, 0.3f) to p(0.08f, 0.97f),
        ),
        // Trukket opp: brystet mot bordet, kroppen vipper rundt hælene.
        end = Frame(
            head = p(0.325f, 0.447f),
            lines = listOf(
                p(0.425f, 0.507f) to p(0.669f, 0.711f),
                p(0.669f, 0.711f) to p(0.93f, 0.93f),
                p(0.425f, 0.507f) to p(0.531f, 0.41f),
                p(0.531f, 0.41f) to p(0.42f, 0.32f),
            ),
        ),
    ),
    IllustrationType.SEATED_ROW to Pose(
        head = p(0.3f, 0.34f),
        lines = listOf(
            p(0.3f, 0.44f) to p(0.32f, 0.9f), // sittende, rett rygg
            p(0.32f, 0.9f) to p(0.9f, 0.9f), // strake bein på gulvet
            p(0.3f, 0.48f) to p(0.18f, 0.62f), // albuen trukket bak
            p(0.18f, 0.62f) to p(0.38f, 0.64f),
        ),
        props = listOf(
            p(0.38f, 0.64f) to p(0.9f, 0.84f), // strikk rundt føttene
        ),
        front = setOf(2, 3),
        // Armene strakt frem igjen.
        end = Frame(
            head = p(0.3f, 0.34f),
            lines = listOf(
                p(0.3f, 0.44f) to p(0.32f, 0.9f),
                p(0.32f, 0.9f) to p(0.9f, 0.9f),
                p(0.3f, 0.48f) to p(0.47f, 0.56f),
                p(0.47f, 0.56f) to p(0.66f, 0.62f),
            ),
            props = listOf(
                p(0.66f, 0.62f) to p(0.9f, 0.84f),
            ),
        ),
    ),
    IllustrationType.PULLUP_HANG to Pose(
        // Pull-up sett forfra: kroppen midt mellom armene. Fra strake armer til haken over stanga, albuene ut og ned.
        head = p(0.5f, 0.25f),
        lines = listOf(
            p(0.445f, 0.907f) to p(0.45f, 0.736f),
            p(0.45f, 0.736f) to p(0.455f, 0.565f),
            p(0.455f, 0.565f) to p(0.5f, 0.565f),
            p(0.5f, 0.565f) to p(0.5f, 0.34f),
            p(0.5f, 0.34f) to p(0.425f, 0.34f),
            p(0.425f, 0.34f) to p(0.355f, 0.235f),
            p(0.355f, 0.235f) to p(0.33f, 0.13f),
            p(0.5f, 0.565f) to p(0.545f, 0.565f),
            p(0.545f, 0.565f) to p(0.55f, 0.736f),
            p(0.55f, 0.736f) to p(0.555f, 0.907f),
            p(0.5f, 0.34f) to p(0.575f, 0.34f),
            p(0.575f, 0.34f) to p(0.645f, 0.235f),
            p(0.645f, 0.235f) to p(0.67f, 0.13f),
        ),
        props = listOf(
            p(0.14f, 0.13f) to p(0.86f, 0.13f), // tverrligger
        ),
        ground = false,
        end = Frame(
            head = p(0.5f, 0.08f),
            lines = listOf(
                p(0.445f, 0.737f) to p(0.45f, 0.566f),
                p(0.45f, 0.566f) to p(0.455f, 0.395f),
                p(0.455f, 0.395f) to p(0.5f, 0.395f),
                p(0.5f, 0.395f) to p(0.5f, 0.17f),
                p(0.5f, 0.17f) to p(0.425f, 0.17f),
                p(0.425f, 0.17f) to p(0.319f, 0.237f),
                p(0.319f, 0.237f) to p(0.33f, 0.13f),
                p(0.5f, 0.395f) to p(0.545f, 0.395f),
                p(0.545f, 0.395f) to p(0.55f, 0.566f),
                p(0.55f, 0.566f) to p(0.555f, 0.737f),
                p(0.5f, 0.17f) to p(0.575f, 0.17f),
                p(0.575f, 0.17f) to p(0.681f, 0.237f),
                p(0.681f, 0.237f) to p(0.67f, 0.13f),
            ),
        ),
    ),
    IllustrationType.PULLDOWN to Pose(
        head = p(0.48f, 0.3f),
        lines = listOf(
            p(0.48f, 0.4f) to p(0.48f, 0.68f), // knestående
            p(0.48f, 0.68f) to p(0.5f, 0.94f), // lår ned til kneet
            p(0.5f, 0.94f) to p(0.22f, 0.94f), // legg langs gulvet
            p(0.48f, 0.43f) to p(0.66f, 0.32f), // armer oppe
            p(0.66f, 0.32f) to p(0.64f, 0.14f),
        ),
        props = listOf(
            p(0.64f, 0.14f) to p(0.62f, 0.02f), // strikk til festepunkt oppe
        ),
        front = setOf(3, 4),
        // Trukket ned: albuen ned langs siden.
        end = Frame(
            head = p(0.48f, 0.3f),
            lines = listOf(
                p(0.48f, 0.4f) to p(0.48f, 0.68f),
                p(0.48f, 0.68f) to p(0.5f, 0.94f),
                p(0.5f, 0.94f) to p(0.22f, 0.94f),
                p(0.48f, 0.43f) to p(0.58f, 0.6f),
                p(0.58f, 0.6f) to p(0.64f, 0.43f),
            ),
            props = listOf(
                p(0.64f, 0.43f) to p(0.62f, 0.02f),
            ),
        ),
    ),
    IllustrationType.CURL to Pose(
        // Bicepscurl: albuen inntil siden, underarmen løftes. A = oppe, B = armen strakt ned.
        head = p(0.46f, 0.225f),
        lines = listOf(
            p(0.495f, 0.955f) to p(0.478f, 0.765f),
            p(0.478f, 0.765f) to p(0.46f, 0.575f),
            p(0.46f, 0.575f) to p(0.46f, 0.325f),
            p(0.46f, 0.325f) to p(0.47f, 0.465f),
            p(0.47f, 0.465f) to p(0.544f, 0.37f),
            p(0.46f, 0.575f) to p(0.443f, 0.765f),
            p(0.443f, 0.765f) to p(0.425f, 0.955f),
        ),
        dots = listOf(p(0.544f, 0.37f)),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.46f, 0.225f),
            lines = listOf(
                p(0.495f, 0.955f) to p(0.478f, 0.765f),
                p(0.478f, 0.765f) to p(0.46f, 0.575f),
                p(0.46f, 0.575f) to p(0.46f, 0.325f),
                p(0.46f, 0.325f) to p(0.47f, 0.465f),
                p(0.47f, 0.465f) to p(0.483f, 0.584f),
                p(0.46f, 0.575f) to p(0.443f, 0.765f),
                p(0.443f, 0.765f) to p(0.425f, 0.955f),
            ),
            dots = listOf(p(0.483f, 0.584f)),
        ),
    ),
    IllustrationType.CURL_BAND to Pose(
        // Bicepscurl med strikk: strikken går fra hånden ned under foten.
        head = p(0.46f, 0.225f),
        lines = listOf(
            p(0.495f, 0.955f) to p(0.478f, 0.765f),
            p(0.478f, 0.765f) to p(0.46f, 0.575f),
            p(0.46f, 0.575f) to p(0.46f, 0.325f),
            p(0.46f, 0.325f) to p(0.47f, 0.465f),
            p(0.47f, 0.465f) to p(0.544f, 0.37f),
            p(0.46f, 0.575f) to p(0.443f, 0.765f),
            p(0.443f, 0.765f) to p(0.425f, 0.955f),
        ),
        props = listOf(
            p(0.544f, 0.37f) to p(0.495f, 0.955f), // strikk fra hånden ned under foten
        ),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.46f, 0.225f),
            lines = listOf(
                p(0.495f, 0.955f) to p(0.478f, 0.765f),
                p(0.478f, 0.765f) to p(0.46f, 0.575f),
                p(0.46f, 0.575f) to p(0.46f, 0.325f),
                p(0.46f, 0.325f) to p(0.47f, 0.465f),
                p(0.47f, 0.465f) to p(0.483f, 0.584f),
                p(0.46f, 0.575f) to p(0.443f, 0.765f),
                p(0.443f, 0.765f) to p(0.425f, 0.955f),
            ),
            props = listOf(
                p(0.483f, 0.584f) to p(0.495f, 0.955f), // strikk fra hånden ned under foten
            ),
        ),
    ),
    IllustrationType.DEAD_HANG to Pose(
        // Hang i tverrligger: strake armer, står stille.
        head = p(0.5f, 0.25f),
        lines = listOf(
            p(0.445f, 0.907f) to p(0.45f, 0.736f),
            p(0.45f, 0.736f) to p(0.455f, 0.565f),
            p(0.455f, 0.565f) to p(0.5f, 0.565f),
            p(0.5f, 0.565f) to p(0.5f, 0.34f),
            p(0.5f, 0.34f) to p(0.425f, 0.34f),
            p(0.425f, 0.34f) to p(0.355f, 0.235f),
            p(0.355f, 0.235f) to p(0.33f, 0.13f),
            p(0.5f, 0.565f) to p(0.545f, 0.565f),
            p(0.545f, 0.565f) to p(0.55f, 0.736f),
            p(0.55f, 0.736f) to p(0.555f, 0.907f),
            p(0.5f, 0.34f) to p(0.575f, 0.34f),
            p(0.575f, 0.34f) to p(0.645f, 0.235f),
            p(0.645f, 0.235f) to p(0.67f, 0.13f),
        ),
        props = listOf(
            p(0.14f, 0.13f) to p(0.86f, 0.13f), // tverrligger
        ),
        ground = false,
    ),
    IllustrationType.PULLUP_BAND to Pose(
        // Assistert pull-up: strikken henger fra stanga og ned under føttene.
        head = p(0.5f, 0.25f),
        lines = listOf(
            p(0.445f, 0.907f) to p(0.45f, 0.736f),
            p(0.45f, 0.736f) to p(0.455f, 0.565f),
            p(0.455f, 0.565f) to p(0.5f, 0.565f),
            p(0.5f, 0.565f) to p(0.5f, 0.34f),
            p(0.5f, 0.34f) to p(0.425f, 0.34f),
            p(0.425f, 0.34f) to p(0.355f, 0.235f),
            p(0.355f, 0.235f) to p(0.33f, 0.13f),
            p(0.5f, 0.565f) to p(0.545f, 0.565f),
            p(0.545f, 0.565f) to p(0.55f, 0.736f),
            p(0.55f, 0.736f) to p(0.555f, 0.907f),
            p(0.5f, 0.34f) to p(0.575f, 0.34f),
            p(0.575f, 0.34f) to p(0.645f, 0.235f),
            p(0.645f, 0.235f) to p(0.67f, 0.13f),
        ),
        props = listOf(
            p(0.14f, 0.13f) to p(0.86f, 0.13f), // tverrligger
            p(0.47f, 0.13f) to p(0.445f, 0.907f), // strikk fra stanga ned under føttene
            p(0.53f, 0.13f) to p(0.555f, 0.907f),
        ),
        ground = false,
        end = Frame(
            head = p(0.5f, 0.08f),
            lines = listOf(
                p(0.445f, 0.737f) to p(0.45f, 0.566f),
                p(0.45f, 0.566f) to p(0.455f, 0.395f),
                p(0.455f, 0.395f) to p(0.5f, 0.395f),
                p(0.5f, 0.395f) to p(0.5f, 0.17f),
                p(0.5f, 0.17f) to p(0.425f, 0.17f),
                p(0.425f, 0.17f) to p(0.319f, 0.237f),
                p(0.319f, 0.237f) to p(0.33f, 0.13f),
                p(0.5f, 0.395f) to p(0.545f, 0.395f),
                p(0.545f, 0.395f) to p(0.55f, 0.566f),
                p(0.55f, 0.566f) to p(0.555f, 0.737f),
                p(0.5f, 0.17f) to p(0.575f, 0.17f),
                p(0.575f, 0.17f) to p(0.681f, 0.237f),
                p(0.681f, 0.237f) to p(0.67f, 0.13f),
            ),
            props = listOf(
                p(0.14f, 0.13f) to p(0.86f, 0.13f), // tverrligger
                p(0.47f, 0.13f) to p(0.445f, 0.737f), // strikk fra stanga ned under føttene
                p(0.53f, 0.13f) to p(0.555f, 0.737f),
            ),
        ),
    ),
    IllustrationType.CHINUP to Pose(
        // Chin-up sett forfra: smalere grep med håndflatene mot deg, albuene ned foran kroppen.
        head = p(0.5f, 0.25f),
        lines = listOf(
            p(0.445f, 0.907f) to p(0.45f, 0.736f),
            p(0.45f, 0.736f) to p(0.455f, 0.565f),
            p(0.455f, 0.565f) to p(0.5f, 0.565f),
            p(0.5f, 0.565f) to p(0.5f, 0.34f),
            p(0.5f, 0.34f) to p(0.425f, 0.34f),
            p(0.425f, 0.34f) to p(0.362f, 0.231f),
            p(0.362f, 0.231f) to p(0.4f, 0.13f),
            p(0.5f, 0.565f) to p(0.545f, 0.565f),
            p(0.545f, 0.565f) to p(0.55f, 0.736f),
            p(0.55f, 0.736f) to p(0.555f, 0.907f),
            p(0.5f, 0.34f) to p(0.575f, 0.34f),
            p(0.575f, 0.34f) to p(0.638f, 0.231f),
            p(0.638f, 0.231f) to p(0.6f, 0.13f),
        ),
        props = listOf(
            p(0.14f, 0.13f) to p(0.86f, 0.13f), // tverrligger
        ),
        ground = false,
        end = Frame(
            head = p(0.5f, 0.08f),
            lines = listOf(
                p(0.445f, 0.737f) to p(0.45f, 0.566f),
                p(0.45f, 0.566f) to p(0.455f, 0.395f),
                p(0.455f, 0.395f) to p(0.5f, 0.395f),
                p(0.5f, 0.395f) to p(0.5f, 0.17f),
                p(0.5f, 0.17f) to p(0.425f, 0.17f),
                p(0.425f, 0.17f) to p(0.44f, 0.235f),
                p(0.44f, 0.235f) to p(0.4f, 0.13f),
                p(0.5f, 0.395f) to p(0.545f, 0.395f),
                p(0.545f, 0.395f) to p(0.55f, 0.566f),
                p(0.55f, 0.566f) to p(0.555f, 0.737f),
                p(0.5f, 0.17f) to p(0.575f, 0.17f),
                p(0.575f, 0.17f) to p(0.56f, 0.235f),
                p(0.56f, 0.235f) to p(0.6f, 0.13f),
            ),
        ),
    ),

    // ------------------------------------------------------------ BEIN
    IllustrationType.SQUAT to Pose(
        // Knebøy med kroppsvekt: A = nederst med armene frem for balanse, B = stående med armene langs siden.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.681f, 0.606f),
            p(0.681f, 0.606f) to p(0.801f, 0.606f),
        ),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.5f, 0.225f),
            lines = listOf(
                p(0.5f, 0.955f) to p(0.5f, 0.765f),
                p(0.5f, 0.765f) to p(0.5f, 0.575f),
                p(0.5f, 0.575f) to p(0.5f, 0.325f),
                p(0.5f, 0.325f) to p(0.495f, 0.465f),
                p(0.495f, 0.465f) to p(0.491f, 0.585f),
            ),
        ),
    ),
    IllustrationType.WALL_SIT to Pose(
        // Holdøvelse: står stille.
        head = p(0.31f, 0.27f),
        lines = listOf(
            p(0.3f, 0.37f) to p(0.3f, 0.62f), // ryggen mot veggen
            p(0.3f, 0.62f) to p(0.64f, 0.62f), // vannrette lår
            p(0.64f, 0.62f) to p(0.64f, 0.96f), // loddrett legg
            p(0.3f, 0.42f) to p(0.46f, 0.58f), // hendene på lårene
        ),
        props = listOf(
            p(0.22f, 0.04f) to p(0.22f, 0.97f), // vegg
        ),
    ),
    IllustrationType.LUNGE to Pose(
        head = p(0.44f, 0.12f),
        lines = listOf(
            p(0.44f, 0.24f) to p(0.44f, 0.29f), // nakke til skulder
            p(0.44f, 0.29f) to p(0.44f, 0.5f), // overkropp
            p(0.44f, 0.5f) to p(0.56f, 0.7f), // fremre bein bøyd
            p(0.56f, 0.7f) to p(0.5f, 0.96f),
            p(0.44f, 0.5f) to p(0.301f, 0.766f), // bakre bein
            p(0.301f, 0.766f) to p(0.15f, 0.96f),
            p(0.44f, 0.29f) to p(0.49f, 0.55f), // armene rett ned langs siden
        ),
        front = setOf(6),
        // Nede: bakre kne nesten i gulvet. Føttene står i ro.
        end = Frame(
            head = p(0.44f, 0.26f),
            lines = listOf(
                p(0.44f, 0.38f) to p(0.44f, 0.43f),
                p(0.44f, 0.43f) to p(0.44f, 0.64f),
                p(0.44f, 0.64f) to p(0.651f, 0.74f),
                p(0.651f, 0.74f) to p(0.5f, 0.96f),
                p(0.44f, 0.64f) to p(0.395f, 0.937f),
                p(0.395f, 0.937f) to p(0.15f, 0.96f),
                p(0.44f, 0.43f) to p(0.49f, 0.69f),
            ),
        ),
    ),
    IllustrationType.BULGARIAN to Pose(
        // Nede i bevegelsen.
        head = p(0.36f, 0.22f),
        lines = listOf(
            p(0.37f, 0.32f) to p(0.374f, 0.37f), // nakke til skulder
            p(0.374f, 0.37f) to p(0.39f, 0.58f), // overkropp
            p(0.39f, 0.58f) to p(0.178f, 0.622f), // fremre lår
            p(0.178f, 0.622f) to p(0.22f, 0.96f), // fremre legg
            p(0.39f, 0.58f) to p(0.578f, 0.857f), // bakre lår ned mot gulvet
            p(0.578f, 0.857f) to p(0.72f, 0.58f), // bakre fot oppe på benken
            p(0.374f, 0.37f) to p(0.335f, 0.63f), // armene rett ned langs siden
        ),
        props = listOf(
            p(0.64f, 0.62f) to p(0.96f, 0.62f), // benk
            p(0.9f, 0.62f) to p(0.9f, 0.97f),
        ),
        front = setOf(6),
        // Oppe.
        end = Frame(
            head = p(0.36f, 0.1f),
            lines = listOf(
                p(0.36f, 0.2f) to p(0.362f, 0.25f),
                p(0.362f, 0.25f) to p(0.37f, 0.46f),
                p(0.37f, 0.46f) to p(0.224f, 0.619f),
                p(0.224f, 0.619f) to p(0.22f, 0.96f),
                p(0.37f, 0.46f) to p(0.479f, 0.777f),
                p(0.479f, 0.777f) to p(0.72f, 0.58f),
                p(0.362f, 0.25f) to p(0.323f, 0.51f),
            ),
        ),
    ),
    IllustrationType.STEP_UP to Pose(
        head = p(0.44f, 0.34f),
        lines = listOf(
            p(0.44f, 0.44f) to p(0.45f, 0.64f), // overkropp
            p(0.45f, 0.64f) to p(0.596f, 0.553f), // fremre lår
            p(0.596f, 0.553f) to p(0.66f, 0.7f), // fremre fot oppe på kassen
            p(0.45f, 0.64f) to p(0.394f, 0.801f), // bakre bein i gulvet
            p(0.394f, 0.801f) to p(0.38f, 0.96f),
            p(0.44f, 0.44f) to p(0.5f, 0.62f), // armen langs siden
        ),
        props = listOf(
            p(0.52f, 0.74f) to p(0.92f, 0.74f), // kasse/trapp
            p(0.52f, 0.74f) to p(0.52f, 0.97f),
            p(0.92f, 0.74f) to p(0.92f, 0.97f),
        ),
        front = setOf(5),
        // Oppe på kassen, bakre bein løftet.
        end = Frame(
            head = p(0.63f, 0.08f),
            lines = listOf(
                p(0.63f, 0.17f) to p(0.64f, 0.37f),
                p(0.64f, 0.37f) to p(0.65f, 0.54f),
                p(0.65f, 0.54f) to p(0.66f, 0.7f),
                p(0.64f, 0.37f) to p(0.58f, 0.53f),
                p(0.58f, 0.53f) to p(0.5f, 0.66f),
                p(0.63f, 0.17f) to p(0.69f, 0.35f),
            ),
        ),
    ),
    IllustrationType.LUNGE_DB to Pose(
        // Utfall med en manual i hver hånd.
        head = p(0.44f, 0.12f),
        lines = listOf(
            p(0.44f, 0.24f) to p(0.44f, 0.29f), // nakke til skulder
            p(0.44f, 0.29f) to p(0.44f, 0.5f), // overkropp
            p(0.44f, 0.5f) to p(0.56f, 0.7f), // fremre bein bøyd
            p(0.56f, 0.7f) to p(0.5f, 0.96f),
            p(0.44f, 0.5f) to p(0.301f, 0.766f), // bakre bein
            p(0.301f, 0.766f) to p(0.15f, 0.96f),
            p(0.44f, 0.29f) to p(0.49f, 0.55f), // armene rett ned langs siden
        ),
        dots = listOf(p(0.495f, 0.575f)),
        front = setOf(6),
        // Nede: bakre kne nesten i gulvet. Føttene står i ro.
        end = Frame(
            head = p(0.44f, 0.26f),
            lines = listOf(
                p(0.44f, 0.38f) to p(0.44f, 0.43f),
                p(0.44f, 0.43f) to p(0.44f, 0.64f),
                p(0.44f, 0.64f) to p(0.651f, 0.74f),
                p(0.651f, 0.74f) to p(0.5f, 0.96f),
                p(0.44f, 0.64f) to p(0.395f, 0.937f),
                p(0.395f, 0.937f) to p(0.15f, 0.96f),
                p(0.44f, 0.43f) to p(0.49f, 0.69f),
            ),
            dots = listOf(p(0.495f, 0.715f)),
        ),
    ),
    IllustrationType.BULGARIAN_DB to Pose(
        // Med en manual i hver hånd.
        head = p(0.36f, 0.22f),
        lines = listOf(
            p(0.37f, 0.32f) to p(0.374f, 0.37f), // nakke til skulder
            p(0.374f, 0.37f) to p(0.39f, 0.58f), // overkropp
            p(0.39f, 0.58f) to p(0.178f, 0.622f), // fremre lår
            p(0.178f, 0.622f) to p(0.22f, 0.96f), // fremre legg
            p(0.39f, 0.58f) to p(0.578f, 0.857f), // bakre lår ned mot gulvet
            p(0.578f, 0.857f) to p(0.72f, 0.58f), // bakre fot oppe på benken
            p(0.374f, 0.37f) to p(0.335f, 0.63f), // armene rett ned langs siden
        ),
        props = listOf(
            p(0.64f, 0.62f) to p(0.96f, 0.62f), // benk
            p(0.9f, 0.62f) to p(0.9f, 0.97f),
        ),
        dots = listOf(p(0.33f, 0.655f)),
        front = setOf(6),
        // Oppe.
        end = Frame(
            head = p(0.36f, 0.1f),
            lines = listOf(
                p(0.36f, 0.2f) to p(0.362f, 0.25f),
                p(0.362f, 0.25f) to p(0.37f, 0.46f),
                p(0.37f, 0.46f) to p(0.224f, 0.619f),
                p(0.224f, 0.619f) to p(0.22f, 0.96f),
                p(0.37f, 0.46f) to p(0.479f, 0.777f),
                p(0.479f, 0.777f) to p(0.72f, 0.58f),
                p(0.362f, 0.25f) to p(0.323f, 0.51f),
            ),
            dots = listOf(p(0.318f, 0.535f)),
        ),
    ),
    IllustrationType.GOBLET_SQUAT to Pose(
        // Goblet-knebøy: én manual holdt inntil brystet med albuene pekende ned.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.495f, 0.738f),
            p(0.495f, 0.738f) to p(0.603f, 0.685f),
        ),
        dots = listOf(p(0.613f, 0.702f)),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.5f, 0.225f),
            lines = listOf(
                p(0.5f, 0.955f) to p(0.5f, 0.765f),
                p(0.5f, 0.765f) to p(0.5f, 0.575f),
                p(0.5f, 0.575f) to p(0.5f, 0.325f),
                p(0.5f, 0.325f) to p(0.536f, 0.46f),
                p(0.536f, 0.46f) to p(0.596f, 0.356f),
            ),
            dots = listOf(p(0.606f, 0.373f)),
        ),
    ),
    IllustrationType.FRONT_SQUAT to Pose(
        // Frontknebøy: manualene hviler oppå skuldrene, albuene høyt og pekende frem.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.642f, 0.703f),
            p(0.642f, 0.703f) to p(0.563f, 0.612f),
        ),
        dots = listOf(p(0.609f, 0.651f)),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.5f, 0.225f),
            lines = listOf(
                p(0.5f, 0.955f) to p(0.5f, 0.765f),
                p(0.5f, 0.765f) to p(0.5f, 0.575f),
                p(0.5f, 0.575f) to p(0.5f, 0.325f),
                p(0.5f, 0.325f) to p(0.638f, 0.349f),
                p(0.638f, 0.349f) to p(0.522f, 0.318f),
            ),
            dots = listOf(p(0.568f, 0.357f)),
        ),
    ),
    IllustrationType.BB_SQUAT to Pose(
        // Knebøy med stang: stanga ligger på øvre del av ryggen, med skiver i hver ende.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.461f, 0.721f),
            p(0.461f, 0.721f) to p(0.492f, 0.605f),
        ),
        props = listOf(
            p(0.292f, 0.605f) to p(0.692f, 0.605f), // stanga
        ),
        dots = listOf(p(0.292f, 0.605f), p(0.692f, 0.605f)),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.5f, 0.225f),
            lines = listOf(
                p(0.5f, 0.955f) to p(0.5f, 0.765f),
                p(0.5f, 0.765f) to p(0.5f, 0.575f),
                p(0.5f, 0.575f) to p(0.5f, 0.325f),
                p(0.5f, 0.325f) to p(0.42f, 0.44f),
                p(0.42f, 0.44f) to p(0.451f, 0.324f),
            ),
            dots = listOf(p(0.251f, 0.324f), p(0.651f, 0.324f)),
            props = listOf(
                p(0.251f, 0.324f) to p(0.651f, 0.324f),
            ),
        ),
    ),
    IllustrationType.BAND_SQUAT to Pose(
        // Knebøy med strikk: strikken går fra hendene ved skuldrene ned under føttene.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.642f, 0.703f),
            p(0.642f, 0.703f) to p(0.563f, 0.612f),
        ),
        props = listOf(
            p(0.563f, 0.612f) to p(0.5f, 0.955f), // strikk fra hendene ved skuldrene ned under føttene
        ),
        front = setOf(3, 4),
        end = Frame(
            head = p(0.5f, 0.225f),
            lines = listOf(
                p(0.5f, 0.955f) to p(0.5f, 0.765f),
                p(0.5f, 0.765f) to p(0.5f, 0.575f),
                p(0.5f, 0.575f) to p(0.5f, 0.325f),
                p(0.5f, 0.325f) to p(0.638f, 0.349f),
                p(0.638f, 0.349f) to p(0.522f, 0.318f),
            ),
            props = listOf(
                p(0.522f, 0.318f) to p(0.5f, 0.955f), // strikk fra hendene ved skuldrene ned under føttene
            ),
        ),
    ),
    IllustrationType.PISTOL_BOX to Pose(
        // Pistol squat ned mot en stol, på ett bein.
        head = p(0.61f, 0.44f),
        lines = listOf(
            p(0.544f, 0.516f) to p(0.39f, 0.7f), // overkropp lent frem
            p(0.39f, 0.7f) to p(0.635f, 0.726f), // lår nesten vannrett
            p(0.635f, 0.726f) to p(0.5f, 0.96f), // legg, kneet frem over tærne
            p(0.544f, 0.52f) to p(0.8f, 0.54f), // armer frem for balanse
            p(0.39f, 0.7f) to p(0.635f, 0.65f), // det andre beinet strakt frem
            p(0.635f, 0.65f) to p(0.88f, 0.6f),
        ),
        props = listOf(
            p(0.18f, 0.78f) to p(0.42f, 0.78f), // stol bak
            p(0.21f, 0.78f) to p(0.21f, 0.97f),
            p(0.39f, 0.78f) to p(0.39f, 0.97f),
            p(0.18f, 0.78f) to p(0.18f, 0.56f),
        ),
        // Stående. Føttene står i ro.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.2f) to p(0.5f, 0.44f),
                p(0.5f, 0.44f) to p(0.5f, 0.69f),
                p(0.5f, 0.69f) to p(0.5f, 0.96f),
                p(0.5f, 0.21f) to p(0.756f, 0.23f),
                p(0.5f, 0.44f) to p(0.66f, 0.63f),
                p(0.66f, 0.63f) to p(0.82f, 0.82f),
            ),
        ),
    ),
    IllustrationType.BOX_JUMP to Pose(
        // Bokshopp: knebøy foran kassen. Animeres som en sekvens, se sequence.
        head = p(0.452f, 0.554f),
        lines = listOf(
            p(0.34f, 0.96f) to p(0.437f, 0.833f), // fremre legg
            p(0.437f, 0.833f) to p(0.28f, 0.8f), // fremre lår
            p(0.28f, 0.8f) to p(0.395f, 0.636f), // overkropp
            p(0.395f, 0.636f) to p(0.248f, 0.721f), // arm
            p(0.28f, 0.8f) to p(0.436f, 0.835f), // bakre lår (dekker det fremre når beina står samlet)
            p(0.436f, 0.835f) to p(0.336f, 0.96f), // bakre legg
        ),
        props = listOf(
            p(0.52f, 0.76f) to p(0.95f, 0.76f), // kasse
            p(0.52f, 0.76f) to p(0.52f, 0.97f),
            p(0.95f, 0.76f) to p(0.95f, 0.97f),
        ),
        front = setOf(3),
        sequence = listOf(
            // sats
            Key(300, Frame(
                head = p(0.493f, 0.156f),
                lines = listOf(
                    p(0.42f, 0.76f) to p(0.464f, 0.606f),
                    p(0.464f, 0.606f) to p(0.43f, 0.45f),
                    p(0.43f, 0.45f) to p(0.472f, 0.254f),
                    p(0.472f, 0.254f) to p(0.639f, 0.284f),
                    p(0.43f, 0.45f) to p(0.462f, 0.607f),
                    p(0.462f, 0.607f) to p(0.416f, 0.76f),
                ),
            )),
            // knærne opp over kanten
            Key(250, Frame(
                head = p(0.662f, 0.158f),
                lines = listOf(
                    p(0.62f, 0.6f) to p(0.717f, 0.473f),
                    p(0.717f, 0.473f) to p(0.56f, 0.44f),
                    p(0.56f, 0.44f) to p(0.628f, 0.252f),
                    p(0.628f, 0.252f) to p(0.798f, 0.252f),
                    p(0.56f, 0.44f) to p(0.716f, 0.475f),
                    p(0.716f, 0.475f) to p(0.616f, 0.6f),
                ),
            )),
            // myk landing
            Key(250, Frame(
                head = p(0.812f, 0.314f),
                lines = listOf(
                    p(0.7f, 0.725f) to p(0.796f, 0.597f),
                    p(0.796f, 0.597f) to p(0.64f, 0.56f),
                    p(0.64f, 0.56f) to p(0.755f, 0.396f),
                    p(0.755f, 0.396f) to p(0.915f, 0.454f),
                    p(0.64f, 0.56f) to p(0.795f, 0.599f),
                    p(0.795f, 0.599f) to p(0.696f, 0.725f),
                ),
            )),
            // reiser seg
            Key(400, Frame(
                head = p(0.7f, 0.12f),
                lines = listOf(
                    p(0.7f, 0.725f) to p(0.743f, 0.571f),
                    p(0.743f, 0.571f) to p(0.69f, 0.42f),
                    p(0.69f, 0.42f) to p(0.697f, 0.22f),
                    p(0.697f, 0.22f) to p(0.712f, 0.389f),
                    p(0.69f, 0.42f) to p(0.741f, 0.572f),
                    p(0.741f, 0.572f) to p(0.696f, 0.725f),
                ),
            )),
            // står
            Key(300, Frame(
                head = p(0.7f, 0.12f),
                lines = listOf(
                    p(0.7f, 0.725f) to p(0.743f, 0.571f),
                    p(0.743f, 0.571f) to p(0.69f, 0.42f),
                    p(0.69f, 0.42f) to p(0.697f, 0.22f),
                    p(0.697f, 0.22f) to p(0.712f, 0.389f),
                    p(0.69f, 0.42f) to p(0.741f, 0.572f),
                    p(0.741f, 0.572f) to p(0.696f, 0.725f),
                ),
            )),
            // bakre fot bakover over kanten
            Key(300, Frame(
                head = p(0.666f, 0.161f),
                lines = listOf(
                    p(0.7f, 0.725f) to p(0.752f, 0.574f),
                    p(0.752f, 0.574f) to p(0.64f, 0.46f),
                    p(0.64f, 0.46f) to p(0.657f, 0.261f),
                    p(0.657f, 0.261f) to p(0.742f, 0.408f),
                    p(0.64f, 0.46f) to p(0.598f, 0.614f),
                    p(0.598f, 0.614f) to p(0.44f, 0.64f),
                ),
            )),
            // senker seg
            Key(250, Frame(
                head = p(0.582f, 0.325f),
                lines = listOf(
                    p(0.7f, 0.725f) to p(0.681f, 0.566f),
                    p(0.681f, 0.566f) to p(0.53f, 0.62f),
                    p(0.53f, 0.62f) to p(0.565f, 0.423f),
                    p(0.565f, 0.423f) to p(0.674f, 0.553f),
                    p(0.53f, 0.62f) to p(0.46f, 0.764f),
                    p(0.46f, 0.764f) to p(0.34f, 0.87f),
                ),
            )),
            // bakre fot i gulvet
            Key(250, Frame(
                head = p(0.522f, 0.385f),
                lines = listOf(
                    p(0.7f, 0.725f) to p(0.606f, 0.596f),
                    p(0.606f, 0.596f) to p(0.47f, 0.68f),
                    p(0.47f, 0.68f) to p(0.505f, 0.483f),
                    p(0.505f, 0.483f) to p(0.59f, 0.63f),
                    p(0.47f, 0.68f) to p(0.412f, 0.829f),
                    p(0.412f, 0.829f) to p(0.32f, 0.96f),
                ),
            )),
            // fremre fot løftes
            Key(200, Frame(
                head = p(0.492f, 0.373f),
                lines = listOf(
                    p(0.68f, 0.65f) to p(0.555f, 0.55f),
                    p(0.555f, 0.55f) to p(0.45f, 0.67f),
                    p(0.45f, 0.67f) to p(0.478f, 0.472f),
                    p(0.478f, 0.472f) to p(0.55f, 0.626f),
                    p(0.45f, 0.67f) to p(0.402f, 0.823f),
                    p(0.402f, 0.823f) to p(0.32f, 0.96f),
                ),
            )),
            // føres bakover
            Key(200, Frame(
                head = p(0.436f, 0.361f),
                lines = listOf(
                    p(0.56f, 0.64f) to p(0.466f, 0.51f),
                    p(0.466f, 0.51f) to p(0.41f, 0.66f),
                    p(0.41f, 0.66f) to p(0.427f, 0.461f),
                    p(0.427f, 0.461f) to p(0.485f, 0.621f),
                    p(0.41f, 0.66f) to p(0.396f, 0.819f),
                    p(0.396f, 0.819f) to p(0.32f, 0.96f),
                ),
            )),
            // forbi kanten
            Key(200, Frame(
                head = p(0.361f, 0.35f),
                lines = listOf(
                    p(0.4f, 0.7f) to p(0.469f, 0.556f),
                    p(0.469f, 0.556f) to p(0.34f, 0.65f),
                    p(0.34f, 0.65f) to p(0.354f, 0.45f),
                    p(0.354f, 0.45f) to p(0.384f, 0.617f),
                    p(0.34f, 0.65f) to p(0.368f, 0.807f),
                    p(0.368f, 0.807f) to p(0.32f, 0.96f),
                ),
            )),
            // begge føtter i gulvet
            Key(250, Frame(
                head = p(0.35f, 0.36f),
                lines = listOf(
                    p(0.34f, 0.96f) to p(0.396f, 0.81f),
                    p(0.396f, 0.81f) to p(0.34f, 0.66f),
                    p(0.34f, 0.66f) to p(0.347f, 0.46f),
                    p(0.347f, 0.46f) to p(0.362f, 0.629f),
                    p(0.34f, 0.66f) to p(0.385f, 0.814f),
                    p(0.385f, 0.814f) to p(0.32f, 0.96f),
                ),
            )),
            // står
            Key(300, Frame(
                head = p(0.34f, 0.345f),
                lines = listOf(
                    p(0.34f, 0.96f) to p(0.363f, 0.802f),
                    p(0.363f, 0.802f) to p(0.33f, 0.645f),
                    p(0.33f, 0.645f) to p(0.337f, 0.445f),
                    p(0.337f, 0.445f) to p(0.352f, 0.614f),
                    p(0.33f, 0.645f) to p(0.353f, 0.803f),
                    p(0.353f, 0.803f) to p(0.32f, 0.96f),
                ),
            )),
            // ned i knebøy
            Key(450, Frame(
                head = p(0.452f, 0.554f),
                lines = listOf(
                    p(0.34f, 0.96f) to p(0.437f, 0.833f),
                    p(0.437f, 0.833f) to p(0.28f, 0.8f),
                    p(0.28f, 0.8f) to p(0.395f, 0.636f),
                    p(0.395f, 0.636f) to p(0.248f, 0.721f),
                    p(0.28f, 0.8f) to p(0.436f, 0.835f),
                    p(0.436f, 0.835f) to p(0.336f, 0.96f),
                ),
            )),
            // klar
            Key(300, Frame(
                head = p(0.452f, 0.554f),
                lines = listOf(
                    p(0.34f, 0.96f) to p(0.437f, 0.833f),
                    p(0.437f, 0.833f) to p(0.28f, 0.8f),
                    p(0.28f, 0.8f) to p(0.395f, 0.636f),
                    p(0.395f, 0.636f) to p(0.248f, 0.721f),
                    p(0.28f, 0.8f) to p(0.436f, 0.835f),
                    p(0.436f, 0.835f) to p(0.336f, 0.96f),
                ),
            )),
        ),
    ),
    IllustrationType.WALKING_LUNGE to Pose(
        // Gående utfall: steg frem ned i utfall, opp og videre med det andre beinet. Toner ut og starter forfra.
        head = p(0.363f, 0.415f),
        lines = listOf(
            p(0.49f, 0.955f) to p(0.541f, 0.772f),
            p(0.541f, 0.772f) to p(0.351f, 0.765f),
            p(0.351f, 0.765f) to p(0.36f, 0.515f),
            p(0.36f, 0.515f) to p(0.355f, 0.655f),
            p(0.355f, 0.655f) to p(0.351f, 0.775f),
            p(0.351f, 0.765f) to p(0.39f, 0.951f),
            p(0.39f, 0.951f) to p(0.2f, 0.955f),
        ),
        sequence = listOf(
            // nederst
            Key(150, Frame(
                head = p(0.363f, 0.415f),
                lines = listOf(
                    p(0.49f, 0.955f) to p(0.541f, 0.772f),
                    p(0.541f, 0.772f) to p(0.351f, 0.765f),
                    p(0.351f, 0.765f) to p(0.36f, 0.515f),
                    p(0.36f, 0.515f) to p(0.355f, 0.655f),
                    p(0.355f, 0.655f) to p(0.351f, 0.775f),
                    p(0.351f, 0.765f) to p(0.39f, 0.951f),
                    p(0.39f, 0.951f) to p(0.2f, 0.955f),
                ),
            )),
            // opp, bakre bein svinger frem
            Key(550, Frame(
                head = p(0.502f, 0.235f),
                lines = listOf(
                    p(0.49f, 0.955f) to p(0.533f, 0.77f),
                    p(0.533f, 0.77f) to p(0.49f, 0.585f),
                    p(0.49f, 0.585f) to p(0.499f, 0.335f),
                    p(0.499f, 0.335f) to p(0.494f, 0.475f),
                    p(0.494f, 0.475f) to p(0.49f, 0.595f),
                    p(0.49f, 0.585f) to p(0.599f, 0.741f),
                    p(0.599f, 0.741f) to p(0.55f, 0.925f),
                ),
            )),
            // ned i neste utfall
            Key(550, Frame(
                head = p(0.653f, 0.415f),
                lines = listOf(
                    p(0.49f, 0.955f) to p(0.68f, 0.951f),
                    p(0.68f, 0.951f) to p(0.641f, 0.765f),
                    p(0.641f, 0.765f) to p(0.65f, 0.515f),
                    p(0.65f, 0.515f) to p(0.645f, 0.655f),
                    p(0.645f, 0.655f) to p(0.641f, 0.775f),
                    p(0.641f, 0.765f) to p(0.831f, 0.772f),
                    p(0.831f, 0.772f) to p(0.78f, 0.955f),
                ),
            )),
            // nederst
            Key(150, Frame(
                head = p(0.653f, 0.415f),
                lines = listOf(
                    p(0.49f, 0.955f) to p(0.68f, 0.951f),
                    p(0.68f, 0.951f) to p(0.641f, 0.765f),
                    p(0.641f, 0.765f) to p(0.65f, 0.515f),
                    p(0.65f, 0.515f) to p(0.645f, 0.655f),
                    p(0.645f, 0.655f) to p(0.641f, 0.775f),
                    p(0.641f, 0.765f) to p(0.831f, 0.772f),
                    p(0.831f, 0.772f) to p(0.78f, 0.955f),
                ),
            )),
            // opp, samler beina
            Key(650, Frame(
                head = p(0.792f, 0.225f),
                lines = listOf(
                    p(0.776f, 0.955f) to p(0.778f, 0.765f),
                    p(0.778f, 0.765f) to p(0.78f, 0.575f),
                    p(0.78f, 0.575f) to p(0.789f, 0.325f),
                    p(0.789f, 0.325f) to p(0.784f, 0.465f),
                    p(0.784f, 0.465f) to p(0.78f, 0.585f),
                    p(0.78f, 0.575f) to p(0.78f, 0.765f),
                    p(0.78f, 0.765f) to p(0.78f, 0.955f),
                ),
            )),
            // står
            Key(250, Frame(
                head = p(0.792f, 0.225f),
                lines = listOf(
                    p(0.776f, 0.955f) to p(0.778f, 0.765f),
                    p(0.778f, 0.765f) to p(0.78f, 0.575f),
                    p(0.78f, 0.575f) to p(0.789f, 0.325f),
                    p(0.789f, 0.325f) to p(0.784f, 0.465f),
                    p(0.784f, 0.465f) to p(0.78f, 0.585f),
                    p(0.78f, 0.575f) to p(0.78f, 0.765f),
                    p(0.78f, 0.765f) to p(0.78f, 0.955f),
                ),
            )),
            // toner ut
            Key(300, Frame(
                head = p(0.792f, 0.225f),
                lines = listOf(
                    p(0.776f, 0.955f) to p(0.778f, 0.765f),
                    p(0.778f, 0.765f) to p(0.78f, 0.575f),
                    p(0.78f, 0.575f) to p(0.789f, 0.325f),
                    p(0.789f, 0.325f) to p(0.784f, 0.465f),
                    p(0.784f, 0.465f) to p(0.78f, 0.585f),
                    p(0.78f, 0.575f) to p(0.78f, 0.765f),
                    p(0.78f, 0.765f) to p(0.78f, 0.955f),
                ),
            ), alpha = 0f),
            // tilbake til start
            Key(40, Frame(
                head = p(0.212f, 0.225f),
                lines = listOf(
                    p(0.204f, 0.955f) to p(0.202f, 0.765f),
                    p(0.202f, 0.765f) to p(0.2f, 0.575f),
                    p(0.2f, 0.575f) to p(0.209f, 0.325f),
                    p(0.209f, 0.325f) to p(0.204f, 0.465f),
                    p(0.204f, 0.465f) to p(0.2f, 0.585f),
                    p(0.2f, 0.575f) to p(0.2f, 0.765f),
                    p(0.2f, 0.765f) to p(0.2f, 0.955f),
                ),
            ), alpha = 0f),
            // toner inn
            Key(300, Frame(
                head = p(0.212f, 0.225f),
                lines = listOf(
                    p(0.204f, 0.955f) to p(0.202f, 0.765f),
                    p(0.202f, 0.765f) to p(0.2f, 0.575f),
                    p(0.2f, 0.575f) to p(0.209f, 0.325f),
                    p(0.209f, 0.325f) to p(0.204f, 0.465f),
                    p(0.204f, 0.465f) to p(0.2f, 0.585f),
                    p(0.2f, 0.575f) to p(0.2f, 0.765f),
                    p(0.2f, 0.765f) to p(0.2f, 0.955f),
                ),
            )),
            // steg frem og ned
            Key(650, Frame(
                head = p(0.363f, 0.415f),
                lines = listOf(
                    p(0.49f, 0.955f) to p(0.541f, 0.772f),
                    p(0.541f, 0.772f) to p(0.351f, 0.765f),
                    p(0.351f, 0.765f) to p(0.36f, 0.515f),
                    p(0.36f, 0.515f) to p(0.355f, 0.655f),
                    p(0.355f, 0.655f) to p(0.351f, 0.775f),
                    p(0.351f, 0.765f) to p(0.39f, 0.951f),
                    p(0.39f, 0.951f) to p(0.2f, 0.955f),
                ),
            )),
        ),
    ),
    IllustrationType.LATERAL_LUNGE to Pose(
        // Sideutfall sett forfra: bred stilling, vekten over til den ene siden med bøyd kne mens det andre beinet er strakt.
        head = p(0.311f, 0.393f),
        lines = listOf(
            p(0.3f, 0.955f) to p(0.146f, 0.843f),
            p(0.146f, 0.843f) to p(0.31f, 0.747f),
            p(0.31f, 0.747f) to p(0.36f, 0.74f),
            p(0.36f, 0.74f) to p(0.325f, 0.492f),
            p(0.325f, 0.492f) to p(0.241f, 0.504f),
            p(0.241f, 0.504f) to p(0.12f, 0.575f),
            p(0.12f, 0.575f) to p(0.24f, 0.58f),
            p(0.36f, 0.74f) to p(0.41f, 0.733f),
            p(0.41f, 0.733f) to p(0.587f, 0.802f),
            p(0.587f, 0.802f) to p(0.7f, 0.955f),
            p(0.325f, 0.492f) to p(0.409f, 0.48f),
            p(0.409f, 0.48f) to p(0.36f, 0.611f),
            p(0.36f, 0.611f) to p(0.244f, 0.58f),
        ),
        sequence = listOf(
            // tilbake
            Key(550, Frame(
                head = p(0.5f, 0.25f),
                lines = listOf(
                    p(0.3f, 0.955f) to p(0.375f, 0.777f),
                    p(0.375f, 0.777f) to p(0.45f, 0.6f),
                    p(0.45f, 0.6f) to p(0.5f, 0.6f),
                    p(0.5f, 0.6f) to p(0.5f, 0.35f),
                    p(0.5f, 0.35f) to p(0.415f, 0.35f),
                    p(0.415f, 0.35f) to p(0.39f, 0.488f),
                    p(0.39f, 0.488f) to p(0.5f, 0.44f),
                    p(0.5f, 0.6f) to p(0.55f, 0.6f),
                    p(0.55f, 0.6f) to p(0.625f, 0.777f),
                    p(0.625f, 0.777f) to p(0.7f, 0.955f),
                    p(0.5f, 0.35f) to p(0.585f, 0.35f),
                    p(0.585f, 0.35f) to p(0.614f, 0.487f),
                    p(0.614f, 0.487f) to p(0.504f, 0.44f),
                ),
            )),
            // pause
            Key(150, Frame(
                head = p(0.5f, 0.25f),
                lines = listOf(
                    p(0.3f, 0.955f) to p(0.375f, 0.777f),
                    p(0.375f, 0.777f) to p(0.45f, 0.6f),
                    p(0.45f, 0.6f) to p(0.5f, 0.6f),
                    p(0.5f, 0.6f) to p(0.5f, 0.35f),
                    p(0.5f, 0.35f) to p(0.415f, 0.35f),
                    p(0.415f, 0.35f) to p(0.39f, 0.488f),
                    p(0.39f, 0.488f) to p(0.5f, 0.44f),
                    p(0.5f, 0.6f) to p(0.55f, 0.6f),
                    p(0.55f, 0.6f) to p(0.625f, 0.777f),
                    p(0.625f, 0.777f) to p(0.7f, 0.955f),
                    p(0.5f, 0.35f) to p(0.585f, 0.35f),
                    p(0.585f, 0.35f) to p(0.614f, 0.487f),
                    p(0.614f, 0.487f) to p(0.504f, 0.44f),
                ),
            )),
            // ned mot høyre
            Key(650, Frame(
                head = p(0.689f, 0.393f),
                lines = listOf(
                    p(0.3f, 0.955f) to p(0.413f, 0.802f),
                    p(0.413f, 0.802f) to p(0.59f, 0.733f),
                    p(0.59f, 0.733f) to p(0.64f, 0.74f),
                    p(0.64f, 0.74f) to p(0.675f, 0.492f),
                    p(0.675f, 0.492f) to p(0.591f, 0.48f),
                    p(0.591f, 0.48f) to p(0.644f, 0.61f),
                    p(0.644f, 0.61f) to p(0.76f, 0.58f),
                    p(0.64f, 0.74f) to p(0.69f, 0.747f),
                    p(0.69f, 0.747f) to p(0.854f, 0.843f),
                    p(0.854f, 0.843f) to p(0.7f, 0.955f),
                    p(0.675f, 0.492f) to p(0.759f, 0.504f),
                    p(0.759f, 0.504f) to p(0.883f, 0.568f),
                    p(0.883f, 0.568f) to p(0.764f, 0.58f),
                ),
            )),
            // tilbake
            Key(550, Frame(
                head = p(0.5f, 0.25f),
                lines = listOf(
                    p(0.3f, 0.955f) to p(0.375f, 0.777f),
                    p(0.375f, 0.777f) to p(0.45f, 0.6f),
                    p(0.45f, 0.6f) to p(0.5f, 0.6f),
                    p(0.5f, 0.6f) to p(0.5f, 0.35f),
                    p(0.5f, 0.35f) to p(0.415f, 0.35f),
                    p(0.415f, 0.35f) to p(0.39f, 0.488f),
                    p(0.39f, 0.488f) to p(0.5f, 0.44f),
                    p(0.5f, 0.6f) to p(0.55f, 0.6f),
                    p(0.55f, 0.6f) to p(0.625f, 0.777f),
                    p(0.625f, 0.777f) to p(0.7f, 0.955f),
                    p(0.5f, 0.35f) to p(0.585f, 0.35f),
                    p(0.585f, 0.35f) to p(0.614f, 0.487f),
                    p(0.614f, 0.487f) to p(0.504f, 0.44f),
                ),
            )),
            // pause
            Key(150, Frame(
                head = p(0.5f, 0.25f),
                lines = listOf(
                    p(0.3f, 0.955f) to p(0.375f, 0.777f),
                    p(0.375f, 0.777f) to p(0.45f, 0.6f),
                    p(0.45f, 0.6f) to p(0.5f, 0.6f),
                    p(0.5f, 0.6f) to p(0.5f, 0.35f),
                    p(0.5f, 0.35f) to p(0.415f, 0.35f),
                    p(0.415f, 0.35f) to p(0.39f, 0.488f),
                    p(0.39f, 0.488f) to p(0.5f, 0.44f),
                    p(0.5f, 0.6f) to p(0.55f, 0.6f),
                    p(0.55f, 0.6f) to p(0.625f, 0.777f),
                    p(0.625f, 0.777f) to p(0.7f, 0.955f),
                    p(0.5f, 0.35f) to p(0.585f, 0.35f),
                    p(0.585f, 0.35f) to p(0.614f, 0.487f),
                    p(0.614f, 0.487f) to p(0.504f, 0.44f),
                ),
            )),
            // ned mot venstre
            Key(650, Frame(
                head = p(0.311f, 0.393f),
                lines = listOf(
                    p(0.3f, 0.955f) to p(0.146f, 0.843f),
                    p(0.146f, 0.843f) to p(0.31f, 0.747f),
                    p(0.31f, 0.747f) to p(0.36f, 0.74f),
                    p(0.36f, 0.74f) to p(0.325f, 0.492f),
                    p(0.325f, 0.492f) to p(0.241f, 0.504f),
                    p(0.241f, 0.504f) to p(0.12f, 0.575f),
                    p(0.12f, 0.575f) to p(0.24f, 0.58f),
                    p(0.36f, 0.74f) to p(0.41f, 0.733f),
                    p(0.41f, 0.733f) to p(0.587f, 0.802f),
                    p(0.587f, 0.802f) to p(0.7f, 0.955f),
                    p(0.325f, 0.492f) to p(0.409f, 0.48f),
                    p(0.409f, 0.48f) to p(0.36f, 0.611f),
                    p(0.36f, 0.611f) to p(0.244f, 0.58f),
                ),
            )),
        ),
    ),

    // ------------------------------------------------------------ HOFTE
    IllustrationType.HIP_HINGE to Pose(
        head = p(0.76f, 0.26f),
        lines = listOf(
            p(0.68f, 0.33f) to p(0.4f, 0.55f), // flat rygg, fremoverlent
            p(0.4f, 0.55f) to p(0.42f, 0.78f), // lett bøyde knær
            p(0.42f, 0.78f) to p(0.45f, 0.97f),
            p(0.68f, 0.33f) to p(0.55f, 0.62f), // arm henger med vekten
        ),
        dots = listOf(p(0.55f, 0.62f)),
        front = setOf(3),
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.5f, 0.5f),
            ),
            dots = listOf(p(0.5f, 0.515f)),
        ),
    ),
    IllustrationType.GLUTE_BRIDGE to Pose(
        head = p(0.08f, 0.87f),
        lines = listOf(
            p(0.16f, 0.9f) to p(0.5f, 0.64f), // hofta løftet, skuldrene i gulvet
            p(0.5f, 0.64f) to p(0.72f, 0.7f), // lår
            p(0.72f, 0.7f) to p(0.8f, 0.94f), // legg, foten i gulvet
            p(0.16f, 0.9f) to p(0.4f, 0.93f), // armene langs gulvet
        ),
        // Hofta senket til gulvet.
        end = Frame(
            head = p(0.08f, 0.87f),
            lines = listOf(
                p(0.16f, 0.9f) to p(0.57f, 0.915f),
                p(0.57f, 0.915f) to p(0.682f, 0.716f),
                p(0.682f, 0.716f) to p(0.8f, 0.94f),
                p(0.16f, 0.9f) to p(0.4f, 0.93f),
            ),
        ),
    ),
    IllustrationType.CALF_RAISE to Pose(
        head = p(0.5f, 0.12f),
        lines = listOf(
            p(0.5f, 0.24f) to p(0.5f, 0.5f),
            p(0.5f, 0.5f) to p(0.5f, 0.8f),
            p(0.44f, 0.86f) to p(0.44f, 0.8f), // hælene løftet
            p(0.56f, 0.86f) to p(0.56f, 0.8f),
            p(0.5f, 0.24f) to p(0.38f, 0.5f), // armene langs siden (sett forfra)
            p(0.5f, 0.24f) to p(0.62f, 0.5f),
        ),
        // Hælene ned i gulvet.
        end = Frame(
            head = p(0.5f, 0.2f),
            lines = listOf(
                p(0.5f, 0.32f) to p(0.5f, 0.58f),
                p(0.5f, 0.58f) to p(0.5f, 0.88f),
                p(0.44f, 0.94f) to p(0.44f, 0.88f),
                p(0.56f, 0.94f) to p(0.56f, 0.88f),
                p(0.5f, 0.32f) to p(0.38f, 0.58f),
                p(0.5f, 0.32f) to p(0.62f, 0.58f),
            ),
        ),
    ),
    IllustrationType.SUPERMAN to Pose(
        // Superman: på magen med armene strakt frem. Armer, bryst og bein løftes fra gulvet og holdes, hofta ligger.
        head = p(0.234f, 0.814f),
        lines = listOf(
            p(0.928f, 0.837f) to p(0.744f, 0.886f),
            p(0.744f, 0.886f) to p(0.56f, 0.935f),
            p(0.56f, 0.935f) to p(0.321f, 0.862f),
            p(0.321f, 0.862f) to p(0.195f, 0.801f),
            p(0.195f, 0.801f) to p(0.087f, 0.748f),
        ),
        sequence = listOf(
            // holder
            Key(600, Frame(
                head = p(0.234f, 0.814f),
                lines = listOf(
                    p(0.928f, 0.837f) to p(0.744f, 0.886f),
                    p(0.744f, 0.886f) to p(0.56f, 0.935f),
                    p(0.56f, 0.935f) to p(0.321f, 0.862f),
                    p(0.321f, 0.862f) to p(0.195f, 0.801f),
                    p(0.195f, 0.801f) to p(0.087f, 0.748f),
                ),
            )),
            // senker
            Key(550, Frame(
                head = p(0.212f, 0.914f),
                lines = listOf(
                    p(0.94f, 0.935f) to p(0.75f, 0.935f),
                    p(0.75f, 0.935f) to p(0.56f, 0.935f),
                    p(0.56f, 0.935f) to p(0.31f, 0.935f),
                    p(0.31f, 0.935f) to p(0.17f, 0.935f),
                    p(0.17f, 0.935f) to p(0.05f, 0.935f),
                ),
            )),
            // hviler
            Key(400, Frame(
                head = p(0.212f, 0.914f),
                lines = listOf(
                    p(0.94f, 0.935f) to p(0.75f, 0.935f),
                    p(0.75f, 0.935f) to p(0.56f, 0.935f),
                    p(0.56f, 0.935f) to p(0.31f, 0.935f),
                    p(0.31f, 0.935f) to p(0.17f, 0.935f),
                    p(0.17f, 0.935f) to p(0.05f, 0.935f),
                ),
            )),
            // løfter
            Key(550, Frame(
                head = p(0.234f, 0.814f),
                lines = listOf(
                    p(0.928f, 0.837f) to p(0.744f, 0.886f),
                    p(0.744f, 0.886f) to p(0.56f, 0.935f),
                    p(0.56f, 0.935f) to p(0.321f, 0.862f),
                    p(0.321f, 0.862f) to p(0.195f, 0.801f),
                    p(0.195f, 0.801f) to p(0.087f, 0.748f),
                ),
            )),
        ),
    ),
    IllustrationType.CALF_RAISE_DB to Pose(
        // Med en manual i hver hånd.
        head = p(0.5f, 0.12f),
        lines = listOf(
            p(0.5f, 0.24f) to p(0.5f, 0.5f),
            p(0.5f, 0.5f) to p(0.5f, 0.8f),
            p(0.44f, 0.86f) to p(0.44f, 0.8f), // hælene løftet
            p(0.56f, 0.86f) to p(0.56f, 0.8f),
            p(0.5f, 0.24f) to p(0.38f, 0.5f), // armene langs siden (sett forfra)
            p(0.5f, 0.24f) to p(0.62f, 0.5f),
        ),
        dots = listOf(p(0.37f, 0.53f), p(0.63f, 0.53f)),
        // Hælene ned i gulvet.
        end = Frame(
            head = p(0.5f, 0.2f),
            lines = listOf(
                p(0.5f, 0.32f) to p(0.5f, 0.58f),
                p(0.5f, 0.58f) to p(0.5f, 0.88f),
                p(0.44f, 0.94f) to p(0.44f, 0.88f),
                p(0.56f, 0.94f) to p(0.56f, 0.88f),
                p(0.5f, 0.32f) to p(0.38f, 0.58f),
                p(0.5f, 0.32f) to p(0.62f, 0.58f),
            ),
            dots = listOf(p(0.37f, 0.61f), p(0.63f, 0.61f)),
        ),
    ),
    IllustrationType.GLUTE_BRIDGE_1LEG to Pose(
        // Enbens: det frie beinet følger hofta.
        head = p(0.08f, 0.87f),
        lines = listOf(
            p(0.16f, 0.9f) to p(0.5f, 0.64f), // hofta løftet, skuldrene i gulvet
            p(0.5f, 0.64f) to p(0.72f, 0.7f), // lår
            p(0.72f, 0.7f) to p(0.8f, 0.94f), // legg, foten i gulvet
            p(0.16f, 0.9f) to p(0.4f, 0.93f), // armene langs gulvet
            p(0.5f, 0.64f) to p(0.85f, 0.373f), // det andre beinet strakt i forlengelsen av kroppen
        ),
        // Hofta senket til gulvet.
        end = Frame(
            head = p(0.08f, 0.87f),
            lines = listOf(
                p(0.16f, 0.9f) to p(0.57f, 0.915f),
                p(0.57f, 0.915f) to p(0.682f, 0.716f),
                p(0.682f, 0.716f) to p(0.8f, 0.94f),
                p(0.16f, 0.9f) to p(0.4f, 0.93f),
                p(0.57f, 0.915f) to p(0.97f, 0.93f),
            ),
        ),
    ),
    IllustrationType.HIP_THRUST to Pose(
        // Hip thrust: skuldrene mot benken, manualen på hofta.
        head = p(0.1f, 0.62f),
        lines = listOf(
            p(0.18f, 0.66f) to p(0.5f, 0.62f), // skuldrene på benken, hofta løftet
            p(0.5f, 0.62f) to p(0.72f, 0.64f), // lår
            p(0.72f, 0.64f) to p(0.78f, 0.95f), // legg, foten i gulvet
        ),
        props = listOf(
            p(0.04f, 0.73f) to p(0.25f, 0.73f), // benk
            p(0.08f, 0.73f) to p(0.08f, 0.97f),
            p(0.22f, 0.73f) to p(0.22f, 0.97f),
        ),
        dots = listOf(p(0.5f, 0.56f)),
        // Hofta senket. Skuldrene og føttene står i ro.
        end = Frame(
            head = p(0.1f, 0.62f),
            lines = listOf(
                p(0.18f, 0.66f) to p(0.44f, 0.85f),
                p(0.44f, 0.85f) to p(0.596f, 0.693f),
                p(0.596f, 0.693f) to p(0.78f, 0.95f),
            ),
            dots = listOf(p(0.44f, 0.79f)),
        ),
    ),
    IllustrationType.KB_SWING to Pose(
        // Kettlebell-sving: fra hoftebøy til strake armer frem i brysthøyde.
        head = p(0.76f, 0.26f),
        lines = listOf(
            p(0.68f, 0.33f) to p(0.4f, 0.55f), // flat rygg, fremoverlent
            p(0.4f, 0.55f) to p(0.42f, 0.78f), // lett bøyde knær
            p(0.42f, 0.78f) to p(0.45f, 0.97f),
            p(0.68f, 0.33f) to p(0.55f, 0.62f), // armene henger med kettlebellen mellom beina
        ),
        dots = listOf(p(0.55f, 0.62f)),
        front = setOf(3),
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.75f, 0.22f),
            ),
            dots = listOf(p(0.78f, 0.22f)),
        ),
    ),
    IllustrationType.GOOD_MORNING to Pose(
        // Good morning med strikk over skuldrene.
        head = p(0.76f, 0.26f),
        lines = listOf(
            p(0.68f, 0.33f) to p(0.4f, 0.55f), // flat rygg, fremoverlent
            p(0.4f, 0.55f) to p(0.42f, 0.78f), // lett bøyde knær
            p(0.42f, 0.78f) to p(0.45f, 0.97f),
            p(0.68f, 0.33f) to p(0.6f, 0.43f), // hendene holder strikken ved skuldrene
            p(0.6f, 0.43f) to p(0.7f, 0.4f),
        ),
        props = listOf(
            p(0.7f, 0.4f) to p(0.45f, 0.97f), // strikk fra skuldrene ned under føttene
        ),
        front = setOf(3, 4),
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.5f, 0.31f),
                p(0.5f, 0.31f) to p(0.55f, 0.2f),
            ),
            props = listOf(
                p(0.55f, 0.2f) to p(0.45f, 0.97f),
            ),
        ),
    ),
    IllustrationType.PULL_THROUGH to Pose(
        // Pull-through: strikken går mellom beina bak til et festepunkt.
        head = p(0.76f, 0.26f),
        lines = listOf(
            p(0.68f, 0.33f) to p(0.4f, 0.55f), // flat rygg, fremoverlent
            p(0.4f, 0.55f) to p(0.42f, 0.78f), // lett bøyde knær
            p(0.42f, 0.78f) to p(0.45f, 0.97f),
            p(0.68f, 0.33f) to p(0.55f, 0.62f), // arm henger med vekten
        ),
        props = listOf(
            p(0.55f, 0.62f) to p(0.02f, 0.88f), // strikk mellom beina bak til festepunkt
        ),
        front = setOf(3),
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.5f, 0.5f),
            ),
            props = listOf(
                p(0.5f, 0.5f) to p(0.02f, 0.88f),
            ),
        ),
    ),
    IllustrationType.SINGLE_LEG_RDL to Pose(
        // Enbens markløft: det andre beinet løftes bak.
        head = p(0.76f, 0.26f),
        lines = listOf(
            p(0.68f, 0.33f) to p(0.4f, 0.55f), // flat rygg, fremoverlent
            p(0.4f, 0.55f) to p(0.42f, 0.78f), // lett bøyde knær
            p(0.42f, 0.78f) to p(0.45f, 0.97f),
            p(0.68f, 0.33f) to p(0.55f, 0.62f), // arm henger med vekten
            p(0.4f, 0.55f) to p(0.02f, 0.44f), // det andre beinet løftet bak
        ),
        dots = listOf(p(0.55f, 0.62f)),
        front = setOf(3),
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.5f, 0.5f),
                p(0.44f, 0.55f) to p(0.36f, 0.95f),
            ),
            dots = listOf(p(0.5f, 0.515f)),
        ),
    ),

    // ------------------------------------------------------------ KJERNE
    IllustrationType.PLANK to Pose(
        // Holdøvelse: står stille.
        head = p(0.08f, 0.66f),
        lines = listOf(
            p(0.18f, 0.72f) to p(0.58f, 0.8f), // rett kropp
            p(0.58f, 0.8f) to p(0.94f, 0.91f), // bein til tærne
            p(0.19f, 0.72f) to p(0.21f, 0.92f), // overarm ned til albuen
            p(0.21f, 0.92f) to p(0.04f, 0.92f), // underarm langs gulvet
        ),
    ),
    IllustrationType.SIDE_PLANK to Pose(
        // Sideplanke: underarmen i gulvet med albuen rett under skulderen, strak kropp. Den andre armen peker opp.
        head = p(0.116f, 0.761f),
        lines = listOf(
            p(0.814f, 0.955f) to p(0.631f, 0.903f),
            p(0.631f, 0.903f) to p(0.444f, 0.871f),
            p(0.444f, 0.871f) to p(0.2f, 0.815f),
            p(0.2f, 0.815f) to p(0.2f, 0.955f),
            p(0.2f, 0.955f) to p(0.08f, 0.955f),
            p(0.2f, 0.815f) to p(0.2f, 0.675f),
            p(0.2f, 0.675f) to p(0.2f, 0.555f),
        ),
    ),
    IllustrationType.MOUNTAIN_CLIMBER to Pose(
        head = p(0.1f, 0.56f),
        lines = listOf(
            p(0.18f, 0.62f) to p(0.56f, 0.7f), // overkropp i høy planke
            p(0.18f, 0.62f) to p(0.2f, 0.93f), // strak arm
            p(0.56f, 0.7f) to p(0.74f, 0.8f), // strakt bakre bein
            p(0.74f, 0.8f) to p(0.92f, 0.9f),
            p(0.56f, 0.7f) to p(0.36f, 0.8f), // kneet dratt frem
            p(0.36f, 0.8f) to p(0.47f, 0.965f),
        ),
        // Beina bytter plass.
        end = Frame(
            head = p(0.1f, 0.56f),
            lines = listOf(
                p(0.18f, 0.62f) to p(0.56f, 0.7f),
                p(0.18f, 0.62f) to p(0.2f, 0.93f),
                p(0.56f, 0.7f) to p(0.36f, 0.8f),
                p(0.36f, 0.8f) to p(0.47f, 0.965f),
                p(0.56f, 0.7f) to p(0.74f, 0.8f),
                p(0.74f, 0.8f) to p(0.92f, 0.9f),
            ),
        ),
    ),
    IllustrationType.BIRD_DOG to Pose(
        head = p(0.22f, 0.5f),
        lines = listOf(
            p(0.32f, 0.62f) to p(0.64f, 0.62f), // flat rygg på alle fire
            p(0.32f, 0.62f) to p(0.32f, 0.95f), // støttearm
            p(0.64f, 0.62f) to p(0.66f, 0.95f), // støttekne
            p(0.66f, 0.95f) to p(0.86f, 0.95f),
            p(0.32f, 0.64f) to p(0.04f, 0.66f), // motsatt arm strakt frem
            p(0.64f, 0.62f) to p(0.96f, 0.58f), // motsatt bein strakt bak
        ),
        // Tilbake på alle fire.
        end = Frame(
            head = p(0.22f, 0.5f),
            lines = listOf(
                p(0.32f, 0.62f) to p(0.64f, 0.62f),
                p(0.32f, 0.62f) to p(0.32f, 0.95f),
                p(0.64f, 0.62f) to p(0.66f, 0.95f),
                p(0.66f, 0.95f) to p(0.86f, 0.95f),
                p(0.32f, 0.64f) to p(0.36f, 0.95f),
                p(0.64f, 0.62f) to p(0.7f, 0.95f),
            ),
        ),
    ),
    IllustrationType.DEAD_BUG to Pose(
        // Dead bug: på ryggen med armene rett opp og knærne bøyd i 90 grader. Én arm og motsatt bein strekkes ut mot gulvet, så bytter det.
        head = p(0.215f, 0.9f),
        lines = listOf(
            p(0.76f, 0.745f) to p(0.57f, 0.745f),
            p(0.57f, 0.745f) to p(0.57f, 0.935f),
            p(0.57f, 0.935f) to p(0.32f, 0.935f),
            p(0.32f, 0.935f) to p(0.32f, 0.795f),
            p(0.32f, 0.795f) to p(0.32f, 0.675f),
            p(0.57f, 0.935f) to p(0.573f, 0.745f),
            p(0.573f, 0.745f) to p(0.763f, 0.745f),
            p(0.32f, 0.935f) to p(0.323f, 0.795f),
            p(0.323f, 0.795f) to p(0.323f, 0.675f),
        ),
        sequence = listOf(
            // arm og motsatt bein ut
            Key(700, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.76f, 0.745f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.183f, 0.908f),
                    p(0.183f, 0.908f) to p(0.065f, 0.885f),
                    p(0.57f, 0.935f) to p(0.758f, 0.905f),
                    p(0.758f, 0.905f) to p(0.946f, 0.875f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                ),
            )),
            // hold
            Key(250, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.76f, 0.745f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.183f, 0.908f),
                    p(0.183f, 0.908f) to p(0.065f, 0.885f),
                    p(0.57f, 0.935f) to p(0.758f, 0.905f),
                    p(0.758f, 0.905f) to p(0.946f, 0.875f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                ),
            )),
            // tilbake
            Key(650, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.76f, 0.745f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                    p(0.57f, 0.935f) to p(0.573f, 0.745f),
                    p(0.573f, 0.745f) to p(0.763f, 0.745f),
                    p(0.32f, 0.935f) to p(0.323f, 0.795f),
                    p(0.323f, 0.795f) to p(0.323f, 0.675f),
                ),
            )),
            // pause
            Key(200, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.76f, 0.745f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                    p(0.57f, 0.935f) to p(0.573f, 0.745f),
                    p(0.573f, 0.745f) to p(0.763f, 0.745f),
                    p(0.32f, 0.935f) to p(0.323f, 0.795f),
                    p(0.323f, 0.795f) to p(0.323f, 0.675f),
                ),
            )),
            // andre arm og bein ut
            Key(700, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.946f, 0.875f) to p(0.758f, 0.905f),
                    p(0.758f, 0.905f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                    p(0.57f, 0.935f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.76f, 0.745f),
                    p(0.32f, 0.935f) to p(0.183f, 0.908f),
                    p(0.183f, 0.908f) to p(0.065f, 0.885f),
                ),
            )),
            // hold
            Key(250, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.946f, 0.875f) to p(0.758f, 0.905f),
                    p(0.758f, 0.905f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                    p(0.57f, 0.935f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.76f, 0.745f),
                    p(0.32f, 0.935f) to p(0.183f, 0.908f),
                    p(0.183f, 0.908f) to p(0.065f, 0.885f),
                ),
            )),
            // tilbake
            Key(650, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.76f, 0.745f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                    p(0.57f, 0.935f) to p(0.573f, 0.745f),
                    p(0.573f, 0.745f) to p(0.763f, 0.745f),
                    p(0.32f, 0.935f) to p(0.323f, 0.795f),
                    p(0.323f, 0.795f) to p(0.323f, 0.675f),
                ),
            )),
            // pause
            Key(200, Frame(
                head = p(0.215f, 0.9f),
                lines = listOf(
                    p(0.76f, 0.745f) to p(0.57f, 0.745f),
                    p(0.57f, 0.745f) to p(0.57f, 0.935f),
                    p(0.57f, 0.935f) to p(0.32f, 0.935f),
                    p(0.32f, 0.935f) to p(0.32f, 0.795f),
                    p(0.32f, 0.795f) to p(0.32f, 0.675f),
                    p(0.57f, 0.935f) to p(0.573f, 0.745f),
                    p(0.573f, 0.745f) to p(0.763f, 0.745f),
                    p(0.32f, 0.935f) to p(0.323f, 0.795f),
                    p(0.323f, 0.795f) to p(0.323f, 0.675f),
                ),
            )),
        ),
    ),
    IllustrationType.HOLLOW_HOLD to Pose(
        // Holdøvelse: står stille.
        head = p(0.26f, 0.7f),
        lines = listOf(
            p(0.32f, 0.8f) to p(0.54f, 0.9f), // skuldrene løftet, korsryggen i gulvet
            p(0.54f, 0.9f) to p(0.95f, 0.78f), // strake bein løftet
            p(0.32f, 0.8f) to p(0.04f, 0.74f), // armer strakt bak hodet
        ),
    ),
    IllustrationType.LEG_RAISE to Pose(
        head = p(0.1f, 0.87f),
        lines = listOf(
            p(0.18f, 0.9f) to p(0.54f, 0.9f), // liggende flatt
            p(0.54f, 0.9f) to p(0.61f, 0.49f), // strake bein løftet opp
            p(0.24f, 0.91f) to p(0.44f, 0.93f), // armene langs siden
        ),
        // Beina senket nesten til gulvet.
        end = Frame(
            head = p(0.1f, 0.87f),
            lines = listOf(
                p(0.18f, 0.9f) to p(0.54f, 0.9f),
                p(0.54f, 0.9f) to p(0.95f, 0.86f),
                p(0.24f, 0.91f) to p(0.44f, 0.93f),
            ),
        ),
    ),
    IllustrationType.HANGING_RAISE to Pose(
        head = p(0.6f, 0.25f),
        lines = listOf(
            p(0.5f, 0.34f) to p(0.5f, 0.08f), // armer opp til stanga (sett fra siden)
            p(0.5f, 0.34f) to p(0.48f, 0.62f), // overkropp
            p(0.48f, 0.62f) to p(0.66f, 0.58f), // knærne løftet
            p(0.66f, 0.58f) to p(0.67f, 0.76f),
        ),
        props = listOf(
            p(0.2f, 0.08f) to p(0.8f, 0.08f), // tverrligger
        ),
        ground = false,
        // Hengende med strake bein.
        end = Frame(
            head = p(0.6f, 0.25f),
            lines = listOf(
                p(0.5f, 0.34f) to p(0.5f, 0.08f),
                p(0.5f, 0.34f) to p(0.48f, 0.62f),
                p(0.48f, 0.62f) to p(0.49f, 0.8f),
                p(0.49f, 0.8f) to p(0.5f, 0.97f),
            ),
        ),
    ),
    IllustrationType.SEATED_TWIST to Pose(
        // Russian twist sett forfra: sittende med knærne opp foran brystet og overkroppen lent litt bakover. Overkroppen vris, så manualen føres ned ved hofta på den ene siden, så den andre.
        head = p(0.5f, 0.55f),
        lines = listOf(
            p(0.425f, 0.845f) to p(0.455f, 0.72f),
            p(0.455f, 0.72f) to p(0.45f, 0.935f),
            p(0.45f, 0.935f) to p(0.5f, 0.935f),
            p(0.5f, 0.935f) to p(0.5f, 0.64f),
            p(0.5f, 0.64f) to p(0.415f, 0.64f),
            p(0.415f, 0.64f) to p(0.405f, 0.73f),
            p(0.405f, 0.73f) to p(0.5f, 0.735f),
            p(0.5f, 0.935f) to p(0.55f, 0.935f),
            p(0.55f, 0.935f) to p(0.545f, 0.72f),
            p(0.545f, 0.72f) to p(0.575f, 0.845f),
            p(0.5f, 0.64f) to p(0.585f, 0.64f),
            p(0.585f, 0.64f) to p(0.595f, 0.73f),
            p(0.595f, 0.73f) to p(0.504f, 0.735f),
        ),
        dots = listOf(p(0.502f, 0.74f)),
        front = setOf(0, 1, 5, 6, 8, 9, 11, 12),
        sequence = listOf(
            // vri mot venstre
            Key(550, Frame(
                head = p(0.464f, 0.55f),
                lines = listOf(
                    p(0.425f, 0.845f) to p(0.455f, 0.72f),
                    p(0.455f, 0.72f) to p(0.45f, 0.935f),
                    p(0.45f, 0.935f) to p(0.5f, 0.935f),
                    p(0.5f, 0.935f) to p(0.5f, 0.64f),
                    p(0.5f, 0.64f) to p(0.4f, 0.65f),
                    p(0.4f, 0.65f) to p(0.37f, 0.77f),
                    p(0.37f, 0.77f) to p(0.3f, 0.89f),
                    p(0.5f, 0.935f) to p(0.55f, 0.935f),
                    p(0.55f, 0.935f) to p(0.545f, 0.72f),
                    p(0.545f, 0.72f) to p(0.575f, 0.845f),
                    p(0.5f, 0.64f) to p(0.54f, 0.65f),
                    p(0.54f, 0.65f) to p(0.4f, 0.77f),
                    p(0.4f, 0.77f) to p(0.304f, 0.89f),
                ),
                dots = listOf(p(0.302f, 0.895f)),
            )),
            // manualen ned ved hofta
            Key(200, Frame(
                head = p(0.464f, 0.55f),
                lines = listOf(
                    p(0.425f, 0.845f) to p(0.455f, 0.72f),
                    p(0.455f, 0.72f) to p(0.45f, 0.935f),
                    p(0.45f, 0.935f) to p(0.5f, 0.935f),
                    p(0.5f, 0.935f) to p(0.5f, 0.64f),
                    p(0.5f, 0.64f) to p(0.4f, 0.65f),
                    p(0.4f, 0.65f) to p(0.37f, 0.77f),
                    p(0.37f, 0.77f) to p(0.3f, 0.89f),
                    p(0.5f, 0.935f) to p(0.55f, 0.935f),
                    p(0.55f, 0.935f) to p(0.545f, 0.72f),
                    p(0.545f, 0.72f) to p(0.575f, 0.845f),
                    p(0.5f, 0.64f) to p(0.54f, 0.65f),
                    p(0.54f, 0.65f) to p(0.4f, 0.77f),
                    p(0.4f, 0.77f) to p(0.304f, 0.89f),
                ),
                dots = listOf(p(0.302f, 0.895f)),
            )),
            // tilbake
            Key(500, Frame(
                head = p(0.5f, 0.55f),
                lines = listOf(
                    p(0.425f, 0.845f) to p(0.455f, 0.72f),
                    p(0.455f, 0.72f) to p(0.45f, 0.935f),
                    p(0.45f, 0.935f) to p(0.5f, 0.935f),
                    p(0.5f, 0.935f) to p(0.5f, 0.64f),
                    p(0.5f, 0.64f) to p(0.415f, 0.64f),
                    p(0.415f, 0.64f) to p(0.405f, 0.73f),
                    p(0.405f, 0.73f) to p(0.5f, 0.735f),
                    p(0.5f, 0.935f) to p(0.55f, 0.935f),
                    p(0.55f, 0.935f) to p(0.545f, 0.72f),
                    p(0.545f, 0.72f) to p(0.575f, 0.845f),
                    p(0.5f, 0.64f) to p(0.585f, 0.64f),
                    p(0.585f, 0.64f) to p(0.595f, 0.73f),
                    p(0.595f, 0.73f) to p(0.504f, 0.735f),
                ),
                dots = listOf(p(0.502f, 0.74f)),
            )),
            // vri mot høyre
            Key(550, Frame(
                head = p(0.536f, 0.55f),
                lines = listOf(
                    p(0.425f, 0.845f) to p(0.455f, 0.72f),
                    p(0.455f, 0.72f) to p(0.45f, 0.935f),
                    p(0.45f, 0.935f) to p(0.5f, 0.935f),
                    p(0.5f, 0.935f) to p(0.5f, 0.64f),
                    p(0.5f, 0.64f) to p(0.46f, 0.65f),
                    p(0.46f, 0.65f) to p(0.56f, 0.77f),
                    p(0.56f, 0.77f) to p(0.7f, 0.89f),
                    p(0.5f, 0.935f) to p(0.55f, 0.935f),
                    p(0.55f, 0.935f) to p(0.545f, 0.72f),
                    p(0.545f, 0.72f) to p(0.575f, 0.845f),
                    p(0.5f, 0.64f) to p(0.6f, 0.65f),
                    p(0.6f, 0.65f) to p(0.67f, 0.77f),
                    p(0.67f, 0.77f) to p(0.704f, 0.89f),
                ),
                dots = listOf(p(0.702f, 0.895f)),
            )),
            // manualen ned ved hofta
            Key(200, Frame(
                head = p(0.536f, 0.55f),
                lines = listOf(
                    p(0.425f, 0.845f) to p(0.455f, 0.72f),
                    p(0.455f, 0.72f) to p(0.45f, 0.935f),
                    p(0.45f, 0.935f) to p(0.5f, 0.935f),
                    p(0.5f, 0.935f) to p(0.5f, 0.64f),
                    p(0.5f, 0.64f) to p(0.46f, 0.65f),
                    p(0.46f, 0.65f) to p(0.56f, 0.77f),
                    p(0.56f, 0.77f) to p(0.7f, 0.89f),
                    p(0.5f, 0.935f) to p(0.55f, 0.935f),
                    p(0.55f, 0.935f) to p(0.545f, 0.72f),
                    p(0.545f, 0.72f) to p(0.575f, 0.845f),
                    p(0.5f, 0.64f) to p(0.6f, 0.65f),
                    p(0.6f, 0.65f) to p(0.67f, 0.77f),
                    p(0.67f, 0.77f) to p(0.704f, 0.89f),
                ),
                dots = listOf(p(0.702f, 0.895f)),
            )),
            // tilbake
            Key(500, Frame(
                head = p(0.5f, 0.55f),
                lines = listOf(
                    p(0.425f, 0.845f) to p(0.455f, 0.72f),
                    p(0.455f, 0.72f) to p(0.45f, 0.935f),
                    p(0.45f, 0.935f) to p(0.5f, 0.935f),
                    p(0.5f, 0.935f) to p(0.5f, 0.64f),
                    p(0.5f, 0.64f) to p(0.415f, 0.64f),
                    p(0.415f, 0.64f) to p(0.405f, 0.73f),
                    p(0.405f, 0.73f) to p(0.5f, 0.735f),
                    p(0.5f, 0.935f) to p(0.55f, 0.935f),
                    p(0.55f, 0.935f) to p(0.545f, 0.72f),
                    p(0.545f, 0.72f) to p(0.575f, 0.845f),
                    p(0.5f, 0.64f) to p(0.585f, 0.64f),
                    p(0.585f, 0.64f) to p(0.595f, 0.73f),
                    p(0.595f, 0.73f) to p(0.504f, 0.735f),
                ),
                dots = listOf(p(0.502f, 0.74f)),
            )),
        ),
    ),
    IllustrationType.HANGING_LEG_RAISE to Pose(
        // Beinløft i hang: strake bein.
        head = p(0.6f, 0.25f),
        lines = listOf(
            p(0.5f, 0.34f) to p(0.5f, 0.08f), // armer opp til stanga (sett fra siden)
            p(0.5f, 0.34f) to p(0.48f, 0.62f), // overkropp
            p(0.48f, 0.62f) to p(0.66f, 0.56f), // strake bein løftet frem
            p(0.66f, 0.56f) to p(0.84f, 0.5f),
        ),
        props = listOf(
            p(0.2f, 0.08f) to p(0.8f, 0.08f), // tverrligger
        ),
        ground = false,
        // Hengende med strake bein.
        end = Frame(
            head = p(0.6f, 0.25f),
            lines = listOf(
                p(0.5f, 0.34f) to p(0.5f, 0.08f),
                p(0.5f, 0.34f) to p(0.48f, 0.62f),
                p(0.48f, 0.62f) to p(0.49f, 0.8f),
                p(0.49f, 0.8f) to p(0.5f, 0.97f),
            ),
        ),
    ),
    IllustrationType.BODY_SAW to Pose(
        // Kroppssag: planke på underarmene med føttene på noe som glir. Kroppen skyves bakover og frem fra skuldrene.
        head = p(0.076f, 0.812f),
        lines = listOf(
            p(0.791f, 0.955f) to p(0.606f, 0.913f),
            p(0.606f, 0.913f) to p(0.42f, 0.872f),
            p(0.42f, 0.872f) to p(0.176f, 0.817f),
            p(0.176f, 0.817f) to p(0.2f, 0.955f),
            p(0.2f, 0.955f) to p(0.08f, 0.955f),
        ),
        end = Frame(
            head = p(0.207f, 0.868f),
            lines = listOf(
                p(0.931f, 0.955f) to p(0.743f, 0.928f),
                p(0.743f, 0.928f) to p(0.554f, 0.901f),
                p(0.554f, 0.901f) to p(0.307f, 0.865f),
                p(0.307f, 0.865f) to p(0.2f, 0.955f),
                p(0.2f, 0.955f) to p(0.08f, 0.955f),
            ),
        ),
    ),

    // ------------------------------------------------------------ KONDISJON / ANNET
    IllustrationType.CARDIO_RUN to Pose(
        // Høye knær: oppreist overkropp, knærne vekselvis opp til hoftehøyde og armene i motsatt takt.
        head = p(0.535f, 0.241f),
        lines = listOf(
            p(0.46f, 0.955f) to p(0.51f, 0.772f),
            p(0.51f, 0.772f) to p(0.475f, 0.585f),
            p(0.475f, 0.585f) to p(0.518f, 0.339f),
            p(0.518f, 0.339f) to p(0.566f, 0.471f),
            p(0.566f, 0.471f) to p(0.664f, 0.402f),
            p(0.475f, 0.585f) to p(0.663f, 0.559f),
            p(0.663f, 0.559f) to p(0.67f, 0.749f),
            p(0.518f, 0.339f) to p(0.459f, 0.466f),
            p(0.459f, 0.466f) to p(0.5f, 0.579f),
        ),
        front = setOf(3, 4),
        sequence = listOf(
            // bytter
            Key(300, Frame(
                head = p(0.535f, 0.241f),
                lines = listOf(
                    p(0.51f, 0.925f) to p(0.575f, 0.746f),
                    p(0.575f, 0.746f) to p(0.475f, 0.585f),
                    p(0.475f, 0.585f) to p(0.518f, 0.339f),
                    p(0.518f, 0.339f) to p(0.459f, 0.466f),
                    p(0.459f, 0.466f) to p(0.5f, 0.579f),
                    p(0.475f, 0.585f) to p(0.51f, 0.772f),
                    p(0.51f, 0.772f) to p(0.46f, 0.955f),
                    p(0.518f, 0.339f) to p(0.566f, 0.471f),
                    p(0.566f, 0.471f) to p(0.664f, 0.402f),
                ),
            )),
            // andre kne opp
            Key(300, Frame(
                head = p(0.535f, 0.241f),
                lines = listOf(
                    p(0.67f, 0.749f) to p(0.663f, 0.559f),
                    p(0.663f, 0.559f) to p(0.475f, 0.585f),
                    p(0.475f, 0.585f) to p(0.518f, 0.339f),
                    p(0.518f, 0.339f) to p(0.459f, 0.466f),
                    p(0.459f, 0.466f) to p(0.5f, 0.579f),
                    p(0.475f, 0.585f) to p(0.51f, 0.772f),
                    p(0.51f, 0.772f) to p(0.46f, 0.955f),
                    p(0.518f, 0.339f) to p(0.566f, 0.471f),
                    p(0.566f, 0.471f) to p(0.664f, 0.402f),
                ),
            )),
            // bytter
            Key(300, Frame(
                head = p(0.535f, 0.241f),
                lines = listOf(
                    p(0.46f, 0.955f) to p(0.51f, 0.772f),
                    p(0.51f, 0.772f) to p(0.475f, 0.585f),
                    p(0.475f, 0.585f) to p(0.518f, 0.339f),
                    p(0.518f, 0.339f) to p(0.566f, 0.471f),
                    p(0.566f, 0.471f) to p(0.664f, 0.402f),
                    p(0.475f, 0.585f) to p(0.575f, 0.746f),
                    p(0.575f, 0.746f) to p(0.51f, 0.925f),
                    p(0.518f, 0.339f) to p(0.459f, 0.466f),
                    p(0.459f, 0.466f) to p(0.5f, 0.579f),
                ),
            )),
            // første kne opp
            Key(300, Frame(
                head = p(0.535f, 0.241f),
                lines = listOf(
                    p(0.46f, 0.955f) to p(0.51f, 0.772f),
                    p(0.51f, 0.772f) to p(0.475f, 0.585f),
                    p(0.475f, 0.585f) to p(0.518f, 0.339f),
                    p(0.518f, 0.339f) to p(0.566f, 0.471f),
                    p(0.566f, 0.471f) to p(0.664f, 0.402f),
                    p(0.475f, 0.585f) to p(0.663f, 0.559f),
                    p(0.663f, 0.559f) to p(0.67f, 0.749f),
                    p(0.518f, 0.339f) to p(0.459f, 0.466f),
                    p(0.459f, 0.466f) to p(0.5f, 0.579f),
                ),
            )),
        ),
    ),
    IllustrationType.JUMP to Pose(
        // Hoppknebøy: ned i knebøy med armene bak, eksplosivt opp med armene frem og opp, myk landing.
        head = p(0.597f, 0.523f),
        lines = listOf(
            p(0.5f, 0.955f) to p(0.589f, 0.787f),
            p(0.589f, 0.787f) to p(0.401f, 0.813f),
            p(0.401f, 0.813f) to p(0.541f, 0.606f),
            p(0.541f, 0.606f) to p(0.42f, 0.676f),
            p(0.42f, 0.676f) to p(0.316f, 0.736f),
        ),
        front = setOf(3, 4),
        sequence = listOf(
            // eksplosivt opp
            Key(320, Frame(
                head = p(0.499f, 0.226f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.507f, 0.765f),
                    p(0.507f, 0.765f) to p(0.489f, 0.576f),
                    p(0.489f, 0.576f) to p(0.496f, 0.326f),
                    p(0.496f, 0.326f) to p(0.634f, 0.302f),
                    p(0.634f, 0.302f) to p(0.752f, 0.281f),
                ),
            )),
            // i lufta
            Key(220, Frame(
                head = p(0.5f, 0.145f),
                lines = listOf(
                    p(0.5f, 0.875f) to p(0.5f, 0.685f),
                    p(0.5f, 0.685f) to p(0.5f, 0.495f),
                    p(0.5f, 0.495f) to p(0.5f, 0.245f),
                    p(0.5f, 0.245f) to p(0.604f, 0.151f),
                    p(0.604f, 0.151f) to p(0.693f, 0.071f),
                ),
            )),
            // toppen
            Key(120, Frame(
                head = p(0.5f, 0.145f),
                lines = listOf(
                    p(0.5f, 0.875f) to p(0.5f, 0.685f),
                    p(0.5f, 0.685f) to p(0.5f, 0.495f),
                    p(0.5f, 0.495f) to p(0.5f, 0.245f),
                    p(0.5f, 0.245f) to p(0.604f, 0.151f),
                    p(0.604f, 0.151f) to p(0.693f, 0.071f),
                ),
            )),
            // lander
            Key(280, Frame(
                head = p(0.498f, 0.268f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.532f, 0.768f),
                    p(0.532f, 0.768f) to p(0.425f, 0.611f),
                    p(0.425f, 0.611f) to p(0.477f, 0.366f),
                    p(0.477f, 0.366f) to p(0.604f, 0.425f),
                    p(0.604f, 0.425f) to p(0.713f, 0.476f),
                ),
            )),
            // ned i knebøy
            Key(450, Frame(
                head = p(0.597f, 0.523f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.589f, 0.787f),
                    p(0.589f, 0.787f) to p(0.401f, 0.813f),
                    p(0.401f, 0.813f) to p(0.541f, 0.606f),
                    p(0.541f, 0.606f) to p(0.42f, 0.676f),
                    p(0.42f, 0.676f) to p(0.316f, 0.736f),
                ),
            )),
            // klar
            Key(350, Frame(
                head = p(0.597f, 0.523f),
                lines = listOf(
                    p(0.5f, 0.955f) to p(0.589f, 0.787f),
                    p(0.589f, 0.787f) to p(0.401f, 0.813f),
                    p(0.401f, 0.813f) to p(0.541f, 0.606f),
                    p(0.541f, 0.606f) to p(0.42f, 0.676f),
                    p(0.42f, 0.676f) to p(0.316f, 0.736f),
                ),
            )),
        ),
    ),
    IllustrationType.CARRY to Pose(
        head = p(0.5f, 0.14f),
        lines = listOf(
            p(0.5f, 0.25f) to p(0.5f, 0.55f),
            p(0.5f, 0.55f) to p(0.62f, 0.75f), // fremre bein
            p(0.62f, 0.75f) to p(0.58f, 0.96f),
            p(0.5f, 0.55f) to p(0.382f, 0.751f), // bakre bein
            p(0.382f, 0.751f) to p(0.32f, 0.955f),
            p(0.5f, 0.3f) to p(0.56f, 0.62f), // arm med vekten langs siden
        ),
        dots = listOf(p(0.56f, 0.64f)),
        front = setOf(5),
        // Går: beina bytter plass.
        end = Frame(
            head = p(0.5f, 0.14f),
            lines = listOf(
                p(0.5f, 0.25f) to p(0.5f, 0.55f),
                p(0.5f, 0.55f) to p(0.382f, 0.751f),
                p(0.382f, 0.751f) to p(0.32f, 0.955f),
                p(0.5f, 0.55f) to p(0.62f, 0.75f),
                p(0.62f, 0.75f) to p(0.58f, 0.96f),
                p(0.5f, 0.3f) to p(0.56f, 0.62f),
            ),
            dots = listOf(p(0.56f, 0.64f)),
        ),
    ),
    IllustrationType.JUMP_ROPE to Pose(
        // Hoppetau sett forfra.
        head = p(0.5f, 0.21f),
        lines = listOf(
            p(0.5f, 0.3f) to p(0.5f, 0.58f), // i luften, sett forfra
            p(0.5f, 0.58f) to p(0.46f, 0.88f),
            p(0.5f, 0.58f) to p(0.54f, 0.88f),
            p(0.5f, 0.33f) to p(0.38f, 0.52f), // armer ut til siden
            p(0.5f, 0.33f) to p(0.62f, 0.52f),
        ),
        props = listOf(
            p(0.38f, 0.52f) to p(0.36f, 0.95f), // tauet under føttene
            p(0.36f, 0.95f) to p(0.64f, 0.95f),
            p(0.64f, 0.95f) to p(0.62f, 0.52f),
        ),
        // Landet, tauet over hodet.
        end = Frame(
            head = p(0.5f, 0.27f),
            lines = listOf(
                p(0.5f, 0.36f) to p(0.5f, 0.64f),
                p(0.5f, 0.64f) to p(0.46f, 0.94f),
                p(0.5f, 0.64f) to p(0.54f, 0.94f),
                p(0.5f, 0.39f) to p(0.38f, 0.58f),
                p(0.5f, 0.39f) to p(0.62f, 0.58f),
            ),
            props = listOf(
                p(0.38f, 0.58f) to p(0.34f, 0.08f),
                p(0.34f, 0.08f) to p(0.66f, 0.08f),
                p(0.66f, 0.08f) to p(0.62f, 0.58f),
            ),
        ),
    ),
    IllustrationType.BURPEE to Pose(
        // Burpee: ned i knebøy med hendene i gulvet, hopp bakover til planke, hopp inn igjen, opp og hopp med armene over hodet.
        head = p(0.838f, 0.674f),
        lines = listOf(
            p(0.166f, 0.955f) to p(0.338f, 0.874f),
            p(0.338f, 0.874f) to p(0.512f, 0.798f),
            p(0.512f, 0.798f) to p(0.74f, 0.695f),
            p(0.74f, 0.695f) to p(0.74f, 0.835f),
            p(0.74f, 0.835f) to p(0.74f, 0.955f),
        ),
        front = setOf(3, 4),
        sequence = listOf(
            // planke
            Key(250, Frame(
                head = p(0.838f, 0.674f),
                lines = listOf(
                    p(0.166f, 0.955f) to p(0.338f, 0.874f),
                    p(0.338f, 0.874f) to p(0.512f, 0.798f),
                    p(0.512f, 0.798f) to p(0.74f, 0.695f),
                    p(0.74f, 0.695f) to p(0.74f, 0.835f),
                    p(0.74f, 0.835f) to p(0.74f, 0.955f),
                ),
            )),
            // hopper inn igjen
            Key(280, Frame(
                head = p(0.798f, 0.7f),
                lines = listOf(
                    p(0.6f, 0.955f) to p(0.658f, 0.774f),
                    p(0.658f, 0.774f) to p(0.47f, 0.8f),
                    p(0.47f, 0.8f) to p(0.698f, 0.698f),
                    p(0.698f, 0.698f) to p(0.721f, 0.836f),
                    p(0.721f, 0.836f) to p(0.74f, 0.955f),
                ),
            )),
            // reiser seg
            Key(320, Frame(
                head = p(0.6f, 0.22f),
                lines = listOf(
                    p(0.6f, 0.955f) to p(0.6f, 0.762f),
                    p(0.6f, 0.762f) to p(0.6f, 0.57f),
                    p(0.6f, 0.57f) to p(0.6f, 0.32f),
                    p(0.6f, 0.32f) to p(0.67f, 0.199f),
                    p(0.67f, 0.199f) to p(0.711f, 0.086f),
                ),
            )),
            // hopper
            Key(220, Frame(
                head = p(0.6f, 0.155f),
                lines = listOf(
                    p(0.58f, 0.885f) to p(0.593f, 0.695f),
                    p(0.593f, 0.695f) to p(0.6f, 0.505f),
                    p(0.6f, 0.505f) to p(0.6f, 0.255f),
                    p(0.6f, 0.255f) to p(0.68f, 0.14f),
                    p(0.68f, 0.14f) to p(0.74f, 0.036f),
                ),
            )),
            // lander
            Key(280, Frame(
                head = p(0.6f, 0.225f),
                lines = listOf(
                    p(0.6f, 0.955f) to p(0.6f, 0.765f),
                    p(0.6f, 0.765f) to p(0.6f, 0.575f),
                    p(0.6f, 0.575f) to p(0.6f, 0.325f),
                    p(0.6f, 0.325f) to p(0.595f, 0.465f),
                    p(0.595f, 0.465f) to p(0.591f, 0.585f),
                ),
            )),
            // står
            Key(300, Frame(
                head = p(0.6f, 0.225f),
                lines = listOf(
                    p(0.6f, 0.955f) to p(0.6f, 0.765f),
                    p(0.6f, 0.765f) to p(0.6f, 0.575f),
                    p(0.6f, 0.575f) to p(0.6f, 0.325f),
                    p(0.6f, 0.325f) to p(0.595f, 0.465f),
                    p(0.595f, 0.465f) to p(0.591f, 0.585f),
                ),
            )),
            // ned med hendene i gulvet
            Key(400, Frame(
                head = p(0.798f, 0.7f),
                lines = listOf(
                    p(0.6f, 0.955f) to p(0.658f, 0.774f),
                    p(0.658f, 0.774f) to p(0.47f, 0.8f),
                    p(0.47f, 0.8f) to p(0.698f, 0.698f),
                    p(0.698f, 0.698f) to p(0.721f, 0.836f),
                    p(0.721f, 0.836f) to p(0.74f, 0.955f),
                ),
            )),
            // hopper bakover til planke
            Key(280, Frame(
                head = p(0.838f, 0.674f),
                lines = listOf(
                    p(0.166f, 0.955f) to p(0.338f, 0.874f),
                    p(0.338f, 0.874f) to p(0.512f, 0.798f),
                    p(0.512f, 0.798f) to p(0.74f, 0.695f),
                    p(0.74f, 0.695f) to p(0.74f, 0.835f),
                    p(0.74f, 0.835f) to p(0.74f, 0.955f),
                ),
            )),
        ),
    ),
)

private fun poseFor(type: IllustrationType): Pose = POSES.getValue(type)

/** Kobler en øvelse-id til riktig illustrasjonstype, med fall-back basert på bevegelsesmønster. */
fun illustrationFor(exercise: Exercise): IllustrationType = when (exercise.id) {
    "db_bench", "bb_bench" -> IllustrationType.BENCH_PRESS
    "db_floor_press", "db_floor_press_1arm" -> IllustrationType.FLOOR_PRESS
    "db_shoulder_press" -> IllustrationType.OVERHEAD_PRESS
    "kb_press" -> IllustrationType.OVERHEAD_PRESS_1ARM
    "db_triceps_ext" -> IllustrationType.TRICEPS_EXT
    "thruster_db" -> IllustrationType.THRUSTER
    "band_press", "pallof_press", "band_face_pull" -> IllustrationType.FORWARD_PRESS
    "pushup" -> IllustrationType.PUSHUP
    "pushup_incline" -> IllustrationType.PUSHUP_INCLINE
    "pushup_decline" -> IllustrationType.PUSHUP_DECLINE
    "pushup_diamond" -> IllustrationType.PUSHUP_DIAMOND
    "pushup_archer" -> IllustrationType.PUSHUP_ARCHER
    "pushup_band" -> IllustrationType.PUSHUP_BAND
    "pike_pushup" -> IllustrationType.PIKE
    "dips_chair" -> IllustrationType.DIP
    "band_triceps_pushdown" -> IllustrationType.PUSHDOWN
    "db_lateral_raise" -> IllustrationType.LATERAL_RAISE
    "band_lateral_raise" -> IllustrationType.LATERAL_RAISE_BAND
    "pullup", "negative_pullup" -> IllustrationType.PULLUP_HANG
    "chinup" -> IllustrationType.CHINUP
    "pullup_band_assist" -> IllustrationType.PULLUP_BAND
    "dead_hang" -> IllustrationType.DEAD_HANG
    "band_pulldown" -> IllustrationType.PULLDOWN
    "table_row", "trx_row" -> IllustrationType.INVERTED_ROW
    "band_row" -> IllustrationType.SEATED_ROW
    "db_rear_fly" -> IllustrationType.ROW
    "db_curl" -> IllustrationType.CURL
    "band_curl" -> IllustrationType.CURL_BAND
    "superman" -> IllustrationType.SUPERMAN
    "air_squat", "tempo_squat" -> IllustrationType.SQUAT
    "goblet_squat" -> IllustrationType.GOBLET_SQUAT
    "db_front_squat" -> IllustrationType.FRONT_SQUAT
    "bb_squat" -> IllustrationType.BB_SQUAT
    "band_squat" -> IllustrationType.BAND_SQUAT
    "pistol_box" -> IllustrationType.PISTOL_BOX
    "wall_sit" -> IllustrationType.WALL_SIT
    "db_lunge" -> IllustrationType.LUNGE_DB
    "bulgarian" -> IllustrationType.BULGARIAN
    "db_bulgarian" -> IllustrationType.BULGARIAN_DB
    "step_up" -> IllustrationType.STEP_UP
    "walking_lunge" -> IllustrationType.WALKING_LUNGE
    "lateral_lunge" -> IllustrationType.LATERAL_LUNGE
    "glute_bridge", "hamstring_slide" -> IllustrationType.GLUTE_BRIDGE
    "glute_bridge_1leg" -> IllustrationType.GLUTE_BRIDGE_1LEG
    "hip_thrust_db" -> IllustrationType.HIP_THRUST
    "rdl_1leg_db" -> IllustrationType.SINGLE_LEG_RDL
    "kb_swing", "kb_swing_cardio" -> IllustrationType.KB_SWING
    "good_morning_band" -> IllustrationType.GOOD_MORNING
    "band_pullthrough" -> IllustrationType.PULL_THROUGH
    "calf_raise" -> IllustrationType.CALF_RAISE
    "calf_raise_db" -> IllustrationType.CALF_RAISE_DB
    "side_plank" -> IllustrationType.SIDE_PLANK
    "bird_dog" -> IllustrationType.BIRD_DOG
    "dead_bug" -> IllustrationType.DEAD_BUG
    "hollow_hold" -> IllustrationType.HOLLOW_HOLD
    "leg_raise" -> IllustrationType.LEG_RAISE
    "hanging_knee_raise" -> IllustrationType.HANGING_RAISE
    "hanging_leg_raise" -> IllustrationType.HANGING_LEG_RAISE
    "russian_twist_db" -> IllustrationType.SEATED_TWIST
    "ab_wheel_alt" -> IllustrationType.BODY_SAW
    "suitcase_carry" -> IllustrationType.CARRY
    "mountain_climber" -> IllustrationType.MOUNTAIN_CLIMBER
    "jump_rope" -> IllustrationType.JUMP_ROPE
    "jump_squat" -> IllustrationType.JUMP
    "burpee" -> IllustrationType.BURPEE
    "box_jump" -> IllustrationType.BOX_JUMP
    "high_knees" -> IllustrationType.CARDIO_RUN
    else -> when (exercise.pattern) {
        Pattern.PUSH -> IllustrationType.PUSHUP
        Pattern.PULL -> IllustrationType.ROW
        Pattern.SQUAT -> IllustrationType.SQUAT
        Pattern.LUNGE -> IllustrationType.LUNGE
        Pattern.HINGE -> IllustrationType.HIP_HINGE
        Pattern.CORE -> IllustrationType.PLANK
        Pattern.CARDIO -> IllustrationType.CARDIO_RUN
    }
}

// ---------------------------------------------------------------- ANIMASJON
//
// Figuren beveges mellom startposen (A) og sluttposisjonen (B) slik:
// 1. Hvert lem roterer rundt leddet sitt (vinkel og lengde glir), i stedet for at endepunktene glir
//    i rett linje. Ellers ville armer og bein krympe midt i bevegelsen.
// 2. Punkter som står likt i A og B (hender på stanga, føtter i gulvet) festes, og albuen/kneet
//    mellom regnes ut med to-ledds IK så lemmene når frem.
// 3. Ingenting går under gulvet.
// Forhåndsvisningene (GIF-ene) ble laget med en Python-kopi av nøyaktig denne algoritmen.

private const val FLOOR_Y = 0.965f

private class Solved(
    val head: Offset,
    val lines: List<Pair<Offset, Offset>>,
    val dots: List<Offset>,
    val props: List<Pair<Offset, Offset>>,
)

private fun lerpAngle(a: Float, b: Float, k: Float): Float {
    var d = b - a
    val twoPi = (2 * PI).toFloat()
    while (d > PI) d -= twoPi
    while (d < -PI) d += twoPi
    return a + d * k
}

/** Plasserer punktet i den andre enden av et lem, målt fra [anchorNow], med interpolert vinkel og lengde. */
private fun bone(anchorNow: Offset, fromA: Offset, toA: Offset, fromB: Offset, toB: Offset, k: Float): Offset {
    val va = toA - fromA
    val vb = toB - fromB
    val angle = lerpAngle(atan2(va.y, va.x), atan2(vb.y, vb.x), k)
    val la = va.getDistance()
    val length = la + (vb.getDistance() - la) * k
    return Offset(anchorNow.x + cos(angle) * length, anchorNow.y + sin(angle) * length)
}

/** Leddet mellom [p] og [f] når lemmene har lengde [l1] og [l2]. [side] (+1/-1) velger bøyeretning. */
private fun twoBone(p: Offset, f: Offset, l1: Float, l2: Float, side: Float): Offset {
    val d = (f - p).getDistance().coerceAtLeast(1e-4f)
    val ux = (f.x - p.x) / d
    val uy = (f.y - p.y) / d
    if (d >= l1 + l2) return Offset(p.x + ux * d * l1 / (l1 + l2), p.y + uy * d * l1 / (l1 + l2))
    val a = (l1 * l1 - l2 * l2 + d * d) / (2 * d)
    val h = sqrt((l1 * l1 - a * a).coerceAtLeast(0f))
    return Offset(p.x + ux * a - uy * h * side, p.y + uy * a + ux * h * side)
}

/** Hvor mye og hvilken vei leddet [j] er bøyd på linjen [p]-[f] (normalisert kryssprodukt, ca. sinus til vinkelen). */
private fun bend(p: Offset, j: Offset, f: Offset): Float {
    val c = (f.x - p.x) * (j.y - p.y) - (f.y - p.y) * (j.x - p.x)
    return c / ((f - p).getDistance() * (j - p).getDistance()).coerceAtLeast(1e-9f)
}

private fun solvePose(pose: Pose, end: Frame, k: Float): Solved {
    val a = pose.lines
    val b = end.lines
    val n = a.size
    // Punkt i A -> samme punkt i B (et ledd som deles av flere linjer har én oppføring).
    val a2b = LinkedHashMap<Offset, Offset>()
    for (i in 0 until n) {
        a2b[a[i].first] = b[i].first
        a2b[a[i].second] = b[i].second
    }
    val fixed = a2b.filter { (pa, pb) -> pa == pb }.keys.toList()
    val placed = HashMap<Offset, Offset>()
    fixed.firstOrNull()?.let { placed[it] = it } // roten: et punkt som står i ro

    val done = BooleanArray(n)
    var remaining = n
    while (remaining > 0) {
        var progressed = false
        for (i in 0 until n) {
            if (done[i]) continue
            val (a0, b0) = a[i]
            val (a1, b1) = b[i]
            val pa = placed[a0]
            val pb = placed[b0]
            when {
                pa != null -> if (pb == null) placed[b0] = bone(pa, a0, b0, a1, b1, k)
                pb != null -> placed[a0] = bone(pb, b0, a0, b1, a1, k)
                else -> continue
            }
            done[i] = true
            remaining--
            progressed = true
        }
        if (!progressed) {
            // En del som ikke henger sammen med resten (f.eks. en pil): start den der den skal være.
            val i = done.indexOfFirst { !it }
            placed[a[i].first] = lerp(a[i].first, b[i].first, k)
        }
    }

    // Fest faste punkter med to-ledds IK: f (fast) - j (albue/kne) - p (resten av kroppen).
    for (f in fixed.drop(1)) {
        if ((placed.getValue(f) - f).getDistance() < 1e-6f) continue
        val seg = a.firstOrNull { it.first == f || it.second == f } ?: continue
        val j = if (seg.first == f) seg.second else seg.first
        if (j in fixed) continue
        val seg2 = a.firstOrNull { (it.first == j || it.second == j) && it.first != f && it.second != f } ?: continue
        val p = if (seg2.first == j) seg2.second else seg2.first
        val l1a = (p - j).getDistance()
        val l2a = (j - f).getDistance()
        val l1 = l1a + ((a2b.getValue(p) - a2b.getValue(j)).getDistance() - l1a) * k
        val l2 = l2a + ((a2b.getValue(j) - a2b.getValue(f)).getDistance() - l2a) * k
        // Bøyeretning fra den stillingen der lemmet er mest bøyd (en nesten strak arm sier lite om retningen).
        val bendA = bend(p, j, f)
        val bendB = bend(a2b.getValue(p), a2b.getValue(j), a2b.getValue(f))
        val chosen = if (abs(bendA) >= abs(bendB)) bendA else bendB
        placed[j] = twoBone(placed.getValue(p), f, l1, l2, if (chosen >= 0f) 1f else -1f)
        placed[f] = f
    }

    if (pose.ground) {
        for (key in placed.keys.toList()) {
            val q = placed.getValue(key)
            if (q.y > FLOOR_Y) placed[key] = Offset(q.x, FLOOR_Y)
        }
    }

    val lines = a.map { (a0, b0) -> placed.getValue(a0) to placed.getValue(b0) }
    val endpoints = a2b.keys.toList()
    fun attached(p0: Offset, p1: Offset): Offset {
        val anchor = endpoints.minBy { (it - p0).getDistance() }
        return bone(placed.getValue(anchor), anchor, p0, a2b.getValue(anchor), p1, k)
    }
    // Hodet følger nakken: festepunktet som har samme avstand til hodet i begge stillinger (og er nærmest),
    // ikke bare det nærmeste. Ellers kan hodet henge på en albue eller hånd som svinger forbi (dead bug, thrusters).
    val headAnchor = endpoints.minBy {
        val d0 = (it - pose.head).getDistance()
        val d1 = (a2b.getValue(it) - end.head).getDistance()
        d0 + d1 + 3f * abs(d0 - d1)
    }
    val head = bone(placed.getValue(headAnchor), headAnchor, pose.head, a2b.getValue(headAnchor), end.head, k)
    val dots = if (end.dots.isNotEmpty() && end.dots.size == pose.dots.size) {
        pose.dots.zip(end.dots) { d0, d1 -> attached(d0, d1) }
    } else {
        pose.dots
    }
    val props = if (end.props.isNotEmpty() && end.props.size == pose.props.size) {
        // Punkter som står likt i begge stillinger (festepunkt for strikk) står i ro.
        // Stanga følger skivene (samme punkt som en vekt); annet utstyr som beveger seg, som hoppetauet, glir.
        fun propPoint(p0: Offset, p1: Offset): Offset = placed[p0] ?: when {
            p0 == p1 -> p0
            p0 in pose.dots -> attached(p0, p1)
            else -> lerp(p0, p1, k)
        }
        pose.props.zip(end.props) { (a0, b0), (a1, b1) -> propPoint(a0, a1) to propPoint(b0, b1) }
    } else {
        pose.props
    }
    return Solved(head, lines, dots, props)
}

private fun Pose.withFrame(f: Frame): Pose = copy(
    head = f.head,
    lines = f.lines,
    dots = if (f.dots.size == dots.size) f.dots else dots,
    props = if (f.props.size == props.size) f.props else props,
)

/** Hvor synlig figuren er ved [timeMs]: glir mellom nøkkelstillingenes [Key.alpha] (1 før første nøkkel). */
private fun sequenceAlpha(keys: List<Key>, timeMs: Float): Float {
    var start = 0f
    var from = 1f
    for ((index, key) in keys.withIndex()) {
        val end = start + key.ms
        if (timeMs < end || index == keys.lastIndex) {
            val u = ((timeMs - start) / key.ms.coerceAtLeast(1)).coerceIn(0f, 1f)
            val eased = u * u * (3 - 2 * u)
            return (from + (key.alpha - from) * eased).coerceIn(0f, 1f)
        }
        start = end
        from = key.alpha
    }
    return 1f
}

/** Finner hvor i sekvensen [timeMs] er, og løser figuren mellom de to nærmeste nøkkelstillingene. */
private fun solveSequence(pose: Pose, keys: List<Key>, timeMs: Float): Solved {
    var start = 0f
    var from = Frame(pose.head, pose.lines, pose.dots, pose.props)
    for ((index, key) in keys.withIndex()) {
        val end = start + key.ms
        if (timeMs < end || index == keys.lastIndex) {
            val u = ((timeMs - start) / key.ms.coerceAtLeast(1)).coerceIn(0f, 1f)
            val eased = u * u * (3 - 2 * u) // myk start og stopp i hvert steg
            val fromPose = pose.withFrame(from)
            return if (from == key.frame || eased <= 0f) {
                Solved(fromPose.head, fromPose.lines, fromPose.dots, fromPose.props)
            } else {
                solvePose(fromPose, key.frame, eased)
            }
        }
        start = end
        from = key.frame
    }
    return Solved(pose.head, pose.lines, pose.dots, pose.props)
}

/**
 * Tegner strekfiguren for en øvelse. Med [animate] = true beveger figuren seg rolig frem og tilbake
 * mellom de to ytterstillingene (bare for poser som har en sluttposisjon). Brukes i detaljvisningen;
 * listene bruker stillbilder så skjermen ikke blir urolig.
 */
@Composable
fun ExerciseIllustration(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = false,
) {
    val pose = poseFor(illustrationFor(exercise))
    // Sperre: passer ikke sluttposisjonen til posen (feil antall linjer), står figuren heller stille.
    val end = pose.end?.takeIf { it.lines.size == pose.lines.size && pose.lines.isNotEmpty() }
    val sequence = pose.sequence.takeIf { keys ->
        keys.isNotEmpty() && pose.lines.isNotEmpty() && keys.all { it.frame.lines.size == pose.lines.size }
    }
    val cycleMs = sequence?.sumOf { it.ms } ?: REP_MS
    val phase = if (animate && (end != null || sequence != null)) {
        val transition = rememberInfiniteTransition(label = "ovelse")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(cycleMs, easing = LinearEasing), RepeatMode.Restart),
            label = "fase",
        )
        t
    } else {
        0f
    }
    // 0 -> 1 -> 0 med myk oppbremsing i begge ytterstillinger, som en rolig repetisjon.
    val k = ((1 - cos(2 * PI * phase)) / 2).toFloat()
    val solved = when {
        sequence != null && phase > 0f -> solveSequence(pose, sequence, phase * cycleMs)
        end != null && k > 0f -> solvePose(pose, end, k)
        else -> Solved(pose.head, pose.lines, pose.dots, pose.props)
    }
    val figureAlpha = if (sequence != null && phase > 0f) sequenceAlpha(sequence, phase * cycleMs) else 1f
    val head = solved.head
    val lines = solved.lines
    val dots = solved.dots
    val props = solved.props

    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.07f
        fun toPx(o: Offset) = Offset(o.x * w, o.y * h)

        if (pose.ground) {
            drawLine(
                color = strokeColor.copy(alpha = 0.25f),
                start = toPx(p(0.04f, 0.97f)),
                end = toPx(p(0.96f, 0.97f)),
                strokeWidth = stroke * 0.5f,
                cap = StrokeCap.Round,
            )
        }
        if (figureAlpha <= 0f) return@Canvas
        // Toning: figuren (og utstyret som følger den) tegnes i et eget lag med gjennomsiktighet. Gulvet står fast.
        val faded = figureAlpha < 1f
        if (faded) drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = figureAlpha })
        fun drawProps() = props.forEach { (a, b) ->
            drawLine(
                color = strokeColor.copy(alpha = 0.45f),
                start = toPx(a),
                end = toPx(b),
                strokeWidth = stroke * 0.75f,
                cap = StrokeCap.Round,
            )
        }
        // Utstyret tegnes etter luftkanten når figuren har armer foran, så strikk og stang ikke klippes bort.
        if (pose.front.isEmpty()) drawProps()
        lines.forEachIndexed { i, (a, b) ->
            if (i !in pose.front) {
                drawLine(color = strokeColor, start = toPx(a), end = toPx(b), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        // Luftkant: klipp et smalt hull rundt armene i det som allerede er tegnet (kroppen bak), og tegn så
        // armene oppå. Starter 30 % ned på armen så skulderleddet henger sammen med kroppen.
        pose.front.forEach { i ->
            val (a, b) = lines[i]
            drawLine(
                color = Color.Black,
                start = toPx(lerp(a, b, 0.3f)),
                end = toPx(b),
                strokeWidth = stroke * 1.9f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Clear,
            )
        }
        // Hodet etter luftkanten (så det ikke klippes) men før armene foran, så en arm som går foran ansiktet synes.
        drawCircle(color = strokeColor, radius = pose.headRadius * size.minDimension, center = toPx(head))
        if (pose.front.isNotEmpty()) drawProps()
        pose.front.forEach { i ->
            val (a, b) = lines[i]
            drawLine(color = strokeColor, start = toPx(a), end = toPx(b), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        dots.forEach { d ->
            drawCircle(color = accentColor, radius = stroke * 1.1f, center = toPx(d))
        }
        if (faded) drawContext.canvas.restore()
    }
}

/** Liten sirkulær thumbnail med illustrasjonen, til bruk i lister. */
@Composable
fun ExerciseThumbnail(exercise: Exercise, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
    ) {
        ExerciseIllustration(
            exercise = exercise,
            modifier = Modifier
                .size(size)
                .padding(6.dp),
        )
    }
}
