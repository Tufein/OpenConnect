# Vergelijkbare apps: wat doen ze en wat nemen we over?

*Onderzoek van 2026-11-14, voor OpenConnect na v0.33.0. Alleen documentatie: er is geen code gewijzigd.*

OpenConnect is een zelf-gehoste, test-first gebouwde Garmin Connect-vervanger voor één persoon. Dit document zet acht
bekende apps naast elkaar, laat zien wat ze kunnen en wat OpenConnect daarvan al heeft, en sluit af met de beste ideeën
om over te nemen. De Garmin Connect-functies zelf staan al uitgebreid in [FEATURES.md](FEATURES.md); hier gaat het om de
rest van het veld.

**Eerlijk over de bronnen.** Functies komen van de eigen pagina's van de apps (Strava-ondersteuning, Intervals.icu,
Runalyze, Oura, Garmin-handleiding, Gadgetbridge) en, waar die niet bereikbaar waren (Whoop gaf een 403), uit
zoekresultaten en reviews. Prijzen verschillen per bron en zijn alleen een indicatie. Wat OpenConnect al heeft is
gehaald uit README, ROADMAP, INSIGHTS, FEATURES en de releasenotities, niet uit een scan van de code.

## 1. De selectie

| App | Soort | Waarom in de selectie |
|-----|-------|-----------------------|
| **Garmin Connect** (+ Connect+) | Eigen platform bij het horloge | De maatstaf waar OpenConnect tegen concurreert |
| **Strava** | Sociaal netwerk voor sporters | De bekendste sport-app, veel betaalde analyse |
| **Intervals.icu** | Gratis analyse- en planningsplatform | Het diepste "fitness, vermoeidheid, vorm" voor weinig geld |
| **TrainingPeaks** | Coachplatform | De standaard voor belastingsmodellen en trainingsplannen |
| **Runalyze** | Analyse voor hardlopers | Doorzichtige formules, bijna dezelfde filosofie als OpenConnect |
| **Gadgetbridge** | Open-source horlogeapp zonder cloud | Zelfde idee (eigen data), en de bron van onze Bluetooth-sync |
| **Oura** | Ring met abonnement | Slaap, herstel en uitleg; OpenConnect leest Oura al |
| **WHOOP** | Band met lidmaatschap | Herstel, belasting en gewoontes (dagboek) |

Buiten de selectie gebleven: Apple Fitness en Fitbit/Google (veel gesloten en weinig om van te leren voor een Garmin-gebruiker),
Polar, Suunto en COROS (vergelijkbaar met Garmin). Gadgetbridge noemt in zijn releasenotities nog de zelf-gehoste projecten
Endurain en Wanderer; die zijn niet uitgezocht en zijn een logisch vervolg als we naar andere self-hosted apps willen kijken.

## 2. Per app: kernfuncties en wat opvalt

### Garmin Connect en Connect+
- Dagoverzicht met stappen, slaap, hartslag, stress, Body Battery, HRV en Pulse Ox; activiteiten met kaart, grafieken en rondes.
- Training Readiness, Training Status, Training Load, Race Predictor en Garmin Coach (adaptieve plannen); trainingen en
  routes (courses) naar het horloge; badges, uitdagingen, groepen, LiveTrack; apparaatbeheer en de Connect IQ-store.
- **Connect+** (betaald, volgens Garmins handleiding): *Active Intelligence* (AI-inzichten over je data), voeding loggen,
  live activiteitsstatistieken, LiveTrack+, een aanpasbaar prestatiedashboard, Follow Garmin Trails en extra badges.
- Voor OpenConnect belangrijk: veel van de slimste scores (Training Readiness, Race Predictor) worden niet aan
  ontwikkelaars gegeven. Daarom rekent OpenConnect ze zelf uit en zegt dat erbij.

### Strava
- Gratis: activiteiten opnemen en delen, feed, clubs, deel van de segmenten, de wereldwijde heatmap bekijken.
- Betaald (officiële lijst): filter- en persoonlijke segmentranglijsten en Live Segments, Relative Effort, Fitness &
  Freshness, Training Log, Matched Activities, Grade Adjusted Pace, eigen hartslagzones, trainingsplannen, Best Efforts,
  eigen doelen, routes maken en voorgestelde routes, persoonlijke heatmaps, offline kaarten, Beacon, weer bij een activiteit.
