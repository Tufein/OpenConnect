package nl.tufein.openconnect.ui

import nl.tufein.openconnect.core.ChartMetric
import nl.tufein.openconnect.core.DayAge
import nl.tufein.openconnect.core.DayFreshness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import androidx.compose.foundation.layout.height
import nl.tufein.openconnect.core.DailySummary
import nl.tufein.openconnect.core.JournalBook
import nl.tufein.openconnect.core.LoadStatus
import nl.tufein.openconnect.core.TrainingLoad
import nl.tufein.openconnect.core.WeeklyGoals
import nl.tufein.openconnect.core.WeeklyGoalProgress
import nl.tufein.openconnect.core.Activity
import nl.tufein.openconnect.core.PlayerLevel
import nl.tufein.openconnect.core.Readiness
import nl.tufein.openconnect.core.ReadinessLevel
import nl.tufein.openconnect.core.ReadinessPart
import nl.tufein.openconnect.garmin.GarminSyncUi
import nl.tufein.openconnect.garmin.GarminUi
import nl.tufein.openconnect.garmin.SyncBadge
import nl.tufein.openconnect.garmin.syncBadge
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    garmin: GarminUi,
    now: Instant,
    /** The charge the watch and the ring last reported; both sit on the front page too. */
    battery: BatteriesUi = BatteriesUi(),
    onOpenActivity: (String) -> Unit,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    onWatch: () -> Unit,
    onGarmin: () -> Unit,
    onSync: () -> Unit,
    onTrophies: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    zone: ZoneId = ZoneId.systemDefault(),
    /** A tile was tapped: open that chart on the Health screen. */
    onOpenHealth: (ChartMetric) -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    /** The advice card was tapped: open the Form tab. */
    onOpenForm: () -> Unit = {},
    /** A day was marked or unmarked in the journal card. */
    onToggleJournal: (java.time.LocalDate, nl.tufein.openconnect.core.JournalTag) -> Unit = { _, _ -> },
    /** False for someone who said at the first start that they have no Garmin: no sync status, no Garmin buttons. */
    useGarmin: Boolean = true,
    /** Opens the Oura tab; null for someone without a ring. */
    onOura: (() -> Unit)? = null,
) {
    val editorial = LocalStyle.current == Style.EDITORIAL
    val badge = syncBadge(garmin).takeIf { useGarmin }
    val metrics = OpenConnectColors.metrics
    val gutter = if (editorial) 20.dp else 16.dp
    // The day itself stays open; the month chart and the activity list are one tap away, which keeps the first screen calm.
    var showMonth by rememberSaveable { mutableStateOf(false) }
    var showRecent by rememberSaveable { mutableStateOf(false) }
    var showTrends by rememberSaveable { mutableStateOf(false) }
    // Remembered per sync, not forever: after the next successful sync the card is worth seeing again.
    var syncToastHiddenFor by rememberSaveable { mutableStateOf(0L) }

    // The five orbs over the photo: real values from the latest day; a dash keeps the row's rhythm when a source is silent.
    val orbToday = state.overview?.today
    val orbSleep = state.sleep?.takeIf { it.durationS > 0 }
    val stepGoal = (orbToday?.stepsGoal ?: 10_000).coerceAtLeast(1)
    val activityPct = orbToday?.let { Math.rint(it.steps * 100.0 / stepGoal).toInt().coerceIn(0, 100) }
    val readinessScore = state.readiness?.score
    val sleepOrbValue = orbSleep?.score?.toString() ?: orbSleep?.let { Fmt.oneDecimal(it.hours) }
    val orbs = listOf(
        OrbValue(
            label = stringResource(R.string.readiness_title),
            value = readinessScore?.toString() ?: "–",
            progress = (readinessScore ?: 0) / 100f,
            color = metrics.readiness,
            description = readinessScore?.let { stringResource(R.string.readiness_description, it) },
            onClick = { onOpenHealth(ChartMetric.READINESS) },
        ),
        OrbValue(
            label = stringResource(R.string.sleep),
            value = sleepOrbValue ?: "–",
            progress = orbSleep?.let { s -> s.score?.let { it / 100f } ?: (s.durationS / (8f * 3600f)) } ?: 0f,
            color = metrics.sleep,
            description = sleepOrbValue?.let { stringResource(R.string.editorial_orb_sleep_desc, it) },
            onClick = { onOpenHealth(ChartMetric.SLEEP) },
        ),
        OrbValue(
            label = stringResource(R.string.oura_activity),
            value = activityPct?.toString() ?: "–",
            progress = (activityPct ?: 0) / 100f,
            color = metrics.steps,
            description = activityPct?.let { stringResource(R.string.editorial_orb_activity_desc, it) },
            onClick = { onOpenHealth(ChartMetric.STEPS) },
        ),
        OrbValue(
            label = stringResource(R.string.editorial_orb_heart),
            value = orbToday?.restingHr?.takeIf { it > 0 }?.toString() ?: "–",
            progress = orbToday?.restingHr?.takeIf { it > 0 }?.let { hr -> ((100 - hr) / 60f).coerceIn(0f, 1f) } ?: 0f,
            color = metrics.heart,
            description = orbToday?.restingHr?.takeIf { it > 0 }?.let { stringResource(R.string.editorial_orb_heart_desc, it) },
            onClick = { onOpenHealth(ChartMetric.RESTING_HR) },
        ),
        OrbValue(
            label = stringResource(R.string.stress),
            value = orbToday?.avgStress?.toString() ?: "–",
            progress = orbToday?.avgStress?.let { stress -> ((100 - stress) / 100f).coerceIn(0f, 1f) } ?: 0f,
            color = metrics.stress,
            description = orbToday?.avgStress?.let { stringResource(R.string.editorial_orb_stress_desc, it) },
            onClick = { onOpenHealth(ChartMetric.STRESS) },
        ),
    )

    // Pulling down syncs the Garmin account, like in Garmin Connect; without one there is nothing to fetch.
    PullToRefreshBox(
        isRefreshing = useGarmin && garmin.sync is GarminSyncUi.Running,
        onRefresh = { if (!useGarmin) Unit else if (garmin.hasKey) onSync() else onGarmin() },
        modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = if (editorial) 0.dp else 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (editorial) 18.dp else 14.dp),
        ) {
            /** One item at the page's gutter; the hero is the only thing that runs edge to edge. */
            fun block(key: String? = null, content: @Composable () -> Unit) {
                val wrapped: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { content() } }
                if (key == null) item { wrapped() } else item(key = key) { wrapped() }
            }

            if (editorial) {
                item(key = "home-hero") {
                    EditorialHero(
                        eyebrow = Fmt.date(now.atZone(zone).toLocalDate()),
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(greetingFor(now, zone)),
                        // A new install gets a shorter hero, so the way in (demo or import) is on the first screen.
                        height = if (state.hasData) 360.dp else 280.dp,
                        trailing = { state.level?.let { LevelChip(it, onTrophies) } },
                        footer = { MetricOrbRow(orbs, onPhoto = true) },
                    )
                }
                val fresh = badge as? SyncBadge.Fresh
                if (fresh != null && fresh.lastSync.toEpochMilli() != syncToastHiddenFor) {
                    block("home-status-toast") {
                        StatusToast(
                            title = stringResource(R.string.editorial_sync_done),
                            body = stringResource(R.string.sync_fresh, agoText(fresh.lastSync, now)),
                            actionLabel = stringResource(R.string.editorial_sync_again),
                            onAction = onSync,
                            onDismiss = { syncToastHiddenFor = fresh.lastSync.toEpochMilli() },
                        )
                    }
                } else if (badge != null) {
                    block("home-sync") {
                        SyncPill(
                            badge,
                            now,
                            { if (badge is SyncBadge.NoAccount || badge is SyncBadge.KeyRejected || badge is SyncBadge.Syncing) onGarmin() else onSync() },
                        )
                    }
                }
            } else {
                block {
                    Header(
                        now = now,
                        zone = zone,
                        badge = badge,
                        level = state.level,
                        onPill = { if (badge is SyncBadge.NoAccount || badge is SyncBadge.KeyRejected || badge is SyncBadge.Syncing) onGarmin() else onSync() },
                        onLevel = onTrophies,
                    )
                }
            }
            // On an empty front page the way in (demo data or an import) sits directly under the hero, so it is the first
            // thing a new install sees instead of the bottom of a long page. The devices stay just below it.
            val noData = state.loaded && (!state.hasData || state.overview == null)
            if (editorial && noData) block("home-empty-early") { EmptyState(onDemo, onImport) }
            // The charge of the devices themselves, high on the front page: a dead watch or ring is worth noticing early.
            if (useGarmin || onOura != null) block("home-battery") {
                BatteryCard(battery, showWatch = useGarmin, showRing = onOura != null, onOpenOura = onOura)
            }
            if (!state.loaded) return@LazyColumn
            val overview = state.overview
            // With data on screen the sync pill and the buttons at the bottom already lead to Garmin and the watch.
            if (useGarmin && (!state.hasData || overview == null)) block("home-connect") { ConnectCard(garmin.hasKey, onWatch, onGarmin) }
            if (!state.hasData || overview == null) {
                if (!editorial) block("home-empty") { EmptyState(onDemo, onImport) }
                return@LazyColumn
            }
            val today = overview.today
            // "Today" is the latest day any source gave. When the watch has not reached the account yet that is an older day,
            // and the card must say so, or its numbers look wrong next to the watch.
            val freshness = DayFreshness.of(today?.date, now.atZone(zone).toLocalDate())

            fun readiness() = block("home-readiness-detail") {
                Foldable("home-readiness") { Box(Modifier.tappable { onOpenHealth(ChartMetric.READINESS) }) { ReadinessCard(state.readiness) } }
            }
            fun goalsAndLoad() {
                state.goalProgress?.let { progress -> block("home-goals") { Foldable("home-goals-fold") { Box(Modifier.tappable(onOpenInsights)) { GoalsCard(progress, state.goals) } } } }
                state.load?.let { load -> block("home-load") { Foldable("home-load-fold") { Box(Modifier.tappable(onOpenActivities)) { LoadCard(load) } } } }
            }
            fun stepsCard() = block("home-steps") {
                val goal = (today?.stepsGoal ?: 10_000).coerceAtLeast(1)
                val steps = today?.steps ?: 0
                Foldable("home-steps") {
                    SurfaceCard(Modifier.tappable { onOpenHealth(ChartMetric.STEPS) }, accent = metrics.steps) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            RingGauge(
                                progress = steps.toFloat() / goal,
                                color = metrics.steps,
                                description = stringResource(R.string.steps_of_goal, Fmt.thousands(steps), Fmt.thousands(goal)),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Fmt.thousands(steps), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.of_goal, Fmt.thousands(goal)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(dayTitle(freshness), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("home-day"))
                                Fact(stringResource(R.string.distance), Fmt.km((today?.distanceM ?: 0.0)) + " km", metrics.distance)
                                Fact(stringResource(R.string.floors), (today?.floorsClimbed ?: 0).toString(), metrics.battery)
                                Fact(stringResource(R.string.active_kcal), (today?.activeKcal ?: 0).toString(), metrics.calories)
                                Fact(stringResource(R.string.intensity_minutes), (today?.intensityMinutes ?: 0).toString(), metrics.stress)
                            }
                        }
                    }
                }
            }

            val last7 = overview.trend.takeLast(7)
            // The four signals as tiles: today's value and a line of the last seven days.
            val signals = listOf<@Composable (Modifier) -> Unit>(
                { MetricTile(stringResource(R.string.resting_hr), today?.restingHr?.takeIf { it > 0 }?.toString() ?: "–", metrics.heart, it, "bpm", last7.map { r -> r.restingHr?.toDouble() }, onClick = { onOpenHealth(ChartMetric.RESTING_HR) }, showSeries = false) },
                { MetricTile(stringResource(R.string.body_battery), today?.bodyBatteryHigh?.toString() ?: "–", metrics.battery, it, unit = today?.bodyBatteryHigh?.let { stringResource(R.string.unit_highest_of_day) }, series = last7.map { r -> r.bodyBatteryHigh?.toDouble() }, onClick = { onOpenHealth(ChartMetric.BODY_BATTERY) }, showSeries = false) },
                { MetricTile(stringResource(R.string.stress), today?.avgStress?.toString() ?: "–", metrics.stress, it, series = last7.map { r -> r.avgStress?.toDouble() }, onClick = { onOpenHealth(ChartMetric.STRESS) }, showSeries = false) },
                { MetricTile(stringResource(R.string.sleep), state.sleep?.takeIf { it.durationS > 0 }?.let { s -> Fmt.hours(s.durationS) } ?: "–", metrics.sleep, it, "h", last7.map { r -> r.sleepHours }, onClick = { onOpenHealth(ChartMetric.SLEEP) }, showSeries = false) },
            )
            fun signalGrid() {
                block("home-signal-0") { TileRow(signals[0], signals[1]) }
                block("home-signal-1") { TileRow(signals[2], signals[3]) }
            }

            // One order: how ready you are, today's four signals, the day so far, then the week.
            if (freshness.isStale) block("home-stale") { StaleDayNotice(freshness) }
            if (editorial) {
                block("home-calories") { EditorialCaloriesCard(today) }
                block("home-readiness-summary") { EditorialReadinessCard(state.readiness) { onOpenHealth(ChartMetric.READINESS) } }
                block("home-sleep-summary") { EditorialSleepCard(state.sleep, today) { onOpenHealth(ChartMetric.SLEEP) } }
                block("home-checkin") {
                    EditorialCheckInCard(state.journal, now.atZone(zone).toLocalDate(), onToggleJournal)
                }
            }
            readiness()
            state.advice?.let { advice -> block("home-advice") { Foldable("home-advice") { Box(Modifier.tappable(onOpenForm)) { AdviceCard(advice) } } } }
            state.warning?.let { warning -> block("home-warning") { Foldable("home-warning") { WarningCard(warning) } } }
            state.restingHrTrend?.let { t -> block("home-rhr") { Foldable("home-rhr") { RestingHrTrendCard(t) } } }
            signalGrid()
            block("home-trends") {
                val trendRows = listOf(
                    stringResource(R.string.resting_hr) to (last7.map { r -> r.restingHr?.toDouble() } to metrics.heart),
                    stringResource(R.string.body_battery) to (last7.map { r -> r.bodyBatteryHigh?.toDouble() } to metrics.battery),
                    stringResource(R.string.stress) to (last7.map { r -> r.avgStress?.toDouble() } to metrics.stress),
                    stringResource(R.string.sleep) to (last7.map { r -> r.sleepHours } to metrics.sleep),
                )
                FoldSection(
                    title = stringResource(R.string.trends_7_days),
                    summary = "",
                    open = showTrends,
                    onToggle = { showTrends = !showTrends },
                ) {
                    for ((label, row) in trendRows) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Sparkline(row.first, row.second, Modifier.fillMaxWidth().height(40.dp))
                    }
                }
            }
            stepsCard()
            state.stepGoalWeek?.let { w -> block("home-step-goal-week") { StepGoalWeekCard(w) } }
            goalsAndLoad()
            state.sleepNeed?.let { need -> block("home-sleep-need") { Foldable("home-sleep-need") { SleepNeedCard(need) } } }
            state.sleepConsistency?.let { c -> block("home-sleep-consistency") { Foldable("home-sleep-consistency") { SleepConsistencyCard(c) } } }
            block("home-journal") { Foldable("home-journal") { JournalCard(state.journal, now.atZone(zone).toLocalDate(), onToggleJournal) } }

            block("home-last7") {
                val week = overview.last7
                CollapsibleSection("home-last7", stringResource(R.string.last_7_days)) {
                    Spacer(Modifier.padding(top = 8.dp))
                    TileRow(
                        { StatTile(stringResource(R.string.distance), Fmt.km(week.totalDistanceM), it, unit = "km", color = metrics.distance, onClick = onOpenActivities) },
                        { StatTile(stringResource(R.string.activities), week.activityCount.toString(), it, color = MaterialTheme.colorScheme.primary, onClick = onOpenActivities) },
                    )
                }
            }
            block("home-steps-stats") {
                val change = overview.stepsChangePct
                val week = overview.last7
                TileRow(
                    { StatTile(stringResource(R.string.avg_steps), week.avgSteps?.let { Fmt.thousands(Math.rint(it).toInt()) } ?: "–", it, color = metrics.steps, onClick = { onOpenHealth(ChartMetric.STEPS) }) },
                    {
                        StatTile(
                            stringResource(R.string.steps_vs_previous),
                            change?.let { (if (it >= 0) "+" else "") + Fmt.oneDecimal(it) + "%" } ?: "–",
                            it,
                            color = metrics.calories,
                            onClick = { onOpenHealth(ChartMetric.STEPS) },
                        )
                    },
                )
            }
            block("home-month") {
                FoldSection(
                    title = stringResource(R.string.steps_30_days),
                    summary = overview.stepsChangePct?.let { (if (it >= 0) "+" else "") + Fmt.oneDecimal(it) + "%" }.orEmpty(),
                    open = showMonth,
                    onToggle = { showMonth = !showMonth },
                ) {
                    BarChart(
                        values = overview.trend.map { it.steps?.toDouble() },
                        goal = 10_000.0,
                        color = metrics.steps,
                        description = stringResource(R.string.steps_30_days),
                        labels = overview.trend.map { Fmt.shortDate(it.date) },
                        format = { insightValue(ChartMetric.STEPS, it) },
                    )
                }
            }
            block("home-recent") {
                FoldSection(
                    title = stringResource(R.string.recent_activities),
                    summary = overview.recentActivities.size.toString(),
                    open = showRecent,
                    onToggle = { showRecent = !showRecent },
                ) {
                    for (activity in overview.recentActivities.take(6)) ActivityRow(activity, onOpenActivity)
                }
            }
        }
    }
}

