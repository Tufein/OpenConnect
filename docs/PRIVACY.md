# Privacy statement / Privacyverklaring

English first, Nederlands daaronder. The in-app page *Settings → About and licences → What leaves your phone* is generated
from the same canonical list (`android/core/.../privacy/PrivacyRegister.kt`) as the tables below. A test fails when the code
mentions a network address, a data source or a boundary field that the register and this statement do not cover.

The short version: **OpenConnect has no server of ours, no account of ours, no advertising, no analytics and no crash
reporting.** Your data stays on your phone unless you switch on a source, a provider or a file export. Every outbound
request starts from something you did. The **GitHub build** has an in-app updater; the **store build** has no updater, no
install-packages permission and no FileProvider.

## English

### What is stored, and where

- **SQLite** (`openconnect.db`): activities and their GPS tracks, daily numbers, sleep, body weight, water, reports,
  samples and imported watch files, plus older Oura, WHOOP and Hume rows. This database is in the app's private storage;
  no other app can read it.
- **Plain settings**: the journal, shoes, training plan, race goal, weekly goals, maximum heart rate, work-outs, the sync
  log, the Health Connect bookkeeping, the list of apps allowed to forward notifications, and the watch address and name.
- **Encrypted settings** (`EncryptedSharedPreferences`, backed by the Android Keystore): AI keys, your Garmin key and Oura
  client id/secret/tokens. If secure storage is unavailable, a key is not stored at all.
- **Memory only**: the coach conversation and a short notification-forwarding trail (at most 150 lines, holding app names
  and decisions, never notification text). Both disappear when the app process ends.
- **App cache (GitHub build only)**: a downloaded and SHA-256-checked update APK, until Android installs it or the cache
  is cleared. The store build never downloads an update.
- Android's own cloud backup and device transfer are **off** for this app. The app never writes health data into a Google
  backup.

A backup or “Export everything” file that **you** make is a plain file you choose where to save. A backup never contains
keys. Treat these files as sensitive: anyone who has one can read your history.

### What leaves your phone

Only the subjects below, only when you use them, and only over HTTPS (except the watch and Health Connect, which are local
radio/on-device connections):

| Subject | When | Where | What leaves | What comes back |
|---|---|---|---|---|
| **Garmin account** | you add your own Garmin key and sync | `personal.connectapi.garmin.com` | your Garmin API key (to exchange for a bearer token), the bearer token, the date range and an activity id | daily summaries, sleep, steps, stress, Body Battery, HRV, activities and FIT files |
| **Oura Ring** | you connect Oura and sync | `cloud.ouraring.com` (browser login), `api.ouraring.com` | your Oura application's client id and secret, PKCE state/challenge/verifier, the OAuth code or refresh token, access token, date range and page token | access/refresh tokens, sleep, readiness, activity, SpO2, work-outs and ring battery |
| **Claude** | you add a Claude key and ask for a report, a coach answer or a key check | `api.anthropic.com` | your key (header), model, system prompt, the bounded facts, the coach question/history and the report language | the generated text |
| **Gemini** | the same with a Gemini key | `generativelanguage.googleapis.com` | the same facts, question/history and language, with your Gemini key | the generated text |
| **GitHub updates** (GitHub build only) | you open *Settings → Updates* and check/download | `api.github.com`, `github.com` | a GET for the newest release list, then a GET for the APK and its `.sha256`; no account, token or health data | release JSON, APK and checksum. GitHub sees your network address and user agent like any website |
| **Notifications on the watch** | you turn it on and Android delivers a notification | your watch, over Bluetooth | notification id, app id/name, title, body/big text, subtitle, time, category and dismiss flag | nothing; the notification is not sent to a server |
| **Work-outs to the watch** | you press send for a work-out | your watch, over USB | the work-out as a FIT file in `GARMIN/NewFiles` | nothing; no server is involved |
| **Watch and live heart rate** | you read a watch or start live heart rate | your watch, over USB, a folder or Bluetooth | nothing is sent; FIT files and heart-rate samples come in | activities, daily data, sleep, stress, Body Battery, SpO2, HRV and live heart rate |
| **Health Connect** | you switch it on and grant types | on this phone only | exercise session, distance and calories when you ask to save an activity | steps, sleep and weight, only where OpenConnect has no data yet |
| **Files you choose** | you import, back up, export everything or share diagnostics | a file or app you choose | a FIT/GPX/TCX/gzip/ZIP import, a backup, an export, or the diagnostics text | a file you pick; no server is involved |
| **Keys and app lock** | you save a key or turn on the lock | this phone only | nothing | nothing; keys stay in the Keystore-backed preferences |
| **Removed WHOOP and Hume** | you open an old installation | this phone only | nothing; those integrations no longer run | old rows that were already on the phone |