- Volgens de tech-pers in 2026: AI-samenvattingen (Athlete Intelligence), AI-routeplanning, strengere controle op
  valsspelers in ranglijsten en meer wandelfuncties. Indicatieprijs rond 12 dollar per maand (derde bronnen, niet bevestigd).
- Les: de kracht is het sociale en de segmenten, en dat is niets voor een app voor één persoon. Maar *Relative Effort*,
  *Matched Activities* (dezelfde route in de tijd vergelijken) en de *persoonlijke heatmap* kunnen lokaal.

### Intervals.icu
- **Fitness, Fatigue en Form** (CTL, ATL, TSB) met een voorspelling op basis van je geplande trainingen.
- Powercurve met meerdere critical power-modellen en watt per kilo; eigen zones voor elke datastroom (FTP, LTHR of eigen veld).
- Trainingskalender met slepen en neerzetten, wekelijkse totalen, naleving van het plan en weersverwachting in de kalender.
- Workout builder met stappen op vermogen, hartslag, tempo of cadans, import en export (ZWO, FIT, MRC, ERG), sync naar het horloge.
- Jaarplan dat vanaf een doelrace terugrekent in fases (Base, Build, Peak) met herstelweken en taper.
- Gratis kern; een Supporter-abonnement van ongeveer 4 dollar per maand voegt extra's toe (volgens een derde bron).
- Les: één klein team bewijst dat diepe analyse weinig kost. De Fitness-Fatigue-Form-grafiek is een standaard in de duursport en zit bij drie van de acht apps.

### TrainingPeaks
- **Performance Management Chart**: dagelijkse TSS wordt Fitness (CTL, 42 dagen), Fatigue (ATL, 7 dagen) en Form (TSB).
- Gestructureerde workout builder die TSS meteen uitrekent en doelen vult op basis van je zones.
- Trainingsplannen kopen, coach-sporter-werkstroom en naleving. Premium kost volgens de eigen pagina ongeveer 125 dollar per jaar.
- Les: alles hangt af van goede zones; foute zones geven foute belasting. Een app moet zones dus zichtbaar en aanpasbaar maken.

### Runalyze
- **TRIMP**, **effectieve VO2max** per activiteit uit de verhouding tussen hartslag en tempo (met een correctiefactor uit je beste race).
- Racevoorspelling met kiesbare methoden (Bock/CPP, Steffny, Cameron, VO2max) en **Marathon Shape**: past je weekvolume en je langste
  duurloop bij de afstand (17 procent is genoeg voor een 10 km, 42,5 voor een halve, 100 voor een marathon).
- Monotonie, training strain en stressbalans als waarschuwing voor te eentonig of te zwaar trainen; ANOVA-boxplots, trendanalyse, Climb Score.
- Import van veel formaten (FIT, GPX, TCX, SLF, TTBIN en meer), sync met Garmin Connect, Polar, Suunto en Fitbit, materiaal bijhouden, postergenerator, weer.
- Premium is volgens de functiepagina maar twee hulpmiddelen groot (de tegelteller en de VO2max-factoranalyse); de rest is vrij.
- Les: de formules staan uitgelegd. Dat past bij OpenConnects regel "een eigen schatting, geen belofte". Marathon Shape is het beste ontbrekende stuk bij onze racevoorspelling.

### Gadgetbridge
- Open source (AGPL), Android, geen leverancierscloud, ondersteunt veel horloges; Garmin deels (synchronisatie loopt soms vast, nieuwere modellen geven niet alles).
- Meldingen, agenda, tijd en weer naar het horloge; lokale database met activiteit en slaap; export (FIT en database) en uploaden naar zelf gekozen diensten.
- Les: dit is "horlogecompanion": wat het horloge van de telefoon nodig heeft. OpenConnect leest alleen en schrijft (nog) niets terug naar het horloge behalve
  watchfaces en trainingen. Ons Bluetooth-protocol komt uit hun code, dus verbeteringen daar zijn ook voor ons nuttig.