/** The greeting that goes with the hour, shared by the editorial header and the older one. */
private fun greetingFor(now: Instant, zone: ZoneId): Int = when (now.atZone(zone).hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    in 18..22 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

/** The large arc on Home: today's active calories, with no invented goal, just a round reference that leaves room above. */
@Composable
private fun EditorialCaloriesCard(today: DailySummary?) {
    val kcal = today?.activeKcal ?: 0
    val max = maxOf(120, ((kcal + 119) / 120) * 120)
    EditorialCard {
        ArcGauge(
            value = kcal,
            max = max,
            label = stringResource(R.string.editorial_active_calories),
            color = OpenConnectColors.metrics.calories,
        )
        Text(
            stringResource(R.string.editorial_active_calories_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/** A page of readiness: the score, a serif line that says what kind of day it is, and the strongest and weakest signal. */
@Composable
private fun EditorialReadinessCard(readiness: Readiness?, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (readiness == null) {
        EditorialCard {
            EditorialSectionTitle(stringResource(R.string.readiness_title))
            Text(stringResource(R.string.editorial_readiness_empty_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.readiness_missing), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            EditorialPillButton(stringResource(R.string.editorial_learn_more), onOpen, Modifier.fillMaxWidth())
        }
        return
    }
    val tint = when (readiness.level) {
        ReadinessLevel.LOW -> colors.error
        ReadinessLevel.MODERATE -> OpenConnectColors.metrics.stress
        ReadinessLevel.GOOD, ReadinessLevel.EXCELLENT -> OpenConnectColors.metrics.readiness
    }
    val title = when (readiness.level) {
        ReadinessLevel.LOW -> R.string.editorial_readiness_low_title
        ReadinessLevel.MODERATE -> R.string.editorial_readiness_mid_title
        ReadinessLevel.GOOD -> R.string.editorial_readiness_good_title
        ReadinessLevel.EXCELLENT -> R.string.editorial_readiness_high_title
    }
    val body = when (readiness.level) {
        ReadinessLevel.LOW -> R.string.editorial_readiness_low_body
        ReadinessLevel.MODERATE -> R.string.editorial_readiness_mid_body
        ReadinessLevel.GOOD -> R.string.editorial_readiness_good_body
        ReadinessLevel.EXCELLENT -> R.string.editorial_readiness_high_body
    }
    EditorialCard(accent = tint) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EditorialSectionTitle(stringResource(R.string.readiness_title))
                Text(stringResource(readinessLabel(readiness.level)), style = MaterialTheme.typography.labelMedium, color = tint)
            }
            Text(readiness.score.toString(), style = MaterialTheme.typography.displayMedium, color = tint)
            Text(
                "/100",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            EditorialStat(stringResource(R.string.editorial_strongest), stringResource(readinessPartLabel(readiness.strongest.part)), Modifier.weight(1f))
            EditorialStat(stringResource(R.string.editorial_weakest), stringResource(readinessPartLabel(readiness.weakest.part)), Modifier.weight(1f))
        }
        EditorialRule()
        EditorialPillButton(stringResource(R.string.editorial_learn_more), onOpen, Modifier.fillMaxWidth())
    }
}

/** The night in the editorial language: the score, a short reading of it, the stages and two numbers. */
@Composable
private fun EditorialSleepCard(sleep: nl.tufein.openconnect.core.SleepSummary?, today: DailySummary?, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    EditorialCard(accent = OpenConnectColors.metrics.sleep) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EditorialSectionTitle(stringResource(R.string.sleep))
                Text(stringResource(R.string.oura_last_night), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Text(
                sleep?.score?.toString() ?: sleep?.let { Fmt.hours(it.durationS) } ?: "–",
                style = MaterialTheme.typography.displayMedium,
                color = OpenConnectColors.metrics.sleep,
            )
        }
        if (sleep == null || sleep.durationS <= 0) {
            Text(stringResource(R.string.editorial_sleep_empty_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.editorial_sleep_empty_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            EditorialPillButton(stringResource(R.string.editorial_open_sleep), onOpen, Modifier.fillMaxWidth())
            return@EditorialCard
        }
        val score = sleep.score
        val hours = sleep.hours
        val band = when {
            score != null && score >= 80 -> 2
            score != null && score >= 60 -> 1
            score == null && hours >= 7 -> 2
            score == null && hours >= 6 -> 1
            else -> 0
        }
        Text(
            stringResource(
                when (band) {
                    2 -> R.string.editorial_sleep_good_title
                    1 -> R.string.editorial_sleep_mid_title
                    else -> R.string.editorial_sleep_low_title
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(
                when (band) {
                    2 -> R.string.editorial_sleep_good_body
                    1 -> R.string.editorial_sleep_mid_body
                    else -> R.string.editorial_sleep_low_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        SleepStageBar(
            deepS = sleep.deepS,
            lightS = sleep.lightS,
            remS = sleep.remS,
            awakeS = sleep.awakeS,
            description = stringResource(R.string.editorial_sleep_stages),
        )
        Text(
            stringResource(R.string.editorial_sleep_summary, Fmt.hours(sleep.durationS), Fmt.hours(sleep.deepS), Fmt.hours(sleep.remS)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            EditorialStat(stringResource(R.string.editorial_asleep), Fmt.hours(sleep.durationS), Modifier.weight(1f))
            EditorialStat(
                stringResource(R.string.resting_hr),
                today?.restingHr?.takeIf { it > 0 }?.let { "$it bpm" } ?: "–",
                Modifier.weight(1f),
            )
        }
        EditorialRule()
        EditorialPillButton(stringResource(R.string.editorial_open_sleep), onOpen, Modifier.fillMaxWidth())
    }
}

/** The check-in's own words for the journal tags, so the card does not repeat the journal card further down. */
private fun checkInLabel(tag: nl.tufein.openconnect.core.JournalTag): Int = when (tag) {
    nl.tufein.openconnect.core.JournalTag.ALCOHOL -> R.string.editorial_checkin_drink
    nl.tufein.openconnect.core.JournalTag.LATE_MEAL -> R.string.editorial_checkin_late_meal
    nl.tufein.openconnect.core.JournalTag.CAFFEINE -> R.string.editorial_checkin_caffeine
    nl.tufein.openconnect.core.JournalTag.STRESS -> R.string.editorial_checkin_stress
    nl.tufein.openconnect.core.JournalTag.TRAVEL -> R.string.editorial_checkin_travel
    nl.tufein.openconnect.core.JournalTag.ILL -> R.string.editorial_checkin_ill
}

/** The editorial check-in, wired to the journal: a tap marks today with that tag, another tap removes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorialCheckInCard(book: JournalBook, day: java.time.LocalDate, onToggle: (java.time.LocalDate, nl.tufein.openconnect.core.JournalTag) -> Unit) {
    val colors = MaterialTheme.colorScheme
    EditorialCard {
        EditorialSectionTitle(stringResource(R.string.editorial_checkin_title))
        Text(stringResource(R.string.editorial_checkin_question), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.editorial_checkin_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (tag in nl.tufein.openconnect.core.JournalTag.entries) {
                PixelChip(
                    selected = tag in book.tags(day),
                    onClick = { onToggle(day, tag) },
                    label = { Text(stringResource(checkInLabel(tag))) },
                )
            }
        }
    }
}

@Composable
private fun Header(
    now: Instant,
    zone: ZoneId,
    badge: SyncBadge?,
    level: PlayerLevel?,
    onPill: () -> Unit,
    onLevel: () -> Unit,
) {
    val greeting = greetingFor(now, zone)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoMark(size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(greeting), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            }
            if (level != null) LevelChip(level, onLevel)
        }
        if (badge != null) SyncPill(badge, now, onPill)
    }
}

/** "LV 4" like the level of a game character, with its experience points; it opens the trophies. */
@Composable
private fun LevelChip(level: PlayerLevel, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val editorial = LocalStyle.current == Style.EDITORIAL
    val shape = if (editorial) PillShape else MaterialTheme.shapes.small
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .hardShadow(shadowColor(), shape)
            .background(if (editorial) colors.surfaceVariant else colors.secondary, shape)
            .border(if (editorial) 1.dp else lineWidth(), if (editorial) colors.outlineVariant else inkColor(), shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (editorial) 14.dp else 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val text = if (editorial) colors.onSurface else colors.onSecondary
        Text(stringResource(R.string.level_short, level.level), style = MaterialTheme.typography.titleLarge, color = text)
        Text(stringResource(R.string.xp_short, level.xp), style = MaterialTheme.typography.labelSmall, color = text)
    }
}

@Composable
private fun Fact(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, Modifier.padding(end = 8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ConnectCard(hasGarmin: Boolean, onWatch: () -> Unit, onGarmin: () -> Unit) {
    SurfaceCard(accent = MaterialTheme.colorScheme.tertiary) {
        Text(stringResource(R.string.watch_card_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.watch_card_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasGarmin) {
                SecondaryButton(onClick = onGarmin) { Text(stringResource(R.string.action_garmin)) }
                PrimaryButton(onClick = onWatch) { Text(stringResource(R.string.action_watch)) }
            } else {
                PrimaryButton(onClick = onGarmin) { Text(stringResource(R.string.action_garmin)) }
                SecondaryButton(onClick = onWatch) { Text(stringResource(R.string.action_watch)) }
            }
        }
    }
}

@Composable
private fun ReadinessCard(readiness: Readiness?) {
    val colors = MaterialTheme.colorScheme
    val metric = OpenConnectColors.metrics.readiness
    if (readiness == null) {
        SurfaceCard {
            Text(stringResource(R.string.readiness_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.readiness_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        return
    }
    val levelColor = when (readiness.level) {
        ReadinessLevel.LOW -> colors.error
        ReadinessLevel.MODERATE -> OpenConnectColors.metrics.stress
        ReadinessLevel.GOOD, ReadinessLevel.EXCELLENT -> metric
    }
    SurfaceCard(accent = levelColor) {
        // The score is the "health bar" of the day: a big pixel number over a bar of 25 blocks.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(readiness.score.toString(), style = MaterialTheme.typography.displayLarge, color = levelColor)
            Text("/100", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.readiness_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(readinessLabel(readiness.level)), style = MaterialTheme.typography.titleLarge, color = levelColor)
            }
        }
        val description = stringResource(R.string.readiness_description, readiness.score)
        SegmentBar(
            readiness.score / 100f, levelColor,
            Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = description },
            segments = 25,
        )
        Text(
            stringResource(R.string.readiness_best, stringResource(readinessPartLabel(readiness.strongest.part))),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (readiness.components.size > 2) {
            Text(
                stringResource(R.string.readiness_worst, stringResource(readinessPartLabel(readiness.weakest.part))),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.readiness_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private fun readinessLabel(level: ReadinessLevel): Int = when (level) {
    ReadinessLevel.LOW -> R.string.readiness_low
    ReadinessLevel.MODERATE -> R.string.readiness_moderate
    ReadinessLevel.GOOD -> R.string.readiness_good
    ReadinessLevel.EXCELLENT -> R.string.readiness_excellent
}

private fun readinessPartLabel(part: ReadinessPart): Int = when (part) {
    ReadinessPart.SLEEP -> R.string.sleep
    ReadinessPart.BODY_BATTERY -> R.string.body_battery
    ReadinessPart.HRV -> R.string.hrv
    ReadinessPart.RESTING_HR -> R.string.resting_hr
    ReadinessPart.STRESS -> R.string.stress
}

@Composable
fun ActivityRow(activity: Activity, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.clickable(role = Role.Button) { onOpen(activity.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SportBadge(activity.sport)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                // A workout that only the Oura Ring or the WHOOP band recorded says so in the list (v0.43.0, v0.49.0).
                val band = nl.tufein.openconnect.core.BandSource.entries.firstOrNull { it.source == activity.source }
                Text(
                    stringResource(sportLabel(activity.sport)) + " · " + Fmt.dateTime(activity.startTime) + if (band != null) " · " + band.device else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val summary = buildList {
                    if (activity.distanceM > 0) add(Fmt.km(activity.distanceM) + " km")
                    add(Fmt.duration(activity.durationS))
                    activity.paceSPerKm?.takeIf { activity.sport.usesPace() }?.let { add(Fmt.pace(it) + " /km") }
                    activity.avgHr?.let { add("$it bpm") }
                }.joinToString("  ·  ")
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

fun nl.tufein.openconnect.core.Sport.usesPace(): Boolean = this in setOf(
    nl.tufein.openconnect.core.Sport.RUNNING,
    nl.tufein.openconnect.core.Sport.WALKING,
    nl.tufein.openconnect.core.Sport.HIKING,
)

@Composable
private fun GoalsCard(progress: WeeklyGoalProgress, goals: WeeklyGoals) {
    val metrics = OpenConnectColors.metrics
    val rows = listOf(
        Triple(R.string.goal_intensity, progress.intensity, metrics.calories),
        Triple(R.string.goal_distance, progress.distance, metrics.distance),
        Triple(R.string.goal_activities, progress.activities, metrics.steps),
    ).filter { it.second.goal > 0 }
    SurfaceCard {
        SectionTitle(stringResource(R.string.goals_title))
        if (rows.isEmpty()) {
            Text(stringResource(R.string.goals_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SurfaceCard
        }
        for ((label, goal, color) in rows) {
            val unit = when (label) {
                R.string.goal_intensity -> " min"
                R.string.goal_distance -> " km"
                else -> ""
            }
            val done = if (label == R.string.goal_distance) Fmt.oneDecimal(goal.done) else Math.rint(goal.done).toInt().toString()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.goal_value, done, "${goal.goal}$unit"),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (goal.reached) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SegmentBar(goal.fraction.toFloat(), color, Modifier.fillMaxWidth().height(10.dp))
        }
        val active = rows.size
        Text(
            stringResource(R.string.goals_reached, progress.reachedCount, active),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadCard(load: TrainingLoad) {
    val colors = MaterialTheme.colorScheme
    val (label, text, color) = when (load.status) {
        LoadStatus.LIGHT -> Triple(R.string.load_light, R.string.load_light_text, OpenConnectColors.metrics.distance)
        LoadStatus.STEADY -> Triple(R.string.load_steady, R.string.load_steady_text, OpenConnectColors.metrics.steps)
        LoadStatus.HEAVY -> Triple(R.string.load_heavy, R.string.load_heavy_text, OpenConnectColors.metrics.stress)
        LoadStatus.OVERREACHING -> Triple(R.string.load_overreaching, R.string.load_overreaching_text, colors.error)
    }
    SurfaceCard(accent = color) {
        SectionTitle(stringResource(R.string.load_title))
        Text(stringResource(label), style = MaterialTheme.typography.titleLarge, color = color)
        BarChart(
            values = load.weeks.map { it.second },
            description = stringResource(R.string.load_title),
            color = color,
            goal = load.chronicWeekly,
            height = 90.dp,
            labels = load.weeks.map { Fmt.shortDate(it.first) },
            format = { Fmt.thousands(Math.rint(it).toInt()) },
        )
        Text(
            stringResource(R.string.load_summary, Fmt.thousands(Math.rint(load.acute).toInt()), Fmt.thousands(Math.rint(load.chronicWeekly).toInt())),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.load_note), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

/** "Today", "Yesterday" or the date, for the card that shows the latest day with data. */
@Composable
private fun dayTitle(f: DayFreshness): String = when (f.age) {
    DayAge.YESTERDAY -> stringResource(R.string.day_yesterday)
    DayAge.EARLIER -> Fmt.date(f.date!!)
    else -> stringResource(R.string.today)
}

/** Says why the numbers may differ from the watch: they belong to an earlier day, because today's has not arrived. */
@Composable
private fun StaleDayNotice(f: DayFreshness) {
    SurfaceCard(accent = OpenConnectColors.metrics.stress) {
        Text(
            if (f.age == DayAge.YESTERDAY) stringResource(R.string.home_stale_yesterday) else stringResource(R.string.home_stale_earlier, Fmt.date(f.date!!)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
