package no.rigg.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Rigg sitt utseende: grafitt og benhvitt fra appikonet, rust som eneste aksentfarge.
// Barlow Condensed til overskrifter og store tall, Barlow til vanlig tekst. Følger telefonens lyse/mørke tema.

private val Dark = darkColorScheme(
    primary = Color(0xFFC2521D), // fylte knapper
    onPrimary = Color(0xFFFFF7F0),
    primaryContainer = Color(0xFF3A2419), // valgt flis, valgt fane
    onPrimaryContainer = Color(0xFFE8804F),
    secondary = Color(0xFFD9622B), // fremdrift, ringer, haker
    onSecondary = Color(0xFF1A1A1A),
    tertiary = Color(0xFFE8804F), // aksenttekst
    background = Color(0xFF151514),
    onBackground = Color(0xFFF2F0EA),
    surface = Color(0xFF151514),
    onSurface = Color(0xFFF2F0EA),
    surfaceVariant = Color(0xFF262523), // figurbakgrunn
    onSurfaceVariant = Color(0xFFA9A49A),
    surfaceContainerLowest = Color(0xFF111110),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF1F1E1C), // kort
    surfaceContainerHigh = Color(0xFF262523),
    surfaceContainerHighest = Color(0xFF2E2D2A),
    outline = Color(0xFF3D3B37),
    outlineVariant = Color(0xFF2C2A27),
    inverseSurface = Color(0xFFF2F0EA), // valgt knapp i en gruppe
    inverseOnSurface = Color(0xFF151514),
    error = Color(0xFFF2A28A),
)

private val Light = lightColorScheme(
    primary = Color(0xFFC2521D),
    onPrimary = Color(0xFFFFF7F0),
    primaryContainer = Color(0xFFF7E3D8),
    onPrimaryContainer = Color(0xFF8A3510),
    secondary = Color(0xFFD9622B),
    onSecondary = Color(0xFF1A1A1A),
    tertiary = Color(0xFFA8431A),
    background = Color(0xFFF4F2EC),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF4F2EC),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE9E6DE),
    onSurfaceVariant = Color(0xFF5F5A51),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF9F5),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFECE5),
    surfaceContainerHighest = Color(0xFFE9E6DE),
    outline = Color(0xFFCFCAC0),
    outlineVariant = Color(0xFFE0DCD3),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF4F2EC),
    error = Color(0xFFB3261E),
)

val Condensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

val BodyFont = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semibold, FontWeight.Bold),
)

private fun display(size: Int, weight: FontWeight = FontWeight.Bold, line: Int = size) =
    TextStyle(fontFamily = Condensed, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.35).toInt()) =
    TextStyle(fontFamily = BodyFont, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val RiggType = Typography(
    displayLarge = display(84, line = 80),
    displayMedium = display(64, line = 62),
    displaySmall = display(44, line = 44),
    headlineLarge = display(38, line = 40),
    headlineMedium = display(32, line = 34),
    headlineSmall = display(26, line = 30),
    titleLarge = display(22, line = 26),
    titleMedium = body(16, FontWeight.SemiBold),
    titleSmall = body(14, FontWeight.SemiBold),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(13),
    labelLarge = body(15, FontWeight.SemiBold),
    labelMedium = body(12, FontWeight.SemiBold),
    labelSmall = body(11, FontWeight.Medium),
)

@Composable
fun RiggTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = RiggType, content = content)
}

// ---------------------------------------------------------------- SMÅ FELLES BYGGEKLOSSER

/** Liten overskrift i versaler med luft mellom bokstavene, som "NESTE ØKT". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp), color = color)
}

/** Den store handlingsknappen (Start økt, Ferdig sett, Lag program). */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text.uppercase(), style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.8.sp))
        }
    }
}

/** Øvelsesfiguren i en avrundet flis, brukt i lister og kort. */
@Composable
fun ExerciseTile(exercise: Exercise, size: Dp = 48.dp, animate: Boolean = false) =
    FigureTile(illustrationFor(exercise), size, animate)

