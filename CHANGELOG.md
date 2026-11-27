# Changelog

Generated from [docs/releases](docs/releases) by `python tools/changelog.py`; do not edit by hand. Each entry is the title and the first paragraph of that release's notes (the notes are in Dutch from v0.43.0).

## v1.13.11: hartslag op de Oura-tab

De Oura-tab toont nu een eigen hartslagkaart met het gemiddelde en de laagste hartslag van de laatste nacht. Oura meet tijdens de slaap; de API geeft geen live hartslag, en dat zegt de kaart er ook bij.

[Release notes](docs/releases/v1.13.11.md)

## v1.13.10: Oura ververst bij openen

De Oura-tab synchroniseert nu vanzelf wanneer je hem opent en de laatste sync te oud is. Je hoeft niet meer eerst op **Sync now** te tikken; de knop blijft er voor een directe sync.

[Release notes](docs/releases/v1.13.10.md)

## v1.13.9: live hartslag start vanzelf

De live hartslag start nu automatisch wanneer je Cercis opent. De app onthoudt de laatste sensor; is er geen, dan zoekt hij een horloge of band. De opname blijft lopen als je van tab wisselt. Zo hoef je niets meer aan te tikken; stoppen kan via de kaart of de melding.

[Release notes](docs/releases/v1.13.9.md)

## v1.13.8: live hartslag op de voorpagina

De live hartslag heeft nu een eigen kaart op de voorpagina. Je start, kiest en stopt dezelfde opname als op Watch, en ziet de bpm, bron, min/avg/max en duur zonder eerst naar Watch te gaan. De kaart staat standaard aan en is in te stellen onder Instellingen → Weergave → Voorpagina.

[Release notes](docs/releases/v1.13.8.md)

## v1.13.7: live hartslag start nu echt

De live-hartslagbron uit v1.13.6 werd op een echte Fenix 8 niet zichtbaar. De app vroeg Garmins real-time hartslagdienst (Multi-Link-service 6) al tijdens de eerste handshake aan, voordat het horloge zijn configuratie had gestuurd. Een Fenix 8 negeert die vroege aanvraag, waardoor de Watch-kaart wel de watch kon tonen maar nooit een hartslag ontving.

[Release notes](docs/releases/v1.13.7.md)

## v1.13.6: live hartslag via je Garmin-horloge

De Watch-kaart kan je hartslag nu ook rechtstreeks uit een Garmin-horloge lezen, over de eigen Bluetoothverbinding. Je hoeft daarvoor **Broadcast Heart Rate** op het horloge niet meer aan te zetten. Gericht op de **Garmin Fenix 8 51mm**, maar de route werkt voor Garmins die dezelfde real-time service hebben.

[Release notes](docs/releases/v1.13.6.md)

## v1.13.5: de horlogebatterij blijft niet meer hangen

De batterijkaart van het horloge bleef op een oude waarde staan wanneer de watch een nieuwe lading zelf doorgaf, of wanneer een sync via een al openstaande Bluetooth-verbinding liep. Deze release neemt die twee routes mee. Gericht op de **Garmin Fenix 8 51mm**, maar de fix geldt voor elke Garmin die zijn lading via hetzelfde protocol meldt.

[Release notes](docs/releases/v1.13.5.md)

## v1.13.4: rustige ringen en een batterij die meteen bijwerkt

De vier scores op **Gezondheid** hebben nu dezelfde rustige ring in plaats van een horizontale balk. Daarnaast blijft de lading van je horloge niet meer een sync achter: zodra het horloge antwoordt, leest de batterijkaart het nieuwe cijfer.

[Release notes](docs/releases/v1.13.4.md)

## v1.13.3: Compose 1.13 en schermtests die niet meer vastlopen

Deze release zet de bibliotheek waarmee de app zijn schermen tekent (Jetpack Compose) van 1.11 naar 1.13, de update die Dependabot in #92 voorstelde. Aan wat je ziet en doet, is niets bewust veranderd. Daarnaast is gevonden waarom de schermtests met Compose 1.13 bij elke run op andere plekken vastliepen: het lag aan de testopstelling, niet aan de app. De tests gebruiken nu de nieuwe …

[Release notes](docs/releases/v1.13.3.md)

## v1.13.2: de release-controles weer streng

