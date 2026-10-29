package com.example.sharkoverlay

import kotlin.math.*
import kotlin.random.Random

object RandomPetBatch {
    fun remainingSlots(existingCount: Int): Int =
        (SharkConfig.MAX_PETS - existingCount.coerceAtLeast(0)).coerceAtLeast(0)

    fun generate(count: Int, existingCount: Int, random: Random = Random.Default,
                 variation: BatchVariation = BatchVariation.FIXED, species: SharkSpecies? = null): List<SharkConfig> {
        require(existingCount in 0..SharkConfig.MAX_PETS)
        require(count in 1..remainingSlots(existingCount))
        require(species?.isBoss != true) { "Bosses are exclusive, not batch pets" }
        val regular=SharkSpecies.regularEntries
        return List(count) {
            val selected = species ?: regular[random.nextInt(regular.size)]
            if (variation == BatchVariation.NORMAL) {
                SharkConfig(size = normalValue(random, 25.0, 30, 200),
                    speed = normalValue(random, 20.0, 20, 200), species = selected)
            } else SharkConfig(species = selected)
        }
    }

    private fun normalValue(random: Random, deviation: Double, minimum: Int, maximum: Int): Int {
        // Box-Muller, with independent draws for size and speed. Reject outliers
        // instead of clipping them onto the ends of the sliders.
        repeat(64) {
            val radius = sqrt(-2.0 * ln(1.0 - random.nextDouble()))
            val z = radius * cos(2.0 * PI * random.nextDouble())
            val value = 100.0 + deviation * z
            if (value >= minimum && value <= maximum) return value.roundToInt()
        }
        // Bounded even with a degenerate injected RNG; practically unreachable normally.
        return 100
    }
}

