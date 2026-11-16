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

## Journal (v0.36.0)

*Home, bottom card; Insights, Journal.* Six marks (alcohol, late meal, late caffeine, stressful day, travel, feeling ill) can be put on
today or yesterday. A mark on day D is compared with the night that ends on D+1: the HRV and resting heart rate of that day and the
sleep that ended on it. For each mark and each of the three numbers there must be at least four nights with the mark and four
without. A difference is *notable* from 5 percent in HRV, 2 beats in resting heart rate or 24 minutes of sleep; lower HRV, a higher
resting heart rate and shorter sleep are coloured as worse. It shows what goes together, not what causes what. Code: `core/.../Journal.kt`.

## Heads-up (v0.36.0)

*Home, under the advice.* The normal is the average resting heart rate and HRV of the 28 days before the last three (at least 7
days of values). A day is odd when the resting heart rate is 4 or more beats above normal or the HRV is 20 percent or more below it.
Two odd days in a row ending today give a mild heads-up; three, or both signals odd on the latest day, give a strong one. It often goes
with getting ill or too little rest, but it is a rule of thumb from two numbers, not a diagnosis. Code: `core/.../EarlyWarning.kt`.

## Monotony and strain (v0.37.0)

*Insights, Form.* Foster's measures over the last seven days of daily training load (the same load as the form chart): monotony is the mean
divided by the standard deviation of the seven daily loads (capped at 5; the same load every day is the cap), strain is the week's load
times monotony. Below 1.5 is a varied week, below 2.0 a bit uniform, from 2.0 very monotonous. The usual strain is the average of the four
weeks before that had any training. It needs two weeks of activities and some load in the last week. Code: `core/.../Recovery.kt`.

## Sleep tonight (v0.37.0)

*Home.* The need for tonight is 8 hours, plus 15 minutes after a day with a load of 150 or more and 30 minutes from 300, plus a quarter of
the sleep debt (how far the last seven nights fall short of 8 hours each, never below zero) up to one hour. Debt under 1 hour is none,
under 4 hours small, from 4 hours big. It needs four nights with sleep time. It is a suggestion in the spirit of WHOOP's sleep need,
not medical advice. Code: `SleepNeedCalculator` in `core/.../Recovery.kt`.

## Map (v0.38.0)

*Insights, Map.* The tracks of the newest 300 activities with a distance, each thinned to at most 500 positions, are laid on one grid of at
most 48 by 48 squares, with longitude scaled by the cosine of the latitude so squares are square. The bounds cut 1 percent of the
positions off each end of both axes (from 200 positions up) so one stray point does not shrink the rest into a corner. A square counts
once per activity; sparse tracks are joined in steps of half a square. Brightness has four steps on a log scale up to the busiest square.
There is no background map and nothing leaves the phone. Code: `core/.../Heatmap.kt`.

## Same route (v0.38.0)

*On an activity.* Two activities are the same route when they have the same sport, their distances differ by at most 10 percent, and the first,
the middle and the last position are each within 300 metres. The place is by time at the distance of the activity you look at (a
route run a bit long is not punished); the card also shows how far behind your best it was, with the date of that best, and the difference
with the average of the others. Code: `core/.../RouteMatcher.kt`.

## Hi-score (v0.39.0)

*Insights, Hi-score.* For a year (UTC days, like the calendar): the number of activities, the summed distance and time, the days with an
activity and the longest run of days in a row, the longest running and cycling activity by distance, the biggest climb of one activity,
the month with the most kilometres, the steps and the best day of steps from the daily summaries, and the distance of the year before for
the change in percent. Years with an activity or a day of steps can be picked. Sharing sends one line of text through the share sheet.
Code: `core/.../YearScore.kt`.

## Season plan (v0.39.0)

*Insights, Plan, under the race goal.* Counted back from the race date: taper 1 week (5K, 10K), 2 (half marathon) or 3 (marathon); peak 2, 3
or 3 weeks; build 3, 4 or 4 weeks; base for all the weeks before. The weeks run from today and a part of a week counts as a week; with too few weeks
the front phases fall away, and a race more than a year away plans the last 52 weeks. Kilometres a week per phase are a range around what
you run now (base 95 to 105 percent, build 105 to 120, peak 120 to 130, taper 50 to 70). A rule of thumb for how a season is usually cut up,
not a coach. Code: `core/.../SeasonPlan.kt`.

## Training ramp (v0.41.0)

*Insights, Form.* The load of the last 7 days (UTC days, the same per-activity load as the Form tab) divided by the average load of the four
7-day windows before it. Under 0.8 is *lighter than usual*, 0.8 up to 1.3 *right on your usual*, 1.3 up to 1.5 *more than usual*, 1.5 and up
*a big jump*. It needs 35 days of history and a non-empty four weeks before. A rule of thumb in OpenConnect's own load units, not
medical advice. Code: `core/.../RampRate.kt`.

## Sleep rhythm (v0.42.0)

*Home.* The population standard deviation of the length of the nights of the last 14 days that have a duration, in minutes (at least
7 nights needed). Under 30 minutes is *steady*, 30 up to 60 *variable*, 60 and more *erratic*. Bedtimes are not stored, so it measures
how alike the nights are in length, not in timing. Code: `core/.../SleepConsistency.kt`.

## Resting heart rate trend (v0.42.0)

*Home.* The average resting heart rate (days with a value above 0) of the last 7 days minus the average of the 28 days before. 2 beats or
more lower is *lower*, 2 beats or more higher is *higher*, in between *stable*. Needs 4 days in the last week and 10 in the 28 before.
A rule of thumb from one number, not medical advice. Code: `core/.../RestingHrTrend.kt`.

## Step goal this week (v0.42.0)

*Home.* Of the last 7 days that have steps, the number whose steps reach that day's step goal (the watch's goal, or 10,000 when none is
given). At least 3 days with steps are needed. Also shows the rounded average, the busiest day and the goal of the latest day. Code:
`core/.../StepGoalWeek.kt`.
