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

/**
 * En pågående økt. Veggklokke-tid slik at den kan lagres og overleve at appen lukkes.
 * Pauser trekkes fra: [pausedAtMs] er satt mens økta står på pause, [pausedTotalMs] er summen av tidligere pauser.
 */
@Serializable
data class ActiveSession(
    val dayTitle: String,
    val startedAtMs: Long,
    val pausedAtMs: Long? = null,
    val pausedTotalMs: Long = 0,
) {
    val paused: Boolean get() = pausedAtMs != null
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        ((pausedAtMs ?: now) - startedAtMs - pausedTotalMs).coerceAtLeast(0)
    fun elapsedSec(now: Long = System.currentTimeMillis()): Int = (elapsedMs(now) / 1000).toInt()
}

/**
 * Hviletimer definert av et sluttidspunkt (elapsedRealtime), ikke en teller som trekkes fra hvert sekund.
 * Da driver den ikke, og den blir riktig selv om noe blir forsinket.
 */
data class RestTimer(
    val totalSeconds: Int,
    val endsAtElapsed: Long,
    /** Satt mens hvilen står på pause: tiden som var igjen da den ble satt på pause. */
    val pausedLeftMs: Long? = null,
) {
    val paused: Boolean get() = pausedLeftMs != null
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = pausedLeftMs ?: (endsAtElapsed - now)
    fun secondsLeft(now: Long = SystemClock.elapsedRealtime()): Int =
        ceil(remainingMs(now).coerceAtLeast(0) / 1000.0).toInt()
}

/** Hva en arbeidsnedtelling gjelder: en oppvarmingsøvelse eller et tidssett (planke, veggsitt ...). */
enum class WorkKind { WARMUP, SET }

/**
 * Hvor man er i et tidssett som tas én side om gangen (sideplanke ...): første side, bytte (kort pause), andre side.
 * NONE = vanlig sett.
 */
enum class WorkSide { NONE, FIRST, SWITCH, SECOND }

/**
 * Nedtelling for oppvarming og tidsøvelser. Lever her (ikke i skjermen), så den fortsetter når øktmodus skjules,
 * og tjenesten piper selv med skjermen av. [index] er oppvarmingsøvelsen eller settet (steget) den hører til.
 * [finished] settes av tjenesten ved null; AppViewModel går da videre (neste øvelse eller logger settet).
 */
