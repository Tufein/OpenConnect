# Changelog

Generated from [docs/releases](docs/releases) by `python tools/changelog.py`; do not edit by hand. Each entry is the title and the first paragraph of that release's notes (the notes are in Dutch from v0.43.0).

## v0.58.0: klein, snel getest en eerlijk over privacy

Mijlpalen M5 en M6 en de afhankelijkheden (M1) op de weg naar 1.0, zie `docs/ROADMAP.md`. De app zelf ziet er hetzelfde uit; wat verandert is dat ze **een derde zo groot** is, dat de **getekende APK vóór elke release op een echte Android wordt gestart en doorlopen**, dat de **privacyverklaring** er is (ook in de app) en dat er een **installatiegids** is.

[Release notes](docs/releases/v0.58.0.md)

## v0.57.0: toegankelijk en tweetalig

Vierde mijlpaal (M4) op de weg naar 1.0, zie `docs/ROADMAP.md`. De app is er ook voor mensen met een schermlezer, grote letters of een tablet. Nieuw is vooral een controle in de testen die dit bewaakt, en die meteen echte gaten vond.

[Release notes](docs/releases/v0.57.0.md)

## v0.56.0: gegevens veilig

Derde mijlpaal (M3) op de weg naar 1.0, zie `docs/ROADMAP.md`. Het gaat om één vraag: raak je iets kwijt? Een back-up bevatte tot nu toe alleen de database, dus een nieuwe telefoon verloor je dagboek, je schoenen, je trainingsplan en je doelen. Dat gat is dicht, en het is nu bewezen dat de database van elke eerdere versie opent zonder verlies.

[Release notes](docs/releases/v0.56.0.md)

## v0.55.0: eerlijk en te diagnosticeren

Tweede mijlpaal (M2) op de weg naar 1.0, zie `docs/ROADMAP.md`. De app zegt nu zelf wat er echt getest is, heeft een Over-scherm met alle licenties, en kan een diagnose-rapport maken waar geen sleutels, namen of gezondheidswaarden in staan. Daarmee kan Stefan (of een tester) een proefstap op een echt apparaat doen en iets terugsturen.

[Release notes](docs/releases/v0.55.0.md)

## v0.54.0: Home zegt welke dag het laat zien

Stefan zette de app naast zijn horloge en de cijfers kwamen niet overeen: 2.762 stappen in de app tegen 1.660 op het horloge, en een Body Battery die ook anders was. Dat is het eerste echte signaal van een telefoon in dit project, en het was geen fout van het horloge. Twee dingen in de app zeiden iets anders dan ze deden.

[Release notes](docs/releases/v0.54.0.md)

## v0.53.0: schoon huis

Eerste mijlpaal (M1) op de weg naar 1.0, zie `docs/ROADMAP.md`. Er verandert **niets aan wat de app doet**; wat verandert is alles eromheen: de README, het changelog, het beveiligingsdocument, de build en de testen die soms zonder reden faalden. De Android-bibliotheken zelf (Compose, navigatie en andere) volgen in de volgende release, omdat die sprongen apart bekeken moeten worden.

[Release notes](docs/releases/v0.53.0.md)

## v0.52.0: Android 9 als ondergrens, en de roadmap naar 1.0

Stefan besloot op 2026-11-18 dat het minimum omhoog mag van Android 8.0 naar **Android 9**. Dit is de eerste stap op de weg naar een openbare 1.0: een nieuwere ondergrens betekent minder oude codepaden die niemand meer test, en de mogelijkheid om de ondertekeningssleutel ooit te wisselen zonder dat gebruikers opnieuw moeten installeren (dat kan alleen vanaf Android 9). In dezelfde release staat …

[Release notes](docs/releases/v0.52.0.md)

## v0.51.0: apparaten naast elkaar

Met een horloge, een ring en een band krijg je drie apps met drie verschillende getallen voor dezelfde nacht. Op het Dag-tabblad van Inzichten staat nu een kaart **Apparaten vergeleken** die de getallen van al je apparaten naast elkaar zet en zegt of ze dicht bij elkaar liggen. Het idee komt uit een verse blik op de fitness-apps (zie `docs/APP-VERGELIJKING.md`, sectie 7).

[Release notes](docs/releases/v0.51.0.md)

## v0.50.0: Hume Band erbij, gelezen uit Health Connect

