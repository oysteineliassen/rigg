package no.rigg.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Forgrunnstjeneste som kjører så lenge en økt eller en hvile pågår.
 *
 * Hvorfor: en vanlig nedtelling i appen blir satt på pause av Android når skjermen er av, så lyden
 * kom for sent eller ikke i det hele tatt. Tjenesten holder prosessen i live (også mens du ser en
 * video i YouTube), og under selve hvilen holdes en wake lock slik at pipene kommer presist.
 * Varselet viser en klokke som systemet selv teller, så det trenger ikke oppdateres hvert sekund.
 */
class WorkoutTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Hvilken hvile vi har pipet for, og hvilket sekund vi pipet sist (så hvert pip bare kommer én gang).
    private var cueForEndsAt = Long.MIN_VALUE
    private var lastCueSecond = Int.MAX_VALUE

    private var lastNotificationKey: String? = null

    // Samme objekt hver gang, så onDestroy bare fjerner sin egen lytter (ikke en ny instans sin).
    private val listener: () -> Unit = { restartLoop() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutTimers.init(this)
        ensureChannel(this)
        WorkoutTimers.serviceListener = listener
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground() // må skje raskt etter startForegroundService
        WorkoutTimers.serviceListener = listener
        restartLoop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (WorkoutTimers.serviceListener === listener) WorkoutTimers.serviceListener = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        val notification = buildNotification()
        lastNotificationKey = notificationKey()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun restartLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (true) {
                val rest = WorkoutTimers.rest
                if (rest == null && WorkoutTimers.session == null) break
                val waitMs = if (rest != null) handleRest(rest) else {
                    releaseWakeLock()
                    1000L
                }
                updateNotificationIfChanged()
                delay(waitMs) // kaster ved cancel, så oppryddingen under kjører bare når alt er ferdig
            }
            // Gi siste tone tid til å spille ferdig (wake lock holdes fortsatt) før prosessen kan fryses.
            // Startes noe nytt i mellomtiden, avbrytes denne jobben og tjenesten fortsetter.
            delay(800)
            // Slipp lytteren: starter du noe nytt mens tjenesten avsluttes, startes den på nytt i stedet.
            if (WorkoutTimers.serviceListener === listener) WorkoutTimers.serviceListener = null
            releaseWakeLock()
            ServiceCompat.stopForeground(this@WorkoutTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Spiller pip ved 3, 2 og 1 sekund igjen og en høyere tone ved 0. Returnerer ventetid til neste sekundskifte. */
    private fun handleRest(rest: RestTimer): Long {
        if (rest.paused) {
            // På pause: ingen pip og ingen wake lock, bare se etter endringer en gang i sekundet.
            releaseWakeLock()
            return 1000L
        }
        if (rest.endsAtElapsed != cueForEndsAt) {
            cueForEndsAt = rest.endsAtElapsed
            lastCueSecond = Int.MAX_VALUE
        }
        val remaining = rest.remainingMs()
        acquireWakeLock(remaining.coerceAtLeast(0) + 15_000)

        if (remaining <= 0) {
            Beeper.go()
            Haptics.vibrate(this, 500)
            WorkoutTimers.finishRest()
            // Wake lock slippes av løkka: med en gang hvis økta fortsetter, ellers etter at tonen er ferdig.
            return 0L
        }

        val secondsLeft = rest.secondsLeft()
        if (secondsLeft <= 3 && secondsLeft < lastCueSecond) {
            Beeper.countdown()
            Haptics.vibrate(this, 60)
            lastCueSecond = secondsLeft
        }

        // Våkn rett etter neste hele sekund (regnet fra slutten), så pipene treffer sekundskiftene.
        val toBoundary = remaining % 1000
        return (if (toBoundary == 0L) 1000L else toBoundary) + 5
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        val wl = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Rigg:restTimer")
            .also { it.setReferenceCounted(false); wakeLock = it }
        wl.acquire(timeoutMs.coerceAtLeast(1000))
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun notificationKey(): String {
        val r = WorkoutTimers.rest
        val s = WorkoutTimers.session
        return "${r?.endsAtElapsed}|${r?.pausedLeftMs}|${s?.startedAtMs}|${s?.pausedAtMs}|${s?.pausedTotalMs}"
    }

    private fun updateNotificationIfChanged() {
        val key = notificationKey()
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val rest = WorkoutTimers.rest
        val session = WorkoutTimers.session
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rigg)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp)
        when {
            rest != null && rest.paused -> builder
                .setContentTitle(tr("Rest paused", "Hvilen er på pause"))
                .setContentText(tr("${formatDuration(rest.secondsLeft())} left", "${formatDuration(rest.secondsLeft())} igjen"))
                .setShowWhen(false)
            session != null && session.paused -> builder
                .setContentTitle(tr("Workout paused", "Økta er på pause"))
                .setContentText("${session.dayTitle} · ${formatDuration(session.elapsedSec())}")
                .setShowWhen(false)
            rest != null -> builder
                .setContentTitle(tr("Rest", "Hvile"))
                .setContentText(session?.let { tr("Workout: ${it.dayTitle}", "Økt: ${it.dayTitle}") } ?: tr("Rest in progress", "Pausen er i gang"))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + rest.remainingMs().coerceAtLeast(0))
            session != null -> builder
                .setContentTitle(tr("Workout in progress", "Økt pågår"))
                .setContentText(session.dayTitle)
                .setShowWhen(true)
                .setUsesChronometer(true)
                // Klokka i varselet teller fra et tenkt starttidspunkt, så pausene ikke telles med.
                .setWhen(System.currentTimeMillis() - session.elapsedMs())
            else -> builder.setContentTitle("Rigg")
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "treningsokt"
        private const val NOTIFICATION_ID = 4242

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WorkoutTimerService::class.java)) }
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Lav viktighet: varselet skal ikke lage lyd selv, pipene kommer fra Beeper.
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, tr("Active workout", "Pågående økt"), NotificationManager.IMPORTANCE_LOW))
        }
    }
}
