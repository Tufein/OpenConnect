package nl.tufein.openconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import nl.tufein.openconnect.R

/** What a watch face can show; each one is a value the watch itself knows, so a face needs no phone to fill it. */
enum class WatchField(val key: String, val label: Int) {
    BATTERY("battery", R.string.wf_field_battery),
    HEART("heart", R.string.wf_field_heart),
    STRESS("stress", R.string.wf_field_stress),
    SLEEP("sleep", R.string.wf_field_sleep),
    STEPS("steps", R.string.wf_field_steps);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }

        /** Four different fields, in order; anything unusable falls back to the defaults. */
        fun parse(text: String?): List<WatchField> {
            val list = text.orEmpty().split(',').mapNotNull { of(it.trim()) }.distinct().take(4)
            return if (list.size == 4) list else listOf(BATTERY, HEART, STRESS, SLEEP)
        }
    }
}

/** The accent colour of a face. [argb] is the same value the build script gets as a hex colour. */
enum class WatchColor(val key: String, val argb: Long, val label: Int) {
    MINT("mint", 0xFF5CF2C0, R.string.wf_color_mint),
    BLUE("blue", 0xFF7AA7FF, R.string.wf_color_blue),
    AMBER("amber", 0xFFFFB000, R.string.wf_color_amber),
    PINK("pink", 0xFFFF5FA2, R.string.wf_color_pink),
    GREEN("green", 0xFF9BBC0F, R.string.wf_color_green);

    val color: Color get() = Color(argb)

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: BLUE
    }
}

/** The choices for one face: which of the three designs, the accent colour and four fields (the first is the big one). */
data class WatchFaceConfig(
    val design: String = "clean",
    val color: String = "blue",
    val fields: List<String> = listOf("battery", "heart", "stress", "sleep"),
) {
    val designValue: Design get() = Design.of(design)
    val colorValue: WatchColor get() = WatchColor.of(color)
    val fieldValues: List<WatchField> get() = WatchField.parse(fields.joinToString(","))

    /** The command that builds this face with watch/build.sh (see docs/WATCH_FACES.md). */
    fun buildCommand(device: String = "fenix847mm"): String =
        "watch/build.sh --device $device --design $design --color ${colorValue.key} --fields ${fieldValues.joinToString(",") { it.key }}"
}

/** The numbers a face shows now; null shows a dash. */
data class WatchFaceData(
    val time: String,
    val date: String,
    val battery: Int?,
    val heart: Int?,
    val stress: Int?,
    val sleepHours: Double?,
    val steps: Int?,
    val stepsGoal: Int = 10_000,
) {
    fun value(field: WatchField): String = when (field) {
        WatchField.BATTERY -> battery?.toString()
        WatchField.HEART -> heart?.toString()
        WatchField.STRESS -> stress?.toString()
        WatchField.SLEEP -> sleepHours?.let { String.format(java.util.Locale.ROOT, "%d:%02d", it.toInt(), Math.round((it - it.toInt()) * 60).toInt()) }
        WatchField.STEPS -> steps?.let { Fmt.thousands(it) }
    } ?: "–"

    /** 0 to 1 for the field's ring or bar. */
    fun fraction(field: WatchField): Float = when (field) {
        WatchField.BATTERY -> (battery ?: 0) / 100f
        WatchField.HEART -> ((heart ?: 0) / 120f)
        WatchField.STRESS -> (stress ?: 0) / 100f
        WatchField.SLEEP -> ((sleepHours ?: 0.0) / 9.0).toFloat()
        WatchField.STEPS -> (steps ?: 0).toFloat() / stepsGoal.coerceAtLeast(1)
    }.coerceIn(0f, 1f)
}

private val Ink = Color(0xFF000000)
private val Tile = Color(0xFF12161B)
private val Muted = Color(0xFF9AA3AF)
private val RetroBg = Color(0xFF15123A)
private val RetroOff = Color(0xFF2E2970)

/** The signal colour of a field, the same ones the app uses for the same numbers. */
private fun fieldColor(field: WatchField, accent: Color, m: MetricColors): Color = when (field) {
    WatchField.BATTERY -> m.battery
    WatchField.HEART -> m.heart
    WatchField.STRESS -> m.stress
    WatchField.SLEEP -> m.sleep
    WatchField.STEPS -> accent
}

/**
 * A round preview of what the watch face will look like, drawn with the app's own numbers. All sizes are given for a
 * 454 px watch (the fenix 8 AMOLED) and scaled to [size]. It is a drawing: the real face is built from watch/ (Monkey C).
 */
