# OpenConnect: roadmap

Waar OpenConnect staat en waar het heen gaat. Opnieuw geschreven op 2026-11-14, na v0.23.0. Alles is bedacht voor één
persoon die zijn eigen Garmin-gegevens volgt, en alles is **test-first** gebouwd: eerst de test, dan de code, en niets
gaat uit zonder dat CI groen is. Wat zonder horloge of telefoon niet te controleren is, staat er eerlijk bij.

**Legenda:** ✅ klaar · 🚧 in deze release · 🔜 volgende · ❓ heeft iets nodig dat alleen de eigenaar kan geven

## Klaar

| Versie | Wat |
|--------|-----|
| v0.1.0 | Python-server, FIT/GPX/TCX-import en -export, dashboard, AI-rapporten |
| v0.2.0 | Zelfstandige Android-app |
| v0.3.0 – v0.4.0 | Horloge via USB, live hartslag over Bluetooth, Garmin-account via de Personal API |
| v0.5.0 – v0.9.0 | Nieuwe look, coach, Gemini en Claude, Bluetooth-sync (uit Gadgetbridge), uitleg bij grafieken, weekdoelen, trainingsbelasting |
| v0.10.0 – v0.15.0 | Back-up, synclogboek, widget en meldingen, Health Connect, beste prestaties, trainingsplan, app-vergrendeling |
| v0.16.0 – v0.19.0 | Oura Ring, één AI tegelijk, apparaatkeuze, thema's, drie ontwerpen (Clean, Dashboard, Retro) |
| v0.20.0 – v0.23.0 | Watchface-voorvertoning, watchfaces en widget als Connect IQ-broncode met bouwscript |
| v0.24.0 | Op weg naar de Connect IQ-store: instellingen in Garmins app, `.iq`-build, controlescript, store-teksten, store-link in de app |

## v0.24.0: op weg naar de Connect IQ-store ✅

1. **Instellen in Garmins app.** Ontwerp, kleur en vier velden van face, widget en glance zijn instellingen in de
   Connect IQ-app van Garmin (Nederlands en Engels). Ongeldige of ontbrekende instellingen vallen terug op de
   ingebouwde waarden, dus het scherm blijft nooit leeg.
2. **`.iq`-bestand voor de store:** `watch/build.sh --package` maakt één bestand met alle fenix 8-modellen en een vast,
   nooit veranderend app-id per app.
3. **`watch/check-store.sh`:** controleert alles wat zonder Garmin-login kan (manifest, versie, producten, instellingen,
   teksten, pictogram) en valideert tegen Garmins eigen `resources.xsd` als de SDK aanwezig is.
4. **Store-teksten** in het Nederlands en Engels en een lijst van screenshots (`docs/store/`).
5. **De link in de app** (Instellingen, Watchface-voorvertoning): één constante per app in `ConnectIqStore.kt`, leeg tot
   de eerste upload. Zie [CONNECT_IQ_STORE.md](CONNECT_IQ_STORE.md).

## v0.25.0: racevoorspelling en racedoel ✅

6. **Voorspelde racetijden** (5 km, 10 km, halve en hele marathon) uit je beste prestaties, met de formule van Riegel
   en een eerlijke betrouwbaarheid (een marathon voorspellen uit een 5 km is een schatting, geen belofte). Garmins eigen
   Race Predictor wordt niet aan ontwikkelaars gegeven, dit is OpenConnects eigen schatting.
7. **Racedoel** met datum, afstand en streeftijd, bovenaan de Plan-tab: aftelling, het tempo dat je nodig hebt en een
   oordeel (voor op schema, op koers, meer werk). Datum en streeftijd stel je bij met knoppen (+/- 1 week, +/- 1 minuut).

## v0.26.0: trainingen naar je horloge ✅

8. **Trainingsplan als FIT-trainingen:** elke sessie van je plan wordt een gestructureerde training (warming-up,
   intervallen of tempo, cooldown) in het FIT-formaat, dat Garmin-horloges kennen. Getest door ze met een
   onafhankelijke decoder terug te lezen.
