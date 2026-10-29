package com.rst.heyrst.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data model for real form filling history cards:
 * Real webpage name, exact date and time, real screenshot thumbnail, and completion status.
 */
data class FormHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String = "",
    val dateTime: String,
    val timeBadge: String = "",
    val status: String = "Completed",
    val progressPercent: Int = 100,
    val thumbnailPath: String? = null,
    val filledFieldsCount: Int = 0,
    val totalFieldsCount: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("url", url)
            put("dateTime", dateTime)
            put("timeBadge", timeBadge)
            put("status", status)
            put("progressPercent", progressPercent)
            put("thumbnailPath", thumbnailPath ?: "")
            put("filledFieldsCount", filledFieldsCount)
            put("totalFieldsCount", totalFieldsCount)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FormHistoryItem {
            val thumb = json.optString("thumbnailPath", "")
            return FormHistoryItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Webpage Form"),
                url = json.optString("url", ""),
                dateTime = json.optString("dateTime", "Today"),
                timeBadge = json.optString("timeBadge", ""),
                status = json.optString("status", "Completed"),
                progressPercent = json.optInt("progressPercent", 100),
                thumbnailPath = if (thumb.isNotBlank()) thumb else null,
                filledFieldsCount = json.optInt("filledFieldsCount", 0),
                totalFieldsCount = json.optInt("totalFieldsCount", 0)
            )
        }
    }
}

/**
 * Local storage for Form Filling History.
 * Strictly stores real webpage visits and screenshots with zero fake/sample demo items.
 */
object FormHistoryStorage {
    private const val PREFS_NAME = "formate_history_prefs"
    private const val KEY_HISTORY = "real_history_items_json"

    fun getHistory(context: Context): List<FormHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<FormHistoryItem>()
            for (i in 0 until array.length()) {
                list.add(FormHistoryItem.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun addHistoryItem(context: Context, item: FormHistoryItem) {
        val current = getHistory(context).toMutableList()
        current.add(0, item)
        saveHistoryList(context, current)
    }

    fun updateHistoryItem(context: Context, updated: FormHistoryItem) {
        val current = getHistory(context).map {
            if (it.id == updated.id) updated else it
        }
        saveHistoryList(context, current)
    }

    fun deleteHistoryItem(context: Context, id: String) {
        val current = getHistory(context).filter { it.id != id }
        saveHistoryList(context, current)
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistoryList(context: Context, list: List<FormHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
