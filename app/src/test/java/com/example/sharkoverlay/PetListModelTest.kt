package com.example.sharkoverlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PetListModelTest {
    @Test fun filteringNeverChangesIdentityAndEditsOnlySelectedPets() {
        val pets=listOf(SharkConfig(species=SharkSpecies.TIGER),SharkConfig(species=SharkSpecies.BLUE_SPOTTED_RAY),
            SharkConfig(species=SharkSpecies.ORCA))
        val model=PetListModel(pets)
        assertEquals(pets.map { it.id },model.items.map { it.id })
        assertEquals(listOf(pets[0].id),model.filtered("虎",PetFilter.SHARKS).map { it.id })
        assertEquals(listOf(pets[1].id),model.filtered("",PetFilter.RAYS).map { it.id })
        assertEquals(listOf(pets[2].id),model.filtered("",PetFilter.WHALES).map { it.id })
        model.update(setOf(pets[1].id),size=150)
        assertEquals(150,model.items[1].size)
        assertEquals(100,model.items[1].speed)
        assertEquals(100,model.items[0].size)
    }

    @Test fun deleteAndUndoRestoreTheOriginalOrderAndSeparateEqualLookingPets() {
        val model=PetListModel(List(5) { SharkConfig() })
        val ids=model.items.map { it.id }
        val removed=model.remove(setOf(ids[1],ids[3]))
        assertEquals(listOf(ids[0],ids[2],ids[4]),model.items.map { it.id })
        assertEquals(2,model.restore(removed))
        assertEquals(ids,model.items.map { it.id })
        assertEquals(0,model.restore(removed))
    }

    @Test fun addingAndRestoringCannotExceedFiftyOrDuplicateIdentities() {
        val model=PetListModel(List(50) { SharkConfig() })
        val removed=model.remove(setOf(model.items.last().id))
        model.add(RandomPetBatch.generate(1,49,Random(7)))
        assertEquals(0,model.restore(removed))
        try { model.add(listOf(SharkConfig())); fail("capacity") } catch (_: IllegalArgumentException) { }
        assertEquals(50,model.items.size)
    }

    @Test fun specifiedRayBatchKeepsSelectedSpeciesAndNormalVariation() {
        val batch=RandomPetBatch.generate(50,0,Random(7),BatchVariation.NORMAL,SharkSpecies.MANTA_RAY)
        assertTrue(batch.all { it.species==SharkSpecies.MANTA_RAY })
        assertTrue(batch.map { it.size }.distinct().size>1)
        assertEquals(50,batch.map { it.id }.distinct().size)
    }
}
