package no.rigg.app

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

// ---------------------------------------------------------------- TEKST TIL BILDET (rene funksjoner, testes i StatsTest)

/**
 * Settene for én øvelse på én linje. Lik vekt i alle sett skrives én gang: "8 / 12 / 12 reps · 13.5 kg".
 * Ulik vekt: "8×12.5 · 10×15 kg". Tidsøvelser: "30 / 45 sek".
 */
fun setsSummary(sets: List<SetResult>, timed: Boolean): String {
    if (sets.isEmpty()) return ""
    val reps = sets.joinToString(" / ") { it.reps.toString() }
    if (timed) return reps + tr(" s", " sek")
    val weights = sets.map { it.weightKg }
    return when {
        weights.all { it == null } -> "$reps reps"
        weights.distinct().size == 1 -> "$reps reps · ${formatWeight(weights.first()!!)} kg"
        else -> sets.joinToString(" · ") { s -> s.weightKg?.let { "${s.reps}×${formatWeight(it)}" } ?: "${s.reps}" } + " kg"
    }
}

/** Totalt løftet (reps × kg) i økta. Tidsøvelser og sett uten vekt teller ikke. */
fun sessionVolumeKg(log: SessionLog): Double = log.results.sumOf { r ->
    if (ExerciseLibrary.get(r.exerciseId)?.timed == true) 0.0
    else r.sets.sumOf { s -> (s.weightKg ?: 0.0) * s.reps }
}

/** "1 240": mellomrom som tusenskille, uten desimaler. */
fun formatThousands(value: Long): String = String.format(Locale.US, "%,d", value).replace(',', ' ')

// ---------------------------------------------------------------- BILDET

/**
 * Tegner en loggført økt som et bilde (PNG) med figurene, klart til å deles på Strava, Instagram osv.
 * 1080 piksler bredt og minst 4:5 (høyere hvis økta har mange øvelser). Alltid mørkt, uansett tema i appen.
 */
object WorkoutShareImage {
    private const val W = 1080
    private const val PAD = 72f
    private const val ROW = 156f
    private const val TILE = 124f

    private const val BG = 0xFF151514.toInt()
    private const val CARD = 0xFF1F1E1C.toInt()
    private const val TILE_BG = 0xFF262523.toInt()
    private const val TEXT = 0xFFF2F0EA.toInt()
    private const val MUTED = 0xFFA9A49A.toInt()
    private const val ACCENT = 0xFFD9622B.toInt()
    private const val ACCENT_TEXT = 0xFFE8804F.toInt()
    private const val LINE = 0xFF2E2D2A.toInt()

    fun render(context: Context, log: SessionLog): Bitmap {
        val rows = log.results.filter { it.sets.isNotEmpty() }
        val headerH = 520f
        val footerH = 140f
        val height = max(1350, (headerH + rows.size * ROW + footerH).toInt())
        val bitmap = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bitmap)
        c.drawColor(BG)

        val condensed = font(context, R.font.barlow_condensed_bold, Typeface.DEFAULT_BOLD)
        val semibold = font(context, R.font.barlow_semibold, Typeface.DEFAULT_BOLD)
        val regular = font(context, R.font.barlow_regular, Typeface.DEFAULT)
        fun paint(face: Typeface, px: Float, color: Int, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = px
            this.color = color
            letterSpacing = spacing
        }
        fun fit(text: String, p: TextPaint, width: Float) = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()
        val inner = W - 2 * PAD

        // Topp: merke og dato.
        c.drawText("RIGG", PAD, PAD + 34f, paint(condensed, 40f, ACCENT_TEXT, 0.18f))
        val datePaint = paint(regular, 32f, MUTED)
        val date = formatDate(log.date)
        c.drawText(date, W - PAD - datePaint.measureText(date), PAD + 32f, datePaint)

        // Tittel.
        val titlePaint = paint(condensed, 104f, TEXT)
        c.drawText(fit(log.dayTitle, titlePaint, inner), PAD, PAD + 170f, titlePaint)

