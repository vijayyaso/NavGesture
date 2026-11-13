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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.AppListStyle
import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.CornerShortcut
import com.vijayyaso.navgesture.service.RuntimeAccess
import com.vijayyaso.navgesture.service.ShortcutCatalog
import com.vijayyaso.navgesture.service.ShortcutEntry
import com.vijayyaso.navgesture.ui.theme.IconTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** A corner's row on App switching: shows its shortcut and opens the picker page. */
@Composable
internal fun CornerShortcutRow(
    title: String,
    shortcut: CornerShortcut,
    tag: String,
    onOpen: () -> Unit,
) {
    NavigationRow(
        title = title,
        supporting = shortcutLabel(shortcut),
        tag = tag,
        onClick = onOpen,
    )
}

/** The name a shortcut is shown by; an app or app shortcut shows its own name once loaded. */
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
    is CornerShortcut.AppShortcut -> appShortcutLabel(shortcut)
}

/** "App: Shortcut", read off the main thread; says so when the app no longer offers it. */
@Composable
private fun appShortcutLabel(shortcut: CornerShortcut.AppShortcut): String {
    val context = LocalContext.current
    // Null while loading; an empty list when the app no longer offers the shortcut.
    val names by produceState<List<String>?>(initialValue = null, shortcut.id) {
        value = withContext(Dispatchers.IO) {
            val found = ShortcutCatalog.find(context, shortcut.packageName, shortcut.shortcutId)
            if (found == null) emptyList() else listOf(appLabel(context, shortcut.packageName), found.label)
        }
    }
    val loaded = names ?: return stringResource(R.string.switcher_loading)
    if (loaded.isEmpty()) return stringResource(R.string.corner_app_shortcut_missing)
    return stringResource(R.string.corner_app_shortcut_named, loaded[0], loaded[1])
}

private fun appLabel(context: Context, packageName: String): String = runCatching {
    val manager = context.packageManager
    manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

/**
 * The picker page for one corner: the fixed choices, then a search box over the apps' own
 * shortcuts and the apps themselves. Picking saves at once and goes back.
 */
@Composable
internal fun CornerPickerScreen(corner: BottomCorner, model: AppModel, onDone: () -> Unit) {
    val current = model.configuration.shortcutFor(corner)
    val pick: (CornerShortcut) -> Unit = { picked ->
        model.update {
            when (corner) {
                BottomCorner.LEFT -> it.copy(leftCornerShortcut = picked)
                BottomCorner.RIGHT -> it.copy(rightCornerShortcut = picked)
            }
        }
        onDone()
    }

    // The fixed choices keep the order of CornerShortcut.fixed, split into three cards.
    val fixed = CornerShortcut.fixed.groupBy(::fixedSection)
    FixedGroup(null, fixed[FixedSection.NOTHING].orEmpty(), current, pick)
    FixedGroup(stringResource(R.string.corner_section_app_lists), fixed[FixedSection.LISTS].orEmpty(), current, pick)
    FixedGroup(stringResource(R.string.corner_section_phone_actions), fixed[FixedSection.ACTIONS].orEmpty(), current, pick)

    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.corner_search_apps)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp),
    )

    val apps by rememberLaunchableApps()
    AppShortcutsGroup(apps, query.trim(), current, pick)
    OpenAppGroup(apps, query.trim(), current, pick)
}

private enum class FixedSection { NOTHING, LISTS, ACTIONS }

private fun fixedSection(shortcut: CornerShortcut): FixedSection = when (shortcut) {
    CornerShortcut.None -> FixedSection.NOTHING
    CornerShortcut.RecentsList, CornerShortcut.FavouritesList -> FixedSection.LISTS
    else -> FixedSection.ACTIONS
}

@Composable
private fun FixedGroup(
    title: String?,
    choices: List<CornerShortcut>,
    current: CornerShortcut,
    pick: (CornerShortcut) -> Unit,
) {
    if (choices.isEmpty()) return
    SettingsGroup(title = title) {
        choices.forEach { choice ->
            PickRow(
                title = shortcutLabel(choice),
                selected = choice == current,
                leading = { fixedIcon(choice)?.let { IconBadge(it) } },
            ) { pick(choice) }
        }
    }
}

