# Cercis: roadmap van v1.14 naar 2.0 en verder

Waar Cercis staat, waar het heen gaat en in welke volgorde. **Opnieuw geschreven op 2026-11-28, bij v1.14.0**, nadat
Stefan bevestigde dat de app op zijn fenix 8 en zijn telefoon "allemaal heel goed" werkt. De vorige roadmap (de weg
naar 1.0, de mijlpalen M1 tot en met M16, het 1.1-programma en de eerste koers naar 2.0) staat ongewijzigd in
**[ROADMAP-HISTORIE.md](ROADMAP-HISTORIE.md)**. Het register van wat niet is gecontroleerd (paragraaf 7), de lijst
*Klaar per versie* (paragraaf 8) en de uitleg over releases (paragraaf 9) zijn meegegaan.

Dit plan heeft twaalf **sporen** (A tot en met L) met genummerde onderdelen, een **ladder** die die onderdelen in
releases giet, zeven **poorten** voor 2.0 en een **richting voor 3.0**. Een onderdeel kan van plaats wisselen op de
ladder; het nummer blijft, zodat een release note of een melding er altijd naar kan wijzen (bijvoorbeeld "B4").

Alles blijft **test-first**: eerst de test, dan de code, en niets gaat uit zonder groene CI. Wat zonder horloge, ring,
telefoon of account niet te controleren is, staat er eerlijk bij.

De afvinklijst voor proeven op echte apparaten en het terugdraaiplan staan in
**[PUBLIC_RELEASE.md](PUBLIC_RELEASE.md)**. Dit bestand is het plan; dat bestand is de lijst die je afvinkt.

**Legenda:** ✅ klaar · 🔜 volgende · 🧪 gebouwd en getest met nepgegevens, nog nooit op echte hardware of tegen de
echte dienst · ❓ heeft iets nodig dat alleen de eigenaar (of iemand buiten het project) kan geven · **S/M/L/XL** =
klein (een sessie), middel, groot (meerdere releases), heel groot

## 1. Waar we staan (v1.17.0, 2026-11-28)

- **Wat Cercis is.** Een Android-app die je Garmin-horloge, je Garmin-account, een Oura Ring, Health Connect en je
  eigen bestanden (FIT, GPX, TCX, hele archieven van Garmin en Strava) op je eigen telefoon samenbrengt, zonder server
  en zonder abonnement. Daarnaast een Python-server voor wie liever zelf host. AGPL-3.0, openbare repository,
  Nederlands, Engels, Duits en Frans (de laatste twee machine-draft, niet native nagekeken).
- **Op echte hardware bevestigd (drie bronnen):** de Garmin-account met eigen sleutel (2026-11-12), Bluetooth-sync met
  horlogebatterij en live hartslag op een **fenix 8** (2026-11-28, v1.13.12), en meldingen van de telefoon op dat
  horloge (zelfde dag). In de app dragen die bronnen het label *Getest op echt apparaat* met datum en versie; de rest
  draagt *Experimenteel*. Zie het register in paragraaf 7.
- **Kwaliteit:** ruim 1.400 tests over app, `core` en server, waarvan sinds v1.13.13 **fuzz-tests** die elke
  bestandslezer en elke Bluetooth-lezer met tienduizenden beschadigde invoeren bestoken; dat vond zeven echte fouten,
  die in v1.13.13 zijn gerepareerd. Een emulator-rooktest houdt elke release tegen.
- **Klein:** sinds v1.14.0 is de APK **6,4 MB** in plaats van 13,3 MB, omdat de code (de dex-bestanden) gecomprimeerd
  in de APK zit. Het budget ging van 14 naar **8 MiB**, met bijna 1,9 MB over.
- **Onderhoudbaar:** sinds v1.14.0 staan de schermtoestanden in een eigen bestand (`ui/AppState.kt`) en de
  doorgeefluiken naar horloge, Health Connect, meldingen, Garmin, Oura en AI in `ui/AppViewModelDevices.kt`;
  `AppViewModel.kt` ging van 1.754 naar 1.402 regels, zonder gedragswijziging. Een **pre-push-controle**
  (`tools/prepush.sh`) vangt opmaakfouten, een verouderde CHANGELOG en een versienummer dat niet overal gelijk is
  voordat CI dat doet.
- **Sinds v1.15.0: het retro-pakket, deel 1.** Een LCD-palet in vier tinten groen, scanlijnen over de stijl Retro en
  korte 8-bit-deuntjes als een sync iets nieuws binnenhaalt. Alles eigen ontwerp, alles standaard uit, en geen enkel
  beeld- of geluidsbestand erbij: de lijnen worden getekend en de deuntjes op de telefoon uitgerekend.
- **Sinds v1.16.0: level-up.** Speelt nieuwe data een trofee of een nieuw level vrij, dan zegt de app dat in een korte
  melding, met een eigen deuntje als de 8-bit-geluiden aan staan. Standaard uit; een grote import of de demo blijft
  stil, zodat je niet twintig felicitaties achter elkaar krijgt.
- **Sinds v1.17.0: de eregalerij.** Op de tab Trofeeën staan per afstand je vijf snelste runs, met de drie letters die
  je zelf kiest, zoals de tabel op een arcadekast. Bij het bouwen bleek dat de back-up je zelfgemaakte workouts nooit
  bewaarde (sinds v1.6.0); dat is gerepareerd.
- **Wat nog zwak is:**
  1. **De meeste bronnen zijn nooit op echte hardware geprobeerd:** USB, Health Connect, Oura, de AI-sleutels, de
     updater, de archief-import met een echte export en de winkelvariant. Dat is spoor B, D, E en F, en de rij in
     paragraaf 6.
  2. **De ondertekeningssleutel staat nog in een privé-conceptrelease** in plaats van in GitHub-secrets met een
     offline kopie (H1). Alleen Stefan kan dat veranderen; de sleutel zelf mag **niet** worden vervangen, want dan
     werkt geen enkele geïnstalleerde app nog bij.
  3. **Vijftien compilerwaarschuwingen** in eigen code, waarvan negen omdat `EncryptedSharedPreferences` en `MasterKey`
     door Google zijn afgeschreven (A6, H3).
  4. **De naam Cercis is juridisch niet gecontroleerd** en er is geen Play-account; de winkelvariant blijft daarom
     ongepubliceerd (I1, I2).
  5. **`AppViewModel` is nog steeds één klasse voor alle schermen** (A5).

