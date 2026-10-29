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
    OCEANIC_WHITETIP("oceanic_whitetip", "遠洋白鰭鯊", "oceanic_whitetip", 1800.0, 0.11f),
    BLUE_SPOTTED_RAY("blue_spotted_ray", "深海藍點魟魚", "blue_spotted_ray", 2000.0),
    GOLDEN_RAY("golden_ray", "暖金藍點魟", "golden_ray", 2000.0),
    EAGLE_RAY("eagle_ray", "黑金星斑鷹魟", "eagle_ray", 2000.0),
    SILVER_RAY("silver_ray", "銀灰鑽石魟", "silver_ray", 2000.0),
    MANTA_RAY("manta_ray", "深藍蝠鱝", "manta_ray", 4000.0),
    DUNKLEOSTEUS("dunkleosteus", "鄧氏魚", "dunkleosteus", 3600.0, .065f),
    GIANT_GROUPER("giant_grouper", "巨型龍膽石斑", "giant_grouper", 4200.0, .05f),
    GIANT_SQUID("giant_squid", "大王魷魚", "giant_squid", 4000.0, .16f);

    val isBoss: Boolean get() = this == DUNKLEOSTEUS || this == GIANT_GROUPER || this == GIANT_SQUID

    val isRay: Boolean get() = this == BLUE_SPOTTED_RAY || this == GOLDEN_RAY ||
        this == EAGLE_RAY || this == SILVER_RAY || this == MANTA_RAY

    companion object {
        val regularEntries get() = entries.filter { !it.isBoss }
        val bosses get() = entries.filter { it.isBoss }
        fun fromId(id: String?): SharkSpecies = entries.firstOrNull { it.id == id } ?: HAMMERHEAD
    }
}

data class SharkConfig(
    var size: Int = 100,
    var speed: Int = 100,
    val species: SharkSpecies = SharkSpecies.HAMMERHEAD,
    val id: String = java.util.UUID.randomUUID().toString()
) {
    val renderScale: Float get() = size/100f * if(species.isBoss) 2.8f else 1f
    val motionSpeed: Float get() = speed/100f * if(species.isBoss) .4f else 1f
    companion object {
        const val MAX_PETS = 50
        fun savedCount(count: Int): Int = count.coerceIn(0, MAX_PETS)
        fun canAdd(count: Int): Boolean = count in 0 until MAX_PETS

        /** Legacy preferences without a species remain hammerheads. */
        fun fromSaved(size: Int = 100, speed: Int = 100, speciesId: String? = null, id: String? = null) =
            SharkConfig(size.coerceIn(30, 200), speed.coerceIn(20, 200), SharkSpecies.fromId(speciesId),
                id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString())
    }
}

