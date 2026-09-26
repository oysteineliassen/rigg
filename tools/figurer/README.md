# Figurer

Verktøy for de animerte strekfigurene i appen. Selve appen bruker dem ikke; de lager og sjekker koden i
`app/src/main/java/no/rigg/app/Illustration.kt`.

## Oppsett (én gang)

Du trenger Python 3.8 eller nyere og biblioteket Pillow (for bildene). I terminalen:

    winget install Python.Python.3.12
    python -m pip install pillow

Lukk og åpne terminalen igjen etter at Python er installert.

## Vanlig arbeidsflyt

Gå til mappen med verktøyene:

    cd tools\figurer

Gjør så slik:

1. **Endre** en figur i `gen_poses.py`. Hver figur har en startstilling (stillbildet i listene) og en sluttstilling
   (`end=`). Bokshopp har i stedet en sekvens av nøkkelstillinger (`seq`).
2. **Generer** Kotlin-koden:

       python gen_poses.py

3. **Sjekk** at ingenting knekker, krymper eller går gjennom kassen:

       python sjekk.py

4. **Se** resultatet:

       python forhandsvis.py BOX_JUMP

   Da lages `utvalg.gif`. Uten navn lages `alle.gif` med alle figurene. Med `--stripe` lages stillbilder.
5. Bygg appen og commit som vanlig.

Rediger ikke de genererte delene av `Illustration.kt` for hånd. Det gjelder `POSES`, `enum class IllustrationType`
og `illustrationFor()`. De skrives over neste gang `gen_poses.py` kjøres.

## Koordinater

- Bildet går fra 0 til 1 i begge retninger, med x mot høyre og y nedover.
- Gulvet ligger på y = 0.97.
- Lår og legg er 0.16 lange i bokshopp-figuren.
- Hjelpefunksjonen `joint()` regner ut hvor kneet eller albuen skal være når du oppgir hofte og fot
  (eller skulder og hånd). Den brukes så lemmene holder lengden.

## Skjelettet

De nyeste figurene er bygd med `side()` (sett fra siden) og `front()` (sett forfra) i `gen_poses.py`. Du oppgir
hofte, vinkel på overkroppen og hvert lem, så regnes leddene ut med faste lengder (`LEN`). Et lem kan oppgis
med vinkler, `("a", 60, 100)`, eller med et fast endepunkt, `("p", (0.5, 0.955), 0)`. Da ligger for eksempel
foten fast i gulvet, og kneet bøyer seg mot vinkelen i siste tall.

Bevegelser som ikke bare går frem og tilbake lages som en sekvens: `put(navn, ..., [stillbilde, nøkkel1, nøkkel2, ...],
keys=[(ms, "etikett"), ...])`. En nøkkel med `alpha=0` toner figuren ut, som på gående utfall.

## Ny øvelse som skal ha figur

Legg øvelses-id-en inn i `MAP` nederst i `gen_poses.py`, enten koblet til en eksisterende figur eller til en ny
`pose(...)`. Øvelser som ikke står der, får en figur etter bevegelsesmønsteret (`FALLBACK`).

## Filene

| Fil | Hva den gjør |
|---|---|
| `gen_poses.py` | Alle figurene, og skriver dem inn i `Illustration.kt` |
| `anim_solve.py` | Samme animasjonslogikk som `solvePose()` i appen, for sjekk og forhåndsvisning. Endres motoren i Kotlin, må denne endres likt. |
| `figurer.py` | Leser figurene fra `Illustration.kt` og tegner dem |
| `sjekk.py` | Automatiske sjekker |
| `forhandsvis.py` | GIF-er og stillbilder |