De Android-app kan nu ook een Hume Band tonen: nachten, stappen, hartslag, HRV, bloedzuurstof en workouts, in een eigen tabblad, gescheiden van je horloge en je andere banden. **Anders dan bij Oura en WHOOP** gaat dit niet via een eigen ontwikkelaarsapp en inloggen: Hume heeft geen publieke koppeling. De Hume Health-app schrijft wat de band meet naar **Health Connect** (de gedeelde …

[Release notes](docs/releases/v0.50.0.md)

## v0.49.0: WHOOP-band erbij, op dezelfde manier als de Oura Ring

De Android-app kan nu ook een WHOOP-band lezen: herstel, belasting (strain), slaap en workouts, in een eigen tabblad, gescheiden van je horloge en je ring. Zelfde aanpak als bij Oura: een eigen ontwikkelaarsapp bij WHOOP, inloggen met OAuth, en alles eerst als test geschreven.

[Release notes](docs/releases/v0.49.0.md)

## v0.48.0: update from inside the app

The Android app can now update itself from this project's GitHub releases, with a checksum check before Android is asked to install anything.

[Release notes](docs/releases/v0.48.0.md)

## v0.47.0: the last roadmap items

The remaining items from the roadmap are in: activities through your own key, Ping/Pull callbacks, optional sign-in and the two health types that had no fixed layout.

[Release notes](docs/releases/v0.47.0.md)

## v0.46.0: your own Garmin key on the server, series, splits and best efforts

Five items from the roadmap in one release: the last big gap in server sync, the missing health series, and the track-derived numbers people asked for.

[Release notes](docs/releases/v0.46.0.md)

## v0.45.0: the server reads HRV, Pulse Ox, respiration and body composition

The server catches up with the Health API: the four data types that were still marked *planned* now land in the database, in the JSON API and on the *Health* page.

[Release notes](docs/releases/v0.45.0.md)

## v0.44.0: inzichten per dag met keuze uit Garmin of Oura Ring, en datums onder alle grafieken

Stefans wens: "Insights per dag ook zetten, de keuze in uitklaplijsten als het lukt, daarbij keuze uit Oura en Garmin. Datums bij de grafieken is ook goed, dan is aflezen makkelijker." Test eerst geschreven, in het Handheld-uiterlijk.

[Release notes](docs/releases/v0.44.0.md)

## v0.43.0: instellingen die inklappen, Connect IQ opent Garmins app, watchface eruit, activiteiten aangevuld met horloge en Oura Ring

Eén release met vier wensen van Stefan, test eerst geschreven, in het Handheld-uiterlijk en in het Nederlands.

[Release notes](docs/releases/v0.43.0.md)

## v0.42.0: three small cards on Home, sleep rhythm, resting heart rate trend and step goal week

Second release of the "many small releases before midnight" run. Three small, well-tested ideas from the "later" list in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (what Oura, WHOOP and Garmin Connect each show), shipped together because the time left before midnight did not allow one release for each.

[Release notes](docs/releases/v0.42.0.md)

## v0.41.0: a training ramp card, this week against your usual

First release of the "many small releases before midnight" run, with one small, well-tested idea each (from the "later" list in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md): the acute to chronic workload idea of TrainingPeaks and Intervals.icu).

[Release notes](docs/releases/v0.41.0.md)

## v0.40.0: live heart rate keeps recording with the screen off

The first item from the roadmap's "later" list: a **foreground service** so a live heart-rate recording keeps running once the screen goes off.

[Release notes](docs/releases/v0.40.0.md)

## v0.39.0: a hi-score card for your year, and a season plan to your race

Last release from the app comparison in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (the hi-score card and the season plan from the "later" list).

[Release notes](docs/releases/v0.39.0.md)

## v0.38.0: a heatmap of your own routes, and the same route compared

Fifth release from the app comparison in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (idea 8, and the matched activities from the "later" list).

[Release notes](docs/releases/v0.38.0.md)

## v0.37.0: how monotonous your week was, and how long to sleep tonight

Fourth release from the app comparison in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (idea 5, and the sleep need from the "later" list).

[Release notes](docs/releases/v0.37.0.md)

## v0.36.0: a journal for your days, and a heads-up when your body is off

Third release from the app comparison in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (ideas 2 and 4).

[Release notes](docs/releases/v0.36.0.md)

## v0.35.0: shape for each distance, and VO2max from your runs

Second release from the app comparison in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (ideas 3 and 7).

[Release notes](docs/releases/v0.35.0.md)

## v0.34.0: fitness, fatigue and form, and a training advice for today

This is the first release from the app comparison in [APP-VERGELIJKING.md](../APP-VERGELIJKING.md) (ideas 1 and 6).

[Release notes](docs/releases/v0.34.0.md)

