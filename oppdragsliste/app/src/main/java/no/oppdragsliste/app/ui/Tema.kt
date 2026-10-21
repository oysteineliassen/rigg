package no.oppdragsliste.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.data.Tema

private val Lys = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF55606E),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E9EF),
    error = Color(0xFFC62828),
)

private val Mork = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFBDC7D6),
    background = Color(0xFF111316),
    surface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFF2B2F35),
    error = Color(0xFFFF8A80),
)

@Composable
fun erMorkt(tema: Tema): Boolean = when (tema) {
    Tema.SYSTEM -> isSystemInDarkTheme()
    Tema.LYS -> false
    Tema.MORK -> true
}

@Composable
fun OppdragTema(tema: Tema, innhold: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (erMorkt(tema)) Mork else Lys, content = innhold)
}

/** Fargen på venstrekanten og prioritetsvalget. */
@Composable
@ReadOnlyComposable
fun Prioritet.farge(): Color {
    val morkt = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (this) {
        Prioritet.AKUTT -> if (morkt) Color(0xFFFF6B6B) else Color(0xFFD32F2F)
        Prioritet.AVTALE -> if (morkt) Color(0xFF64B5F6) else Color(0xFF1976D2)
        Prioritet.HASTER -> if (morkt) Color(0xFFFFB74D) else Color(0xFFEF6C00)
        Prioritet.OMRADE -> if (morkt) Color(0xFF81C784) else Color(0xFF388E3C)
    }
}

/** Rød tekst for forfalte datoer. */
val forfaltFarge: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
