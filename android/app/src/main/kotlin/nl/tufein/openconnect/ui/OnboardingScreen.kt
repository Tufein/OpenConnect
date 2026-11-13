package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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

/**
 * The first start (v0.17.0): which devices do you use? Only what is chosen shows up in the app, so someone without a
 * Garmin or without an Oura Ring is never bothered by it. It can be changed later under Settings, "Your devices".
 */
@Composable
fun OnboardingScreen(initial: SourcesUi, onDone: (garmin: Boolean, oura: Boolean) -> Unit, design: String = "retro", onDesign: (String) -> Unit = {}) {
    var garmin by rememberSaveable { mutableStateOf(initial.garmin) }
    var oura by rememberSaveable { mutableStateOf(initial.oura) }
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
                DeviceSwitch(stringResource(R.string.device_garmin), stringResource(R.string.device_garmin_body), garmin) { garmin = it }
                DeviceSwitch(stringResource(R.string.device_oura), stringResource(R.string.device_oura_body), oura) { oura = it }
            }
            SectionTitle(stringResource(R.string.onboarding_design))
            SurfaceCard { DesignChoice(design, onDesign) }
            Text(
                stringResource(if (!garmin && !oura) R.string.onboarding_none else R.string.onboarding_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onDone(garmin, oura) }, modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.onboarding_start))
            }
        }
    }
}
