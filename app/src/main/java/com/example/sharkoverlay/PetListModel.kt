package com.example.sharkoverlay

enum class PetFilter(val label: String) { ALL("全部"), SHARKS("鯊魚"), RAYS("魟魚"), WHALES("鯨豚") }

data class RemovedPet(val index: Int, val config: SharkConfig)

/** Stable identity keeps edits, filtering, deletion and desktop reconciliation consistent. */
class PetListModel(initial: List<SharkConfig>) {
    val items = initial.take(SharkConfig.MAX_PETS).map { it.copy() }.toMutableList()

    fun filtered(query: String, filter: PetFilter): List<SharkConfig> {
        val term = query.trim()
        return items.filter { pet ->
            val category = when (filter) {
                PetFilter.ALL -> true
                PetFilter.SHARKS -> !pet.species.isRay && pet.species != SharkSpecies.ORCA
                PetFilter.RAYS -> pet.species.isRay
                PetFilter.WHALES -> pet.species == SharkSpecies.ORCA
            }
            category && (term.isEmpty() || pet.species.displayName.contains(term, true) || pet.species.id.contains(term, true))
        }
    }

    fun add(pets: List<SharkConfig>) {
        require(items.size + pets.size <= SharkConfig.MAX_PETS)
        require((items + pets).map { it.id }.distinct().size == items.size + pets.size)
        items.addAll(pets)
    }

    fun update(ids: Set<String>, size: Int? = null, speed: Int? = null) {
        for (pet in items) if (pet.id in ids) {
            if (size != null) pet.size = size.coerceIn(30, 200)
            if (speed != null) pet.speed = speed.coerceIn(20, 200)
        }
    }

    fun remove(ids: Set<String>): List<RemovedPet> {
        val removed = items.mapIndexedNotNull { index, pet ->
            if (pet.id in ids) RemovedPet(index, pet.copy()) else null
        }
        items.removeAll { it.id in ids }
        return removed
    }

    fun restore(removed: List<RemovedPet>): Int {
        var count = 0
        for (entry in removed.sortedBy { it.index }) {
            if (items.size >= SharkConfig.MAX_PETS) break
            if (items.any { it.id == entry.config.id }) continue
            items.add(entry.index.coerceIn(0, items.size), entry.config.copy())
            count++
        }
        return count
    }
}