Deze release verandert **niets aan wat de app doet**; de APK is dezelfde app als v1.13.1 met een nieuw versienummer. Wat verandert is het vangnet eromheen: de emulator-rooktest houdt een release weer tegen als hij faalt, de APK heeft een budget met ruimte (13 MiB, in plaats van 20 KB onder de oude grens) dat bij elke wijziging wordt gecontroleerd, en de release notes van vier eerdere versies …

[Release notes](docs/releases/v1.13.2.md)

## v1.13.1: voortaan Cercis

Vanaf deze release heet de app **Cercis**, niet meer OpenConnect. De oude naam botste met de bekende OpenConnect-VPN-client, waardoor zoeken in een winkel de verkeerde app kan opleveren. Alleen de weergavenaam verandert. De pakketnaam `nl.tufein.openconnect`, de repository, de APK-namen en de updater blijven; v1.13.0 werkt gewoon bij.

[Release notes](docs/releases/v1.13.1.md)

## v1.13.0: twee kanalen, één app

Deze GitHub-release splitst de Android-app in twee kanalen uit één codebase. De **GitHub-versie** (`github`) houdt de in-app-updater, de installatietoestemming en de FileProvider; de **winkelversie** (`store`) laat die drie weg en verbergt de updater. De winkelversie is gebouwd en getest, maar **niet gepubliceerd**: Google Play en F-Droid blijven geblokkeerd. Verder gaat targetSdk naar 36.

[Release notes](docs/releases/v1.13.0.md)

## v1.12.0: wat je telefoon verlaat

Deze release maakt privacy concreet: **Instellingen → Over en licenties → Wat je telefoon verlaat** opent een index met twaalf onderwerpen. Elk onderwerp heeft een eigen pagina met wat er wel weggaat, wat er terugkomt, wat nooit meegaat, waar het bewaard blijft, wanneer het gebeurt en welke toestemming je ervoor geeft. De pagina is opgebouwd uit dezelfde `PrivacyRegister` als de statische …

[Release notes](docs/releases/v1.12.0.md)

## v1.11.0: Duits, Frans en een voorblad dat met de dag meegaat

De app heeft nu vier talen. Kies in **Instellingen → Taal** voor *Taal van telefoon*, Engels, Nederlands, Duits of Frans; de keuze geldt voor de hele app, de widget en de meldingen. De rapporten en de coach blijven voorlopig Engels of Nederlands, en de app zegt dat er eerlijk bij. Op een Duitse of Franse telefoon opent de app na deze update meteen in die taal.

[Release notes](docs/releases/v1.11.0.md)

## v1.10.0: een schema dat meedenkt

Je schema houdt nu rekening met een gemiste week: de app toont één rustig voorstel om het sessiepatroon naar een latere trainingsweek te verplaatsen. Jij kiest **Toepassen** of **Niet nu**; beide keuzes blijven bewaard, en de coach mag dat voorstel bespreken maar verandert nooit iets zelf.

[Release notes](docs/releases/v1.10.0.md)

## v1.9.3: het jaar in twaalf maanden

Het Insights-tabblad heeft er een **Jaaroverzicht**-tab bij: twaalf maanden stappen, slaap, rusthartslag en trainingen naast elkaar, met dezelfde eerlijke "geen data"-regels. Onder de motorkap staat de app nu op Gradle 9.6, AGP 9.5 en compileSdk 37, met Kotlin 2.4 als ingebouwde taal.

[Release notes](docs/releases/v1.9.3.md)

## v1.9.2: rustige lijnen, zes tinten en een voorblad van jou

De onderbalk tekent geen pixels meer maar rustige lijnen, de thema's zijn teruggebracht tot zes tinten rond het Olijfgroen, en het voorblad stel je zelf samen: welke meetkaart onder de batterij staat en welke kaarten je ziet. Ook de launcher-iconen zijn opnieuw getekend.

[Release notes](docs/releases/v1.9.2.md)

## v1.9.1: je eigen kleuren, ringen en licht

De editorial-look is nu aan te passen: heldere signaalkleuren in plaats van de zachte tinten, dikkere ringen als je die wilt, en een voorbladfoto die met het uur meegaat of op één licht blijft staan. De keuze geldt meteen overal in de app.

[Release notes](docs/releases/v1.9.1.md)

## v1.9.0: het voorblad als een rustige pagina

