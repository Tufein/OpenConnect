# OpenConnect: roadmap naar 2.0

Waar OpenConnect staat en hoe het verder groeit. Opnieuw geschreven op 2026-11-20, na v1.0.4, op 2026-11-22 (na
v1.2.0) met een **thematische koerswissel** (paragraaf 4b, "Je geschiedenis is van jou") en op **2026-11-24 (na
v1.11.0)** met een nieuwe **koers naar 2.0** in paragraaf 6b: geen lijst met losse ideeën, maar vier fasen en vijf
poorten. Op **2026-11-25 (na v1.13.0)** is de winkelvariant als code toegevoegd, zonder publicatie. 1.0 is uitgebracht; dit plan zegt wat de volgorde is. Alles is **test-first** gebouwd: eerst de test, dan de
code, en niets gaat uit zonder dat CI groen is. Wat zonder horloge, ring of telefoon niet te controleren is, staat er
eerlijk bij.

De checklist waarmee 1.0 echt gaat of niet gaat, de proefronde op echte apparaten en het terugdraaiplan staan in
**[PUBLIC_RELEASE.md](PUBLIC_RELEASE.md)**. Dit bestand is het plan; dat bestand is de lijst die je afvinkt.

**Legenda:** ✅ klaar · 🔜 volgende · 🧪 gebouwd en getest met nepgegevens, nog nooit op echte hardware of tegen de echte dienst · ❓ heeft iets nodig dat alleen de eigenaar kan geven · **S/M/L** = klein (een sessie), middel, groot (meerdere releases)

## 1. Waar we staan (v1.13.0, 2026-11-25)

- **13 releases sinds 1.0** (v1.0.0 tot en met v1.13.0, met tientallen kandidaten daartussen), ruim 1.400 tests
  (Python, `core` en de app), AGPL-3.0, openbare repository, een Android-app die zonder server werkt en een
  Python-server voor wie die liever zelf draait. Nederlands, Engels, Duits en Frans; de laatste twee zijn
  machine-draft en nog niet native nagekeken.
- **Bronnen:** Garmin-horloge (USB, Bluetooth, map met bestanden), Garmin-account met een eigen sleutel, Oura Ring,
  Health Connect (sinds v1.2.0 ook op de achtergrond aangevuld), bestanden (FIT, GPX, TCX), Claude en Gemini voor de
  coach. Sinds v1.8.3 zijn de WHOOP- en Hume Band eruit; hun geschiedenis blijft leesbaar.
- **Sterk:** alles is test-first, gegevens blijven op de telefoon, horloge en ring worden nooit door elkaar gemengd, elke release zegt eerlijk
  wat niet is gecontroleerd, en de **GitHub-versie** is met een in-app-updater te bijwerken met SHA-256-controle.
- **Sinds 1.0** gingen de releases over zeven dingen: meldingen op het horloge (v1.0.1), het uiterlijk (van Graphite
  naar de **editorial**-look in v1.9.0, met signaalkleuren, ringdikte en een voorbladfoto die je zelf kiest), kleine
  slices op Health (HRV-status, slaapfases, gewicht, water, Health Connect op de achtergrond), je eigen geschiedenis
  (archief-import, namen, zelf een training en work-out invoeren), **terugkijken** (het jaaroverzicht in v1.9.3) en een
  **schema dat meedenkt** (v1.10.0): één voorstel na een gemiste afgesloten week, jij kiest Toepassen of Niet nu, en
  de coach bespreekt dat voorstel maar verandert niets. In v1.9.3 is ook de gereedschapsketen naar Gradle 9.6, AGP 9.5
  en compileSdk 37 gegaan. In v1.13.0 kwamen daar de twee Android-kanalen bij.
- **Sinds v1.11.0:** vier talen met een taalkeuze in Instellingen, lokale datums en getallen, en Duitse/Franse
  widget- en meldingsteksten. De Oura-tab is opgeruimd en de voorbladfoto volgt de dag met vier composities.
  De Duitse en Franse teksten zijn automatisch opgesteld en niet native nagekeken; dat staat ook in de app en
  in [LANGUAGES.md](LANGUAGES.md).
- **Sinds v1.12.0:** **Wat je telefoon verlaat** met twaalf bronpagina’s uit één `PrivacyRegister`; de pagina noemt
  bestemming, trigger, velden, opslag, toestemming en wat nooit meegaat. De oude tekst is gecorrigeerd (WHOOP/Hume
  alleen legacy, Health Connect alleen stappen/slaap/gewicht, Oura inclusief tokens/SpO2/work-outs/ringbatterij,
  AI zonder GPS/dagboek/notities), en **Delete all data** wist nu ook lichaam, water, dagboek, schoenen, plan,
  race, doelen, work-outs, log en ledger. Machinevertaling en de adviserende emulator-smoke blijven known issues.
- **Sinds v1.13.0:** de Android-app heeft twee kanalen uit één codebase. `github` houdt de updater,
  `REQUEST_INSTALL_PACKAGES` en de FileProvider; `store` laat die drie weg en verbergt de updater. targetSdk staat op
  36. De store-AAB en -APK worden gebouwd en gecontroleerd, maar **niet gepubliceerd**: de naam, een
  Play-ontwikkelaarsaccount met testers, echte schermafbeeldingen en de winkelverklaringen staan nog open. De app is
  dus niet in een winkel te vinden.
- **Zwak voor het verdere verhaal:**
  1. Op één bron na (de Garmin-account, die live is geprobeerd) is **niets op een echte telefoon, horloge of ring geproefd**. Zie het register in paragraaf 7; M9 is dat spoor.
  2. Of de meldingen op de fenix 8 nu bij elk bericht trillen, wacht op een nieuw logboek; v1.0.1 pakte de meest waarschijnlijke oorzaak aan.
  3. De naam: er bestaat al een bekende VPN-client die OpenConnect heet; het besluit staat nog open (G6).
  4. **De geschiedenis zit nog in Garmin Connect en Strava.** De app importeert één bestand tegelijk en haalt via de eigen sleutel een beperkt stuk terug, terwijl elk inzicht (vorm, VO2max, racevoorspelling, heatmap, seizoen) zo goed is als de geschiedenis eronder. Dat is het thema van 4b.
  5. De twee Dependabot-voorstellen (#86 en #89) zijn in v1.9.3 opgelost met de bewuste compileSdk-37/AGP-9-stap (Gradle 9.6, AGP 9.5.2, Kotlin 2.4.21, Health Connect 1.2.0-alpha06).
  6. De in-app-updater past niet in Google Play of F-Droid; de `store`-variant bestaat sinds v1.13.0 als code, maar
     publicatie blijft geblokkeerd (naam, account/testers, echte schermafbeeldingen, verklaringen). Zie M10.
  7. De Oura-ringbatterij werkt in de app, maar **Oura is nooit live geprobeerd**: login en ringdata zijn alleen tegen nep-antwoorden getest. De grootste rem op 2.0 is daarom niet code maar echte-hardwareproeven.

## 2. Wat "1.0" betekent

1.0 is de eerste versie die we aan een vreemde durven geven. Drie beloftes:

1. **Het werkt op een echte telefoon, en wat niet getest is staat er in de app zelf bij.** Een bron die nooit op echte hardware draaide krijgt een zichtbaar label *Experimenteel*; een bron die wel is geprobeerd krijgt *Getest op echt apparaat* met datum en versie.
2. **Je gegevens blijven van jou en gaan niet verloren bij een update.** Elke database van elke eerdere release migreert naar de nieuwe, de back-up komt terug, en alles is te exporteren.
3. **Een vreemde kan het zelf installeren, begrijpen en vertrouwen.** Duidelijke README, privacyverklaring, een veilige sleutel, bekende bronnen van de code en een manier om een fout te melden zonder gezondheidsgegevens te delen.

Na 1.0 geldt versiebeleid: `1.x` voegt toe en repareert, een databaseschema gaat nooit stuk (een migratie of een verwijderfunctie, nooit stilte), en
**Android 9 is het minimum** (besloten op 2026-11-18, uitgebracht in v0.52.0) en blijft dat tot een grote versie zegt van niet.

**Afbakening (aanname, zeg het als het anders moet):** het hart is de **Android-app**. De Python-server krijgt hetzelfde versienummer maar blijft voor
wie zelf host, met zijn beschreven beperkingen ([SECURITY.md](../SECURITY.md)); vóór 1.0 kwam er alleen beveiligingswerk aan te pas, en de sporen hieronder
raken de server alleen als dat nodig is.

## 3. De zeven poorten

1.0 gaat uit als **alle zeven** poorten groen zijn. Elke poort is controleerbaar; de afvinklijst staat in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md).