### Oura
- Drie dagscores (Slaap, Readiness, Activiteit), 50+ meetwaarden, dagstress, cardiovasculaire leeftijd, weerstand tegen stress, Cardio Capacity (VO2max).
- **Symptom Radar** (vroege tekenen van ziek worden), **Advisor** (AI-assistent, alleen met lidmaatschap), Meals (maaltijdtijden en slaap), glucose via Stelo, vruchtbaar venster.
- Lidmaatschap ongeveer 6 dollar per maand of 70 per jaar (officiële pagina, Verenigde Staten). Zonder ring geen volledige inzichten.
- Les: elke score komt met uitleg en context ("je slaapscore is hoog maar je herstel matig, want de slaapschuld loopt op"). OpenConnect doet dat al per grafiek.

### WHOOP
- **Recovery** (1 tot 99 procent), **Strain** (0 tot 21) met een **Strain Target** (hoe zwaar vandaag, afgeleid van je herstel), slaap met slaapbehoefte en bedtijdadvies.
- **Journal**: dagelijks gewoontes aanvinken (alcohol, laat eten, cafeïne) en zien hoe ze je herstel beïnvloeden; Weekly Plan.
- Healthspan en Pace of Aging (negen meetwaarden zoals VO2max, rusthartslag, slaapregelmaat, krachttraining), Health Monitor (afwijkende waarden), Coach (generatieve AI), Advanced Labs (bloedonderzoek via Quest, alleen Verenigde Staten).
- Alleen met lidmaatschap (volgens een review ongeveer 199 tot 239 dollar per jaar; de officiële pagina was niet bereikbaar).
- Les: Journal met correlatie is de slimste kleine functie, en Strain Target maakt een score bruikbaar ("wat doe ik nu?").

## 3. Vergelijkingstabel

● heeft het (ook als het betaald is) · ◐ deels of beperkt · · heeft het niet · 🚫 bewust niet in OpenConnect.
Kolom OC is OpenConnect v0.33.0 volgens de docs. GC = Garmin Connect, Str = Strava, Int = Intervals.icu, TP = TrainingPeaks,
Run = Runalyze, GB = Gadgetbridge. Het is een grove indruk, geen audit.

| Functie | GC | Str | Int | TP | Run | GB | Oura | Whoop | **OC** |
|---------|----|-----|-----|----|-----|----|------|-------|--------|
| Activiteiten met kaart, grafieken, zoeken | ● | ● | ● | ● | ● | ◐ | · | · | ● |
| Rondes en interactieve grafieken | ● | ● | ● | ● | ● | · | · | · | ◐ |
| Import en export FIT, GPX, TCX | ● | ● | ● | ● | ● | ◐ | · | · | ● |
| Slaap, HRV, rusthartslag per dag | ● | · | ◐ | ◐ | ◐ | ● | ● | ● | ● |
| Herstelscore (readiness) | ● | · | · | · | · | · | ● | ● | ● |
| Wekelijkse belasting en oordeel | ● | ● | ● | ● | ● | · | · | ● | ● |
| Fitness, vermoeidheid, vorm (CTL/ATL/TSB) | ◐ | ● | ● | ● | ◐ | · | · | · | **·** |
| Hartslagzones en tijd per zone | ● | ● | ● | ● | ● | · | · | ◐ | ● |
| Racevoorspelling | ● | · | · | · | ● | · | · | · | ● |
| VO2max-schatting per activiteit | ● | · | ◐ | · | ● | · | ● | ● | **·** |
| Marathon Shape (ben ik klaar voor de afstand) | · | · | · | · | ● | · | · | · | **·** |
| Racedoel met aftelling en oordeel | ◐ | · | ● | ● | · | · | · | · | ● |
| Trainingsplan en workouts naar het horloge | ● | ◐ | ● | ● | · | · | · | ◐ | ● (niet op echt horloge getest) |
| Jaarplan in fases terug vanaf de race | ◐ | · | ● | ● | · | · | · | · | **·** |
| Vermogen: powercurve, FTP, TSS | ◐ | ● | ● | ● | · | · | · | · | **·** (niet gevonden in de docs) |
| Routes maken, ontdekken, heatmap | ● | ● | · | · | · | · | · | · | **·** |
| Segmenten, ranglijsten, clubs, vrienden | ● | ● | · | · | · | · | · | · | 🚫 |
| AI-uitleg of coachchat | ● | ● | · | · | · | · | ● | ● | ● (Claude of Gemini, eigen sleutel) |
| Dagboek en gewoontes met correlatie | ◐ | · | · | · | · | · | ◐ | ● | **·** |
| Waarschuwing bij afwijkende waarden (ziek worden) | ◐ | · | · | · | · | · | ● | ● | **·** |
| Slaapbehoefte en bedtijdadvies | ◐ | · | · | · | · | · | ◐ | ● | **·** |
| Gewicht, lichaamssamenstelling, voeding | ◐ | · | ◐ | ◐ | ◐ | ◐ | ● | ◐ | **·** (staat als gepland in FEATURES.md) |
| Schoenen en materiaal | ● | ● | ◐ | ◐ | ● | · | · | · | ● |
| Trofeeën, level, reeksen, kalender | ● | ◐ | ● | ● | ● | · | · | · | ● |
| Horlogecompanion (meldingen, weer, agenda) | ● | · | · | · | · | ● | · | · | **·** |
| Watchface en widget | ● | · | · | · | · | ◐ | · | · | ● (broncode, nog niet gecompileerd) |
| Alles lokaal, geen abonnement, open source | · | · | · | · | · | ● | · | · | ● |
| Meerdere bronnen (Garmin, Oura, Health Connect) | ◐ | ◐ | ● | ● | ● | ◐ | · | · | ● |

