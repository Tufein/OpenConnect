# OpenConnect: roadmap naar 1.0 en verder

Waar OpenConnect staat en hoe het een grote, openbare release wordt. Opnieuw geschreven op 2026-11-18, na v0.51.0, op
verzoek van Stefan ("een grootse release voorbereiden met een heuse roadmap"). Alles is **test-first** gebouwd: eerst
de test, dan de code, en niets gaat uit zonder dat CI groen is. Wat zonder horloge, ring of telefoon niet te
controleren is, staat er eerlijk bij.

De checklist waarmee 1.0 echt gaat of niet gaat, de proefronde op echte apparaten en het terugdraaiplan staan in
**[PUBLIC_RELEASE.md](PUBLIC_RELEASE.md)**. Dit bestand is het plan; dat bestand is de lijst die je afvinkt.

**Legenda:** ✅ klaar · 🔜 volgende · 🧪 gebouwd en getest met nepgegevens, nog nooit op echte hardware of tegen de echte dienst · ❓ heeft iets nodig dat alleen de eigenaar kan geven · **S/M/L** = klein (een sessie), middel, groot (meerdere releases)

## 1. Waar we staan (v0.51.0, 2026-11-18)

- **51 releases**, ruim 970 tests (Python, `core` en de app), AGPL-3.0, openbare repository, een Android-app die zonder server werkt en een
  Python-server voor wie die liever zelf draait. Nederlands en Engels.
- **Bronnen:** Garmin-horloge (USB, Bluetooth, map met bestanden), Garmin-account met een eigen sleutel, Oura Ring, WHOOP, Hume Band
  (via Health Connect), bestanden (FIT, GPX, TCX), Claude en Gemini voor de coach.
- **Sterk:** alles is test-first, gegevens blijven op de telefoon, horloge, ring en banden worden nooit door elkaar gemengd, en elke release zegt eerlijk wat niet is gecontroleerd.
- **Zwak voor een openbare release:**
  1. Op één bron na (de Garmin-account, die live is geprobeerd) is **niets op een echte telefoon, horloge, ring of band geprobeerd**. Zie het register in paragraaf 7.
  2. De README is een muur van "New in vX" en zegt nog "an early foundation"; er is geen privacyverklaring.
  3. De ondertekeningssleutel staat in een privé-conceptrelease in plaats van in GitHub-secrets.
  4. Dertien Dependabot-voorstellen (#43 tot en met #55) liggen open, sommige met grote sprongen (Compose-BOM 2024.10 naar 2026.09).
  5. De in-app-updater (`REQUEST_INSTALL_PACKAGES`) past niet in Google Play of F-Droid. **Besloten op 2026-11-18: 1.0 komt alleen op GitHub**, dus de updater blijft en winkels komen na 1.0 (paragraaf 6).
  6. Een proefrond op echte apparaten levert niets op zonder diagnose: er is geen "deel een rapport"-knop.
  7. De naam: er bestaat al een bekende VPN-client die OpenConnect heet. Zoeken en winkelnamen kunnen daardoor botsen.

## 2. Wat "1.0" betekent

1.0 is de eerste versie die we aan een vreemde durven geven. Drie beloftes:

1. **Het werkt op een echte telefoon, en wat niet getest is staat er in de app zelf bij.** Een bron die nooit op echte hardware draaide krijgt een zichtbaar label *Experimenteel*; een bron die wel is geprobeerd krijgt *Getest op echt apparaat* met datum en versie.
2. **Je gegevens blijven van jou en gaan niet verloren bij een update.** Elke database van elke eerdere release migreert naar de nieuwe, de back-up komt terug, en alles is te exporteren.
3. **Een vreemde kan het zelf installeren, begrijpen en vertrouwen.** Duidelijke README, privacyverklaring, een veilige sleutel, bekende bronnen van de code en een manier om een fout te melden zonder gezondheidsgegevens te delen.

Na 1.0 geldt versiebeleid: `1.x` voegt toe en repareert, een databaseschema gaat nooit stuk (een migratie of een verwijderfunctie, nooit stilte), en
Android 8.0 blijft het minimum tot een grote versie zegt van niet.

**Afbakening (aanname, zeg het als het anders moet):** 1.0 is de **Android-app**. De Python-server krijgt hetzelfde versienummer maar blijft voor
wie zelf host, met zijn beschreven beperkingen ([SECURITY.md](../SECURITY.md)); er komt geen nieuw serverwerk voor 1.0 behalve beveiligingsfixes.

## 3. De zeven poorten

1.0 gaat uit als **alle zeven** poorten groen zijn. Elke poort is controleerbaar; de afvinklijst staat in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md).

