package nl.tufein.openconnect.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.DataSourceKind

/**
 * The first start (v0.17.0): which devices do you use? Only what is chosen shows up in the app, so someone without a
 * Garmin or without an Oura Ring is never bothered by it. It can be changed later under Settings, "Your devices".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initial: SourcesUi,
    style: String,
    palette: String,
    onStyle: (String) -> Unit,
    onPalette: (String) -> Unit,
    onDone: (garmin: Boolean, oura: Boolean, whoop: Boolean, hume: Boolean) -> Unit,
) {
    var garmin by rememberSaveable { mutableStateOf(initial.garmin) }
    var oura by rememberSaveable { mutableStateOf(initial.oura) }
    var whoop by rememberSaveable { mutableStateOf(initial.whoop) }
    var hume by rememberSaveable { mutableStateOf(initial.hume) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LogoMark(size = 56.dp)
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyMedium)
            SectionTitle(stringResource(R.string.onboarding_devices))
            SurfaceCard {
                DeviceSwitch(stringResource(R.string.device_garmin), stringResource(R.string.device_garmin_body), garmin, deviceStatus(DataSourceKind.GARMIN_ACCOUNT)) { garmin = it }
                DeviceSwitch(stringResource(R.string.device_oura), stringResource(R.string.device_oura_body), oura, deviceStatus(DataSourceKind.OURA)) { oura = it }
                DeviceSwitch(stringResource(R.string.device_whoop), stringResource(R.string.device_whoop_body), whoop, deviceStatus(DataSourceKind.WHOOP)) { whoop = it }
                DeviceSwitch(stringResource(R.string.device_hume), stringResource(R.string.device_hume_body), hume, deviceStatus(DataSourceKind.HUME)) { hume = it }
            }
            Text(stringResource(R.string.onboarding_experimental_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle(stringResource(R.string.onboarding_look))
            Text(stringResource(R.string.onboarding_look_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in listOf("clean" to R.string.style_clean, "retro" to R.string.style_retro, "classic" to R.string.style_classic)) {
                    PixelChip(selected = style == key, onClick = { onStyle(key) }, label = { Text(stringResource(label)) })
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val palettes = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add("dynamic" to R.string.palette_dynamic)
                    add("handheld" to R.string.palette_handheld)
                    add("contrast" to R.string.palette_contrast)
                }
                for ((key, label) in palettes) {
                    PixelChip(selected = palette == key, onClick = { onPalette(key) }, label = { Text(stringResource(label)) })
                }
            }
            LookPreview()
            SurfaceCard {
                Text(stringResource(R.string.onboarding_privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.onboarding_privacy_body), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(if (!garmin && !oura && !whoop && !hume) R.string.onboarding_none else R.string.onboarding_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(onClick = { onDone(garmin, oura, whoop, hume) }, modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.onboarding_start))
            }
        }
    }
}
