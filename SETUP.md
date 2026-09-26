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
