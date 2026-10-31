// EndoInsights (:insights) — catalog coverage, stable-id, and scale-taxonomy invariants.
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/ExploreSeriesCatalogTests.swift` (9 cases).
// These unit tests carry the keystone's contract:
//   • The catalog covers EVERY loggable DaySnapshot field (per-symptom-type, per-pain-region,
//     per-food-tag enumerated FROM `entries`, plus every singleton).
//   • Ids are unique (hashCode-on-id has no collisions).
//   • ScaleKind is correct per series, and bleeding appears as BOTH quantitative AND presence.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreSeriesCatalogTests {

    private val ids: Set<String> get() = ExploreSeriesCatalog.all.map { it.id }.toSet()

    private fun series(id: String): ExploreSeries? =
        ExploreSeriesCatalog.all.firstOrNull { it.id == id }

    // MARK: Coverage — enumerated FROM `entries` (cannot drift)

    @Test
    fun catalogCoversEverySymptomType() {
        for (type in SymptomType.entries) {
            val id = "symptom.${type.raw}.severity"
            assertTrue(ids.contains(id), "Missing catalog series for symptom ${type.raw}")
            // exactly one entry per symptom type
            assertEquals(1, ExploreSeriesCatalog.all.count { it.id == id })
        }
    }

    @Test
    fun catalogCoversEveryBodyRegion() {
        for (region in BodyRegion.entries) {
            val id = "pain.${region.raw}.severity"
            assertTrue(ids.contains(id), "Missing catalog series for pain region ${region.raw}")
            assertEquals(1, ExploreSeriesCatalog.all.count { it.id == id })
        }
    }

    @Test
    fun catalogCoversEveryFoodTag() {
        for (tag in FoodTag.entries) {
            val id = "food.${tag.raw}"
            assertTrue(ids.contains(id), "Missing catalog series for food tag ${tag.raw}")
            assertEquals(1, ExploreSeriesCatalog.all.count { it.id == id })
        }
    }

    @Test
    fun catalogHasSingletonFields() {
        val singletons = listOf(
            "sleep.hours", "activity.minutes", "stress", "mood",
            "bleeding.intensity", "bleeding.event", "clots", "prn", "medication.taken",
        )
        for (id in singletons) {
            assertTrue(ids.contains(id), "Missing singleton catalog series $id")
        }
    }

    // MARK: Stable ids (hashCode-on-id proves no collisions)

    @Test
    fun catalogIdsAreUnique() {
        assertEquals(
            ExploreSeriesCatalog.all.size,
            ExploreSeriesCatalog.all.map { it.id }.toSet().size,
            "Catalog ids must be unique — a collision would silently merge two series under Set<ExploreSeries>.",
        )
    }

    @Test
    fun seriesHashableCollapsesOnSharedId() {
        // Two series with the same id are equal and hash-collide (id-only conformance).
        val a = ExploreSeries("x", "A", "", ScaleKind.ORDINAL_0_TO_10, ExploreSeriesField.Stress)
        val b = ExploreSeries("x", "B different", "of 10", ScaleKind.ORDINAL_1_TO_5, ExploreSeriesField.Mood)
        assertEquals(a, b, "Identical ids must compare equal regardless of other metadata.")
        assertEquals(1, setOf(a, b).size, "Identical ids must collapse in a Set.")

        val c = ExploreSeries("y", "A", "", ScaleKind.ORDINAL_0_TO_10, ExploreSeriesField.Stress)
        assertNotEquals(a, c, "Distinct ids must compare unequal.")
        assertEquals(2, setOf(a, c).size)
    }

    // MARK: Scale taxonomy correctness

    @Test
    fun scaleKindsAreCorrect() {
        // per-symptom / per-pain / stress → ORDINAL_0_TO_10
        for (type in SymptomType.entries) {
            assertEquals(ScaleKind.ORDINAL_0_TO_10, series("symptom.${type.raw}.severity")?.scaleKind)
        }
        for (region in BodyRegion.entries) {
            assertEquals(ScaleKind.ORDINAL_0_TO_10, series("pain.${region.raw}.severity")?.scaleKind)
        }
        assertEquals(ScaleKind.ORDINAL_0_TO_10, series("stress")?.scaleKind)

        // mood → ORDINAL_1_TO_5
        assertEquals(ScaleKind.ORDINAL_1_TO_5, series("mood")?.scaleKind)

        // sleep / activity → continuous
        assertEquals(ScaleKind.CONTINUOUS_HOURS, series("sleep.hours")?.scaleKind)
        assertEquals(ScaleKind.CONTINUOUS_MINUTES, series("activity.minutes")?.scaleKind)

        // bleeding.intensity → ORDINAL_BLEEDING
        assertEquals(ScaleKind.ORDINAL_BLEEDING, series("bleeding.intensity")?.scaleKind)

        // presence-kind series → PRESENCE_EVENT
        for (id in listOf("bleeding.event", "clots", "prn", "medication.taken")) {
            assertEquals(ScaleKind.PRESENCE_EVENT, series(id)?.scaleKind)
        }
        for (tag in FoodTag.entries) {
            assertEquals(ScaleKind.PRESENCE_EVENT, series("food.${tag.raw}")?.scaleKind)
        }
    }

    @Test
    fun isQuantitativeMatchesScaleKind() {
        for (s in ExploreSeriesCatalog.all) {
            assertEquals(s.scaleKind != ScaleKind.PRESENCE_EVENT, s.scaleKind.isQuantitative)
            // presence series are NOT quantitative; everything else IS
            if (s.scaleKind == ScaleKind.PRESENCE_EVENT) {
                assertFalse(s.scaleKind.isQuantitative)
            } else {
                assertTrue(s.scaleKind.isQuantitative)
            }
        }
    }

    @Test
    fun bleedingAppearsAsBothQuantitativeAndPresence() {
        // Both framings catalogued, each suppression-gated downstream.
        val intensity = series("bleeding.intensity")
        val event = series("bleeding.event")
        assertEquals(ScaleKind.ORDINAL_BLEEDING, intensity?.scaleKind)
        assertEquals(true, intensity?.scaleKind?.isQuantitative)
        assertEquals(ScaleKind.PRESENCE_EVENT, event?.scaleKind)
        assertEquals(false, event?.scaleKind?.isQuantitative)
    }
}
