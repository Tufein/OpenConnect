# Readiness, trophies, search and export (v0.5.0)

## Readiness (Home)

A number from 0 to 100 for how ready the body looks today, with a ring and one of four labels (below 40 *Take it
easy*, below 70 *Moderate*, below 85 *Good to go*, from 85 *Peak form*). It is **OpenConnect's own estimate** from numbers
your watch reports. It is **not Garmin's Training Readiness** and not medical advice.

| Signal | Weight | How it scores |
|--------|--------|---------------|
| Sleep (the night that ended today) | 35 % | the sleep score if the watch gave one, otherwise hours slept against 8 |
| Body Battery (today's high) | 25 % | the value itself |
| HRV (last night) | 15 % | against your own average of the last 28 days: at or above it scores 80 to 100, below it drops quickly |
| Resting heart rate | 15 % | against your own average: each beat above it costs 10 points from 90 |
| Stress (day average) | 10 % | 100 minus 1.25 times the stress value |

HRV and resting heart rate need at least five earlier days to know "your normal". Signals that are missing are left out
and the others are re-weighted; with fewer than two signals there is no score at all, because one number says too little.
The code is `core/.../Readiness.kt`; the tests pin the weights, the baselines and the edges.

## Trophies and level (Insights, Trophies)

17 trophies computed from what is on the phone, nothing stored: *Press Start* (first activity), *5K Boss*, *10K Champion*,
*Half Marathon Hero* (one run), *Turbo Rider* (one 50 km ride), *Hundred Club*, *Road Warrior*, *Warp Zone* (100, 500,
1,000 km in total), *Everest Level* (8,849 m of climbing), *Combo x7* and *Perfect Month* (step goal 7 and 30 days in a
row), *Million Steps*, *Save Point* (7 hours of sleep, 7 nights in a row), *Multi-Class* (3 sports), *The 150 Club* (150
intensity minutes in a week, the WHO advice), *Dawn Patrol* (5 activities before 06:30) and *Streak Mode* (3 or more
activities a week for 4 weeks). Won ones show the day they were won; the others show a progress bar and how far along.

Your **level** starts at 1 and each level is longer than the last (level 2 at 100 XP, 3 at 400, 4 at 900, ...). XP is
10 per km, 1 per 500 steps, 5 per activity and 100 per trophy. Delete or import data and it is recomputed, so it is
always the truth about what is in the app. Code: `core/.../Achievements.kt`.

## Searching and sorting the activities

Type words in the search field: every word has to match the name, the sport or the date written as `2026-05-02`, so
`run 2026-04` finds the runs of April. Sort by newest, oldest, longest distance or longest time. The sport chips above
still filter. Code: `core/.../ActivityQuery.kt`.

## Export as CSV

*Settings, Export your data* writes **Daily health** (one row per day with steps, distance, calories, floors, intensity
minutes, heart rate, stress, Body Battery, HRV, SpO2, respiration and the sleep of that night) or **Activities** (one row
per activity) to a file you choose. Numbers use dots whatever the phone's language; names that start with `=`, `+`, `-` or
`@` get a leading apostrophe so a spreadsheet does not run them as formulas. The file holds your data only, never a key.
In a spreadsheet with Dutch settings, use *Data, From text/CSV* with a comma as separator. Code: `core/.../Csv.kt`.

## Syncing like Garmin Connect

See [GARMIN.md](GARMIN.md). In short: opening the app syncs (and every 10 minutes while it stays open), pulling the
Home screen down syncs now, a background job runs about every 15 minutes, and the pill at the top of Home says when
the last sync was or how far a running one is.

## Fitness, fatigue and form (v0.34.0)

*Insights, Form.* The picture TrainingPeaks calls the Performance Management Chart, worked out without a power meter from the
daily training load that the weekly load card already uses (minutes times an effort weight from average heart rate against the
highest heart rate seen). **Fitness** is an exponential average of that load over 42 days, **fatigue** over 7 days, and **form** is
fitness minus fatigue. The averages start on the day of the first activity; days without an activity count as zero; it needs two
weeks of history. The status compares form with fitness: 20 percent or more is *Fresh*, down to minus 5 percent *In balance*, down
to minus 30 percent *Building*, below that *Strained*; with almost no fitness (under 10 units, about ten easy minutes a day) there
is no status. The units are OpenConnect's own, not TSS. Code: `core/.../Form.kt`.

## Training advice (v0.34.0)

On Home, under readiness. Readiness (low, moderate, good, excellent) and form (strained, building or in balance, fresh) are put on
two small scales of 0 to 3 and the lower one decides: rest, easy, as planned, or a good day for a hard one. The card says which of
the two holds you back. It is a suggestion from two estimates, not medical advice. Code: `TodayAdviceCalculator` in `core/.../Form.kt`.

## Shape for each distance (v0.35.0)

*Insights, Plan.* The last 42 days of runs (running only) give the average weekly kilometres and the longest run. For each race
distance these are compared with what a typical plan asks (5 km: 20 km a week and an 8 km long run, 10 km: 30 and 12, half marathon:
40 and 18, marathon: 55 and 30), each as a share capped at 100 percent; the shape is the mean of the two. 90 percent or more is
*Ready*, 70 *Almost there*, 40 *Building*, below that *Far off*. The idea is Runalyze's *Marathon Shape*; the numbers are rules of thumb.
Code: `core/.../RaceShape.kt`.

## VO2max from your runs (v0.35.0)

*Insights, Plan.* A run of at least 15 minutes and 2.5 km, with an average heart rate between 62 and 95 percent of your maximum, gives
an estimate: the oxygen cost of its pace (Daniels and Gilbert: `-4.60 + 0.182258 v + 0.000104 v^2`, v in metres a minute) divided by the
share of VO2max its heart rate says it took (Swain: heart rate share = 0.64 times VO2max share plus 0.37). The number shown is the
median of the last five estimates of the last 56 days, with at least two. The race times use Daniels' formula for the share of VO2max
you can hold for a given time and are found by halving an interval; a VO2max of 50 gives 19:57 for 5 km, 41:21 for 10 km, 1:31:35 for a
half marathon and 3:10:49 for a marathon, as in the published tables. It is an estimate, a few ml either way, and not Garmin's VO2 max.
Code: `core/.../Vo2Max.kt`.
