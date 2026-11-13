package nl.tufein.openconnect.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.WidgetSummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeWidgetViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun texts(summary: WidgetSummary?): Triple<String, String, String> {
        Locale.setDefault(Locale.US)
        val root = HomeWidgetViews.build(context, summary).apply(context, FrameLayout(context))
        fun t(id: Int) = root.findViewById<TextView>(id).text.toString()
        return Triple(t(R.id.widget_readiness), t(R.id.widget_steps), t(R.id.widget_week))
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
}
