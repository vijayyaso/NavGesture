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

package com.vijayyaso.navgesture.ui

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.FavouriteList
import com.vijayyaso.navgesture.settings.PackageListStore
import kotlinx.coroutines.launch

/** Picks the list a leftward drag from the bottom shows, in the order the panel shows it. */
@Composable
internal fun FavouriteAppsScreen() {
    val context = LocalContext.current
    val store = remember(context) { PackageListStore.favourites(context) }
    val scope = rememberCoroutineScope()
    var favourites by remember { mutableStateOf<List<String>?>(null) }
    val apps by rememberLaunchableApps()

    LaunchedEffect(store) {
        store.packages.collect { favourites = it }
    }

    val chosen = favourites.orEmpty()
    val labels = remember(apps) {
        apps.orEmpty().associate { it.packageName to it.label }
    }
    val appIcons = remember(apps) {
        apps.orEmpty().associate { it.packageName to it.icon }
    }
    val save: (List<String>) -> Unit = { updated ->
        // Shown at once, then saved; waiting for DataStore would make every tick feel slow.
        favourites = updated
        scope.launch { store.set(updated) }
    }

    SectionHeader(stringResource(R.string.section_favourites))
    if (chosen.isEmpty()) {
        FootNote(stringResource(R.string.favourites_empty))
    }
    chosen.forEachIndexed { index, packageName ->
        ListItem(
            leadingContent = { AppIconImage(appIcons[packageName]) },
            headlineContent = { Text(labels[packageName] ?: packageName) },
            trailingContent = {
                Row {
                    IconButton(
                        onClick = { save(FavouriteList.moveUp(chosen, index)) },
                        enabled = index > 0,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.action_move_up),
                        )
                    }
                    IconButton(onClick = { save(FavouriteList.toggle(chosen, packageName)) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_remove_favourite),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }

    SectionHeader(stringResource(R.string.section_all_apps))
    AppChecklist(apps, chosen) { packageName -> save(FavouriteList.toggle(chosen, packageName)) }
}
