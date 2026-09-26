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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    PIKE, OVERHEAD_PRESS_1ARM, LATERAL_RAISE_BAND, TRICEPS_EXT, THRUSTER, ROW, INVERTED_ROW, SEATED_ROW,
    PULLUP_HANG, PULLDOWN, CURL, CURL_BAND, DEAD_HANG, PULLUP_BAND, SQUAT, WALL_SIT, LUNGE, BULGARIAN,
    STEP_UP, LUNGE_DB, BULGARIAN_DB, GOBLET_SQUAT, FRONT_SQUAT, BB_SQUAT, BAND_SQUAT, PISTOL_BOX,
    HIP_HINGE, GLUTE_BRIDGE, CALF_RAISE, SUPERMAN, CALF_RAISE_DB, GLUTE_BRIDGE_1LEG, HIP_THRUST,
    KB_SWING, GOOD_MORNING, PULL_THROUGH, SINGLE_LEG_RDL, PLANK, SIDE_PLANK, MOUNTAIN_CLIMBER, BIRD_DOG,
    DEAD_BUG, HOLLOW_HOLD, LEG_RAISE, HANGING_RAISE, SEATED_TWIST, HANGING_LEG_RAISE, CARDIO_RUN,
    JUMP, CARRY, JUMP_ROPE,
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

/** Én repetisjon (ned og opp igjen) i millisekunder. */
private const val REP_MS = 2200

private fun p(x: Float, y: Float) = Offset(x, y)