/** Som [ExerciseTile], men for en figur direkte (oppvarmingen). */
@Composable
fun FigureTile(type: IllustrationType, size: Dp = 48.dp, animate: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.5f)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        ExerciseIllustration(type, modifier = Modifier.size(size * 0.8f), animate = animate)
    }
}

// ---------------------------------------------------------------- IKONER (tynne streker, samme stil som i skissene)

private fun strokeIcon(name: String, width: Float, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    paths.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

private fun fillIcon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    paths.forEach { d -> b.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)) }
    return b.build()
}

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

object RiggIcons {
    val Dumbbell = strokeIcon("dumbbell", 2f, "M6.5 8v8M17.5 8v8M3.5 10v4M20.5 10v4M6.5 12h11")
    val Kettlebell = strokeIcon("kettlebell", 1.8f, "M9 9.5V7a3 3 0 0 1 6 0v2.5", circle(12f, 15f, 5.5f))
    val Band = strokeIcon("band", 1.8f, "M3 12c2.5-4.5 4.5-4.5 6 0s3.5 4.5 6 0 3.5-4.5 6 0")
    val PullUpBar = strokeIcon("bar", 1.8f, "M3 6h18M6 6v14M18 6v14")
    val Bench = strokeIcon("bench", 1.8f, "M3 11h18M6 11v7M18 11v7")
    val Box = strokeIcon("box", 1.8f, "M5.5 8h13a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-9A1.5 1.5 0 0 1 5.5 8z", "M4 13h16")
    val Barbell = strokeIcon("barbell", 1.8f, "M2 12h20M5 7v10M8 8.5v7M16 8.5v7M19 7v10")
    val Chair = strokeIcon("chair", 1.8f, "M7 4v9h10V4M7 13v7M17 13v7")
    val Table = strokeIcon("table", 1.8f, "M3 9h18M6 9v10M18 9v10")
    val Straps = strokeIcon("straps", 1.8f, "M8 3v10l-2 5M16 3v10l2 5M4.5 18h3M16.5 18h3")
    val Rope = strokeIcon("rope", 1.8f, "M6 17c0-9 12-9 12 0M6 17v4M18 17v4")
    val Check = strokeIcon("check", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val ArrowUp = strokeIcon("up", 2.2f, "M12 19V5M5.5 11.5L12 5l6.5 6.5")
    val Close = strokeIcon("close", 2f, "M6 6l12 12M18 6L6 18")
    val ChevronDown = strokeIcon("down", 2f, "M6 9l6 6 6-6")
    val Sparkle = strokeIcon("ai", 1.8f, "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z")
    val Play = fillIcon("play", "M7 4.5v15l12.5-7.5z")
    val Pause = fillIcon("pause", "M6.5 5h3.5v14H6.5z", "M14 5h3.5v14H14z")
    val Share = strokeIcon("share", 1.8f, "M12 3v12M7.5 7.5L12 3l4.5 4.5", "M5 12v7.5h14V12")
    val Sliders = strokeIcon("sliders", 1.8f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Lines = strokeIcon("lines", 1.8f, "M9 6h11M9 12h11M9 18h11M4.5 6h0.01M4.5 12h0.01M4.5 18h0.01")
    val Trend = strokeIcon("trend", 1.8f, "M4 17l5-5 4 3 7-8", "M15 7h5v5")
    val More = fillIcon("more", circle(5f, 12f, 1.8f), circle(12f, 12f, 1.8f), circle(19f, 12f, 1.8f))

    fun forEquipment(e: Equipment): ImageVector = when (e) {
        Equipment.BODYWEIGHT -> Dumbbell
        Equipment.DUMBBELLS -> Dumbbell
        Equipment.KETTLEBELL -> Kettlebell
        Equipment.RESISTANCE_BANDS -> Band
        Equipment.PULL_UP_BAR -> PullUpBar
        Equipment.BENCH -> Bench
        Equipment.PLYO_BOX -> Box
        Equipment.BARBELL -> Barbell
        Equipment.CHAIR -> Chair
        Equipment.TABLE -> Table
        Equipment.TRX -> Straps
        Equipment.JUMP_ROPE -> Rope
    }
}
