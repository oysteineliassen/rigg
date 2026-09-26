package no.rigg.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Enkle strekfigur-illustrasjoner tegnet i kode (ingen bilder å laste ned eller lisensiere).
 * Hver pose er definert som linjer og punkter i et 0..1-koordinatsystem (x mot høyre, y nedover),
 * stort sett sett fra siden. Gulvet ligger på y = 0.97.
 *
 * Tips ved endringer: figurene er små (44 dp i listene), så hold dem enkle og la armer og bein
 * stikke tydelig ut fra kroppen, ellers smelter de sammen til én strek.
 */
enum class IllustrationType {
    PUSHUP, BENCH_PRESS, FLOOR_PRESS, OVERHEAD_PRESS, FORWARD_PRESS, LATERAL_RAISE, DIP, PUSHDOWN, PIKE,
    ROW, INVERTED_ROW, SEATED_ROW, PULLUP_HANG, PULLDOWN, CURL,
    SQUAT, WALL_SIT, LUNGE, BULGARIAN, STEP_UP,
    HIP_HINGE, GLUTE_BRIDGE, CALF_RAISE, SUPERMAN,
    PLANK, SIDE_PLANK, MOUNTAIN_CLIMBER, BIRD_DOG, DEAD_BUG, HOLLOW_HOLD, LEG_RAISE, HANGING_RAISE, SEATED_TWIST,
    CARDIO_RUN, JUMP, CARRY,
}

private data class Pose(
    val head: Offset,
    val headRadius: Float = 0.07f,
    /** Kroppen: armer, bein og overkropp. */
    val lines: List<Pair<Offset, Offset>>,
    /** Utstyr og omgivelser (stang, benk, vegg, strikk), tegnet tynnere og lysere enn kroppen. */
    val props: List<Pair<Offset, Offset>> = emptyList(),
    /** Vekter (manual/kettlebell), tegnet i aksentfargen. */
    val dots: List<Offset> = emptyList(),
    val ground: Boolean = true,
)

private fun p(x: Float, y: Float) = Offset(x, y)

