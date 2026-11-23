# Privacy statement / Privacyverklaring

English first, Nederlands daaronder. The same list is in the app under *Settings, About and licences, Where data can go*,
and a test (`PrivacyStatementTest`) fails when the code mentions a network address that is not in this document's list.

## English

**Short version.** OpenConnect has no server of ours, no account of ours, no advertising, no analytics and no crash
reporting. Your health data is stored on your phone and goes nowhere unless you switch a source or a feature on. Uninstalling
the app deletes it.

### What is stored, and where

Activities (with their GPS tracks), daily numbers (steps, heart rate, stress, Body Battery, sleep, HRV, SpO2), the journal,
shoes, goals and your training plan are stored in the app's private storage on the phone (a SQLite database that no other
app can read). Keys and sign-ins (AI keys, your Garmin key and Oura tokens) are stored in the Android Keystore through
`EncryptedSharedPreferences`; if secure storage is not available the key is not stored at all. Android's own backup and
device-transfer are switched off for the app, so the data does not end up in a Google backup either.

A backup file that **you** make (*Settings, Your data, Back up*) and the "Export everything" files are plain files that
you choose where to save. The backup file never contains keys. Treat these files as sensitive: anyone who has the file can
read your history.

### Where data can go

Only to the services below, only when you use them, and only over HTTPS:

