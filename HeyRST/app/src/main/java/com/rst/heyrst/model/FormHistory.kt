package com.rst.heyrst.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data model for form filling history cards shown in the 2-column bento grid.
 */
data class FormHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dateTime: String,
    val timeBadge: String = "9:10 AM",
    val status: String = "Completed", // "Completed" or "In Progress"
    val progressPercent: Int = 100,
    val thumbnailPath: String? = null,
    val categoryIcon: String = "⚡",
    val isDemo: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("dateTime", dateTime)
            put("timeBadge", timeBadge)
            put("status", status)
            put("progressPercent", progressPercent)
            put("thumbnailPath", thumbnailPath ?: "")
            put("categoryIcon", categoryIcon)
            put("isDemo", isDemo)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FormHistoryItem {
            val thumb = json.optString("thumbnailPath", "")
            return FormHistoryItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Online Form"),
                dateTime = json.optString("dateTime", "Today"),
                timeBadge = json.optString("timeBadge", "9:10 AM"),
                status = json.optString("status", "Completed"),
                progressPercent = json.optInt("progressPercent", 100),
                thumbnailPath = if (thumb.isNotBlank()) thumb else null,
                categoryIcon = json.optString("categoryIcon", "⚡"),
                isDemo = json.optBoolean("isDemo", false)
            )
        }
    }
}

/**
 * Local on-device storage for Form Filling History.
 * Pre-seeds default cards matching the design specification:
 * 1. Indian Passport Application
 * 2. California Poppy Bloom
 * 3. Lake Tahoe Trip Planning
 * 4. Nighthawks by Edward Hopper
 * Bottom in-progress: Job Application Form - RST Tech
 */
object FormHistoryStorage {
    private const val PREFS_NAME = "heyrst_history_prefs"
    private const val KEY_HISTORY = "history_items_json"

    fun getHistory(context: Context): List<FormHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null)
        if (raw.isNullOrBlank()) {
            val defaults = getDefaultHistoryItems()
            saveHistoryList(context, defaults)
            return defaults
        }

        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<FormHistoryItem>()
            for (i in 0 until array.length()) {
                list.add(FormHistoryItem.fromJson(array.getJSONObject(i)))
            }
            if (list.isEmpty()) getDefaultHistoryItems() else list
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultHistoryItems()
        }
    }

    fun addHistoryItem(context: Context, item: FormHistoryItem) {
        val current = getHistory(context).toMutableList()
        // Prepend new item to appear at top of history
        current.add(0, item)
        saveHistoryList(context, current)
    }

    fun updateHistoryItem(context: Context, updated: FormHistoryItem) {
        val current = getHistory(context).map {
            if (it.id == updated.id) updated else it
        }
        saveHistoryList(context, current)
    }

    private fun saveHistoryList(context: Context, list: List<FormHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun getDefaultHistoryItems(): List<FormHistoryItem> {
        return listOf(
            FormHistoryItem(
                id = "hist-passport-1",
                title = "Indian Passport Application",
                dateTime = "Tue, 23 Apr",
                timeBadge = "📍 Tue, 23 Apr",
                status = "Completed",
                progressPercent = 100,
                categoryIcon = "📍"
            ),
            FormHistoryItem(
                id = "hist-poppy-2",
                title = "California Poppy Bloom",
                dateTime = "Today",
                timeBadge = "⚡ 9:10 AM",
                status = "Completed",
                progressPercent = 100,
                categoryIcon = "⚡"
            ),
            FormHistoryItem(
                id = "hist-tahoe-3",
                title = "Lake Tahoe Trip Planning",
                dateTime = "Today",
                timeBadge = "⚡ 8:45 AM",
                status = "Completed",
                progressPercent = 100,
                categoryIcon = "⚡"
            ),
            FormHistoryItem(
                id = "hist-nighthawks-4",
                title = "Nighthawks by Edward Hopper",
                dateTime = "Wed, 16 Apr",
                timeBadge = "⚡ Wed, 16 Apr",
                status = "Completed",
                progressPercent = 100,
                categoryIcon = "⚡"
            )
        )
    }
}
