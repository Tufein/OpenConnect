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

## v0.28.0: schoenen 🚧

11. **Schoenen en kilometers:** schoenen toevoegen, een standaardpaar per sport, afstand per paar uit je activiteiten
    en een melding als een paar aan vervanging toe is (standaard bij 800 km, instelbaar).

## v0.29.0: kalender en reeksen 🔜

12. **Maandkalender** van je activiteiten (afstand en aantal per dag, bladeren per maand) en **reeksen**: langste en
    huidige reeks dagen en weken met beweging.

## Later 💡

- Het nieuwere Garmin-protobuf-synchronisatieprotocol voor horloges die de klassieke mapsynchronisatie niet meer gebruiken.
- Live hartslag als voorgrondservice (een training blijft opnemen met scherm uit).
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