De app opent nu in de **editorial**-stijl: een rustige pagina met een foto bovenaan, grote ronde kaarten, Instrument Serif als titelletters en de nieuwe **Olive**-kleuren. De sportieve Sleek-stijl, Retro en Classic blijven, en een look die je al koos blijft staan.

[Release notes](docs/releases/v1.9.0.md)

## v1.8.3: de banden eruit, het horloge bij

De WHOOP-band en de Hume Band zijn uit de app, en het horloge krijgt kort na het verbinden een tweede batterijvraag zodat de kaart niet een procent achterloopt. Wat je eerder van die banden opsloeg, blijft leesbaar.

[Release notes](docs/releases/v1.8.3.md)

## v1.8.2: elke sectie inklapbaar, en de ring zegt waarom

De ringbatterij bleef soms leeg zonder uitleg, en de app was op elk tabblad een lange lijst geworden. Nu zegt de batterijkaart het als Oura de lading niet deelt, met een nieuwe login, en elke sectie klapt in onder zijn titel. De drie knoppen onderaan het voorblad zijn weg.

[Release notes](docs/releases/v1.8.2.md)

## v1.8.1: de lading van je horloge en je ring op het voorblad

De batterijkaart van het horloge en de Oura Ring stond alleen op het Watch-tabblad. Hij staat nu ook bovenaan het voorblad, zodat je ziet of een van je devices bijna leeg is voordat je gaat trainen.

[Release notes](docs/releases/v1.8.1.md)

## v1.8.0: de lading van je horloge en je ring, en work-outs op afstand

Twee dingen die in de dagelijkse ronde ontbraken: je kon niet zien hoe vol je horloge of je ring was, en de work-outbouwer telde alleen minuten. Allebei is dat nu anders.

[Release notes](docs/releases/v1.8.0.md)

## v1.7.0: een widget in drie maten

De widget op je startscherm groeit nu mee met de ruimte die je hem geeft: klein toont readiness en stappen, de gewone kaart voegt je weekdoel toe en een grote kaart ook de slaap van vannacht en je rusthartslag. Je sleept hem groter of kleiner en hij tekent zichzelf opnieuw.

[Release notes](docs/releases/v1.7.0.md)

## v1.6.0: je eigen work-out bouwen

Op het Plan-tab staat nu een kaart **Je eigen work-outs**: geef een training een naam, kies hoeveel herhalingen, hoe lang snel en rust, en hoeveel warming-up en cooldown — de app maakt er een FIT-work-out van die met dezelfde knop naar je horloge gaat.

[Release notes](docs/releases/v1.6.0.md)

## v1.5.0: zelf een training toevoegen

Wat geen horloge, ring of band heeft vastgelegd, kun je nu zelf invoeren: op Activities opent *Toevoegen* een kort formulier met sport, datum, minuten, kilometers en gemiddelde hartslag. Het resultaat is een gewone activiteit in je eigen lijst, met bron `manual`, en het gaat mee in de back-up.

[Release notes](docs/releases/v1.5.0.md)

## v1.4.0: namen uit je export

Een geïmporteerde activiteit heet niet meer "Running 2026-05-02": de naam die je in Strava of Garmin Connect gaf, komt mee uit het archief. En van de bestanden die deze versie nog niet kan lezen schrijft het rapport op hoe ze eruitzien (alleen sleutelnamen), zodat een echte Garmin-export kan laten zien wat er in de dagbestanden staat. Tweede stap van het thema **je geschiedenis is van jou**.

[Release notes](docs/releases/v1.4.0.md)

## v1.3.0: de verhuisdoos

Eén importknop die een heel archief aankan: de export van Garmin Connect, het archief van Strava of OpenConnect's eigen *Alles exporteren* gaat er in één keer in, zonder uitpakken. Dit is het begin van een nieuw thema, **je geschiedenis is van jou** (roadmap, paragraaf 4b): eerst binnenhalen (v1.3.0 en v1.4.0), daarna terugkijken en vrij gebruiken.

[Release notes](docs/releases/v1.3.0.md)

## v1.2.0: de app haalt het zelf op

Health Connect is nu ook een bron die de app zelf bijhoudt: één schakelaar laat ontbrekende dagen, nachten en gewichten op de achtergrond aanvullen, en gewicht landt in de Body-kaart. De lees-knop haalt dat gewicht er meteen bij, met een aparte toestemming die je mag weigeren.