| Poort | Moet waar zijn | Wie |
|-------|----------------|-----|
| **G1 Echt getest** | Installeren, bijwerken, demo, bestanden importeren, Garmin-sleutel, fenix 8 over USB en Bluetooth en Health Connect zijn op een echt apparaat geprobeerd en vastgelegd. Wat niet kon (ring, WHOOP, Hume, als die er niet zijn) draagt het label *Experimenteel* in app en docs | Stefan probeert, Claude repareert |
| **G2 Gegevens veilig** | Migratietest vanaf elke uitgebrachte schemaversie, terugzetten van een back-up, volledige export, geen verlies bij bijwerken | Claude |
| **G3 Sleutel en beveiliging** | Sleutel in GitHub-secrets met een offline kopie, de conceptrelease weg, privé melden van kwetsbaarheden aan, `SECURITY.md` klopt, geen openstaande beveiligingsmeldingen van afhankelijkheden | Stefan (secrets), Claude (rest) |
| **G4 Kwaliteit** | Drie CI-runs op rij groen zonder opnieuw starten, afhankelijkheden bij, prestatiebudgetten gehaald, geen bekende crash | Claude |
| **G5 Begrijpelijk** | README opnieuw, installatiegids, privacyverklaring (NL en EN), schermafbeeldingen van een echte telefoon, uitleg bij de eerste start | Claude, Stefan levert schermafbeeldingen |
| **G6 Juridisch en naam** | Overzicht van alle licenties in de app, Garmin-disclaimer in de app, keuze over de naam gemaakt | Stefan beslist |
| **G7 Verspreiding** | **Besloten op 2026-11-18: alleen GitHub.** De release met APK, SHA-256 en uitleg over installeren, controleren en bijwerken (ook met Obtainium) is voor een vreemde te volgen | Claude |

## 4. De mijlpalen

Elke mijlpaal is een gewone release (pull request, groene CI, ondertekende APK, uitleg in het Nederlands). De volgorde is gekozen op
het **kritieke pad**: een proefronde op echte apparaten duurt het langst en hangt van Stefan af, dus alles wat die proefronde mogelijk
maakt komt eerst (M1 en M2), en de rest loopt ernaast.

### M1: v0.52.0 "Schoon huis" 🔜 (S tot M)

Doel: een opgeruimde basis waarop de rest veilig gebouwd kan worden.

- **Afhankelijkheden bij:** de 13 open Dependabot-voorstellen in vier groepen (GitHub Actions, Android-bibliotheken, Robolectric en testhulp, Docker-basisimage), elke groep een eigen pull request met CI als scheidsrechter. De grote sprongen (Compose-BOM, navigation) apart en met extra aandacht voor schermen die verschuiven. Het Python 3.15-image alleen als alle pakketten bouwen.
- **CI harder:** volledige uitzonderingen in testlogs (`testLogging`), de koude-start-time-out in `AppTestBase` omhoog zodat de vier bekende flakes verdwijnen, en `release.yml` krijgt een standaardwaarde die niet op `v0.9.0` blijft staan.
- **README opnieuw:** kort en actueel (wat het is, installeren, vijf minuten starten, wat is getest, links). De lijst "New in v…" gaat naar een `CHANGELOG.md` die uit `docs/releases/` komt.
- **`SECURITY.md` bijgewerkt** met wat sinds v0.47.0 klopt (optionele aanmelding, Ping/Pull-allowlist).
- **Sleutel naar secrets (❓ Stefan):** het script ondersteunt het al; zodra de secrets er staan gebruikt één release ze, en daarna kan het concept weg.
- **Versiebeleid** vastgelegd (`docs/VERSIONING.md`).