**AI facts, precisely.** The JSON sent to Claude or Gemini can contain activity names, sport, date, duration, distance,
pace/speed, heart rate, elevation, calories, device, kilometre splits and heart-rate halves; week/month/year totals including
steps, sleep, stress, Body Battery and resting heart rate; and, for the coach, the plan weeks with planned/actual distance
and the current proposal. It does **not** contain GPS points or a route, the journal, notes, your own work-out text,
contacts, calendar or any key other than the provider key in its header. Splits and heart-rate halves are derived from a
track, so they are still derived data; the app says so rather than claiming “never anything from your route”.

**Notification text, precisely.** Android hands the notification listener every app's notification; the app reads title,
text and metadata, then drops ongoing, grouped, local-only, call, low-importance and do-not-disturb ones, and applies your
app choices and hide-text switch. At most 16 texts wait at most two minutes in memory while a watch reconnects. Nothing is
written to the database, the sync log or a server. The in-memory forwarding trail holds app names and decisions only.

What an AI provider, Garmin or Oura does with what it receives is governed by that provider's own terms, which you accepted
when you made the key or account. If you do not add an AI key, reports are written by a template on the phone and no AI
provider is called.

### What never leaves the phone

GPS points and routes are never uploaded; they are only stored locally and sent to a watch or Health Connect when you ask.
The journal, notes and your own work-out text are never sent to Garmin, Oura or an AI provider. Keys are never written into
a URL, a report body or a backup; each provider key travels only in its own authentication header. The app does not read your
contacts or calendar, and it does not collect your location for itself.

### Permissions, and why

- **Internet**: only for the services in the table above.
- **Install packages (GitHub build only)**: the updater asks Android to install the APK it downloaded and checked; Android
  asks you. The store build does not declare this permission.
- **Notifications**: nudges, live heart rate, and the notification that shows while forwarding is on.
- **Notification access**: only if you switch on *Notifications on the watch*. It lets the app read the notifications Android
  delivers so it can pass the chosen ones to your watch. You can take it back in Android at any time.
- **Bluetooth / nearby devices**: to talk to a watch or heart-rate strap. On Android 11 and older the system also requires
  the location permission for Bluetooth scanning; the app never reads your location.
- **Foreground service (connected device)**: while live heart rate runs or the watch connection is kept for forwarding.
- **Biometrics**: only for the app lock, if you switch it on.
- **Health Connect**: only the types you allow. OpenConnect reads steps, sleep and weight, and writes exercise, distance and
  calories. It does not read heart rate, HRV, SpO2, active calories or exercise sessions from Health Connect.
- **Photos/files**: only when you choose a file to import or a place to save a backup/export.

### Deleting your data

*Settings → Your data → Delete all data* empties the SQLite database (including activities, tracks, body weight, water,
sleep, reports and imported files) and clears the journal, shoes, plan, race goal, weekly goals, maximum heart rate,
work-outs, sync log, Health Connect bookkeeping and nudge-sent record. It keeps your keys/tokens, onboarding answers,
theme/language, app lock and device switches; keys have their own remove buttons. Uninstalling the app deletes everything it
stored, including keys.

