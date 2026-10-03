package no.rigg.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Innstillinger for opplasting til Strava. Du lager selv en (gratis) API-app på strava.com/settings/api og
 * limer inn Client ID og Client Secret. Nøklene lagres bare på telefonen og er ikke med i backup-filen.
 */
@Serializable
data class StravaSettings(
    /** Hovedbryteren: av = Rigg gjør ingenting mot Strava. */
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    /** Når access-tokenet går ut (sekunder siden 1970). */
    val expiresAt: Long = 0,
    val athleteName: String = "",
    /** Last opp automatisk når en økt lagres. Av = bare med knappen. */
    val autoUpload: Boolean = false,
) {
    val connected: Boolean get() = refreshToken.isNotBlank()
    val ready: Boolean get() = enabled && connected
}

/** Hvordan det gikk med én opplasting. */
sealed class StravaUpload {
    /** Venter på at klokka synker pulsen til Health Connect før opplastingen. */
    data object WaitingForHeartRate : StravaUpload()
    data object Working : StravaUpload()
    data class Done(val activityId: Long) : StravaUpload()
    data class Failed(val message: String) : StravaUpload()
}

/**
 * Snakker med Strava sitt API: innlogging (OAuth) og opplasting av økter som FIT-filer ([StrengthFit]).
 * Strava leser settene i fila og tegner muskelkartet ut fra øvelsene.
 */
object StravaClient {
    /** Adressen Strava sender deg tilbake til etter innlogging. Fanges av MainActivity (se manifestet). */
    const val REDIRECT_URI = "rigg://localhost/strava"
    private const val BASE = "https://www.strava.com"

    fun authorizeUri(clientId: String): Uri = Uri.parse("$BASE/oauth/mobile/authorize").buildUpon()
        .appendQueryParameter("client_id", clientId.trim())
        .appendQueryParameter("redirect_uri", REDIRECT_URI)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("approval_prompt", "auto")
        .appendQueryParameter("scope", "activity:write,read")
        .build()

    /** Bytter koden fra innloggingen mot tokens. */
    suspend fun exchangeCode(settings: StravaSettings, code: String): StravaSettings = withContext(Dispatchers.IO) {
        val body = postForm(
            "$BASE/oauth/token",
            mapOf(
                "client_id" to settings.clientId.trim(),
                "client_secret" to settings.clientSecret.trim(),
                "code" to code,
                "grant_type" to "authorization_code",
            ),
        )
        val athlete = body.optJSONObject("athlete")
        val name = listOfNotNull(athlete?.optString("firstname"), athlete?.optString("lastname"))
            .filter { it.isNotBlank() }.joinToString(" ")
        withTokens(settings, body).copy(athleteName = name)
    }

    /** Gyldig access-token, fornyet hvis det snart går ut. Returnerer også innstillingene som skal lagres. */
    suspend fun freshToken(settings: StravaSettings): StravaSettings = withContext(Dispatchers.IO) {
        if (settings.accessToken.isNotBlank() && settings.expiresAt > System.currentTimeMillis() / 1000 + 120) {
            return@withContext settings
        }
        val body = postForm(
            "$BASE/oauth/token",
            mapOf(
                "client_id" to settings.clientId.trim(),
                "client_secret" to settings.clientSecret.trim(),
                "grant_type" to "refresh_token",
                "refresh_token" to settings.refreshToken,
            ),
        )
        withTokens(settings, body)
    }

    /**
     * Laster opp FIT-fila og venter (opptil ca. 30 sekunder) til Strava har laget aktiviteten.
     * Returnerer aktivitets-id-en.
     */
    suspend fun upload(token: String, fit: ByteArray, name: String, description: String, externalId: String): Long =
        withContext(Dispatchers.IO) {
            val boundary = "rigg" + System.currentTimeMillis()
            val out = ByteArrayOutputStream()
            fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
            fun field(key: String, value: String) {
                text("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n")
            }
            field("data_type", "fit")
            field("name", name)
            field("description", description)
            field("external_id", externalId)
            text("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$externalId.fit\"\r\n")
            text("Content-Type: application/octet-stream\r\n\r\n")
            out.write(fit)
            text("\r\n--$boundary--\r\n")

            val conn = open("$BASE/api/v3/uploads", "POST", token)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.doOutput = true
            conn.outputStream.use { it.write(out.toByteArray()) }
            var status = readJson(conn)

            // Strava behandler fila i bakgrunnen: spør til aktiviteten finnes eller det kom en feil.
            repeat(15) {
                status.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { error ->
                    // Allerede lastet opp (samme external_id eller fil): bruk den eksisterende aktiviteten.
                    Regex("""duplicate of.*?(\d+)""").find(error)?.let { return@withContext it.groupValues[1].toLong() }
                    throw StravaException(error)
                }
                val activityId = status.optLong("activity_id", 0)
                if (activityId > 0) return@withContext activityId
                delay(2000)
                status = readJson(open("$BASE/api/v3/uploads/${status.getLong("id")}", "GET", token))
            }
            throw StravaException(
                tr("Strava is still processing the workout. Check Strava in a moment.", "Strava behandler fortsatt økta. Sjekk Strava om litt."),
            )
        }

    /** Tittel og beskrivelse til aktiviteten: settene i klartekst. */
    fun describe(log: SessionLog): String = buildString {
        log.results.filter { it.sets.isNotEmpty() }.forEach { r ->
            val ex = ExerciseLibrary.get(r.exerciseId)
            append(ex?.name ?: r.exerciseId).append(": ").append(setsSummary(r.sets, ex?.timed == true)).append('\n')
        }
        append(tr("Logged with Rigg", "Logget med Rigg"))
    }

    fun activityUrl(id: Long) = "$BASE/activities/$id"

    private fun withTokens(settings: StravaSettings, body: JSONObject) = settings.copy(
        accessToken = body.getString("access_token"),
        refreshToken = body.optString("refresh_token").ifBlank { settings.refreshToken },
        expiresAt = body.optLong("expires_at", 0),
    )

    private fun open(url: String, method: String, token: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun postForm(url: String, params: Map<String, String>): JSONObject {
        val conn = open(url, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val form = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        conn.outputStream.use { it.write(form.toByteArray()) }
        return readJson(conn)
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text) }.getOrNull()?.let { j ->
                j.optString("message").ifBlank { j.optString("error") }
            }.orEmpty()
            throw StravaException(
                when (code) {
                    401 -> tr("Strava rejected the login. Connect again under More.", "Strava avviste innloggingen. Koble til på nytt under Mer.")
                    else -> tr("Strava answered $code", "Strava svarte $code") + if (msg.isNotBlank()) ": $msg" else ""
                },
            )
        }
        return JSONObject(text)
    }
}

class StravaException(message: String) : Exception(message)