## 2. Richting

### Wat 1.0 beloofde (gehaald op 2026-11-20)

Het werkt op een echte telefoon en wat niet getest is staat er in de app bij; je gegevens blijven van jou en gaan niet
verloren bij een update; een vreemde kan het zelf installeren, begrijpen en vertrouwen.

### Wat 2.0 belooft: je hebt geen tweede app nodig

1. **Compleet.** Van je eerste run tot de training van gisteren staat erin, uit elke bron die je gebruikt, zonder gaten
   en zonder dubbelen. Wat er niet in staat, zegt de app eerlijk.
2. **Het denkt mee.** De app zegt wat je vandaag het beste kunt doen en waarom, uit jouw cijfers, en durft te zeggen
   dat hij het niet weet. Ook zonder AI.
3. **Het is uit te leggen.** Van elke tegel is te herleiden welke bron en welke berekening erachter zit, en van elk
   veld dat de telefoon verlaat staat erbij waar het heen gaat en waarom.
4. **Het is gezond gebouwd.** Geen afgeschreven beveiligings-API's, geen compilerwaarschuwingen, elke lezer gefuzzd,
   en een sleutel die veilig bewaard is.
5. **Het is leuk.** Cercis heeft een eigen gezicht, met een retro-hart: pixelletters, trofeeën, een CRT- en een
   LCD-look, en kleine beloningen die je zelf aan- of uitzet. Allemaal eigen ontwerp.

### Waar 3.0 heen gaat: je eigen sportlab

Na 2.0 is de vraag niet meer "kan ik zonder Garmin Connect", maar "kan ik met mijn eigen gegevens alles wat ik wil".
De richting, nog zonder belofte:

- **Al je eigen apparaten, niemand ertussen.** Telefoon, tablet en een eigen server houden elkaar bij, eind-tot-eind
  versleuteld, met het open formaat (K1) als contract. Geen cloud van ons.
- **Analyses als bouwstenen.** Elke berekening (vorm, VO2max, drempel, slaapritme) is nu al een zuivere functie met
  tests. In 3.0 wordt dat een gedocumenteerde laag waar iemand anders een eigen analyse aan kan toevoegen, met dezelfde
  testregels.
- **Een eerlijke coach die op de telefoon blijft.** Regelgebaseerd waar dat kan, AI alleen als jij een sleutel geeft,
  en altijd met de bron erbij.
- **Beslissingen die bij Stefan liggen (❓):** een eigen Connect IQ-dataveld op het horloge (botst met *geen
  horlogecompanion* uit paragraaf 10), synchroniseren tussen telefoons in één huishouden, en of de server een eigen
  webweergave krijgt.

## 3. De zeven poorten van 2.0

2.0 gaat uit als **alle zeven** poorten groen zijn. Een poort gaat pas op groen als het bewijs in de repository staat
(een test, een release note of een gedateerde proef), niet omdat het idee goed klinkt.

| Poort | Nu | Moet waar zijn | Sporen |
|-------|----|----------------|--------|
| **P1 Geschiedenis** | 🧪 | Elke bron die de app noemt importeert zonder verlies, een tweede import verdubbelt niets, alles komt terug uit een back-up, en de archief-import is met minstens één echte Garmin- of Strava-export bevestigd | E, K |
| **P2 Echt** | 🧪 | Elke bron is op echte hardware of tegen de echte dienst geprobeerd en gedateerd, of draagt in de app het label *Experimenteel* | B, D, F |
| **P3 Talen** | 🧪 | Nederlands, Engels, Duits en Frans volledig; de taaltest faalt op een ontbrekende regel; Duits en Frans native nagekeken | I |
| **P4 Uitleg** | 🧪 | Elke tegel noemt zijn bron en berekening; *Wat verlaat je telefoon* klopt met de code en met een netwerktest | F, H |
| **P5 Bereik** | 🧪 | De app werkt zonder server, netwerk en updater; een winkelversie en de GitHub-versie botsen niet; minstens één winkelinstallatie of een reproduceerbare F-Droid-build | I |
| **P6 Fundament** | 🔜 | Nul compilerwaarschuwingen in eigen code als CI-poort, geen afgeschreven beveiligings-API's, fuzz-tests over elke lezer, drie CI-runs op rij groen zonder opnieuw starten, APK binnen budget | A, H |
| **P7 Sleutel** | ❓ | De ondertekeningssleutel in GitHub-secrets met een offline kopie, de conceptrelease weg, privé melden van kwetsbaarheden aan | H |

**Wat er al groen is binnen P6:** de fuzz-tests (v1.13.13) en het APK-budget (v1.14.0). Wat er nog mist: de
waarschuwingen en de afgeschreven sleutelkluis.

## 4. De sporen

Elk onderdeel heeft een nummer, wat het is, hoe we weten dat het klopt, een maat en waar het op wacht. Een ✅ noemt de
versie waarin het uitkwam.

### A. Kwaliteit en fundament

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| A1 | ✅ v1.13.13 Fuzz-tests over elke bestands- en Bluetooth-lezer (app, `core`, server) | Vaste seeds; elke andere uitzondering dan de eigen fout faalt | M | — |
| A2 | ✅ v1.14.0 Dex gecomprimeerd in de APK: 13,3 naar 6,4 MB, budget 8 MiB | `check_apk_size.py` in elk pull request en elke release; zipalign en apksigner groen | S | — |
| A3 | ✅ v1.14.0 Pre-push-controle: ruff, CHANGELOG, versie op vier plekken, release note bestaat | `tests/unit/test_prepush.py` | S | — |
| A4 | ✅ v1.14.0 Eén scroll-helper voor schermtests (`scrollToText`, ook in `clickText`) | De Home-tests die wisselend faalden gebruiken hem | S | — |
| A5 | AppViewModel per scherm: eigen ViewModels voor Home, Gezondheid, Inzichten, Activiteiten, Horloge en Instellingen, gedeelde toestand in de repository. Stap 1 (toestanden en doorgeefluiken naar eigen bestanden) is ✅ v1.14.0 | Elke schermtest blijft groen zonder aanpassing van de verwachting | M | — |
| A6 | Nul compilerwaarschuwingen in `app` en `core`, daarna `allWarningsAsErrors` als CI-poort | De build faalt op een nieuwe waarschuwing | S/M | H3 |
| A7 | Nachtelijke fuzz-run met nieuwe seeds (geplande workflow); een gevonden fout komt als test met die seed terug | De workflow draait elke nacht en bewaart de seed | S | — |
| A8 | Mutatietests (PIT) op de inzichtberekeningen in `core`, met een minimumscore | Rapport als CI-artefact; een daling faalt | M | — |
| A9 | Opstartprofiel (baseline profile) en een opstartbudget in de prestatietest | Koude start gemeten op de emulator, budget in `PERFORMANCE.md` | M | — |
| A10 | Gradle-configuratiecache en buildcache in CI | CI-tijd per job vóór en na in de release note | S | — |
| A11 | "Drie keer groen" meten: een script leest de laatste runs van `main` en zegt of G4/P6 haalbaar is | Script met een test op een opgenomen API-antwoord | S | — |
| A12 | Register van wiebelende tests: elke herkansing komt in een bestand, doel nul | De rooktest en de schermtests schrijven hun herkansing weg | S | — |
| A13 | StrictMode in debug-builds: schijf of netwerk op de hoofdthread is een fout | Robolectric-test die een overtreding opvangt | S | — |
| A14 | Afhankelijkheden bij zonder sprongen: Dependabot per familie blijft, elke grote sprong een eigen release | CI groen, rooktest groen | S per keer | — |

