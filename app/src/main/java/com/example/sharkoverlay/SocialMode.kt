package com.example.sharkoverlay

enum class SocialMode(val id: String, val label: String, val description: String) {
    OFF("off", "關閉", "各自自由游動"),
    NATURAL("natural", "自然水族箱", "輕微避讓、短暫並游、大小魚禮讓"),
    PLAYFUL("playful", "活潑寵物", "輕微避讓、好奇跟隨、偶爾追逐"),
    OCEAN("ocean", "海洋魚群", "輕微避讓、尾流跟隨、鬆散結群");

    companion object {
        const val PREFERENCE = "social_mode"
        fun fromId(id: String?): SocialMode = entries.firstOrNull { it.id == id } ?: NATURAL
    }
}
