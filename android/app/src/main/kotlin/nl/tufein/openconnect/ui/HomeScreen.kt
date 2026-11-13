package nl.tufein.openconnect.ui

import nl.tufein.openconnect.core.ChartMetric

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tufein.openconnect.R
import androidx.compose.foundation.layout.height
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    garmin: GarminUi,
    now: Instant,
    onOpenActivity: (String) -> Unit,
    onDemo: () -> Unit,
    onImport: () -> Unit,
    onWatch: () -> Unit,
    onGarmin: () -> Unit,
    onSync: () -> Unit,
    onTrophies: () -> Unit,
    onCoach: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    zone: ZoneId = ZoneId.systemDefault(),
    /** A tile was tapped: open that chart on the Health screen. */
    onOpenHealth: (ChartMetric) -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    /** False for someone who said at the first start that they have no Garmin: no sync status, no Garmin buttons. */
    useGarmin: Boolean = true,
    /** Opens the Oura tab; null for someone without a ring. */
    onOura: (() -> Unit)? = null,
) {
    val badge = syncBadge(garmin).takeIf { useGarmin }
    val metrics = OpenConnectColors.metrics
    val design = LocalDesign.current
    // Pulling down syncs the Garmin account, like in Garmin Connect; without one there is nothing to fetch.
    PullToRefreshBox(
        isRefreshing = useGarmin && garmin.sync is GarminSyncUi.Running,
        onRefresh = { if (!useGarmin) Unit else if (garmin.hasKey) onSync() else onGarmin() },
        modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Header(
                    now = now,
                    zone = zone,
                    badge = badge,
                    level = state.level,
                    onPill = { if (badge is SyncBadge.NoAccount || badge is SyncBadge.KeyRejected || badge is SyncBadge.Syncing) onGarmin() else onSync() },
                    onLevel = onTrophies,
                )
            }
            if (!state.loaded) return@LazyColumn
            val overview = state.overview
            // With data on screen the sync pill and the buttons at the bottom already lead to Garmin and the watch.
            if (useGarmin && (!state.hasData || overview == null)) item { ConnectCard(garmin.hasKey, onWatch, onGarmin) }
            if (!state.hasData || overview == null) {
                item { EmptyState(onDemo, onImport) }
                return@LazyColumn
            }
            val today = overview.today

            fun readiness() = item { Box(Modifier.tappable { onOpenHealth(ChartMetric.READINESS) }) { ReadinessCard(state.readiness) } }
            fun goalsAndLoad() {
                state.goalProgress?.let { progress -> item { Box(Modifier.tappable(onOpenInsights)) { GoalsCard(progress, state.goals) } } }
                state.load?.let { load -> item { Box(Modifier.tappable(onOpenActivities)) { LoadCard(load) } } }
            }
            fun stepsCard() = item {
                val goal = (today?.stepsGoal ?: 10_000).coerceAtLeast(1)
                val steps = today?.steps ?: 0
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
                            Text(stringResource(R.string.today), style = MaterialTheme.typography.titleMedium)
                            Fact(stringResource(R.string.distance), Fmt.km((today?.distanceM ?: 0.0)) + " km", metrics.distance)
                            Fact(stringResource(R.string.floors), (today?.floorsClimbed ?: 0).toString(), metrics.battery)
                            Fact(stringResource(R.string.active_kcal), (today?.activeKcal ?: 0).toString(), metrics.calories)
                            Fact(stringResource(R.string.intensity_minutes), (today?.intensityMinutes ?: 0).toString(), metrics.stress)
                        }
                    }
                }
            }

            val last7 = overview.trend.takeLast(7)
            // The four signals as tiles: today's value and a line of the last seven days. Each design places them differently.
            val signals = listOf<@Composable (Modifier) -> Unit>(
                { MetricTile(stringResource(R.string.resting_hr), today?.restingHr?.takeIf { it > 0 }?.toString() ?: "–", metrics.heart, it, "bpm", last7.map { r -> r.restingHr?.toDouble() }, onClick = { onOpenHealth(ChartMetric.RESTING_HR) }) },
                { MetricTile(stringResource(R.string.body_battery), today?.bodyBatteryHigh?.toString() ?: "–", metrics.battery, it, series = last7.map { r -> r.bodyBatteryHigh?.toDouble() }, onClick = { onOpenHealth(ChartMetric.BODY_BATTERY) }) },
                { MetricTile(stringResource(R.string.stress), today?.avgStress?.toString() ?: "–", metrics.stress, it, series = last7.map { r -> r.avgStress?.toDouble() }, onClick = { onOpenHealth(ChartMetric.STRESS) }) },
                { MetricTile(stringResource(R.string.sleep), state.sleep?.takeIf { it.durationS > 0 }?.let { s -> Fmt.hours(s.durationS) } ?: "–", metrics.sleep, it, "h", last7.map { r -> r.sleepHours }, onClick = { onOpenHealth(ChartMetric.SLEEP) }) },
            )
            fun signalGrid() {
                item { TileRow(signals[0], signals[1]) }
                item { TileRow(signals[2], signals[3]) }
            }

            when (design) {
                // Clean Pro: the readiness ring as the hero, then "My day" as one calm list.
                Design.CLEAN -> {
                    readiness()
                    item { SectionTitle(stringResource(R.string.home_my_day)) }
                    stepsCard()
                    signals.forEach { tile -> item { tile(Modifier.fillMaxWidth()) } }
                    goalsAndLoad()
                }
                // Dashboard Dark: the four signal tiles first, everything at a glance.
                Design.DASHBOARD -> {
                    signalGrid()
                    stepsCard()
                    readiness()
                    goalsAndLoad()
                }
                // Retro Pro: the order of earlier versions.
                Design.RETRO -> {
                    readiness()
                    goalsAndLoad()
                    stepsCard()
                    signalGrid()
                }
            }

            item {
                val week = overview.last7
                SectionTitle(stringResource(R.string.last_7_days))
                Spacer(Modifier.padding(top = 8.dp))
                TileRow(
                    { StatTile(stringResource(R.string.distance), Fmt.km(week.totalDistanceM), it, unit = "km", color = metrics.distance, onClick = onOpenActivities) },
                    { StatTile(stringResource(R.string.activities), week.activityCount.toString(), it, color = MaterialTheme.colorScheme.primary, onClick = onOpenActivities) },
                )
            }
            item {
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
            item {
                SectionTitle(stringResource(R.string.steps_30_days))
                Spacer(Modifier.padding(top = 8.dp))
                SurfaceCard {
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (useGarmin) {
                        OutlinedButton(onClick = onGarmin, Modifier.weight(1f)) { Text(stringResource(R.string.action_garmin_short), maxLines = 1) }
                        OutlinedButton(onClick = onWatch, Modifier.weight(1f)) { Text(stringResource(R.string.action_watch_short), maxLines = 1) }
                    }
                    if (onOura != null) {
                        OutlinedButton(onClick = onOura, Modifier.weight(1f)) { Text(stringResource(R.string.tab_oura), maxLines = 1) }
                    }
                    OutlinedButton(onClick = onCoach, Modifier.weight(1f)) { Text(stringResource(R.string.action_coach_short), maxLines = 1) }
                }
            }
            item { SectionTitle(stringResource(R.string.recent_activities)) }
            items(overview.recentActivities, key = { it.id }) { ActivityRow(it, onOpenActivity) }
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
    val hour = now.atZone(zone).hour
    val greeting = when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoMark(size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(greeting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            }
            if (level != null) LevelChip(level, onLevel)
        }
        if (badge != null) SyncPill(badge, now, onPill)
    }
}