private fun fixedIcon(shortcut: CornerShortcut): GroupIcon? = when (shortcut) {
    CornerShortcut.None -> GroupIcon(Icons.Filled.Clear, IconTint.GREY)
    CornerShortcut.RecentsList -> GroupIcon(Icons.AutoMirrored.Filled.List, IconTint.BLUE)
    CornerShortcut.FavouritesList -> GroupIcon(Icons.Filled.Star, IconTint.ORANGE)
    CornerShortcut.Notifications -> GroupIcon(Icons.Filled.Notifications, IconTint.RED)
    CornerShortcut.QuickSettings -> GroupIcon(Icons.Filled.Settings, IconTint.GREY)
    CornerShortcut.Screenshot -> GroupIcon(ScreenshotGlyph, IconTint.TEAL)
    CornerShortcut.LockScreen -> GroupIcon(Icons.Filled.Lock, IconTint.GREY)
    CornerShortcut.PowerMenu -> GroupIcon(PowerGlyph, IconTint.RED)
    CornerShortcut.SplitScreen -> GroupIcon(SplitScreenGlyph, IconTint.PURPLE)
    is CornerShortcut.OpenApp, is CornerShortcut.AppShortcut -> null
}

/** A row that picks: a check mark marks the current choice, and it reads as a radio button. */
@Composable
private fun PickRow(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = leading,
        headlineContent = { Text(title) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** An app with its usable shortcuts and their icons, loaded once for the page. */
private class AppShortcuts(val app: LaunchableApp, val shortcuts: List<Pair<ShortcutEntry, ImageBitmap?>>)

/** Every app's shortcuts, and whether the run-time ones could be read (github build). */
private class ShortcutPage(val access: RuntimeAccess, val apps: List<AppShortcuts>)

@Composable
private fun AppShortcutsGroup(
    apps: List<LaunchableApp>?,
    query: String,
    current: CornerShortcut,
    pick: (CornerShortcut) -> Unit,
) {
    val context = LocalContext.current
    val iconPx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    // Bumped on each return to the app, so access given in Settings or from a computer shows.
    // The first resume is the page opening, which loads anyway.
    var returns by remember { mutableIntStateOf(0) }
    val opening = remember { booleanArrayOf(true) }
    LifecycleResumeEffect(Unit) {
        if (opening[0]) opening[0] = false else returns++
        onPauseOrDispose { }
    }
    // A reload keeps the page it replaces on screen until the new one is ready.
    val page by produceState<ShortcutPage?>(initialValue = null, apps, returns) {
        val loaded = apps ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val (access, byPackage) = ShortcutCatalog.forPackages(context, loaded.map { it.packageName })
            val entries = loaded.mapNotNull { app ->
                val shortcuts = byPackage[app.packageName] ?: return@mapNotNull null
                AppShortcuts(
                    app,
                    shortcuts.map { shortcut ->
                        // A drawable that throws while drawing must not take the page down.
                        shortcut to runCatching { ShortcutCatalog.icon(context, shortcut)?.toIconBitmap(iconPx) }.getOrNull()
                    },
                )
            }
            ShortcutPage(access, entries)
        }
    }
    val chosenPackage = (current as? CornerShortcut.AppShortcut)?.packageName
    // The app holding the current choice starts open (keyed, as the settings load late); a
    // search opens every match, and those can still be closed one by one.
    var open by remember(chosenPackage) { mutableStateOf(setOfNotNull(chosenPackage)) }
    var closedInSearch by remember { mutableStateOf(emptySet<String>()) }
    SettingsGroup(
        title = stringResource(R.string.corner_section_app_shortcuts),
        footer = stringResource(R.string.corner_app_shortcuts_body),
    ) {
        val loaded = page
        if (loaded == null) {
            FootNote(stringResource(R.string.switcher_loading))
            return@SettingsGroup
        }
        // github build only: how to unlock the run-time shortcuts, when they are locked.
        RuntimeShortcutsHint(loaded.access)
        loaded.apps.forEach { entry ->
            val appMatches = entry.app.label.contains(query, ignoreCase = true)
            val shown = if (query.isEmpty() || appMatches) {
                entry.shortcuts
            } else {
                entry.shortcuts.filter { (shortcut, _) -> shortcut.label.contains(query, ignoreCase = true) }
            }
            if (shown.isEmpty()) return@forEach
            val name = entry.app.packageName
            val searching = query.isNotEmpty()
            val expanded = if (searching) name !in closedInSearch else name in open
            // One child per app, so the card draws its lines between apps, not inside one.
            Column {
                AppHeaderRow(entry.app, expanded, TestTags.cornerApp(name)) {
                    if (searching) {
                        closedInSearch = if (name in closedInSearch) closedInSearch - name else closedInSearch + name
                    } else {
                        open = if (name in open) open - name else open + name
                    }
                }
                if (expanded) {
                    shown.forEach { (shortcut, icon) ->
                        PickRow(
                            title = shortcut.label,
                            selected = current == CornerShortcut.AppShortcut(shortcut.packageName, shortcut.id),
                            modifier = Modifier
                                .padding(start = 40.dp)
                                .testTag(TestTags.cornerShortcut(shortcut.packageName, shortcut.id)),
                            leading = { AppIconImage(icon ?: entry.app.icon) },
                        ) { pick(CornerShortcut.AppShortcut(shortcut.packageName, shortcut.id)) }
                    }
                }
            }
        }
    }
}

/** Opens or closes an app's shortcut list; it picks nothing itself. */
@Composable
private fun AppHeaderRow(app: LaunchableApp, expanded: Boolean, tag: String, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.corner_expanded else R.string.corner_collapsed)
    ListItem(
        leadingContent = { AppIconImage(app.icon) },
        headlineContent = { Text(app.label) },
        trailingContent = {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state },
    )
}

