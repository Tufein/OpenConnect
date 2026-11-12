package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.PlayerLevel
import nl.tufein.openconnect.core.Trophy
import nl.tufein.openconnect.core.TrophyKey
import nl.tufein.openconnect.core.TrophyUnit

private fun trophyTitle(key: TrophyKey): Int = when (key) {
    TrophyKey.FIRST_ACTIVITY -> R.string.trophy_first_activity
    TrophyKey.RUN_5K -> R.string.trophy_run_5k
    TrophyKey.RUN_10K -> R.string.trophy_run_10k
    TrophyKey.RUN_HALF -> R.string.trophy_run_half
    TrophyKey.RIDE_50K -> R.string.trophy_ride_50k
    TrophyKey.DISTANCE_100 -> R.string.trophy_distance_100
    TrophyKey.DISTANCE_500 -> R.string.trophy_distance_500
    TrophyKey.DISTANCE_1000 -> R.string.trophy_distance_1000
    TrophyKey.CLIMB_EVEREST -> R.string.trophy_climb_everest
    TrophyKey.STEP_STREAK_7 -> R.string.trophy_step_streak_7
    TrophyKey.STEP_STREAK_30 -> R.string.trophy_step_streak_30
    TrophyKey.STEPS_MILLION -> R.string.trophy_steps_million
    TrophyKey.SLEEP_STREAK_7 -> R.string.trophy_sleep_streak_7
    TrophyKey.VARIETY_3 -> R.string.trophy_variety_3
    TrophyKey.WEEK_150 -> R.string.trophy_week_150
    TrophyKey.EARLY_BIRD -> R.string.trophy_early_bird
    TrophyKey.CONSISTENT_4 -> R.string.trophy_consistent_4
}

private fun trophyBody(key: TrophyKey): Int = when (key) {
    TrophyKey.FIRST_ACTIVITY -> R.string.trophy_first_activity_body
    TrophyKey.RUN_5K -> R.string.trophy_run_5k_body
    TrophyKey.RUN_10K -> R.string.trophy_run_10k_body
    TrophyKey.RUN_HALF -> R.string.trophy_run_half_body
    TrophyKey.RIDE_50K -> R.string.trophy_ride_50k_body
    TrophyKey.DISTANCE_100 -> R.string.trophy_distance_100_body
    TrophyKey.DISTANCE_500 -> R.string.trophy_distance_500_body
    TrophyKey.DISTANCE_1000 -> R.string.trophy_distance_1000_body
    TrophyKey.CLIMB_EVEREST -> R.string.trophy_climb_everest_body
    TrophyKey.STEP_STREAK_7 -> R.string.trophy_step_streak_7_body
    TrophyKey.STEP_STREAK_30 -> R.string.trophy_step_streak_30_body
    TrophyKey.STEPS_MILLION -> R.string.trophy_steps_million_body
    TrophyKey.SLEEP_STREAK_7 -> R.string.trophy_sleep_streak_7_body
    TrophyKey.VARIETY_3 -> R.string.trophy_variety_3_body
    TrophyKey.WEEK_150 -> R.string.trophy_week_150_body
    TrophyKey.EARLY_BIRD -> R.string.trophy_early_bird_body
    TrophyKey.CONSISTENT_4 -> R.string.trophy_consistent_4_body
}

/** A short tag for the badge, in the same arcade style as the sport badges. */
private fun trophyTag(key: TrophyKey): String = when (key) {
    TrophyKey.FIRST_ACTIVITY -> "1UP"
    TrophyKey.RUN_5K -> "5K"
    TrophyKey.RUN_10K -> "10K"
    TrophyKey.RUN_HALF -> "21K"
    TrophyKey.RIDE_50K -> "50K"
    TrophyKey.DISTANCE_100 -> "100"
    TrophyKey.DISTANCE_500 -> "500"
    TrophyKey.DISTANCE_1000 -> "1K"
    TrophyKey.CLIMB_EVEREST -> "8849"
    TrophyKey.STEP_STREAK_7 -> "x7"
    TrophyKey.STEP_STREAK_30 -> "x30"
    TrophyKey.STEPS_MILLION -> "1M"
    TrophyKey.SLEEP_STREAK_7 -> "ZZZ"
    TrophyKey.VARIETY_3 -> "MIX"
    TrophyKey.WEEK_150 -> "150"
    TrophyKey.EARLY_BIRD -> "AM"
    TrophyKey.CONSISTENT_4 -> "4WK"
}

/** "4.9 / 5 km", "3 / 7 days": how far along, in the unit the trophy counts in. */
@Composable
private fun progressText(trophy: Trophy): String {
    val shown = minOf(trophy.current, trophy.target)
    fun whole(v: Double) = Fmt.thousands(Math.floor(v + 1e-9).toInt())
    return when (trophy.key.unit) {
        TrophyUnit.KM -> Fmt.oneDecimal(shown) + " / " + Fmt.oneDecimal(trophy.target) + " km"
        TrophyUnit.METRES -> whole(shown) + " / " + whole(trophy.target) + " m"
        TrophyUnit.DAYS -> whole(shown) + " / " + whole(trophy.target) + " " + stringResource(R.string.unit_days)
        TrophyUnit.STEPS -> whole(shown) + " / " + whole(trophy.target)
        TrophyUnit.SPORTS -> whole(shown) + " / " + whole(trophy.target)
        TrophyUnit.MINUTES -> whole(shown) + " / " + whole(trophy.target) + " " + stringResource(R.string.unit_minutes)
        TrophyUnit.ACTIVITIES -> whole(shown) + " / " + whole(trophy.target)
        TrophyUnit.WEEKS -> whole(shown) + " / " + whole(trophy.target) + " " + stringResource(R.string.unit_weeks)
    }
}

@Composable
fun TrophiesContent(state: TrophiesState, contentPadding: PaddingValues = PaddingValues()) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val achievements = state.achievements ?: return@LazyColumn
        item { LevelCard(achievements.player, achievements.trophies.count { it.unlocked }, achievements.trophies.size) }
        // Won trophies first, then the ones closest to being won.
        val ordered = achievements.trophies.sortedWith(compareByDescending<Trophy> { it.unlocked }.thenByDescending { it.progress })
        items(ordered, key = { it.key.name }) { TrophyCard(it) }
    }
}

@Composable
private fun LevelCard(player: PlayerLevel, won: Int, total: Int) {
    SurfaceCard(accent = MaterialTheme.colorScheme.secondary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(player.level.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.trophies_level, player.level), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.trophies_xp, player.xp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.trophies_won, won, total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(
            progress = { (player.xpIntoLevel.toFloat() / player.xpForNext.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
        )
        Text(
            stringResource(R.string.trophies_next, player.xpForNext - player.xpIntoLevel, player.level + 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.trophies_xp_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrophyCard(trophy: Trophy) {
    val gold = MaterialTheme.colorScheme.secondary
    val dim = MaterialTheme.colorScheme.outline
    val tint = if (trophy.unlocked) gold else dim
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = if (trophy.unlocked) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(trophyTag(trophy.key), style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(trophyTitle(trophy.key)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(trophyBody(trophy.key)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (trophy.unlocked) {
                    Text(
                        trophy.unlockedOn?.let { stringResource(R.string.trophy_won_on, Fmt.date(it)) } ?: stringResource(R.string.trophy_won),
                        style = MaterialTheme.typography.labelMedium,
                        color = gold,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { trophy.progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    )
                    Text(progressText(trophy), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