Files you exported or backed up yourself are yours to delete. Data at an AI provider, Garmin or Oura is theirs: use their
own tools to delete it. The in-app page states the same scope, and a test covers each category.

### What the project knows about you

Nothing. GitHub hosts the code and releases and sees who downloads them, as it does for any repository. If you open an
issue, what you write there is public; use *Share diagnostics* (it shows the report first and leaves out keys, tokens, names,
health values and routes) and describe the rest in words.

### Changes and contact

Changes to this statement are part of the release that changes them and show in the changelog. Questions: an issue on the
repository, or the private report route in [SECURITY.md](../SECURITY.md) for a security problem.

## Nederlands

**Kort.** OpenConnect heeft geen server van ons, geen account van ons, geen reclame, geen statistieken en geen
crashrapporten. Je gegevens blijven op je telefoon tenzij jij een bron, een aanbieder of een bestandsexport aanzet. Elk
verzoek naar buiten begint met iets wat jij deed. De **GitHub-versie** heeft een in-app-updater; de **winkelversie**
heeft geen updater, geen installatietoestemming en geen FileProvider.

### Wat er wordt bewaard, en waar

- **SQLite** (`openconnect.db`): activiteiten met hun gps-route, dagcijfers, slaap, gewicht, water, verslagen, samples en
  geïmporteerde horlogebestanden, plus oudere Oura-, WHOOP- en Hume-rijen. De database staat in de privéopslag van de app;
  geen andere app kan hem lezen.
- **Gewone instellingen**: het dagboek, schoenen, trainingsplan, wedstrijddoel, weekdoelen, maximale hartslag, work-outs,
  het synchronisatielogboek, de Health Connect-administratie, de lijst met apps die meldingen mogen doorsturen, en het adres
  en de naam van het horloge.
- **Versleutelde instellingen** (`EncryptedSharedPreferences`, met de Android Keystore): AI-sleutels, je Garmin-sleutel en
  Oura client-id/geheim/tokens. Is veilige opslag niet beschikbaar, dan wordt een sleutel niet bewaard.
- **Alleen in geheugen**: het coachgesprek en een korte sporenlijst van doorgestuurde meldingen (maximaal 150 regels, met
  appnamen en beslissingen, zonder meldingstekst). Allebei weg als het app-proces stopt.
- **App-cache (alleen GitHub-versie)**: een gedownloade en met SHA-256 gecontroleerde update-APK, tot Android hem
  installeert of de cache wordt leeggemaakt. De winkelversie downloadt nooit een update.
- De cloudback-up en toestelovername van Android staan voor deze app **uit**. De app schrijft geen gezondheidsgegevens in
  een Google-back-up.

Een back-up of “Alles exporteren”-bestand dat **jij** maakt, is een gewoon bestand waarvan jij de plek kiest. Een back-up
bevat nooit sleutels. Behandel zo'n bestand als gevoelig: wie het heeft, kan je geschiedenis lezen.

### Waar gegevens heen kunnen

Alleen de onderwerpen hieronder, alleen als je ze gebruikt, en alleen via HTTPS (behalve het horloge en Health Connect: dat
zijn lokale radio- of toestelverbindingen):

