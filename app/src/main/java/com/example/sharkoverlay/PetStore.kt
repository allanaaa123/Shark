package com.example.sharkoverlay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PetStore {
    fun load(context: Context): List<SharkConfig> {
        val prefs = context.getSharedPreferences("shark", Context.MODE_PRIVATE)
        if (!prefs.contains("sharks")) return listOf(SharkConfig())
        val result = mutableListOf<SharkConfig>()
        val ids = hashSetOf<String>()
        var migrated = false
        try {
            val array = JSONArray(prefs.getString("sharks", "[]") ?: "[]")
            for (i in 0 until SharkConfig.savedCount(array.length())) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("id", "").isBlank()) migrated = true
                var config = SharkConfig.fromSaved(obj.optInt("size", 100), obj.optInt("speed", 100),
                    obj.optString("species", ""), obj.optString("id", ""))
                if (!ids.add(config.id)) {
                    migrated = true
                    config = config.copy(id = java.util.UUID.randomUUID().toString())
                    ids.add(config.id)
                }
                result.add(config)
            }
        } catch (_: Exception) { }
        val normalized=BossRules.normalize(result)
        if (migrated || normalized != result) save(context, normalized)
        return normalized
    }

    fun save(context: Context, pets: List<SharkConfig>) {
        val array = JSONArray()
        BossRules.normalize(pets).forEach {
            array.put(JSONObject().put("id", it.id).put("size", it.size)
                .put("speed", it.speed).put("species", it.species.id))
        }
        context.getSharedPreferences("shark", Context.MODE_PRIVATE).edit()
            .putString("sharks", array.toString()).apply()
    }
}
