package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.ai.ActiveAi
import nl.tufein.openconnect.ai.CoachMessage
import nl.tufein.openconnect.ai.CoachUi

private val SUGGESTIONS = listOf(
    R.string.coach_suggest_sleep,
    R.string.coach_suggest_recovery,
    R.string.coach_suggest_training,
    R.string.coach_suggest_tomorrow,
)

/**
 * A chat with an AI coach that sees a digest of your last four weeks (numbers, no GPS), through the Claude or Gemini
 * key you saved. Without a key there is nothing to ask, and the screen says how to add one.
 */
@Composable
fun CoachContent(
    state: CoachUi,
    active: ActiveAi,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onOpenAiSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (!state.canAsk) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SurfaceCard(accent = MaterialTheme.colorScheme.tertiary) {
                    Text(stringResource(R.string.coach_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.coach_needs_key), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenAiSettings) { Text(stringResource(R.string.coach_open_settings)) }
                }
            }
        }
        return
    }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Keep the newest line in view: the intro card, the suggestions (only while empty), the messages, the spinner, the error.
    val itemCount = 1 + (if (state.messages.isEmpty()) 1 else 0) + state.messages.size + (if (state.busy) 1 else 0) + (if (state.error != null) 1 else 0)
    LaunchedEffect(itemCount) { if (state.messages.isNotEmpty()) listState.scrollToItem(itemCount - 1) }
    fun send(text: String) {
        if (text.isNotBlank() && !state.busy) {
            onAsk(text)
            draft = ""
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SurfaceCard(accent = MaterialTheme.colorScheme.tertiary) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.coach_title), style = MaterialTheme.typography.titleMedium)
                        if (state.messages.isNotEmpty()) {
                            TextButton(onClick = onClear) { Text(stringResource(R.string.coach_new_chat)) }
                        }
                    }
                    Text(stringResource(R.string.coach_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(if (active == ActiveAi.GEMINI) R.string.coach_via_gemini else R.string.coach_via_claude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.coach_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (id in SUGGESTIONS) {
                            val text = stringResource(id)
                            AssistChip(onClick = { send(text) }, label = { Text(text) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            itemsIndexed(state.messages) { _, message -> Bubble(message) }
            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.coach_thinking), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.error?.let { problem ->
                item { Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.coach_hint)) },
                maxLines = 3,
            )
            Button(onClick = { send(draft) }, enabled = draft.isNotBlank() && !state.busy) { Text(stringResource(R.string.coach_send)) }
        }
    }
}

@Composable
private fun Bubble(message: CoachMessage) {
    val mine = message.role == "user"
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .fillMaxWidth(if (mine) 0.85f else 1f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (mine) colors.primaryContainer else colors.surfaceContainerHigh)
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mine) {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
                } else {
                    MarkdownText(message.text)
                    if (message.provider != null) {
                        Text(
                            stringResource(R.string.coach_footer, message.provider, message.model ?: "–"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