9. **Naar het horloge kopiëren** via de USB-verbinding (map `GARMIN/NewFiles`), de komende 7 dagen per keer. Delen als bestand
   is niet gebouwd.
   ❓ Of een echte fenix 8 de bestanden accepteert is alleen met het horloge te zien.

## v0.27.0: hartslagzones ✅

10. **Zones** als percentage van je maximale hartslag (instelbaar, standaard de hoogste gemeten), tijd per zone bij
    elke activiteit en per week, met een duidelijk oordeel over de verdeling (veel rustig, weinig zwaar).

## v0.28.0: schoenen ✅

11. **Schoenen en kilometers:** schoenen toevoegen, een standaardpaar per sport, afstand per paar uit je activiteiten
    en een melding als een paar aan vervanging toe is (standaard bij 800 km, instelbaar).

## v0.29.0: kalender en reeksen ✅

12. **Maandkalender** van je activiteiten (afstand en aantal per dag, bladeren per maand) en **reeksen**: langste en
    huidige reeks dagen en weken met beweging.

## v0.31.0: een eigen ontwerp ✅

13. **Eén eigen uiterlijk ("Handheld")** in plaats van drie ontwerpen en drie stijlen: pixellettertype, pixelhoeken,
    vlakke panelen met een harde schaduw, pixeliconen en -logo, blokgrafieken. Zie [DESIGN.md](DESIGN.md).

## v0.32.0: meer thema's, leesbaarder, kiesbaar icoon ✅

14. **24 kleurthema's** in vier groepen, gemeten op leesbaarheid, een keuze tussen **pixelletters en duidelijke letters**
    en een **keuze uit acht app-iconen**. Zie [DESIGN.md](DESIGN.md).

## v0.33.0: strakke en deftige stijl naast retro ✅

15. Een **stijlkeuze**: Retro (de pixelstijl), **Strak** (afgeronde hoeken, dunne lijnen, duidelijke letters) en **Deftig**
    (kleine hoeken, titels met schreef). Alle 24 kleurthema's werken in alle drie. Zie [DESIGN.md](DESIGN.md).

## Uit de app-vergelijking ✅ / 🚧

Van [APP-VERGELIJKING.md](APP-VERGELIJKING.md) (8 apps naast OpenConnect) wordt alles in losse versies gebouwd:

- **v0.34.0** ✅ fitheid, vermoeidheid en vorm (`Form.kt`, tabblad Vorm) en een trainingsadvies voor vandaag op Home.
- **v0.35.0** ✅ conditie per afstand (`RaceShape.kt`) en VO2max uit je loopjes met racetijden (`Vo2Max.kt`), in het tabblad Plan.
- **v0.36.0** ✅ dagboek (`Journal.kt`, kaart op Home, tabblad Dagboek met het effect op je nachten) en een vroege waarschuwing bij rusthartslag of HRV (`EarlyWarning.kt`).
- **v0.37.0** ✅ eentonigheid en belasting van je week (`Recovery.kt`, tabblad Vorm) en een slaapdoel voor vannacht op Home.
- **v0.38.0** ✅ persoonlijke heatmap van je routes (`Heatmap.kt`, tabblad Kaart) en dezelfde route vergeleken (`RouteMatcher.kt`, kaart bij een activiteit).
- **v0.39.0** ✅ hi-scorekaart per jaar (`YearScore.kt`, tabblad Hi-score) en een seizoensplan in fases onder het racedoel (`SeasonPlan.kt`). Hiermee is de lijst uit de vergelijking klaar, op wat niet past (vermogensanalyse, horlogecompanion, upload naar Intervals.icu, segmenten en sociaal, labuitslagen, biologische leeftijd).

## v0.40.0: live hartslag blijft opnemen met het scherm uit ✅