## 4. Wat OpenConnect al goed doet

- Eigen data in één bestand op je telefoon, geen abonnement en geen account bij ons; dat doet alleen Gadgetbridge ook.
- Garmin, Oura en Health Connect naast elkaar, plus rechtstreekse sync met het horloge over USB en Bluetooth zonder Garmin Connect.
- Bij elke grafiek staat in gewoon Nederlands wat hij laat zien, en elke eigen berekening heet een schatting. Dat zien we nergens anders zo consequent.
- Test-first, en een eerlijke lijst van wat nooit op een echt horloge is geprobeerd.

## 5. De beste ideeën om over te nemen

Gerangschikt op nut voor één persoon, de kleinste inspanning en hoe goed het past bij wat er al is (alles is lokaal te
berekenen, pure Kotlin in `core` met tests eerst). Maat: S is een paar uur, M een dag of twee, L een flinke brok.

| # | Idee | Komt van | Waarom nu | Maat |
|---|------|----------|-----------|------|
| 1 | **Fitness, vermoeidheid en vorm** als een grafiek over de dagen (exponentiële gemiddelden van 42 en 7 dagen over de belasting die er al is, het verschil is "vorm"). Past als een levensbalk: hoeveel HP heb je vandaag? | Intervals.icu, TrainingPeaks, Strava | Een standaard in de duursport, en de invoer (minuten × inspanningsgewicht) bestaat al in `TrainingLoad.kt`. Werkt zonder powermeter. | M |
| 2 | **Dagboek met correlatie**: een paar aanvinkvakjes per dag (alcohol, laat eten, cafeïne, reizen, ziek) en per vakje hoeveel HRV, slaap en herstel gemiddeld afwijken op die dagen, pas na genoeg dagen. | Whoop, Oura | Privé, lokaal, klein, en geeft Stefan iets dat Garmin niet laat zien. De rekenregel is makkelijk te testen. | M |
| 3 | **Marathon Shape** (weekvolume en langste duurloop tegenover wat de afstand vraagt) naast de racevoorspelling. | Runalyze | Maakt de bestaande voorspelling eerlijker: een marathontijd uit een 5 km zonder lange duurlopen is een gok. | S tot M |
| 4 | **Waarschuwing bij afwijkende waarden**: rusthartslag hoger en HRV lager dan je eigen 28 dagen, meerdere dagen achter elkaar, krijgt een melding "neem het rustig". Geen medische claim. | Oura Symptom Radar, Whoop Health Monitor | De basislijnen bestaan al in de readiness-berekening. Past bij de meldingen die er zijn. | S tot M |
| 5 | **Monotonie en strain** (Foster) als extra regel op de belastingkaart, en een tip voor een rustweek. | Runalyze | Een paar regels rekenwerk bovenop de weekbelasting. | S |
| 6 | **Vandaag-advies** (lichte dag, normaal, zwaar) uit readiness en recente belasting, zoals een Strain Target. | Whoop | Maakt een score bruikbaar op Home. Past bij het racedoel-oordeel. | S |
| 7 | **VO2max per loop** uit hartslag en tempo met een correctiefactor uit je beste race, getoond als trend, en als nieuwe invoer voor de racevoorspelling. | Runalyze | Vult een gat dat Garmin niet aan ontwikkelaars geeft; wel duidelijk als schatting benoemen. | M |
| 8 | **Persoonlijke heatmap**: al je routes over elkaar als een puntenwolk in pixelstijl op een donker scherm, zonder kaarttegels. | Strava | Lokaal en privé, en het past precies bij het retro-uiterlijk (denk aan een kaart in een rollenspel). | M |

