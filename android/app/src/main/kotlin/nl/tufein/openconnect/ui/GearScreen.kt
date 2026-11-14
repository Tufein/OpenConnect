package nl.tufein.openconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Gear
import nl.tufein.openconnect.core.ShoeStatus
import nl.tufein.openconnect.core.ShoeUsage
import nl.tufein.openconnect.core.Sport

/** A change to the shoes. */
sealed interface GearAction {
    data class Add(val name: String, val sport: Sport, val countEarlier: Boolean) : GearAction

    data class Retire(val id: String) : GearAction

    data class Resume(val id: String) : GearAction

    /** Moves the replace-after limit by [km] kilometres. */
    data class MoveLimit(val id: String, val km: Int) : GearAction

    data class Remove(val id: String) : GearAction

    /** Puts an activity on a pair, on no shoes ([Gear.NONE]) or, with null, back on the default. */
    data class Assign(val activityId: String, val shoeId: String?) : GearAction
}

private fun statusColor(status: ShoeStatus): Color = when (status) {
    ShoeStatus.FRESH -> Color(0xFF4CAF50)
    ShoeStatus.GETTING_OLD -> Color(0xFFFFC107)
    ShoeStatus.REPLACE -> Color(0xFFF44336)
}

private fun statusText(status: ShoeStatus): Int = when (status) {
    ShoeStatus.FRESH -> R.string.gear_status_fresh
    ShoeStatus.GETTING_OLD -> R.string.gear_status_old
    ShoeStatus.REPLACE -> R.string.gear_status_replace
}

@Composable
fun GearContent(state: GearState, onAction: (GearAction) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.loaded) return@LazyColumn
        val due = Gear.due(state.usage)
        if (due.isNotEmpty()) {
            item {
                SurfaceCard {
                    for (u in due) {
                        Text(
                            stringResource(R.string.gear_alert, u.shoe.name, Math.round(u.km).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor(ShoeStatus.REPLACE),
                        )
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.gear_title)) }
        if (state.usage.isEmpty()) {
            item { Text(stringResource(R.string.gear_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        // Pairs in use first, retired ones after them.
        val ordered = state.usage.filter { it.shoe.until == null } + state.usage.filter { it.shoe.until != null }
        items(ordered.size) { i -> ShoeCard(ordered[i], onAction) }
        item { AddShoesCard(onAction) }
        item {
            Text(
                stringResource(R.string.gear_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoeCard(u: ShoeUsage, onAction: (GearAction) -> Unit) {
    SurfaceCard {
        Text(u.shoe.name, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(sportLabel(u.shoe.sport)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.gear_km, Fmt.oneDecimal(u.km), u.shoe.retireKm), style = MaterialTheme.typography.headlineSmall)
        SegmentBar(u.fraction.coerceIn(0.0, 1.0).toFloat(), statusColor(u.status), Modifier.fillMaxWidth().height(10.dp))
        Text(stringResource(statusText(u.status)), style = MaterialTheme.typography.bodyMedium)
        val last = u.last
        Text(
            if (last == null) stringResource(R.string.gear_no_activities) else stringResource(R.string.gear_activities, u.activities, Fmt.date(last)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        u.shoe.until?.let { until ->
            Text(stringResource(R.string.gear_retired_on, Fmt.date(until)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(onClick = { onAction(GearAction.MoveLimit(u.shoe.id, -Gear.LIMIT_STEP_KM)) }) { Text(stringResource(R.string.gear_limit_minus)) }
            SecondaryButton(onClick = { onAction(GearAction.MoveLimit(u.shoe.id, Gear.LIMIT_STEP_KM)) }) { Text(stringResource(R.string.gear_limit_plus)) }
            if (u.shoe.until == null) {
                SecondaryButton(onClick = { onAction(GearAction.Retire(u.shoe.id)) }) { Text(stringResource(R.string.gear_retire)) }
            } else {
                SecondaryButton(onClick = { onAction(GearAction.Resume(u.shoe.id)) }) { Text(stringResource(R.string.gear_resume)) }
            }
            SecondaryButton(onClick = { onAction(GearAction.Remove(u.shoe.id)) }) { Text(stringResource(R.string.gear_remove)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddShoesCard(onAction: (GearAction) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf(Sport.RUNNING.key) }
    var earlier by rememberSaveable { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.gear_add_title))
    SurfaceCard {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(Gear.MAX_NAME) },
            label = { Text(stringResource(R.string.gear_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in Gear.SPORTS) {
                PixelChip(selected = sport == s.key, onClick = { sport = s.key }, label = { Text(stringResource(sportLabel(s))) })
            }
        }
        PixelChip(selected = earlier, onClick = { earlier = !earlier }, label = { Text(stringResource(R.string.gear_earlier)) })
        PrimaryButton(
            enabled = Gear.cleanName(name).isNotEmpty(),
            onClick = {
                onAction(GearAction.Add(name, Sport.fromKey(sport), earlier))
                name = ""
            },
        ) { Text(stringResource(R.string.gear_add)) }
    }
}

/** On an activity: which pair it went on, and a way to change that. Hidden when there are no pairs for its sport. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityShoesCard(state: DetailState, onAssign: (String?) -> Unit) {
    val activity = state.activity ?: return
    if (state.shoeChoices.isEmpty()) return
    SectionTitle(stringResource(R.string.gear_activity_title))
    SurfaceCard {
        val shoe = state.shoe
        Text(
            if (shoe == null) stringResource(R.string.gear_on_none) else stringResource(R.string.gear_on, shoe.name),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in state.shoeChoices) {
                PixelChip(selected = shoe?.id == c.id, onClick = { onAssign(c.id) }, label = { Text(c.name) })
            }
            PixelChip(selected = shoe == null, onClick = { onAssign(Gear.NONE) }, label = { Text(stringResource(R.string.gear_none_chip)) })
        }
        if (state.shoeByHand) {
            SecondaryButton(onClick = { onAssign(null) }) { Text(stringResource(R.string.gear_automatic)) }
        }
        Text(
            stringResource(R.string.gear_activity_note, stringResource(sportLabel(activity.sport))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
