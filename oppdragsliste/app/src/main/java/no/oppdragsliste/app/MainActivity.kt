package no.oppdragsliste.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.oppdragsliste.app.ui.InnstillingerSkjerm
import no.oppdragsliste.app.ui.ListeSkjerm
import no.oppdragsliste.app.ui.MainViewModel
import no.oppdragsliste.app.ui.OppdragTema
import no.oppdragsliste.app.ui.SkjemaArk

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) behandle(intent)

        setContent {
            val oppsett by vm.oppsett.collectAsStateWithLifecycle()
            val liste by vm.liste.collectAsStateWithLifecycle()
            val sok by vm.sok.collectAsStateWithLifecycle()
            val skjema by vm.skjema.collectAsStateWithLifecycle()
            var visInnstillinger by rememberSaveable { mutableStateOf(false) }

            OppdragTema(oppsett.tema) {
                if (visInnstillinger) {
                    BackHandler { visInnstillinger = false }
                    InnstillingerSkjerm(
                        oppsett = oppsett,
                        onEndre = vm::endreOppsett,
                        onTilbake = { visInnstillinger = false },
                    )
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
                        naer = null,
                        onNaer = null,
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
        if (intent?.action == HANDLING_NYTT) {
            vm.nyttOppdrag(
                telefon = intent.getStringExtra(EKSTRA_TELEFON).orEmpty(),
                kunde = intent.getStringExtra(EKSTRA_KUNDE).orEmpty(),
            )
        }
    }

    companion object {
        const val HANDLING_NYTT = "no.oppdragsliste.app.NYTT_OPPDRAG"
        const val EKSTRA_TELEFON = "telefon"
        const val EKSTRA_KUNDE = "kunde"
    }
}
