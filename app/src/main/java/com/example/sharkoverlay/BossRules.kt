package com.example.sharkoverlay

/** One shared slot across all three boss species, including persistence and undo paths. */
object BossRules {
    fun normalize(pets: List<SharkConfig>): List<SharkConfig> {
        var seen = false
        return pets.take(SharkConfig.MAX_PETS).mapNotNull { pet ->
            if (!pet.species.isBoss) pet.copy()
            else if (seen) null
            else { seen = true; pet.copy(size=100,speed=100) }
        }
    }
}
