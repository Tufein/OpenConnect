package nl.tufein.openconnect.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.WidgetFormat
import nl.tufein.openconnect.core.WidgetSummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeWidgetViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun view(summary: WidgetSummary?, format: WidgetFormat = WidgetFormat.MEDIUM): View {
        Locale.setDefault(Locale.US)
        // RemoteViews.apply does not add the inflated layout to the parent it is given; it returns it.
        return HomeWidgetViews.build(context, summary, format).apply(context, FrameLayout(context))
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun texts(summary: WidgetSummary?): Triple<String, String, String> {
        val root = view(summary)
        return Triple(root.text(R.id.widget_readiness), root.text(R.id.widget_steps), root.text(R.id.widget_week))
    }

    @Test
    fun `readiness, steps against the goal and the week are shown`() {
        val (r, steps, week) = texts(WidgetSummary(readiness = 72, steps = 6500, stepsGoal = 10_000, weekActivities = 2, weekActivityGoal = 3))
        assertEquals("72", r)
        assertEquals("6,500 of 10,000 steps", steps)
        assertEquals("2 of 3 activities this week", week)
    }

    @Test
    fun `missing data shows a dash and no steps, a goal that is off shows only the count`() {
        assertEquals(Triple("–", "No steps yet", "0 activities this week"), texts(null))
        val (_, steps, week) = texts(WidgetSummary(null, 800, null, 4, 0))
        assertEquals("800 steps", steps)
        assertEquals("4 activities this week", week)
    }

    @Test
    fun `the small format leaves the week out`() {
        val root = view(WidgetSummary(64, 1200, 8000, 1, 3), WidgetFormat.SMALL)
        assertEquals("64", root.text(R.id.widget_readiness))
        assertEquals("1,200 of 8,000 steps", root.text(R.id.widget_steps))
        assertNull(root.findViewById<TextView>(R.id.widget_week))
        assertNull(root.findViewById<TextView>(R.id.widget_sleep))
    }

    @Test
    fun `the large format adds sleep and the resting heart rate`() {
        val root = view(
            WidgetSummary(72, 6500, 10_000, 2, 3, sleepMinutes = 7 * 60 + 8, restingHr = 52),
            WidgetFormat.LARGE,
        )
        assertEquals("72", root.text(R.id.widget_readiness))
        assertEquals("2 of 3 activities this week", root.text(R.id.widget_week))
        assertEquals("7 h 8 m of sleep", root.text(R.id.widget_sleep))
        assertEquals("Resting 52 bpm", root.text(R.id.widget_rest))
    }

    @Test
    fun `the large format says so when sleep and rest are missing`() {
        val root = view(WidgetSummary(50, 300, null, 0, 0), WidgetFormat.LARGE)
        assertEquals("No sleep yet", root.text(R.id.widget_sleep))
        assertEquals("No resting heart rate", root.text(R.id.widget_rest))
    }
}
