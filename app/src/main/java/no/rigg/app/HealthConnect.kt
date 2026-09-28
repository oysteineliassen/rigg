package no.rigg.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

/**
 * Skriver fullførte økter til Health Connect (styrketrening, med start, slutt og dagens navn),
 * så de dukker opp i andre helse- og treningsapper. Appen ber bare om å skrive, aldri lese.
 */
object HealthConnectSync {
    val permissions: Set<String> = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    /** SDK_AVAILABLE, SDK_UNAVAILABLE eller SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean = status(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermission(context: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    suspend fun writeWorkout(context: Context, title: String, start: Instant, end: Instant): Result<Unit> = runCatching {
        check(isAvailable(context)) { tr("Health Connect isn't available", "Health Connect er ikke tilgjengelig") }
        check(hasPermission(context)) { tr("Permission is missing. Turn it on again under More.", "Mangler tillatelse. Slå det på igjen under Mer.") }
        val rules = ZoneId.systemDefault().rules
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = rules.getOffset(start),
            endTime = end,
            endZoneOffset = rules.getOffset(end),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = title,
            metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE)),
        )
        HealthConnectClient.getOrCreate(context).insertRecords(listOf(record))
        Unit
    }

    /** Åpner Play Store for å installere eller oppdatere Health Connect (trengs på Android 13 og eldre). */
    fun openInstall(context: Context) {
        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage("com.android.vending"))
        }
    }
}

/**
 * Personvernforklaringen Health Connect viser når brukeren trykker på lenken i tillatelsesdialogen.
 * Health Connect krever at appen har en slik skjerm før den får be om tillatelse.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiggTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(tr("Rigg and Health Connect", "Rigg og Health Connect"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            tr(
                                "Rigg only writes data: when you log a workout that was timed with the workout clock, " +
                                    "Rigg saves a strength training session (start, end and the day's name) to Health Connect. " +
                                    "Rigg doesn't read anything from Health Connect and doesn't send data anywhere else.",
                                "Rigg skriver bare: når du logger en økt som er tatt tid på med øktklokka, lagrer Rigg en " +
                                    "styrketreningsøkt (start, slutt og navnet på dagen) i Health Connect. Rigg leser ingenting " +
                                    "fra Health Connect og sender ikke data noe annet sted.",
                            ),
                        )
                        Text(
                            tr(
                                "You can turn this off under More in the app, or remove the permission in Health Connect.",
                                "Du kan slå det av under Mer i appen, eller fjerne tillatelsen i Health Connect.",
                            ),
                        )
                        Button(onClick = { finish() }) { Text(tr("Close", "Lukk")) }
                    }
                }
            }
        }
    }
}
