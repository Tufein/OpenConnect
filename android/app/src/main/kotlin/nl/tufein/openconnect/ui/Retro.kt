package nl.tufein.openconnect.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.tufein.openconnect.core.retro.Chiptune
import java.util.concurrent.Executors

// The retro pack (v1.15.0): scanlines over the Retro style and 8-bit tunes. Both are our own design, both are off until
// chosen in Settings, and neither adds a file to the APK: the lines are drawn and the tunes are computed on the phone.

/** One dark line every few pixels, starting at the top; empty for an empty screen or a step that would never advance. */
internal fun scanlineOffsets(heightPx: Float, stepPx: Float): List<Float> {
    if (heightPx <= 0f || stepPx <= 0f) return emptyList()
    val rows = ArrayList<Float>()
    var y = 0f
    while (y < heightPx) {
        rows += y
        y += stepPx
    }
    return rows
}

/** A tenth of black: the lines are seen, the text stays readable (one row in three loses a tenth of its light). */
private val ScanlineInk = Color.Black.copy(alpha = 0.10f)

/** Draws the scanlines of an old CRT screen over everything inside, one thin dark line every 3 dp. */
internal fun Modifier.scanlines(): Modifier = drawWithContent {
    drawContent()
    val line = 1.dp.toPx().coerceAtLeast(1f)
    for (y in scanlineOffsets(size.height, 3.dp.toPx())) {
        drawRect(ScanlineInk, topLeft = Offset(0f, y), size = Size(size.width, line))
    }
}

/** Plays one 8-bit tune. Tests pass a fake that writes down what was played; the app passes [AudioTrackChiptunePlayer]. */
fun interface ChiptunePlayer {
    fun play(tune: Chiptune.Tune)
}

/**
 * Plays a tune through Android's AudioTrack as a system sound (usage "assistance sonification"), so it follows the
 * volume of the phone's other little sounds and is quiet when those are. Rendering and playing happen on one background
 * thread, one tune at a time, so a burst of syncs never stacks sounds on top of each other.
 */
class AudioTrackChiptunePlayer : ChiptunePlayer {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "chiptune").apply { isDaemon = true } }

    override fun play(tune: Chiptune.Tune) {
        worker.execute {
            runCatching {
                val samples = Chiptune.render(tune)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Chiptune.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                try {
                    track.write(samples, 0, samples.size)
                    track.play()
                    // A static track plays on its own; wait for it to finish before letting it go.
                    Thread.sleep(tune.millis.toLong() + 50)
                    track.stop()
                } finally {
                    track.release()
                }
            }
            // A phone without a free audio output (a call, a broken mixer) simply stays quiet: a tune is never worth a crash.
        }
    }
}