16. **Voorgrondservice voor live hartslag:** zodra een hartslagmeting loopt, toont OpenConnect een vaste melding met de
    hartslag en de verstreken tijd (en een Stop-knop). Die melding houdt het proces en de Bluetooth-verbinding in leven,
    zodat een training doorloopt als het scherm uitgaat of de app op de achtergrond komt. Stoppen kan in de app of vanaf
    de melding; wat al is opgenomen blijft bewaard en kan daarna als activiteit worden opgeslagen. Niet geprobeerd op een
    echte telefoon.

- **v0.41.0** ✅ trainingsopbouw: deze week tegenover je gebruikelijke week (`RampRate.kt`, kaart onder Inzichten, Vorm).

- **v0.42.0** ✅ drie kleine kaarten op Home: slaapritme (`SleepConsistency.kt`), trend rusthartslag (`RestingHrTrend.kt`) en stappendoel deze week (`StepGoalWeek.kt`).



- **v0.43.0** ✅ uiterlijk-instellingen in één inklapbare sectie, Connect IQ opent Garmins eigen app, watchface uit de app, activiteiten aangevuld met horloge en Oura Ring (`ActivityAround.kt`).

- **v0.44.0** ✅ Inzichten per dag (`DayInsights.kt`, tabblad Dag) met een keuze uit Garmin of Oura Ring in uitklaplijsten, en datums onder alle grafieken (`AxisTicks.kt`).

- **v0.45.0** ✅ de server leest nu ook nachtelijke HRV, Pulse Ox, ademhaling en lichaamssamenstelling (`domain.py`, `garmin/mapper.py`, `web/templates/health.html`), per dag samengevoegd met de bestaande daggegevens en met twee nieuwe JSON-endpoints (`/api/health/vitals` en `/api/health/body-composition`). ❓ Nog niet met een echt Garmin-account opgehaald; de demo-bron maakt ze alle vier.

- **v0.46.0** ✅ de server kan met een **persoonlijke Garmin-sleutel** (`GARMIN_PERSONAL_KEY`) daggegevens, slaap, HRV, SpO2, ademhaling en Body Battery ophalen zonder ontwikkelaarsaccount; stress- en Body Battery-metingen en slaapseries worden per dag bewaard en getoond; kilometer-splittingen en beste prestaties (1K/5K/10K) komen uit tracks; wekelijkse rapporten noemen de nachtelijke vitale waarden; bloeddruk, huidtemperatuur, VO2max en fitte leeftijd worden gelezen (veldnamen **verify**) en in live-modus staat er een backfill-knop in Instellingen. ❓ Activiteiten via de persoonlijke sleutel en het volgen van Ping/Pull-URL's zijn er nog niet; optionele aanmelding vraagt een ontwerpkeuze.

## Later 💡

- Het nieuwere Garmin-protobuf-synchronisatieprotocol voor horloges die de klassieke mapsynchronisatie niet meer gebruiken.
- Wear OS of een eigen horloge-app buiten Garmin valt buiten dit project.

## Heeft de eigenaar nodig ❓

- **Connect IQ-store:** een Garmin-ontwikkelaarsaccount, de eerste upload en Garmins keuring. Daarna het store-id in
  `ConnectIqStore.kt` zetten. Stap voor stap in [CONNECT_IQ_STORE.md](CONNECT_IQ_STORE.md).
- **De Monkey C-code is nooit gecompileerd** (de apparaatbestanden vragen een Garmin-login). Eén keer bouwen met
  `watch/build.sh` laat zien of er nog fouten in zitten.
- Een echte **fenix 8** voor de Bluetooth-sync en voor het kopiëren van watchfaces en trainingen.
- Live controles van **Claude, Gemini en Oura** met eigen sleutels.

## Hoe releases werken

Elke versie is een pull request, daarna een GitHub-release met de ondertekende APK (zelfde sleutel, dus de nieuwe versie
installeert over de oude en houdt je gegevens). Er gaat niets uit zonder groene CI. Elke release legt uit wat er in de
code is veranderd en wat niet is gecontroleerd.