Klaar als: alle voorstellen zijn samengevoegd of met reden gesloten, CI is groen zonder opnieuw starten, de README past op één scherm.

### M2: v0.53.0 "Eerlijk en te diagnosticeren" 🔜 (M)

Doel: een proefronde die iets oplevert, en een app die zelf zegt wat getest is.

- **"Over" wordt een eigen scherm:** nu is het één alinea in Instellingen. Het krijgt de versie, de licentie (AGPL-3.0), een link naar de broncode, een lijst van alle gebruikte licenties (Gadgetbridge, lettertypen, bibliotheken), de Garmin-disclaimer en de privacyverklaring.
- **Label per bron:** *Getest op echt apparaat (datum, versie)* of *Experimenteel*, uit één lijst in `core` met een test die elke bron dwingt er een te hebben. Zichtbaar onder Instellingen, Je toestellen en bij de apparaatkeuze bij de eerste start.
- **Diagnose-rapport:** een knop "Diagnose delen" met versie, Android-versie en toestel, de status van elke bron en de laatste regels van het synclogboek. **Sleutels, tokens, namen en gezondheidswaarden zijn eruit** en een test bewijst dat met nep-geheimen. Een GitHub-meldingssjabloon "Hardwaretest" verwacht dit rapport.
- **Uitleg bij de eerste start:** wat op de telefoon blijft, wat naar een AI gaat als je een sleutel toevoegt, en wat het label *Experimenteel* betekent.

Klaar als: Stefan een proefstap kan doen en een rapport kan terugsturen waar geen persoonlijke gegevens in staan.

### M3: v0.54.0 "Gegevens veilig" 🔜 (M)

- **Migratietest vanaf elke uitgebrachte schemaversie:** per release een database-bestand als testgegeven, geopend door de huidige code, met controle dat er niets verdwijnt.
- **Back-up en terugzetten bestaan al** (sleutels staan nooit in het bestand, terugzetten voegt toe zonder iets te verwijderen). Nieuw: een test per bron dat alles terugkomt, en een besluit of sleutels ooit versleuteld mee mogen.
- **Alles exporteren:** nu is er een CSV. Dat wordt één ZIP met de CSV, de originele FIT/GPX/TCX en JSON, met een test die dat weer inleest.
- **Bijwerken zonder verlies:** de updater weigert een afwijkende SHA-256 of handtekening met een duidelijke melding, getest met een nep-GitHub.

Klaar als: een database van v0.2.0 tot en met v0.53.0 opent in de nieuwe versie met alle gegevens.

### M4: v0.55.0 "Toegankelijk en tweetalig" 🔜 (M)

- **TalkBack en toegankelijkheid:** elk scherm doorgelopen, grafieken met een tekstsamenvatting, aanraakdoelen van minstens 48 dp (getest, ook in de pixelstijl), lettergrootte 200 procent zonder afgesneden tekst.
- **Taalpariteit:** een test dat elke tekst in Nederlands en Engels bestaat, geen overgebleven Engelse tekst in het Nederlands, meervouden kloppen.
- **Grotere schermen:** tablet en opgevouwen toestellen (vanaf 600 dp) zonder afgesneden of uitgerekte onderdelen.

Klaar als: de toegankelijkheidstests en taalpariteit in CI staan en groen zijn.

### M5: v0.56.0 "Snel, klein en actueel" 🔜 (M)

- **Prestatiebudget:** een gegenereerde historie van vijf jaar (ongeveer 1800 activiteiten met tracks en dagrijen) en een test op de openingstijd van elk tabblad en op het geheugen van de heatmap; opstarttijd gemeten.
- **Verkleinen:** R8 en het verkleinen van resources beoordeeld (de APK is 11 MB), met een test dat de serialisatie en reflectie nog werken.
- **targetSdk bijhouden:** nu 35. Zonder Google Play is er geen verplichte datum; omhoog alleen als een nieuwe Android-versie het vraagt, met de lijst van gedragswijzigingen afgewerkt.
- **Batterij:** de voorgrondservice voor live hartslag en de synchronisatie elke 10 minuten bij open app doorgemeten.

