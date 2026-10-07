package no.oppdragsliste.app

import android.app.Application
import no.oppdragsliste.app.data.AppDatabase
import no.oppdragsliste.app.data.Innstillinger
import no.oppdragsliste.app.data.OppdragRepository

class OppdragApp : Application() {
    val database by lazy { AppDatabase.bygg(this) }
    val innstillinger by lazy { Innstillinger(this) }
    val repository by lazy { OppdragRepository(database.oppdragDao()) }
}

val android.content.Context.app: OppdragApp get() = applicationContext as OppdragApp
