package nl.tufein.openconnect.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v0.55.0: every source and feature that touches the outside world carries a label, *Tested on a real device* (with the
 * day and the version) or *Experimental*. The list is the one in docs/ROADMAP.md, section 7, and it does not flatter.
 */
class SourceLabelsTest {
    @Test
    fun everySourceHasALabel() {
        for (kind in DataSourceKind.values()) {
            val status = SourceLabels.status(kind)
            assertEquals(kind, status.kind)
        }
        assertEquals(DataSourceKind.values().size, SourceLabels.all.size)
    }

    @Test
    fun aTestedSourceSaysWhenAndInWhichVersionAndAnExperimentalOneSaysNothing() {
        for (s in SourceLabels.all) {
            if (s.tested) {
                assertNotNull(s.testedOn, "${s.kind} is tested: on which day")
                assertTrue(s.version!!.matches(Regex("""\d+\.\d+\.\d+""")), "${s.kind} is tested: in which version")
            } else {
                assertNull(s.testedOn, "${s.kind} is experimental, so no date")
                assertNull(s.version)
            }
        }
    }

    /**
     * The honest state. To change this list there must be a real trial to point at: a date and a version, and a line in
     * docs/PUBLIC_RELEASE.md section 2. A source never goes to tested because its tests are green. The Garmin account
     * was tried live on 2026-11-12; Stefan confirmed Bluetooth and live heart rate on his fenix 8 on 2026-11-28.
     */
    @Test
    fun onlyWhatWasTriedOnARealDeviceIsLabelledTested() {
        val tested = SourceLabels.all.filter { it.tested }.map { it.kind }
        assertEquals(listOf(DataSourceKind.GARMIN_ACCOUNT, DataSourceKind.WATCH_BLUETOOTH, DataSourceKind.LIVE_HEART_RATE), tested)
        val garmin = SourceLabels.status(DataSourceKind.GARMIN_ACCOUNT)
        assertEquals(LocalDate.of(2026, 11, 12), garmin.testedOn)
        assertEquals("0.4.0", garmin.version)
        for (kind in listOf(DataSourceKind.WATCH_BLUETOOTH, DataSourceKind.LIVE_HEART_RATE)) {
            assertEquals(LocalDate.of(2026, 11, 28), SourceLabels.status(kind).testedOn, "$kind")
            assertEquals("1.13.12", SourceLabels.status(kind).version, "$kind")
        }
        // the USB cable, the ring and the AI keys were not part of that trial, so they stay experimental
        for (kind in listOf(DataSourceKind.WATCH_USB, DataSourceKind.OURA, DataSourceKind.AI_CLAUDE, DataSourceKind.HEALTH_CONNECT)) {
            assertNull(SourceLabels.status(kind).testedOn, "$kind")
        }
    }

    @Test
    fun keysAreUniqueSoTheyCanBeUsedInTexts() {
        val keys = DataSourceKind.values().map { it.key }
        assertEquals(keys.toSet().size, keys.size)
    }
}
