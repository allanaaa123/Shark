package com.example.sharkoverlay

enum class SharkSpecies(
    val id: String, val displayName: String, val assetFolder: String,
    val swimCycleMs: Double = 1600.0, val tailAmplitude: Float = 0.14f
) {
    HAMMERHEAD("hammerhead", "雙髻鯊", "hammerhead", 1008.0),
    WHALE_SHARK("whale_shark", "鯨鯊", "whale_shark"),
    ORCA("orca", "殺人鯨（虎鯨）", "orca", 1400.0, 0.16f),
    GREAT_WHITE("great_white", "大白鯊", "great_white", 1400.0, 0.13f),
    TIGER("tiger", "虎鯊", "tiger", 1500.0, 0.12f),
    SHORTFIN_MAKO("shortfin_mako", "尖吻鯖鯊", "shortfin_mako", 1100.0, 0.13f),
    THRESHER("thresher", "長尾鯊", "thresher", 1900.0, 0.20f),
    ZEBRA("zebra", "豹紋鯊", "zebra", 2100.0, 0.17f),
    OCEANIC_WHITETIP("oceanic_whitetip", "遠洋白鰭鯊", "oceanic_whitetip", 1800.0, 0.11f);

    companion object {
        fun fromId(id: String?): SharkSpecies = entries.firstOrNull { it.id == id } ?: HAMMERHEAD
    }
}

data class SharkConfig(
    var size: Int = 100,
    var speed: Int = 100,
    val species: SharkSpecies = SharkSpecies.HAMMERHEAD
) {
    companion object {
        const val MAX_PETS = 50
        fun savedCount(count: Int): Int = count.coerceIn(0, MAX_PETS)
        fun canAdd(count: Int): Boolean = count in 0 until MAX_PETS

        /** Legacy preferences without a species remain hammerheads. */
        fun fromSaved(size: Int = 100, speed: Int = 100, speciesId: String? = null) =
            SharkConfig(size.coerceIn(30, 200), speed.coerceIn(20, 200), SharkSpecies.fromId(speciesId))
    }
}
