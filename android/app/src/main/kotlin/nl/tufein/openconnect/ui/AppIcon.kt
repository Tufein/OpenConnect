package nl.tufein.openconnect.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R

/**
 * The icons the app can have on the launcher (Settings, App icon). Each one is an activity alias in the manifest with
 * its own adaptive icon (res/mipmap-anydpi-v26/ic_launcher*.xml). The foregrounds are smooth vector drawings made by
 * android/tools/launcher_icons.py: one rounded heart with a pulse for the six heart variants, and a rounded star and
 * stopwatch for the other two. Exactly one alias is switched on at a time, see [AppIcons].
 *
 * [background] is the solid colour behind the mark. [foreground] is the generated vector itself: the preview draws that
 * same drawable, so it cannot drift from what the launcher shows.
 */
enum class AppIcon(
    val key: String,
    /** The name of the activity alias in the manifest, relative to the app's package. */
    val alias: String,
    @StringRes val label: Int,
    val background: Color,
    @DrawableRes val foreground: Int,
) {
    HANDHELD("handheld", "IconHandheld", R.string.icon_handheld, Color(0xFFF4EFE4), R.drawable.ic_launcher_foreground),
    NIGHT("night", "IconNight", R.string.icon_night, Color(0xFF12150F), R.drawable.ic_launcher_night_foreground),
    GAMEBOY("gameboy", "IconGameBoy", R.string.icon_gameboy, Color(0xFF263B2C), R.drawable.ic_launcher_gameboy_foreground),
    AMBER("amber", "IconAmber", R.string.icon_amber, Color(0xFF251B11), R.drawable.ic_launcher_amber_foreground),
    SYNTHWAVE("synthwave", "IconSynthwave", R.string.icon_synthwave, Color(0xFF1A1424), R.drawable.ic_launcher_synthwave_foreground),
    CANDY("candy", "IconCandy", R.string.icon_candy, Color(0xFFF5EDE7), R.drawable.ic_launcher_candy_foreground),
    STAR("star", "IconStar", R.string.icon_star, Color(0xFF1D2821), R.drawable.ic_launcher_star_foreground),
    STOPWATCH("stopwatch", "IconStopwatch", R.string.icon_stopwatch, Color(0xFFEEEAE0), R.drawable.ic_launcher_stopwatch_foreground);

    companion object {
        fun of(key: String?): AppIcon = entries.firstOrNull { it.key == key } ?: HANDHELD
    }
}

/**
 * Switching the launcher icon. Android shows one launcher entry per enabled alias, so choosing an icon switches that
 * alias on and the others off. The state lives in the system (it survives clearing the app's data), so the current
 * icon is read from there and not from the settings.
 */
object AppIcons {
    private const val PACKAGE = "nl.tufein.openconnect"

    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, "$PACKAGE.${icon.alias}")

    private fun isOn(pm: PackageManager, name: ComponentName): Boolean = try {
        when (pm.getComponentEnabledSetting(name)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> pm.getActivityInfo(name, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    /** The icon that is on now; "handheld" when the system cannot tell. */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { isOn(pm, component(context, it)) } ?: AppIcon.HANDHELD
    }

    /** Switches to [icon]. The new one goes on before the old ones go off, so the app never has no launcher entry. */
    fun select(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        return try {
            pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            for (other in AppIcon.entries) {
                if (other != icon) {
                    pm.setComponentEnabledSetting(component(context, other), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * An adaptive icon as the launcher shows it: the generated foreground vector over the same background, cropped to the
 * central 72 of its 108 units. The vector is the one in res/drawable, so the preview and the launcher use one drawing.
 */
@Composable
fun AppIconPreview(icon: AppIcon, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(icon.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon.foreground),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                // Show the central 72 of 108 units, the part the launcher mask keeps, instead of the full bleed.
                .scale(108f / 72f),
        )
    }
}
