# Rigg: oppsett

1. Åpne prosjektmappen i Android Studio (File > Open). La Gradle synkronisere.
2. Kjør på telefon eller emulator (minSdk 26).

Pakkenavnet (applicationId) er `no.rigg.app`. Det kan ikke endres etter at appen er lagt ut på Play Store.

## Git
Prosjektet er et git-repo. Endringer fra Claude kommer som enkeltfiler (eller en liten zip) som erstatter
filene i prosjektet. Kjør deretter i Terminal-fanen nederst i Android Studio:

    git add -A
    git commit -m "Kort beskrivelse"

Da blir endringen en egen commit som du kan se i Git-fanen, og angre med `git revert` hvis noe er galt.
Har du endret noe selv, commit det først, så holdes endringene adskilt.

## GitHub: automatisk bygging
`.github/workflows/build.yml` bygger appen, kjører testene og sjekker figurene hver gang du pusher.
Den ferdige APK-en legges ut under **Releases** i repoet (for eksempel `rigg-42.apk`), så du kan laste
den ned rett på telefonen. Feiler bygget, står feilmeldingen under **Actions**.

Første gang:
1. Lag et **privat** repo på github.com (uten README).
2. I Terminal-fanen i Android Studio:

       git remote add origin https://github.com/BRUKERNAVN/rigg.git
       git push -u origin HEAD:main

3. Etter det holder det med `git push` etter hver commit.

## Signeringsnøkkel
For at apper bygget på GitHub og i Android Studio skal kunne installeres over hverandre, må de signeres
med samme nøkkel. Kopier Android Studios debug-nøkkel inn i prosjektet og commit den:

    copy %USERPROFILE%\.android\debug.keystore app\rigg-debug.keystore

Da beholder også appen som allerede er på telefonen din dataene sine ved oppdatering. (En debug-nøkkel er
ikke hemmelig, men hold likevel repoet privat.) Versjonsnummeret er antall commits, så en nyere versjon
alltid kan installeres over en eldre.

For Play Store trengs en egen release-nøkkel. Den leses fra `keystore.properties` (ikke i git) eller fra
miljøvariablene `RIGG_KEYSTORE`, `RIGG_KEYSTORE_PASSWORD`, `RIGG_KEY_ALIAS` og `RIGG_KEY_PASSWORD`.

## Tester
Enhetstestene ligger i `app/src/test`. Kjør dem i Android Studio (høyreklikk mappen > Run Tests) eller
med `gradle testDebugUnitTest`. De kjøres også automatisk på GitHub.

## Health Connect
Kan slås på under Mer. Da lagres hver loggførte økt som er tatt tid på, som en styrketreningsøkt.
Appen ber bare om å skrive. På Android 13 og eldre må Health Connect-appen være installert.

## Språk
Appen er engelsk i bunn og norsk når telefonens systemspråk er norsk (bokmål, nynorsk eller «no").
Alle andre språk får engelsk. Tekstene står i koden som `tr("English", "Norsk")` (se `I18n.kt`),
og øvelsene har navn og notat på begge språk i `ExerciseLibrary.kt`. Videosøket på YouTube bruker alltid
det engelske øvelsesnavnet, fordi det gir flest treff. Program som allerede er laget, beholder språket de
ble laget på; lag et nytt program for å få det på det andre språket.

## AI
- **Med API-nøkkel:** Fanen «Mer» > AI > velg leverandør, lim inn nøkkel. Nøkkelen lagres kun lokalt.
  - Claude: console.anthropic.com > API keys
  - ChatGPT: platform.openai.com > API keys
- **Uten nøkkel:** Fanen «Oppsett» > «Kopier prompt til chat», lim inn i chatten du er logget inn på,
  kopier svaret og bruk «Lim inn svaret». Svaret valideres mot øvelsesbiblioteket.

## Filer
- `Models.kt`: datamodeller
- `ExerciseLibrary.kt`: øvelser med utstyrskrav (legg til egne her)
- `ProgramGenerator.kt`: regelbasert generator (offline)
- `AiClient.kt`: prompt, API-kall og validering av AI-svar
- `Store.kt`, `AppViewModel.kt`, `MainActivity.kt`: lagring, tilstand og skjermer
- `Training.kt`: vektene du har, oppvarming, supersett, øktmodusens rekkefølge og mål, neste blokk
- `WorkoutSessionScreen.kt`: øktmodus (sett for sett, nedtelling for tidsøvelser)
- `HealthConnect.kt`: skriving til Health Connect og personvernskjermen den krever
- `ExerciseLibraryScreen.kt`: fanen «Øvelser»
- `Illustration.kt`: de animerte strekfigurene. Store deler av filen er generert av verktøyene i
  `tools/figurer` (se README der), så figurene endres der og ikke direkte i Kotlin-filen.

## Hviletimer og øktklokke
- `WorkoutTimers.kt`: felles tilstand for hvile og økt, pluss lydene (`Beeper`) og vibrasjon (`Haptics`).
  Tonene justeres med `COUNT_HZ` / `GO_HZ` øverst i `Beeper`.
- `WorkoutTimerService.kt`: forgrunnstjeneste som holder nedtellingen i gang med skjermen av og viser et varsel
  med klokke. Pip ved 3, 2, 1 sekund igjen og en høyere tone ved 0. Lydene går på alarmkanalen (alarmvolumet).
- Øktklokka lagres, så den overlever at Android lukker appen i bakgrunnen (glemte økter over 12 timer forkastes).

## Appikon
- `res/drawable/ic_launcher_foreground.xml` / `ic_launcher_background.xml`: det adaptive ikonet (rigg + stang, rå/minimalistisk stil).
- `res/mipmap-anydpi-v26/ic_launcher.xml`: setter sammen bakgrunn + forgrunn (fungerer fra minSdk 26, så ingen egne PNG-mipmaps trengs).
- Vil du ha et annet uttrykk (farge, form), si ifra så justerer jeg vektorene.