| Onderwerp | Wanneer | Waar | Wat gaat er heen | Wat komt er terug |
|---|---|---|---|---|
| **Garmin-account** | je voegt je eigen Garmin-sleutel toe en synchroniseert | `personal.connectapi.garmin.com` | je Garmin-API-sleutel (voor een bearer-token), het bearer-token, de datumperiode en een activiteit-id | dagoverzichten, slaap, stappen, stress, Body Battery, HRV, activiteiten en FIT-bestanden |
| **Oura Ring** | je koppelt Oura en synchroniseert | `cloud.ouraring.com` (inloggen in de browser), `api.ouraring.com` | client-id en geheim van je eigen Oura-app, PKCE-state/challenge/verifier, de OAuth-code of refresh-token, access-token, datumperiode en paginatoken | access/refresh-tokens, slaap, readiness, activiteit, SpO2, work-outs en ringbatterij |
| **Claude** | je voegt een Claude-sleutel toe en vraagt een verslag, coachantwoord of sleutelcontrole | `api.anthropic.com` | je sleutel (header), model, systeemprompt, de begrensde feiten, de coachvraag/geschiedenis en de rapporttaal | de gegenereerde tekst |
| **Gemini** | hetzelfde met een Gemini-sleutel | `generativelanguage.googleapis.com` | dezelfde feiten, vraag/geschiedenis en taal, met je Gemini-sleutel | de gegenereerde tekst |
| **GitHub-updates** (alleen GitHub-versie) | je opent *Instellingen → Updates* en controleert/downloadt | `api.github.com`, `github.com` | een GET voor de nieuwste releaselijst, daarna een GET voor de APK en de `.sha256`; geen account, token of gezondheidsdata | release-JSON, APK en checksum. GitHub ziet je netwerkadres en user-agent zoals elke website |
| **Meldingen op het horloge** | je zet het aan en Android levert een melding | je horloge, via Bluetooth | melding-id, app-id/naam, titel, tekst/big text, subtitel, tijd, categorie en wegklikvlag | niets; de melding gaat niet naar een server |
| **Work-outs naar het horloge** | je drukt op verzenden | je horloge, via USB | de work-out als FIT-bestand in `GARMIN/NewFiles` | niets; er is geen server bij betrokken |
| **Horloge en live hartslag** | je leest een horloge of start live hartslag | je horloge, via USB, een map of Bluetooth | er gaat niets uit; FIT-bestanden en hartslagmetingen komen binnen | activiteiten, dagcijfers, slaap, stress, Body Battery, SpO2, HRV en live hartslag |
| **Health Connect** | je zet het aan en geeft soorten toestemming | alleen op deze telefoon | training, afstand en calorieën als je een activiteit opslaat | stappen, slaap en gewicht, alleen waar OpenConnect nog niets heeft |
| **Bestanden die jij kiest** | je importeert, maakt een back-up, exporteert alles of deelt diagnostiek | een bestand of app die jij kiest | een FIT/GPX/TCX/gzip/ZIP-import, een back-up, een export of de diagnosetekst | een bestand dat jij kiest; geen server |
| **Sleutels en app-vergrendeling** | je bewaart een sleutel of zet de vergrendeling aan | alleen op deze telefoon | niets | niets; sleutels blijven in de Keystore-opslag |
| **Verwijderde WHOOP en Hume** | je opent een oude installatie | alleen op deze telefoon | niets; die integraties draaien niet meer | oude rijen die al op de telefoon stonden |

**AI-feiten, precies.** De JSON naar Claude of Gemini kan bevatten: activiteitsnamen, sport, datum, duur, afstand,
tempo/snelheid, hartslag, hoogtemeters, calorieën, apparaat, kilometersplits en hartslaghelften; week-/maand-/jaartotalen
met stappen, slaap, stress, Body Battery en rusthartslag; en voor de coach de planweken met geplande/werkelijke afstand en
het huidige voorstel. Er zitten **geen** gps-punten of route, dagboek, notities, je eigen work-outtekst, contacten, agenda
of een andere sleutel dan de aanbiedersleutel in de header in. Splits en hartslaghelften zijn afgeleid van een track; de app
zegt dat eerlijk in plaats van “nooit iets van je route”.

**Meldingstekst, precies.** Android geeft de notification listener alle meldingen van alle apps; de app leest titel, tekst
en metadata en laat doorlopende, gegroepeerde, alleen-lokale, oproep-, lage-importantie- en niet-storenmeldingen vallen. Dan
gelden je appkeuzes en de tekst-verbergen-schakelaar. Maximaal 16 teksten wachten maximaal twee minuten in het geheugen
terwijl een horloge opnieuw verbindt. Niets wordt in de database, het synchronisatielogboek of op een server gezet. De
sporenlijst in het geheugen bevat alleen appnamen en beslissingen.

