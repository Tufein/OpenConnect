package nl.tufein.openconnect.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.health.HealthAccess
import nl.tufein.openconnect.core.hume.HumeDay
import nl.tufein.openconnect.core.hume.HumeOrigins
import nl.tufein.openconnect.hume.HumeProblem
import nl.tufein.openconnect.hume.HumeUi

/**
 * The Hume tab (v0.50.0), shown only to someone who chose the Hume Band. Hume has no connection of its own: its app writes
 * to Health Connect and this reads it from there, only what that app wrote. On top the band's last night, steps and
 * heart rate, kept apart from the watch's and the other bands'; below the link: what Health Connect allows, which app wrote
 * the band's data, and the sync. The texts for the last sync, the schedule and the average are the Oura screen's.
 */
@Composable
fun HumeScreen(
    ui: HumeUi,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onChooseOrigin: (String?) -> Unit,
    onForget: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { onRefresh() }
    // Ask Health Connect again every time the tab opens: the person may have changed the permissions in the system settings.
    LaunchedEffect(Unit) { onRefresh() }
    val metrics = OpenConnectColors.metrics
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val positions = remember { HashMap<String, Int>() }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var index = 0
        fun section(key: String, content: @Composable LazyItemScope.() -> Unit) {
            positions[key] = index++
            item(key = key, content = content)
        }
        fun scrollTo(key: String) {
            positions[key]?.let { scope.launch { list.animateScrollToItem(it) } }
        }
        section("title") {
            Text(stringResource(R.string.hume_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (ui.days.isNotEmpty()) {
            humeDashboard(ui.days, ::section, ::scrollTo, metrics)
        } else {
            section("intro") {
                Text(stringResource(if (ui.ready) R.string.hume_no_days else R.string.hume_intro), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (ui.busy) item { PixelProgress(Modifier.fillMaxWidth()) }
        ui.problem?.let { problem ->
            item(key = "problem") {
                Text(stringResource(humeProblemText(problem)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (ui.access) {
            HealthAccess.UNAVAILABLE -> item(key = "unavailable") {
                Text(stringResource(R.string.hume_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HealthAccess.NEEDS_PERMISSION -> item(key = "allow") {
                FoldableCard("hume-allow", stringResource(R.string.hume_allow_title)) {
                    Text(stringResource(R.string.hume_allow_body), style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(onClick = { permission.launch(ui.permissions) }, enabled = ui.permissions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hume_allow))
                    }
                }
            }
            HealthAccess.READY -> {
                item(key = "link") {
                    FoldableCard("hume-connected", stringResource(R.string.oura_connected_title), accent = OpenConnectColors.metrics.readiness) {
                        Text(
                            ui.lastSync?.let { stringResource(R.string.oura_last_sync, Fmt.dateTime(it)) } ?: stringResource(R.string.oura_never_synced),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (ui.using.isNotEmpty()) {
                            Text(
                                stringResource(R.string.hume_origin_using, ui.using.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ui.last?.let { r ->
                            Text(
                                stringResource(R.string.oura_report, r.daysUpdated, r.nightsUpdated, r.workoutsAdded),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.hume_schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryButton(onClick = onSync, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.oura_sync_now)) }
                    }
                }
                item(key = "origin") {
                    FoldableCard("hume-origin", stringResource(R.string.hume_origin_title)) {
                        Text(stringResource(R.string.hume_origin_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // The apps that wrote data: the one in use, and when the band's was not found, every candidate.
                        val choices = (ui.found + listOfNotNull(ui.origin)).toSortedSet()
                        OriginChoice(stringResource(R.string.hume_origin_auto), selected = ui.origin == null) { onChooseOrigin(null) }
                        for (pkg in choices) {
                            OriginChoice(pkg + if (HumeOrigins.looksLikeHume(pkg)) "  ·  Hume" else "", selected = ui.origin == pkg) { onChooseOrigin(pkg) }
                        }
                        if (ui.lastSync != null || ui.origin != null) {
                            SecondaryButton(onClick = onForget) { Text(stringResource(R.string.hume_forget)) }
                        }
                    }
                }
            }
        }
    }
}

/** One app to pick as the band's; the chosen one says so, so the person sees the choice without a dialog. */
@Composable
private fun OriginChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    SecondaryButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text((if (selected) "● " else "○ ") + label, maxLines = 2)
    }
}

/** The band's last night, steps and heart rate, the charts and the workouts of the last 30 days. */
private fun LazyListScope.humeDashboard(
    days: List<HumeDay>,
    section: (String, @Composable LazyItemScope.() -> Unit) -> Unit,
    scrollTo: (String) -> Unit,
    metrics: MetricColors,
) {
    val last = days.last()
    val byDate = days.associateBy { it.date }
    val dates = (29 downTo 0).map { last.date.minusDays(it.toLong()) }
    val labels = dates.map { Fmt.shortDate(it) }
    fun series(pick: (HumeDay) -> Number?): List<Double?> = dates.map { d -> byDate[d]?.let(pick)?.toDouble() }
    section("today") {
        SectionTitle(stringResource(R.string.hume_today, Fmt.date(last.date)))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(stringResource(R.string.hume_sleep), last.sleepS?.let { Fmt.hours(it) }, metrics.sleep, Modifier.weight(1f), onClick = { scrollTo("sleep") })
            ValueTile(stringResource(R.string.hume_steps), last.steps?.let { Fmt.thousands(it) }, metrics.steps, Modifier.weight(1f), onClick = { scrollTo("steps") })
            ValueTile(
                stringResource(R.string.hume_heart),
                (last.restingHr ?: last.lowestHr)?.let { "$it" },
                metrics.heart,
                Modifier.weight(1f),
                detail = "bpm",
                onClick = { scrollTo("rhr") },
            )
        }
    }
    section("night") {
        SurfaceCard {
            Text(stringResource(R.string.hume_last_night), style = MaterialTheme.typography.titleMedium)
            val night = last.sleepS
            Text(
                when {
                    night == null -> stringResource(R.string.hume_no_night)
                    last.deepS != null && last.remS != null -> stringResource(R.string.hume_night_line, Fmt.hours(night), Fmt.hours(last.deepS!!), Fmt.hours(last.remS!!))
                    else -> stringResource(R.string.hume_night_total, Fmt.hours(night))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val extras = listOfNotNull(
                last.awakeS?.takeIf { night != null }?.let { stringResource(R.string.hume_awake, Fmt.hours(it)) },
                last.restingHr?.let { stringResource(R.string.whoop_resting_hr, it) },
                last.lowestHr?.let { stringResource(R.string.hume_lowest_hr, it) },
                last.hrv?.let { stringResource(R.string.whoop_hrv_ms, Fmt.oneDecimal(it)) },
                last.spo2?.let { stringResource(R.string.whoop_spo2_line, Fmt.oneDecimal(it)) },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val day = listOfNotNull(
                last.activeKcal?.let { stringResource(R.string.hume_active_kcal, it) },
                last.avgHr?.let { stringResource(R.string.hume_avg_hr, it) },
                last.maxHr?.let { stringResource(R.string.hume_max_hr, it) },
            )
            if (day.isNotEmpty()) Text(day.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val charts = listOf(
        HumeChart("steps", R.string.hume_chart_steps, series { it.steps }, metrics.steps, bars = true) { Fmt.thousands(Math.round(it).toInt()) },
        HumeChart("sleep", R.string.hume_chart_sleep, series { it.sleepHours }, metrics.sleep, bars = true) { Fmt.oneDecimal(it) + " h" },
        HumeChart("hrv", R.string.hume_chart_hrv, series { it.hrv }, metrics.distance, bars = false) { "${Math.round(it)} ms" },
        HumeChart("rhr", R.string.hume_chart_rhr, series { it.restingHr }, metrics.heart, bars = false) { "${Math.round(it)} bpm" },
        HumeChart("lowest", R.string.hume_chart_lowest, series { it.lowestHr }, metrics.heart, bars = false) { "${Math.round(it)} bpm" },
    )
    for (chart in charts) {
        if (chart.values.all { it == null }) continue
        section(chart.key) {
            val title = stringResource(chart.title)
            SurfaceCard {
                Text(title, style = MaterialTheme.typography.titleMedium)
                val average = chart.values.filterNotNull().average()
                Text(stringResource(R.string.oura_average, chart.format(average)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (chart.bars) {
                    BarChart(chart.values, description = title, color = chart.color, goal = null, labels = labels, format = chart.format)
                } else {
                    LineChart(chart.values, description = title, color = chart.color, labels = labels, format = chart.format)
                }
            }
        }
    }
    val workouts = days.flatMap { it.workouts }.sortedByDescending { it.startMs }.take(10)
    if (workouts.isNotEmpty()) {
        section("workouts") {
            FoldableCard("hume-workouts", stringResource(R.string.hume_workouts)) {
                for (w in workouts) {
                    val parts = listOfNotNull(
                        Fmt.duration(w.durationS.toDouble()),
                        w.distanceM?.takeIf { it > 0 }?.let { Fmt.km(it) + " km" },
                        w.calories?.let { "$it kcal" },
                    )
                    Text(w.name, style = MaterialTheme.typography.bodyLarge)
                    Text(Fmt.dateTime(w.start) + "  ·  " + parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A number of the band's day with its name above it; tapping it jumps to its chart. Health Connect has no scores, so no bar. */
@Composable
private fun ValueTile(label: String, value: String?, color: Color, modifier: Modifier = Modifier, detail: String? = null, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Panel(modifier.tappable(onClick), tint = color, padding = 14.dp, spacing = 4.dp) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (onClick != null) OpenHint()
        }
        Text(
            value ?: "–",
            style = MaterialTheme.typography.headlineMedium,
            color = if (value == null) colors.onSurfaceVariant else color,
            maxLines = 1,
        )
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

private class HumeChart(
    val key: String,
    val title: Int,
    val values: List<Double?>,
    val color: Color,
    val bars: Boolean,
    val format: (Double) -> String,
)

private fun humeProblemText(problem: HumeProblem): Int = when (problem) {
    HumeProblem.UNAVAILABLE -> R.string.hume_unavailable
    HumeProblem.NEEDS_PERMISSION -> R.string.hume_problem_permission
    HumeProblem.NO_DATA -> R.string.hume_problem_nodata
    HumeProblem.FAILED -> R.string.hume_problem_failed
}