Nog interessant, maar later:

- **Vergelijkbare activiteiten** (dezelfde route in de tijd naast elkaar): Strava *Matched Activities*. M.
- **Slaapbehoefte en bedtijdadvies** uit de slaapschuld van 7 dagen: Whoop. S.
- **Jaarplan in fases** terug vanaf het racedoel (Base, Build, Peak, taper): Intervals.icu. M tot L, na idee 1 want het gebruikt de belastingsgrafiek.
- **Hi-score-kaart of jaarposter** met je mooiste cijfers in pixelstijl, om te delen: Runalyze-poster, Strava jaaroverzicht. S.
- **Uploaden naar Intervals.icu** met je eigen sleutel, zodat je er een tweede paar ogen op hebt: lees eerst de gebruiksvoorwaarden van de API. M.
- **Vermogen** (FTP, normalized power, TSS, powercurve): alleen als er powermeters in beeld komen; niet gevonden in de docs. L.
- **Horlogecompanion** (meldingen, weer, agenda): groot, en pas zinvol zodra de Bluetooth-sync op een echt horloge bewezen is. L.

Bewust niet overnemen:

- Segmenten, ranglijsten, clubs en vrienden: sociaal is niet het doel van een app voor één persoon.
- Bloedonderzoek (Advanced Labs), glucose, voeding via foto: gevoelige gegevens en medische claims; geen plek voor in een zelf-gehoste app zonder jurist.
- Healthspan of "biologische leeftijd": mooi verpakt, maar ouder dan je paspoort zeggen is een medische claim die we niet kunnen onderbouwen.
- Een account of cloud bij ons: de belofte is juist dat alles bij jou blijft.

## 6. Aanbevolen volgorde

1. **Fitness, vermoeidheid en vorm** (1), daarna het **vandaag-advies** (6): een kleine reeks die samen de Home-pagina slimmer maakt.
2. **Marathon Shape** (3) en **VO2max per loop** (7) samen: ze maken de racevoorspelling aantoonbaar eerlijker.
3. **Dagboek met correlatie** (2) en **waarschuwing bij afwijkende waarden** (4): hierdoor wordt OpenConnect persoonlijk, niet alleen een Garmin-spiegel.
4. De **heatmap** (8) als leuk tussendoortje, voor het retro-gevoel.

## 7. Verversing van 2026-11-18 (na v0.50.0)

*Bijgewerkt voor OpenConnect v0.51.0. De tabel in sectie 3 is niet herschreven; hieronder staat wat er sindsdien veranderde.*

**Wat is gebouwd sinds dit onderzoek.** Alle ideeën van de ranglijst in sectie 5 zijn gebouwd, een per release (v0.34.0 tot
en met v0.39.0: fitness, vermoeidheid en vorm; dagboek; marathon shape en VO2max per loop; waarschuwing bij afwijkende waarden;
monotonie en strain; vandaag-advies; kaart en dezelfde route; hi-score en jaarplan). Daarna kwamen de rest van de Garmin-ruimte
(v0.40.0 tot v0.48.0) en de banden: **WHOOP (v0.49.0)** en **Hume Band (v0.50.0)**, op dezelfde manier als de Oura Ring: een eigen
tabblad, eigen opslag, nooit gemengd met het horloge. De rijen WHOOP en Hume in "Meerdere bronnen" kloppen dus nu.