| Address | When | What is sent |
|---------|------|--------------|
| `personal.connectapi.garmin.com` | you use your own Garmin key | the key (to this address only), the dates you ask for; you get your daily data and activities back |
| `cloud.ouraring.com`, `api.ouraring.com` | you connect an Oura Ring | the Oura sign-in (your own Oura app's client id and secret), the dates you ask for |
| `api.anthropic.com` | you add a Claude key and ask for a report or use the coach | a short JSON summary of your numbers and activity names, never the GPS track, and what you type to the coach; your key |
| `generativelanguage.googleapis.com` | you add a Gemini key and ask for a report or use the coach | the same, with your Gemini key |
| `api.github.com`, `github.com` | you open *Settings, Updates* | a request for the newest release and then the download of the APK; nothing about you, but GitHub sees your network address like any website does |

What an AI provider does with what it receives is governed by that provider's own terms, which you accepted when you made
your key. If you do not add an AI key, nothing is sent to an AI provider and the reports are written by a template on your
phone.

Nothing else is ever sent. The app does not collect your location, does not read your contacts, calendar or other apps'
files, and never uploads your GPS tracks. Links to websites (Garmin Connect to make a key, the AI providers' key pages, the
Connect IQ store, the source code) are opened in your browser; the app itself does not call them.

### Permissions, and why

- **Internet**: for the services above, and nothing else.
- **Install packages**: the updater asks Android to install the APK it downloaded and checked. Android asks you each time.
- **Notifications**: nudges, the live heart-rate notification and the one that shows while notifications are sent to the watch.
- **Notification access** (only if you switch on *Notifications on the watch* and give it in Android's settings): the app reads the notifications of the apps you chose, only to pass their title and text to your watch over Bluetooth. The text is never stored, logged or sent anywhere else. The only trace is a short log in the phone's memory for the *What is happening?* part of the card (150 lines at most, gone when the app ends, copied only if you press the button): it holds the name of the app a notification came from and what was decided, never its title or text. The app also asks Android whether the Garmin Connect app is installed, to give a hint; that stays on the phone. Without this access the app sees none of your notifications. You can take it back in Android at any time. See [NOTIFICATIONS.md](NOTIFICATIONS.md).
- **Bluetooth (nearby devices)**: to talk to your watch or a heart-rate strap. On Android 11 and older, Android only lets an app
  scan for Bluetooth devices when it may also see the location, so it asks for location there; the app never reads your location.
- **Foreground service (connected device)**: only while *Live heart rate* runs, or while *Notifications on the watch* is on and keeps its Bluetooth connection.
- **Biometrics**: only for the app lock, if you switch it on.
- **Health Connect**: only when you switch it on, and only the types you allow in Android's own screen: steps, sleep, heart
  rate, resting heart rate, HRV, SpO2, active calories, exercise and distance are read; exercise, distance and calories are
  written when you ask to save an activity to Health Connect. You can take the permissions back in Android at any time.

### Deleting your data

*Settings, Your data, Delete all data* empties the database. Uninstalling the app deletes everything it stored, including
keys. Files you exported or backed up yourself are yours to delete. Keys can be removed one by one in Settings. Data at an AI
provider, Garmin or Oura is theirs: use their own tools to delete it.

### What the project knows about you

Nothing. GitHub hosts the code and the releases and sees who downloads them, as it does for any repository. If you open an
issue, what you write there is public; use *Share diagnostics* (it shows you the report first and leaves out keys, tokens,
names, health values and routes) and describe the rest in words.

### Changes and contact

Changes to this statement are part of the release that changes them and show in the changelog. Questions: an issue on the
repository, or the private report route in [SECURITY.md](../SECURITY.md) for a security problem.

## Nederlands

**Kort.** OpenConnect heeft geen server van ons, geen account van ons, geen reclame, geen statistieken en geen
crashrapporten. Je gezondheidsgegevens staan op je telefoon en gaan nergens heen tenzij jij een bron of functie aanzet. Als je
de app verwijdert, zijn ze weg.

### Wat er wordt bewaard, en waar

Activiteiten (met hun gps-route), dagcijfers (stappen, hartslag, stress, Body Battery, slaap, HRV, SpO2), het dagboek,
schoenen, doelen en je trainingsplan staan in de eigen opslag van de app op de telefoon (een SQLite-database die geen andere
app kan lezen). Sleutels en logins (AI-sleutels, je Garmin-sleutel en Oura-tokens) staan in de Android Keystore via
`EncryptedSharedPreferences`; is veilige opslag er niet, dan wordt een sleutel helemaal niet bewaard. De back-up en
toestelovername van Android zelf staan voor de app uit, dus de gegevens komen ook niet in een Google-back-up terecht.

Een back-upbestand dat **jij** maakt (*Instellingen, Jouw gegevens, Back-up maken*) en de bestanden van "Alles exporteren" zijn gewone
bestanden waarvan jij kiest waar ze komen. Het back-upbestand bevat nooit sleutels. Behandel ze als gevoelig: wie het bestand
heeft, kan je geschiedenis lezen.

### Waar gegevens heen kunnen

Alleen naar de diensten hieronder, alleen als je ze gebruikt, en alleen via HTTPS:

| Adres | Wanneer | Wat gaat er heen |
|-------|---------|------------------|
| `personal.connectapi.garmin.com` | je gebruikt je eigen Garmin-sleutel | de sleutel (alleen naar dit adres), de data die je opvraagt; je krijgt je dagcijfers en activiteiten terug |
| `cloud.ouraring.com`, `api.ouraring.com` | je koppelt een Oura Ring | het inloggen bij Oura (met de client-id en het geheim van je eigen Oura-toepassing), de data die je opvraagt |
| `api.anthropic.com` | je voegt een Claude-sleutel toe en vraagt een verslag of gebruikt de coach | een korte JSON-samenvatting van je cijfers en activiteitsnamen, nooit de gps-route, en wat je aan de coach typt; je sleutel |
| `generativelanguage.googleapis.com` | je voegt een Gemini-sleutel toe en vraagt een verslag of gebruikt de coach | hetzelfde, met je Gemini-sleutel |
| `api.github.com`, `github.com` | je opent *Instellingen, Updates* | een vraag om de nieuwste release en daarna het binnenhalen van de APK; niets over jou, maar GitHub ziet je netwerkadres zoals elke website |

Wat een AI-aanbieder met het ontvangene doet, valt onder de voorwaarden van die aanbieder, die je accepteerde toen je je
sleutel maakte. Voeg je geen AI-sleutel toe, dan gaat er niets naar een AI-aanbieder en schrijft een sjabloon op je telefoon
de verslagen.

Er wordt nooit iets anders verstuurd. De app verzamelt je locatie niet, leest je contacten, agenda of de bestanden van andere
apps niet en uploadt je gps-routes nooit. Verwijzingen naar websites (Garmin Connect om een sleutel te maken, de sleutelpagina's
van de AI-aanbieders, de Connect IQ-winkel, de broncode) worden in je browser geopend; de app roept ze zelf niet aan.

### Rechten, en waarom

- **Internet**: voor de diensten hierboven, en voor niets anders.
- **Pakketten installeren**: de updater vraagt Android de APK te installeren die hij binnenhaalde en controleerde. Android
  vraagt het je elke keer.
- **Meldingen**: aansporingen en de melding van live hartslag.
- **Bluetooth (apparaten in de buurt)**: om met je horloge of hartslagband te praten. Op Android 11 en ouder laat Android een
  app alleen naar Bluetooth zoeken als hij ook de locatie mag zien, dus daar wordt om locatie gevraagd; de app leest je
  locatie nooit.
- **Voorgrondservice (verbonden apparaat)**: alleen zolang *Live hartslag* loopt.
- **Biometrie**: alleen voor de app-vergrendeling, als je die aanzet.
- **Health Connect**: alleen als je het aanzet, en alleen de soorten die je toestaat in het scherm van Android zelf: stappen,
  slaap, hartslag, rusthartslag, HRV, SpO2, actieve calorieën, training en afstand worden gelezen; training, afstand en
  calorieën worden geschreven als je vraagt een activiteit naar Health Connect te schrijven. Je kunt de rechten in Android
  altijd intrekken.

### Je gegevens wissen

*Instellingen, Jouw gegevens, Alle gegevens wissen* maakt de database leeg. De app verwijderen wist alles wat hij bewaarde,
sleutels inbegrepen. Bestanden die je zelf exporteerde of als back-up bewaarde, zijn aan jou om te wissen. Sleutels kun je
in Instellingen één voor één verwijderen. Gegevens bij een AI-aanbieder of bij Garmin of Oura zijn van hen: gebruik hun
eigen hulpmiddelen om ze te wissen.

### Wat het project over jou weet

Niets. GitHub host de code en de releases en ziet wie ze downloadt, zoals bij elke repository. Open je een issue, dan is wat je
daar schrijft openbaar; gebruik *Diagnose delen* (je ziet het rapport eerst, sleutels, tokens, namen, gezondheidswaarden en
routes blijven eruit) en beschrijf de rest in woorden.

### Wijzigingen en contact

Wijzigingen in deze verklaring horen bij de release die ze doorvoert en staan in het changelog. Vragen: een issue op de
repository, of voor een beveiligingsprobleem de privéroute in [SECURITY.md](../SECURITY.md).
