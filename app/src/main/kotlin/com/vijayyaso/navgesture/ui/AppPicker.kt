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


package com.vijayyaso.navgesture.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.service.AppSwitcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The installed apps for a picker, loaded off the main thread; null while loading. */
@Composable
internal fun rememberLaunchableApps(): State<List<LaunchableApp>?> {
    val context = LocalContext.current
    return produceState<List<LaunchableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
}

/** Every installed app with a tick box; [onToggle] gets the package that was tapped. */
@Composable
internal fun AppChecklist(apps: List<LaunchableApp>?, chosen: List<String>, onToggle: (String) -> Unit) {
    if (apps == null) {
        FootNote(stringResource(R.string.switcher_loading))
        return
    }
    apps.forEach { app ->
        val checked = app.packageName in chosen
        ListItem(
            leadingContent = { AppIconImage(app.icon) },
            headlineContent = { Text(app.label) },
            trailingContent = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onToggle(app.packageName) },
        )
    }
}

internal data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/** A 40 dp icon slot, blank when the package manager gave nothing usable. */
@Composable
internal fun AppIconImage(icon: ImageBitmap?) {
    if (icon == null) return
    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
}

/** Draws an app icon into a square bitmap; adaptive icons draw fine this way. */
private fun Drawable.toIconBitmap(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

/** Every app with a launcher entry, minus the home screens and NavGesture itself. */
private fun launchableApps(context: Context): List<LaunchableApp> {
    val manager = context.packageManager
    val home = AppSwitcher(context).homePackages()
    val iconSize = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    return manager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        0,
    ).asSequence()
        .mapNotNull { it.activityInfo }
        .filterNot { it.packageName == context.packageName || it.packageName in home }
        .map { info ->
            val label = manager.getApplicationLabel(info.applicationInfo).toString()
            val icon = runCatching {
                manager.getApplicationIcon(info.applicationInfo).toIconBitmap(iconSize)
            }.getOrNull()
            LaunchableApp(info.packageName, label, icon)
        }
        .sortedBy { it.label.lowercase() }
        // Some apps ship several launcher activities; show each package once.
        .distinctBy { it.packageName }
        .toList()
}