### B. Het horloge (fenix 8 en andere Garmins)

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| B1 | ✅ v1.13.13 Bluetooth-sync, horlogebatterij, meldingen en live hartslag bevestigd op een fenix 8 | Stefans melding van 2026-11-28 | — | — |
| B2 | **Training Effect** (aeroob en anaeroob), **hersteltijd** en **Performance Condition** uit de FIT van een activiteit, met uitleg en een register dat zegt of het op echte data bevestigd is | Test met een echte fenix 8-FIT als vast bestand | L | ❓ één echte activiteit van Stefans fenix 8 |
| B3 | **Synchroniseren zonder de app te openen:** koppelen via Android's CompanionDeviceManager, zodat het horloge op de achtergrond synct als het in de buurt komt; WorkManager als terugval | Robolectric met een neplink; echt: een nacht op Stefans telefoon | L | proef van Stefan |
| B4 | **Agenda op het horloge:** het horloge vraagt nu al om afspraken en krijgt "geen afspraken"; met toestemming (standaard uit) krijgt het de echte afspraken van vandaag en morgen, met een regel in *Wat verlaat je telefoon* | Nepwatch stuurt de agendavraag; het antwoord bevat de afspraken | M | bevestiging op fenix 8 |
| B5 | **Zoek mijn telefoon** vanaf het horloge: de telefoon gaat over, ook op stil | Nepwatch stuurt het verzoek; de app start het geluid | S/M | protocol bevestigen op fenix 8 |
| B6 | **Muziekbediening** van de telefoon vanaf het horloge (afspelen, volgende, volume) | Nepwatch; Android MediaSession in Robolectric | M | bevestiging op fenix 8 |
| B7 | **Weer op het horloge** via Open-Meteo (geen sleutel), standaard uit, alleen een afgeronde locatie | Neptijdserver; privacytest op velden | M | ❓ Stefan kiest of dit mag (netwerk) |
| B8 | **Routes naar het horloge:** een GPX wordt een FIT-course en gaat via Bluetooth of USB naar het horloge | Course-FIT tegen de FIT-specificatie; echte proef | L | ❓ of een fenix 8 de course accepteert |
| B9 | Garmin's nieuwere **protobuf-bestandssync** naast de bestaande GFDI-sync | Nepwatch met opgenomen berichten | L | ❓ een opname van Stefans horloge |
| B10 | **Profiel en zones uit het horloge** lezen (gewicht, maximale hartslag, zones) en voorstellen om ze over te nemen | Nepwatch; nooit stil overschrijven | M | bevestiging op fenix 8 |
| B11 | **Meer dan één horloge:** wisselen zonder opnieuw te koppelen, met per horloge een eigen batterij en log | Twee nepwatches in één test | M | — |
| B12 | USB-proef (T6) en scherm-uit-proef (T8) en batterijproef (T16) uit `PUBLIC_RELEASE.md` | Gedateerde rij in het register | S | ❓ Stefan |

### C. Training en analyse

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| C1 | **Grade-adjusted pace** (tempo gecorrigeerd voor helling) per activiteit en per kilometer | Zuivere functie met bekende hellingen en een gepubliceerde energiecurve | M | — |
| C2 | **Rondes en intervallen:** de rondes uit de FIT tonen, en snelle en rustige blokken automatisch herkennen als er geen rondes zijn | Vaste FIT-bestanden met en zonder rondes | M | — |
| C3 | **Drempelschatting:** een lactaatdrempel-hartslag uit je eigen inspanningen van 20 tot 60 minuten, als voorstel voor de zones | Tests op gegenereerde inspanningen; het blijft een vuistregel en dat staat erbij | M | — |
| C4 | **Tempozones** naast de hartslagzones, per activiteit en per week | Tests zoals bij de hartslagzones | S | C3 |
| C5 | **Trainingsstatus** (productief, onderhoud, te veel, te weinig) uit je eigen belasting en de trend van VO2max | Tabeltest per toestand; uitleg bij elke uitkomst | M | — |
| C6 | **Cadans en paslengte** als de FIT ze heeft, met een trend | Vaste FIT; leeg als het veld ontbreekt | S | — |
| C7 | **Zwemmen:** baantjes en slagen uit een zwembad-FIT | Vaste FIT | M | ❓ een echte zwem-FIT |
| C8 | **Kracht:** sets en herhalingen uit de FIT | Vaste FIT | M | ❓ een echte kracht-FIT |
| C9 | **Racedag:** een afbouwweek (taper) in het schema voor het racedoel, met het schema dat meedenkt | Tests op het plan | M | — |
| C10 | **Splits vergelijken op dezelfde route:** per kilometer naast je beste en je vorige keer | Tests op twee gegenereerde routes | S | — |

