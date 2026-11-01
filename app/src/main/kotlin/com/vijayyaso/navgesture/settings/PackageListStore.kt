// Copyright (C) 2026  Vijayyaso
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.


package com.vijayyaso.navgesture.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vijayyaso.navgesture.gesture.FavouriteList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A list of package names the user picked, kept in the settings DataStore but outside the
 * gesture configuration, so "Reset to defaults" leaves it alone. Favourites and bank apps
 * are two of these.
 */
class PackageListStore private constructor(
    private val context: Context,
    private val key: String,
    private val debugOverride: String?,
) {
    private val dataStore = context.applicationContext.gestureDataStore
    private val preferenceKey = stringPreferencesKey(key)

    val packages: Flow<List<String>>
        get() = debugPackages()?.let { flowOf(it) }
            ?: dataStore.data.map { preferences -> FavouriteList.decode(preferences[preferenceKey]) }

    suspend fun set(packages: List<String>) {
        dataStore.edit { preferences -> preferences[preferenceKey] = FavouriteList.encode(packages) }
    }

    /** Test seam for the e2e suites: a comma-separated global setting wins. Debug only. */
    private fun debugPackages(): List<String>? {
        val name = debugOverride ?: return null
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return runCatching { Settings.Global.getString(context.contentResolver, name) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let(FavouriteList::decode)
    }

    companion object {
        /** The apps a leftward drag from the bottom shows, in order. */
        fun favourites(context: Context) =
            PackageListStore(context.applicationContext, "favourite_apps", debugOverride = null)

        /** The apps that pause NavGesture while they are open; see bankpause/BankPause.kt. */
        fun bankApps(context: Context) =
            PackageListStore(context.applicationContext, "bank_apps", BANK_APPS_OVERRIDE)

        const val BANK_APPS_OVERRIDE = "navgesture_bank_apps"
    }
}
