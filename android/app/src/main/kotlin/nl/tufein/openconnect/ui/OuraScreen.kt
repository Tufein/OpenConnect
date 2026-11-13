package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.oura.OuraProblem
import nl.tufein.openconnect.oura.OuraUi

/** The Oura Ring link: your own Oura application, the login in the browser, and the sync. */
@Composable
fun OuraScreen(
    ui: OuraUi,
    onBack: () -> Unit,
    onSaveClient: (id: String, secret: String, redirect: String) -> Unit,
    loginUrl: () -> String?,
    onPasteRedirect: (String) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onPreferSleep: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val uri = LocalUriHandler.current
    var id by remember(ui.clientId) { mutableStateOf(ui.clientId) }
    var secret by remember { mutableStateOf("") }
    var redirect by remember(ui.redirectUri) { mutableStateOf(ui.redirectUri) }
    var pasted by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(stringResource(R.string.oura_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        item { Text(stringResource(R.string.oura_intro), style = MaterialTheme.typography.bodyMedium) }
        if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ui.problem?.let { problem -> item { Text(stringResource(problemText(problem)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        if (ui.connected) {
            item {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = ui.preferSleep, onCheckedChange = onPreferSleep)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.oura_prefer_sleep), style = MaterialTheme.typography.bodyMedium)
                    }
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
