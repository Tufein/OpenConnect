package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.whoop.WhoopAuth
import nl.tufein.openconnect.core.whoop.WhoopDay
import nl.tufein.openconnect.whoop.WhoopProblem
import nl.tufein.openconnect.whoop.WhoopUi

/**
 * The WHOOP tab (v0.49.0), shown only to someone who chose the WHOOP band. On top the band's own recovery, strain and sleep,
 * kept apart from the watch's and the ring's; below the link: your own WHOOP application, the login in the browser and the sync.
 * The texts for the client id, secret, redirect address, pasting, logging out and the schedule are the Oura screen's.
 */
@Composable
fun WhoopScreen(
    ui: WhoopUi,
    onSaveClient: (id: String, secret: String, redirect: String) -> Unit,
    loginUrl: () -> String?,
    onPasteRedirect: (String) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val uri = LocalUriHandler.current
    var id by remember(ui.clientId) { mutableStateOf(ui.clientId) }
    var secret by remember { mutableStateOf("") }
    var redirect by remember(ui.redirectUri) { mutableStateOf(ui.redirectUri) }
    var pasted by remember { mutableStateOf("") }
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
            Text(stringResource(R.string.whoop_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (ui.days.isNotEmpty()) {
            whoopDashboard(ui.days, ::section, ::scrollTo, metrics)
        } else {
            section("intro") { Text(stringResource(if (ui.connected) R.string.whoop_no_days else R.string.whoop_intro), style = MaterialTheme.typography.bodyMedium) }
        }
        if (ui.busy) item { PixelProgress(Modifier.fillMaxWidth()) }
        ui.problem?.let { problem -> item(key = "problem") { Text(stringResource(whoopProblemText(problem)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        if (ui.connected) {
            item(key = "link") {
                SectionTitle(stringResource(R.string.oura_connected_title))
                SurfaceCard(accent = OpenConnectColors.metrics.readiness) {
                    Text(
                        ui.lastSync?.let { stringResource(R.string.oura_last_sync, Fmt.dateTime(it)) } ?: stringResource(R.string.oura_never_synced),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ui.last?.let { r ->
                        Text(
                            stringResource(R.string.oura_report, r.daysUpdated, r.nightsUpdated, r.activitiesAdded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.oura_schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton(onClick = onSync, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.oura_sync_now)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(onClick = onDisconnect) { Text(stringResource(R.string.oura_log_out)) }
                        SecondaryButton(onClick = onForget) { Text(stringResource(R.string.oura_forget)) }
                    }
                }
            }
            return@LazyColumn
        }
        item {
            SectionTitle(stringResource(R.string.whoop_step1))
            SurfaceCard {
                Text(stringResource(R.string.whoop_step1_body, ui.redirectUri), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(onClick = { uri.openUri(WhoopAuth.DASHBOARD_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.whoop_open_apps))
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.whoop_step2))
            SurfaceCard {
                if (!ui.canStore) {
                    Text(stringResource(R.string.whoop_cannot_store), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(id, { id = it }, label = { Text(stringResource(R.string.oura_client_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        secret, { secret = it },
                        label = { Text(stringResource(if (ui.hasClient) R.string.oura_client_secret_saved else R.string.oura_client_secret)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(redirect, { redirect = it }, label = { Text(stringResource(R.string.oura_redirect)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(onClick = { onSaveClient(id, secret, redirect); secret = "" }, enabled = id.isNotBlank() && (secret.isNotBlank() || ui.hasClient)) {
                        Text(stringResource(R.string.oura_save_client))
                    }
                }
            }
        }
        if (ui.hasClient) {
            item {
                SectionTitle(stringResource(R.string.whoop_step3))
                SurfaceCard {
                    Text(stringResource(R.string.whoop_step3_body), style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(onClick = { loginUrl()?.let(uri::openUri) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.whoop_log_in))
                    }
                    OutlinedTextField(pasted, { pasted = it }, label = { Text(stringResource(R.string.oura_paste)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(onClick = { onPasteRedirect(pasted); pasted = "" }, enabled = pasted.isNotBlank() && !ui.busy) {
                        Text(stringResource(R.string.oura_paste_button))
                    }
                    SecondaryButton(onClick = onForget) { Text(stringResource(R.string.oura_forget)) }
                }
            }
        }
    }
}

/** The band's recovery, strain and sleep, the last night, the charts and the workouts of the last 30 days. */
private fun LazyListScope.whoopDashboard(
    days: List<WhoopDay>,
    section: (String, @Composable LazyItemScope.() -> Unit) -> Unit,
    scrollTo: (String) -> Unit,
    metrics: MetricColors,
) {
    val last = days.last()
    val byDate = days.associateBy { it.date }
    val dates = (29 downTo 0).map { last.date.minusDays(it.toLong()) }
    val labels = dates.map { Fmt.shortDate(it) }
    fun series(pick: (WhoopDay) -> Number?): List<Double?> = dates.map { d -> byDate[d]?.let(pick)?.toDouble() }
    section("scores") {
        SectionTitle(stringResource(R.string.whoop_today, Fmt.date(last.date)))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreTile(stringResource(R.string.whoop_recovery), last.recovery, metrics.readiness, Modifier.weight(1f), onClick = { scrollTo("recovery") })
            StrainTile(last.strain, metrics.stress, Modifier.weight(1f), onClick = { scrollTo("strain") })
            ScoreTile(stringResource(R.string.whoop_sleep), last.sleepPerformance, metrics.sleep, Modifier.weight(1f), onClick = { scrollTo("sleep") })
        }
    }
    section("night") {
        SurfaceCard {
            Text(stringResource(R.string.whoop_last_night), style = MaterialTheme.typography.titleMedium)
            val night = last.sleepS
            Text(
                if (night == null) stringResource(R.string.whoop_no_night)
                else stringResource(R.string.whoop_night_line, Fmt.hours(night), Fmt.hours(last.deepS ?: 0), Fmt.hours(last.remS ?: 0)),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (night != null && last.sleepNeedS != null && last.sleepPerformance != null && last.sleepEfficiency != null) {
                Text(
                    stringResource(R.string.whoop_night_need, Fmt.hours(last.sleepNeedS!!), last.sleepPerformance!!, last.sleepEfficiency!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val extras = listOfNotNull(
                last.restingHr?.let { stringResource(R.string.whoop_resting_hr, it) },
                last.hrv?.let { stringResource(R.string.whoop_hrv_ms, Fmt.oneDecimal(it)) },
                last.respiration?.let { stringResource(R.string.whoop_breaths, Fmt.oneDecimal(it)) },
                last.spo2?.let { stringResource(R.string.whoop_spo2_line, Fmt.oneDecimal(it)) },
                last.skinTempC?.let { stringResource(R.string.whoop_skin_temp, Fmt.oneDecimal(it)) },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val whole = listOfNotNull(last.kcal, last.avgHr, last.maxHr)
            if (whole.size == 3) {
                Text(
                    stringResource(R.string.whoop_day_line, last.kcal!!, last.avgHr!!, last.maxHr!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val charts = listOf(
        WhoopChart("recovery", R.string.whoop_chart_recovery, series { it.recovery }, metrics.readiness, bars = false) { "${Math.round(it)}%" },
        WhoopChart("strain", R.string.whoop_chart_strain, series { it.strain }, metrics.stress, bars = true) { Fmt.oneDecimal(it) },
        WhoopChart("sleep", R.string.whoop_chart_sleep, series { it.sleepHours }, metrics.sleep, bars = true) { Fmt.oneDecimal(it) + " h" },
        WhoopChart("hrv", R.string.whoop_chart_hrv, series { it.hrv }, metrics.distance, bars = false) { "${Math.round(it)} ms" },
        WhoopChart("rhr", R.string.whoop_chart_rhr, series { it.restingHr }, metrics.heart, bars = false) { "${Math.round(it)} bpm" },
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
            SectionTitle(stringResource(R.string.whoop_workouts))
            SurfaceCard {
                for (w in workouts) {
                    val parts = listOfNotNull(
                        Fmt.duration(w.durationS.toDouble()),
                        w.distanceM?.takeIf { it > 0 }?.let { Fmt.km(it) + " km" },
                        w.calories?.let { "$it kcal" },
                    )
                    Text(w.name, style = MaterialTheme.typography.bodyLarge)
                    Text(Fmt.dateTime(w.start) + "  ·  " + parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (w.strain != null && w.avgHr != null) {
                        Text(stringResource(R.string.whoop_workout_extra, Fmt.oneDecimal(w.strain!!), w.avgHr!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Day strain: WHOOP's number goes from 0 to 21, so the bar is filled in 21sts and not in hundredths like the scores. */
@Composable
private fun StrainTile(strain: Double?, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Panel(modifier.tappable(onClick), tint = color, padding = 14.dp, spacing = 4.dp) {
        Row {
            Text(stringResource(R.string.whoop_strain), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (onClick != null) OpenHint()
        }
        Text(
            strain?.let { Fmt.oneDecimal(it) } ?: "–",
            style = MaterialTheme.typography.displayMedium,
            color = if (strain == null) colors.onSurfaceVariant else color,
        )
        SegmentBar(fraction = ((strain ?: 0.0) / 21.0).toFloat(), color = color, Modifier.fillMaxWidth().height(10.dp))
        Text(stringResource(R.string.whoop_of_21), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

private class WhoopChart(
    val key: String,
    val title: Int,
    val values: List<Double?>,
    val color: Color,
    val bars: Boolean,
    val format: (Double) -> String,
)

private fun whoopProblemText(problem: WhoopProblem): Int = when (problem) {
    WhoopProblem.CANNOT_STORE -> R.string.whoop_cannot_store
    WhoopProblem.MISSING_FIELDS -> R.string.oura_problem_fields
    WhoopProblem.LOGIN_FAILED -> R.string.whoop_problem_login
    WhoopProblem.LOGIN_EXPIRED -> R.string.whoop_problem_expired
    WhoopProblem.FORBIDDEN -> R.string.whoop_problem_forbidden
    WhoopProblem.RATE_LIMITED -> R.string.whoop_problem_rate
    WhoopProblem.NETWORK -> R.string.whoop_problem_network
    WhoopProblem.SERVER -> R.string.whoop_problem_server
}
