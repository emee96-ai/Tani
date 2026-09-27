package com.tani.app.data.cache

import android.content.Context

class SearchPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCity: String?
        get() = prefs.getString(KEY_CITY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_CITY, value?.trim()?.takeIf { it.isNotBlank() }).apply()
        }

    val lastQuery: String?
        get() = recentQueries.firstOrNull()

    val recentQueries: List<String>
        get() = prefs.getString(KEY_RECENT, null)
            ?.split(RECENT_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.length >= 2 }
            ?.distinct()
            ?.take(MAX_RECENT)
            .orEmpty()

    fun recordQuery(query: String) {
        val clean = query.trim().replace(Regex("\\s+"), " ").take(80)
        if (clean.length < 2) return
        val updated = buildList {
            add(clean)
            recentQueries.filterNot { it.equals(clean, ignoreCase = true) }.forEach(::add)
        }.take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, updated.joinToString(RECENT_SEPARATOR)).apply()
    }

    companion object {
        private const val PREFS_NAME = "tani_search_preferences"
        private const val KEY_CITY = "selected_city"
        private const val KEY_RECENT = "recent_queries"
        private const val RECENT_SEPARATOR = "\u001F"
        private const val MAX_RECENT = 6
    }
}