### D. Gezondheid

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| D1 | **Gezondheid door de jaren:** stappen, slaap en rusthartslag jaar op jaar, zoals Terugblik voor training (bewust buiten M15 gelaten) | Zuivere functies met tests; schermtest op demodata | M | — |
| D2 | **HRV-nacht** als grafiek met je eigen normale band | Tests op de band | S | — |
| D3 | **Slaapregelmaat** (Sleep Regularity Index) naast het slaapritme | Tests op bekende patronen | S | — |
| D4 | **Ziek of geblesseerd** als dag in het dagboek: het schema en de vormberekening pauzeren in plaats van je te straffen | Tests op plan en vorm | M | — |
| D5 | **Bloeddruk** met de hand invoeren en uit Health Connect lezen, met schemamigratie en back-up | Migratietest vanaf elke schemaversie | M | — |
| D6 | **Ademhaling en SpO2** als trend met eerlijke grenzen en een "geen meting"-regel | Tests op lege en echte reeksen | S | — |
| D7 | **Health Connect schrijven:** ook trainingen en hartslag, niet alleen stappen, slaap en gewicht | Fake Health Connect-client | M | proef op een echte telefoon |
| D8 | **Dagen uit de Garmin-export** (het oude M14b): stappen, rusthartslag en slaap waar de app nog niets heeft | Lezer pas na een echt rapport | M | ❓ Stefans export en *Rapport kopiëren* |
| D9 | Health Connect op een echte telefoon proberen | Gedateerde rij in het register | S | ❓ Stefan |

### E. Je geschiedenis

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| E1 | ✅ Archief-import (v1.3.0), namen (v1.4.0), Terugblik (v1.13.12) | Zie paragraaf 8 | — | — |
| E2 | **Echte export bevestigen:** een Garmin- en een Strava-export één keer importeren en het rapport delen | Rapport in een melding, zonder waarden | S | ❓ Stefan |
| E3 | **Samenvoegen en splitsen:** twee opnames van dezelfde training (horloge en ring) samenvoegen, een vergeten stop afknippen | Tests op track, tijden en totalen; terug te draaien | M | — |
| E4 | **Prullenbak:** een verwijderde activiteit blijft 30 dagen terug te halen | Schematest en back-uptest | S/M | — |
| E5 | **Achteraf bewerken:** sport, naam, notitie en schoen van een activiteit | Schermtest; bron blijft zichtbaar | S | — |
| E6 | **Dubbelen-rapport:** over alle bronnen heen tonen wat mogelijk dubbel is, met één tik om te houden of samen te voegen | Tests op de 90-secondenregel en verschillende bronnen | S | E3 |
| E7 | **Op deze dag:** wat je een, twee of vijf jaar geleden vandaag deed, als kaart op Home (uit te zetten) | Tests op schrikkeljaren en lege dagen | S | — |

### F. Coach en AI

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| F1 | **Coach zonder AI:** een weekterugblik en een advies voor vandaag uit regels, offline, met de bron per zin | Tabeltests op de regels | M | — |
| F2 | **Zien wat er weggaat:** vóór een AI-vraag de exacte tekst tonen die naar Claude of Gemini gaat | Test dat het voorbeeld en het verzoek gelijk zijn | S | — |
| F3 | **"Waarom zegt de app dit?"** bij elke tegel: bron, berekening en de cijfers erachter (P4) | Elke tegel heeft een uitleg; een test faalt op een tegel zonder | M | — |
| F4 | **Modellen bij:** de nieuwste Claude- en Gemini-modellen als standaard, met een test die de lijst bewaakt | Unit-test op de modellijst | S | — |
| F5 | **Kosten per vraag** (tokens in en uit) in de coach | Tests op het tellen | S | F4 |
| F6 | AI met echte sleutels proberen | Gedateerde rij in het register | S | ❓ Stefan |

### G. Uiterlijk en gevoel, met een retro-hart

Eigen ontwerp, geen namaak van een bestaand spel: eigen pixeltekeningen, eigen kleuren, eigen geluidjes. Elke
beweging volgt de systeeminstelling *Animaties verwijderen*, elk geluid staat standaard uit, en elk nieuw thema haalt
de contrasttest.

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| G1 | **Trofeeënkast in pixels:** de bestaande trofeeën krijgen eigen 16×16-pixelbadges (als vector, dus geen APK-groei), met de datum waarop je ze haalde | Schermtest; elke trofee heeft een tekening en een TalkBack-tekst | M | — |
| G2 | ✅ v1.15.0 **CRT-look:** een optionele laag met scanlijnen voor de stijl Retro (een zachte gloed kan later) | Contrasttest en schermafbeelding in de test | S/M | — |
| G3 | ✅ v1.15.0 **LCD-palet:** vier tinten groen zoals een oud handheld-scherm, als zevende palet | Contrasttest op elke tekstkleur | S | — |
| G4 | ✅ v1.16.0 voor trofeeën en levels (record en weekdoel volgen in v1.19.0) **Level-up:** een korte felicitatie bij een nieuwe trofee, een nieuw level, een nieuw record of een gehaald weekdoel | Schermtest; geen animatie, dus niets uit te zetten bij *Animaties verwijderen*; TalkBack leest de melding voor | S | — |
| G5 | ✅ v1.15.0 en v1.16.0 (bij een sync, een trofee en een level) **8-bit-geluidjes** bij een record of een afgeronde sync, op de telefoon zelf opgewekt (geen geluidsbestanden), standaard uit | Test op de toongenerator; geluid alleen als het aan staat | S | — |
| G6 | **"Continue?"** na een gemiste week: het voorstel van het schema dat meedenkt, als speels scherm met Toepassen of Niet nu | Schermtest op beide knoppen | S | — |
| G7 | **Pixel-heatmap:** de heatmap als blokjeskaart in de stijl Retro | Schermtest | S | — |
| G8 | **Widgets in elke stijl,** ook Retro en LCD | Glance-test per stijl | S | G3 |
| G9 | ✅ v1.17.0 (als *Eregalerij* op de tab Trofeeën; de naam Hi-score was al van de jaarkaart) **Highscore-tabel per afstand** met initialen die je zelf kiest (drie letters), naast de bestaande records | Schermtest; back-up bewaart de initialen | S | — |
| G10 | Echte schermafbeeldingen en een TalkBack-proef (T14) | Gedateerde rij in `PUBLIC_RELEASE.md` | S | ❓ Stefan |

