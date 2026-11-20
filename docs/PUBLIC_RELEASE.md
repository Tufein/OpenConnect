# De weg naar 1.0: afvinklijst, proefronde en terugdraaiplan

Het plan staat in [ROADMAP.md](ROADMAP.md). Dit bestand is de lijst die je afvinkt. **1.0 gaat uit als elk vakje van de zeven poorten
(paragraaf 1) is aangevinkt.** Een vakje gaat pas aan met bewijs: een link naar de pull request, een testnaam, of een regel in
de proefronde (paragraaf 2) met datum en versie.

Stand op 2026-11-18, na v0.53.0: nog niets aangevinkt dat van een echt apparaat afhangt. Aangevinkt is wat het werk van Claude alleen was: minimum Android 9 (v0.52.0), de README, `SECURITY.md`, `docs/VERSIONING.md` en het changelog (v0.53.0).

## 1. Go/no-go per poort

### G1 Echt getest
- [ ] Installeren van de APK en controle van de SHA-256 (T1)
- [ ] Eerste start, demodata, wissen (T2)
- [ ] Bestanden importeren, hetzelfde bestand twee keer geeft geen dubbele activiteit (T3)
- [ ] Bijwerken over een oudere versie heen, gegevens blijven staan, en de updater onder Instellingen (T4)
- [ ] Garmin-account met een eigen sleutel (T5)
- [ ] Een Garmin-horloge over USB, bij Stefan de fenix 8 (T6)
- [ ] Een Garmin-horloge over Bluetooth, bij Stefan de fenix 8 (T7)
- [ ] Live hartslag met het scherm uit (T8)
- [ ] Health Connect lezen en schrijven (T9)
- [ ] Back-up, alles wissen, terugzetten (T13)
- [ ] App-vergrendeling, thema's en iconen op een echte telefoon (T14)
- [ ] Oura, Claude, Gemini, WHOOP en Hume: elke bron is **getest** (T10 tot en met T12) of draagt het label *Experimenteel* in de app en in de docs
- [ ] Het register in ROADMAP.md, paragraaf 7, is bijgewerkt: geen 🧪 meer zonder label in de app

### G2 Gegevens veilig
- [x] Migratietest vanaf elke uitgebrachte schemaversie (v0.56.0: `SchemaMigrationTest`, schema 1 tot en met 5, plus een bewaker voor de volgende)
- [x] Terugzettest per bron (v0.56.0: `BackupOnRealDatabaseTest` en `UserDataBackupTest`)
- [x] Volledige export (ZIP) en een test die hem weer inleest (v0.56.0: `ExportAllTest`)
- [x] De updater weigert een afwijkende SHA-256, een release zonder checksum en een andere ondertekenaar met een duidelijke melding (v0.56.0, `UpdateControllerTest`; de controle van de handtekening van een echte APK is nog niet op een toestel geprobeerd)

### G3 Sleutel en beveiliging
- [ ] De sleutel staat in GitHub-secrets (`OPENCONNECT_KEYSTORE_BASE64` en `OPENCONNECT_KEYSTORE_PASSWORD`), één release heeft ze gebruikt
- [ ] Een offline kopie van het sleutelbestand en het wachtwoord ligt bij Stefan (niet in de repository, niet in een issue)
- [ ] Het privé-conceptrelease "Signing key" is verwijderd, ná de vorige twee
- [x] `SECURITY.md` klopt met de code (herschreven in v0.53.0: optionele aanmelding, Ping/Pull-allowlist, Android-app, ondertekening)
- [ ] Privé melden van kwetsbaarheden staat aan (Stefan: Settings, Security, *Private vulnerability reporting*)
- [ ] Geen openstaande beveiligingsmeldingen van afhankelijkheden
- [x] Minimum Android op 9 gezet (Stefan, 2026-11-18, v0.52.0): sleutelwissel met een bewijsketen werkt daardoor op elke ondersteunde telefoon (zie paragraaf 4)

### G4 Kwaliteit
- [x] De 13 Dependabot-voorstellen zijn samengevoegd of met reden gesloten (v0.58.0; de gereedschapssprong naar AGP 9 en Kotlin 2.4 is een aparte klus na 1.0, zie ROADMAP M1)
- [ ] Drie CI-runs op rij groen zonder opnieuw starten, op `main`
- [x] Prestatiebudget gehaald (vijf jaar aan gegevens, openingstijd per tabblad; v0.58.0, `docs/PERFORMANCE.md`; op een telefoon nog niet gemeten)
- [x] Toegankelijkheidstests en taalpariteit staan in CI (v0.57.0: `AccessibilityAuditTest`, `LanguageParityTest`; een echte TalkBack-ronde op een telefoon blijft nodig, T14)
- [ ] Geen bekende crash of dataverlies in de issues