private val POSES: Map<IllustrationType, Pose> = mapOf(
    // ------------------------------------------------------------ PRESS
    IllustrationType.PUSHUP to Pose(
        head = p(0.1f, 0.58f),
        lines = listOf(
            p(0.18f, 0.64f) to p(0.58f, 0.74f), // overkropp
            p(0.58f, 0.74f) to p(0.92f, 0.84f), // bein til tærne
            p(0.18f, 0.64f) to p(0.2f, 0.78f), // overarm (nesten strak, med en anelse bøy samme vei som albuen går nede)
            p(0.2f, 0.78f) to p(0.2f, 0.92f), // underarm ned til hånden
        ),
        // Nede: kroppen vipper rundt tærne, albuene går bakover. Hender og tær står i ro.
        end = Frame(
            head = p(0.12f, 0.74f),
            lines = listOf(
                p(0.2f, 0.8f) to p(0.58f, 0.82f),
                p(0.58f, 0.82f) to p(0.92f, 0.84f),
                p(0.2f, 0.8f) to p(0.326f, 0.86f),
                p(0.326f, 0.86f) to p(0.2f, 0.92f),
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
        head = p(0.78f, 0.42f),
        lines = listOf(
            p(0.1f, 0.9f) to p(0.48f, 0.14f), // bein opp til hoften
            p(0.48f, 0.14f) to p(0.7f, 0.5f), // overkropp ned
            p(0.7f, 0.5f) to p(0.78f, 0.7f), // arm ned til hånden
            p(0.78f, 0.7f) to p(0.86f, 0.9f),
        ),
        // Nede: hodet mot gulvet mellom hendene, albuene bøyd.
        end = Frame(
            head = p(0.635f, 0.708f),
            lines = listOf(
                p(0.1f, 0.9f) to p(0.5f, 0.2f),
                p(0.5f, 0.2f) to p(0.605f, 0.608f),
                p(0.605f, 0.608f) to p(0.804f, 0.692f),
                p(0.804f, 0.692f) to p(0.86f, 0.9f),
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
        // Triceps-fransk: én manual holdt med begge hender over hodet, sett forfra.
        head = p(0.5f, 0.33f),
        lines = listOf(
            p(0.5f, 0.42f) to p(0.5f, 0.7f), // sett forfra
            p(0.5f, 0.7f) to p(0.44f, 0.96f),
            p(0.5f, 0.7f) to p(0.56f, 0.96f),
            p(0.5f, 0.45f) to p(0.36f, 0.25f), // albuene opp på hver side av hodet
            p(0.36f, 0.25f) to p(0.48f, 0.1f), // hendene sammen om manualen
            p(0.5f, 0.45f) to p(0.64f, 0.25f),
            p(0.64f, 0.25f) to p(0.52f, 0.1f),
        ),
        dots = listOf(p(0.5f, 0.07f)),
        // Manualen senket bak hodet (stikker så vidt opp). Albuene står i ro.
        end = Frame(
            head = p(0.5f, 0.33f),
            lines = listOf(
                p(0.5f, 0.42f) to p(0.5f, 0.7f),
                p(0.5f, 0.7f) to p(0.44f, 0.96f),
                p(0.5f, 0.7f) to p(0.56f, 0.96f),
                p(0.5f, 0.45f) to p(0.36f, 0.25f),
                p(0.36f, 0.25f) to p(0.45f, 0.42f),
                p(0.5f, 0.45f) to p(0.64f, 0.25f),
                p(0.64f, 0.25f) to p(0.55f, 0.42f),
            ),
            dots = listOf(p(0.5f, 0.17f)),
        ),
    ),
    IllustrationType.THRUSTER to Pose(
        // Thruster: knebøy med manualene foran skuldrene. Figuren er mindre så den får plass med armene over hodet.
        head = p(0.596f, 0.528f),
        lines = listOf(
            p(0.539f, 0.597f) to p(0.41f, 0.75f), // nede i knebøy
            p(0.41f, 0.75f) to p(0.61f, 0.77f),
            p(0.61f, 0.77f) to p(0.5f, 0.96f),
            p(0.539f, 0.597f) to p(0.6f, 0.68f), // manualene foran skuldrene
            p(0.6f, 0.68f) to p(0.68f, 0.62f),
        ),
        dots = listOf(p(0.705f, 0.61f)),
        // Opp fra knebøyen og rett videre til press over hodet.
        end = Frame(
            head = p(0.5f, 0.23f),
            lines = listOf(
                p(0.5f, 0.32f) to p(0.5f, 0.52f),
                p(0.5f, 0.52f) to p(0.5f, 0.74f),
                p(0.5f, 0.74f) to p(0.5f, 0.96f),
                p(0.5f, 0.32f) to p(0.58f, 0.23f),
                p(0.58f, 0.23f) to p(0.6f, 0.116f),
            ),
            dots = listOf(p(0.6f, 0.09f)),
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
        // Hengende, sett forfra. Stanga er senket litt så haken får plass over den i toppen.
        head = p(0.5f, 0.36f),
        lines = listOf(
            p(0.5f, 0.46f) to p(0.5f, 0.68f), // overkropp
            p(0.5f, 0.68f) to p(0.46f, 0.95f), // hengende bein
            p(0.5f, 0.68f) to p(0.54f, 0.95f),
            p(0.5f, 0.48f) to p(0.37f, 0.31f), // armer i V opp til stanga
            p(0.37f, 0.31f) to p(0.3f, 0.16f),
            p(0.5f, 0.48f) to p(0.63f, 0.31f),
            p(0.63f, 0.31f) to p(0.7f, 0.16f),
        ),
        props = listOf(
            p(0.1f, 0.16f) to p(0.9f, 0.16f), // tverrligger
        ),
        ground = false,
        // Toppen: haken over stanga, albuene ut og ned. Hendene står i ro på stanga.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.22f) to p(0.5f, 0.46f),
                p(0.5f, 0.46f) to p(0.46f, 0.73f),
                p(0.5f, 0.46f) to p(0.54f, 0.73f),
                p(0.5f, 0.24f) to p(0.331f, 0.323f),
                p(0.331f, 0.323f) to p(0.3f, 0.16f),
                p(0.5f, 0.24f) to p(0.669f, 0.323f),
                p(0.669f, 0.323f) to p(0.7f, 0.16f),
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
        head = p(0.44f, 0.16f),
        lines = listOf(
            p(0.44f, 0.27f) to p(0.44f, 0.58f),
            p(0.44f, 0.58f) to p(0.38f, 0.96f),
            p(0.44f, 0.58f) to p(0.52f, 0.96f),
            p(0.44f, 0.32f) to p(0.47f, 0.5f), // albuen inntil siden
            p(0.47f, 0.5f) to p(0.68f, 0.38f), // underarm curlet opp
        ),
        dots = listOf(p(0.71f, 0.36f)),
        // Armen strakt ned. Albuen står i ro.
        end = Frame(
            head = p(0.44f, 0.16f),
            lines = listOf(
                p(0.44f, 0.27f) to p(0.44f, 0.58f),
                p(0.44f, 0.58f) to p(0.38f, 0.96f),
                p(0.44f, 0.58f) to p(0.52f, 0.96f),
                p(0.44f, 0.32f) to p(0.47f, 0.5f),
                p(0.47f, 0.5f) to p(0.5f, 0.74f),
            ),
            dots = listOf(p(0.505f, 0.77f)),
        ),
    ),
    IllustrationType.CURL_BAND to Pose(
        // Med strikk i stedet for manual.
        head = p(0.44f, 0.16f),
        lines = listOf(
            p(0.44f, 0.27f) to p(0.44f, 0.58f),
            p(0.44f, 0.58f) to p(0.38f, 0.96f),
            p(0.44f, 0.58f) to p(0.52f, 0.96f),
            p(0.44f, 0.32f) to p(0.47f, 0.5f), // albuen inntil siden
            p(0.47f, 0.5f) to p(0.68f, 0.38f), // underarm curlet opp
        ),
        props = listOf(
            p(0.68f, 0.38f) to p(0.52f, 0.96f), // strikk fra hånden ned under foten
        ),
        // Armen strakt ned. Albuen står i ro.
        end = Frame(
            head = p(0.44f, 0.16f),
            lines = listOf(
                p(0.44f, 0.27f) to p(0.44f, 0.58f),
                p(0.44f, 0.58f) to p(0.38f, 0.96f),
                p(0.44f, 0.58f) to p(0.52f, 0.96f),
                p(0.44f, 0.32f) to p(0.47f, 0.5f),
                p(0.47f, 0.5f) to p(0.5f, 0.74f),
            ),
            props = listOf(
                p(0.5f, 0.74f) to p(0.52f, 0.96f),
            ),
        ),
    ),
    IllustrationType.DEAD_HANG to Pose(
        // Hang i tverrligger: står stille.
        head = p(0.5f, 0.36f),
        lines = listOf(
            p(0.5f, 0.46f) to p(0.5f, 0.68f), // overkropp
            p(0.5f, 0.68f) to p(0.46f, 0.95f), // hengende bein
            p(0.5f, 0.68f) to p(0.54f, 0.95f),
            p(0.5f, 0.48f) to p(0.37f, 0.31f), // armer i V opp til stanga
            p(0.37f, 0.31f) to p(0.3f, 0.16f),
            p(0.5f, 0.48f) to p(0.63f, 0.31f),
            p(0.63f, 0.31f) to p(0.7f, 0.16f),
        ),
        props = listOf(
            p(0.1f, 0.16f) to p(0.9f, 0.16f), // tverrligger
        ),
        ground = false,
    ),
    IllustrationType.PULLUP_BAND to Pose(
        // Assistert pull-up: strikken fra stanga ned under føttene.
        head = p(0.5f, 0.36f),
        lines = listOf(
            p(0.5f, 0.46f) to p(0.5f, 0.68f), // overkropp
            p(0.5f, 0.68f) to p(0.46f, 0.95f), // hengende bein
            p(0.5f, 0.68f) to p(0.54f, 0.95f),
            p(0.5f, 0.48f) to p(0.37f, 0.31f), // armer i V opp til stanga
            p(0.37f, 0.31f) to p(0.3f, 0.16f),
            p(0.5f, 0.48f) to p(0.63f, 0.31f),
            p(0.63f, 0.31f) to p(0.7f, 0.16f),
        ),
        props = listOf(
            p(0.1f, 0.16f) to p(0.9f, 0.16f), // tverrligger
            p(0.47f, 0.16f) to p(0.46f, 0.95f), // strikk fra stanga ned under føttene
            p(0.53f, 0.16f) to p(0.54f, 0.95f),
        ),
        ground = false,
        // Toppen: haken over stanga, albuene ut og ned. Hendene står i ro på stanga.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.22f) to p(0.5f, 0.46f),
                p(0.5f, 0.46f) to p(0.46f, 0.73f),
                p(0.5f, 0.46f) to p(0.54f, 0.73f),
                p(0.5f, 0.24f) to p(0.331f, 0.323f),
                p(0.331f, 0.323f) to p(0.3f, 0.16f),
                p(0.5f, 0.24f) to p(0.669f, 0.323f),
                p(0.669f, 0.323f) to p(0.7f, 0.16f),
            ),
            props = listOf(
                p(0.1f, 0.16f) to p(0.9f, 0.16f),
                p(0.47f, 0.16f) to p(0.46f, 0.73f),
                p(0.53f, 0.16f) to p(0.54f, 0.73f),
            ),
        ),
    ),

    // ------------------------------------------------------------ BEIN
    IllustrationType.SQUAT to Pose(
        // Nederst i knebøyen. Figuren er litt mindre enn før, fordi den også må få plass stående.
        head = p(0.61f, 0.44f),
        lines = listOf(
            p(0.544f, 0.516f) to p(0.39f, 0.7f), // overkropp lent frem
            p(0.39f, 0.7f) to p(0.635f, 0.726f), // lår nesten vannrett
            p(0.635f, 0.726f) to p(0.5f, 0.96f), // legg, kneet frem over tærne
            p(0.544f, 0.52f) to p(0.8f, 0.54f), // armer frem for balanse
        ),
        // Stående. Føttene står i ro.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.2f) to p(0.5f, 0.44f),
                p(0.5f, 0.44f) to p(0.5f, 0.69f),
                p(0.5f, 0.69f) to p(0.5f, 0.96f),
                p(0.5f, 0.21f) to p(0.756f, 0.23f),
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
            p(0.44f, 0.24f) to p(0.44f, 0.5f), // overkropp
            p(0.44f, 0.5f) to p(0.56f, 0.7f), // fremre bein bøyd
            p(0.56f, 0.7f) to p(0.5f, 0.96f),
            p(0.44f, 0.5f) to p(0.301f, 0.766f), // bakre bein
            p(0.301f, 0.766f) to p(0.15f, 0.96f),
            p(0.44f, 0.24f) to p(0.54f, 0.49f), // armene langs siden
        ),
        // Nede: bakre kne nesten i gulvet. Føttene står i ro.
        end = Frame(
            head = p(0.44f, 0.26f),
            lines = listOf(
                p(0.44f, 0.38f) to p(0.44f, 0.64f),
                p(0.44f, 0.64f) to p(0.651f, 0.74f),
                p(0.651f, 0.74f) to p(0.5f, 0.96f),
                p(0.44f, 0.64f) to p(0.395f, 0.937f),
                p(0.395f, 0.937f) to p(0.15f, 0.96f),
                p(0.44f, 0.38f) to p(0.54f, 0.63f),
            ),
        ),
    ),
    IllustrationType.BULGARIAN to Pose(
        // Nede i bevegelsen.
        head = p(0.36f, 0.22f),
        lines = listOf(
            p(0.37f, 0.32f) to p(0.39f, 0.58f), // overkropp
            p(0.39f, 0.58f) to p(0.178f, 0.622f), // fremre lår
            p(0.178f, 0.622f) to p(0.22f, 0.96f), // fremre legg
            p(0.39f, 0.58f) to p(0.578f, 0.857f), // bakre lår ned mot gulvet
            p(0.578f, 0.857f) to p(0.72f, 0.58f), // bakre fot oppe på benken
            p(0.37f, 0.32f) to p(0.29f, 0.58f), // armene langs siden
        ),
        props = listOf(
            p(0.64f, 0.62f) to p(0.96f, 0.62f), // benk
            p(0.9f, 0.62f) to p(0.9f, 0.97f),
        ),
        // Oppe.
        end = Frame(
            head = p(0.36f, 0.1f),
            lines = listOf(
                p(0.36f, 0.2f) to p(0.37f, 0.46f),
                p(0.37f, 0.46f) to p(0.224f, 0.619f),
                p(0.224f, 0.619f) to p(0.22f, 0.96f),
                p(0.37f, 0.46f) to p(0.479f, 0.777f),
                p(0.479f, 0.777f) to p(0.72f, 0.58f),
                p(0.36f, 0.2f) to p(0.28f, 0.46f),
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
            p(0.44f, 0.24f) to p(0.44f, 0.5f), // overkropp
            p(0.44f, 0.5f) to p(0.56f, 0.7f), // fremre bein bøyd
            p(0.56f, 0.7f) to p(0.5f, 0.96f),
            p(0.44f, 0.5f) to p(0.301f, 0.766f), // bakre bein
            p(0.301f, 0.766f) to p(0.15f, 0.96f),
            p(0.44f, 0.24f) to p(0.54f, 0.49f), // armene langs siden
        ),
        dots = listOf(p(0.545f, 0.52f)),
        // Nede: bakre kne nesten i gulvet. Føttene står i ro.
        end = Frame(
            head = p(0.44f, 0.26f),
            lines = listOf(
                p(0.44f, 0.38f) to p(0.44f, 0.64f),
                p(0.44f, 0.64f) to p(0.651f, 0.74f),
                p(0.651f, 0.74f) to p(0.5f, 0.96f),
                p(0.44f, 0.64f) to p(0.395f, 0.937f),
                p(0.395f, 0.937f) to p(0.15f, 0.96f),
                p(0.44f, 0.38f) to p(0.54f, 0.63f),
            ),
            dots = listOf(p(0.545f, 0.66f)),
        ),
    ),
    IllustrationType.BULGARIAN_DB to Pose(
        // Med en manual i hver hånd.
        head = p(0.36f, 0.22f),
        lines = listOf(
            p(0.37f, 0.32f) to p(0.39f, 0.58f), // overkropp
            p(0.39f, 0.58f) to p(0.178f, 0.622f), // fremre lår
            p(0.178f, 0.622f) to p(0.22f, 0.96f), // fremre legg
            p(0.39f, 0.58f) to p(0.578f, 0.857f), // bakre lår ned mot gulvet
            p(0.578f, 0.857f) to p(0.72f, 0.58f), // bakre fot oppe på benken
            p(0.37f, 0.32f) to p(0.29f, 0.58f), // armene langs siden
        ),
        props = listOf(
            p(0.64f, 0.62f) to p(0.96f, 0.62f), // benk
            p(0.9f, 0.62f) to p(0.9f, 0.97f),
        ),
        dots = listOf(p(0.285f, 0.61f)),
        // Oppe.
        end = Frame(
            head = p(0.36f, 0.1f),
            lines = listOf(
                p(0.36f, 0.2f) to p(0.37f, 0.46f),
                p(0.37f, 0.46f) to p(0.224f, 0.619f),
                p(0.224f, 0.619f) to p(0.22f, 0.96f),
                p(0.37f, 0.46f) to p(0.479f, 0.777f),
                p(0.479f, 0.777f) to p(0.72f, 0.58f),
                p(0.36f, 0.2f) to p(0.28f, 0.46f),
            ),
            dots = listOf(p(0.275f, 0.49f)),
        ),
    ),
    IllustrationType.GOBLET_SQUAT to Pose(
        // Goblet: én vekt holdt inntil brystet med albuene ned.
        head = p(0.61f, 0.44f),
        lines = listOf(
            p(0.544f, 0.516f) to p(0.39f, 0.7f), // overkropp lent frem
            p(0.39f, 0.7f) to p(0.635f, 0.726f), // lår nesten vannrett
            p(0.635f, 0.726f) to p(0.5f, 0.96f), // legg, kneet frem over tærne
            p(0.544f, 0.516f) to p(0.6f, 0.67f), // albuene ned
            p(0.6f, 0.67f) to p(0.65f, 0.56f), // hendene holder vekta inntil brystet
        ),
        dots = listOf(p(0.675f, 0.57f)),
        // Stående. Føttene står i ro.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.2f) to p(0.5f, 0.44f),
                p(0.5f, 0.44f) to p(0.5f, 0.69f),
                p(0.5f, 0.69f) to p(0.5f, 0.96f),
                p(0.5f, 0.2f) to p(0.55f, 0.36f),
                p(0.55f, 0.36f) to p(0.61f, 0.27f),
            ),
            dots = listOf(p(0.635f, 0.28f)),
        ),
    ),
    IllustrationType.FRONT_SQUAT to Pose(
        // Frontknebøy: manualene holdt foran skuldrene.
        head = p(0.61f, 0.44f),
        lines = listOf(
            p(0.544f, 0.516f) to p(0.39f, 0.7f), // overkropp lent frem
            p(0.39f, 0.7f) to p(0.635f, 0.726f), // lår nesten vannrett
            p(0.635f, 0.726f) to p(0.5f, 0.96f), // legg, kneet frem over tærne
            p(0.544f, 0.516f) to p(0.66f, 0.65f), // albuene frem
            p(0.66f, 0.65f) to p(0.7f, 0.55f), // manualene foran skuldrene
        ),
        dots = listOf(p(0.72f, 0.54f)),
        // Stående. Føttene står i ro.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.2f) to p(0.5f, 0.44f),
                p(0.5f, 0.44f) to p(0.5f, 0.69f),
                p(0.5f, 0.69f) to p(0.5f, 0.96f),
                p(0.5f, 0.2f) to p(0.6f, 0.33f),
                p(0.6f, 0.33f) to p(0.62f, 0.21f),
            ),
            dots = listOf(p(0.63f, 0.2f)),
        ),
    ),
    IllustrationType.BB_SQUAT to Pose(
        // Stanga på skuldrene, med skiver i hver ende.
        head = p(0.61f, 0.44f),
        lines = listOf(
            p(0.544f, 0.516f) to p(0.39f, 0.7f), // overkropp lent frem
            p(0.39f, 0.7f) to p(0.635f, 0.726f), // lår nesten vannrett
            p(0.635f, 0.726f) to p(0.5f, 0.96f), // legg, kneet frem over tærne
            p(0.544f, 0.516f) to p(0.47f, 0.6f), // albuene bak og ned
            p(0.47f, 0.6f) to p(0.44f, 0.5f), // hendene holder stanga
        ),
        props = listOf(
            p(0.34f, 0.5f) to p(0.74f, 0.5f), // stanga over skuldrene
        ),
        dots = listOf(p(0.34f, 0.5f), p(0.74f, 0.5f)),
        // Stående. Føttene står i ro.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.2f) to p(0.5f, 0.44f),
                p(0.5f, 0.44f) to p(0.5f, 0.69f),
                p(0.5f, 0.69f) to p(0.5f, 0.96f),
                p(0.5f, 0.2f) to p(0.4f, 0.28f),
                p(0.4f, 0.28f) to p(0.38f, 0.2f),
            ),
            dots = listOf(p(0.3f, 0.2f), p(0.7f, 0.2f)),
            props = listOf(
                p(0.3f, 0.2f) to p(0.7f, 0.2f),
            ),
        ),
    ),
    IllustrationType.BAND_SQUAT to Pose(
        // Strikken går fra hendene ved skuldrene ned under føttene.
        head = p(0.61f, 0.44f),
        lines = listOf(
            p(0.544f, 0.516f) to p(0.39f, 0.7f), // overkropp lent frem
            p(0.39f, 0.7f) to p(0.635f, 0.726f), // lår nesten vannrett
            p(0.635f, 0.726f) to p(0.5f, 0.96f), // legg, kneet frem over tærne
            p(0.544f, 0.516f) to p(0.66f, 0.65f), // albuene frem
            p(0.66f, 0.65f) to p(0.7f, 0.55f), // manualene foran skuldrene
        ),
        props = listOf(
            p(0.7f, 0.55f) to p(0.5f, 0.96f), // strikk fra hendene ned under føttene
        ),
        // Stående. Føttene står i ro.
        end = Frame(
            head = p(0.5f, 0.1f),
            lines = listOf(
                p(0.5f, 0.2f) to p(0.5f, 0.44f),
                p(0.5f, 0.44f) to p(0.5f, 0.69f),
                p(0.5f, 0.69f) to p(0.5f, 0.96f),
                p(0.5f, 0.2f) to p(0.6f, 0.33f),
                p(0.6f, 0.33f) to p(0.62f, 0.21f),
            ),
            props = listOf(
                p(0.62f, 0.21f) to p(0.5f, 0.96f),
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
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.47f, 0.51f),
            ),
            dots = listOf(p(0.47f, 0.51f)),
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
        head = p(0.06f, 0.68f),
        lines = listOf(
            p(0.14f, 0.74f) to p(0.5f, 0.84f), // overkropp løftet fra gulvet
            p(0.5f, 0.84f) to p(0.78f, 0.76f), // bein løftet fra gulvet
            p(0.78f, 0.76f) to p(0.92f, 0.66f),
        ),
        // Liggende flatt.
        end = Frame(
            head = p(0.06f, 0.84f),
            lines = listOf(
                p(0.14f, 0.88f) to p(0.5f, 0.9f),
                p(0.5f, 0.9f) to p(0.78f, 0.9f),
                p(0.78f, 0.9f) to p(0.94f, 0.9f),
            ),
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
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.47f, 0.51f),
            ),
            props = listOf(
                p(0.47f, 0.51f) to p(0.02f, 0.88f),
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
        // Stående: hofta skjøvet frem, vekta langs beina.
        end = Frame(
            head = p(0.45f, 0.09f),
            lines = listOf(
                p(0.45f, 0.194f) to p(0.44f, 0.55f),
                p(0.44f, 0.55f) to p(0.44f, 0.78f),
                p(0.44f, 0.78f) to p(0.45f, 0.97f),
                p(0.45f, 0.194f) to p(0.47f, 0.51f),
                p(0.44f, 0.55f) to p(0.36f, 0.95f),
            ),
            dots = listOf(p(0.47f, 0.51f)),
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
        // Holdøvelse: står stille.
        head = p(0.1f, 0.42f),
        lines = listOf(
            p(0.2f, 0.48f) to p(0.54f, 0.6f), // overkropp
            p(0.54f, 0.6f) to p(0.88f, 0.74f), // bein stablet
            p(0.22f, 0.48f) to p(0.24f, 0.92f), // støttearm ned til gulvet
            p(0.26f, 0.46f) to p(0.26f, 0.16f), // øverste arm hevet
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
        head = p(0.1f, 0.87f),
        lines = listOf(
            p(0.18f, 0.9f) to p(0.54f, 0.9f), // liggende på ryggen
            p(0.24f, 0.9f) to p(0.24f, 0.5f), // armer rett opp
            p(0.54f, 0.9f) to p(0.56f, 0.66f), // ett bein i 90 grader
            p(0.56f, 0.66f) to p(0.76f, 0.66f),
            p(0.54f, 0.9f) to p(0.78f, 0.87f), // andre bein strakt lavt
            p(0.78f, 0.87f) to p(0.97f, 0.85f),
        ),
        // Begge beina tilbake i 90 grader.
        end = Frame(
            head = p(0.1f, 0.87f),
            lines = listOf(
                p(0.18f, 0.9f) to p(0.54f, 0.9f),
                p(0.24f, 0.9f) to p(0.24f, 0.5f),
                p(0.54f, 0.9f) to p(0.56f, 0.66f),
                p(0.56f, 0.66f) to p(0.76f, 0.66f),
                p(0.54f, 0.9f) to p(0.58f, 0.665f),
                p(0.58f, 0.665f) to p(0.78f, 0.67f),
            ),
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
        head = p(0.26f, 0.46f),
        lines = listOf(
            p(0.46f, 0.9f) to p(0.3f, 0.56f), // sittende, lent bakover
            p(0.46f, 0.9f) to p(0.7f, 0.66f), // lår
            p(0.7f, 0.66f) to p(0.92f, 0.72f), // føttene løftet
            p(0.31f, 0.6f) to p(0.5f, 0.66f), // armer holder vekten ut til siden
        ),
        dots = listOf(p(0.53f, 0.66f)),
        // Vekta over til den andre siden.
        end = Frame(
            head = p(0.26f, 0.46f),
            lines = listOf(
                p(0.46f, 0.9f) to p(0.3f, 0.56f),
                p(0.46f, 0.9f) to p(0.7f, 0.66f),
                p(0.7f, 0.66f) to p(0.92f, 0.72f),
                p(0.31f, 0.6f) to p(0.14f, 0.72f),
            ),
            dots = listOf(p(0.11f, 0.74f)),
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

    // ------------------------------------------------------------ KONDISJON / ANNET
    IllustrationType.CARDIO_RUN to Pose(
        head = p(0.38f, 0.14f),
        lines = listOf(
            p(0.4f, 0.26f) to p(0.46f, 0.5f), // overkropp
            p(0.46f, 0.5f) to p(0.68f, 0.54f), // ett kne høyt opp
            p(0.68f, 0.54f) to p(0.66f, 0.77f),
            p(0.46f, 0.5f) to p(0.43f, 0.73f), // støttebein
            p(0.43f, 0.73f) to p(0.42f, 0.96f),
            p(0.4f, 0.28f) to p(0.56f, 0.38f), // arm frem
            p(0.4f, 0.28f) to p(0.26f, 0.4f), // arm bak
        ),
        // Beina og armene bytter.
        end = Frame(
            head = p(0.38f, 0.14f),
            lines = listOf(
                p(0.4f, 0.26f) to p(0.46f, 0.5f),
                p(0.46f, 0.5f) to p(0.43f, 0.73f),
                p(0.43f, 0.73f) to p(0.42f, 0.96f),
                p(0.46f, 0.5f) to p(0.68f, 0.54f),
                p(0.68f, 0.54f) to p(0.66f, 0.77f),
                p(0.4f, 0.28f) to p(0.26f, 0.4f),
                p(0.4f, 0.28f) to p(0.56f, 0.38f),
            ),
        ),
    ),
    IllustrationType.JUMP to Pose(
        head = p(0.5f, 0.12f),
        lines = listOf(
            p(0.5f, 0.24f) to p(0.5f, 0.42f),
            p(0.5f, 0.42f) to p(0.6f, 0.565f), // bein trukket opp
            p(0.6f, 0.565f) to p(0.48f, 0.69f),
            p(0.5f, 0.26f) to p(0.35f, 0.14f), // arm svingt opp
            p(0.7f, 0.5f) to p(0.7f, 0.3f), // pil som viser bevegelsen
            p(0.7f, 0.3f) to p(0.65f, 0.37f),
            p(0.7f, 0.3f) to p(0.75f, 0.37f),
        ),
        ground = false,
        // Nede i knebøy, klar til å hoppe.
        end = Frame(
            head = p(0.52f, 0.46f),
            lines = listOf(
                p(0.5f, 0.56f) to p(0.46f, 0.72f),
                p(0.46f, 0.72f) to p(0.62f, 0.8f),
                p(0.62f, 0.8f) to p(0.52f, 0.96f),
                p(0.5f, 0.58f) to p(0.33f, 0.7f),
                p(0.7f, 0.5f) to p(0.7f, 0.3f),
                p(0.7f, 0.3f) to p(0.65f, 0.37f),
                p(0.7f, 0.3f) to p(0.75f, 0.37f),
            ),
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
    "pike_pushup" -> IllustrationType.PIKE
    "dips_chair" -> IllustrationType.DIP
    "band_triceps_pushdown" -> IllustrationType.PUSHDOWN
    "db_lateral_raise" -> IllustrationType.LATERAL_RAISE
    "band_lateral_raise" -> IllustrationType.LATERAL_RAISE_BAND
    "pullup", "chinup", "negative_pullup" -> IllustrationType.PULLUP_HANG
    "pullup_band_assist" -> IllustrationType.PULLUP_BAND
    "dead_hang" -> IllustrationType.DEAD_HANG
    "band_pulldown" -> IllustrationType.PULLDOWN
    "table_row", "trx_row" -> IllustrationType.INVERTED_ROW
    "band_row" -> IllustrationType.SEATED_ROW
    "db_rear_fly" -> IllustrationType.ROW
    "db_curl" -> IllustrationType.CURL
    "band_curl" -> IllustrationType.CURL_BAND
    "superman" -> IllustrationType.SUPERMAN
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
    "ab_wheel_alt" -> IllustrationType.PLANK
    "suitcase_carry" -> IllustrationType.CARRY
    "mountain_climber" -> IllustrationType.MOUNTAIN_CLIMBER
    "jump_rope" -> IllustrationType.JUMP_ROPE
    "jump_squat", "burpee" -> IllustrationType.JUMP
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
    val head = attached(pose.head, end.head)
    val dots = if (end.dots.isNotEmpty() && end.dots.size == pose.dots.size) {
        pose.dots.zip(end.dots) { d0, d1 -> attached(d0, d1) }
    } else {
        pose.dots
    }
    val props = if (end.props.isNotEmpty() && end.props.size == pose.props.size) {
        pose.props.zip(end.props) { (a0, b0), (a1, b1) ->
            (placed[a0] ?: lerp(a0, a1, k)) to (placed[b0] ?: lerp(b0, b1, k))
        }
    } else {
        pose.props
    }
    return Solved(head, lines, dots, props)
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
    val phase = if (animate && end != null) {
        val transition = rememberInfiniteTransition(label = "ovelse")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(REP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "fase",
        )
        t
    } else {
        0f
    }
    // 0 -> 1 -> 0 med myk oppbremsing i begge ytterstillinger, som en rolig repetisjon.
    val k = ((1 - cos(2 * PI * phase)) / 2).toFloat()
    val solved = if (end != null && k > 0f) {
        solvePose(pose, end, k)
    } else {
        Solved(pose.head, pose.lines, pose.dots, pose.props)
    }
    val head = solved.head
    val lines = solved.lines
    val dots = solved.dots
    val props = solved.props

    Canvas(modifier = modifier) {
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
        props.forEach { (a, b) ->
            drawLine(
                color = strokeColor.copy(alpha = 0.45f),
                start = toPx(a),
                end = toPx(b),
                strokeWidth = stroke * 0.75f,
                cap = StrokeCap.Round,
            )
        }
        lines.forEach { (a, b) ->
            drawLine(color = strokeColor, start = toPx(a), end = toPx(b), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(color = strokeColor, radius = pose.headRadius * size.minDimension, center = toPx(head))
        dots.forEach { d ->
            drawCircle(color = accentColor, radius = stroke * 1.1f, center = toPx(d))
        }
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