[Release notes](docs/releases/v1.2.0.md)

## v1.1.0: het 1.1-programma landt

De eerste slices van het 1.1-programma zijn klaar en in één versie gebundeld: HRV-status, slaapfases en gewicht met vetpercentage op Health. Nieuw is **water loggen**: Garmin's API geeft geen water, dus de app houdt zelf een dagtotaal bij.

[Release notes](docs/releases/v1.1.0.md)

## v1.0.7: slaapfases en gewicht op Health

Op Health staan nu twee dingen die er nog niet waren. De **nachtkaart** toont diep, licht, REM en wakker in uren en als balk — die gegevens kwamen al binnen van horloge, ring of band, maar werden niet getoond. De **Body-kaart** laat je je gewicht en optioneel vetpercentage invullen; dat wordt bewaard, geback-upt en als lijn van de laatste 30 dagen getoond.

[Release notes](docs/releases/v1.0.7.md)

## v1.0.6: je HRV tegen je eigen normaal

Op Health staat nu een **HRV-status**: je laatste zeven nachten tegen je eigen vierweekse basis, met *onder je normaal*, *in balans* of *boven je normaal* en het verschil in procenten. Tik op de kaart en de HRV-grafiek opent. In de roadmap staat vanaf nu het volledige **1.1-programma**: trainingsanalyse, work-outs en routes, lichaam en voeding, sync en invoer, en UX en bereik.

[Release notes](docs/releases/v1.0.6.md)

## v1.0.5: de Health Connect-poort dicht

Geen nieuwe functies. Op een telefoon **zonder de Health Connect-dienst** crashte de opstart-coroutine van de Health Connect- en Hume-bronnen; die vragen nu netjes "niet beschikbaar" en de app gaat door. Een poging om de bouw naar AGP 9 en Compose 2026.11 te verhogen is **voor de release teruggedraaid** en in de roadmap vastgelegd.

[Release notes](docs/releases/v1.0.5.md)

## v1.0.4: één grafiek tegelijk, in tabs

Op Health stonden tien grafieken onder elkaar; dat was één lange muur. De scores blijven bovenaan staan, daaronder kies je met tabs welke grafiek je ziet. Tik je op een score, dan opent de bijbehorende grafiek. De ingeklapte secties van Home en Insights blijven zoals ze zijn.

[Release notes](docs/releases/v1.0.4.md)

## v1.0.3: alleen scores open, grafieken op aanvraag

De cijfers blijven open, de grafieken niet. De vier signaaltegels op Home tonen alleen hun score; de lijnen van zeven dagen zitten in een ingeklapte sectie. De maandgrafiek en de recente activiteiten deden dat al, Insights houdt zes dagelijkse tabs en zet de rest achter *Meer*. Widget en startscherm volgen nu het gekozen thema.

[Release notes](docs/releases/v1.0.3.md)

## v1.0.2: sportiever, rustiger en minder vol

Een sportieve pas over het hele uiterlijk: koel **Graphite** met één blauw accent, strakke panelen met een haarlijn, compactere letters en rechte knoppen in plaats van pillen. Op Home blijven de cijfers van vandaag staan; de maandgrafiek en de activiteitenlijst klappen weg. Alles blijft kiesbaar, Retro inclusief.

[Release notes](docs/releases/v1.0.2.md)

## v1.0.1: elk nieuw bericht trilt weer, en het thema beweegt mee

De eerste update na 1.0. Op het horloge trilt nu **elk nieuw bericht**, ook als het in een melding staat die er al was, en meldingen die tijdens het opnieuw verbinden binnenkomen gaan niet meer verloren. De app start op Android 12 en nieuwer met de **kleuren van je achtergrond**, met een **live voorbeeld** en een keuze bij de eerste start.

[Release notes](docs/releases/v1.0.1.md)

## v1.0.1-rc.1: elk nieuw bericht laat het horloge trillen, en het horloge krijgt antwoord op zijn agendavraag

De **eerste release candidate na 1.0**, een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Stefan stuurde op 20 november een logboek van v1.0.0 op zijn fenix 8: **de meeste meldingen komen aan, maar niet allemaal.** Deze versie repareert wat de code daarover liet zien. **De oorzaak is niet bewezen op een horloge:** er is geen horloge in deze omgeving, en de meldingen …

[Release notes](docs/releases/v1.0.1-rc.1.md)

