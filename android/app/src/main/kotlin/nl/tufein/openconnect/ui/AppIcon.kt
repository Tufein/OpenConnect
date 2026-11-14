package nl.tufein.openconnect.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.R

/**
 * The icons the app can have on the launcher (Settings, App icon). Each one is an activity alias in the manifest with
 * its own picture (res/mipmap-anydpi-v26/ic_launcher*.xml, made by android/tools/launcher_icons.py from the same pixel
 * grids that the app draws); exactly one alias is switched on at a time, see [AppIcons].
 *
 * The colours here are the ones in that script: the preview in Settings draws the same grid in the same colours. The
 * hearts use the logo grid (`#` outline, `o` fill, `y` shine, `w` pulse); the others are one colour (`#`).
 */
enum class AppIcon(
    val key: String,
    /** The name of the activity alias in the manifest, relative to the app's package. */
    val alias: String,
    @StringRes val label: Int,
    val background: Color,
    val rows: List<String>,
    private val colours: Map<Char, Color>,
) {
    HANDHELD(
        "handheld", "IconHandheld", R.string.icon_handheld, Color(0xFFECE5D0), PixelIcons.Logo,
        mapOf('#' to Color(0xFF17150F), 'o' to Color(0xFFE8501B), 'y' to Color(0xFFFFD23F), 'w' to Color(0xFFF7F2E2)),
    ),
    NIGHT(
        "night", "IconNight", R.string.icon_night, Color(0xFF131210), PixelIcons.Logo,
        mapOf('#' to Color(0xFFF0E9D6), 'o' to Color(0xFFFF7A3D), 'y' to Color(0xFFFFD23F), 'w' to Color(0xFFF0E9D6)),
    ),
    GAMEBOY(
        "gameboy", "IconGameBoy", R.string.icon_gameboy, Color(0xFF9BBC0F), PixelIcons.Logo,
        mapOf('#' to Color(0xFF0F380F), 'o' to Color(0xFF306230), 'y' to Color(0xFF8BAC0F), 'w' to Color(0xFFCADC9F)),
    ),
    AMBER(
        "amber", "IconAmber", R.string.icon_amber, Color(0xFF0D0900), PixelIcons.Logo,
        mapOf('#' to Color(0xFFFFE2A8), 'o' to Color(0xFFFFB000), 'y' to Color(0xFFFFD45C), 'w' to Color(0xFF0D0900)),
    ),
    SYNTHWAVE(
        "synthwave", "IconSynthwave", R.string.icon_synthwave, Color(0xFF14061F), PixelIcons.Logo,
        mapOf('#' to Color(0xFF00E5FF), 'o' to Color(0xFFFF4FB8), 'y' to Color(0xFFFFD166), 'w' to Color(0xFFFFFFFF)),
    ),
    CANDY(
        "candy", "IconCandy", R.string.icon_candy, Color(0xFFFFEFF8), PixelIcons.Logo,
        mapOf('#' to Color(0xFF3A1030), 'o' to Color(0xFFFF7EB6), 'y' to Color(0xFFFFFFFF), 'w' to Color(0xFF3A1030)),
    ),
    STAR("star", "IconStar", R.string.icon_star, Color(0xFF1B2A5C), PixelIcons.Star.rows, mapOf('#' to Color(0xFFFFD23F))),
    STOPWATCH("stopwatch", "IconStopwatch", R.string.icon_stopwatch, Color(0xFF0D2233), PixelIcons.Stopwatch.rows, mapOf('#' to Color(0xFF29B6F6)));

    fun colourOf(ch: Char): Color? = colours[ch]

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

/** An icon drawn as the launcher draws it: its background with the pixel picture on it, with the corners of the app. */
@Composable
fun AppIconPreview(icon: AppIcon, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Canvas(modifier.size(size).clip(MaterialTheme.shapes.medium)) {
        drawRect(icon.background)
        // The launcher shows the middle 72 of the 108 units of an adaptive icon, so the picture is drawn a little smaller.
        val inset = this.size.minDimension * 0.16f
        val area = this.size.minDimension - 2 * inset
        val columns = icon.rows.maxOf { it.length }
        val cell = area / maxOf(columns, icon.rows.size)
        val offX = inset + (area - cell * columns) / 2f
        val offY = inset + (area - cell * icon.rows.size) / 2f
        icon.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val colour = icon.colourOf(ch)
                if (colour != null) {
                    drawRect(colour, topLeft = Offset(offX + x * cell, offY + y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
