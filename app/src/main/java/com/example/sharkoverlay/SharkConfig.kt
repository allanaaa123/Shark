package com.example.sharkoverlay

enum class SharkSpecies(val id: String, val displayName: String, val assetFolder: String) {
    HAMMERHEAD("hammerhead", "雙髻鯊", "hammerhead"),
    WHALE_SHARK("whale_shark", "鯨鯊", "whale_shark"),
    ORCA("orca", "殺人鯨（虎鯨）", "orca");

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
        /** Legacy preferences without a species remain hammerheads. */
        fun fromSaved(size: Int = 100, speed: Int = 100, speciesId: String? = null) =
            SharkConfig(size.coerceIn(30, 200), speed.coerceIn(20, 200), SharkSpecies.fromId(speciesId))
    }
}
