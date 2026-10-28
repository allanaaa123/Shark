package com.example.sharkoverlay

import kotlin.random.Random

object RandomPetBatch {
    fun remainingSlots(existingCount: Int): Int =
        (SharkConfig.MAX_PETS - existingCount.coerceAtLeast(0)).coerceAtLeast(0)

    fun generate(count: Int, existingCount: Int, random: Random = Random.Default): List<SharkConfig> {
        require(existingCount in 0..SharkConfig.MAX_PETS)
        require(count in 1..remainingSlots(existingCount))
        return List(count) {
            SharkConfig(species = SharkSpecies.entries[random.nextInt(SharkSpecies.entries.size)])
        }
    }
}
