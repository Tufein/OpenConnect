package nl.tufein.openconnect.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.PrivacyStatement
import nl.tufein.openconnect.core.privacy.PrivacyField
import nl.tufein.openconnect.core.privacy.PrivacyGroup
import nl.tufein.openconnect.core.privacy.PrivacyNever
import nl.tufein.openconnect.core.privacy.PrivacyOperation
import nl.tufein.openconnect.core.privacy.PrivacyPlace
import nl.tufein.openconnect.core.privacy.PrivacyRegister
import nl.tufein.openconnect.core.privacy.PrivacyRequirement
import nl.tufein.openconnect.core.privacy.PrivacyStore
import nl.tufein.openconnect.core.privacy.PrivacySubject
import nl.tufein.openconnect.core.privacy.PrivacySubjectSpec
import nl.tufein.openconnect.core.privacy.PrivacyTrigger

/**
 * "What leaves your phone": the privacy register (v1.12.0) as a page instead of prose. The index groups every subject by
 * [PrivacyGroup]; a detail page shows, operation by operation, what goes out and what comes back, then what is never
 * sent, kept or needed, with a fold of raw field names for anyone who wants the code's own words.
 *
 * The page is fixed text from [PrivacyRegister]: it is not a live traffic monitor and nothing is recorded here.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedKey?.let { key -> PrivacyRegister.subjects.firstOrNull { it.subject.key == key } }
    // The arrow and the system back button both step from a detail back to the index, never straight out of the page.
    BackHandler(enabled = selected != null) { selectedKey = null }
    if (selected == null) {
        PrivacyIndex(
            onBack = onBack,
            onOpen = { selectedKey = it.subject.key },
            contentPadding = contentPadding,
        )
    } else {
        PrivacyDetailScreen(
            spec = selected,
            onBack = { selectedKey = null },
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun PrivacyIndex(
    onBack: () -> Unit,
    onOpen: (PrivacySubjectSpec) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PixelIconButton(onClick = onBack) {
                PixelIcon(PixelIcons.ArrowLeft, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            SurfaceCard {
                Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.privacy_static_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (group in PrivacyGroup.entries) {
            val subjects = PrivacyRegister.subjects.filter { it.group == group }
            if (subjects.isEmpty()) continue
            item(key = "privacy-group-${group.key}") {
                SectionTitle(subjectGroupLabel(group))
            }
            for (spec in subjects) {
                item(key = "privacy-subject-${spec.subject.key}") {
                    PrivacySubjectRow(spec, onOpen)
                }
            }
        }
        item {
            SecondaryButton(
                onClick = { openLink(context, PrivacyStatement.STATEMENT_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_privacy_statement))
            }
        }
    }
}

/** One subject on the index: its name, the group it sits in and, for WHOOP and Hume, that only old rows remain. */
@Composable
private fun PrivacySubjectRow(spec: PrivacySubjectSpec, onOpen: (PrivacySubjectSpec) -> Unit) {
    val summary = if (spec.legacy) {
        stringResource(R.string.privacy_row_legacy, subjectGroupLabel(spec.group))
    } else {
        subjectGroupLabel(spec.group)
    }
    SurfaceCard(
        modifier = Modifier
            .testTag("privacy-subject-${spec.subject.key}")
            .tappable { onOpen(spec) },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(subjectName(spec.subject), style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OpenHint()
        }
    }
}

/**
 * One subject in full: what each operation sends and receives, what never crosses, what is stored, when it runs, what it
 * needs, and the raw field names in the technical fold. WHOOP and Hume only carry [PrivacySubjectSpec.legacy] rows.
 */
