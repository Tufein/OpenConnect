package nl.tufein.openconnect.ui

import androidx.annotation.StringRes
import nl.tufein.openconnect.R

/**
 * The card a person can put under the battery on the front page. The key is what is stored in Settings (the same keys
 * as SettingsStore.HOME_METRICS); the label is what the picker and the card call it. Calories are the default, so a fresh
 * install looks as it did before the choice existed. [of] never throws: a key from a newer version or a typo falls back
 * to calories.
 */
enum class HomeMetric(val key: String, @StringRes val label: Int) {
    CALORIES("calories", R.string.active_kcal),
    STEPS("steps", R.string.steps),
    RESTING_HR("resting_hr", R.string.resting_hr),
    STRESS("stress", R.string.stress),
    SLEEP("sleep", R.string.sleep),
    READINESS("readiness", R.string.readiness_title),
    ACTIVITY("activity", R.string.oura_activity);

    companion object {
        const val DEFAULT = "calories"

        fun of(key: String?): HomeMetric = entries.firstOrNull { it.key == key } ?: CALORIES
    }
}