Klaar als: het budget in CI staat en de APK niet groter is geworden dan 12 MB.

### M6: v0.57.0 "Installeren en vertrouwen" 🔜 (S tot M)

Besloten op 2026-11-18: 1.0 verschijnt **alleen op GitHub**. De app houdt daarom haar updater en er komt geen aparte winkelvariant voor 1.0.

- **Privacyverklaring** (`docs/PRIVACY.md`, Nederlands en Engels, ook in de app): niets gaat naar een server van ons, wat een AI-aanbieder krijgt als je een sleutel toevoegt, hoe Health Connect gebruikt wordt, hoe je alles wist.
- **Installatiegids:** "zo installeer en update je OpenConnect" voor de GitHub-APK (ook met Obtainium), met de controle van de SHA-256 en een uitleg over "apps installeren toestaan".
- **Releasepagina als voordeur:** een vaste kop in elke release met installeren, controleren en wat is getest, zodat een vreemde zonder de README toekan.
- **Echte schermafbeeldingen** van Stefans telefoon in plaats van de Robolectric-plaatjes.

Klaar als: iemand die het project niet kent de gids kan volgen tot een draaiende app.

### M7: v0.58.0 en v0.59.0 "Proefrondes" 🔜 (L, loopt vanaf M2, Stefan bepaalt het tempo)

Stefan loopt de lijst in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) af met het diagnose-rapport; Claude repareert wat misgaat en zet het label van een bron op *Getest op echt apparaat* zodra het klopt. Volgorde van belangrijkheid: (1) installeren, bijwerken, eerste start, demo, bestanden importeren; (2) Garmin-sleutel; (3) fenix 8 over USB; (4) fenix 8 over Bluetooth, live hartslag en de melding met scherm uit; (5) Health Connect; (6) Oura; (7) Claude en Gemini live; (8) WHOOP en Hume, alleen als die er zijn: anders blijven ze *Experimenteel* in 1.0, en dat is eerlijk.

Klaar als: G1 groen is.

### M8: v1.0.0-rc.1, daarna v1.0.0 🔜 (M)

- **Code-bevriezing:** vanaf hier alleen reparaties en teksten.
- **`release.yml` krijgt een voorlopig-optie**, zodat een kandidaat als **GitHub pre-release** uitkomt en de updater die niet als nieuwste ziet (getest). De labelcontrole moet `1.0.0-rc.1` en `1.0.0rc1` aan elkaar kunnen koppelen.
- **Wachttijd van minstens 7 dagen** met de kandidaat in dagelijks gebruik op Stefans eigen telefoon.
- **Go/no-go:** alle poorten in PUBLIC_RELEASE.md groen, releasenotes in het Nederlands en Engels, de README definitief.
- **v1.0.0** via dezelfde workflow, daarna de controle zoals altijd (SHA-256, handtekening, installeren over de kandidaat heen).

## 5. Wat alleen Stefan kan

| Wanneer | Wat | Waarom het niet zonder jou kan |
|---------|-----|--------------------------------|
| Voor M1 klaar is | De sleutel in GitHub-secrets zetten en een offline kopie bewaren | Alleen de eigenaar bewaart een kopie; secrets maak je in de instellingen van de repository |
| Voor M1 klaar is | Privé melden van kwetsbaarheden aanzetten (Settings, Security) | Een instelling van de eigenaar |
| Zodra M2 uit is | De proefronde op echte apparaten: telefoon, fenix 8, en de ring of banden die je hebt | Alleen jij hebt de apparaten en de accounts |
| ✅ 2026-11-18 | Gekozen waar 1.0 uitkomt: alleen GitHub | Gedaan: winkels (F-Droid, Google Play) komen na 1.0 |
| Voor M8 begint | Beslissen over de naam: houden of veranderen (de pakketnaam `nl.tufein.openconnect` verandert in elk geval niet) | Het is jouw project en jouw merk |
| Bij M6 | Schermafbeeldingen van je eigen telefoon aanleveren | Mijn plaatjes komen uit een test, niet van een toestel |