### H. Privacy en beveiliging

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| H1 | **Sleutel naar GitHub-secrets** met een offline kopie, en de privé-conceptrelease `signing-key` weg. **Niet vervangen:** een nieuwe sleutel betekent dat geen geïnstalleerde app meer bijwerkt | De release-workflow leest de sleutel alleen nog uit secrets | S | ❓ Stefan |
| H2 | **Privé melden van kwetsbaarheden** aanzetten (Settings, Security) | Link in `SECURITY.md` werkt | S | ❓ Stefan |
| H3 | **Eigen sleutelkluis** op de Android Keystore (AES-GCM) in plaats van het afgeschreven `EncryptedSharedPreferences`, met een migratie die oude sleutels meeneemt | Robolectric-test: oude kluis in, nieuwe kluis uit, niets kwijt | M | — |
| H4 | **Versleutelde back-up** met een wachtwoord (optioneel) | Test: zonder wachtwoord geen inhoud; fout wachtwoord nette fout | M | — |
| H5 | **Netwerktest:** een test kent elke host die de app kan aanspreken en faalt op een nieuwe, gekoppeld aan *Wat verlaat je telefoon* | Unit-test over de bronnen | S/M | — |
| H6 | **Herkomst:** GitHub Actions vastgepind op commit, Gradle-verificatie van afhankelijkheden | CI faalt op een gewijzigde afhankelijkheid | S/M | — |
| H7 | **SBOM** (CycloneDX) als bijlage bij elke release | Release-workflow controleert dat hij er is | S | — |
| H8 | **Reproduceerbare build:** dezelfde bron geeft dezelfde APK-bytes (op de handtekening na) | Twee builds in CI vergeleken | L | — |

### I. Bereik en verspreiding

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| I1 | Juridische controle van de naam **Cercis** | Gedateerde regel in `PUBLIC_RELEASE.md` | S | ❓ Stefan |
| I2 | Play-ontwikkelaarsaccount met testers (12 testers, 14 dagen bij een nieuw persoonlijk account) | Gesloten test loopt | M | ❓ Stefan |
| I3 | **F-Droid:** metadata en een reproduceerbare build | F-Droid-build slaagt | M/L | H8 |
| I4 | Duits en Frans **native nagekeken** | Lijst met nagekeken schermen in `LANGUAGES.md` | M | ❓ moedertaalsprekers |
| I5 | Installatiegids met schermafbeeldingen van een echte telefoon | `INSTALL.md` met echte beelden | S | ❓ Stefan |
| I6 | **Obtainium in één tik:** een link en QR-code die de repository en de juiste APK al invullen | Test op de link | S | — |
| I7 | Engelstalige korte versie van dit plan in de README | Linkcontrole | S | — |

### J. De server

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| J1 | ✅ v1.13.13 Fuzz-tests op de GPX-, TCX- en FIT-lezers | `tests/unit/test_formats_fuzz.py` | S | — |
| J2 | **`openconnect import archief.zip`** met dezelfde regels als de app (nooit overschrijven of verdubbelen) | Dezelfde testarchieven als de app | M | — |
| J3 | **De telefoon schrijft naar je eigen server** (opt-in, token, alleen HTTPS) | Integratietest app-formaat naar server | L | K1 |
| J4 | Python 3.14 in de testmatrix | CI-matrix | S | — |
| J5 | Docker-image voor arm64 (Raspberry Pi) naast amd64, als release-bijlage of in de pakketregistry van de repository | Build in CI op beide architecturen | S/M | — |
| J6 | Zakelijke Garmin-OAuth (Developer Program) | Proef tegen Garmin | M | ❓ goedkeuring van Garmin |

### K. Open en koppelbaar

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| K1 | **Eigen open formaat:** gedocumenteerd, versiebeheerd JSON naast CSV, dat de app ook weer inleest | Een test per schemaversie: export, import, gelijk | M | — |
| K2 | **Het schema als agenda:** het trainingsplan exporteren als iCalendar-bestand | Test tegen de iCalendar-regels | S | — |
| K3 | **Koppelen met Tasker en Home Assistant:** alleen lezen, standaard uit, met een regel op de privacypagina | Robolectric-test op de koppeling en de schakelaar | M | — |
| K4 | **Webhook naar je eigen server** bij een nieuwe activiteit | Test met een nepserver | S/M | J3 |

### L. Documentatie en community

| # | Wat | Hoe we weten dat het klopt | Maat | Wacht op |
|---|-----|----------------------------|------|----------|
| L1 | ✅ v1.14.0 **Zo werk je verder:** paragraaf 9 legt stap voor stap uit hoe je bouwt, test en een release maakt | Een release volgens die stappen | S | — |
| L2 | `ARCHITECTURE.md` bij met de opsplitsing van AppViewModel | Linkcontrole | S | A5 |
| L3 | **Bijdragersgids per onderwerp:** een horlogemodel, een thema, een taal of een bron toevoegen | Elke gids verwijst naar de test die je moet uitbreiden | S | — |
| L4 | Sjablonen voor meldingen per bron (de *Hardwaretest* bestaat) | Sjablonen in `.github/ISSUE_TEMPLATE` | S | — |

## 5. De ladder

Een voorstel, geen wet: elke release staat op zichzelf en mag van plaats wisselen als een proef op echte hardware
daarom vraagt. Wat op Stefan wacht (❓) staat bewust laat, zodat het de rest niet ophoudt; komt het eerder binnen, dan
schuift het naar voren. Versienummers worden pas bij het uitbrengen gegeven.

