package no.oppdragsliste.app

import android.app.Application
import kotlinx.coroutines.launch
import no.oppdragsliste.app.data.AppDatabase
import no.oppdragsliste.app.data.Innstillinger
import no.oppdragsliste.app.data.OppdragRepository
import no.oppdragsliste.app.kalender.Kalender
import no.oppdragsliste.app.sted.Geofencer
import no.oppdragsliste.app.varsler.Planlegger
import no.oppdragsliste.app.varsler.Varsler
import no.oppdragsliste.app.varsler.mottakerOmfang

class OppdragApp : Application() {
    val database by lazy { AppDatabase.bygg(this) }
    val innstillinger by lazy { Innstillinger(this) }
    val repository by lazy { OppdragRepository(database.oppdragDao(), database.kjentAnleggDao(), database.paminnelseDao()) { planleggAlt() } }

    override fun onCreate() {
        super.onCreate()
        Varsler.opprettKanaler(this)
    }

    /** Planlegger påminnelser (og geofencer) på nytt ut fra oppdragene og innstillingene. */
    suspend fun planleggAlt(tvingGeofencer: Boolean = false) {
        Planlegger.planleggAlle(this)
        Geofencer.synk(this, tvingGeofencer)
        Kalender.synk(this)
    }

    /** Som [planleggAlt], men fra kode som ikke kjører i en korutine. */
    fun planleggAltSenere(tvingGeofencer: Boolean = false) {
        mottakerOmfang.launch { planleggAlt(tvingGeofencer) }
    }
}

val android.content.Context.app: OppdragApp get() = applicationContext as OppdragApp
