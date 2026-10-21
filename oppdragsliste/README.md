# Oppdragsliste

Android-app for å holde styr på oppdrag som kommer inn over telefon: akutte, faste avtaler,
de som haster og de som tas «når jeg er i området». Kun til eget bruk, installeres som APK.

- Pakke: `no.oppdragsliste.app`
- Kotlin, Jetpack Compose (Material3), Room, MVVM (ViewModel + StateFlow), WorkManager
- minSdk 26, targetSdk/compileSdk 36
- Ingen backend: alt lagres lokalt på telefonen

## Bygge APK-en

### Alternativ 1: Android Studio
1. File > Open > velg mappen `oppdragsliste` (ikke rotmappen til repoet).
2. La Gradle synkronisere ferdig.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s).
   APK-en havner i `app/build/outputs/apk/debug/app-debug.apk`.

### Alternativ 2: kommandolinje
Krever JDK 17 eller nyere og Android SDK (sett `ANDROID_HOME`, eller lag `local.properties` med
`sdk.dir=/sti/til/Android/Sdk`).

    cd oppdragsliste
    ./gradlew assembleDebug          # APK i app/build/outputs/apk/debug/
    ./gradlew testDebugUnitTest      # enhetstestene

På Windows: `gradlew.bat assembleDebug`.

### Alternativ 3: GitHub
`.github/workflows/oppdragsliste.yml` bygger appen og kjører testene hver gang noe i `oppdragsliste/`
pushes. APK-en ligger under **Actions** > siste kjøring > **Artifacts** > `oppdragsliste-apk` (en zip
med APK-en i). Pushes det til `main`, legges APK-en også ut under **Releases** (`oppdragsliste-N`).

## Installere på telefonen

**Med kabel (adb):** Slå på utvikleralternativer og USB-feilsøking på telefonen, koble til og kjør

    adb install -r app/build/outputs/apk/debug/app-debug.apk

**Uten kabel:** Last ned APK-en på telefonen (fra GitHub, Google Disk e.l.), åpne den og tillat
installasjon fra den appen (nettleser/filbehandler) når Android spør.

Alle bygg signeres med den samme debug-nøkkelen (`app/oppdragsliste-debug.keystore`, ikke hemmelig),
så nye versjoner kan installeres over gamle uten at dataene forsvinner. Versjonsnummeret er antall
commits i git, så en nyere versjon er alltid høyere.

## Første oppstart
1. Gi tillatelse til varsler.
2. Gå til **Innstillinger** (tannhjulet) og slå på det du vil bruke:
   - **Påminnelser:** varsel før avtaler (standard 60 min) og morgenoversikt.
   - **Etter telefonsamtaler:** ber om telefonstatus, anropslogg og kontakter.
   - **Du er i området:** ber først om posisjon, deretter om posisjon «hele tiden».
3. Legg gjerne til flisen «Nytt oppdrag» i hurtiginnstillingene (knapp i innstillingene på Android 13+).
   Snarveien «Nytt oppdrag» finnes også ved å holde på app-ikonet.

## Funksjoner

- **Liste** i seksjonene Akutt → Avtalt tid → Haster → Når i området, med fargekant per prioritet.
  Avtaler sorteres på dato og klokkeslett, «Når i området» eldste først med «Lagt inn for X dager
  siden». Forfalte datoer vises i rødt. Avtalt tid er delt i Forfalt, I dag, I morgen, Resten av uka,
  Neste uke og én gruppe per uke etter det (de senere ukene er foldet sammen). Alle overskrifter kan
  trykkes for å folde sammen/åpne; ved søk vises alt. Utførte oppdrag ligger sammenfoldet nederst og kan gjenåpnes.
- **Søk** i sted, adresse, kunde, beskrivelse og telefon (også uten mellomrom i nummeret).
- **Trykk på et kort** for Ring, Vis i kart, Ferdig, Endre og Slett (bekreftes i kortet).
- **Nytt oppdrag:** starter som Avtalt tid (kan endres i Innstillinger > Nytt oppdrag). Krever beskrivelse
  eller kunde, og dato for Avtalt tid. Kontaktbok-ikonet i Kontaktperson henter navn og nummer fra
  kontaktene (telefonens egen velger, ingen ekstra tillatelse). Tid settes som «Fra» og
  eventuelt «Til» (sluttid samme dag); flyttes starten, flyttes sluttiden med.
- **Kunde og anlegg huskes** i et eget register i databasen, sammen med kontaktperson, telefon, sted
  og adresse. Forslag kommer rett under feltet mens du skriver:
  - Kunde først: anleggene til kunden foreslås under Anlegg.
  - Bare anlegg: forslagene viser kunden («Rådhuset · Søgne kommune»), og kunden fylles inn når du velger.
    Kunden kan endres etterpå (flere kunder kan ha samme anlegg).
  - Kontaktperson og telefon følger kunden, sted og adresse følger anlegget. Det du har skrevet selv,
    overskrives ikke.
  - Hold fingeren på et forslag for å fjerne det fra minnet (f.eks. skrivefeil). Det må bekreftes, og kan
    angres i noen sekunder etterpå. Oppdragene påvirkes ikke.
- **Notat for kunde/anlegg** (under Anlegg): f.eks. adgangskoder eller hvor sikringsskapet er. Lagres på
  kunden/anlegget, fylles inn neste gang du lager oppdrag der, og vises på kortene. Endrer du et gammelt
  oppdrag uten å røre notatet, overskrives ikke et nyere notat. Notatet sendes ikke til Google Kalender,
  men er med i sikkerhetskopien (som er ukryptert – oppbevar den trygt).
