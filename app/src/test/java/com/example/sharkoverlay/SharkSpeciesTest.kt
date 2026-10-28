package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test

class SharkSpeciesTest {
    @Test fun legacyAndUnknownSpeciesRemainHammerheads() {
        assertEquals(SharkSpecies.HAMMERHEAD, SharkConfig.fromSaved().species)
        assertEquals(SharkSpecies.HAMMERHEAD, SharkConfig.fromSaved(speciesId = "future_species").species)
        assertEquals(SharkSpecies.HAMMERHEAD, SharkConfig.fromSaved(speciesId = "").species)
    }

    @Test fun everySpeciesHasAStableUniqueIdAndRetainsSettings() {
        assertEquals(3, SharkSpecies.entries.map { it.id }.toSet().size)
        for (species in SharkSpecies.entries) {
            val saved = SharkConfig.fromSaved(150, 75, species.id)
            assertEquals(species, saved.species)
            assertEquals(species, saved.copy().species)
            assertEquals(150, saved.size)
            assertEquals(75, saved.speed)
        }
        val clamped = SharkConfig.fromSaved(999, -1, "orca")
        assertEquals(200, clamped.size)
        assertEquals(20, clamped.speed)
    }

    @Test fun differentLoopDurationsDoNotResetAtHammerheadBoundary() {
        val state = SharkAnimationState(3023.0)
        state.advance(0.04f, 100)
        assertEquals(4, state.frame(SharkMotion.SWIM, 0f, 8, 1200))
        val wrap = SharkAnimationState(377999.0)
        wrap.advance(0.04f, 100)
        assertEquals(0, wrap.frame(SharkMotion.SWIM, 0f, 8, 1200))
        assertEquals(0, wrap.frame(SharkMotion.SWIM, 0f, 8, 1000))
        assertEquals(0, wrap.frame(SharkMotion.SWIM, 0f, 72, 3024))
    }
}