| Poort | Moet waar zijn | Wie |
|-------|----------------|-----|
| **G1 Echt getest** | Installeren, bijwerken, demo, bestanden importeren, Garmin-sleutel, fenix 8 over USB en Bluetooth en Health Connect zijn op een echt apparaat geprobeerd en vastgelegd. Wat niet kon (de ring, als die er niet is) draagt het label *Experimenteel* in app en docs | Stefan probeert, Claude repareert |
| **G2 Gegevens veilig** | Migratietest vanaf elke uitgebrachte schemaversie, terugzetten van een back-up, volledige export, geen verlies bij bijwerken | Claude |
| **G3 Sleutel en beveiliging** | Sleutel in GitHub-secrets met een offline kopie, de conceptrelease weg, privé melden van kwetsbaarheden aan, `SECURITY.md` klopt, geen openstaande beveiligingsmeldingen van afhankelijkheden | Stefan (secrets), Claude (rest) |
| **G4 Kwaliteit** | Drie CI-runs op rij groen zonder opnieuw starten, afhankelijkheden bij, prestatiebudgetten gehaald, geen bekende crash | Claude |
| **G5 Begrijpelijk** | README opnieuw, installatiegids, privacyverklaring (NL en EN), schermafbeeldingen van een echte telefoon, uitleg bij de eerste start | Claude, Stefan levert schermafbeeldingen |
| **G6 Juridisch en naam** | Overzicht van alle licenties in de app, Garmin-disclaimer in de app, **keuze over de naam gemaakt (nog open)** | Stefan beslist |
| **G7 Verspreiding** | **Besloten op 2026-11-18: alleen GitHub.** De release met APK, SHA-256 en uitleg over installeren, controleren en bijwerken (ook met Obtainium) is voor een vreemde te volgen | Claude |

## 4. De mijlpalen

Elke mijlpaal is een gewone release (pull request, groene CI, ondertekende APK, uitleg in het Nederlands). **Versienummers worden pas bij het uitbrengen gegeven:** er komen ook andere releases tussendoor (v0.44.0 tot en met v0.51.0 kwamen zo binnen terwijl dit plan werd geschreven), dus een mijlpaal krijgt het eerstvolgende vrije nummer. De eerste stap is al gezet: **v0.52.0 zette het minimum op Android 9.** De volgorde is gekozen op
het **kritieke pad**: een proefronde op echte apparaten duurt het langst en hangt van Stefan af, dus alles wat die proefronde mogelijk
maakt komt eerst (M1 en M2), en de rest loopt ernaast.

### M1: "Schoon huis" ✅ v0.53.0 en v0.58.0 (S tot M)

Doel: een opgeruimde basis waarop de rest veilig gebouwd kan worden.

- **Afhankelijkheden bij:** de 13 open Dependabot-voorstellen in vier groepen (GitHub Actions, Android-bibliotheken, Robolectric en testhulp, Docker-basisimage), elke groep een eigen pull request met CI als scheidsrechter. De grote sprongen (Compose-BOM, navigation) apart en met extra aandacht voor schermen die verschuiven. Het Python 3.15-image alleen als alle pakketten bouwen.
- **CI harder:** volledige uitzonderingen in testlogs (`testLogging`), de koude-start-time-out in `AppTestBase` omhoog zodat de vier bekende flakes verdwijnen, en `release.yml` krijgt een standaardwaarde die niet op `v0.9.0` blijft staan.
- **README opnieuw:** kort en actueel (wat het is, installeren, vijf minuten starten, wat is getest, links). De lijst "New in v…" gaat naar een `CHANGELOG.md` die uit `docs/releases/` komt.
- **`SECURITY.md` bijgewerkt** met wat sinds v0.47.0 klopt (optionele aanmelding, Ping/Pull-allowlist).
- **Sleutel naar secrets (❓ Stefan):** het script ondersteunt het al; zodra de secrets er staan gebruikt één release ze, en daarna kan het concept weg.
- **Versiebeleid** vastgelegd (`docs/VERSIONING.md`).

Klaar als: alle voorstellen zijn samengevoegd of met reden gesloten, CI is groen zonder opnieuw starten, de README past op één scherm.

**Besluit over de afhankelijkheden (2026-11-18, v0.58.0):** samengevoegd zijn de GitHub Actions (setup-java 6, download-artifact 8) en Robolectric 4.18. Uit de Android-bibliotheken is het deel genomen dat op de huidige gereedschapsketen past (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35): `security-crypto` 1.1.0 (stabiel, de sleutelkluis), `work-runtime` 2.12.0, `core-ktx` 1.16.0. **Met reden gesloten** (Dependabot stelt die versies niet opnieuw voor): de Compose-BOM 2026 en navigation 2.10 (vijf schermtests faalden op de eerste proef met de nieuwe BOM, en de nieuwste versies vragen compileSdk 37), Health Connect 1.1.0-beta01 (de API verandert: `Metadata` krijgt fabrieksmethoden), Kotlin 2.4 met serialization 1.13 en coroutines 1.11 (een hele gereedschapssprong, en dan is lokaal testen zonder Maven-toegang niet meer mogelijk), Gradle 9.9 en AGP 9.5 (migratie), en het Python 3.15-image (de server wordt alleen op 3.11 tot 3.13 getest). **Die sprong is één aparte klus voor na 1.0** (AGP 9, Gradle 9, Kotlin 2.4, compileSdk 37, Compose-BOM 2026, Health Connect 1.1), met een eigen release en een volledige proefronde. **Die sprong is in v1.9.3 gedaan** (Gradle 9.6, AGP 9.5.2, compileSdk 37, Kotlin 2.4.21); de Compose-BOM 2026 staat nog open als #92.

### M2: "Eerlijk en te diagnosticeren" ✅ v0.55.0 (M)

Doel: een proefronde die iets oplevert, en een app die zelf zegt wat getest is.

- **"Over" wordt een eigen scherm:** nu is het één alinea in Instellingen. Het krijgt de versie, de licentie (AGPL-3.0), een link naar de broncode, een lijst van alle gebruikte licenties (Gadgetbridge, lettertypen, bibliotheken), de Garmin-disclaimer en de privacyverklaring.
- **Label per bron:** *Getest op echt apparaat (datum, versie)* of *Experimenteel*, uit één lijst in `core` met een test die elke bron dwingt er een te hebben. Zichtbaar onder Instellingen, Je toestellen en bij de apparaatkeuze bij de eerste start.
- **Diagnose-rapport:** een knop "Diagnose delen" met versie, Android-versie en toestel, de status van elke bron en de laatste regels van het synclogboek. **Sleutels, tokens, namen en gezondheidswaarden zijn eruit** en een test bewijst dat met nep-geheimen. Een GitHub-meldingssjabloon "Hardwaretest" verwacht dit rapport.
- **Uitleg bij de eerste start:** wat op de telefoon blijft, wat naar een AI gaat als je een sleutel toevoegt, en wat het label *Experimenteel* betekent.

Klaar als: Stefan een proefstap kan doen en een rapport kan terugsturen waar geen persoonlijke gegevens in staan.

### M3: "Gegevens veilig" ✅ v0.56.0 (M)

