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
        assertEquals(17, SharkSpecies.entries.map { it.id }.toSet().size)
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
        assertEquals(14, state.frame(SharkMotion.SWIM, 0f, 29, 1218))
        val longRunning = SharkAnimationState(1_000_000_000.0)
        longRunning.advance(0.04f, 100)
        for ((frames, duration) in listOf(29 to 1218, 24 to 1008, 72 to 3024)) {
            val expected = ((1_000_000_040.0 % duration) / duration * frames).toInt()
            assertEquals(expected, longRunning.frame(SharkMotion.SWIM, 0f, frames, duration))
        }
    }
}