@Composable
fun WatchFacePreview(config: WatchFaceConfig, data: WatchFaceData, modifier: Modifier = Modifier, size: Dp = 220.dp) {
    val s = size.value / 454f
    val accent = config.colorValue.color
    val fields = config.fieldValues
    val metrics = OpenConnectColors.metrics
    val design = config.designValue
    val bg = if (design == Design.RETRO) RetroBg else Ink
    val description = stringResource(R.string.wf_preview_description, stringResource(designName(design)), data.time)
    Box(
        modifier.size(size).clip(CircleShape).background(bg).border(4.dp, Color(0xFF2A2D33), CircleShape).semantics { contentDescription = description },
    ) {
        when (design) {
            Design.CLEAN -> CleanFace(s, accent, fields, data, metrics)
            Design.DASHBOARD -> DashboardFace(s, accent, fields, data, metrics)
            Design.RETRO -> RetroFace(s, accent, fields, data, metrics)
        }
    }
}

@Composable
private fun Num(text: String, size: Float, s: Float, color: Color = Color.White, weight: FontWeight = FontWeight.Bold, mono: Boolean = false, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Text(
        text, color = color, fontSize = (size * s).sp, lineHeight = (size * s * 1.05f).sp, fontWeight = weight, textAlign = align, maxLines = 1,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, modifier = modifier,
    )
}

@Composable
private fun CleanFace(s: Float, accent: Color, fields: List<WatchField>, d: WatchFaceData, m: MetricColors) {
    val main = fields[0]
    val ring = fieldColor(main, accent, m)
    Canvas(Modifier.fillMaxSize()) {
        val w = 16f * s * 2.2f
        val inset = w / 2 + 6.dp.toPx()
        val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
        drawArc(ring.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
        drawArc(ring, -90f, 360f * d.fraction(main), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Num(d.date, 20f, s, Muted, FontWeight.Normal, modifier = Modifier.padding(top = (70 * s).dp))
        Num(d.time, 112f, s)
        Num(stringResource(main.label) + " " + d.value(main), 22f, s, ring, FontWeight.SemiBold)
        Row(Modifier.padding(top = (24 * s).dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((40 * s).dp)) {
            for (f in fields.drop(1).take(2)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Num(d.value(f), 34f, s, if (f == WatchField.STEPS) Color.White else fieldColor(f, accent, m))
                    Num(stringResource(f.label), 16f, s, Muted, FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun DashboardFace(s: Float, accent: Color, fields: List<WatchField>, d: WatchFaceData, m: MetricColors) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Num(d.time, 56f, s, weight = FontWeight.ExtraBold, modifier = Modifier.padding(top = (44 * s).dp))
        Column(Modifier.padding(top = (8 * s).dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((10 * s).dp)) {
            for (row in fields.chunked(2)) {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((10 * s).dp)) {
                    for (f in row) {
                        Column(
                            Modifier.width((150 * s).dp).height((116 * s).dp).clip(RoundedCornerShape((20 * s).dp)).background(Tile).padding((12 * s).dp, (10 * s).dp),
                        ) {
                            Num(stringResource(f.label), 14f, s, fieldColor(f, accent, m), FontWeight.SemiBold, align = TextAlign.Start)
                            Num(d.value(f), 40f, s, weight = FontWeight.ExtraBold, align = TextAlign.Start)
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(top = (18 * s).dp).width((250 * s).dp).height((10 * s).dp).clip(RoundedCornerShape(50)).background(Color(0xFF22272E))) {
            Box(Modifier.height((10 * s).dp).width((250 * s * d.fraction(WatchField.STEPS)).dp).background(accent))
        }
    }
}

@Composable
private fun RetroFace(s: Float, accent: Color, fields: List<WatchField>, d: WatchFaceData, m: MetricColors) {
    val main = fields[0]
    val lit = Math.round(d.fraction(main) * 10)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Num(stringResource(R.string.wf_retro_level), 13f, s, accent, mono = true, modifier = Modifier.padding(top = (62 * s).dp))
        Num(d.time, 58f, s, mono = true, modifier = Modifier.padding(top = (12 * s).dp))
        Num(stringResource(main.label).uppercase(), 11f, s, Color(0xFFA9A4D6), FontWeight.Normal, mono = true, modifier = Modifier.padding(top = (26 * s).dp))
        Row(Modifier.padding(top = (8 * s).dp).width((290 * s).dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((4 * s).dp)) {
            for (i in 0 until 10) Box(Modifier.weight(1f).height((22 * s).dp).background(if (i < lit) accent else RetroOff))
        }
        Column(Modifier.padding(top = (20 * s).dp), horizontalAlignment = Alignment.Start) {
            for (f in fields.drop(1)) {
                Num(stringResource(f.label).uppercase() + " " + d.value(f), 12f, s, fieldColor(f, accent, m), FontWeight.Normal, mono = true, align = TextAlign.Start, modifier = Modifier.padding(bottom = (6 * s).dp))
            }
        }
    }
}
