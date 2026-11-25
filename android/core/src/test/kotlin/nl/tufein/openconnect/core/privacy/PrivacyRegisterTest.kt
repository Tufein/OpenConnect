package nl.tufein.openconnect.core.privacy

import nl.tufein.openconnect.core.DataSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The register is the one source of privacy truth, so these tests guard its shape rather than any screen text: every
 * [DataSourceKind] is covered exactly once, every subject has the parts the page needs, a removed subject sends
 * nothing, keys are stable and every field that crosses a network boundary names the place it travels.
 */
class PrivacyRegisterTest {
    private val subjects = PrivacyRegister.subjects

    @Test
    fun everyDataSourceKindIsCoveredExactlyOnce() {
        val covered = subjects.flatMap { it.dataSources }
        assertEquals(DataSourceKind.entries.toSet(), covered.toSet(), "every data source kind needs a privacy subject")
        assertEquals(covered.size, covered.toSet().size, "a data source kind must not be claimed by two subjects")
    }

    @Test
    fun notificationsCoverNoDataSourceButAreARealSubject() {
        val notifications = PrivacyRegister.bySubject.getValue(PrivacySubject.NOTIFICATIONS)
        assertEquals(emptySet(), notifications.dataSources)
        assertTrue(notifications.operations.isNotEmpty())
    }

    @Test
    fun everySubjectHasAnOperationTriggerDestinationStoreAndRequirement() {
        for (spec in subjects) {
            assertTrue(spec.operations.isNotEmpty(), "${spec.subject.key} has no operation")
            assertTrue(spec.stores.isNotEmpty(), "${spec.subject.key} has no store")
            assertTrue(spec.requirements.isNotEmpty(), "${spec.subject.key} has no requirement")
            for (operation in spec.operations) {
                assertTrue(operation.id.isNotBlank(), "${spec.subject.key} has an operation without an id")
                assertTrue(operation.trigger.key.isNotBlank(), "${spec.subject.key}/${operation.id} has no trigger")
                assertTrue(operation.destination.key.isNotBlank(), "${spec.subject.key}/${operation.id} has no destination")
            }
        }
    }

    @Test
    fun removedSubjectsAreLegacyAndSendNothing() {
        for (spec in subjects.filter { it.legacy }) {
            assertEquals(PrivacyGroup.REMOVED, spec.group, "${spec.subject.key} is legacy but not in the removed group")
            for (operation in spec.operations) {
                assertEquals(PrivacyTrigger.NONE, operation.trigger, "${spec.subject.key}/${operation.id} still has a trigger")
                assertEquals(emptyList(), operation.sends, "${spec.subject.key}/${operation.id} still sends fields")
                assertEquals(emptyList(), operation.receives, "${spec.subject.key}/${operation.id} still receives fields")
                assertEquals(emptyList(), operation.destination.hosts, "${spec.subject.key}/${operation.id} still names a host")
            }
        }
        val legacyKinds = subjects.filter { it.legacy }.flatMap { it.dataSources }.toSet()
        assertEquals(setOf(DataSourceKind.WHOOP, DataSourceKind.HUME), legacyKinds)
        assertTrue(PrivacyRegister.activeHosts.none { "whoop" in it || "hume" in it }, "a removed band still has an active host")
    }

    @Test
    fun activeSubjectsHaveARealTriggerAndAreNotMarkedLegacy() {
        for (spec in subjects.filterNot { it.legacy }) {
            assertFalse(spec.group == PrivacyGroup.REMOVED, "${spec.subject.key} is active but in the removed group")
            for (operation in spec.operations) {
                assertFalse(operation.trigger == PrivacyTrigger.NONE, "${spec.subject.key}/${operation.id} has no real trigger")
            }
        }
    }

    @Test
    fun subjectAndOperationIdsAreUnique() {
        val subjectKeys = subjects.map { it.subject.key }
        assertEquals(subjectKeys.size, subjectKeys.toSet().size, "duplicate subject key")
        assertEquals(subjects.size, subjects.map { it.subject }.toSet().size, "duplicate subject")
        for (spec in subjects) {
            val operationIds = spec.operations.map { it.id }
            assertEquals(operationIds.size, operationIds.toSet().size, "${spec.subject.key} has duplicate operation ids")
        }
    }

