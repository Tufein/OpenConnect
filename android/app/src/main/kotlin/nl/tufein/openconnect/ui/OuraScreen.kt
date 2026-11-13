package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import nl.tufein.openconnect.core.oura.OuraDay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.oura.OuraProblem
import nl.tufein.openconnect.oura.OuraUi

/**
 * The Oura tab (v0.17.0), shown only to someone who chose the Oura Ring. On top the ring's own scores and charts, kept
 * apart from the watch's; below the link: your own Oura application, the login in the browser, and the sync.
 */
@Composable
fun OuraScreen(
    ui: OuraUi,
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
            Text(stringResource(R.string.oura_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (ui.days.isNotEmpty()) {
            ouraDashboard(ui.days, ::section, ::scrollTo, metrics)
        } else {
            section("intro") { Text(stringResource(if (ui.connected) R.string.oura_no_days else R.string.oura_intro), style = MaterialTheme.typography.bodyMedium) }
        }
        if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ui.problem?.let { problem -> item(key = "problem") { Text(stringResource(problemText(problem)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        if (ui.connected) {
            item(key = "link") {
                SectionTitle(stringResource(R.string.oura_connected_title))
                SurfaceCard(accent = OpenConnectColors.metrics.sleep) {
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
                    Button(onClick = onSync, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.oura_sync_now)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.oura_log_out)) }
                        OutlinedButton(onClick = onForget) { Text(stringResource(R.string.oura_forget)) }
                    }
                }
            }
            return@LazyColumn
        }
        item {
            SectionTitle(stringResource(R.string.oura_step1))
            SurfaceCard {
                Text(stringResource(R.string.oura_step1_body, ui.redirectUri), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { uri.openUri("https://cloud.ouraring.com/oauth/applications") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.oura_open_apps))
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.oura_step2))
            SurfaceCard {
                if (!ui.canStore) {
                    Text(stringResource(R.string.oura_cannot_store), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    Button(onClick = { onSaveClient(id, secret, redirect); secret = "" }, enabled = id.isNotBlank() && (secret.isNotBlank() || ui.hasClient)) {
                        Text(stringResource(R.string.oura_save_client))
                    }
                }
            }
        }
        if (ui.hasClient) {
            item {
                SectionTitle(stringResource(R.string.oura_step3))
                SurfaceCard {
                    Text(stringResource(R.string.oura_step3_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { loginUrl()?.let(uri::openUri) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.oura_log_in))
                    }
                    OutlinedTextField(pasted, { pasted = it }, label = { Text(stringResource(R.string.oura_paste)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { onPasteRedirect(pasted); pasted = "" }, enabled = pasted.isNotBlank() && !ui.busy) {
                        Text(stringResource(R.string.oura_paste_button))
                    }
                    OutlinedButton(onClick = onForget) { Text(stringResource(R.string.oura_forget)) }
                }
            }
        }
    }
}

/** The ring's scores, charts and workouts of the last 30 days. */
private fun LazyListScope.ouraDashboard(
    days: List<OuraDay>,
    section: (String, @Composable LazyItemScope.() -> Unit) -> Unit,
    scrollTo: (String) -> Unit,
    metrics: MetricColors,
) {
    val last = days.last()
    val byDate = days.associateBy { it.date }
    val dates = (29 downTo 0).map { last.date.minusDays(it.toLong()) }
    val labels = dates.map { Fmt.shortDate(it) }
    fun series(pick: (OuraDay) -> Number?): List<Double?> = dates.map { d -> byDate[d]?.let(pick)?.toDouble() }
    section("scores") {
        SectionTitle(stringResource(R.string.oura_today, Fmt.date(last.date)))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreTile(stringResource(R.string.oura_readiness), last.readiness, metrics.readiness, Modifier.weight(1f), onClick = { scrollTo("readiness") })
            ScoreTile(stringResource(R.string.oura_sleep), last.sleepScore, metrics.sleep, Modifier.weight(1f), onClick = { scrollTo("sleep") })
            ScoreTile(stringResource(R.string.oura_activity), last.activityScore, metrics.steps, Modifier.weight(1f), onClick = { scrollTo("steps") })
        }
    }
    section("night") {
        SurfaceCard {
            Text(stringResource(R.string.oura_last_night), style = MaterialTheme.typography.titleMedium)
            val night = last.sleepS
            Text(
                if (night == null) stringResource(R.string.oura_no_night)
                else stringResource(R.string.oura_night_line, Fmt.hours(night), Fmt.hours(last.deepS ?: 0), Fmt.hours(last.remS ?: 0)),
                style = MaterialTheme.typography.bodyMedium,
            )
            val extras = listOfNotNull(
                last.lowestHr?.let { stringResource(R.string.oura_lowest_hr, it) },
                last.averageHrv?.let { stringResource(R.string.oura_hrv_ms, it) },
                last.temperatureDeviation?.let { stringResource(R.string.oura_temp, String.format(java.util.Locale.getDefault(), "%+.1f", it)) },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val charts = listOf(
        OuraChart("readiness", R.string.oura_readiness, series { it.readiness }, metrics.readiness, bars = false, goal = null) { "${Math.round(it)}" },
        OuraChart("sleep", R.string.oura_sleep_hours, series { it.sleepHours }, metrics.sleep, bars = true, goal = 8.0) { Fmt.oneDecimal(it) + " h" },
        OuraChart("steps", R.string.steps, series { it.steps }, metrics.steps, bars = true, goal = 10_000.0) { Fmt.thousands(Math.round(it).toInt()) },
        OuraChart("hr", R.string.oura_lowest_hr_title, series { it.lowestHr }, metrics.heart, bars = false, goal = null) { "${Math.round(it)} bpm" },
        OuraChart("hrv", R.string.hrv, series { it.averageHrv }, metrics.distance, bars = false, goal = null) { "${Math.round(it)} ms" },
        OuraChart("temp", R.string.oura_temp_title, series { it.temperatureDeviation }, metrics.stress, bars = false, goal = null) {
            String.format(java.util.Locale.getDefault(), "%+.1f °C", it)
        },
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
                    BarChart(chart.values, description = title, color = chart.color, goal = chart.goal, labels = labels, format = chart.format)
                } else {
                    LineChart(chart.values, description = title, color = chart.color, includeZero = chart.key == "temp", labels = labels, format = chart.format)
                }
            }
        }
    }
    val workouts = days.flatMap { it.workouts }.sortedByDescending { it.startMs }.take(10)
    if (workouts.isNotEmpty()) {
        section("workouts") {
            SectionTitle(stringResource(R.string.oura_workouts))
            SurfaceCard {
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

private class OuraChart(
    val key: String,
    val title: Int,
    val values: List<Double?>,
    val color: androidx.compose.ui.graphics.Color,
    val bars: Boolean,
    val goal: Double?,
    val format: (Double) -> String,
)

private fun problemText(problem: OuraProblem): Int = when (problem) {
    OuraProblem.CANNOT_STORE -> R.string.oura_cannot_store
    OuraProblem.MISSING_FIELDS -> R.string.oura_problem_fields
    OuraProblem.LOGIN_FAILED -> R.string.oura_problem_login
    OuraProblem.LOGIN_EXPIRED -> R.string.oura_problem_expired
    OuraProblem.FORBIDDEN -> R.string.oura_problem_forbidden
    OuraProblem.RATE_LIMITED -> R.string.oura_problem_rate
    OuraProblem.NETWORK -> R.string.oura_problem_network
    OuraProblem.SERVER -> R.string.oura_problem_server
}