| Versie | Naam | Wat erin zit | Maat |
|--------|------|--------------|------|
| **v1.13.13** ✅ | Bevestigd | Fenix 8 bevestigd (B1), zeven fouten uit fuzz-tests (A1, J1), appcompat 1.8.0 | S |
| **v1.14.0** ✅ | Schoon fundament | APK 6,4 MB en budget 8 MiB (A2), pre-push (A3), scroll-helper (A4), AppViewModel stap 1 (A5), deze roadmap (L1) | M |
| **v1.15.0** ✅ | Retro, deel 1 | LCD-palet (G3), scanlijnen (G2), 8-bit-deuntjes na een sync met iets nieuws (G5) | S/M |
| **v1.16.0** ✅ | Retro, deel 2: level-up | Een melding bij een nieuwe trofee of een nieuw level (G4), met het record- of doeldeuntje als de geluiden aan staan (G5); een grote import blijft stil | S |
| **v1.17.0** ✅ | Retro, deel 3: eregalerij | Je vijf snelste runs per afstand met je eigen initialen (G9); de back-up bewaart eindelijk ook je zelfgemaakte workouts | S |
| **v1.18.0** | Zonder waarschuwingen | Eigen sleutelkluis (H3), nul waarschuwingen als poort (A6), StrictMode (A13), configuratiecache (A10) | M |
| **v1.19.0** | Retro, deel 4 | Pixeltrofeeën (G1), level-up bij een record en een gehaald weekdoel (rest van G4), "Continue?" (G6) | M |
| **v1.20.0** | Looptechniek | Grade-adjusted pace (C1), rondes en intervallen (C2), cadans en paslengte (C6), splits op dezelfde route (C10) | M |
| **v1.21.0** | Drempel | Drempelschatting (C3), tempozones (C4), trainingsstatus (C5) | M |
| **v1.22.0** | Gezondheid door de jaren | Jaar op jaar (D1), HRV-nacht (D2), slaapregelmaat (D3), ziek of geblesseerd (D4), ademhaling en SpO2 (D6) | M |
| **v1.23.0** | Open formaat | Open JSON (K1), schema als agenda (K2), prullenbak (E4), achteraf bewerken (E5) | M |
| **v1.24.0** | Horloge praat terug | Agenda (B4), zoek mijn telefoon (B5), muziekbediening (B6): met nepwatch getest, op de fenix 8 te bevestigen | M/L |
| **v1.25.0** | Eerlijke coach | Coach zonder AI (F1), zien wat er weggaat (F2), "Waarom zegt de app dit?" (F3), modellen bij (F4) | M |
| **v1.26.0** | Op de achtergrond | Synchroniseren zonder de app te openen (B3), meer dan één horloge (B11) | L |
| **v1.27.0** | Geschiedenis heel | Samenvoegen en splitsen (E3), dubbelen-rapport (E6), op deze dag (E7) | M |
| **v1.28.0** | Koppelbaar | Tasker en Home Assistant (K3), server-import (J2), Python 3.14 (J4) | M |
| **v1.29.0** | Thuisbasis | Telefoon naar eigen server (J3), webhook (K4), versleutelde back-up (H4) | L |
| **v1.30.0** | Herkomst | Netwerktest (H5), vastgepinde Actions en verificatie (H6), SBOM (H7), reproduceerbare build (H8) | M/L |
| **v1.31.0** | Echte data ❓ | Training Effect en co (B2), dagen uit de export (D8), bevestigde archief-import (E2): zodra Stefans opname en export er zijn | L ❓ |
| **v1.32.0** | Winkelklaar ❓ | Naam (I1), Play-account (I2), F-Droid (I3), native talen (I4), echte schermafbeeldingen (I5, G10) | M ❓ |
| **v2.0.0-rc.1** | Bevriezen | Geen nieuwe functie meer; alleen bewijs en reparaties | M |
| **v2.0.0** | Twee punt nul | De zeven poorten van paragraaf 3 groen | XL |

**Waarom deze volgorde:** de eerste drie delen van het retro-pakket kwamen vóór de sleutelkluis omdat ze klein zijn,
volledig in tests te controleren en niets aan je opgeslagen sleutels raken. De sleutelkluis (H3) verhuist je AI-,
Garmin- en Oura-sleutels naar een nieuwe opslag; een fout daarin kost je die sleutels, dus die release krijgt een eigen
proef op een echte telefoon voordat hij uitgaat. Daarna eerst wat de basis gezond maakt (geen
afgeschreven sleutelkluis meer onder je AI- en Garmin-sleutels), dan de rest van het leuke werk, dan analyse die alleen je
eigen bestanden nodig heeft, en daarna pas het werk dat op een echte opname, een export of een account wacht.
Tussendoor komen patchreleases als een proef of een melding daarom vraagt.

### Na 2.0

| Versie | Richting | Wat |
|--------|----------|-----|
| **v2.x** | Verdiepen | De onderdelen die op ❓ bleven wachten; routes naar het horloge (B8), protobuf-sync (B9), profiel uit het horloge (B10), weer (B7) als Stefan daarvoor kiest, zwemmen en kracht (C7, C8) |
| **v2.x** | Kwaliteit | Mutatietests (A8), opstartprofiel (A9), nachtelijke fuzz (A7), wiebelregister (A12) |
| **v3.0.0** | Je eigen sportlab | Eind-tot-eind versleuteld bijhouden tussen je eigen apparaten, analyses als gedocumenteerde bouwstenen, een coach die op de telefoon blijft; zie paragraaf 2 |

## 6. Wat alleen Stefan kan

| Wanneer | Wat | Waarom het niet zonder jou kan |
|---------|-----|--------------------------------|
| **Zo snel mogelijk** | De ondertekeningssleutel in GitHub-secrets zetten, een offline kopie bewaren en de privé-conceptrelease `signing-key` verwijderen (H1). **Vervang de sleutel niet** | Alleen de eigenaar beheert secrets en bewaart een kopie; een nieuwe sleutel breekt het bijwerken van elke geïnstalleerde app |
| Zo snel mogelijk | Privé melden van kwetsbaarheden aanzetten (H2) | Instelling van de eigenaar |
| Bij v1.31.0 | Eén activiteit van je fenix 8 als FIT aanleveren, liefst een harde training (B2) | Alleen jouw horloge levert Training Effect, hersteltijd en Performance Condition |
| Bij v1.31.0 | Je export bij Garmin Connect en eventueel Strava aanvragen, één keer importeren en *Rapport kopiëren* in een melding plakken (E2, D8) | De veldnamen van die exporten staan nergens beschreven |
| Wanneer het uitkomt | De proeven T6 (USB), T8 (scherm tien minuten uit), T14 (TalkBack) en T16 (batterij) uit `PUBLIC_RELEASE.md` | Alleen jij hebt de apparaten |
| Wanneer het uitkomt | Oura, Health Connect en de AI-sleutels een keer echt proberen (D9, F6) | Alleen jij hebt de accounts en sleutels |
| Bij v1.24.0 | Agenda, zoek mijn telefoon en muziekbediening op de fenix 8 proberen (B4 tot en met B6) | Het protocol is alleen op een echt horloge te bevestigen |
| Vóór B7 | Kiezen of het weer naar het horloge mag (netwerkverkeer naar Open-Meteo) | Een privacykeuze |
| Bij v1.32.0 | Juridische naamcontrole, Play-account met testers, echte schermafbeeldingen, moedertaalsprekers voor Duits en Frans (I1 tot en met I5) | Account-, merk- en bewijsstukken |
| Vóór 3.0 | Kiezen over een eigen Connect IQ-dataveld, delen binnen een huishouden en een webweergave op de server | Richtingkeuzes |
| ✅ 2026-11-28 | Bevestigd dat de app op de fenix 8 en je telefoon werkt | Gedaan; staat in de app en in paragraaf 7 |
| ✅ 2026-11-25 | Weergavenaam Cercis gekozen | Gedaan; de juridische controle staat nog open |
| ✅ 2026-11-18 | Alleen GitHub als verspreiding voor 1.0; minimum Android 9 | Gedaan |