    @Test
    fun enumKeysAreUnique() {
        val groups = PrivacyGroup.entries.map { it.key }
        val subjects = PrivacySubject.entries.map { it.key }
        val places = PrivacyPlace.entries.map { it.key }
        val triggers = PrivacyTrigger.entries.map { it.key }
        val stores = PrivacyStore.entries.map { it.key }
        val never = PrivacyNever.entries.map { it.key }
        val requirements = PrivacyRequirement.entries.map { it.key }
        assertEquals(groups.size, groups.toSet().size, "duplicate group key")
        assertEquals(subjects.size, subjects.toSet().size, "duplicate subject key")
        assertEquals(places.size, places.toSet().size, "duplicate place key")
        assertEquals(triggers.size, triggers.toSet().size, "duplicate trigger key")
        assertEquals(stores.size, stores.toSet().size, "duplicate store key")
        assertEquals(never.size, never.toSet().size, "duplicate never key")
        assertEquals(requirements.size, requirements.toSet().size, "duplicate requirement key")
    }

    @Test
    fun fieldKeysAreUniqueAndOperationsUseTheCatalogueEntries() {
        val fieldKeys = PrivacyFields.all.map { it.key }
        assertEquals(fieldKeys.size, fieldKeys.toSet().size, "duplicate field key in the catalogue")
        val used = subjects.flatMap { it.operations }.flatMap { it.sends + it.receives }
        for (field in used) {
            assertTrue(
                PrivacyFields.all.any { it === field },
                "field ${field.key} is not the stable catalogue entry; operations must reuse PrivacyFields",
            )
        }
    }

    @Test
    fun everyFieldCrossingANetworkBoundaryHasAWirePath() {
        for (spec in subjects) {
            for (operation in spec.operations) {
                if (operation.destination.hosts.isEmpty()) continue
                for (field in operation.sends + operation.receives) {
                    assertTrue(
                        field.wire != null,
                        "${spec.subject.key}/${operation.id}: ${field.key} crosses to ${operation.destination.key} without a wire path",
                    )
                }
            }
        }
    }

    @Test
    fun theCompatibilityViewIsDerivedFromTheActiveDestinations() {
        assertEquals(listOf("garmin", "oura", "claude", "gemini", "github"), PrivacyRegister.called.keys.toList())
        assertEquals(listOf("personal.connectapi.garmin.com"), PrivacyRegister.called.getValue("garmin"))
        assertEquals(listOf("cloud.ouraring.com", "api.ouraring.com"), PrivacyRegister.called.getValue("oura"))
        assertEquals(listOf("api.anthropic.com"), PrivacyRegister.called.getValue("claude"))
        assertEquals(listOf("generativelanguage.googleapis.com"), PrivacyRegister.called.getValue("gemini"))
        assertEquals(listOf("api.github.com", "github.com"), PrivacyRegister.called.getValue("github"))
        assertEquals(PrivacyRegister.called.values.flatten().toSet(), PrivacyRegister.activeHosts)
        assertTrue(PrivacyRegister.hostsFor(PrivacyPlace.YOUR_WATCH).isEmpty())
        assertTrue(PrivacyRegister.hostsFor(PrivacyPlace.THIS_PHONE).isEmpty())
    }

    @Test
    fun wirePathsResolveOneOperationWithoutThrowingOnAnUnknownOne() {
        assertEquals("Authorization: Bearer", PrivacyRegister.wirePaths(PrivacySubject.GARMIN, "DAILY")["ACCESS_TOKEN"])
        assertEquals("query.startDate,query.endDate / query.start_date,query.end_date", PrivacyRegister.wirePaths(PrivacySubject.GARMIN, "DAILY")["DATE_RANGE"])
        assertTrue(PrivacyRegister.wirePaths(PrivacySubject.GARMIN, "NOPE").isEmpty())
    }
}