@Composable
fun PrivacyDetailScreen(
    spec: PrivacySubjectSpec,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val key = spec.subject.key
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PixelIconButton(onClick = onBack) {
                PixelIcon(PixelIcons.ArrowLeft, contentDescription = stringResource(R.string.back))
            }
            Text(
                subjectName(spec.subject),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                subjectGroupLabel(spec.group),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SurfaceCard {
                if (spec.legacy) {
                    Text(stringResource(R.string.privacy_legacy_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.privacy_legacy_body), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.privacy_static_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            FoldableCard("privacy-$key-leaves", stringResource(R.string.privacy_section_leaves)) {
                OperationFields(spec.operations.filter { it.sends.isNotEmpty() }, sending = true, emptyText = R.string.privacy_no_sends)
            }
        }
        item {
            FoldableCard("privacy-$key-comes-back", stringResource(R.string.privacy_section_comes_back)) {
                OperationFields(spec.operations.filter { it.receives.isNotEmpty() }, sending = false, emptyText = R.string.privacy_no_receives)
            }
        }
        item {
            FoldableCard("privacy-$key-never", stringResource(R.string.privacy_section_never)) {
                val never = spec.operations.flatMap { it.never }.distinct()
                BulletList(never.map { neverLabel(it) })
            }
        }
        item {
            FoldableCard("privacy-$key-stays", stringResource(R.string.privacy_section_stays)) {
                BulletList(spec.stores.map { storeLabel(it) })
            }
        }
        item {
            FoldableCard("privacy-$key-when", stringResource(R.string.privacy_section_when)) {
                BulletList(spec.operations.map { it.trigger }.distinct().map { triggerLabel(it) })
            }
        }
        item {
            FoldableCard("privacy-$key-needs", stringResource(R.string.privacy_section_needs)) {
                BulletList(spec.requirements.map { requirementLabel(it) })
            }
        }
        item {
            FoldableCard("privacy-$key-technical", stringResource(R.string.privacy_section_technical), initiallyOpen = false) {
                TechnicalBody(spec)
            }
        }
        item {
            SecondaryButton(
                onClick = { openLink(context, PrivacyStatement.STATEMENT_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_privacy_statement))
            }
        }
    }
}

/** One operation per block: where it goes, when it runs and the readable names of the fields it carries. */
@Composable
private fun OperationFields(operations: List<PrivacyOperation>, sending: Boolean, emptyText: Int) {
    if (operations.isEmpty()) {
        Text(stringResource(emptyText), style = MaterialTheme.typography.bodyMedium)
        return
    }
    for (operation in operations) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.privacy_to, placeLabel(operation.destination)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.privacy_when_line, triggerLabel(operation.trigger)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val fields = if (sending) operation.sends else operation.receives
            for (field in fields) {
                Text("• " + fieldLabel(field), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BulletList(items: List<String>) {
    if (items.isEmpty()) return
    for (item in items) Text("• " + item, style = MaterialTheme.typography.bodyMedium)
}

/**
 * The code's own words: operation id, trigger and destination keys, the exact field key and where the field travels. A
 * field without a wire path stays on the phone or comes in without a path of its own.
 */
@Composable
private fun TechnicalBody(spec: PrivacySubjectSpec) {
    Text(
        stringResource(R.string.privacy_technical_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for (operation in spec.operations) {
        Text(
            "${operation.id} · ${operation.trigger.key} → ${operation.destination.key}",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
        )
        val hosts = PrivacyRegister.hostsFor(operation.destination)
        if (hosts.isNotEmpty()) {
            Text(hosts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fields = operation.sends + operation.receives
        if (fields.isEmpty()) {
            Text(stringResource(R.string.privacy_technical_no_fields), style = MaterialTheme.typography.bodySmall)
        } else {
            for (field in fields) {
                val where = field.wire ?: stringResource(R.string.privacy_technical_local)
                Text("${field.key} → $where", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
    Text(
        stringResource(R.string.privacy_technical_stores, spec.stores.joinToString(", ") { it.key }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The app's own English field names, turned into readable words ("COACH_QUESTION" is "Coach question"). */
private val fieldWords = mapOf(
    "API" to "API",
    "AI" to "AI",
    "ID" to "ID",
    "URI" to "URI",
    "OAUTH" to "OAuth",
    "PKCE" to "PKCE",
    "APK" to "APK",
    "JSON" to "JSON",
    "SPO2" to "SpO2",
    "BLE" to "BLE",
    "FIT" to "FIT",
)

private fun fieldLabel(field: PrivacyField): String =
    field.key.split('_').mapIndexed { index, part ->
        val word = fieldWords[part] ?: part.lowercase()
        if (index == 0 && word == word.lowercase()) word.replaceFirstChar { it.titlecase() } else word
    }.joinToString(" ")

@Composable
private fun subjectName(subject: PrivacySubject): String = stringResource(
    when (subject) {
        PrivacySubject.GARMIN -> R.string.source_garmin
        PrivacySubject.OURA -> R.string.source_oura
        PrivacySubject.CLAUDE -> R.string.source_ai_claude
        PrivacySubject.GEMINI -> R.string.source_ai_gemini
        PrivacySubject.GITHUB -> R.string.source_updater
        PrivacySubject.NOTIFICATIONS -> R.string.privacy_subject_notifications
        PrivacySubject.WORKOUTS -> R.string.source_workouts_to_watch
        PrivacySubject.WATCH -> R.string.source_watch
        PrivacySubject.HEALTH_CONNECT -> R.string.source_health_connect
        PrivacySubject.FILES -> R.string.privacy_subject_files
        PrivacySubject.KEYS -> R.string.source_key_vault_lock
        PrivacySubject.REMOVED -> R.string.privacy_subject_removed
    },
)

@Composable
private fun subjectGroupLabel(group: PrivacyGroup): String = stringResource(
    when (group) {
        PrivacyGroup.LEAVES_PHONE -> R.string.privacy_group_leaves_phone
        PrivacyGroup.LEAVES_FOR_WATCH -> R.string.privacy_group_leaves_for_watch
        PrivacyGroup.STAYS_ON_PHONE -> R.string.privacy_group_stays_on_phone
        PrivacyGroup.FILES_YOU_CHOOSE -> R.string.privacy_group_files_you_choose
        PrivacyGroup.REMOVED -> R.string.privacy_group_removed
    },
)

@Composable
private fun placeLabel(place: PrivacyPlace): String = stringResource(
    when (place) {
        PrivacyPlace.GARMIN -> R.string.privacy_place_garmin
        PrivacyPlace.OURA -> R.string.privacy_place_oura
        PrivacyPlace.ANTHROPIC -> R.string.privacy_place_anthropic
        PrivacyPlace.GOOGLE -> R.string.privacy_place_google
        PrivacyPlace.GITHUB -> R.string.privacy_place_github
        PrivacyPlace.YOUR_WATCH -> R.string.privacy_place_your_watch
        PrivacyPlace.HEALTH_CONNECT -> R.string.privacy_place_health_connect
        PrivacyPlace.THIS_PHONE -> R.string.privacy_place_this_phone
        PrivacyPlace.FILE_YOU_CHOOSE -> R.string.privacy_place_file_you_choose
        PrivacyPlace.APP_YOU_CHOOSE -> R.string.privacy_place_app_you_choose
    },
)

@Composable
private fun triggerLabel(trigger: PrivacyTrigger): String = stringResource(
    when (trigger) {
        PrivacyTrigger.MANUAL_SYNC -> R.string.privacy_trigger_manual_sync
        PrivacyTrigger.APP_OPEN -> R.string.privacy_trigger_app_open
        PrivacyTrigger.BACKGROUND -> R.string.privacy_trigger_background
        PrivacyTrigger.CONNECT -> R.string.privacy_trigger_connect
        PrivacyTrigger.REPORT -> R.string.privacy_trigger_report
        PrivacyTrigger.COACH -> R.string.privacy_trigger_coach
        PrivacyTrigger.KEY_CHECK -> R.string.privacy_trigger_key_check
        PrivacyTrigger.UPDATE_CHECK -> R.string.privacy_trigger_update_check
        PrivacyTrigger.DOWNLOAD -> R.string.privacy_trigger_download
        PrivacyTrigger.NOTIFICATION -> R.string.privacy_trigger_notification
        PrivacyTrigger.WORKOUT_SEND -> R.string.privacy_trigger_workout_send
        PrivacyTrigger.FILE_EXPORT -> R.string.privacy_trigger_file_export
        PrivacyTrigger.DIAGNOSTICS -> R.string.privacy_trigger_diagnostics
        PrivacyTrigger.USER_ACTION -> R.string.privacy_trigger_user_action
        PrivacyTrigger.NONE -> R.string.privacy_trigger_none
    },
)

@Composable
private fun neverLabel(never: PrivacyNever): String = stringResource(
    when (never) {
        PrivacyNever.GPS_POINTS -> R.string.privacy_never_gps_points
        PrivacyNever.JOURNAL -> R.string.privacy_never_journal
        PrivacyNever.NOTES -> R.string.privacy_never_notes
        PrivacyNever.OWN_WORKOUT_TEXT -> R.string.privacy_never_own_workout_text
        PrivacyNever.OTHER_KEYS -> R.string.privacy_never_other_keys
        PrivacyNever.LOCATION -> R.string.privacy_never_location
        PrivacyNever.CONTACTS -> R.string.privacy_never_contacts
        PrivacyNever.CALENDAR -> R.string.privacy_never_calendar
        PrivacyNever.NOTIFICATION_TEXT_TO_SERVER -> R.string.privacy_never_notification_text_to_server
    },
)

@Composable
private fun storeLabel(store: PrivacyStore): String = stringResource(
    when (store) {
        PrivacyStore.SQLITE -> R.string.privacy_store_sqlite
        PrivacyStore.PLAIN_PREFS -> R.string.privacy_store_plain_prefs
        PrivacyStore.ENCRYPTED_PREFS -> R.string.privacy_store_encrypted_prefs
        PrivacyStore.MEMORY -> R.string.privacy_store_memory
        PrivacyStore.APP_CACHE -> R.string.privacy_store_app_cache
        PrivacyStore.USER_FILE -> R.string.privacy_store_user_file
        PrivacyStore.HEALTH_CONNECT -> R.string.privacy_store_health_connect
        PrivacyStore.WATCH -> R.string.privacy_store_watch
    },
)

@Composable
private fun requirementLabel(requirement: PrivacyRequirement): String = stringResource(
    when (requirement) {
        PrivacyRequirement.GARMIN_KEY -> R.string.privacy_needs_garmin_key
        PrivacyRequirement.OURA_LOGIN -> R.string.privacy_needs_oura_login
        PrivacyRequirement.AI_KEY -> R.string.privacy_needs_ai_key
        PrivacyRequirement.NOTIFICATION_ACCESS -> R.string.privacy_needs_notification_access
        PrivacyRequirement.WATCH_CONNECTION -> R.string.privacy_needs_watch_connection
        PrivacyRequirement.HEALTH_CONNECT_PERMISSION -> R.string.privacy_needs_health_connect_permission
        PrivacyRequirement.USER_ACTION -> R.string.privacy_needs_user_action
    },
)
