package no.oppdragsliste.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.oppdragsliste.app.ui.InnstillingerSkjerm
import no.oppdragsliste.app.ui.KopiInnstillinger
import no.oppdragsliste.app.ui.ListeSkjerm
import no.oppdragsliste.app.ui.MainViewModel
import no.oppdragsliste.app.ui.NaerInnstillinger
import no.oppdragsliste.app.ui.OppdragTema
import no.oppdragsliste.app.ui.SamtaleInnstillinger
import no.oppdragsliste.app.ui.SkjemaArk
import no.oppdragsliste.app.ui.VarselInnstillinger
import no.oppdragsliste.app.sted.harPosisjon
import no.oppdragsliste.app.varsler.Varsler

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val varselTillatelse = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val posisjonTillatelse = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.visNaer(true)
    }

    /** «I nærheten»: be om posisjon først hvis vi ikke har den. */
    private fun visNaer(pa: Boolean) {
        if (pa && !harPosisjon(this)) {
            posisjonTillatelse.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            vm.visNaer(pa)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            behandle(intent)
            app.planleggAltSenere(tvingGeofencer = true)
            if (!Varsler.kanVarsle(this) && Build.VERSION.SDK_INT >= 33) {
                varselTillatelse.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val oppsett by vm.oppsett.collectAsStateWithLifecycle()
            val liste by vm.liste.collectAsStateWithLifecycle()
            val sok by vm.sok.collectAsStateWithLifecycle()
            val skjema by vm.skjema.collectAsStateWithLifecycle()
            val naer by vm.naer.collectAsStateWithLifecycle()
            val geokodStatus by vm.geokoder.collectAsStateWithLifecycle()
            val kopiStatus by vm.kopiStatus.collectAsStateWithLifecycle()
            var visInnstillinger by rememberSaveable { mutableStateOf(false) }

            OppdragTema(oppsett.tema) {
                if (visInnstillinger) {
                    BackHandler { visInnstillinger = false }
                    InnstillingerSkjerm(
                        oppsett = oppsett,
                        onEndre = vm::endreOppsett,
                        onTilbake = { visInnstillinger = false },
                    ) {
                        VarselInnstillinger(oppsett, vm::endreOppsett)
                        SamtaleInnstillinger(oppsett, vm::endreOppsett)
                        NaerInnstillinger(oppsett, vm::endreOppsett, geokodStatus, vm::geokodManglende)
                        KopiInnstillinger(kopiStatus, vm::eksporterTil, vm::importerFra)
                    }
                } else {
                    ListeSkjerm(
                        tilstand = liste,
                        sok = sok,
                        onSok = vm::settSok,
                        onNytt = { vm.nyttOppdrag() },
                        onEndre = vm::endre,
                        onFerdig = { o, f -> vm.settFerdig(o, f) },
                        onSlett = { vm.slett(it) },
                        onInnstillinger = { visInnstillinger = true },
                        naer = naer,
                        onNaer = ::visNaer,
                    )
                }
                skjema?.let { s ->
                    SkjemaArk(
                        skjema = s,
                        onEndre = vm::oppdaterSkjema,
                        onLagre = { vm.lagreSkjema() },
                        onLukk = vm::lukkSkjema,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        behandle(intent)
    }

    /** Åpner skjemaet når appen startes fra snarvei, Quick Settings-flis eller samtalevarsel. */
    private fun behandle(intent: Intent?) {
        if (intent?.action == HANDLING_NAER) visNaer(true)
        if (intent?.action == HANDLING_NYTT) {
            val varsel = intent.getIntExtra(EKSTRA_VARSEL, -1)
            if (varsel != -1) NotificationManagerCompat.from(this).cancel(varsel)
            vm.nyttOppdrag(
                telefon = intent.getStringExtra(EKSTRA_TELEFON).orEmpty(),
                kunde = intent.getStringExtra(EKSTRA_KUNDE).orEmpty(),
            )
        }
    }

    companion object {
        const val HANDLING_NYTT = "no.oppdragsliste.app.NYTT_OPPDRAG"
        const val HANDLING_NAER = "no.oppdragsliste.app.I_NAERHETEN"
        const val EKSTRA_TELEFON = "telefon"
        const val EKSTRA_KUNDE = "kunde"
        const val EKSTRA_VARSEL = "varsel"
    }
}
