package nl.tufein.openconnect.ui

import nl.tufein.openconnect.core.Achievements
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.ActivityAround
import nl.tufein.openconnect.core.BodyEntry
import nl.tufein.openconnect.core.LookBackReport
import nl.tufein.openconnect.core.WaterEntry
import nl.tufein.openconnect.core.Effort
import nl.tufein.openconnect.core.Lap
import nl.tufein.openconnect.core.OwnWorkout
import nl.tufein.openconnect.core.PlanAssessment
import nl.tufein.openconnect.core.PlanProgress
import nl.tufein.openconnect.core.PlanProposal
import nl.tufein.openconnect.core.RaceState
import nl.tufein.openconnect.core.TrainingPlan
import nl.tufein.openconnect.core.ArchiveImport
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.DaySeries
import nl.tufein.openconnect.core.Overview
import nl.tufein.openconnect.core.PlayerLevel
import nl.tufein.openconnect.core.Readiness
import nl.tufein.openconnect.core.MonthView
import nl.tufein.openconnect.core.StreakSummary
import nl.tufein.openconnect.core.StreakRun
import nl.tufein.openconnect.core.Shoe
import nl.tufein.openconnect.core.ShoeUsage
import nl.tufein.openconnect.core.ActivityZones
import nl.tufein.openconnect.core.MaxHr
import nl.tufein.openconnect.core.MaxHrSource
import nl.tufein.openconnect.core.ZoneOverview
import nl.tufein.openconnect.core.ZoneVerdict
import nl.tufein.openconnect.core.TrainingLoad
import nl.tufein.openconnect.core.BodyWarning
import nl.tufein.openconnect.core.FormSeries
import nl.tufein.openconnect.core.HeatGrid
import nl.tufein.openconnect.core.JournalBook
import nl.tufein.openconnect.core.MonotonyResult
import nl.tufein.openconnect.core.RampRate
import nl.tufein.openconnect.core.RestingHrTrend
import nl.tufein.openconnect.core.SleepConsistency
import nl.tufein.openconnect.core.StepGoalWeek
import nl.tufein.openconnect.core.SleepNeed
import nl.tufein.openconnect.core.RouteHistory
import nl.tufein.openconnect.core.SeasonPlan
import nl.tufein.openconnect.core.YearScore
import nl.tufein.openconnect.core.YearOverview
import nl.tufein.openconnect.core.TagEffect
import nl.tufein.openconnect.core.TodayAdvice
import nl.tufein.openconnect.core.RaceShape
import nl.tufein.openconnect.core.Vo2Summary
import nl.tufein.openconnect.core.WeeklyGoalProgress
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.RecordEntry
import nl.tufein.openconnect.core.SleepSummary
import nl.tufein.openconnect.core.Sport
import nl.tufein.openconnect.core.StoredReport
import nl.tufein.openconnect.core.TrendRow
import nl.tufein.openconnect.core.WeeklySummary
import nl.tufein.openconnect.core.reports.ReportPeriod
import nl.tufein.openconnect.core.weekStart
import java.time.LocalDate
import java.time.ZoneOffset

// The screen states the ViewModel publishes, one immutable value per screen. Moved here from AppViewModel.kt in
// v1.14.0 so that file holds behaviour only; nothing in them changed.

data class HomeState(
    val loaded: Boolean = false,
    val overview: Overview? = null,
    val hasData: Boolean = false,
    /** OpenConnect's own 0 to 100 estimate for the day, null while fewer than two signals are known. */
    val readiness: Readiness? = null,
    /** The night that ended on the day shown, if the watch reported it. */
    val sleep: SleepSummary? = null,
    val level: PlayerLevel? = null,
    val goals: WeeklyGoals = WeeklyGoals(),
    /** This week against [goals]. */
    val goalProgress: WeeklyGoalProgress? = null,
    /** This week's training load against your four-week average; null until there are two weeks of activities. */
    val load: TrainingLoad? = null,
    /** Fitness, fatigue and form over the last 90 days; null until there are two weeks of activities. */
    val form: FormSeries? = null,
    /** How hard today should be, from readiness and form; null when neither is known. */
    val advice: TodayAdvice? = null,
    /** The days marked in the journal. */
    val journal: JournalBook = JournalBook(),
    /** Resting heart rate or HRV off for days in a row compared with your own last four weeks; null when all is as usual. */
    val warning: BodyWarning? = null,
    /** How alike the last seven days of training were, and the strain; null until there are two weeks of activities. */
    val monotony: MonotonyResult? = null,
    val ramp: RampRate? = null,
    /** How long to sleep tonight; null while fewer than four of the last seven nights are known. */
    val sleepNeed: SleepNeed? = null,
    val sleepConsistency: SleepConsistency? = null,
    val stepGoalWeek: StepGoalWeek? = null,
    val restingHrTrend: RestingHrTrend? = null,
)