Wat een AI-aanbieder, Garmin of Oura met het ontvangene doet, valt onder de voorwaarden van die aanbieder, die je accepteerde
toen je de sleutel of het account maakte. Voeg je geen AI-sleutel toe, dan schrijft een sjabloon op de telefoon de
verslagen en wordt er geen AI-aanbieder aangeroepen.

### Wat nooit de telefoon verlaat

Gps-punten en routes worden nooit geüpload; ze worden alleen lokaal bewaard en naar een horloge of Health Connect gestuurd
als jij dat vraagt. Het dagboek, notities en je eigen work-outtekst gaan nooit naar Garmin, Oura of een AI-aanbieder.
Sleutels komen nooit in een URL, rapporttekst of back-up; elke aanbiedersleutel reist alleen in zijn eigen
authenticatieheader. De app leest je contacten of agenda niet en verzamelt niet voor zichzelf je locatie.

### Rechten, en waarom

- **Internet**: alleen voor de diensten in de tabel hierboven.
- **Pakketten installeren (alleen GitHub-versie)**: de updater vraagt Android de gedownloade en gecontroleerde APK te
  installeren; Android vraagt het jou. De winkelversie vraagt deze toestemming niet aan.
- **Meldingen**: aansporingen, live hartslag en de melding die zichtbaar is tijdens doorsturen.
- **Toegang tot meldingen**: alleen als je *Meldingen op het horloge* aanzet. De app leest wat Android aflevert om de
  gekozen meldingen naar je horloge te sturen. Je kunt de toegang in Android altijd intrekken.
- **Bluetooth / apparaten in de buurt**: om met een horloge of hartslagband te praten. Op Android 11 en ouder vraagt het
  systeem ook de locatietoestemming voor Bluetooth-scannen; de app leest je locatie nooit.
- **Voorgrondservice (verbonden apparaat)**: zolang live hartslag loopt of de horlogeverbinding voor doorsturen open blijft.
- **Biometrie**: alleen voor de app-vergrendeling, als je die aanzet.
- **Health Connect**: alleen de soorten die je toestaat. OpenConnect leest stappen, slaap en gewicht, en schrijft training,
  afstand en calorieën. Het leest geen hartslag, HRV, SpO2, actieve calorieën of trainingen uit Health Connect.
- **Foto's/bestanden**: alleen als je een bestand kiest om te importeren of een plek om een back-up/export te bewaren.

### Je gegevens wissen

*Instellingen → Jouw gegevens → Alle gegevens wissen* maakt de SQLite-database leeg (inclusief activiteiten, tracks,
gewicht, water, slaap, verslagen en geïmporteerde bestanden) en wist het dagboek, schoenen, plan, wedstrijddoel, weekdoelen,
maximale hartslag, work-outs, synchronisatielogboek, Health Connect-administratie en de nudge-administratie. Sleutels/tokens,
onboarding-antwoorden, thema/taal, app-vergrendeling en apparaatschakelaars blijven staan; sleutels hebben hun eigen
verwijderknoppen. De app verwijderen wist alles wat hij bewaarde, sleutels inbegrepen.

Bestanden die je zelf exporteerde of als back-up bewaarde, zijn aan jou om te wissen. Gegevens bij een AI-aanbieder, Garmin
of Oura zijn van hen: gebruik hun eigen hulpmiddelen om ze te wissen. De pagina in de app zegt hetzelfde, en een test dekt
elke categorie.

### Wat het project over jou weet

Niets. GitHub host de code en de releases en ziet wie ze downloadt, zoals bij elke repository. Open je een issue, dan is wat
je daar schrijft openbaar; gebruik *Diagnose delen* (je ziet het rapport eerst; sleutels, tokens, namen, gezondheidswaarden
en routes blijven eruit) en beschrijf de rest in woorden.

### Wijzigingen en contact

Wijzigingen aan deze verklaring horen bij de release die ze verandert en staan in de changelog. Vragen: een issue op de
repository, of de privé-meldroute in [SECURITY.md](../SECURITY.md) voor een beveiligingsprobleem.