- **Migratietest vanaf elke uitgebrachte schemaversie:** per release een database-bestand als testgegeven, geopend door de huidige code, met controle dat er niets verdwijnt.
- **Back-up en terugzetten bestaan al** (sleutels staan nooit in het bestand, terugzetten voegt toe zonder iets te verwijderen). Nieuw (v0.56.0): het dagboek, de schoenen, het trainingsplan, de wedstrijd, de maximale hartslag en de doelen zitten nu ook in het bestand (ze stonden er nooit in, een nieuwe telefoon verloor ze), een test per bron op de echte database, en een bewaker die faalt als een nieuwe tabel niet in de back-up zit of met reden is weggelaten. **Besluit: sleutels gaan in 1.x nooit mee**, ook niet versleuteld; opnieuw invoeren kost een minuut, een bestand met sleutels is een groter risico dan het waard is.
- **Alles exporteren:** nu is er een CSV. Dat wordt één ZIP met de CSV, de originele FIT/GPX/TCX en JSON, met een test die dat weer inleest.
- **Bijwerken zonder verlies:** de updater weigert een afwijkende SHA-256 of handtekening met een duidelijke melding, getest met een nep-GitHub.

Klaar als: een database van elke eerdere release opent in de nieuwe versie met alle gegevens.

### M4: "Toegankelijk en tweetalig" ✅ v0.57.0 (M)

- **TalkBack en toegankelijkheid:** elk scherm doorgelopen, grafieken met een tekstsamenvatting, aanraakdoelen van minstens 48 dp (getest, ook in de pixelstijl), lettergrootte 200 procent zonder afgesneden tekst.
- **Taalpariteit:** een test dat elke tekst in Nederlands en Engels bestaat, geen overgebleven Engelse tekst in het Nederlands, meervouden kloppen.
- **Grotere schermen:** tablet en opgevouwen toestellen (vanaf 600 dp) zonder afgesneden of uitgerekte onderdelen.

Klaar als: de toegankelijkheidstests en taalpariteit in CI staan en groen zijn.

### M5: "Snel, klein en actueel" ✅ v0.58.0 (M)

- **Prestatiebudget:** een gegenereerde historie van vijf jaar (ongeveer 1800 activiteiten met tracks en dagrijen) en een test op de openingstijd van elk tabblad en op het geheugen van de heatmap; opstarttijd gemeten.
- **Verkleinen:** R8 en het verkleinen van resources beoordeeld. De APK was 30 MB (niet 11: dat was nooit gemeten), nu 9,8 MB. De test dat serialisatie, WorkManager en de sleutelkluis nog werken is een **rooktest op een echte Android** (emulator) die ook vóór elke release draait.
- **targetSdk bijhouden:** nu 36 (sinds v1.13.0; compileSdk 37). Zonder Google Play is er geen verplichte datum; omhoog alleen als een nieuwe Android-versie het vraagt, met de lijst van gedragswijzigingen afgewerkt.
- **Batterij:** niet te meten zonder telefoon. Wat er op de achtergrond draait staat met zijn frequentie in `docs/PERFORMANCE.md`; het cijfer van Android na een dag is proefstap T16 voor Stefan.

Klaar als: het budget in CI staat en de APK niet groter is geworden dan 12 MiB (`release.yml` faalde erop; sinds v1.13.1 is het budget 13 MiB en staat het in `android/scripts/check_apk_size.py`, zie [PERFORMANCE.md](PERFORMANCE.md)).

### M6: "Installeren en vertrouwen" ✅ v0.58.0, behalve de echte schermafbeeldingen (S tot M)

Besloten op 2026-11-18: 1.0 verschijnt **alleen op GitHub**. De app houdt daarom haar updater en er komt geen aparte winkelvariant voor 1.0.

- **Privacyverklaring** (`docs/PRIVACY.md`, Nederlands en Engels, ook in de app): niets gaat naar een server van ons, wat een AI-aanbieder krijgt als je een sleutel toevoegt, hoe Health Connect gebruikt wordt, hoe je alles wist.
- **Installatiegids:** "zo installeer en update je OpenConnect" voor de GitHub-APK (ook met Obtainium), met de controle van de SHA-256 en een uitleg over "apps installeren toestaan".
- **Releasepagina als voordeur:** een vaste kop in elke release met installeren, controleren en wat is getest, zodat een vreemde zonder de README toekan.
- **Echte schermafbeeldingen** van Stefans telefoon in plaats van de Robolectric-plaatjes. ❓ Stefan. (De emulator-rooktest bewaart bij elke run schermafbeeldingen van een echte Android als bouwresultaat; die zijn een begin, maar niet van zijn telefoon.)

Klaar als: iemand die het project niet kent de gids kan volgen tot een draaiende app.

### M7: "Proefrondes" 🔜 (L, loopt vanaf M2, Stefan bepaalt het tempo)

Stefan loopt de lijst in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) af met het diagnose-rapport; Claude repareert wat misgaat en zet het label van een bron op *Getest op echt apparaat* zodra het klopt. Volgorde van belangrijkheid: (1) installeren, bijwerken, eerste start, demo, bestanden importeren; (2) Garmin-sleutel; (3) fenix 8 over USB; (4) fenix 8 over Bluetooth, live hartslag en de melding met scherm uit; (5) Health Connect; (6) Oura; (7) Claude en Gemini live.

Klaar als: G1 groen is.

### M8: v1.0.0-rc.1 ✅ 2026-11-18, rc.2 tot en met rc.7 ✅ 2026-11-19 en 2026-11-20, v1.0.0 ✅ 2026-11-20

Op verzoek van Stefan is `v1.0.0` na rc.7 uitgebracht als eerste volledige release, met de moderne standaardstijl. De
openstaande apparaatproeven (T1–T17) en go/no-go-vakjes in [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) blijven staan en zijn
eerlijk opgenomen in de [releasenotes](releases/v1.0.0.md); de RC's en/of een latere versie kunnen die alsnog afdekken.

De kandidaat `v1.0.0-rc.1` is uitgebracht als GitHub pre-release (zelfde code als v0.58.0, versie `1.0.0-rc.1`, `versionCode` 59). Wat nu nog moet is van Stefan: de wachttijd, de proefstappen T1 tot en met T17, de sleutel in GitHub-secrets, privé melden aanzetten, echte schermafbeeldingen en het besluit over de naam. Daarna kijkt Claude naar de poorten in PUBLIC_RELEASE.md en brengt `v1.0.0` uit.

- **Code-bevriezing:** vanaf hier alleen reparaties en teksten. ✅ Vanaf `v1.0.0-rc.1`. **Zes uitzonderingen, op Stefans verzoek:** `v1.0.0-rc.2` (`versionCode` 60) herkent alle Garmin-families (zie [GARMIN_DEVICES.md](GARMIN_DEVICES.md)) en `v1.0.0-rc.3` (`versionCode` 61) stuurt meldingen van de telefoon door naar het horloge (zie [NOTIFICATIONS.md](NOTIFICATIONS.md)); `v1.0.0-rc.4` (`versionCode` 62) laat per stap zien waarom een melding niet aankomt en stuurt ze ook als het horloge er niet om vraagt; `v1.0.0-rc.5` (`versionCode` 63) herkent een Bluetooth-verbinding die stil is gevallen, beantwoordt alle berichten van het horloge zoals Gadgetbridge en laat in het logboek zien wat het horloge antwoordt; `v1.0.0-rc.6` (`versionCode` 64) beantwoordt de protobuf-vragen van het horloge zoals Gadgetbridge, zodat het niet blijft wachten, en verbindt opnieuw als het horloge vastloopt, met de gemiste melding nog een keer; de wachttijd van 7 dagen begint daarmee opnieuw voor het horloge; `v1.0.0-rc.7` (`versionCode` 65) is een doorloop van alle code: geannuleerde achtergrond-syncs blokkeren niets meer, een kapotte sleutelkluis laat de app niet crashen, TalkBack ziet aantikbare rijen als knop, een wachtwoord met een accent kan inloggen op de server en het sessiecookie hangt aan een afgeleide sleutel (zie [de notities](releases/v1.0.0-rc.7.md)).
- ✅ **`release.yml` krijgt een voorlopig-optie** (v0.58.0, voor het eerst doorlopen bij rc.1), zodat een kandidaat als **GitHub pre-release** uitkomt en de updater die niet als nieuwste ziet (getest). De labelcontrole moet `1.0.0-rc.1` en `1.0.0rc1` aan elkaar kunnen koppelen.
- **Wachttijd van minstens 7 dagen** met de kandidaat in dagelijks gebruik op Stefans eigen telefoon.
- **Go/no-go:** alle poorten in PUBLIC_RELEASE.md groen, releasenotes in het Nederlands en Engels, de README definitief.
- **v1.0.0** via dezelfde workflow, daarna de controle zoals altijd (SHA-256, handtekening, installeren over de kandidaat heen).

