package nl.tufein.openconnect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.Effort
import nl.tufein.openconnect.core.retro.Initials

// v1.17.0, retro part 3: the hi-score table. For each distance the five fastest runs, with the three letters the person
// chose, laid out like the table an arcade cabinet shows between games. The places read "1ST" to "5TH" in every language,
// as on a cabinet; TalkBack reads each row as a sentence in the app's language instead.

@Composable
internal fun HiScoreCard(table: Map<Double, List<Effort>>, initials: String, onInitials: (String) -> Unit, onOpenActivity: (String) -> Unit) {
    if (table.isEmpty()) return
    val locale = currentLocale()
    var distance by rememberSaveable { mutableStateOf(table.keys.first()) }
    if (distance !in table) distance = table.keys.first()
    var editing by rememberSaveable { mutableStateOf(false) }
    SurfaceCard(accent = MaterialTheme.colorScheme.tertiary) {
        Text(stringResource(R.string.hiscore_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.hiscore_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in table.keys) {
                PixelChip(selected = d == distance, onClick = { distance = d }, label = { Text(effortLabel(d)) })
            }
        }
        val entries = table[distance].orEmpty()
        entries.forEachIndexed { index, e ->
            val place = index + 1
            val time = Fmt.duration(e.seconds)
            val date = e.date?.let { Fmt.date(it, locale) }.orEmpty()
            val sentence = stringResource(R.string.hiscore_row, place, time, date, initials)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = e.activityId != null, role = Role.Button) { e.activityId?.let(onOpenActivity) }
                    .semantics(mergeDescendants = true) { contentDescription = sentence },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val colour = if (place == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                Text(Initials.rank(place), Modifier.width(44.dp), style = MaterialTheme.typography.titleMedium, color = colour)
                Text(initials, Modifier.width(44.dp), style = MaterialTheme.typography.titleMedium, color = colour)
                Text(time, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colour, textAlign = TextAlign.End)
                Text(date, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
        if (editing) {
            NameEntry(initials, onInitials)
            PixelChip(selected = false, onClick = { editing = false }, label = { Text(stringResource(R.string.hiscore_done)) })
        } else {
            PixelChip(selected = false, onClick = { editing = true }, label = { Text(stringResource(R.string.hiscore_edit)) })
        }
    }
}

/** The name entry screen of a cabinet: three letters, each with an arrow up and an arrow down. */
@Composable
private fun NameEntry(initials: String, onInitials: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        for (slot in 0 until Initials.LENGTH) {
            val up = stringResource(R.string.hiscore_up, slot + 1)
            val down = stringResource(R.string.hiscore_down, slot + 1)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelChip(
                    selected = false,
                    onClick = { onInitials(Initials.step(initials, slot, +1)) },
                    label = { Text("▲") },
                    modifier = Modifier.semantics { contentDescription = up },
                )
                Text(initials[slot].toString(), style = MaterialTheme.typography.headlineMedium)
                PixelChip(
                    selected = false,
                    onClick = { onInitials(Initials.step(initials, slot, -1)) },
                    label = { Text("▼") },
                    modifier = Modifier.semantics { contentDescription = down },
                )
            }
        }
    }
}
