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
        val bildet = InputImage.fromFilePath(context, bilde)
        val resultat = gjenkjenner.process(bildet).await()
        val hoyde = bildet.height
        return resultat.textBlocks
            .flatMap { blokk -> blokk.lines }
            // Linjer helt nederst ligger ofte halvveis bak nettleserens bunnlinje og blir feillest; de tas
            // med fra neste skjermbilde i stedet. Øverst er statuslinjen.
            .filter { l ->
                val boks = l.boundingBox ?: return@filter true
                hoyde <= 0 || (boks.bottom < hoyde * 0.885 && boks.top > hoyde * 0.03)
            }
            // Ovenfra og ned; linjer på omtrent samme høyde fra venstre mot høyre.
            .sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text }
    } finally {
        gjenkjenner.close()
    }
}