### M9: proeven op echte apparaten 🔜 (S per proef, loopt, Stefan bepaalt het tempo)

Het register in paragraaf 7 is de lijst. Per bron: installeren en bijwerken over de vorige versie, demo en import, de Garmin-sleutel, de fenix 8 over
USB en over Bluetooth (sync, live hartslag, en voor de meldingen een **nieuw logboek** zodat blijkt of de reparaties van v1.0.1 houden), Health Connect,
en Oura als die er is. Wat klopt, krijgt in de app het label *Getest op echt apparaat* met datum en versie; wat niet kan blijft
*Experimenteel*. Reparaties komen als gewone 1.0.x-uitgaven; geen nieuwe features in dit spoor. Schermafbeeldingen van je eigen telefoon horen hier
ook bij (G5), evenals het besluit over de naam (G6), dat Stefan alleen kan nemen.

### M10: winkels 🧪 code klaar, publicatie geblokkeerd (L, ❓)

Sinds v1.13.0 is de **`store`-variant** er als code: dezelfde `applicationId` als de GitHub-app, zonder updater en
zonder `REQUEST_INSTALL_PACKAGES`/FileProvider, met eigen AAB- en APK-taken. De store-workflow bouwt en controleert die
artefacten; de release-workflow publiceert alleen de GitHub-APK. Publicatie in F-Droid of Google Play is nog niet
gebeurd en blijft geblokkeerd: de naam botst met een bestaande OpenConnect-VPN-app, een Play-ontwikkelaarsaccount en de
testersregel (12 testers, 14 dagen) staan open, echte schermafbeeldingen en een feature graphic ontbreken, en de
verklaringen voor gegevensveiligheid, Health Connect en voorgrondservice zijn niet afgerond. Voor F-Droid komen daar de
ontbrekende `v1.13.0`-tag, de toolchain op de buildserver en de ondertekening bij. F-Droid ondertekent met een eigen
sleutel, dus die APK installeert niet over de GitHub-APK heen; dat hoort in de uitleg. Google Play vraagt eenmalig
ongeveer 25 dollar voor een ontwikkelaarsaccount en een recent genoeg targetSdk (nu 36). De updater blijft gewoon in de
GitHub-versie.

### M11: inhoud na 1.0 🔜 (M-L)

Geen belofte, wel de volgorde zoals die in paragraaf 6 stond:

- **Dagboekvakjes uitbreiden** en een **trend van actieve tijd** (uit de app-vergelijking, sectie 7): klein en past bij wat er is.
- **Garmin's nieuwere protobuf-synchronisatie** voor horloges die de klassieke map niet meer gebruiken.
- **Het watchface** terug, als er een Garmin-ontwikkelaarsaccount en een eerste upload zijn (de Monkey C-map staat nog in `watch/` maar is nooit gecompileerd).

### M12: opschonen en bereik 🔜 (M)

