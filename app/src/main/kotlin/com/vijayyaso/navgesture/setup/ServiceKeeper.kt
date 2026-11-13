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

package com.vijayyaso.navgesture.setup

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.bankpause.BankPause
import com.vijayyaso.navgesture.diagnostics.DiagnosticsCollector
import com.vijayyaso.navgesture.diagnostics.ServiceLog
import com.vijayyaso.navgesture.diagnostics.withServiceListed
import com.vijayyaso.navgesture.diagnostics.withServiceUnlisted
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.service.NavGestureAccessibilityService

/**
 * Brings the gestures back after the system switched the service off on its own.
 *
 * A force stop - from MIUI's cleaners, battery tools or App info - makes Android drop the
 * service from its Accessibility list without any callback to the app. Measured on a POCO
 * F5, HyperOS 3: `am force-stop` removed the entry, and the gestures stayed dead until the
 * user found the switch again.
 *
 * The service marks itself as wanted when it starts. Switching it off in Settings unbinds
 * it, and the unbind clears the mark, so the user's own choice stands. A force stop skips
 * the unbind and leaves the mark set, which is what this looks for. Every caller - boot,
 * the Control Center tile, the app opening - still runs after a force stop, because the
 * system starts them explicitly. Turning a service on needs the adb-granted
 * WRITE_SECURE_SETTINGS, the same grant "Hide the gesture bar" uses.
 */
object ServiceKeeper {

    private const val TAG = "NavGesture"
    private const val PREFS = "service_keeper"
    private const val KEY_WANTED = "wanted_on"

    /** Called by the service: true on start, false when the system unbinds it cleanly. */
    fun markWanted(context: Context, wanted: Boolean) {
        // commit, not apply: the process can die right after an unbind.
        prefs(context).edit(commit = true) { putBoolean(KEY_WANTED, wanted) }
    }

    /**
     * False in the Play build, which never switches the service on by itself: Google Play's review
     * expects the user to do that through Android's own Accessibility screen.
     */
    fun canTurnOn(context: Context): Boolean =
        !BuildConfig.IS_PLAY && GestureBarHider.canWrite(context)

    /**
     * Reads small files, so call it off the main thread. When the service stays off, a bar
     * [GestureBarHider] hid comes back, in the Play build too.
     */
    fun check(context: Context, from: String) {
        val healed = heal(context, from)
        val bar = when (
            GestureBarHider.restoreIfStopped(
                context,
                serviceRunning = NavGestureAccessibilityService.isRunning(),
                switchedOn = DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context),
            )
        ) {
            GestureBarHider.Restore.SHOWN -> "gestures gone, the phone's own bar shown again"
            GestureBarHider.Restore.NOT_SHOWN -> "gestures gone, the phone's own bar did not come back"
            GestureBarHider.Restore.NO_PERMISSION -> "gestures gone; ${GestureBarHider.STUCK_LINE}"
            GestureBarHider.Restore.NOT_NEEDED -> null
        }
        // One line per check, so the history's repeat filter keeps the tile's checks to one line.
        val line = listOfNotNull(healed, bar).joinToString("; ")
        if (line.isNotEmpty()) ServiceLog.add(context, "$from: $line")
    }

    /** What it found, for the history, or null when there is nothing to say. */
    private fun heal(context: Context, from: String): String? {
        if (BuildConfig.IS_PLAY) return null
        if (NavGestureAccessibilityService.isRunning()) return null
        val pause = BankPause.paused(context)
        if (pause != null) {
            // A live bank pause owns the switch: turning it on now would show the bank app
            // the service it was paused for.
            if (BankPause.watcherRunning) return null
            // The process died in the middle of a pause, and the bank app may still be in front.
            // A fresh watcher reads the same history and decides; only a refused start ends it.
            if (BankPause.startWatcher(context)) return "a bank pause lost its watcher, started it again"
            BankPause.clearPause(context)
            ServiceLog.add(context, "$from: a bank pause outlived its watcher, ending it")
        }
        // Listed but not yet running: the system binds it by itself.
        if (DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context)) return null
        if (!prefs(context).getBoolean(KEY_WANTED, false)) return null
        return when {
            !GestureBarHider.canWrite(context) -> "found switched off; the adb permission is gone"
            turnOn(context) -> "found switched off without a goodbye, turned back on"
            else -> "found switched off, could not turn it on"
        }
    }

    /** Adds the service to the Accessibility list, as the Settings switch does. */
    fun turnOn(context: Context): Boolean {
        if (!canTurnOn(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val entry = ComponentName(context, NavGestureAccessibilityService::class.java)
                .flattenToString()
            val current =
                Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                withServiceListed(current, entry),
            ) && Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.onFailure { Log.w(TAG, "Could not switch the service on", it) }.getOrDefault(false)
    }

    /**
     * The user's own switch-off, from the Home switch or the tile. It clears "wanted" first, so
     * neither boot, the tile nor the app opening turns the service back on. A running service
     * switches itself off, which needs no permission. One that is listed but not running can only
     * be taken off the list with the adb grant. False when the switch could not be changed.
     */
    fun turnOff(context: Context, from: String): Boolean {
        markWanted(context, wanted = false)
        // Cleared here, not only by the service: a pause that is starting can take the service
        // down before its own switch-off runs, and the watcher would then turn it back on.
        BankPause.consumeGoingAway(context)
        if (BankPause.paused(context) != null) {
            BankPause.clearPause(context)
            BankPause.stopWatcher(context)
        }
        if (NavGestureAccessibilityService.turnOff(from)) return true
        if (!DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context)) return true
        if (!GestureBarHider.canWrite(context)) return false
        val done = runCatching {
            val resolver = context.contentResolver
            val entry = ComponentName(context, NavGestureAccessibilityService::class.java)
                .flattenToString()
            val current =
                Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                withServiceUnlisted(current, entry),
            )
        }.onFailure { Log.w(TAG, "Could not switch the service off", it) }.getOrDefault(false)
        if (done) {
            ServiceLog.add(context, "$from: taken off the Accessibility list")
            // No service is left to show a bar it hid, so it is shown from here.
            GestureBarHider.restoreIfStopped(context, serviceRunning = false, switchedOn = false)
        }
        return done
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