### G5 Begrijpelijk
- [x] README opnieuw (kort, actueel, wat is getest), met `CHANGELOG.md` en `docs/VERSIONING.md` (v0.53.0)
- [x] Installatiegids (APK, "apps installeren toestaan", controle van de SHA-256, bijwerken, ook met Obtainium): `docs/INSTALL.md`, v0.58.0
- [x] Privacyverklaring in het Nederlands en Engels, ook in de app (v0.58.0: `docs/PRIVACY.md` en Over, *Waar gegevens heen kunnen*; een test controleert elk adres in de code)
- [ ] Schermafbeeldingen van een echte telefoon
- [x] Uitleg bij de eerste start (v0.55.0)

### G6 Juridisch en naam
- [x] Alle licenties van gebruikte onderdelen zijn zichtbaar in de app (Instellingen, Over en licenties, v0.55.0)
- [x] De Garmin-disclaimer staat in de app en in de README (v0.55.0)
- [ ] **Besluit over de naam: nog open** (houden of veranderen; er bestaat al een bekende VPN-client die OpenConnect heet). Voorlopig blijft het OpenConnect

### G7 Verspreiding
- [x] Besluit welke kanalen: **alleen GitHub** (Stefan, 2026-11-18). Winkels komen na 1.0
- [x] De release met APK, SHA-256 en uitleg werkt (v0.58.0: vaste kop op de releasepagina met installeren, controleren en wat is getest)
- [ ] Iemand die het project niet kent kan de installatiegids volgen tot een draaiende app (T1)

## 2. Proefronde op echte apparaten

Voor Stefan. Doe de stappen op je eigen telefoon en horloge, in deze volgorde (de belangrijkste eerst), en schrijf per stap het
resultaat op. Sinds v0.55.0 (mijlpaal M2, zie [ROADMAP.md](ROADMAP.md)) geeft Instellingen een knop **Diagnose delen**: die toont eerst de tekst
en laat sleutels, tokens, namen, gezondheidswaarden en routes weg. Plak die in een nieuwe GitHub-melding met het sjabloon
"Hardwaretest" (*Issues, New issue, Hardware trial*) en lees hem eerst zelf na.

Noteer bij elke stap de **versie van de app** (die staat in Instellingen) en de **datum**.

