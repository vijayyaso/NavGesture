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

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * The run-time (dynamic) shortcuts other apps publish, such as GPay "Scan any QR code". Only the
 * home app may ask Android for them, so this reads the system's own debug listing instead:
 * `dumpsys shortcut`, run as this app. The shortcut service answers only a caller that holds
 * DUMP and PACKAGE_USAGE_STATS (both granted once from a computer with adb) and whose Usage
 * access is not switched off. Without them it prints a refusal, read by [ShortcutDump.denial].
 *
 * The listing is debug text whose layout can change in any Android release, so everything here
 * fails soft: an unreadable listing gives no run-time shortcuts, and the fixed ones stay.
 */
internal object RuntimeShortcuts {

    /** What the user runs once from a computer. Both lines are needed. */
    const val GRANT_COMMANDS =
        "adb shell pm grant com.vijayyaso.navgesture android.permission.DUMP\n" +
            "adb shell pm grant com.vijayyaso.navgesture android.permission.PACKAGE_USAGE_STATS"

    /** Every usable run-time shortcut of this user, for the picker. Off the main thread. */
    fun readAll(context: Context): RuntimeRead {
        if (!granted(context)) return RuntimeRead(RuntimeAccess.NEEDS_GRANT, emptyMap())
        val text = dump(listOf("--user", userId()), ALL_TIMEOUT_SECONDS)
            ?: return RuntimeRead(RuntimeAccess.UNREADABLE, emptyMap())
        when (ShortcutDump.denial(text)) {
            DumpDenial.NO_PERMISSION -> return RuntimeRead(RuntimeAccess.NEEDS_GRANT, emptyMap())
            DumpDenial.NO_USAGE_ACCESS -> return RuntimeRead(RuntimeAccess.NEEDS_USAGE_ACCESS, emptyMap())
            DumpDenial.NONE -> Unit
        }
        val entries = runCatching {
            ShortcutDump.parse(text).mapNotNull { toEntry(context, it) }.groupBy { it.packageName }
        }.onFailure { Log.w(TAG, "shortcut listing unreadable", it) }.getOrNull()
            ?: return RuntimeRead(RuntimeAccess.UNREADABLE, emptyMap())
        return RuntimeRead(RuntimeAccess.READY, entries)
    }

    /**
     * One app's run-time shortcuts, for a corner swipe. Runs on the service's main thread, so
     * it asks for that one package only and gives up after [ONE_TIMEOUT_SECONDS].
     */
    fun forPackage(context: Context, packageName: String): List<ShortcutEntry> {
        if (!granted(context)) return emptyList()
        val filter = "^" + Pattern.quote(packageName) + "$"
        val text = dump(listOf("--user", userId(), "-p", filter), ONE_TIMEOUT_SECONDS) ?: return emptyList()
        if (ShortcutDump.denial(text) != DumpDenial.NONE) return emptyList()
        return runCatching {
            ShortcutDump.parse(text).filter { it.packageName == packageName }.mapNotNull { toEntry(context, it) }
        }.onFailure { Log.w(TAG, "shortcuts of $packageName unreadable", it) }.getOrDefault(emptyList())
    }

    private fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED

    // UserHandle.getUserId is hidden; the uid range per user is fixed at 100000.
    private fun userId(): String = (Process.myUid() / PER_USER_RANGE).toString()

    /** The listing as text, or null if dumpsys could not run or did not finish in time. */
    private fun dump(args: List<String>, timeoutSeconds: Long): String? = runCatching {
        val process = ProcessBuilder(listOf(DUMPSYS, "shortcut") + args).redirectErrorStream(true).start()
        val output = arrayOfNulls<String>(1)
        // Read while it runs: a listing larger than the pipe buffer would block it otherwise.
        val reader = Thread({
            runCatching { output[0] = process.inputStream.bufferedReader().use { it.readText() } }
        }, "shortcut-dump")
        reader.start()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroy()
            Log.w(TAG, "dumpsys shortcut took over $timeoutSeconds s")
            return@runCatching null
        }
        reader.join(TimeUnit.SECONDS.toMillis(timeoutSeconds))
        output[0]
    }.onFailure { Log.w(TAG, "dumpsys shortcut failed", it) }.getOrNull()

    /** The intents rebuilt as the shortcut service would hand them to a launcher. */
    private fun toEntry(context: Context, dumped: DumpedShortcut): ShortcutEntry? {
        val intents = dumped.intents.map { toIntent(it, dumped.packageName) ?: return null }
        // LauncherAppsService.startShortcut adds this to the first intent of every shortcut.
        intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val entry = ShortcutEntry(dumped.packageName, dumped.id, dumped.label, dumped.iconRes, intents)
        return entry.takeIf { ShortcutCatalog.usable(context.packageManager, it) }
    }

    private fun toIntent(dumped: DumpedIntent, owner: String): Intent? {
        val intent = Intent(dumped.action)
        dumped.categories.forEach { intent.addCategory(it) }
        dumped.type?.let { intent.type = it }
        dumped.identifier?.let { intent.identifier = it }
        intent.flags = dumped.flags
        dumped.packageName?.let { intent.setPackage(it) }
        if (dumped.component != null) {
            intent.component = ComponentName.unflattenFromString(dumped.component) ?: return null
        } else if (dumped.packageName == null) {
            // A shortcut's intent with no target is aimed at the app that published it.
            intent.setPackage(owner)
        }
        dumped.extras.forEach { (key, value) ->
            when (value) {
                is String -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is Double -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
                else -> return null
            }
        }
        return intent
    }

    private const val DUMPSYS = "/system/bin/dumpsys"
    private const val PER_USER_RANGE = 100_000
    private const val ALL_TIMEOUT_SECONDS = 10L
    private const val ONE_TIMEOUT_SECONDS = 3L
    private const val TAG = "NavGesture"
}
