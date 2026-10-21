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
        val linjer = resultat.textBlocks.flatMap { blokk -> blokk.lines }
        val vanligHoyde = linjer.mapNotNull { it.boundingBox?.height() }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        return linjer
            .filter { l ->
                val boks = l.boundingBox ?: return@filter true
                if (hoyde <= 0) return@filter true
                // Øverst er statusfeltet til telefonen.
                if (boks.top <= hoyde * 0.03) return@filter false
                // Nederst kan nettleserens verktøylinje ligge over teksten (eller selv bli lest som tekst). Den er
                // ikke alltid der, så nederste del av bildet tas med, unntatt linjer som er delvis skjult (lavere
                // enn vanlig) eller som gjenkjenningen er usikker på.
                if (boks.bottom > hoyde * 0.8) {
                    if (vanligHoyde > 0 && boks.height() < vanligHoyde * 0.75) return@filter false
                    if (l.confidence < 0.7f) return@filter false
                }
                true
            }
            // Ovenfra og ned; linjer på omtrent samme høyde fra venstre mot høyre.
            .sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text }
    } finally {
        gjenkjenner.close()
    }
}
