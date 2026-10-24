// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vijayyaso.navgesture.gesture.FavouriteList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Shares settings storage while keeping favourites outside configuration resets. */
class FavouriteAppsStore(context: Context) {
    private val dataStore = context.applicationContext.gestureDataStore

    val favourites: Flow<List<String>> = dataStore.data.map { preferences ->
        FavouriteList.decode(preferences[FAVOURITE_APPS])
    }

    suspend fun set(packages: List<String>) {
        dataStore.edit { preferences ->
            preferences[FAVOURITE_APPS] = FavouriteList.encode(packages)
        }
    }

    private companion object {
        val FAVOURITE_APPS = stringPreferencesKey("favourite_apps")
    }
}
