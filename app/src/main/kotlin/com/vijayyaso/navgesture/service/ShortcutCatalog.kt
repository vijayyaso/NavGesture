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

package com.vijayyaso.navgesture.service

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

/** One shortcut of an app, ready to start. [iconRes] is 0 when it has none. */
data class ShortcutEntry(
    val packageName: String,
    val id: String,
    val label: String,
    val iconRes: Int,
    val intents: List<Intent>,
)

/** Whether this build can read the run-time shortcuts, and what is missing if not. */
enum class RuntimeAccess {
    /** The play build never reads them. */
    NOT_IN_BUILD,

    /** The one-time permission from a computer is missing. */
    NEEDS_GRANT,

    /** Granted, but Usage access is off, which the system also asks for. */
    NEEDS_USAGE_ACCESS,

    /** Granted, but the system's list could not be read; only fixed shortcuts are shown. */
    UNREADABLE,
    READY,
}

/** The run-time shortcuts by package, and whether they could be read at all. */
data class RuntimeRead(val access: RuntimeAccess, val byPackage: Map<String, List<ShortcutEntry>>)

/**
 * Every app shortcut NavGesture can offer: the fixed ones from [StaticShortcuts], then the
 * run-time ones from [RuntimeShortcuts] (github build, after a one-time grant) that the fixed
 * list does not already hold. A fixed shortcut wins a clash, since its data URI is exact.
 *
 * Starting one from the service is a background start, allowed for the same reason as
 * [AppSwitcher.launch]: the edge strips are visible at that moment.
 */
object ShortcutCatalog {

    /** Why the last [launch] failed, for the diagnostics panel. */
    @Volatile
    var lastFailure: String? = null
        private set

    /** The picker's list for [packages], with one read of the run-time shortcuts for them all. */
    fun forPackages(context: Context, packages: List<String>): Pair<RuntimeAccess, Map<String, List<ShortcutEntry>>> {
        val runtime = RuntimeShortcuts.readAll(context)
        val merged = packages.associateWith { name ->
            merge(StaticShortcuts.forPackage(context, name), runtime.byPackage[name].orEmpty())
        }.filterValues { it.isNotEmpty() }
        return runtime.access to merged
    }

    /** The shortcut a corner stored, read again so an app update is followed; null if gone. */
    fun find(context: Context, packageName: String, shortcutId: String): ShortcutEntry? =
        StaticShortcuts.forPackage(context, packageName).firstOrNull { it.id == shortcutId }
            ?: RuntimeShortcuts.forPackage(context, packageName).firstOrNull { it.id == shortcutId }

    fun launch(context: Context, shortcut: ShortcutEntry): Boolean = runCatching {
        // A fresh copy, so the stored intents keep their flags for the next launch.
        val intents = shortcut.intents.map { Intent(it) }.toTypedArray()
        context.startActivities(intents)
    }.onFailure { throwable ->
        lastFailure = "${shortcut.id}: ${throwable.javaClass.name}"
        Log.w(TAG, "starting shortcut ${shortcut.packageName}/${shortcut.id} threw", throwable)
    }.onSuccess {
        lastFailure = null
    }.isSuccess

    /** The shortcut's own icon, for the picker; null when it has none. */
    fun icon(context: Context, shortcut: ShortcutEntry): Drawable? {
        if (shortcut.iconRes == 0) return null
        return runCatching {
            context.packageManager.getResourcesForApplication(shortcut.packageName)
                .getDrawable(shortcut.iconRes, null)
        }.getOrNull()
    }

    /**
     * Listed only if every intent reaches an exported, enabled activity of the app itself that
     * needs no permission. A launcher starts shortcuts as the app; NavGesture starts them as
     * itself, so anything it lists is something any app could already start.
     */
    fun usable(manager: PackageManager, shortcut: ShortcutEntry): Boolean =
        shortcut.intents.all { intent ->
            val info = activityFor(manager, intent) ?: return@all false
            info.exported && info.enabled && info.permission == null &&
                info.packageName == shortcut.packageName
        }

    private fun activityFor(manager: PackageManager, intent: Intent): ActivityInfo? {
        val component = intent.component
        return runCatching {
            if (component != null) {
                manager.getActivityInfo(component, 0)
            } else {
                manager.resolveActivity(intent, 0)?.activityInfo
            }
        }.getOrNull()
    }

    private fun merge(fixed: List<ShortcutEntry>, runtime: List<ShortcutEntry>): List<ShortcutEntry> {
        val ids = fixed.mapTo(HashSet()) { it.id }
        return fixed + runtime.filter { it.id !in ids }
    }

    private const val TAG = "NavGesture"
}
