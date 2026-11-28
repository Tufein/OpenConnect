# Verhuizen naar OpenConnect / Moving to OpenConnect

*Sinds v1.3.0. Roadmap: [M13 en M14](ROADMAP-HISTORIE.md#4b-nieuw-thema-je-geschiedenis-is-van-jou-2026-11-22).*

## Nederlands

Wie overstapt van Garmin Connect of Strava laat jaren aan activiteiten achter. OpenConnect kan sinds v1.3.0 zo'n hele
export in één keer inlezen, zonder dat je iets hoeft uit te pakken.

### Garmin Connect

1. Ga op een computer naar je Garmin-account (garmin.com, *Account*, *Gegevensbeheer*) en kies **Je gegevens exporteren**,
   dan *Gegevensexport aanvragen*. Garmin mailt een link zodra het bestand klaar is; dat duurt uren tot dagen.
2. Download het ZIP-bestand op je telefoon (of kopieer het erheen). **Niet uitpakken.**
3. OpenConnect, *Instellingen*, *Jouw gegevens*, **Bestanden of een archief importeren**, en kies het ZIP-bestand.
4. Laat OpenConnect open tot het klaar is. De voortgang staat in Instellingen onder de knop; je kunt stoppen, wat al binnen is blijft staan.

Wat erin zit: de FIT-bestanden van je horloge staan in een ZIP in de export (`UploadedFiles_*.zip`); OpenConnect opent die zelf.
`summarizedActivities.json` geeft sinds v1.4.0 de namen (gekoppeld op starttijd). De dagbestanden (stappen, slaap, rusthartslag) worden nog **niet**
gelezen, want Garmin beschrijft die nergens; ze worden overgeslagen en geteld, en het rapport schrijft de vorm op (patroon van de bestandsnaam en
sleutelnamen, geen waarden, geen e-mailadres). Met **Rapport kopiëren** zet je dat op het klembord om in een melding op GitHub te plakken.

### Strava

1. Strava, *Instellingen*, *Mijn account*, *Je account downloaden of verwijderen*, *Aan de slag*, **Archief aanvragen**. Strava mailt een link.
2. Download het ZIP-bestand en importeer het zoals hierboven. Strava bewaart je activiteiten in `activities/` als `.fit.gz`, `.gpx` of
   `.tcx.gz`; OpenConnect leest ze alle drie.
3. De namen die je in Strava gaf staan in `activities.csv`. Sinds v1.4.0 krijgt elke activiteit die naam; met v1.3.0 stond er *Running 2026-05-02*. Importeer
   het archief nog eens als je in v1.3.0 al iets hebt binnengehaald: dan krijgt wat nog de bedachte naam draagt zijn echte naam (een naam die jij gaf blijft).

### Eigen export

*Alles exporteren* van OpenConnect zelf (Instellingen, Exporteren) is ook een ZIP en kan dus terug. De activiteiten komen binnen; het
dagboek, de schoenen, het plan en de doelen komen met **Terugzetten uit back-up** (het rapport zegt het als er een back-up in het archief zit).

### Wat OpenConnect doet en niet doet

- **Niets wordt overschreven of verdubbeld.** Een activiteit telt als *al aanwezig* als de id bestaat of als er al een activiteit
  is die binnen 90 seconden na hetzelfde moment begint (dezelfde training uit je horloge, je Garmin-account of een eerdere import). Dezelfde
  ZIP twee keer importeren voegt dus niets toe.
- **Wat geen activiteit is wordt overgeslagen en geteld**, geen foutmelding: routes, trainingen, foto's, CSV en JSON. Een kapot bestand
  staat met zijn naam in het rapport en houdt de rest niet tegen.
- **Het archief gaat nergens heen.** Het wordt stromend gelezen, nergens opgeslagen of verstuurd; alleen de activiteiten komen in de
  database van de app. Grenzen tegen een ZIP-bom: 25 MB per bestand, een totaal van 16 GB, een half miljoen bestanden en drie ZIP's in elkaar.
- Alles blijft op je telefoon, ook de geschiedenis. Het eindrapport staat in het synchronisatieoverzicht als *Archief importeren*.

### Eerlijk over wat niet is gecontroleerd

De lezer is getest met archieven die in de computer zijn gemaakt, in de indeling die Garmin en Strava beschrijven (een ZIP met een ZIP erin,
`.fit.gz`, `.gpx`, `.tcx.gz`). **Hij is nooit op een echte export van Garmin of Strava losgelaten.** Lukt het bij jou niet, zet dan wat het
rapport in Instellingen zegt (aantallen en bestandsnamen, geen gegevens) in een melding op GitHub.

## English

Moving from Garmin Connect or Strava means leaving years of activities behind. Since v1.3.0 OpenConnect reads such an export in one go,
without unpacking.

**Garmin Connect.** On a computer open your Garmin account (garmin.com, *Account*, *Data Management*), choose **Export Your Data** and request
the export; Garmin emails a link when it is ready (hours to days). Download the ZIP on your phone and do **not** unpack it. In OpenConnect:
*Settings*, *Your data*, **Import files or an archive**, pick the ZIP. Keep the app open until it finishes. The watch's FIT files sit in a ZIP
inside the export (`UploadedFiles_*.zip`); OpenConnect opens that itself. Since v1.4.0 `summarizedActivities.json` gives the names (matched on start time). The daily files (steps, sleep, resting heart rate) are **not**
read yet because Garmin does not document them; they are skipped and counted, and the report writes down their shape (file name pattern and key names,
no values, no addresses). **Copy report** puts it on the clipboard to paste into a GitHub issue.

**Strava.** *Settings*, *My Account*, *Download or Delete Your Account*, *Get Started*, **Request Your Archive**. Import the ZIP the same
way. Strava keeps activities in `activities/` as `.fit.gz`, `.gpx` or `.tcx.gz`; all three are read. The names you gave them are in
`activities.csv` and, since v1.4.0, the activities get them (import the archive again if you imported it with v1.3.0; a name you gave stays).

**Your own export.** *Export everything* from OpenConnect is a ZIP too: the activities come back, and the journal, shoes, plan and goals come
back with **Restore from backup** (the report says when a backup is inside).

**Rules.** Nothing is overwritten or doubled: an activity is *already there* when its id exists or another activity starts within 90 seconds of it
(the same training from your watch, Garmin account or an earlier import), so importing the same ZIP twice adds nothing. What is not an activity
(routes, workouts, photos, CSV, JSON) is skipped and counted, not reported as an error; a broken file is named in the report and the rest goes on.
The archive is streamed and never stored or sent anywhere; limits against a ZIP bomb are 25 MB per file, 16 GB in total, 500,000 files and
three ZIPs inside each other. The result is also one line in the sync log, *Archive import*.

**Not verified.** The reader is tested with archives made on the computer in the layout Garmin and Strava describe. **It has never met a real
Garmin or Strava export.** If it fails for you, put what the report in Settings says (counts and file names, no data) in a GitHub issue.