**Nieuw idee uit het verse onderzoek, gebouwd in v0.51.0: apparaten naast elkaar.** Wie een horloge, een ring en een band draagt,
krijgt drie apps met drie verschillende getallen voor dezelfde nacht. Voor zover dit onderzoek reikte zet geen van de apps ze naast elkaar (elke
leverancier laat alleen zijn eigen cijfer zien); voor een app die al meerdere bronnen leest kost het weinig en het past bij de
belofte dat alles bij jou blijft. De kaart *Apparaten vergeleken* op het Dag-tabblad vergelijkt alleen wat alle apparaten op
dezelfde manier tellen (slaap, stappen, rusthartslag, bloedzuurstof) en noemt de afstand tussen de getallen, geen winnaar. Zie
[INSIGHTS.md](INSIGHTS.md).

**Gezien en nog niet gebouwd** (uit zoekresultaten en leveranciersberichten van 2026; niet bevestigd op de eigen pagina's):

| Idee | Komt van | Waarom het (nog) niet is gebouwd | Maat |
|------|----------|----------------------------------|------|
| Trend van *actieve tijd* per week (minuten bewegen, niet alleen stappen) | Oura | Stap- en minutentrend bestaan al; weinig nieuws zolang er maar een bron voor minuten is. Eerst zien of Health Connect en Oura het gelijk tellen. | S |
| Voeding en leefstijl loggen als bron voor het dagboek | Garmin Connect+ | Het dagboek (v0.36.0) kent al vrije vakjes met correlatie; voeding is gevoelig en vraagt een eigen voedselbron. Alleen de vakjes uitbreiden is S. | S tot L |
| Een training van de band doorsturen naar Strava | WHOOP | De API-voorwaarden van Strava voor 2026 verbieden volgens de berichten gebruik voor AI-doeleinden, en OpenConnect heeft een AI-coach; eerst de voorwaarden lezen. | M |
| Een nieuw schermloos Garmin-bandje (Cirqa) als apparaat | Garmin | Alleen een aankondiging; er is nog niets bekend over een koppeling of Health Connect-gegevens. Pas bekijken als er documentatie is. | ? |

**Volgorde van hier.** (1) Eerst de drie banden echt proberen (nu draait niets daarvan tegen een echt account, band of telefoon;
zie de "Niet gecontroleerd"-lijsten in [WHOOP.md](WHOOP.md) en [HUME.md](HUME.md)). (2) Het dagboek met extra vakjes. (3) De
trend van actieve tijd.

## Bronnen

* Strava abonnementsfuncties: <https://support.strava.com/hc/articles/216917657-Strava-Subscription-Features>
* Strava in 2026 (derde bronnen): <https://www.wareable.com/sport/strava-ai-route-planning-cheater-leaderboards-update>, <https://www.t3.com/active/strava-2026-future-and-challenges>
* Intervals.icu: <https://www.intervals.icu/features/plan/>, <https://www.intervals.icu/features/fitness-chart/>
* TrainingPeaks Premium: <https://www.trainingpeaks.com/premium/>
* Runalyze functies: <https://runalyze.com/help/article/features>, <https://blog.runalyze.com/features/new-marathon-shape-for-other-distances>
* Gadgetbridge Garmin: <https://gadgetbridge.org/basics/topics/garmin>, <https://gadgetbridge.org/blog/release-0_92_00/>
* Oura lidmaatschap: <https://ouraring.com/membership>
* WHOOP (via zoekresultaten): <https://www.whoop.com/us/en/thelocker/2026-whats-new/>, <https://www.whoop.com/thelocker/whoop-advanced-labs>
* Garmin Connect+ (handleiding): <https://www8.garmin.com/manuals/webhelp/GUID-D3BE589F-1A6C-4B6F-9ABC-07DB9AA6C739/EN-US/GUID-476303CC-9A30-4D97-B3E2-978EAE76647B.html>