data class WorkTimer(
    val kind: WorkKind,
    val label: String,
    val index: Int,
    val totalSeconds: Int,
    val endsAtElapsed: Long,
    val pausedLeftMs: Long? = null,
    val weightKg: Double? = null,
    val finished: Boolean = false,
    val side: WorkSide = WorkSide.NONE,
    /** Sekundene per side (for [side] != NONE); det er dette som logges. */
    val holdSeconds: Int = totalSeconds,
    /** Øvelsens navn, uten side-tillegget i [label]. */
    val name: String = label,
) {
    val paused: Boolean get() = pausedLeftMs != null
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long =
        if (finished) 0 else pausedLeftMs ?: (endsAtElapsed - now)
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
    var work by mutableStateOf<WorkTimer?>(null)
        private set

    /** Satt av tjenesten mens den kjører, så endringer slår inn med en gang i stedet for ved neste tikk. */
    internal var serviceListener: (() -> Unit)? = null

    private var initialized = false

    val isActive: Boolean get() = rest != null || session != null || work != null

    /** Økta (eller hvilen, hvis den går uten økt) står på pause. */
    val paused: Boolean get() = session?.paused ?: rest?.paused ?: work?.paused ?: false

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
        // Står økta på pause, starter hvilen også på pause.
        val pausedLeft = if (session?.paused == true) seconds * 1000L else null
        rest = RestTimer(seconds, SystemClock.elapsedRealtime() + seconds * 1000L, pausedLeft)
        changed(context)
    }

    fun addRest(context: Context, deltaSeconds: Int) {
        val r = rest ?: return
        val left = r.pausedLeftMs
        if (left != null) {
            val newLeft = left + deltaSeconds * 1000L
            rest = if (newLeft <= 0) null else r.copy(
                pausedLeftMs = newLeft,
                totalSeconds = maxOf(r.totalSeconds, ((newLeft + 999) / 1000).toInt()),
            )
            changed(context)
            return
        }
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

    /** Sekunder til å bytte side i tidsøvelser som tas én side om gangen. */
    const val SWITCH_SECONDS = 5

    /**
     * Starter en nedtelling for oppvarming eller et tidssett. Står økta på pause, starter den på pause.
     * [perSide]: først [seconds] på den ene siden, så [SWITCH_SECONDS] til å bytte, så [seconds] på den andre.
     */
    fun startWork(
        context: Context, kind: WorkKind, label: String, index: Int, seconds: Int, weightKg: Double? = null,
        perSide: Boolean = false,
    ) {
        if (seconds <= 0) return
        val first = WorkTimer(kind, label, index, seconds, 0, null, weightKg, holdSeconds = seconds, name = label)
        work = phase(if (perSide) first.copy(side = WorkSide.FIRST) else first, if (perSide) WorkSide.FIRST else WorkSide.NONE)
        changed(context)
    }

    /** [w] klar til å telle ned i fasen [side], med riktig lengde og etikett. */
    private fun phase(w: WorkTimer, side: WorkSide): WorkTimer {
        val seconds = if (side == WorkSide.SWITCH) SWITCH_SECONDS else w.holdSeconds
        val label = when (side) {
            WorkSide.NONE -> w.name
            WorkSide.FIRST -> w.name + tr(" · side 1", " · side 1")
            WorkSide.SWITCH -> tr("Switch sides", "Bytt side")
            WorkSide.SECOND -> w.name + tr(" · side 2", " · side 2")
        }
        val ms = seconds * 1000L
        val pausedLeft = if (session?.paused == true) ms else null
        return w.copy(
            side = side, label = label, totalSeconds = seconds,
            endsAtElapsed = SystemClock.elapsedRealtime() + ms, pausedLeftMs = pausedLeft, finished = false,
        )
    }

    fun stopWork(context: Context) {
        if (work == null) return
        work = null
        changed(context)
    }

    /**
     * Kalles av tjenesten når nedtellingen når null (lyd og vibrasjon er allerede spilt). Tas settet én side om
     * gangen, går den videre til bytte og andre side her, så det skjer selv med skjermen av.
     */
    internal fun finishWork() {
        val w = work ?: return
        work = when (w.side) {
            WorkSide.FIRST -> phase(w, WorkSide.SWITCH)
            WorkSide.SWITCH -> phase(w, WorkSide.SECOND)
            WorkSide.NONE, WorkSide.SECOND -> w.copy(finished = true)
        }
    }

    /** Henter og fjerner en ferdig nedtelling, så den bare behandles én gang. */
    fun takeFinishedWork(context: Context): WorkTimer? {
        val w = work?.takeIf { it.finished } ?: return null
        work = null
        changed(context)
        return w
    }

    /** Setter øktklokka, hvilen og en eventuell oppvarming/tidsøvelse på pause. */
    fun pause(context: Context) {
        val s = session
        if (s != null && !s.paused) setSession(context, s.copy(pausedAtMs = System.currentTimeMillis()))
        val r = rest
        if (r != null && !r.paused) rest = r.copy(pausedLeftMs = r.remainingMs().coerceAtLeast(0))
        val w = work
        if (w != null && !w.paused && !w.finished) work = w.copy(pausedLeftMs = w.remainingMs().coerceAtLeast(0))
        changed(context)
    }

    /** Fortsetter etter [pause]: pausen trekkes fra økttiden, og hvilen fortsetter der den stoppet. */
    fun resume(context: Context) {
        val s = session
        val at = s?.pausedAtMs
        if (s != null && at != null) {
            val pausedFor = (System.currentTimeMillis() - at).coerceAtLeast(0)
            setSession(context, s.copy(pausedAtMs = null, pausedTotalMs = s.pausedTotalMs + pausedFor))
        }
        val r = rest
        val left = r?.pausedLeftMs
        if (r != null && left != null) {
            rest = r.copy(endsAtElapsed = SystemClock.elapsedRealtime() + left, pausedLeftMs = null)
        }
        val w = work
        val workLeft = w?.pausedLeftMs
        if (w != null && workLeft != null) {
            work = w.copy(endsAtElapsed = SystemClock.elapsedRealtime() + workLeft, pausedLeftMs = null)
        }
        changed(context)
    }

    private fun setSession(context: Context, s: ActiveSession) {
        session = s
        Store(context).saveActiveSession(s)
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
