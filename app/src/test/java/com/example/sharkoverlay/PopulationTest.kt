package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PopulationTest {
    @Test fun savedSettingsKeepAllFiftyAndRejectOnlyExcessEntries() {
        assertEquals(50, SharkConfig.MAX_PETS)
        for (count in listOf(0, 10, 49, 50)) assertEquals(count, SharkConfig.savedCount(count))
        assertEquals(50, SharkConfig.savedCount(51))
        assertEquals(0, SharkConfig.savedCount(-1))
        assertTrue(SharkConfig.canAdd(49))
        assertFalse(SharkConfig.canAdd(50))
        assertFalse(SharkConfig.canAdd(51))
        val saved = List(50) { SharkConfig.fromSaved(speciesId = SharkSpecies.entries[it % SharkSpecies.entries.size].id) }
        assertEquals(50, saved.take(SharkConfig.savedCount(saved.size)).size)
        assertEquals(SharkSpecies.entries.toSet(), saved.map { it.species }.toSet())
    }

    @Test fun crowdDensityScalesDownWhileKeepingTurnBubblesRicher() {
        assertEquals(1f, SwimEffects.detailFor(1), 0f)
        assertEquals(1f, SwimEffects.detailFor(10), 0f)
        val detail = SwimEffects.detailFor(50)
        assertTrue(detail in 0.4f..0.5f)
        val straight = SwimEffects(Random(7))
        val turn = SwimEffects(Random(7))
        repeat(240) {
            straight.update(1f/60, it.toFloat(), 200f, 0f, 0f, 1f, detailFactor=detail)
            turn.update(1f/60, it.toFloat(), 200f, 0f, 100f, 1f, detailFactor=detail)
        }
        assertTrue(turn.emittedBubbles > straight.emittedBubbles * 4)
    }

    @Test fun fiftyMixedPetsStayFiniteWithBoundedEffectsAndValidMeshes() {
        val navigators = List(50) { SharkNavigator(500f, 500f, it * 7f, Random(it)) }
        val effects = List(50) { SwimEffects(Random(it)) }
        val mesh = FloatArray(HammerheadMotion.VERTEX_FLOATS)
        val rayMesh = FloatArray(RayMotion.VERTEX_FLOATS)
        val scratch = FloatArray((RayMotion.COLUMNS+1)*2)
        repeat(360) { tick ->
            for (i in navigators.indices) {
                val species = SharkSpecies.entries[i % SharkSpecies.entries.size]
                val nav = navigators[i]
                nav.update(1f/60, 1080f, 1920f, 150f, 50f, species)
                effects[i].update(1f/60, nav.x, nav.y, nav.angle, nav.angularVelocity, 1f,
                    detailFactor=SwimEffects.detailFor(50))
                assertTrue(nav.x.isFinite() && nav.y.isFinite() && nav.angle.isFinite())
                if (species.isRay) {
                    RayMotion.fill(rayMesh,scratch,110f,tick*0.05,species,0.8f,0.5f)
                    assertTrue(rayMesh.all { it.isFinite() })
                } else if (species != SharkSpecies.HAMMERHEAD) {
                    SpeciesSwimMotion.fill(mesh, 96f, tick * 0.05, species, 0.8f, 0.5f)
                    assertTrue(mesh.all { it.isFinite() })
                }
                assertEquals(32, effects[i].bubbles.size)
            }
        }
    }
}

