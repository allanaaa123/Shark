package com.example.sharkoverlay

enum class BatchVariation(val id: String, val label: String) {
    FIXED("fixed", "固定：大小、速度皆為 100%"),
    NORMAL("normal", "常態分佈：大小、速度各自隨機");

    companion object {
        const val PREFERENCE = "batch_variation"
        fun fromId(id: String?): BatchVariation = entries.firstOrNull { it.id == id } ?: FIXED
    }
}
