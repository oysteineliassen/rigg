package no.rigg.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** En pågående økt. Veggklokke-tid slik at den kan lagres og overleve at appen lukkes. */
@Serializable
data class ActiveSession(val dayTitle: String, val startedAtMs: Long) {
    fun elapsedSec(now: Long = System.currentTimeMillis()): Int = ((now - startedAtMs) / 1000).coerceAtLeast(0).toInt()
}

/**
 * Hviletimer definert av et sluttidspunkt (elapsedRealtime), ikke en teller som trekkes fra hvert sekund.
 * Da driver den ikke, og den blir riktig selv om noe blir forsinket.
 */
data class RestTimer(val totalSeconds: Int, val endsAtElapsed: Long) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = endsAtElapsed - now
    fun secondsLeft(now: Long = SystemClock.elapsedRealtime()): Int =
        ceil(remainingMs(now).coerceAtLeast(0) / 1000.0).toInt()
}

/**
 * Felles tilstand for hviletimer og øktklokke. Lever i prosessen (ikke i ViewModel) slik at
 * [WorkoutTimerService] kan kjøre nedtellingen videre når skjermen er av eller appen er i bakgrunnen.
 * Alle kall skjer på hovedtråden.
 */
object WorkoutTimers {
    /** Økter eldre enn dette antas glemt og forkastes ved oppstart. */
    private const val MAX_SESSION_MS = 12L * 60 * 60 * 1000

    var rest by mutableStateOf<RestTimer?>(null)
        private set
    var session by mutableStateOf<ActiveSession?>(null)
        private set

    /** Satt av tjenesten mens den kjører, så endringer slår inn med en gang i stedet for ved neste tikk. */
    internal var serviceListener: (() -> Unit)? = null

    private var initialized = false

    val isActive: Boolean get() = rest != null || session != null

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val store = Store(context)
        val saved = store.loadActiveSession()
        val age = saved?.let { System.currentTimeMillis() - it.startedAtMs }
        if (saved != null && age != null && age in 0..MAX_SESSION_MS) {
            session = saved
        } else if (saved != null) {
            store.saveActiveSession(null)
        }
    }

    fun startRest(context: Context, seconds: Int) {
        if (seconds <= 0) return
        rest = RestTimer(seconds, SystemClock.elapsedRealtime() + seconds * 1000L)
        changed(context)
    }

    fun addRest(context: Context, deltaSeconds: Int) {
        val r = rest ?: return
        val newEnd = r.endsAtElapsed + deltaSeconds * 1000L
        rest = if (newEnd <= SystemClock.elapsedRealtime()) {
            null
        } else {
            val newTimer = r.copy(endsAtElapsed = newEnd)
            newTimer.copy(totalSeconds = maxOf(r.totalSeconds, newTimer.secondsLeft()))
        }
        changed(context)
    }

    fun stopRest(context: Context) {
        rest = null
        changed(context)
    }

    /** Kalles av tjenesten når nedtellingen er ferdig (lyd og vibrasjon er allerede spilt). */
    internal fun finishRest() {
        rest = null
    }

    fun startSession(context: Context, dayTitle: String) {
        val s = ActiveSession(dayTitle, System.currentTimeMillis())
        session = s
        Store(context).saveActiveSession(s)
        changed(context)
    }

    fun stopSession(context: Context) {
        session = null
        Store(context).saveActiveSession(null)
        changed(context)
    }

    /** Sørger for at tjenesten kjører hvis noe er aktivt, eller får beskjed om å oppdatere seg. */
    fun ensureService(context: Context) = changed(context)

    private fun changed(context: Context) {
        val listener = serviceListener
        when {
            listener != null -> listener()
            isActive -> WorkoutTimerService.start(context)
        }
    }
}

/**
 * Syntetiserte pip (ingen lydfiler). Mario Kart-stil: tre like korte pip, så ett lengre en oktav opp.
 * Juster tonene her hvis du vil ha dem lysere/mørkere.
 */
object Beeper {
    private const val SAMPLE_RATE = 44_100
    private const val COUNT_HZ = 784.0 // G5
    private const val GO_HZ = 1568.0 // G6, en oktav over
    private const val COUNT_MS = 170
    private const val GO_MS = 600

    private val countSamples by lazy { tone(COUNT_HZ, COUNT_MS) }
    private val goSamples by lazy { tone(GO_HZ, GO_MS) }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun countdown() = play(countSamples)
    fun go() = play(goSamples)

    private fun tone(freq: Double, durationMs: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val fade = SAMPLE_RATE * 10 / 1000 // 10 ms inn/ut, ellers klikker det
        return ShortArray(n) { i ->
            val envelope = minOf(1.0, i / fade.toDouble(), (n - 1 - i) / fade.toDouble())
            (sin(2 * PI * freq * i / SAMPLE_RATE) * envelope * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun play(samples: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Alarm-kanalen: høres selv om telefonen står på lydløs, styres av alarmvolumet.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            val durationMs = samples.size * 1000L / SAMPLE_RATE
            mainHandler.postDelayed({ runCatching { track.stop(); track.release() } }, durationMs + 200)
        }
    }
}

object Haptics {
    fun vibrate(context: Context, ms: Long) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