| ID | Stap | Verwacht | Als het misgaat |
|----|------|----------|-----------------|
| T1 | Download `openconnect-vX.Y.Z.apk` en het `.sha256`-bestand van de releasepagina. Controleer: `sha256sum -c openconnect-vX.Y.Z.apk.sha256`. Installeer en open | "OK" bij de controle, de app start, de eerste start vraagt welke apparaten je gebruikt | Schermafbeelding van de melding van Android |
| T2 | Kies je toestellen, tik **Demodata laden**, loop alle tabbladen door, wis daarna alle gegevens (Instellingen, Jouw gegevens) | Alles heeft inhoud na het laden en is leeg na het wissen, geen crash | Welk tabblad, wat je zag |
| T3 | Deel een FIT-, GPX- of TCX-bestand met OpenConnect (of kies het onder Jouw gegevens). Doe het **twee keer** met hetzelfde bestand | De activiteit staat er één keer, met route en grafieken | Naam van het bestand en wat er misging |
| T4 | Installeer een nieuwere release **over** de oudere heen. Daarna Instellingen, Updates: laat de updater zoeken | Gegevens en sleutels staan er nog, de updater zegt dat je bij bent of biedt de nieuwere aan | Schermafbeelding, versies voor en na |
| T5 | Maak je eigen Garmin-sleutel (zie [GARMIN.md](GARMIN.md)), plak hem onder Je toestellen, synchroniseer. Vergelijk de stappen en de slaap van **één dag** met de Garmin Connect-app | Dezelfde stappen en slaaptijd (kleine verschillen in afronding mogen), activiteiten met hun FIT-bestand | Welk getal verschilt, voor welke dag |
| T6 | Sluit je Garmin-horloge (Stefan: de fenix 8) met de kabel aan, onder Horloge: **Horloge uitlezen met een USB-kabel** | Telling van gelezen bestanden en nieuwe activiteiten, de laatste activiteit klopt met het horloge | De melding (bijvoorbeeld "antwoordde niet als opslagapparaat") |
| T7 | Koppel het horloge over Bluetooth en synchroniseer ([BLUETOOTH.md](BLUETOOTH.md)). Kijk ook of de lijst *Zoek horloge* het horloge onder zijn eigen naam toont en wat het Synclogboek bij "recognised as" zegt ([GARMIN_DEVICES.md](GARMIN_DEVICES.md)) | Activiteiten, stappen, slaap en hartslag komen binnen, het Synclogboek noemt "Horloge (Bluetooth)" | Het Synclogboek en of het horloge om een koppelcode vroeg |
| T8 | Zet op het horloge hartslag uitzenden aan, start onder Horloge **Live hartslag**, zet het scherm uit en loop tien minuten. Open daarna de app en sla op als activiteit | De melding bleef staan en telde door, de opgeslagen activiteit heeft de tien minuten | Of de melding verdween en na hoeveel minuten |
| T9 | Zet Health Connect aan (Instellingen, Health Connect), sta de rechten toe. Schrijf een activiteit weg en lees stappen en slaap terug | De activiteit staat in Health Connect, geen dubbel geteld, de dagen zijn aangevuld | Welke rechten Android toonde en weigerde |
| T10 | **Alleen met een Oura Ring:** log in met je eigen Oura-toepassing, synchroniseer, open het Oura-tabblad en het Dag-tabblad van Inzichten. Vergelijk paraatheid en slaapscore met de Oura-app voor dezelfde dag | Dezelfde scores | De foutmelding en op welke stap |
| T11 | Voeg een eigen Claude- of Gemini-sleutel toe, vraag een weekverslag en één vraag aan de coach | Een antwoord in je taal, geen GPS-track in het verzoek | De foutmelding |
| T12 | **Alleen met WHOOP of een Hume Band:** doe hetzelfde als T10 (zie [WHOOP.md](WHOOP.md), [HUME.md](HUME.md)) | Eigen tabblad met herstel en slaap (WHOOP) of slaap, stappen en hartslag (Hume) | De foutmelding en op welke stap |
| T13 | Maak een back-up (Back-up en herstel), wis alle gegevens, zet de back-up terug | Alle activiteiten, dagen en nachten zijn terug, de sleutels niet (die moet je opnieuw invoeren) | Wat ontbreekt |
| T14 | Zet App-vergrendeling aan, kies een ander app-icoon, wissel twee thema's, zet de lettergrootte van Android op de grootste stand | De vergrendeling vraagt om je vinger of schermcode, het icoon wisselt, geen afgesneden tekst | Schermafbeelding waar het knelt |
| T15 | Kopieer een training van je plan naar het horloge (USB, `GARMIN/NewFiles`) en open hem op het horloge | De training staat in de lijst en start | Of het horloge hem weigert, met de naam van het bestand |
| T16 | Laat de app een dag draaien met je bronnen aan (Garmin, eventueel Oura of WHOOP) en een uur *Live hartslag*. Open daarna Android, Instellingen, Batterij, OpenConnect | Een batterijverbruik dat je niet hoeft op te merken (Android toont het in procenten en per uur) | Het percentage en wat je aan had staan |
| T17 | Zet *Watch, Meldingen op het horloge* aan, geef toegang tot meldingen (op Android 13 of nieuwer eerst *Beperkte instellingen toestaan* in de app-info), kies één chat-app en druk op *Stuur een test naar het horloge*. Stuur jezelf daarna een bericht in die app | De test en het bericht staan op het horloge, met titel en tekst; wegvegen op het horloge veegt het ook op de telefoon weg; een app die je niet koos komt niet door | Wat het horloge toont (of niets), het stappenlijstje en het logboek onder *Wat gebeurt er?* (kopieer het), de regel *Laatste melding niet verstuurd* in de app, en of de Garmin Connect-app daarna nog verbinding kreeg |

Een stap die niet kan (geen ring, geen band) hoef je niet te doen: die bron blijft dan *Experimenteel* in 1.0, en dat staat in de app.

## 3. De kandidaat (release candidate)