data class TrophiesState(val loaded: Boolean = false, val achievements: Achievements? = null)

data class ActivitiesState(
    val loaded: Boolean = false,
    val sport: Sport? = null,
    val sports: List<Sport> = emptyList(),
    val items: List<Activity> = emptyList(),
)

data class DetailState(
    val loaded: Boolean = false,
    val activity: Activity? = null,
    val laps: List<Lap> = emptyList(),
    val efforts: List<Effort> = emptyList(),
    val report: StoredReport? = null,
    /** Time per heart-rate zone of this activity, and the maximum the zones were worked out from. */
    val zones: ActivityZones? = null,
    val zonesMaxHr: Int = 0,
    /** The other runs on the same route, when there are any. */
    val route: RouteHistory? = null,
    /** The pair of shoes this activity went on, the pairs it could be put on, and whether it was chosen by hand. */
    val shoe: Shoe? = null,
    val shoeChoices: List<Shoe> = emptyList(),
    val shoeByHand: Boolean = false,
    /** What the watch and the Oura Ring measured around this activity (v0.43.0); null when neither has anything to say. */
    val around: ActivityAround? = null,
    /** True for a workout that only the Oura Ring recorded: it is not stored as an activity, so it cannot be deleted, exported or reported on. */
    val fromRing: Boolean = false,
)

data class HealthState(
    val loaded: Boolean = false,
    val trend: List<TrendRow> = emptyList(),
    val sleeps: List<SleepSummary> = emptyList(),
    val dailies: List<DailySummary> = emptyList(),
    /** The weight the person entered by hand, oldest first, at most the last 30 days. */
    val body: List<BodyEntry> = emptyList(),
    /** The water the person logged by hand, oldest first, at most the last 30 days. */
    val water: List<WaterEntry> = emptyList(),
    /** Readiness per day (OpenConnect's estimate) and the watch's sleep score per night, for the score charts. */
    val readiness: List<Pair<LocalDate, Int?>> = emptyList(),
    val sleepScores: List<Pair<LocalDate, Double?>> = emptyList(),
)

/**
 * What the Insights "Day" tab picks from (v0.44.0): the last stretch of days of each device the person uses, read once. The day,
 * the source and the measure are chosen on screen, so they cost no new read. A source is null when its device is not in use.
 */
data class DayState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val garmin: DaySeries? = null,
    val oura: DaySeries? = null,
    val whoop: DaySeries? = null,
    val hume: DaySeries? = null,
)

data class ReportsState(
    val loaded: Boolean = false,
    /** The first day of the week, month or year on screen (the name is from when only weeks existed). */
    val weekStart: LocalDate = weekStart(LocalDate.now(ZoneOffset.UTC)),
    val period: ReportPeriod = ReportPeriod.WEEK,
    val summary: WeeklySummary? = null,
    val report: StoredReport? = null,
    val records: Map<Sport, Map<String, RecordEntry>> = emptyMap(),
    /** The best times over 1 km, 5 km, 10 km and up, over all runs. */
    val efforts: List<Effort> = emptyList(),
)

/**
 * The colour theme ([Palette] key), the letters ([Letters] key), the style ([Style] key), signal colours, ring weight
 * and photo tone; since v1.15.0 also the retro pack: [scanlines] over the Retro style and 8-bit [sounds], and since
 * v1.16.0 [levelUps], a message when new data unlocks a trophy or a level.
 */