- **Utkast:** skjemaet lagres mens du skriver. Tilbaketasten lukker først ved andre trykk, og det du har
  skrevet, står der neste gang (knappen viser da «Fortsett oppdrag»). «Tøm» starter på nytt.
- **Etter samtaler** over minstelengden (standard 20 sek): varselet «Registrere oppdrag fra samtalen med
  Kari Nordmann?», og åpner du appen innen 15 minutter, spør den det samme (én gang per samtale, ikke hvis
  oppdraget allerede er lagt inn). Kunde og anlegg kontakten er registrert på før (på nummer eller navn)
  vises som valg og fylles inn; navnet fra kontaktene blir kontaktperson.
  Under Innstillinger velger du varsel og spørsmål i appen, bare når appen åpnes, eller bare varsel.
  Med to SIM-kort kan du velge hvilke(t) SIM-kort som skal gi varsel/spørsmål.
- **Påminnelser** med eksakte alarmer, planlegges på nytt etter omstart.
- **I nærheten:** sted/adresse geokodes når du lagrer. Geofencer (standard 2 km) gir ett samlet varsel,
  f.eks. «3 oppdrag i nærheten: Søgne (2), Tangvall (1)», maks én gang per oppdrag per dag.
  Android tillater 100 geofencer; akutte og haster prioriteres, deretter de eldste.
  Knappen «I nærheten» i listen sorterer på avstand fra der du er.
- **Google Kalender:** avtaler med dato legges i en valgfri kalender på telefonen (Innstillinger >
  Google Kalender). Endres dato, klokkeslett eller annet, oppdateres hendelsen; slettes avtalen, fjernes
  den. Med sluttid brukes den, ellers varigheten fra innstillingene. Synken går begge veier: flyttes
  hendelsen i kalenderen eller endres start/slutt, oppdateres avtalen (og påminnelsen) i appen.
  Er samme avtale endret begge steder, vinner appen. Sletter du hendelsen i kalenderen, beholdes avtalen.
  Uten klokkeslett blir det heldagshendelse.
- **Påminnelser:** ting du må huske, uavhengig av oppdragene («Påminnelse»-knappen over «Nytt oppdrag»).
  Valgfritt varsel (hurtigvalg som «Om 1 time», «I morgen 07:00», eller egen dato/tid; uten klokkeslett
  kl. 08:00) med «Ferdig» og «Utsett 1 t» i varselet. Kan kobles til et oppdrag, også fra «Påminnelse» på
  oppdragskortet; de vises da på kortet. Ligger øverst i listen, kryss av når ferdig (angres samme dag).
- **Widget** (hold på hjemskjermen > Moduler/Widgets > Oppdragsliste): oppsummering («2 akutte · 3 i dag ·
  8 i området»), knapper for nytt oppdrag og ny påminnelse, og listen over forfalte, akutte, dagens avtaler og
  påminnelser, morgendagens avtaler og det som haster. Gjort liten (2×1) viser den bare oppsummering og «+».
- **Ordre** (fanen nederst): oversikt over saker fra ordrelisten som er utført og må faktureres. Ta skjermbilde
  av ordrelisten og del det til Oppdragsliste (eller «Importer skjermbilde» i fanen; flere bilder går fint).
  Teksten leses av på telefonen (ML Kit, uten nett), og saksnummer (eller P-nummer for ordre uten sak, f.eks. P134940), tittel, adresse og kunde hentes ut
  (dato og tid fra ordrelisten brukes ikke; sakene sorteres på nummer). Samme sak to ganger blir én; «Admin oppgave/Admin task» ignoreres. Kryss av «Utført» og «Fakturert»;
  «Må faktureres» ligger øverst, og saker som er begge deler, går til et sammenfoldet arkiv. Fanen viser hvor
  mange som venter på fakturering. Fra en sak kan du endre den for hånd, lage et oppdrag eller åpne adressen
  i kartet; «Legg til manuelt» lager en sak uten skjermbilde. En sak som leses helt (med status) retter opp
  tidligere feillesing; tekst helt nederst i bildet (bak nettleserens bunnlinje) ignoreres.
- **Sikkerhetskopi:** eksport/import av alle oppdrag som JSON (Innstillinger > Sikkerhetskopi).
  Import hopper over oppdrag som allerede finnes.

Geokoding og geofencer krever Google Play-tjenester (finnes på vanlige Android-telefoner). Uten nett
eller treff lagres oppdraget uten koordinater; bruk «Finn koordinater for oppdrag som mangler» senere.

## Filer (`app/src/main/java/no/oppdragsliste/app`)

| Mappe / fil | Innhold |
|---|---|
| `MainActivity.kt`, `OppdragApp.kt` | Oppstart, intenter fra varsler/snarveier, felles avhengigheter |
| `data/` | Room-entitet `Oppdrag`, DAO, database, repository, innstillinger |
| `logikk/` | Rene funksjoner: sortering, søk, validering, formatering, oversikt, avstand |
| `ui/` | Compose-skjermer: liste, kort, skjema, innstillinger, tema |
| `samtale/` | Samtalevarsel (mottaker + WorkManager-jobb), kontaktoppslag, Quick Settings-flis |
| `varsler/` | Varselkanaler, AlarmManager-planlegging, alarm- og oppstartsmottaker |
| `sted/` | Geokoding, nåværende posisjon, geofencer |
| `sikkerhetskopi/` | JSON-eksport og -import |

Enhetstestene ligger i `app/src/test` (sortering, søk, forfall, validering, formatering,
morgenoversikt, avstand/geofence-utvalg, samtalefilter og sikkerhetskopi).