## v1.0.0: een moderne app, en de eerste volledige versie

Dit is **1.0**: na zeven release candidates komt er een stabiele release bij. Nieuw is het uiterlijk: een moderne, rustige interface met afgeronde kaarten, Plex Sans, pill-vormige bediening en een zwevende tabbalk. De oude Handheld-look blijft beschikbaar als Retro.

[Release notes](docs/releases/v1.0.0.md)

## v1.0.0-rc.7: de hele code nagelopen, en wat daarbij misging gerepareerd

De **zevende release candidate** van 1.0, weer een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Op Stefans verzoek ("verbeter de code tot op de puntjes") is **alle code van de Python-server en van de Android-app doorgelopen** op fouten, foutafhandeling, privacy, toegankelijkheid en verouderde documentatie. Er komt **geen nieuwe functie** bij; wat er stond is …

[Release notes](docs/releases/v1.0.0-rc.7.md)

## v1.0.0-rc.6: het horloge niet langer laten wachten op protobuf-vragen, en een vastgelopen horloge opnieuw verbinden

De **zesde release candidate** van 1.0, weer een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Met rc.5 kwam op Stefans fenix 8 **de eerste melding aan**, maar de meldingen daarna niet. Het logboek van rc.5 en dat van rc.4 laten hetzelfde patroon zien, en deze versie pakt dat op twee manieren aan. **De oorzaak is nog niet bewezen:** er is geen horloge in deze …

[Release notes](docs/releases/v1.0.0-rc.6.md)

## v1.0.0-rc.5: een dode Bluetooth-verbinding herkennen, en het logboek laat zien wat het horloge antwoordt

De **vijfde release candidate** van 1.0, weer een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Het logboek van rc.4 op een fenix 8 liet zien dat de kennismaking klopt en het horloge om meldingen vraagt, maar dat het daarna **geen enkel antwoord meer gaf** op de melding die we aankondigden. **Dit is geen bevestigde oplossing:** er is geen horloge in deze omgeving …

[Release notes](docs/releases/v1.0.0-rc.5.md)

## v1.0.0-rc.4: laat zien waarom een melding niet aankomt, en stuurt ze ook zonder dat het horloge erom vraagt

De **vierde release candidate** van 1.0, weer een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Na rc.3 kwamen de meldingen van de telefoon niet op het horloge aan. **Dit is geen bevestigde oplossing:** er is geen horloge in deze omgeving, dus de oorzaak is niet gezien. Wel is de code naast Gadgetbridge gelegd, zijn er vijf zwakke plekken gevonden en aangepakt (één …

[Release notes](docs/releases/v1.0.0-rc.4.md)

## v1.0.0-rc.3: meldingen van je telefoon op het Garmin-horloge

De **derde release candidate** van 1.0, weer een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Op verzoek van de eigenaar kan de app nu **de meldingen van je telefoon op het Garmin-horloge tonen**, zoals Gadgetbridge en de Garmin Connect-app dat doen: een bericht van een chat-app, een mail, een agenda-herinnering, voor de apps die jij kiest. Dit is de tweede …

[Release notes](docs/releases/v1.0.0-rc.3.md)

## v1.0.0-rc.2: alle Garmin-horloges herkend, niet alleen de fenix

De **tweede release candidate** van 1.0, weer een **GitHub-pre-release** (niet "nieuwste", de updater biedt haar niemand aan). Op verzoek van de eigenaar herkent de app nu **elke Garmin-familie**: fenix, Forerunner, Venu, vívoactive, vívosmart, Instinct, Enduro, epix, Edge, tactix, Descent, Lily en de rest, in plaats van een app die om één horloge, de fenix 8, heen was geschreven. Dit is de enige …

[Release notes](docs/releases/v1.0.0-rc.2.md)

## v1.0.0-rc.1: de kandidaat voor 1.0

De **eerste release candidate** van 1.0, uitgebracht als **GitHub-pre-release**: ze staat niet als "nieuwste" en de updater in de app biedt haar niemand aan. De code is gelijk aan v0.58.0; wat verandert is het versienummer, een paar kleine reparaties aan het gereedschap eromheen en de documentatie. Vanaf nu is de code **bevroren**: alleen reparaties en teksten tot 1.0.

[Release notes](docs/releases/v1.0.0-rc.1.md)

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