data class Look(
    val palette: String = "olive",
    val letters: String = "clear",
    val style: String = "editorial",
    val signals: String = "soft",
    val rings: String = "thin",
    val hero: String = "auto",
    val scanlines: Boolean = false,
    val sounds: Boolean = false,
    val levelUps: Boolean = false,
)

/**
 * What the front page shows: the card under the battery ([metric], a [HomeMetric] key) and whether the battery card,
 * the journal and the "Last 7 days" section are drawn. Everything is on by default, and calories keep the old page.
 */
data class HomePrefs(
    val metric: String = HomeMetric.DEFAULT,
    val battery: Boolean = true,
    val journal: Boolean = true,
    val last7: Boolean = true,
    /** The card that starts and shows a live heart rate from the Garmin watch. On by default. */
    val live: Boolean = true,
)

/** Which devices the person uses. [onboarded] is false until the first-start questions are answered. */
data class SourcesUi(val onboarded: Boolean = true, val garmin: Boolean = true, val oura: Boolean = false)
const val CSV_DAILY = "daily"
const val CSV_ACTIVITIES = "activities"

/** The file import: [running] with how far it is, or the [report] of the last one. */
data class ImportUi(
    val running: Boolean = false,
    val entries: Int = 0,
    val imported: Int = 0,
    val alreadyThere: Int = 0,
    val report: ArchiveImport.Report? = null,
)

/** The Calendar tab: a month with its activities, and the streaks. [view] is null until loaded. */
data class CalendarState(
    val loaded: Boolean = false,
    val view: MonthView? = null,
    val streaks: StreakSummary = StreakSummary(StreakRun.NONE, StreakRun.NONE, StreakRun.NONE, StreakRun.NONE, false),
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

/** The Shoes tab: every pair with what it has done so far. */
data class ScoreState(val loaded: Boolean = false, val years: List<Int> = emptyList(), val selected: Int? = null, val score: YearScore? = null)

data class YearState(val loaded: Boolean = false, val years: List<Int> = emptyList(), val selected: Int? = null, val overview: YearOverview? = null)

/** The Look back tab: the report for the chosen sport, or null until it was first worked out. */
data class LookBackState(val loaded: Boolean = false, val report: LookBackReport? = null)

data class MapState(val loaded: Boolean = false, val grid: HeatGrid? = null)

data class JournalState(val loaded: Boolean = false, val loggedDays: Int = 0, val effects: List<TagEffect> = emptyList())

data class GearState(val loaded: Boolean = false, val usage: List<ShoeUsage> = emptyList())

/** The Zones tab: nothing to show until [loaded]; without any heart rate the overview has no zones. */
data class ZonesState(
    val loaded: Boolean = false,
    val overview: ZoneOverview = ZoneOverview(MaxHr(0, MaxHrSource.NONE), emptyList(), emptyList(), null, ZoneVerdict.NO_DATA),
)

/** The training plan on the Plan tab: nothing chosen yet ([plan] is null) or the plan and how far it has got. */
data class PlanState(
    val loaded: Boolean = false,
    val plan: TrainingPlan? = null,
    val progress: PlanProgress? = null,
    val assessment: PlanAssessment = PlanAssessment(emptyList()),
    val proposal: PlanProposal? = null,
    /** UI-only identity for this plan proposal; never the persisted core proposal id. */
    val proposalActionId: String? = null,
    val actionBusy: Boolean = false,
    val actionFailed: Boolean = false,
    /** What you run in a week now, which the next plan starts from. */
    val baselineKm: Double = 0.0,
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    /** Predicted race times and the race goal, if there is one. */
    val race: RaceState = RaceState(),
    /** How well the last six weeks of running fit each distance; empty without a run in that time. */
    val shapes: List<RaceShape> = emptyList(),
    /** VO2max worked out from the pace and heart rate of recent runs; null with fewer than two such runs. */
    val vo2: Vo2Summary? = null,
    /** The phases from now to the race goal; null without a goal or when the race is not in the future. */
    val season: SeasonPlan? = null,
    /** Work-outs made in the app; they go to the watch together with the plan. */
    val ownWorkouts: List<OwnWorkout> = emptyList(),
)

/** Whether the app is locked right now (and whether the lock is on at all). */
data class LockUi(val enabled: Boolean = false, val locked: Boolean = false)
