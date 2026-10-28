package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.math.*

class RandomPetBatchTest {
    @Test fun requestedBatchContainsIndependentEditablePetsFromKnownSpecies() {
        val pets = RandomPetBatch.generate(50, 0, Random(7))
        assertEquals(50,pets.size)
        assertTrue(pets.all { it.species in SharkSpecies.entries })
        assertTrue(pets.map { it.species }.distinct().size > 1)
        assertTrue(pets.all { it.size == 100 && it.speed == 100 })
        pets[0].size = 150
        assertTrue(pets.drop(1).all { it.size == 100 })
    }

    @Test fun savedVariationDefaultsToFixedAndNormalStaysIndependentAndBounded() {
        assertEquals(BatchVariation.FIXED,BatchVariation.fromId(null))
        assertEquals(BatchVariation.FIXED,BatchVariation.fromId("unknown"))
        for(mode in BatchVariation.entries) assertEquals(mode,BatchVariation.fromId(mode.id))
        val rng=Random(19)
        val pets=(0 until 300).flatMap {
            RandomPetBatch.generate(50,0,rng,variation=BatchVariation.NORMAL)
        }
        assertTrue(pets.all { it.size in 30..200 && it.speed in 20..200 })
        val sizeMean=pets.map { it.size }.average()
        val speedMean=pets.map { it.speed }.average()
        val sizeVariance=pets.map { (it.size-sizeMean).pow(2) }.average()
        val speedVariance=pets.map { (it.speed-speedMean).pow(2) }.average()
        val covariance=pets.map { (it.size-sizeMean)*(it.speed-speedMean) }.average()
        assertEquals(100.0,sizeMean,1.0)
        assertEquals(100.0,speedMean,1.0)
        assertTrue(sqrt(sizeVariance) in 23.0..27.0)
        assertTrue(sqrt(speedVariance) in 18.0..22.0)
        assertTrue(abs(covariance/sqrt(sizeVariance*speedVariance)) < 0.08)
        assertTrue(pets.map { it.size }.distinct().size > 50)
        assertTrue(pets.map { it.speed }.distinct().size > 50)
    }

    @Test fun remainingSlotsAndExactFiftyLimitAreRespected() {
        assertEquals(50,RandomPetBatch.remainingSlots(0))
        assertEquals(1,RandomPetBatch.remainingSlots(49))
        assertEquals(0,RandomPetBatch.remainingSlots(50))
        assertEquals(1,RandomPetBatch.generate(1,49,Random(7)).size)
        assertEquals(49,RandomPetBatch.generate(49,1,Random(7)).size)
    }

    @Test fun invalidQuantitiesCannotCreatePartialOrExcessBatches() {
        for((quantity,current) in listOf(0 to 0,-1 to 0,51 to 0,2 to 49,1 to 50)) {
            try {
                RandomPetBatch.generate(quantity,current,Random(7))
                fail("invalid request should be rejected: $quantity with $current existing")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

