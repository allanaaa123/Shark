package com.example.sharkoverlay

enum class PetFilter(val label: String) { ALL("全部"), SHARKS("鯊魚"), RAYS("魟魚"), WHALES("鯨豚"), BOSSES("霸主") }

data class RemovedPet(val index: Int, val config: SharkConfig)

/** Stable identity keeps edits, filtering, deletion and desktop reconciliation consistent. */
class PetListModel(initial: List<SharkConfig>) {
    val items = BossRules.normalize(initial).toMutableList()
    val boss: SharkConfig? get() = items.firstOrNull { it.species.isBoss }

    fun chooseBoss(species: SharkSpecies?): Boolean {
        require(species == null || species.isBoss)
        val current = boss
        if (current?.species == species) return true
        if (species == null) { items.removeAll { it.species.isBoss }; return true }
        if (current == null && items.size >= SharkConfig.MAX_PETS) return false
        val next = SharkConfig(species=species,id=current?.id ?: java.util.UUID.randomUUID().toString())
        if (current == null) items.add(next) else items[items.indexOf(current)] = next
        return true
    }

    fun canRestore(removed: List<RemovedPet>): Boolean =
        items.size+removed.size <= SharkConfig.MAX_PETS &&
        items.count { it.species.isBoss }+removed.count { it.config.species.isBoss } <= 1


    fun filtered(query: String, filter: PetFilter): List<SharkConfig> {
        val term = query.trim()
        return items.filter { pet ->
            val category = when (filter) {
                PetFilter.ALL -> true
                PetFilter.SHARKS -> !pet.species.isRay && !pet.species.isBoss && pet.species != SharkSpecies.ORCA
                PetFilter.RAYS -> pet.species.isRay
                PetFilter.WHALES -> pet.species == SharkSpecies.ORCA
                PetFilter.BOSSES -> pet.species.isBoss
            }
            category && (term.isEmpty() || pet.species.displayName.contains(term, true) || pet.species.id.contains(term, true))
        }
    }

    fun add(pets: List<SharkConfig>) {
        require(pets.none { it.species.isBoss }) { "Choose bosses through the shared slot" }
        require(items.size + pets.size <= SharkConfig.MAX_PETS)
        require((items + pets).map { it.id }.distinct().size == items.size + pets.size)
        items.addAll(pets)
    }

    fun update(ids: Set<String>, size: Int? = null, speed: Int? = null) {
        for (pet in items) if (pet.id in ids && !pet.species.isBoss) {
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
        if (!canRestore(removed)) return 0
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
