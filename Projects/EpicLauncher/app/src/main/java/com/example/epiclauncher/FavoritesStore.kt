package com.example.epiclauncher

import android.content.Context

object FavoritesStore {

    private const val PREFS = "epic_launcher"

    private const val KEY_FAVORITES = "favorites"

    fun load(context: Context): List<String> {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getStringSet(
                KEY_FAVORITES,
                emptySet()
            )
            ?.toList()
            ?: emptyList()
    }

    fun save(
        context: Context,
        packages: List<String>
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putStringSet(
                KEY_FAVORITES,
                packages.toSet()
            )
            .apply()
    }
}