/** "LV 4" like the level of a game character; it opens the trophies. */
@Composable
private fun LevelChip(level: PlayerLevel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.level_short, level.level), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.xp_short, level.xp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Fact(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 8.dp).size(8.dp).background(color, RoundedCornerShape(50)))
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
                OutlinedButton(onClick = onGarmin) { Text(stringResource(R.string.action_garmin)) }
                Button(onClick = onWatch) { Text(stringResource(R.string.action_watch)) }
            } else {
                Button(onClick = onGarmin) { Text(stringResource(R.string.action_garmin)) }
                OutlinedButton(onClick = onWatch) { Text(stringResource(R.string.action_watch)) }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RingGauge(
                progress = readiness.score / 100f,
                color = levelColor,
                description = stringResource(R.string.readiness_description, readiness.score),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(readiness.score.toString(), style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp), textAlign = TextAlign.Center)
                    Text("/100", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.readiness_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(readinessLabel(readiness.level)), style = MaterialTheme.typography.titleLarge, color = levelColor)
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
            }
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
    SurfaceCard(modifier.clickable { onOpen(activity.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SportBadge(activity.sport)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(sportLabel(activity.sport)) + " · " + Fmt.dateTime(activity.startTime),
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
            SegmentBar(goal.fraction.toFloat(), color, Modifier.fillMaxWidth().height(8.dp))
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
        )
        Text(
            stringResource(R.string.load_summary, Fmt.thousands(Math.rint(load.acute).toInt()), Fmt.thousands(Math.rint(load.chronicWeekly).toInt())),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.load_note), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