## 6. Na 1.0

Geen belofte, wel de volgorde als er iets bijkomt:

- **Winkels (1.1 of later):** F-Droid en Google Play, met een variant `store` zonder updater en zonder `REQUEST_INSTALL_PACKAGES`, de Health Connect-verklaring, de antwoorden op Google Play's gegevensveiligheid en een `fastlane/metadata`-boom. F-Droid ondertekent met een eigen sleutel, dus die APK installeert niet over de GitHub-APK heen. Google Play vraagt eenmalig ongeveer 25 dollar voor een ontwikkelaarsaccount en een recent genoeg targetSdk.
- **Dagboekvakjes uitbreiden** en een **trend van actieve tijd** (uit de app-vergelijking, sectie 7): klein en past bij wat er is.
- **Garmin's nieuwere protobuf-synchronisatie** voor horloges die de klassieke map niet meer gebruiken.
- **Het watchface** terug, als er een Garmin-ontwikkelaarsaccount en een eerste upload zijn (de Monkey C-map staat nog in `watch/` maar is nooit gecompileerd).
- **Meer talen** (Duits, Frans) zodra de tekstpariteitstest er is.
- **Een eerlijke "wat gebeurt er met mijn gegevens"-pagina** in de app met een overzicht van elk verzoek dat naar buiten gaat.

**Bewust niet:** vermogensanalyse, een horlogecompanion, upload naar Intervals.icu of Strava (hun voorwaarden), segmenten en sociaal, labuitslagen, biologische leeftijd, Wear OS.

## 7. Wat nog niet is gecontroleerd (register)

De bron van waarheid voor poort G1. Een regel gaat pas naar ✅ als er een echte proef op staat met datum en versie.

| Bron of onderdeel | Stand | Waar staat het |
|-------------------|-------|----------------|
| Garmin-account met eigen sleutel (daggegevens, slaap, HRV, SpO2, Body Battery) | ✅ een keer live geprobeerd tegen een echt account | [GARMIN.md](GARMIN.md) |
| Activiteiten via de sleutel, Ping/Pull-callbacks, snapshots, epoch summaries | 🧪 veldnamen en callbacklayouts niet tegen een echt account | [GARMIN.md](GARMIN.md) |
| Server met zakelijke OAuth (Garmin Developer Program) | 🧪 ❓ nooit, vraagt goedkeuring van Garmin | [GARMIN.md](GARMIN.md) |
| Horloge over USB (fenix 8) en een map met horlogebestanden | 🧪 niet op een echt horloge | [WATCH.md](WATCH.md) |
| Bluetooth-sync en live hartslag (uit Gadgetbridge) | 🧪 niet op een echt horloge | [BLUETOOTH.md](BLUETOOTH.md) |
| Voorgrondservice met melding bij live hartslag | 🧪 niet op een echte telefoon | [WATCH.md](WATCH.md) |
| Trainingen naar `GARMIN/NewFiles` | 🧪 ❓ of een fenix 8 de bestanden accepteert | [WATCH.md](WATCH.md) |
| Health Connect (lezen en schrijven) | 🧪 niet op een echte telefoon | [ANDROID.md](ANDROID.md) |
| Oura Ring | 🧪 nooit tegen de echte dienst | [OURA.md](OURA.md) |
| WHOOP | 🧪 nooit met een echte band of account, scopenamen zijn aannames | [WHOOP.md](WHOOP.md) |
| Hume Band | 🧪 nooit met een echte band, pakketnaam van de Hume-app is een aanname | [HUME.md](HUME.md) |
| Claude en Gemini voor rapporten en coach | 🧪 geen live aanroepen met eigen sleutels | [AI.md](AI.md) |
| In-app-updater | 🧪 niet op een echte telefoon | [ANDROID.md](ANDROID.md) |
| Sleutelkluis, appvergrendeling, iconen en thema's | 🧪 niet op een echte telefoon | [ANDROID.md](ANDROID.md) |