private val POSES: Map<IllustrationType, Pose> = mapOf(
    // ------------------------------------------------------------ PRESS
    IllustrationType.PUSHUP to Pose(
        head = p(0.1f, 0.58f),
        lines = listOf(
            p(0.18f, 0.64f) to p(0.58f, 0.74f), // overkropp
            p(0.58f, 0.74f) to p(0.92f, 0.84f), // bein til tærne
            p(0.18f, 0.64f) to p(0.2f, 0.92f), // strak arm ned til gulvet
        ),
    ),
    IllustrationType.BENCH_PRESS to Pose(
        head = p(0.12f, 0.7f),
        lines = listOf(
            p(0.2f, 0.74f) to p(0.56f, 0.74f), // overkropp liggende på benken
            p(0.56f, 0.74f) to p(0.74f, 0.72f), // lår
            p(0.74f, 0.72f) to p(0.76f, 0.95f), // legg ned til gulvet
            p(0.3f, 0.74f) to p(0.32f, 0.36f), // arm presser opp
        ),
        props = listOf(
            p(0.1f, 0.81f) to p(0.62f, 0.81f), // benk
            p(0.18f, 0.81f) to p(0.18f, 0.97f),
            p(0.54f, 0.81f) to p(0.54f, 0.97f),
        ),
        dots = listOf(p(0.32f, 0.32f)),
    ),
    IllustrationType.FLOOR_PRESS to Pose(
        head = p(0.1f, 0.87f),
        lines = listOf(
            p(0.18f, 0.9f) to p(0.54f, 0.9f), // liggende på gulvet
            p(0.54f, 0.9f) to p(0.7f, 0.66f), // bøyde knær
            p(0.7f, 0.66f) to p(0.84f, 0.92f),
            p(0.28f, 0.9f) to p(0.3f, 0.52f), // arm presser opp
        ),
        dots = listOf(p(0.3f, 0.48f)),
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
    ),
    IllustrationType.FORWARD_PRESS to Pose(
        head = p(0.4f, 0.16f),
        lines = listOf(
            p(0.4f, 0.27f) to p(0.4f, 0.58f), // stående, sett fra siden
            p(0.4f, 0.58f) to p(0.34f, 0.96f),
            p(0.4f, 0.58f) to p(0.48f, 0.96f),
            p(0.4f, 0.33f) to p(0.8f, 0.35f), // armer strakt frem
        ),
        props = listOf(
            p(0.8f, 0.35f) to p(0.97f, 0.42f), // strikk mot festepunkt
        ),
    ),
    IllustrationType.LATERAL_RAISE to Pose(
        head = p(0.5f, 0.16f),
        lines = listOf(
            p(0.5f, 0.27f) to p(0.5f, 0.56f),
            p(0.5f, 0.56f) to p(0.44f, 0.95f),
            p(0.5f, 0.56f) to p(0.56f, 0.95f),
            p(0.5f, 0.3f) to p(0.24f, 0.28f),
            p(0.5f, 0.3f) to p(0.76f, 0.28f),
        ),
        dots = listOf(p(0.22f, 0.28f), p(0.78f, 0.28f)),
    ),
    IllustrationType.DIP to Pose(
        head = p(0.37f, 0.23f),
        lines = listOf(
            p(0.36f, 0.33f) to p(0.38f, 0.63f), // overkropp foran stolen
            p(0.36f, 0.35f) to p(0.3f, 0.5f), // arm bak til setet
            p(0.38f, 0.63f) to p(0.66f, 0.66f), // lår
            p(0.66f, 0.66f) to p(0.84f, 0.95f), // legg, hælene i gulvet
        ),
        props = listOf(
            p(0.06f, 0.52f) to p(0.32f, 0.52f), // stol/benk
            p(0.1f, 0.52f) to p(0.1f, 0.97f),
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
    ),
    IllustrationType.PIKE to Pose(
        head = p(0.78f, 0.42f),
        lines = listOf(
            p(0.1f, 0.9f) to p(0.48f, 0.14f), // bein opp til hoften
            p(0.48f, 0.14f) to p(0.7f, 0.5f), // overkropp ned
            p(0.7f, 0.5f) to p(0.86f, 0.9f), // arm ned til hånden
        ),
    ),

    // ------------------------------------------------------------ TREKK
    IllustrationType.ROW to Pose(
        head = p(0.86f, 0.34f),
        lines = listOf(
            p(0.78f, 0.41f) to p(0.36f, 0.5f), // flat rygg, fremoverbøyd
            p(0.36f, 0.5f) to p(0.44f, 0.74f), // lett bøyde knær
            p(0.44f, 0.74f) to p(0.38f, 0.97f),
            p(0.76f, 0.42f) to p(0.74f, 0.72f), // armen henger rett ned (startposisjon)
        ),
        dots = listOf(p(0.74f, 0.75f)),
    ),
    IllustrationType.INVERTED_ROW to Pose(
        head = p(0.26f, 0.54f),
        lines = listOf(
            p(0.36f, 0.6f) to p(0.64f, 0.75f), // stiv kropp på skrå under bordet
            p(0.64f, 0.75f) to p(0.93f, 0.93f),
            p(0.36f, 0.6f) to p(0.42f, 0.32f), // armer opp til bordkanten
        ),
        props = listOf(
            p(0.03f, 0.3f) to p(0.5f, 0.3f), // bordplate
            p(0.08f, 0.3f) to p(0.08f, 0.97f),
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
    ),
    IllustrationType.PULLUP_HANG to Pose(
        head = p(0.5f, 0.27f),
        lines = listOf(
            p(0.5f, 0.37f) to p(0.5f, 0.64f), // overkropp (sett forfra)
            p(0.5f, 0.64f) to p(0.45f, 0.94f), // hengende bein
            p(0.5f, 0.64f) to p(0.55f, 0.94f),
            p(0.5f, 0.39f) to p(0.3f, 0.3f), // armer i V opp til stanga
            p(0.3f, 0.3f) to p(0.28f, 0.08f),
            p(0.5f, 0.39f) to p(0.7f, 0.3f),
            p(0.7f, 0.3f) to p(0.72f, 0.08f),
        ),
        props = listOf(
            p(0.1f, 0.08f) to p(0.9f, 0.08f), // tverrligger
        ),
        ground = false,
    ),
    IllustrationType.PULLDOWN to Pose(
        head = p(0.48f, 0.3f),
        lines = listOf(
            p(0.48f, 0.4f) to p(0.48f, 0.68f), // knestående
            p(0.48f, 0.68f) to p(0.5f, 0.94f), // lår ned til kneet
            p(0.5f, 0.94f) to p(0.22f, 0.94f), // legg langs gulvet
            p(0.48f, 0.43f) to p(0.66f, 0.32f), // armer trekker ned
            p(0.66f, 0.32f) to p(0.64f, 0.14f),
        ),
        props = listOf(
            p(0.64f, 0.14f) to p(0.62f, 0.02f), // strikk til festepunkt oppe
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
    ),

    // ------------------------------------------------------------ BEIN
    IllustrationType.SQUAT to Pose(
        head = p(0.58f, 0.2f),
        lines = listOf(
            p(0.55f, 0.3f) to p(0.34f, 0.56f), // overkropp lent frem
            p(0.34f, 0.56f) to p(0.62f, 0.62f), // lår nesten vannrett
            p(0.62f, 0.62f) to p(0.54f, 0.96f), // legg
            p(0.54f, 0.33f) to p(0.82f, 0.36f), // armer frem for balanse
        ),
    ),
    IllustrationType.WALL_SIT to Pose(
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
            p(0.44f, 0.5f) to p(0.26f, 0.74f), // bakre bein
            p(0.26f, 0.74f) to p(0.15f, 0.96f),
        ),
    ),
    IllustrationType.BULGARIAN to Pose(
        head = p(0.36f, 0.14f),
        lines = listOf(
            p(0.36f, 0.24f) to p(0.38f, 0.5f), // overkropp
            p(0.38f, 0.5f) to p(0.2f, 0.62f), // fremre lår
            p(0.2f, 0.62f) to p(0.22f, 0.96f), // fremre legg
            p(0.38f, 0.5f) to p(0.5f, 0.8f), // bakre lår ned mot gulvet
            p(0.5f, 0.8f) to p(0.72f, 0.58f), // bakre fot oppe på benken
        ),
        props = listOf(
            p(0.64f, 0.62f) to p(0.96f, 0.62f), // benk
            p(0.9f, 0.62f) to p(0.9f, 0.97f),
        ),
    ),
    IllustrationType.STEP_UP to Pose(
        head = p(0.44f, 0.1f),
        lines = listOf(
            p(0.44f, 0.2f) to p(0.42f, 0.46f), // overkropp
            p(0.42f, 0.46f) to p(0.62f, 0.5f), // fremre lår
            p(0.62f, 0.5f) to p(0.64f, 0.68f), // fremre fot oppe på kassen
            p(0.42f, 0.46f) to p(0.36f, 0.72f), // bakre bein
            p(0.36f, 0.72f) to p(0.3f, 0.96f),
        ),
        props = listOf(
            p(0.52f, 0.72f) to p(0.92f, 0.72f), // kasse/trapp
            p(0.52f, 0.72f) to p(0.52f, 0.97f),
            p(0.92f, 0.72f) to p(0.92f, 0.97f),
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
    ),
    IllustrationType.GLUTE_BRIDGE to Pose(
        head = p(0.1f, 0.76f),
        lines = listOf(
            p(0.18f, 0.76f) to p(0.52f, 0.58f), // hofta løftet, skuldre i gulvet
            p(0.52f, 0.58f) to p(0.74f, 0.76f), // bøyd bein ned til foten
            p(0.74f, 0.76f) to p(0.9f, 0.92f),
        ),
    ),
    IllustrationType.CALF_RAISE to Pose(
        head = p(0.5f, 0.12f),
        lines = listOf(
            p(0.5f, 0.24f) to p(0.5f, 0.5f),
            p(0.5f, 0.5f) to p(0.5f, 0.8f),
            p(0.44f, 0.86f) to p(0.44f, 0.8f), p(0.56f, 0.86f) to p(0.56f, 0.8f), // hælene løftet
        ),
    ),
    IllustrationType.SUPERMAN to Pose(
        head = p(0.06f, 0.68f),
        lines = listOf(
            p(0.14f, 0.74f) to p(0.5f, 0.84f), // overkropp løftet fra gulvet
            p(0.5f, 0.84f) to p(0.78f, 0.76f), // bein løftet fra gulvet
            p(0.78f, 0.76f) to p(0.92f, 0.66f),
        ),
    ),

    // ------------------------------------------------------------ KJERNE
    IllustrationType.PLANK to Pose(
        head = p(0.08f, 0.66f),
        lines = listOf(
            p(0.18f, 0.72f) to p(0.58f, 0.8f), // rett kropp
            p(0.58f, 0.8f) to p(0.94f, 0.91f), // bein til tærne
            p(0.19f, 0.72f) to p(0.21f, 0.92f), // overarm ned til albuen
            p(0.21f, 0.92f) to p(0.04f, 0.92f), // underarm langs gulvet
        ),
    ),
    IllustrationType.SIDE_PLANK to Pose(
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
            p(0.56f, 0.7f) to p(0.92f, 0.9f), // strakt bakre bein
            p(0.18f, 0.62f) to p(0.2f, 0.93f), // strak arm
            p(0.56f, 0.7f) to p(0.36f, 0.8f), // kneet dratt frem
            p(0.36f, 0.8f) to p(0.46f, 0.93f),
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
    ),
    IllustrationType.DEAD_BUG to Pose(
        head = p(0.1f, 0.87f),
        lines = listOf(
            p(0.18f, 0.9f) to p(0.54f, 0.9f), // liggende på ryggen
            p(0.24f, 0.9f) to p(0.24f, 0.5f), // armer rett opp
            p(0.54f, 0.9f) to p(0.56f, 0.6f), // ett bein i 90 grader
            p(0.56f, 0.6f) to p(0.78f, 0.6f),
            p(0.54f, 0.9f) to p(0.95f, 0.82f), // andre bein strakt lavt
        ),
    ),
    IllustrationType.HOLLOW_HOLD to Pose(
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
            p(0.54f, 0.9f) to p(0.62f, 0.42f), // strake bein løftet opp
            p(0.24f, 0.91f) to p(0.44f, 0.93f), // armene langs siden
        ),
    ),
    IllustrationType.HANGING_RAISE to Pose(
        head = p(0.6f, 0.25f),
        lines = listOf(
            p(0.5f, 0.34f) to p(0.5f, 0.08f), // armer opp til stanga (sett fra siden)
            p(0.5f, 0.34f) to p(0.48f, 0.62f), // overkropp
            p(0.48f, 0.62f) to p(0.74f, 0.56f), // knærne løftet
            p(0.74f, 0.56f) to p(0.76f, 0.82f),
        ),
        props = listOf(
            p(0.2f, 0.08f) to p(0.8f, 0.08f), // tverrligger
        ),
        ground = false,
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
    ),

    // ------------------------------------------------------------ KONDISJON / ANNET
    IllustrationType.CARDIO_RUN to Pose(
        head = p(0.38f, 0.14f),
        lines = listOf(
            p(0.4f, 0.26f) to p(0.46f, 0.5f), // overkropp
            p(0.46f, 0.5f) to p(0.56f, 0.55f), // fremre bein, kneet opp
            p(0.56f, 0.55f) to p(0.5f, 0.36f),
            p(0.46f, 0.5f) to p(0.3f, 0.66f), // bakre bein strakt
            p(0.3f, 0.66f) to p(0.2f, 0.86f),
            p(0.4f, 0.28f) to p(0.56f, 0.36f), // arm frem
            p(0.4f, 0.28f) to p(0.24f, 0.4f), // arm bak
        ),
    ),
    IllustrationType.JUMP to Pose(
        head = p(0.5f, 0.12f),
        lines = listOf(
            p(0.5f, 0.24f) to p(0.5f, 0.42f),
            p(0.5f, 0.42f) to p(0.56f, 0.56f), // bein trukket opp
            p(0.56f, 0.56f) to p(0.5f, 0.66f),
            p(0.5f, 0.26f) to p(0.35f, 0.14f), // arm svingt opp
            p(0.7f, 0.5f) to p(0.7f, 0.3f), // pil som viser bevegelsen
            p(0.7f, 0.3f) to p(0.65f, 0.37f),
            p(0.7f, 0.3f) to p(0.75f, 0.37f),
        ),
        ground = false,
    ),
    IllustrationType.CARRY to Pose(
        head = p(0.5f, 0.14f),
        lines = listOf(
            p(0.5f, 0.25f) to p(0.5f, 0.55f),
            p(0.5f, 0.55f) to p(0.62f, 0.75f), // fremre bein
            p(0.62f, 0.75f) to p(0.58f, 0.96f),
            p(0.5f, 0.55f) to p(0.4f, 0.72f), // bakre bein
            p(0.4f, 0.72f) to p(0.34f, 0.9f),
            p(0.5f, 0.3f) to p(0.56f, 0.62f), // arm med vekten langs siden
        ),
        dots = listOf(p(0.56f, 0.64f)),
    ),
)

private fun poseFor(type: IllustrationType): Pose = POSES.getValue(type)

/** Kobler en øvelse-id til riktig illustrasjonstype, med fall-back basert på bevegelsesmønster. */
fun illustrationFor(exercise: Exercise): IllustrationType = when (exercise.id) {
    // Press
    "db_bench", "bb_bench" -> IllustrationType.BENCH_PRESS
    "db_floor_press", "db_floor_press_1arm" -> IllustrationType.FLOOR_PRESS
    "db_shoulder_press", "kb_press", "db_triceps_ext", "thruster_db" -> IllustrationType.OVERHEAD_PRESS
    "band_press", "pallof_press", "band_face_pull" -> IllustrationType.FORWARD_PRESS
    "pike_pushup" -> IllustrationType.PIKE
    "dips_chair" -> IllustrationType.DIP
    "band_triceps_pushdown" -> IllustrationType.PUSHDOWN
    "db_lateral_raise", "band_lateral_raise" -> IllustrationType.LATERAL_RAISE
    // Trekk
    "pullup", "chinup", "pullup_band_assist", "negative_pullup", "dead_hang" -> IllustrationType.PULLUP_HANG
    "band_pulldown" -> IllustrationType.PULLDOWN
    "table_row", "trx_row" -> IllustrationType.INVERTED_ROW
    "band_row" -> IllustrationType.SEATED_ROW
    "db_rear_fly" -> IllustrationType.ROW
    "db_curl", "band_curl" -> IllustrationType.CURL
    "superman" -> IllustrationType.SUPERMAN
    // Bein
    "wall_sit" -> IllustrationType.WALL_SIT
    "bulgarian", "db_bulgarian" -> IllustrationType.BULGARIAN
    "step_up" -> IllustrationType.STEP_UP
    // Hofte
    "glute_bridge", "glute_bridge_1leg", "hip_thrust_db", "hamstring_slide" -> IllustrationType.GLUTE_BRIDGE
    "calf_raise", "calf_raise_db" -> IllustrationType.CALF_RAISE
    "kb_swing", "kb_swing_cardio" -> IllustrationType.HIP_HINGE
    // Kjerne
    "side_plank" -> IllustrationType.SIDE_PLANK
    "bird_dog" -> IllustrationType.BIRD_DOG
    "dead_bug" -> IllustrationType.DEAD_BUG
    "hollow_hold" -> IllustrationType.HOLLOW_HOLD
    "leg_raise" -> IllustrationType.LEG_RAISE
    "hanging_knee_raise", "hanging_leg_raise" -> IllustrationType.HANGING_RAISE
    "russian_twist_db" -> IllustrationType.SEATED_TWIST
    "ab_wheel_alt" -> IllustrationType.PLANK
    "suitcase_carry" -> IllustrationType.CARRY
    // Kondisjon
    "mountain_climber" -> IllustrationType.MOUNTAIN_CLIMBER
    "jump_squat", "jump_rope", "burpee" -> IllustrationType.JUMP
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

/** Tegner strekfiguren for en øvelse. */
@Composable
fun ExerciseIllustration(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val pose = poseFor(illustrationFor(exercise))
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
        pose.props.forEach { (a, b) ->
            drawLine(
                color = strokeColor.copy(alpha = 0.45f),
                start = toPx(a),
                end = toPx(b),
                strokeWidth = stroke * 0.75f,
                cap = StrokeCap.Round,
            )
        }
        pose.lines.forEach { (a, b) ->
            drawLine(color = strokeColor, start = toPx(a), end = toPx(b), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(color = strokeColor, radius = pose.headRadius * size.minDimension, center = toPx(pose.head))
        pose.dots.forEach { d ->
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
