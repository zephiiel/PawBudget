package com.zephiiel.pawbudget.nativeapp

import android.content.Context
import org.json.JSONObject

class TrackerStore(context: Context) {
    private val preferences = context.getSharedPreferences("pawbudget.native.data", Context.MODE_PRIVATE)

    fun load(): TrackerData {
        val saved = preferences.getString("tracker", null) ?: return TrackerData.initial().also(::save)
        return try {
            TrackerData.fromBackup(JSONObject(saved))
        } catch (error: Exception) {
            throw IllegalStateException("Could not read saved PawBudget data: ${error.message}", error)
        }
    }

    fun save(data: TrackerData) {
        check(preferences.edit().putString("tracker", data.toJson().toString()).commit()) {
            "Could not save changes to this device."
        }
    }
}
