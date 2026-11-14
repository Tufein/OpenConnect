package nl.tufein.openconnect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pixel art as text: every `#` is one filled square of the grid, every other character is empty. The icons of the app are
 * drawn from these grids (see [PixelIcon]) instead of coming from an icon pack, so they share one 12 by 12 grid and look
 * like they belong on a handheld console.
 */
@Immutable
class PixelArt(val rows: List<String>) {
    val columns: Int get() = rows.maxOf { it.length }

    /** The same picture flipped left to right. */
    fun mirrored(): PixelArt = PixelArt(rows.map { it.padEnd(columns, '.').reversed() })
}

/** Draws [art] in one colour; the squares are snapped to whole pixels so no hairline gaps appear between them. */
@Composable
fun PixelIcon(
    art: PixelArt,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(described).size(size)) {
        drawPixels(art.rows) { c -> if (c == '#') tint else null }
    }
}

/** Fills the squares of [rows] that [colorOf] gives a colour for; a run of equal squares in one row is one rectangle. */
internal fun DrawScope.drawPixels(rows: List<String>, colorOf: (Char) -> Color?) {
    val columns = rows.maxOf { it.length }
    val cell = size.minDimension / maxOf(columns, rows.size)
    val offX = (size.width - cell * columns) / 2f
    val offY = (size.height - cell * rows.size) / 2f
    rows.forEachIndexed { y, row ->
        var x = 0
        while (x < row.length) {
            val color = colorOf(row[x])
            if (color == null) {
                x++
                continue
            }
            var end = x
            while (end < row.length && row[end] == row[x]) end++
            val left = Math.round(offX + x * cell).toFloat()
            val right = Math.round(offX + end * cell).toFloat()
            val top = Math.round(offY + y * cell).toFloat()
            val bottom = Math.round(offY + (y + 1) * cell).toFloat()
            drawRect(color, Offset(left, top), Size(right - left, bottom - top))
            x = end
        }
    }
}

/** The icons of the app. */
object PixelIcons {

    /** A little house: the Home tab. */
    val Home = PixelArt(
        listOf(
            ".....##.....",
            "....####....",
            "...######...",
            "..########..",
            ".##########.",
            "############",
            ".##########.",
            ".##########.",
            ".####..####.",
            ".####..####.",
            ".####..####.",
            ".####..####.",
        ),
    )

    /** A stopwatch: the Activities tab. */
    val Stopwatch = PixelArt(
        listOf(
            "....####....",
            ".....##.....",
            "...######...",
            "..##....##..",
            ".##..##..##.",
            ".##..##..##.",
            ".##..###.##.",
            ".##......##.",
            ".##......##.",
            "..##....##..",
            "...######...",
            "............",
        ),
    )

    /** The heart of every game: the Health tab. */
    val Heart = PixelArt(
        listOf(
            "............",
            ".####..####.",
            "############",
            "############",
            "############",
            ".##########.",
            "..########..",
            "...######...",
            "....####....",
            ".....##.....",
            "............",
            "............",
        ),
    )

    /** A high-score star: the Insights tab. */
    val Star = PixelArt(
        listOf(
            ".....##.....",
            ".....##.....",
            "....####....",
            "############",
            ".##########.",
            "..########..",
            "...######...",
            "..########..",
            "..###..###..",
            ".###....###.",
            ".##......##.",
            "............",
        ),
    )

    /** Three mixer sliders: the Settings tab. */
    val Sliders = PixelArt(
        listOf(
            "............",
            ".......##...",
            "############",
            ".......##...",
            "............",
            "..##........",
            "############",
            "..##........",
            "............",
            ".....##.....",
            "############",
            ".....##.....",
        ),
    )

    /** A ring: the Oura tab. */
    val Ring = PixelArt(
        listOf(
            "....####....",
            "..########..",
            ".####..####.",
            ".###....###.",
            "###......###",
            "###......###",
            "###......###",
            "###......###",
            ".###....###.",
            ".####..####.",
            "..########..",
            "....####....",
        ),
    )

    /** The back arrow. */
    val ArrowLeft = PixelArt(
        listOf(
            "............",
            "....#.......",
            "...##.......",
            "..###.......",
            ".###########",
            "############",
            "############",
            ".###########",
            "..###.......",
            "...##.......",
            "....#.......",
            "............",
        ),
    )

    /** Points right, for 'tap for more'; [ChevronLeft] is its mirror. */
    val ChevronRight = PixelArt(
        listOf(
            "............",
            "..##........",
            "...##.......",
            "....##......",
            ".....##.....",
            "......##....",
            "......##....",
            ".....##.....",
            "....##......",
            "...##.......",
            "..##........",
            "............",
        ),
    )

    val ChevronLeft: PixelArt = ChevronRight.mirrored()

    /**
     * The mark of the app: a pixel heart with a pulse through it. `#` is the outline, `o` the fill, `y` the shine and `w` the
     * pulse. The launcher icon (res/drawable/ic_launcher_foreground.xml) is made from the same grid.
     */
    val Logo: List<String> = listOf(
        "...####..####...",
        "..#oooo##oooo#..",
        ".#ooyooooooooo#.",
        ".#oyooowoooooo#.",
        ".#oooowowooooo#.",
        ".#ooowoowooooo#.",
        "..#wwoooowoow#..",
        "...#ooooowow#...",
        "....#ooooow#....",
        ".....#oooo#.....",
        "......#oo#......",
        ".......##.......",
    )
}
