package com.example.sharkoverlay

enum class EffectsStrength(val id: String, val label: String, val density: Float, val opacity: Float) {
    OFF("off", "關閉", 0f, 0f),
    GENTLE("gentle", "輕柔", 1f, 1f),
    PROMINENT("prominent", "明顯", 1.35f, 1.5f);

    companion object {
        const val PREFERENCE = "effects_strength"
        fun fromId(id: String?): EffectsStrength = entries.firstOrNull { it.id == id } ?: GENTLE
    }
}