        // Tall: varighet, sett, volum.
        val setCount = rows.sumOf { it.sets.size }
        val volume = sessionVolumeKg(log)
        val stats = buildList {
            if (log.durationMin > 0) add("${log.durationMin}" to tr("MINUTES", "MINUTTER"))
            add("${rows.size}" to tr("EXERCISES", "ØVELSER"))
            add("$setCount" to tr("SETS", "SETT"))
            if (volume > 0) add(formatThousands(volume.toLong()) to tr("KG LIFTED", "KG LØFTET"))
        }.take(3)
        val statTop = PAD + 220f
        c.drawRoundRect(RectF(PAD, statTop, W - PAD, statTop + 170f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        val colW = inner / stats.size
        val valuePaint = paint(condensed, 84f, TEXT)
        val labelPaint = paint(semibold, 24f, MUTED, 0.14f)
        stats.forEachIndexed { i, (value, label) ->
            val x = PAD + 40f + i * colW
            c.drawText(fit(value, valuePaint, colW - 48f), x, statTop + 98f, valuePaint)
            c.drawText(fit(label, labelPaint, colW - 48f), x, statTop + 138f, labelPaint)
            if (i > 0) c.drawRect(x - 40f, statTop + 36f, x - 38f, statTop + 134f, Paint().apply { color = LINE })
        }

        // Øvelsene, med figur.
        val namePaint = paint(semibold, 40f, TEXT)
        val setsPaint = paint(regular, 32f, MUTED)
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE_BG }
        val figures = mutableListOf<Pair<IllustrationType, RectF>>()
        var y = headerH
        rows.forEach { r ->
            val ex = ExerciseLibrary.get(r.exerciseId)
            val tile = RectF(PAD, y, PAD + TILE, y + TILE)
            c.drawRoundRect(tile, 34f, 34f, tilePaint)
            if (ex != null) {
                val m = TILE * 0.1f
                figures += illustrationFor(ex) to RectF(tile.left + m, tile.top + m, tile.right - m, tile.bottom - m)
            }
            val textX = PAD + TILE + 36f
            val textW = W - PAD - textX
            c.drawText(fit(ex?.name ?: r.exerciseId, namePaint, textW), textX, y + 54f, namePaint)
            c.drawText(fit(setsSummary(r.sets, ex?.timed == true), setsPaint, textW), textX, y + 100f, setsPaint)
            y += ROW
        }

        // Bunn: en tynn strek i aksentfargen og avsender.
        val footY = height - PAD
        c.drawRoundRect(RectF(PAD, footY - 50f, PAD + 64f, footY - 42f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
        c.drawText(tr("Logged with Rigg", "Logget med Rigg"), PAD, footY, paint(semibold, 28f, MUTED, 0.04f))

        // Figurene tegnes med samme kode som i appen.
        val canvas = androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(W.toFloat(), height.toFloat())) {
            figures.forEach { (type, r) ->
                inset(r.left, r.top, W - r.right, height - r.bottom) {
                    drawFigure(type, strokeColor = Color(TEXT), accentColor = Color(ACCENT))
                }
            }
        }
        return bitmap
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

    private fun formatDate(iso: String): String {
        val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        val text = if (Lang.isNorwegian) {
            d.format(DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", Locale.forLanguageTag("nb")))
        } else {
            d.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
        }
        return text.replaceFirstChar { it.uppercase() }
    }

    private fun writePng(bitmap: Bitmap, out: java.io.OutputStream) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    /** Åpner Android-delingen med bildet (Strava, Instagram, meldinger ...). */
    fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "rigg-okt.png")
        file.outputStream().use { writePng(bitmap, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, tr("Share workout", "Del økta")))
    }

    /** Lagrer bildet i Bilder/Rigg. Krever Android 10 eller nyere (ingen tillatelse trengs der). */
    fun saveToPictures(context: Context, bitmap: Bitmap, date: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "rigg-$date-${System.currentTimeMillis() % 100000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rigg")
        }
        return runCatching {
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { writePng(bitmap, it) } ?: return false
            true
        }.getOrDefault(false)
    }
}

/** Forhåndsvisning av bildet, med knapper for å dele det eller lagre det i Bilder. */
@Composable
fun WorkoutShareDialog(log: SessionLog, vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val key = vm.logKey(log)
    val bitmap = remember(key) { WorkoutShareImage.render(context, log) }
    var saved by remember(key) { mutableStateOf<Boolean?>(null) }
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Share the workout", "Del økta"), style = MaterialTheme.typography.titleLarge)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = tr("Image of the workout", "Bilde av økta"),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    when (saved) {
                        true -> tr("Saved in Pictures/Rigg.", "Lagret i Bilder/Rigg.")
                        false -> tr("Couldn't save the image.", "Kunne ikke lagre bildet.")
                        null -> tr(
                            "Share straight to Strava or another app, or save it and add it to the activity.",
                            "Del rett til Strava eller en annen app, eller lagre bildet og legg det til i aktiviteten.",
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                if (vm.strava.ready) StravaRow(log, vm.stravaUploads[key], onUpload = { vm.uploadToStrava(log) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(tr("Close", "Lukk")) }
                    Spacer(Modifier.weight(1f))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        OutlinedButton(onClick = { saved = WorkoutShareImage.saveToPictures(context, bitmap, log.date) }) {
                            Text(tr("Save", "Lagre"))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { runCatching { WorkoutShareImage.share(context, bitmap) } },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) {
                        Icon(RiggIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Share", "Del"))
                    }
                }
            }
        }
    }
}

/** Strava-status for økta: last opp, laster opp, ferdig (med lenke) eller feil (prøv igjen). */
@Composable
private fun StravaRow(log: SessionLog, upload: StravaUpload?, onUpload: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val doneId = (upload as? StravaUpload.Done)?.activityId ?: log.stravaActivityId
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text("Strava", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    upload is StravaUpload.Working -> tr("Uploading …", "Laster opp …")
                    doneId != null -> tr("Uploaded with the right exercises", "Lastet opp med riktige øvelser")
                    upload is StravaUpload.Failed -> upload.message
                    else -> tr("Upload with exercises and sets", "Last opp med øvelser og sett")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (upload is StravaUpload.Failed && doneId == null) colors.error else colors.onSurfaceVariant,
            )
        }
        when {
            upload is StravaUpload.Working ->
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), strokeWidth = 2.5.dp)
            doneId != null -> TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(StravaClient.activityUrl(doneId)))) }
            }) { Text(tr("Open", "Åpne")) }
            upload is StravaUpload.Failed -> TextButton(onClick = onUpload) { Text(tr("Try again", "Prøv igjen")) }
            else -> TextButton(onClick = onUpload) { Text(tr("Upload", "Last opp")) }
        }
    }
}
