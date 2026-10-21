package no.oppdragsliste.app.ordre

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Leser teksten i et bilde med Googles tekstgjenkjenning (ML Kit), som kjører på telefonen uten nett.
 * Linjene kommer ovenfra og ned, så de kan tolkes i rekkefølge.
 */
suspend fun lesTekst(context: Context, bilde: Uri): List<String> {
    val gjenkjenner = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val resultat = gjenkjenner.process(InputImage.fromFilePath(context, bilde)).await()
        return resultat.textBlocks
            .flatMap { blokk -> blokk.lines }
            // Ovenfra og ned; linjer på omtrent samme høyde fra venstre mot høyre.
            .sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text }
    } finally {
        gjenkjenner.close()
    }
}
