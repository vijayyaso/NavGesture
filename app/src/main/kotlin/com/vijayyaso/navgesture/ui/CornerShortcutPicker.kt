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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.AppListStyle
import com.vijayyaso.navgesture.gesture.CornerShortcut

/** One radio row per list style. */
@Composable
internal fun AppListStyleChoice(selected: AppListStyle, onChange: (AppListStyle) -> Unit) {
    AppListStyle.entries.forEach { style ->
        val (title, body) = when (style) {
            AppListStyle.DRAG -> R.string.list_style_drag to R.string.list_style_drag_body
            AppListStyle.TAP -> R.string.list_style_tap to R.string.list_style_tap_body
            AppListStyle.BOTH -> R.string.list_style_both to R.string.list_style_both_body
        }
        ListItem(
            leadingContent = { RadioButton(selected = style == selected, onClick = null) },
            headlineContent = { Text(stringResource(title)) },
            supportingContent = { SupportingText(stringResource(body)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .testTag(TestTags.listStyle(style))
                .selectable(selected = style == selected, role = Role.RadioButton) { onChange(style) },
        )
    }
}

/** A corner's row: shows its shortcut, and opens the picker on a tap. */
@Composable
internal fun CornerShortcutRow(
    title: String,
    shortcut: CornerShortcut,
    tag: String,
    onChange: (CornerShortcut) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    NavigationRow(
        title = title,
        supporting = shortcutLabel(shortcut),
        tag = tag,
        onClick = { picking = true },
    )
    if (picking) {
        CornerShortcutDialog(
            title = title,
            current = shortcut,
            onPick = { picked ->
                picking = false
                onChange(picked)
            },
            onDismiss = { picking = false },
        )
    }
}

/** The name a shortcut is shown by; an app shows its own name once the list has loaded. */
@Composable
internal fun shortcutLabel(shortcut: CornerShortcut): String = when (shortcut) {
    CornerShortcut.None -> stringResource(R.string.corner_none)
    CornerShortcut.RecentsList -> stringResource(R.string.corner_recents_list)
    CornerShortcut.FavouritesList -> stringResource(R.string.corner_favourites_list)
    CornerShortcut.Notifications -> stringResource(R.string.corner_notifications)
    CornerShortcut.QuickSettings -> stringResource(R.string.corner_quick_settings)
    CornerShortcut.Screenshot -> stringResource(R.string.corner_screenshot)
    CornerShortcut.LockScreen -> stringResource(R.string.corner_lock_screen)
    CornerShortcut.PowerMenu -> stringResource(R.string.corner_power_menu)
    CornerShortcut.SplitScreen -> stringResource(R.string.corner_split_screen)
    is CornerShortcut.OpenApp -> {
        val apps by rememberLaunchableApps()
        val label = apps?.firstOrNull { it.packageName == shortcut.packageName }?.label
            ?: shortcut.packageName
        stringResource(R.string.corner_open_named_app, label)
    }
}

@Composable
private fun CornerShortcutDialog(
    title: String,
    current: CornerShortcut,
    onPick: (CornerShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    var choosingApp by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (choosingApp) stringResource(R.string.corner_pick_app) else title) },
        text = {
            if (choosingApp) {
                AppChoiceList(current, onPick)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(CornerShortcut.fixed) { shortcut ->
                        ChoiceRow(shortcutLabel(shortcut), shortcut == current) { onPick(shortcut) }
                    }
                    item {
                        ChoiceRow(
                            text = stringResource(R.string.corner_open_app),
                            selected = current is CornerShortcut.OpenApp,
                        ) { choosingApp = true }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (choosingApp) choosingApp = false else onDismiss() }) {
                Text(stringResource(if (choosingApp) R.string.action_navigate_up else R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AppChoiceList(current: CornerShortcut, onPick: (CornerShortcut) -> Unit) {
    val apps by rememberLaunchableApps()
    val loaded = apps
    if (loaded == null) {
        Text(stringResource(R.string.switcher_loading))
        return
    }
    LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
        items(loaded, key = { it.packageName }) { app ->
            val selected = (current as? CornerShortcut.OpenApp)?.packageName == app.packageName
            ListItem(
                leadingContent = { AppIconImage(app.icon) },
                headlineContent = { Text(app.label) },
                trailingContent = { RadioButton(selected = selected, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(CornerShortcut.OpenApp(app.packageName)) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, modifier = Modifier.padding(start = 12.dp))
    }
}