@Composable
private fun OpenAppGroup(
    apps: List<LaunchableApp>?,
    query: String,
    current: CornerShortcut,
    pick: (CornerShortcut) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.corner_open_app)) {
        if (apps == null) {
            FootNote(stringResource(R.string.switcher_loading))
            return@SettingsGroup
        }
        apps.filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }.forEach { app ->
            PickRow(
                title = app.label,
                selected = current == CornerShortcut.OpenApp(app.packageName),
                leading = { AppIconImage(app.icon) },
            ) { pick(CornerShortcut.OpenApp(app.packageName)) }
        }
    }
}

// material-icons-core has no screenshot, power or split-screen glyph, so these three are drawn
// here on the same 24-unit grid. Icon() tints them like any other glyph.

private val ScreenshotGlyph: ImageVector = ImageVector.Builder(
    name = "Screenshot",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // Four corner brackets around the screen.
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 3f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(5f)
        verticalLineTo(10f); horizontalLineTo(3f); close()
        moveTo(21f, 3f); verticalLineTo(10f); horizontalLineTo(19f); verticalLineTo(5f)
        horizontalLineTo(14f); verticalLineTo(3f); close()
        moveTo(3f, 21f); verticalLineTo(14f); horizontalLineTo(5f); verticalLineTo(19f)
        horizontalLineTo(10f); verticalLineTo(21f); close()
        moveTo(21f, 21f); horizontalLineTo(14f); verticalLineTo(19f); horizontalLineTo(19f)
        verticalLineTo(14f); horizontalLineTo(21f); close()
    }
}.build()

private val PowerGlyph: ImageVector = ImageVector.Builder(
    name = "Power",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // The stem, then a ring open at the top around it.
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
        moveTo(12f, 3.5f)
        verticalLineTo(11.5f)
        moveTo(7.05f, 7.55f)
        arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 16.95f, y1 = 7.55f)
    }
}.build()

private val SplitScreenGlyph: ImageVector = ImageVector.Builder(
    name = "SplitScreen",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // Two windows, one above the other.
    path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 3f); horizontalLineTo(19f); verticalLineTo(11f); horizontalLineTo(5f); close()
        moveTo(5f, 13f); horizontalLineTo(19f); verticalLineTo(21f); horizontalLineTo(5f); close()
    }
}.build()
