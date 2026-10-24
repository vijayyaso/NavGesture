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
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.FavouriteList
import com.vijayyaso.navgesture.service.AppSwitcher
import com.vijayyaso.navgesture.settings.FavouriteAppsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Picks the right-edge app list, in the order the panel will show it. */
@Composable
internal fun FavouriteAppsScreen() {
    val context = LocalContext.current
    val store = remember(context) { FavouriteAppsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var favourites by remember { mutableStateOf<List<String>?>(null) }
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }

    LaunchedEffect(store) {
        store.favourites.collect { favourites = it }
    }

    val chosen = favourites.orEmpty()
    val labels = remember(apps) {
        apps.orEmpty().associate { it.packageName to it.label }
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
    val all = apps
    if (all == null) {
        FootNote(stringResource(R.string.switcher_loading))
        return
    }
    all.forEach { app ->
        val checked = app.packageName in chosen
        ListItem(
            headlineContent = { Text(app.label) },
            trailingContent = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                save(FavouriteList.toggle(chosen, app.packageName))
            },
        )
    }
}

private data class LaunchableApp(val packageName: String, val label: String)

/** Every app with a launcher entry, minus the home screens and NavGesture itself. */
private fun launchableApps(context: Context): List<LaunchableApp> {
    val manager = context.packageManager
    val home = AppSwitcher(context).homePackages()
    return manager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        0,
    ).asSequence()
        .mapNotNull { it.activityInfo }
        .filterNot { it.packageName == context.packageName || it.packageName in home }
        .map { info ->
            val label = manager.getApplicationLabel(info.applicationInfo).toString()
            LaunchableApp(info.packageName, label)
        }
        .sortedBy { it.label.lowercase() }
        // Some apps ship several launcher activities; show each package once.
        .distinctBy { it.packageName }
        .toList()
}