## 7. Wat nog niet is gecontroleerd (register)

De bron van waarheid voor poort P2 (en vroeger G1). Een regel gaat pas naar ✅ als er een echte proef op staat met datum en versie.

| Bron of onderdeel | Stand | Waar staat het |
|-------------------|-------|----------------|
| Garmin-account met eigen sleutel (daggegevens, slaap, HRV, SpO2, Body Battery) | ✅ een keer live geprobeerd tegen een echt account | [GARMIN.md](GARMIN.md) |
| Activiteiten via de sleutel, Ping/Pull-callbacks, snapshots, epoch summaries | 🧪 veldnamen en callbacklayouts niet tegen een echt account | [GARMIN.md](GARMIN.md) |
| Server met zakelijke OAuth (Garmin Developer Program) | 🧪 ❓ nooit, vraagt goedkeuring van Garmin | [GARMIN.md](GARMIN.md) |
| Horloge over USB (fenix 8) en een map met horlogebestanden | 🧪 niet op een echt horloge | [WATCH.md](WATCH.md) |
| Bluetooth-sync, horlogebatterij en live hartslag uit de eigen Garmin-dienst | ✅ fenix 8 en Stefans telefoon, 2026-11-28, v1.13.12 ("werkt allemaal heel goed"; geen logboek per stap) | [BLUETOOTH.md](BLUETOOTH.md) |
| Meldingen van de telefoon op het horloge | ✅ fenix 8, 2026-11-28, v1.13.12 (zelfde bevestiging) | [NOTIFICATIONS.md](NOTIFICATIONS.md) |
| Voorgrondservice met melding bij live hartslag | ✅ in gebruik op Stefans telefoon sinds v1.13.8; de proef met het scherm tien minuten uit (T8) en de batterijproef (T16) zijn niet apart gemeten | [WATCH.md](WATCH.md) |
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
| v1.11.0 | Vier talen: Engels, Nederlands, Duits en Frans, met een taalkeuze los van de rapporttaal, lokale datums/getallen, Duitse/Franse widget- en meldingsteksten, een opgeruimde Oura-tab en een voorblad dat de dag volgt; de nieuwe talen zijn machine-draft zonder native review |
| v1.12.0 | Wat je telefoon verlaat: twaalf bronpagina’s uit één PrivacyRegister, met bestemming, trigger, velden, opslag en “nooit”-regels; de oude privacytekst is gecorrigeerd en Delete all data wist nu ook lichaam, water, dagboek, schoenen, plan, race, doelen, work-outs, log en ledger |
| v1.13.0 | Twee Android-kanalen uit één codebase: GitHub met updater, installatietoestemming en FileProvider; store zonder die drie en met een verborgen updater; targetSdk 36; AAB en APK gebouwd en gecontroleerd maar niet gepubliceerd; Play/F-Droid geblokkeerd door naam, account/testers, echte schermafbeeldingen en verklaringen |
| v1.13.1 | Weergavenaam **Cercis** in plaats van OpenConnect, om verwarring met de OpenConnect-VPN-client te voorkomen; pakketnaam `nl.tufein.openconnect`, repository, APK-namen en updater blijven, dus v1.13.0 werkt gewoon bij; juridische controle en winkelpublicatie staan nog open |
| v1.13.2 | Strengere release-controles: de emulator-rooktest houdt een release weer tegen en de APK heeft een budget van 13 MiB |
| v1.13.3 | Compose 1.13 (BOM 2026.11.01, compileSdk 37.1) en schermtests op de v2-regel, zodat ze niet meer wisselend vastlopen |
| v1.13.4 | Vier ringen op Gezondheid in plaats van losse balken, met dezelfde vorm in elke stijl en tests voor 320 dp, TalkBack en ontbrekende scores; de horlogebatterij leest meteen na het batterijantwoord in plaats van te wachten op de syncstatus |
| v1.13.5 | Horlogebatterij: zelfgestuurde ladingberichten worden bewaard, bij de start van een sync wordt opnieuw gevraagd, de verbinding blijft na `SYNC_COMPLETE` vijf seconden open voor een laat antwoord en een open meldingenverbinding ververst elke vijftien minuten; gericht op de Fenix 8 51mm, nog niet op echte hardware bevestigd |
| v1.13.6 | Live hartslag via de eigen Garmin-Bluetoothverbinding: Multi-Link-service `REAL_TIME_HR` (6), abonneren met `0x01`, bpm en rusthartslag lezen zonder `Broadcast Heart Rate`; de Watch-kaart biedt het horloge als bron, met de bestaande meldingenverbinding als die open is; geen RR/HRV; nog niet op echte hardware bevestigd |
| v1.13.7 | Live hartslag start nu echt: de real-time-hartslaggienst wordt pas na de configuratie van het horloge aangevraagd, niet meer tijdens de eerste handshake; een nepwatch weigert de vroege aanvraag als regressietest; nog niet op echte hardware bevestigd |
| v1.13.8 | Live hartslag op de voorpagina: een eigen kaart start, scant en kiest dezelfde bron als Watch, toont bpm met min/avg/max en duur, stopt en bewaart de opname; standaard aan en uit te zetten onder Weergave → Voorpagina; stopt bij het verlaten van Home; nog niet op echte hardware bevestigd |
| v1.13.9 | Live hartslag start automatisch bij het openen van de app: de laatste sensor wordt onthouden, anders wordt er gezocht en de eerste sensor gekozen; de opname blijft lopen bij een tabwissel, met een schakelaar op Watch om dit uit te zetten; nog niet op echte hardware bevestigd |
| v1.13.10 | De Oura-tab synchroniseert vanzelf bij openen wanneer de laatste sync ouder is dan een minuut; de knop Sync now blijft voor een directe sync; alleen tegen een fake Oura getest |
| v1.13.11 | Een eigen hartslagkaart op de Oura-tab met de gemiddelde en laagste hartslag van de laatste nacht; Oura levert geen live hartslag via de API, en dat staat erbij; alleen tegen een fake Oura getest |
| v1.13.12 | Terugblik (M15): dit jaar tegenover vorig jaar tot dezelfde dag, een maandenraster over alle jaren, elk jaar op zich en de gebroken records; APK-budget van 13 naar 14 MiB; op de JVM getest, niet tegen een echte export |
| v1.13.13 | Fenix 8 op echte hardware bevestigd (Bluetooth-sync, batterij, meldingen, live hartslag; labels in de app); zeven fouten uit fuzz-tests gerepareerd in de FIT-, GPX-, TCX- en Garmin-lezers van app en server; appcompat 1.8.0 |
| v1.14.0 | Schoon fundament: de dex-bestanden gecomprimeerd in de APK (13,3 naar 6,4 MB, budget van 14 naar 8 MiB), één scroll-helper voor de schermtests, een pre-push-controle (ruff, CHANGELOG, versie op vier plekken), AppViewModel opgesplitst zonder gedragswijziging (toestanden en doorgeefluiken naar eigen bestanden) en een nieuwe roadmap met twaalf sporen; de oude staat in `ROADMAP-HISTORIE.md` |
| v1.15.0 | Retro, deel 1: LCD-palet in vier tinten groen (eigen tinten), scanlijnen over de stijl Retro (wisselen bouwt het scherm niet opnieuw op) en 8-bit-deuntjes na een sync met iets nieuws, alleen terwijl de app op het scherm staat; alles standaard uit en zonder beeld- of geluidsbestanden |
| v1.16.0 | Retro, deel 2: level-up-meldingen bij een nieuwe trofee (met de naam) of een nieuw level, met het record- of doeldeuntje als de 8-bit-geluiden aan staan; de eerste berekening na het openen en een sprong van meer dan drie trofeeën of twee levels (een import, de demo) blijven stil; standaard uit |
| v1.17.0 | Retro, deel 3: eregalerij op de tab Trofeeën (per afstand de vijf snelste runs, elke run één keer, met eigen initialen via een naaminvoer met pijltjes, standaard AAA); de back-up bewaart nu ook de zelfgemaakte workouts (zaten sinds v1.6.0 wel in de gegevens maar nooit in het bestand) en de initialen |

