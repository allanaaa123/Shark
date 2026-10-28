package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

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
