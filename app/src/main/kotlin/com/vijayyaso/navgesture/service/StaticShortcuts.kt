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
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** One fixed shortcut of an app, ready to start. [iconRes] is 0 when it has none. */
data class StaticShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val iconRes: Int,
    val intents: List<Intent>,
)

/**
 * Reads the fixed ("static") shortcuts apps declare in their manifest, the ones a long press on
 * the app icon shows. Only the home app may read the run-time (dynamic) shortcuts, so these are
 * all another app can offer. The XML is read the way Android's own ShortcutParser does, so a
 * shortcut is listed here exactly when the home screen would list it.
 *
 * Starting one from the service is a background start, allowed for the same reason as
 * [AppSwitcher.launch]: the edge strips are visible at that moment.
 */
object StaticShortcuts {

    /** Why the last [launch] failed, for the diagnostics panel. */
    @Volatile
    var lastFailure: String? = null
        private set

    /** Every usable shortcut of [packageName]; empty if the app has none or cannot be read. */
    fun forPackage(context: Context, packageName: String): List<StaticShortcut> {
        val manager = context.packageManager
        val launcherActivities = runCatching {
            manager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
                PackageManager.GET_META_DATA,
            )
        }.getOrDefault(emptyList())
        val found = LinkedHashMap<String, StaticShortcut>()
        launcherActivities.mapNotNull { it.activityInfo }.forEach { activity ->
            val xmlRes = activity.metaData?.getInt(META_DATA, 0) ?: 0
            if (xmlRes == 0) return@forEach
            runCatching {
                val resources = manager.getResourcesForApplication(activity.applicationInfo)
                parse(resources, xmlRes, packageName)
                    .filter { usable(manager, it) }
                    .forEach { found.putIfAbsent(it.id, it) }
            }.onFailure { Log.w(TAG, "shortcuts of $packageName unreadable", it) }
        }
        return found.values.toList()
    }

    /** The shortcut a corner stored, read again so an app update is followed; null if gone. */
    fun find(context: Context, packageName: String, shortcutId: String): StaticShortcut? =
        forPackage(context, packageName).firstOrNull { it.id == shortcutId }

    fun launch(context: Context, shortcut: StaticShortcut): Boolean = runCatching {
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
    fun icon(context: Context, shortcut: StaticShortcut): Drawable? {
        if (shortcut.iconRes == 0) return null
        return runCatching {
            context.packageManager.getResourcesForApplication(shortcut.packageName)
                .getDrawable(shortcut.iconRes, null)
        }.getOrNull()
    }

    /** One shortcuts XML, with the rules of AOSP ShortcutParser.parseShortcutsOneFile. */
    private fun parse(resources: Resources, xmlRes: Int, packageName: String): List<StaticShortcut> {
        val result = ArrayList<StaticShortcut>()
        val parser = resources.getXml(xmlRes)
        try {
            val attrs = Xml.asAttributeSet(parser)
            var current: Pending? = null
            var type = parser.next()
            while (type != XmlPullParser.END_DOCUMENT) {
                val depth = parser.depth
                val tag = parser.name
                if (type == XmlPullParser.END_TAG && depth == 2 && tag == TAG_SHORTCUT) {
                    current?.finish(packageName)?.let { done ->
                        if (result.none { it.id == done.id }) result += done
                    }
                    current = null
                } else if (type == XmlPullParser.START_TAG && depth == 2 && tag == TAG_SHORTCUT) {
                    current = Pending.read(resources, parser)
                } else if (type == XmlPullParser.START_TAG && depth == 3 && tag == TAG_INTENT) {
                    val shortcut = current
                    if (shortcut != null && shortcut.enabled) {
                        // Reads action, data, the target and the intent's own category/extra tags.
                        val intent = Intent.parseIntent(resources, parser, attrs)
                        if (intent.action.isNullOrEmpty()) current = null else shortcut.intents += intent
                    }
                }
                type = parser.next()
            }
        } finally {
            parser.close()
        }
        return result
    }

    /** Listed only if every intent reaches an exported, enabled activity of the app itself. */
    private fun usable(manager: PackageManager, shortcut: StaticShortcut): Boolean =
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

    /** A <shortcut> whose intents are still being read. */
    private class Pending(
        val id: String,
        val label: String,
        val iconRes: Int,
        val enabled: Boolean,
    ) {
        val intents = ArrayList<Intent>()

        fun finish(packageName: String): StaticShortcut? {
            if (!enabled || intents.isEmpty()) return null
            intents.forEach { intent ->
                // A target class with no package names nothing; Android aims it at the app.
                if (intent.component == null) intent.setPackage(packageName)
            }
            // The same flags Android gives the first intent of every fixed shortcut.
            intents[0].addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_TASK_ON_HOME,
            )
            return StaticShortcut(packageName, id, label, iconRes, intents.toList())
        }

        companion object {
            /** Null when Android would reject it: a non-literal id or no label resource. */
            fun read(resources: Resources, parser: XmlPullParser): Pending? {
                val id = parser.getAttributeValue(ANDROID_NS, "shortcutId")
                if (id.isNullOrEmpty() || id.startsWith("@")) return null
                val attrs = Xml.asAttributeSet(parser)
                val labelRes = attrs.getAttributeResourceValue(ANDROID_NS, "shortcutShortLabel", 0)
                if (labelRes == 0) return null
                val label = runCatching { resources.getString(labelRes) }.getOrNull() ?: return null
                return Pending(
                    id = id,
                    label = label,
                    iconRes = attrs.getAttributeResourceValue(ANDROID_NS, "icon", 0),
                    enabled = attrs.getAttributeBooleanValue(ANDROID_NS, "enabled", true),
                )
            }
        }
    }

    private const val META_DATA = "android.app.shortcuts"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TAG_SHORTCUT = "shortcut"
    private const val TAG_INTENT = "intent"
    private const val TAG = "NavGesture"
}