## 9. Hoe releases werken

Elke versie is een pull request, daarna een GitHub-release met de ondertekende APK (zelfde sleutel, dus de nieuwe versie
installeert over de oude en houdt je gegevens). Er gaat niets uit zonder groene CI. Elke release legt in het Nederlands
uit wat er veranderd is, wat er in de code veranderd is en wat **niet** is gecontroleerd.

### Zo werk je verder (stap voor stap)

1. **Bouwen en testen.** Python: `pip install -e '.[dev]'` en `pytest`. Android: in `android/`,
   `./gradlew :core:test :app:testGithubDebugUnitTest` (zie [ANDROID.md](ANDROID.md); draai nooit twee Gradle-builds
   tegelijk, dan raken de tussenbestanden van `core` beschadigd).
2. **De pre-push-controle aanzetten:** `git config core.hooksPath .githooks`. Daarna draait `tools/prepush.sh` bij elke
   push: ruff, de CHANGELOG en de versie op vier plekken.
3. **Een nieuwe versie** staat op vier plekken die gelijk moeten zijn: `pyproject.toml`, `src/openconnect/__init__.py`,
   `tests/integration/test_api.py` en `android/app/build.gradle.kts` (`versionName`, en `versionCode` één hoger). De
   statusregel in `README.md` noemt hem ook.
4. **De release note** schrijf je in `docs/releases/vX.Y.Z.md` (Nederlands; vaste kopjes *Wat je merkt*, *Wat er in de
   code veranderd is*, *Getest en niet getest*, *Installeren*). Daarna `python3 tools/changelog.py` voor `CHANGELOG.md`,
   en een regel in paragraaf 8 hieronder.
5. **Pull request**, wachten tot alle checks groen zijn (Python 3.11 tot en met 3.13, de Android-build met alle tests,
   de store-variant, vier emulator-rooktests, het APK-budget), dan samenvoegen.
6. **Publiceren:** start de workflow *Release* (`release.yml`) op `main` met als invoer de tag, bijvoorbeeld `v1.15.0`.
   De tag moet gelijk zijn aan de versie in `pyproject.toml` en in `build.gradle.kts`. De workflow bouwt, test, draait de
   rooktest, ondertekent, controleert de handtekening en zet `openconnect-vX.Y.Z.apk` met zijn `.sha256` in de release.
   Een tag pushen werkt ook.
7. **Controleren:** download de APK, vergelijk de SHA-256 en kijk met `apksigner verify --print-certs` of de
   ondertekenaar dezelfde is als bij de vorige release. De updater in de app weigert een APK met een andere
   ondertekenaar of zonder checksum.

## 10. Bewust niet

- Geen abonnement, advertenties of cloudopslag van gezondheidsgegevens.
- Geen medische uitspraken of diagnose; de app legt uit wat je cijfers zijn, niet wat je moet doen alsof hij je arts is.
- Geen tweede app naast deze, geen horlogecompanion en geen Wear OS-versie (een eigen Connect IQ-dataveld is een
  open vraag voor 3.0, zie paragraaf 2).
- Geen vermogensanalyse, segmenten of sociaal netwerk, geen upload naar Intervals.icu of Strava (hun voorwaarden),
  geen labuitslagen en geen biologische leeftijd.
- Geen AI-model op de telefoon zelf: te groot voor het APK-budget en te zwaar voor de batterij.
- Geen nieuwe machinevertaalde talen zolang Duits en Frans niet native zijn nagekeken.
- **De ondertekeningssleutel niet vervangen.** Hij moet naar een veiliger plek (H1), maar een nieuwe sleutel zou elke
  geïnstalleerde Cercis van updates afsnijden.
- Geen namaak van bestaande spellen in de retro-look: eigen tekeningen, kleuren en geluiden.