## v0.33.0: a sleek and a classic style next to retro

[Release notes](docs/releases/v0.33.0.md)

## v0.32.0: more themes, easier to read, a launcher icon of your choice

[Release notes](docs/releases/v0.32.0.md)

## v0.31.0: a look of its own

[Release notes](docs/releases/v0.31.0.md)

## v0.30.0: calmer, cheaper syncs

[Release notes](docs/releases/v0.30.0.md)

## v0.29.0: calendar and streaks

[Release notes](docs/releases/v0.29.0.md)

## v0.28.0: shoes and their kilometres

[Release notes](docs/releases/v0.28.0.md)

## v0.27.0: heart-rate zones

[Release notes](docs/releases/v0.27.0.md)

## v0.26.0: your training plan on the watch

[Release notes](docs/releases/v0.26.0.md)

## v0.25.0: race predictions and a race goal

[Release notes](docs/releases/v0.25.0.md)

## v0.24.0: the road to the Connect IQ store

[Release notes](docs/releases/v0.24.0.md)

## v0.23.0: glance and widget for your watch

[Release notes](docs/releases/v0.23.0.md)

## v0.22.0: your watch model and one face per colour

[Release notes](docs/releases/v0.22.0.md)

## v0.21.0: the watch faces (source and build script)

[Release notes](docs/releases/v0.21.0.md)

## v0.20.0: watch face preview

[Release notes](docs/releases/v0.20.0.md)

## v0.19.0: three designs

[Release notes](docs/releases/v0.19.0.md)

## v0.18.0: themes and watch faces

[Release notes](docs/releases/v0.18.0.md)

## v0.17.0: choose your devices, Oura on its own tab

[Release notes](docs/releases/v0.17.0.md)

## v0.16.0: Oura Ring, one AI at a time, livelier screens

[Release notes](docs/releases/v0.16.0.md)

## v0.15.0: training plan and app lock

[Release notes](docs/releases/v0.15.0.md)

## v0.14.0: month and year reports, best efforts, lap table

[Release notes](docs/releases/v0.14.0.md)

## v0.13.0: Health Connect

[Release notes](docs/releases/v0.13.0.md)

## v0.12.0: a home-screen widget and optional notifications

[Release notes](docs/releases/v0.12.0.md)

## v0.11.0: archive on the watch and Bluetooth in the background (both optional, both unproven on a real watch)

[Release notes](docs/releases/v0.11.0.md)

## v0.10.0: backup, sync log and data sources

[Release notes](docs/releases/v0.10.0.md)

## v0.9.0: weekly goals, training load and a roadmap

[Release notes](docs/releases/v0.9.0.md)

## v0.8.0: scores, and a sportier look

[Release notes](docs/releases/v0.8.0.md)

## v0.7.0: charts that explain themselves, and living next to Garmin Connect

[Release notes](docs/releases/v0.7.0.md)

## v0.6.0: sync your Garmin watch over Bluetooth

The app can now **pair with a Garmin watch and download its files over Bluetooth**, with no cable, no Garmin account and no Garmin Connect app. Written for the fenix 8. **Not yet tried on a real watch**, so please treat it as a first test.

[Release notes](docs/releases/v0.6.0.md)

## v0.5.0: a new look, a sync that behaves like Garmin Connect, and Gemini

A redesigned app with a new logo, syncing that runs by itself like the Garmin Connect app, **Google Gemini next to Claude**, a **Coach** you can ask about your own numbers, and a lot of new things to look at.

[Release notes](docs/releases/v0.5.0.md)

## v0.4.0: a Garmin connection that really works

The app can now pull your own data from your **Garmin account**, next to the watch routes of v0.3.0. This one was run **live against a real Garmin account** before release, not only against test files.

[Release notes](docs/releases/v0.4.0.md)

## v0.3.0: read your Garmin watch directly

The Android app can now read a Garmin watch **without a server, without a Garmin Connect account and without Garmin Connect on the phone**. Two routes are built; a third was ruled out on purpose.

[Release notes](docs/releases/v0.3.0.md)

## v0.2.0: the Android app

OpenConnect now has a **standalone Android app**. It needs no server and no account: your data stays in the app's private storage on the phone. The Python server from v0.1.0 is unchanged and still included.

[Release notes](docs/releases/v0.2.0.md)

## v0.1.0: the foundation

The first release of OpenConnect, an open-source, self-hosted alternative to Garmin Connect built on the Garmin Connect Developer Program APIs. It runs end to end on built-in demo data, with no Garmin account or API key needed.

[Release notes](docs/releases/v0.1.0.md)
