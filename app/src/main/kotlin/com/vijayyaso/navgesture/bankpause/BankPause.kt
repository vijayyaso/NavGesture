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


package com.vijayyaso.navgesture.bankpause

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.diagnostics.ServiceLog
import com.vijayyaso.navgesture.gesture.FavouriteList
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.ui.hasUsageAccess

/** A saved pause: which picked app started it, the picked set then, and when. */
internal data class SavedPause(val packageName: String, val picked: Set<String>, val startedAtMs: Long)

/**
 * Bank pause: Indian bank apps refuse to open while any third-party Accessibility service is
 * on, so while one the user picked is in front, NavGesture gives the phone its own bar back
 * and switches its service off, and a watcher switches it back on once the user has left.
 * The design is docs/designs/bank-pause.md.
 *
 * State lives in SharedPreferences written with commit, since the service's process can die
 * right after it switches itself off. The watcher is github-only, so it is started by class
 * name and reports through [watcherRunning] and [resumeHook].
 */
internal object BankPause {

    private const val TAG = "NavGesture"
    private const val PREFS = "bank_pause"
    private const val KEY_PACKAGE = "paused_package"
    private const val KEY_PICKED = "paused_picked"
    private const val KEY_STARTED = "paused_started_at"
    private const val KEY_GOING_AWAY = "going_away"
    private const val KEY_HOLD_SINCE = "hold_since"
    private const val KEY_HOLD_PICKED = "hold_picked"

    private const val WATCHER = "com.vijayyaso.navgesture.bankpause.BankPauseWatcher"

    /** Asked of the watcher by the notification action and the tile. */
    const val ACTION_RESUME = "com.vijayyaso.navgesture.RESUME_GESTURES"

    /**
     * Screens that come to the front over a bank app without the user leaving it, so they do
     * not count as away: permission prompts, system UI, installers, and MIUI's security
     * centre, which scans apps as they open. Also what a bank opens for its own steps: the file
     * and photo pickers, the camera, Google's sign-in and SMS sheets, and the call screen. The
     * last group was read from a POCO F5 on HyperOS 3, plus the AOSP file picker.
     */
    val TRANSIENT = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.securityadd",
        "com.miui.securitycore",
        "com.lbe.security.miui",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.google.android.photopicker",
        "com.google.android.providers.media.module",
        "com.android.camera",
        "com.google.android.gms",
        "com.android.incallui",
    )

    /** Set by the watcher while it runs, in this process. */
    @Volatile
    var watcherRunning: Boolean = false

    /** Set by the watcher while it runs: ends the pause now and starts a Resume-now hold. */
    @Volatile
    var resumeHook: (() -> Unit)? = null

    /**
     * Needs the github build, the adb grant (to switch the service back on) and usage access
     * (to see the user leave). Notifications are optional: without them the pause still runs.
     */
    fun canPause(context: Context): Boolean =
        !BuildConfig.IS_PLAY && GestureBarHider.canWrite(context) && hasUsageAccess(context)

    fun savePause(context: Context, packageName: String, picked: Set<String>, nowMs: Long) {
        prefs(context).edit(commit = true) {
            putString(KEY_PACKAGE, packageName)
            putString(KEY_PICKED, FavouriteList.encode(picked.toList()))
            putLong(KEY_STARTED, nowMs)
            putBoolean(KEY_GOING_AWAY, true)
        }
    }

    fun paused(context: Context): SavedPause? {
        val prefs = prefs(context)
        val packageName = prefs.getString(KEY_PACKAGE, null) ?: return null
        return SavedPause(
            packageName = packageName,
            picked = FavouriteList.decode(prefs.getString(KEY_PICKED, null)).toSet() + packageName,
            startedAtMs = prefs.getLong(KEY_STARTED, 0L),
        )
    }

    /** Ends a saved pause. A Resume-now hold, if any, stays. */
    fun clearPause(context: Context) {
        prefs(context).edit(commit = true) {
            remove(KEY_PACKAGE)
            remove(KEY_PICKED)
            remove(KEY_STARTED)
            remove(KEY_GOING_AWAY)
        }
    }

    /** True once, for the unbind that the pause itself caused. */
    fun consumeGoingAway(context: Context): Boolean {
        val prefs = prefs(context)
        val goingAway = prefs.getBoolean(KEY_GOING_AWAY, false)
        if (goingAway) prefs.edit(commit = true) { remove(KEY_GOING_AWAY) }
        return goingAway
    }

    fun setHold(context: Context, picked: Set<String>, nowMs: Long) {
        prefs(context).edit(commit = true) {
            putLong(KEY_HOLD_SINCE, nowMs)
            putString(KEY_HOLD_PICKED, FavouriteList.encode(picked.toList()))
        }
    }

    /**
     * True while a Resume-now hold stands: the user has not been in any app outside the picked
     * set since. Once they have, the hold is cleared and pausing works again.
     */
    fun holdBlocks(context: Context): Boolean {
        val prefs = prefs(context)
        val since = prefs.getLong(KEY_HOLD_SINCE, 0L)
        if (since == 0L) return false
        val picked = FavouriteList.decode(prefs.getString(KEY_HOLD_PICKED, null)).toSet()
        // An unreadable history ends the hold: a hold that never ends would never pause again.
        val fronts = frontApps(context, since)
        if (fronts != null && !PauseClock.leftPicked(picked, fronts)) return true
        prefs.edit(commit = true) {
            remove(KEY_HOLD_SINCE)
            remove(KEY_HOLD_PICKED)
        }
        return false
    }

    /**
     * The apps that came to the front since [sinceMs], minus [TRANSIENT]; null if the usage
     * history could not be read. Without usage access the history reads as empty, so callers
     * check [hasUsageAccess] themselves.
     */
    fun frontApps(context: Context, sinceMs: Long): List<FrontApp>? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching {
            usage.queryEvents(sinceMs, System.currentTimeMillis())
        }.getOrNull() ?: return null
        val fronts = mutableListOf<FrontApp>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val packageName = event.packageName ?: continue
            if (packageName in TRANSIENT) continue
            fronts.add(FrontApp(packageName, event.timeStamp))
        }
        return fronts
    }

    /** The notification's Resume now and the tile tap during a pause. */
    fun resumeNow(context: Context): Boolean {
        val hook = resumeHook ?: return false
        ServiceLog.add(context, "bank pause: resume now")
        hook()
        return true
    }

    /** False if Android refused the start; the caller then undoes the pause. */
    fun startWatcher(context: Context): Boolean = runCatching {
        // ContextCompat's version returns nothing, so call the platform one: null means the
        // watcher is not in this build's manifest.
        context.startForegroundService(Intent().setClassName(context, WATCHER)) != null
    }.onFailure { Log.w(TAG, "Could not start the bank-pause watcher", it) }.getOrDefault(false)

    fun stopWatcher(context: Context) {
        runCatching { context.stopService(Intent().setClassName(context, WATCHER)) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