**Stand:** `v1.0.0-rc.7` is de huidige kandidaat (2026-11-20, GitHub pre-release); `rc.6` tot en met `rc.1` gingen eraan vooraf. De code is bevroren, op zes uitzonderingen na, alle op Stefans verzoek (`rc.4` laat per stap zien waarom een melding niet aankomt, `rc.5` herkent een stil gevallen Bluetooth-verbinding, `rc.6` laat het horloge niet meer wachten op onbeantwoorde vragen, zie [NOTIFICATIONS.md](NOTIFICATIONS.md), en `rc.7` repareert wat een doorloop van alle code vond, zonder nieuwe functie, zie [de notities](releases/v1.0.0-rc.7.md)): `rc.2` herkent **alle** Garmin-families in plaats van vooral de fenix 8 ([GARMIN_DEVICES.md](GARMIN_DEVICES.md)) en `rc.3` stuurt meldingen van de telefoon naar het horloge ([NOTIFICATIONS.md](NOTIFICATIONS.md)). Allebei raken ze Bluetooth, dus **de wachttijd van 7 dagen begint opnieuw** voor het horloge (T6, T7), en vanaf rc.3 komt er een stap bij: *Meldingen op het horloge, Stuur een test naar het horloge*, met bij rc.4 de knop *Wat gebeurt er?*. Er is niets op een echt horloge geprobeerd, ook niet op de fenix 8. De wachttijd van 7 dagen en de proefstappen T1 tot en met T17 zijn aan Stefan.

1. **Bevriezen:** vanaf de eerste kandidaat alleen reparaties en teksten. Een nieuwe functie wacht op 1.1.
2. **Voorlopige uitgave:** `release.yml` krijgt een voorlopig-optie, zodat `v1.0.0-rc.1` als **GitHub pre-release** uitkomt. De updater vraagt `releases/latest` van GitHub, dat voorlopige uitgaven overslaat, dus een kandidaat wordt niemand opgedrongen; een test bewijst dat. De versiecontrole in de workflow moet `1.0.0-rc.1` (tag), `1.0.0rc1` (Python) en `1.0.0-rc.1` (Android) aan elkaar koppelen.
3. **Wachttijd:** minstens 7 dagen dagelijks gebruik van de kandidaat op Stefans eigen telefoon met het horloge erbij. Elke afwijking wordt een melding met het diagnose-rapport.
4. **Nieuwe kandidaat** bij elke reparatie (`rc.2` tot en met `rc.7`); de wachttijd begint opnieuw voor wat de reparatie raakt.
5. **Go/no-go:** paragraaf 1 helemaal aangevinkt. Eén ontbrekend vakje is een "nee", tenzij Stefan het schriftelijk uitstelt met een reden.
6. **v1.0.0** via dezelfde workflow, daarna de gewone controle: SHA-256 gelijk aan het `.sha256`-bestand, dezelfde handtekening, en installeren over de laatste kandidaat heen met behoud van gegevens.

## 4. Terugdraaiplan

Android laat een app niet terug naar een lager versienummer gaan, dus **terugdraaien betekent vooruit repareren**.

- **Fout in 1.0.0, geen gegevensverlies:** zet de release in GitHub op *pre-release* (dan biedt de updater hem niet meer aan), publiceer `1.0.1` met de reparatie en zet er in de release-notities bij wat er mis was.
- **Fout die gegevens beschadigt of wist:** eerst een release die het schrijven stopt of er een export voor aanbiedt, met in de notities hoe iemand uit een back-up terugkeert; daarna de echte reparatie. Het vorige schema blijft leesbaar (dat is het belang van de migratietests).
- **Sleutel verloren:** zonder de sleutel kan geen update meer over bestaande installaties heen; iedereen zou eerst een export moeten maken en opnieuw installeren. Daarom ligt er een offline kopie (G3).
- **Sleutel gelekt of gecompromitteerd:** Android kent sleutelwissel met een bewijsketen (APK Signature Scheme v3). Die werkt vanaf Android 9, en sinds v0.52.0 is dat het minimum, dus de nieuwe sleutel kan over bestaande installaties heen zonder dat iemand opnieuw hoeft te installeren. Het vraagt wel een ondertekeningsstap met `apksigner rotate` en een test dat de keten klopt; die staat nog niet klaar.
- **Na 1.0, als er winkels zijn:** de GitHub-release blijft het referentiepunt; een winkelvariant volgt die.

## 5. Aankondigen

Alleen met Stefans uitdrukkelijke akkoord, niets gaat vanzelf de wereld in:

- [ ] Releasenotes van 1.0.0 in het Nederlands en Engels, met wat is getest en wat *Experimenteel* is
- [ ] README en `CHANGELOG.md` kloppen met 1.0.0
- [ ] De uitleg bij het installeren en het controleren van de SHA-256 staat bovenaan de release
- [ ] Waar het bericht heen gaat (eigen kanalen, forums, een lijst met open-source Garmin-alternatieven) kiest Stefan