- **Dependabot-voorstellen afwerken** (#43 tot en met #55), met de grote sprongen als eigen releases en de APK-maat- en prestatiebudgetten als poort. ❓ **Eerste poging (v1.0.5) teruggedraaid.** De sprong naar AGP 9.5.2 + Kotlin 2.4.21 + Gradle 9.9.2 + Compose-BOM 2026.11 + compile-SDK 37.1 (met Activity 1.13, Lifecycle 2.11, Navigation 2.10, core-ktx 1.19, Robolectric 4.18.1) kompilde en haalde lokaal alle 444 tests, maar op de CI-runner bleef Home daarna soms in de ongeladen stand (`loaded` bleef uit), 13 tests liepen op hun 45 s-timeout; een debugronde wees niet op een uitzondering en de oorzaak is niet gevonden. De sprong wacht op een CI-only debugronde (bijvoorbeeld tests met een langere wachttijd of een profiling-run) vóórdat hij opnieuw gaat; de Python-afhankelijkheden staan als vloer waaronder de nieuwste versies al voldoen.
- **Meer talen** (Duits, Frans) zodra de tekstpariteitstest er is.
- **Een eerlijke "wat gebeurt er met mijn gegevens"-pagina** in de app met een overzicht van elk verzoek dat naar buiten gaat.
- **De naam beslissen** (houden of veranderen; de pakketnaam `nl.tufein.openconnect` verandert in elk geval niet) — dit is een vakje van Stefan, maar de voorbereiding kan hier.

## 4b. Nieuw thema: je geschiedenis is van jou (2026-11-22)

**De koerswissel in één zin:** van *meer meten en melden* naar *meenemen wat je al hebt, er jaren in terugkijken en het vrij kunnen gebruiken*.

**Waarom dit thema.** OpenConnect wil een Garmin Connect-vervanger zijn, maar wie overstapt laat jaren aan activiteiten, slaap en vorm achter.
De app kan nu één FIT-, GPX- of TCX-bestand tegelijk importeren; Garmin's en Strava's eigen exporten zijn één groot ZIP-bestand en blijven dus
onbruikbaar. Alle inzichten die er al zijn (vorm en vermoeidheid, VO2max, racevoorspelling, seizoensplan, heatmap, dezelfde route, hi-scores)
worden pas goed met een lange geschiedenis. En er is geen enkel overzicht van het ene jaar naast het andere. Dit thema heeft drie delen:
**binnenhalen** (M13, M14, M14b), **terugkijken** (M15) en **vrij gebruiken** (M16). Het past ook bij wat er nog moet voor een vreemde (G5 en G7):
een nieuwe gebruiker verhuist, anders begint hij met een lege app.

**Waarom nu en zo.** Bijna alles hieraan loopt via bestanden, dus het is van begin tot eind met tests in CI te controleren, ook zonder horloge
(waar de vorige reeks op vastliep). De 1.1-sporen hierboven blijven staan; de zwaardere delen daarvan (Training Effect en Recovery Time uit FIT,
routes naar het horloge, protobuf-sync, watchface) wachten op een echt horloge en lopen niet voor op dit thema.

### M13: "Verhuisdoos" ✅ v1.3.0 (M)

Eén importknop die ook een heel archief aankan, zodat Garmin's en Strava's export in één keer binnenkomt.

- **Wat erin mag:** een ZIP (ook een ZIP in een ZIP, zoals Garmin's `UploadedFiles_*.zip` in de export), een `.gz`, en losse FIT-, GPX- en TCX-bestanden; Strava's `activities/*.fit.gz`, `*.gpx` en `*.tcx.gz` horen erbij, net als OpenConnect's eigen *Alles exporteren*.
- **Stromend, zonder het archief in het geheugen te zetten**, met grenzen tegen een ZIP-bom: 25 MB per bestand (ook na uitpakken), een totaal, een aantal bestanden en een diepte van drie ZIP's.
- **Wat er al is blijft staan:** een activiteit met dezelfde id of die binnen 90 seconden na een bestaande begint (dezelfde training uit het horloge, de Garmin-account of een eerdere import) wordt niet overschreven en niet verdubbeld; een tweede keer dezelfde ZIP voegt niets toe.
- **Eerlijk over wat niet telde:** routes, trainingen, foto's en CSV's worden overgeslagen en genoemd (geen foutmelding), één kapot bestand houdt de rest niet tegen, en het eindrapport (toegevoegd, al aanwezig, overgeslagen, mislukt met reden, periode) komt in het synchronisatieoverzicht.
- **Voortgang** terwijl het loopt, en annuleren laat af wat al binnen is.
- **Uitleg:** [MIGRATING.md](MIGRATING.md) met de stappen om je export bij Garmin en Strava aan te vragen en hier binnen te halen.

Klaar als: een gemaakt archief met N activiteiten in drie formaten, deels geneste ZIP's en deels `.gz`, N activiteiten in de app oplevert, en dezelfde ZIP nog eens niets verandert. ✅ Zo getest in `ArchiveImportTest` (23 tests) en `ArchiveImportScreenTest`; **nooit met een echte export** (register, paragraaf 7).

### M14: "Namen" ✅ v1.4.0 (M)

De bestanden in een export hebben geen namen; die staan ernaast.

- **Namen mee:** Strava's `activities.csv` (kolommen *Activity Name* en *Filename*) en Garmin's `summarizedActivities.json` (op starttijd, binnen 90 seconden) geven de activiteit haar eigen naam in plaats van "Running 2026-05-02", in welke volgorde de bestanden ook staan. Wat in v1.3.0 al binnenkwam krijgt zijn naam bij een tweede import, mits het nog de bedachte naam draagt; een naam van jou of van een andere bron blijft.
- **Eerlijk over wat niet wordt gelezen:** de dagbestanden van Garmin (`UDSFile_*.json`, `*_sleepData.json`) zijn nergens openbaar beschreven. Ze worden niet geïmporteerd (gokken in je gezondheidsgeschiedenis); het rapport schrijft per soort het patroon van de bestandsnaam en de sleutelnamen op (nooit waarden of e-mailadressen) en heeft een knop *Rapport kopiëren*.

Klaar als: een Strava-archief met CSV namen geeft, een nagemaakte Garmin-export namen geeft op starttijd, en het rapport sleutelnamen toont zonder één waarde. ✅ `ArchiveNamesTest` (14 tests); **nooit met een echte export**.

### M14b: "Dagen" 🔜 (M, ❓ wacht op een echt rapport van Stefan)

Stappen, rusthartslag en slaap uit Garmin's dagbestanden, gevuld waar de app nog niets heeft (nooit over iets heen dat horloge, ring of band gaf), en het `daily.csv` uit *Alles exporteren* weer inlezen. De lezer wordt pas gebouwd als een echte export de sleutelnamen heeft laten zien; het rapport uit v1.4.0 is daar de ingang voor. Tot dan blijft het 🧪 en staat er niets in je gezondheidsgeschiedenis dat ergens op gegokt is.

### M15: "Terugblik" 🔜 v1.5.0 (M)

Lange geschiedenis moet je ook kunnen zien. Er is nu geen jaar-op-jaar.

- **Dit jaar tegenover vorig jaar:** de lopende afstand en tijd als twee lijnen op dezelfde tijdas, met het verschil tot nu toe.
- **Maandentabel over alle jaren** (afstand, tijd, aantal) als raster, de zwaarste maand gemarkeerd.
- **Alle jaren per sport** op één regel, en per jaar de rustige en de drukke weken.
- **Tijdlijn van hi-scores:** wanneer is welk record gevestigd, over alle jaren, niet alleen de huidige stand.
- Alles getest op het vijfjarige demo-archief (en een gegenereerd archief van tien jaar); een prestatiebudget zoals bij M5.

### M16: "Open en koppelbaar" 🔜 (L, in stukken)

- **Eigen open formaat:** een gedocumenteerd, versiebeheerd JSON-exportformaat naast CSV, dat de app ook weer inleest (met een test per schemaversie).
- **Koppelen:** een alleen-lezen koppeling voor Tasker, Home Assistant en scripts (Android-intents of een ContentProvider), standaard uit, met de bijbehorende regel in de privacyverklaring.
- **Server:** `openconnect import archief.zip` voor de Python-server, met dezelfde regels als M13, en een manier om de telefoon naar de eigen server te laten schrijven.
- Elke schakelaar die gegevens buiten de app brengt krijgt een regel op de gegevenspagina uit M12.

## 5. Wat alleen Stefan kan

| Wanneer | Wat | Waarom het niet zonder jou kan |
|---------|-----|--------------------------------|
| Open | De sleutel in GitHub-secrets zetten en een offline kopie bewaren (de releases werken nu via secrets óf de privé-conceptrelease; G3 wil het eerste) | Alleen de eigenaar bewaart een kopie; secrets maak je in de instellingen van de repository |
| Open | Privé melden van kwetsbaarheden aanzetten (Settings, Security) | Een instelling van de eigenaar |
| Nu (M9 loopt) | De proefronde op echte apparaten: telefoon, fenix 8 en de Oura-ring, met een nieuw meldingenlogboek voor de reparaties van v1.0.1 | Alleen jij hebt de apparaten en de accounts |
| ✅ 2026-11-18 | Gekozen waar 1.0 uitkomt: alleen GitHub | Gedaan: winkels (F-Droid, Google Play) komen in M10 |
| ✅ 2026-11-18 | Minimum Android van 8.0 naar 9 | Gedaan in v0.52.0; telefoons met Android 8 blijven op v0.51.0 |
| **Open**, vóór M12 af is | Beslissen over de naam: houden of veranderen (de pakketnaam `nl.tufein.openconnect` verandert in elk geval niet). Voorlopig blijft het OpenConnect | Het is jouw project en jouw merk |
| Bij M9 | Schermafbeeldingen van je eigen telefoon aanleveren | Mijn plaatjes komen uit een test, niet van een toestel |
| Bij M13, M14 en M14b | Je export bij Garmin Connect (Account, Gegevensbeheer, *Je gegevens exporteren*) en eventueel Strava aanvragen en een keer in OpenConnect importeren; daarna in Instellingen op *Rapport kopiëren* drukken en dat in een melding op GitHub plakken (aantallen, bestandsnamen zonder e-mailadres, sleutelnamen, geen waarden) | De veldnamen van Garmin's export staan nergens beschreven; alleen een echte export kan bevestigen dat de lezer klopt |

## 6. Na 1.0

De vier sporen staan als **M9 tot en met M12** bij de mijlpalen (paragraaf 4); dit hoofdstuk blijft de korte samenvatting:
M9 proeft alles op echte apparaten, M10 brengt winkelversies, M11 voegt inhoud toe (dagboekvakjes, actieve tijd, nieuwere
protobuf-sync, watchface) en M12 ruimt op (afhankelijkheden, talen, gegevenspagina, naam).

### 1.1-programma: de vijf sporen die Stefan miste (2026-11-21)

Op Stefans verzoek ("we missen nog een hoop zaken") zijn de gaten in vijf sporen gezet. Per spoor eerst de dunne,
testbare slice; daarna de zwaardere delen. Een ✅ betekent uitgebracht.

| Spoor | Eerste slices | Zwaarder werk |
|-------|----------------|----------------|
| **Trainings- en herstelanalyse** | ✅ HRV-status (v1.0.6, eigen 7-vs-28-dagenvergelijking); ✅ slaapfases op Health (v1.0.7, data kwam al binnen) | Training Effect, Recovery Time en Performance Condition uit FIT lezen en tonen (vraagt een echte activiteit van een fenix 8 om de velden te bevestigen) |
| **Work-outs en routes** | ✅ Vrije work-outbouwer bovenop de bestaande FIT-work-outs: naam, herhalingen, snel/rust, warming-up en cooldown, bewaard en met de verzendknop naar het horloge (v1.6.0) | Courses/routes naar het horloge; wat Garmin's API niet toestaat blijft lokale export |
| **Lichaam en voeding** | ✅ Gewicht en vetpercentage met de hand invoeren, met 30-daagse lijn en back-up (v1.0.7); ✅ water loggen met een dagtotaal, lokaal en in de back-up (v1.1.0) | Lichaamssamenstelling uit de Garmin-account |
| **Sync en invoer** | ✅ Ontbrekende stappen, slaap en gewicht automatisch uit Health Connect aanvullen, ook als de app dicht is (v1.2.0); ✅ een training met de hand invoeren, met eigen bron en in de back-up (v1.5.0) | Garmin's nieuwere protobuf-synchronisatie; watchface terug (vraagt een Garmin-ontwikkelaarsaccount) |
| **UX en bereik** | ✅ Drie widgetformaten naast de bestaande widget: klein toont readiness en stappen, de gewone kaart je weekdoel, groot ook slaap en rusthartslag (v1.7.0); "Wat gaat er naar buiten"-pagina in de app | Duits en Frans |

Bewust buiten scope, zoals eerder: segmenten en sociaal, LiveTrack, upload naar Strava of Intervals.icu, Wear OS.

De volgende slices staan sinds 2026-11-23 niet meer los in dit hoofdstuk, maar in de **grote koers naar 2.0**
(paragraaf 6b): eerst het schema dat meedenkt, dan Duits en Frans, de pagina *Wat verlaat je telefoon*, de geschiedenis
en analyse, en de laatste loodjes. De work-outbouwer (v1.6.0) en het jaaroverzicht (v1.9.3) zijn klaar; de
*"wat verlaat je telefoon"*-pagina staat er als v1.12.0 in, omdat ze pas waarde heeft als elke bron ook echt is
geprobeerd of eerlijk gelabeld.

**Bewust niet:** vermogensanalyse, een horlogecompanion, upload naar Intervals.icu of Strava (hun voorwaarden), segmenten en sociaal, labuitslagen, biologische leeftijd, Wear OS.

### De grote koers naar 2.0 (2026-11-24)

1.0 was de belofte dat een vreemde de app kan installeren en vertrouwen. **2.0 is de belofte dat je geen tweede app
nodig hebt**: al je training, al je geschiedenis, alle inzichten en alle uitleg, op je eigen telefoon, zonder account
en zonder abonnement. Drie dingen moeten dan waar zijn:

1. **Compleet.** Van je eerste run tot de training van gisteren staat erin, uit elke bron die je gebruikt, zonder gaten
   en zonder dubbelen. Wat er niet in staat, zegt de app eerlijk.
2. **Het denkt mee.** De app zegt wat je vandaag het beste kunt doen en waarom, uit jouw cijfers — en durft te zeggen
   dat het het niet weet.
3. **Het is uit te leggen.** Van elke tegel is te herleiden welke bron en welke berekening erachter zit, en van elk
   veld dat de telefoon verlaat staat erbij waar het heen gaat en waarom.

#### De vijf poorten van 2.0

| Poort | Nu | Moet waar zijn |
|-------|----|----------------|
| **P1 Geschiedenis** | 🧪 | Elke bron die de app noemt importeert zonder verlies, een tweede import verdubbelt niets, en alles komt terug uit een back-up |
| **P2 Echt** | 🧪 | Elke bron is op echte hardware geprobeerd en gedateerd, of draagt in de app het label *Experimenteel* |
| **P3 Talen** | 🧪 | Nederlands, Engels, Duits en Frans zijn volledig; de taaltest faalt op een ontbrekende regel. De Duitse en Franse teksten zijn machine-draft en niet native nagekeken |
| **P4 Uitleg** | 🧪 | Elke tegel noemt zijn bron en berekening; *Wat verlaat je telefoon* klopt met de code en met de netwerktest |
| **P5 Bereik** | 🧪 | De app installeert en werkt zonder server, zonder netwerk en zonder updater; een winkelversie en de GitHub-versie botsen niet. De store-code bestaat sinds v1.13.0; een echte winkelpublicatie en winkelinstallatie zijn niet geprobeerd |

Een poort gaat pas op groen als het bewijs in de repository staat (een test, een release note of een gedateerde
proef), niet omdat het idee goed klinkt. **P5 is daarom nog niet groen:** de store-code bestaat sinds v1.13.0 en de
builds zijn per kanaal getest, maar er is geen winkelpublicatie en geen echte installatie van een winkelbuild; ook het
naast of over elkaar installeren is niet op een toestel geprobeerd.

#### De vier fasen

| Fase | Uitkomst | Releases | Maat | Wacht op |
|------|----------|----------|------|----------|
| **1. Zichtbaar en eigen** ✅ | De app voelt als één rustig geheel en jij bepaalt hoe hij eruitziet: editorial-look, signaalkleuren, ringen, foto, thema's, iconen en een voorblad dat je zelf samenstelt | v1.9.0 – v1.9.3 | M | — |
| **2. Het denkt mee** ✅ | Het schema past zich aan als je week anders liep: één voorstel met Toepassen/Niet nu; de coach bespreekt alleen dat voorstel en verandert niets | v1.10.0 | L | niets |
| **3. Bereik en uitleg** | Duits en Frans, één pagina *Wat verlaat je telefoon* per bron, en een winkelversie die naast de GitHub-versie kan staan | v1.11.0, v1.12.0, v1.13.0, v1.15.0 | M | naam, account/testers, echte schermafbeeldingen en verklaringen voor publicatie |
| **4. Geschiedenis en analyse** | Garmin's protobuf-sync en de rest van de exportvelden; Training Effect, hersteltijd en Performance Condition uit je eigen FIT | v1.14.0 of later | L ❓ | een echte Garmin-export en één fenix 8-opname van Stefan |
| **Sluiten** | Alle vijf poorten groen; geen nieuwe functie, alleen bewijs en reparaties | v2.0.0 | XL | de vier fasen |

#### De ladder naar 2.0

Deze volgorde is een voorstel, geen wet: elke release staat op zichzelf en mag van plaats wisselen als een proef op
echte hardware daarom vraagt. **S/M/L/XL** zoals in de legenda.

| Versie | Fase | Wat erin zit | Maat |
|--------|------|--------------|------|
| **v1.9.3** ✅ | 1 | Jaaroverzicht, en de gereedschapsketen naar Gradle 9.6, AGP 9.5 en compileSdk 37 | M |
| **v1.10.0** ✅ | 2 | Het schema past zich aan: een gemiste afgesloten week levert één voorstel met Toepassen/Niet nu; de coach bespreekt alleen dat voorstel en verandert niets | L |
| **v1.11.0** ✅ | 3 | Duits en Frans, met een taaltest die elke tekst dekt en een keuze in de instellingen; de teksten zijn machine-draft en niet native nagekeken | M |
| **v1.12.0** ✅ | 3 | *Wat verlaat je telefoon*: één pagina per bron met wat er wel en niet weggaat, plus een test die elk veld dekt | M |
| **v1.13.0** ✅ | 3 | De winkelvariant als code: `github` en `store` uit één codebase, store zonder updater/installatietoestemming/FileProvider, targetSdk 36; AAB en APK gebouwd en gecontroleerd, niet gepubliceerd | S/M |
| **v1.14.0** | 4 | Training Effect, hersteltijd en Performance Condition uit FIT, met een register dat zegt wanneer het op echte data is bevestigd | L ❓ |
| **v1.15.0** | 3 | Laatste loodjes: winkelpublicatie, schermafbeeldingen van een echte telefoon, installatiegids en de naamkeuze | S/M |
| **v2.0.0** | Slot | De vijf poorten hierboven groen; geen nieuwe functie | XL |

**Waarom deze volgorde:** eerst wat je elke dag ziet en zelf bestuurt (klaar), dan wat je training stuurt, dan bereik
en uitleg, en pas daarna de zware geschiedenis en analyse. Die laatste twee wachten op een export en een opname van
Stefan; ze schuiven door zonder de rest op te houden. v1.15.0 maakt de belofte af voordat 2.0 hem alleen nog hoeft te
bewijzen.

**Verschoven:** de Garmin-protobuf-sync en de rest van de archiefvelden stonden oorspronkelijk op v1.13.0. Ze zijn niet
gedaan; die slice wacht nog op een echte Garmin-export en schuift naar v1.14.0 of later. v1.13.0 is de winkelvariant als
code, zonder publicatie.

#### Wat 2.0 niet is

- Geen abonnement, advertenties of cloudopslag van gezondheidsgegevens.
- Geen medische uitspraken of diagnose; de app legt uit wat je cijfers zijn, niet wat je moet doen alsof je arts bent.
- Geen tweede app naast deze, geen horlogecompanion en geen Wear OS-versie.
- Geen vermogensanalyse, segmenten of sociaal netwerk, geen upload naar Intervals.icu of Strava (hun voorwaarden),
  geen labuitslagen en geen biologische leeftijd.
- Geen watchface die een Garmin-account nodig heeft; wat wel kan, staat in fase 3.

#### Wat Stefan moet leveren

| Wanneer | Wat | Waarom het niet zonder jou kan |
|---------|-----|--------------------------------|
| Vóór de Garmin-protobuf-slice (v1.14.0 of later) | Je export bij Garmin Connect (Account, Gegevensbeheer, *Je gegevens exporteren*) en eventueel Strava, één keer in OpenConnect importeren, daarna *Rapport kopiëren* en als melding plakken (aantallen en bestandsnamen, geen waarden) | De veldnamen staan nergens beschreven; alleen een echte export bevestigt de lezer |
| Vóór v1.14.0 | Eén echte fenix 8-opname met Training Effect, hersteltijd en Performance Condition | Alleen jouw horloge levert die velden |
| Doorlopend | De proefronde op telefoon, fenix 8 en Oura-ring, met een nieuw meldingenlogboek | Alleen jij hebt de apparaten en de accounts |
| Vóór winkelpublicatie | De naam kiezen, een Play-ontwikkelaarsaccount met testers regelen, echte schermafbeeldingen en een feature graphic aanleveren, en de verklaringen (gegevensveiligheid, Health Connect, voorgrondservice) afronden | Account-, merk- en bewijsstukken die alleen de eigenaar kan geven; zonder die stukken gaat er niets naar Play of F-Droid |
| Vóór v1.15.0 | De laatste winkelstukken uit de rij hierboven en de naamkeuze | Mijn plaatjes komen uit een test; de naam is jouw merk |

#### Klaar per release

Elke release is klaar als: de tests eerst geschreven zijn en CI groen is; de release notes in het Nederlands zeggen wat
er veranderd is en wat **niet** is gecontroleerd; de Engelse samenvatting klopt; een databaseschema migreert of er is
een verwijderfunctie; en de demo-data de nieuwe functie laat zien. Voor 2.0 komt daar één regel bij: **geen poort groen
zonder bewijs in de repository.**

**Blijft buiten scope:** dezelfde lijst als hierboven, aangevuld met een abonnement of advertenties, cloudopslag van
gezondheidsgegevens, medische uitspraken en een tweede app naast deze.

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
| Claude en Gemini voor rapporten en coach | 🧪 geen live aanroepen met eigen sleutels | [AI.md](AI.md) |
| Archief-import (Strava- en Garmin-export, ZIP en `.gz`) | 🧪 gebouwd met zelf gemaakte archieven in de indeling die de bronnen beschrijven; nooit met een echte export | [MIGRATING.md](MIGRATING.md) |
| In-app-updater (GitHub-versie) | 🧪 niet op een echte telefoon | [ANDROID.md](ANDROID.md) |
| Winkelvariant (`store`) | 🧪 gebouwd en per kanaal getest; niet gepubliceerd, geen echte winkelinstallatie | [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) |
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
| v0.52.0 | Minimum Android van 8.0 naar 9, dode versiecontroles opgeruimd, en dit plan naar 1.0 |
| v0.53.0 | Schoon huis: README opnieuw, `CHANGELOG.md` uit de releasenotes, `SECURITY.md` en `docs/VERSIONING.md`, GitHub Actions bij, CI-flakes aangepakt, Dependabot per familie |
| v0.54.0 | Eerste echte signaal van Stefans telefoon: Home zegt welke dag het toont (gisteren of ouder) en Body Battery is "hoogste" |
| v0.55.0 | M2: Over-scherm met licenties en Garmin-disclaimer, label per bron (getest of experimenteel), *Diagnose delen* zonder geheimen, privacy-uitleg bij de eerste start, sjabloon "Hardwaretest" |
| v0.56.0 | M3: back-up met dagboek, schoenen, plan en doelen, alles exporteren als ZIP, migratietest vanaf elke schemaversie, updater weigert een release zonder checksum en een APK met een andere ondertekenaar |
| v0.57.0 | M4: toegankelijkheidscontrole in de tests (labels, 48 dp, afgekapte tekst; gewone telefoon, 200 procent letters en tablet), grafieken in woorden, schakelaars als één bedieningselement, inhoud op leesbreedte op brede schermen, taalcontrole |
| v0.58.0 | M5, M6 en de afhankelijkheden: de APK 30 naar 10 MB door R8, prestatiebudget met vijf jaar historie, rooktest op een emulator in CI en als poort in `release.yml`, privacyverklaring (ook in de app, met een test op elk adres), installatiegids, vaste kop op de releasepagina, voorbereiding op een release candidate (voorlopige release, `1.0.0-rc.1` en `1.0.0rc1` als dezelfde versie, de updater kent voorlopige versies) |
| v1.0.0-rc.1 | M8: de kandidaat voor 1.0 als GitHub pre-release, code bevroren; changelog sorteert een eindversie boven haar kandidaten |
| v1.0.0-rc.2 | Alle Garmin-families herkend: catalogus van 265 modellen (407 productnummers), herkenning op productnummer in de Bluetooth-advertentie, op de naam en in het FIT-bestand, eerlijke notitie per model, apparaatnaam uit het FIT-bestand |
| v1.0.0-rc.3 | Meldingen van de telefoon op het Garmin-horloge: Garmins meldingsprotocol uit Gadgetbridge, Android-meldingstoegang, per app kiezen, een vaste Bluetooth-verbinding die de sync meebenut, foreground-service, eerlijk over één telefoon per horloge |
| v1.0.0-rc.4 | Meldingen die niet aankomen: de kaart toont per stap waar het stukloopt (*Wat gebeurt er?*), een kopieerbaar logboek zonder inhoud, tellers, tips, "verstuurd" telt alleen wat het horloge hoorde, terugvalroute als het horloge niet om meldingen vraagt, de luisteraar opnieuw laten binden |
| v1.0.0-rc.5 | Een stille Bluetooth-verbinding herkennen (zes keer opnieuw sturen zonder bevestiging, dan opnieuw verbinden), een kleine vraag als het horloge stil is, bewaking van een vastgelopen schrijfactie, één keer diensten zoeken, alle berichten van het horloge beantwoorden zoals Gadgetbridge, logboek met de bytes en het antwoord van het horloge, de kaart onderscheidt een stil van een negerend horloge |
| v1.0.0-rc.6 | Eerste melding kwam aan op de fenix 8, de rest niet: protobuf-vragen krijgen het antwoord van Gadgetbridge (weggegooid, onbekend verzoek) in plaats van een kale ACK, het logboek noemt de dienst, een vastgelopen horloge wordt herkend en opnieuw verbonden met de gemiste melding nog één keer, telfout in "niet beantwoord binnen 5 s" hersteld |
| v1.0.0-rc.7 | Doorloop van alle code, zonder nieuwe functie: een geannuleerde achtergrond-sync blokkeert de volgende niet meer (Oura, WHOOP, Hume Band, Garmin), annuleren wordt niet meer als fout gemeld, een kapotte sleutelkluis laat opslaan niet crashen, vergeten horloge tijdens verversen, `Retry-After` kan niet overlopen, aantikbare rijen als knop voor TalkBack; server: wachtwoord met accent, sessiecookie met afgeleide sleutel en `Secure`, beveiligingsheaders ook op weigeringen, Garmin-antwoord dat geen JSON is, opruimen van wachtende logins |
| v1.0.0 | M8: eerste volledige release, op verzoek van Stefan na rc.7. Moderne standaardstijl: afgeronde kaarten met zachte schaduw, Plex Sans, pill-vormige bediening, vloeiende grafieken en een zwevende tabbalk; Retro en Classic blijven kiesbaar, net als de 24 kleurenthema's. Robolectric-runtime wordt gecachet in CI. |
| v1.0.1-rc.1 | Niet elke melding trilde op de fenix 8: nieuwe woorden in een melding die het horloge al heeft gaan als nieuwe melding (nieuw nummer, zoals Gadgetbridge) in plaats van een stille wijziging, meldingen tijdens het opnieuw verbinden worden 2 minuten bewaard, de agendavraag krijgt "geen afspraken" en Connect IQ-webvragen "geen netwerk", het logboek vat herhaalde vragen samen |
| v1.0.1 | De meldingenreparaties van rc.1 als volledige release, plus thema: een live voorbeeld bovenin Uiterlijk en een keuze van stijl en thema bij de eerste start (op Android 12+ toen nog wallpaper als standaard) |
| v1.0.2 | Sportieve pas: Graphite (koel neutraal, één blauw accent) en Track (oranje) als nieuwe thema's, Graphite als standaard, hoeken 8–20 dp, vlakke panelen met haarlijn, rechte knoppen en een platte tabbalk, compactere typografie; op Home klappen de maandgrafiek en de activiteitenlijst in met hun waarde in de kop |
| v1.0.3 | Alleen scores open: de vier signaaltegels tonen geen lijntjes meer, die zitten met de maandgrafiek en de activiteiten in ingeklapte secties; Insights houdt zes dagelijkse tabs en zet de rest achter *Meer*; widget en startachtergrond volgen het palet |
| v1.0.4 | Op Health één grafiek tegelijk in tabs onder de scores (alleen grafieken met metingen); tikken op een score opent de bijbehorende grafiek, en een "open Health"-tegel komt op de gevraagde grafiek uit |
| v1.0.5 | Zonder Health Connect geen crash: de gatewaystatus wordt via `runCatchingUnlessCancelled` gelezen, een telefoon zonder de dienst rapporteert "niet beschikbaar" (getest). De sprong naar AGP 9/Kotlin 2.4/Compose 2026.11 kompilde en haalde alle tests lokaal, maar bleef op de CI-runner soms in een ongeladen Home hangen (13 timeouts) en is voor de release teruggedraaid; de bevindingen staan bij M12 |
| v1.0.6 | Eerste slice van het 1.1-programma: HRV-status op Health (eigen 7-vs-28-dagenvergelijking, 90/110%-grenzen, minima van 4 en 10 nachten), tikbaar naar de HRV-grafiek; het volledige 1.1-programma staat in de roadmap |
| v1.0.7 | Nachtkaart met diep/licht/REM/wakker (data die al werd ingelezen) en Body-kaart voor gewicht en vetpercentage: schema 6 met migratie vanaf elke versie, back-up, 30-daagse lijn; tweetalige teksten |
| v1.1.0 | Eerste 1.1-release: het HRV- en Body-werk van v1.0.6/v1.0.7 gebundeld plus een Water-kaart op Health (+250/+500 ml, dagtotaal lokaal omdat Garmin geen water levert), schema 7 met migratie vanaf elke versie en het water in de back-up |
| v1.2.0 | De app haalt zelf op: een schakelaar vult ontbrekende stappen, slaap en gewicht uit Health Connect aan (elke vijftien minuten en bij openen), gewicht uit de telefoon vult de Body-kaart achter een optionele leesrecht, en aanvullingen staan in het synchronisatieoverzicht |
| v1.3.0 | M13, begin van het nieuwe thema *je geschiedenis is van jou*: één importknop voor een heel archief (ZIP, ZIP in ZIP, gzip; Garmin- en Strava-export, eigen export), stromend met grenzen tegen een ZIP-bom, nooit overschrijven of verdubbelen (zelfde id of start binnen 90 s), overgeslagen bestanden geteld, rapport in Instellingen en het synchronisatieoverzicht |
| v1.4.0 | M14: namen uit het archief (Strava `activities.csv`, Garmin `summarizedActivities.json` op starttijd), een tweede import geeft eerder binnengehaalde activiteiten hun naam zolang die nog bedacht is, de vorm van niet-gelezen JSON (alleen sleutelnamen) in het rapport met *Rapport kopiëren* |
| v1.5.0 | Trainingen met de hand invoeren: op Activities opent *Toevoegen* een formulier met sport, datum, minuten, kilometers en gemiddelde hartslag; wat je typt wordt je eigen activiteit (bron `manual`), telt mee in de lijst, gaat in de back-up en er is geen schemawijziging nodig |
| v1.6.0 | Work-outbouwer: op Plan staat *Je eigen work-outs* met naam, herhalingen, snelle minuten, rust, warming-up en cooldown; de FIT-work-outs gaan met dezelfde knop naar het horloge, staan in de back-up en er is geen schemawijziging |
| v1.7.0 | Drie widgetformaten: klein toont readiness en stappen, de gewone kaart je weekdoel, groot ook de slaap van de laatste nacht en de rusthartslag; de widget hertekent zich bij het slepen en de minimale maat ging naar 110 × 70 dp |
| v1.8.0 | Batterij van horloge en ring: het Watch-tabblad toont de lading en wanneer die voor het laatst gemeld is (het horloge via een protobuf-vraag bij het verbinden, de ring uit Oura `ring_battery_level`); de work-outbouwer kent afstand en tempo per herhaling (bijv. 6 × 1 km in 5:00–5:30) en je kunt een work-out achteraf bewerken |
| v1.8.1 | De batterijkaart van horloge en ring staat nu ook bovenaan het voorblad (Home), met per device alleen een rij als je dat device hebt; de waarden verversen zodra een horloge- of Oura-sync klaar is, zonder het Watch-tabblad te openen |
| v1.8.2 | Elke sectie op elk tabblad klapt in onder zijn titel (pijltje, begint open, keuze blijft per scherm); het voorblad heeft ook voor kaarten zonder eigen titel een pijltje en de drie knoppen onderaan (Garmin, Watch, Coach) zijn weg. De ringbatterij zegt het als Oura de lading niet deelt en biedt een nieuwe Oura-login aan, met de bestaande client-configuratie |
| v1.8.3 | De WHOOP-band en de Hume Band zijn uit de app: hun tabbladen, instellingen, achtergrond-sync en netwerkcode weg; eerder opgeslagen dagen blijven leesbaar in de geschiedenis en back-ups. Het horloge krijgt kort na het verbinden een tweede batterijvraag, zodat de kaart niet een procent achterloopt |
| v1.9.0 | De editorial-look als standaard: foto-kop, vijf metrische ringen, calorie-boog, serif readiness- en slaapkaarten, journaal-check-in en het Olive-palet; Sleek, Retro en Classic blijven, en een eerdere keuze blijft staan |
| v1.9.1 | Signaalkleuren zacht of helder, ringen dun of dik, en een voorbladfoto die het uur volgt of op één licht blijft; de keuze geldt meteen overal, ook op de Oura-tab |
| v1.9.2 | Vloeiende lijniconen in de onderbalk, zes rustige thema's rond Olive, opnieuw getekende launcher-iconen en een voorblad dat je zelf samenstelt: de meetkaart onder de batterij plus schakelaars voor batterij, journaal en de laatste 7 dagen |
| v1.9.3 | Jaaroverzicht: twaalf maanden stappen, slaap, rusthartslag en trainingen naast elkaar met eerlijke "geen data"-regels; tegelijk de gereedschapsketen naar Gradle 9.6, AGP 9.5 en compileSdk 37, met Kotlin 2.4 als ingebouwde taal |
| v1.10.0 | Een schema dat meedenkt: de nieuwste afgesloten gemiste week wordt één bron voor een begrensd sessiepatroon in een latere trainingsweek; Toepassen/Niet nu blijven bewaard, dubbele of oude acties doen niets, en de coach krijgt alleen die plan- en voorstelfacts om read-only te bespreken |
| v1.12.0 | Wat je telefoon verlaat: twaalf bronpagina’s uit één PrivacyRegister, met bestemming, trigger, velden, opslag en “nooit”-regels; de oude privacytekst is gecorrigeerd en Delete all data wist nu ook lichaam, water, dagboek, schoenen, plan, race, doelen, work-outs, log en ledger |
| v1.13.0 | Twee Android-kanalen uit één codebase: GitHub met updater, installatietoestemming en FileProvider; store zonder die drie en met een verborgen updater; targetSdk 36; AAB en APK gebouwd en gecontroleerd maar niet gepubliceerd; Play/F-Droid geblokkeerd door naam, account/testers, echte schermafbeeldingen en verklaringen |
| v1.11.0 | Vier talen: Engels, Nederlands, Duits en Frans, met een taalkeuze los van de rapporttaal, lokale datums/getallen, Duitse/Franse widget- en meldingsteksten, een opgeruimde Oura-tab en een voorblad dat de dag volgt; de nieuwe talen zijn machine-draft zonder native review |

## 9. Hoe releases werken

Elke versie is een pull request, daarna een GitHub-release met de ondertekende APK (zelfde sleutel, dus de nieuwe versie
installeert over de oude en houdt je gegevens). Er gaat niets uit zonder groene CI. Elke release legt uit wat er in de
code is veranderd en wat niet is gecontroleerd. Voor 1.0 komt daar een voorlopige kandidaat bij (M8).
