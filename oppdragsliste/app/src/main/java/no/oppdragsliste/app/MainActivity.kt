package no.oppdragsliste.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import no.oppdragsliste.app.ui.Fane
import no.oppdragsliste.app.ui.OrdreSkjerm
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
import no.oppdragsliste.app.ui.KalenderInnstillinger
import no.oppdragsliste.app.ui.KopiInnstillinger
import no.oppdragsliste.app.ui.ListeSkjerm
import no.oppdragsliste.app.ui.MainViewModel
import no.oppdragsliste.app.ui.NaerInnstillinger
import no.oppdragsliste.app.ui.OppdragTema
import no.oppdragsliste.app.ui.PaminnelseDialog
import no.oppdragsliste.app.ui.SamtaleSporsmalDialog
import no.oppdragsliste.app.ui.SamtaleInnstillinger
import no.oppdragsliste.app.ui.SkjemaArk
import no.oppdragsliste.app.ui.VarselInnstillinger
import no.oppdragsliste.app.sted.harPosisjon
import no.oppdragsliste.app.varsler.Varsler

@OptIn(ExperimentalLayoutApi::class)
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
            val kjente by vm.kjente.collectAsStateWithLifecycle()
            val harUtkast by vm.harUtkast.collectAsStateWithLifecycle()
            val fjernet by vm.fjernet.collectAsStateWithLifecycle()
            val paminnelse by vm.paminnelse.collectAsStateWithLifecycle()
            val samtaleSporsmal by vm.samtaleSporsmal.collectAsStateWithLifecycle()
            val geokodStatus by vm.geokoder.collectAsStateWithLifecycle()
            val kopiStatus by vm.kopiStatus.collectAsStateWithLifecycle()
            var visInnstillinger by rememberSaveable { mutableStateOf(false) }
            val fane by vm.fane.collectAsStateWithLifecycle()
            val ordre by vm.ordre.collectAsStateWithLifecycle()
            val ordreImport by vm.ordreImport.collectAsStateWithLifecycle()
            val ordreMelding by vm.ordreMelding.collectAsStateWithLifecycle()

            OppdragTema(oppsett.tema) {
                if (visInnstillinger) {
                    BackHandler { visInnstillinger = false }
                    InnstillingerSkjerm(
                        oppsett = oppsett,
                        onEndre = vm::endreOppsett,
                        onTilbake = { visInnstillinger = false },
                    ) {
                        VarselInnstillinger(oppsett, vm::endreOppsett)
                        KalenderInnstillinger(oppsett, vm::endreOppsett)
                        SamtaleInnstillinger(oppsett, vm::endreOppsett)
                        NaerInnstillinger(oppsett, vm::endreOppsett, geokodStatus, vm::geokodManglende)
                        KopiInnstillinger(kopiStatus, vm::eksporterTil, vm::importerFra)
                    }
                } else {
                    // Faner nederst: Oppdrag og Ordre.
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = fane == Fane.OPPDRAG,
                                    onClick = { vm.visFane(Fane.OPPDRAG) },
                                    icon = { Icon(Icons.Outlined.Build, null) },
                                    label = { Text("Oppdrag") },
                                )
                                NavigationBarItem(
                                    selected = fane == Fane.ORDRE,
                                    onClick = { vm.visFane(Fane.ORDRE) },
                                    icon = {
                                        // Antall som er utført, men ikke fakturert.
                                        val n = ordre.count { it.utfort && !it.fakturert }
                                        BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) {
                                            Icon(Icons.Outlined.Receipt, null)
                                        }
                                    },
                                    label = { Text("Ordre") },
                                )
                            }
                        },
                    ) { fanePadding ->
                        Box(
                            Modifier
                                .padding(bottom = fanePadding.calculateBottomPadding())
                                // Bare bunnen (fanelinjen); toppen trenger skjermene selv for statuslinjen.
                                .consumeWindowInsets(PaddingValues(bottom = fanePadding.calculateBottomPadding())),
                        ) {
                    if (fane == Fane.ORDRE) {
                        BackHandler { vm.visFane(Fane.OPPDRAG) }
                        OrdreSkjerm(
                            ordre = ordre,
                            import = ordreImport,
                            melding = ordreMelding,
                            onMeldingVist = vm::ordreMeldingVist,
                            onImporter = vm::importerOrdreBilder,
                            onBekreftImport = vm::bekreftOrdreImport,
                            onAvbrytImport = vm::avbrytOrdreImport,
                            onUtfort = { o, b -> vm.settOrdreUtfort(o, b) },
                            onFakturert = { o, b -> vm.settOrdreFakturert(o, b) },
                            onLagOppdrag = vm::lagOppdragFraOrdre,
                            onSlett = { vm.slettOrdre(it) },
                            onLagreManuelt = { vm.lagreOrdreManuelt(it) },
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
                        harUtkast = harUtkast,
                        onNyPaminnelse = vm::nyPaminnelse,
                        onEndrePaminnelse = vm::endrePaminnelse,
                        onPaminnelseFerdig = { p, f -> vm.settPaminnelseFerdig(p, f) },
                        naer = naer,
                        onNaer = ::visNaer,
                    )
                    }
                        }
                    }
                }
                samtaleSporsmal?.let { sp -> SamtaleSporsmalDialog(sp) { reg, k -> vm.svarSamtale(reg, k) } }
                paminnelse?.let { p ->
                    PaminnelseDialog(
                        skjema = p,
                        oppdrag = liste.oppdragEtterId.values.filter { !it.ferdig }.sortedByDescending { it.opprettet },
                        oppdragEtterId = liste.oppdragEtterId,
                        onEndre = vm::oppdaterPaminnelse,
                        onLagre = { vm.lagrePaminnelse() },
                        onSlett = vm::slettPaminnelse,
                        onLukk = vm::lukkPaminnelse,
                    )
                }
                SkjemaArk(
                    skjema = skjema,
                    kjente = kjente,
                    onEndre = vm::oppdaterSkjema,
                    onVelgKontakt = vm::velgKontakt,
                    onVelgKunde = vm::velgKunde,
                    onVelgAnlegg = vm::velgAnlegg,
                    onFeltForlatt = vm::fyllFraKjente,
                    onGlem = { k, a -> vm.glemForslag(k, a) },
                    onLagre = { vm.lagreSkjema() },
                    onTom = vm::tomSkjema,
                    fjernet = fjernet?.melding,
                    onAngre = vm::angreFjerning,
                    onLukk = vm::lukkSkjema,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Like etter en samtale: spør om et nytt oppdrag skal registreres.
        vm.sjekkSisteSamtale()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        behandle(intent)
    }

    /** Åpner skjemaet når appen startes fra snarvei, Quick Settings-flis eller samtalevarsel. */
    private fun behandle(intent: Intent?) {
        if (intent?.action == HANDLING_NAER) visNaer(true)
        if (intent?.action == HANDLING_NY_PAMINNELSE) vm.nyPaminnelse()
        // Skjermbilde(r) av ordrelisten delt til appen.
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.importerOrdreBilder(listOf(it)) }
        }
        if (intent?.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.importerOrdreBilder(it) }
        }
        if (intent?.action == HANDLING_NYTT) {
            val varsel = intent.getIntExtra(EKSTRA_VARSEL, -1)
            if (varsel != -1) NotificationManagerCompat.from(this).cancel(varsel)
            val samtale = intent.getLongExtra(EKSTRA_SAMTALE, -1)
            if (samtale != -1L) vm.merkSamtaleSpurt(samtale)
            vm.nyttOppdrag(
                telefon = intent.getStringExtra(EKSTRA_TELEFON).orEmpty(),
                kontaktperson = intent.getStringExtra(EKSTRA_KONTAKT).orEmpty(),
            )
        }
    }

    companion object {
        const val HANDLING_NYTT = "no.oppdragsliste.app.NYTT_OPPDRAG"
        const val HANDLING_NAER = "no.oppdragsliste.app.I_NAERHETEN"
        const val HANDLING_NY_PAMINNELSE = "no.oppdragsliste.app.NY_PAMINNELSE"
        const val EKSTRA_TELEFON = "telefon"
        const val EKSTRA_KONTAKT = "kontakt"
        const val EKSTRA_VARSEL = "varsel"
        const val EKSTRA_SAMTALE = "samtale"
    }
}