De drempels en afbakeningen van de inzichten (vorm, vroege waarschuwing, slaapbehoefte, seizoensfases, scores van de ring) zijn vuistregels die door tests vastliggen, niet door een studie.

## 8. Klaar (per versie)

Wat elke versie bracht staat uitgebreid in `docs/releases/`. Nieuwe versies komen onderaan de lijst, met een regel in dezelfde vorm.

| Versie | Wat |
|--------|-----|
| v0.1.0 | Python-server, FIT/GPX/TCX-import en -export, dashboard, AI-rapporten |
| v0.2.0 | Zelfstandige Android-app |
| v0.3.0 – v0.4.0 | Horloge via USB, live hartslag over Bluetooth, Garmin-account via de Personal API |
| v0.5.0 – v0.9.0 | Nieuwe look, coach, Gemini en Claude, Bluetooth-sync (uit Gadgetbridge), uitleg bij grafieken, weekdoelen, trainingsbelasting |
| v0.10.0 – v0.15.0 | Back-up, synclogboek, widget en meldingen, Health Connect, beste prestaties, trainingsplan, app-vergrendeling |
| v0.16.0 – v0.19.0 | Oura Ring, één AI tegelijk, apparaatkeuze, thema's, drie ontwerpen |
| v0.20.0 – v0.24.0 | Watchface-voorvertoning, watchfaces en widget als Connect IQ-broncode, weg naar de Connect IQ-store |
| v0.25.0 | Racevoorspelling (Riegel) en racedoel |
| v0.26.0 | Trainingsplan als FIT-trainingen, naar het horloge via USB |
| v0.27.0 | Hartslagzones per activiteit en per week |
| v0.28.0 | Schoenen en kilometers met een melding bij vervanging |
| v0.29.0 | Maandkalender en reeksen |
| v0.30.0 | Rustiger synchroniseren: opnieuw proberen, Retry-After, geen herhaald schrijven |
| v0.31.0 – v0.33.0 | Een eigen ontwerp ("Handheld"), 24 kleurthema's, letterkeuze, acht app-iconen, drie stijlen |
| v0.34.0 – v0.39.0 | Uit de app-vergelijking: vorm, conditie per afstand en VO2max, dagboek en vroege waarschuwing, eentonigheid, heatmap en dezelfde route, hi-score en seizoensplan |
| v0.40.0 | Live hartslag blijft opnemen met het scherm uit (voorgrondservice) |
| v0.41.0 – v0.42.0 | Trainingsopbouw, slaapritme, trend van de rusthartslag, stappendoel van de week |
| v0.43.0 | Uiterlijk inklapbaar, Connect IQ opent Garmins app, watchface uit de app, activiteiten aangevuld met horloge en Oura Ring |
| v0.44.0 | Inzichten per dag met keuze uit Garmin of Oura, datums onder elke grafiek |
| v0.45.0 – v0.47.0 | Server: HRV, Pulse Ox, ademhaling en lichaamssamenstelling, eigen Garmin-sleutel, activiteiten, Ping/Pull, optionele aanmelding, snapshots |
| v0.48.0 | In-app-updater met controle van de SHA-256 |
| v0.49.0 – v0.50.0 | WHOOP-band en Hume Band, elk met een eigen tabblad, gescheiden van horloge en ring |
| v0.51.0 | Apparaten naast elkaar: kaart *Apparaten vergeleken* op het Dag-tabblad |

## 9. Hoe releases werken

Elke versie is een pull request, daarna een GitHub-release met de ondertekende APK (zelfde sleutel, dus de nieuwe versie
installeert over de oude en houdt je gegevens). Er gaat niets uit zonder groene CI. Elke release legt uit wat er in de
code is veranderd en wat niet is gecontroleerd. Voor 1.0 komt daar een voorlopige kandidaat bij (M8).
