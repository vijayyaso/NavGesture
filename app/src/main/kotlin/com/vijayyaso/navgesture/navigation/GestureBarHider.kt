// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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

package com.vijayyaso.navgesture.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.diagnostics.ServiceLog

/**
 * Hides the small gesture bar HyperOS draws at the bottom under a third-party launcher.
 *
 * HyperOS has its own full-screen gesture mode (`force_fsg_nav_bar`), which takes over from
 * Android's gestural navigation bar and honours its own "hide the line" switch
 * (`hide_gesture_line`). Both are global settings, so writing them needs
 * `WRITE_SECURE_SETTINGS`, which only a one-time `adb shell pm grant` can give; HyperOS also
 * blocks that grant until "USB debugging (Security settings)" is on. Tried on a POCO F5,
 * HyperOS 3.0: the bar went away and every NavGesture swipe still landed.
 *
 * HyperOS resets the mode when the launcher changes, so while the user wants it hidden the
 * service watches both settings and writes them back.
 *
 * A bar this app hid is marked on disk before the write, so a kill that skips the service's
 * unbind - a force stop or a crash - still leaves a trace. [restoreIfStopped] then gives the
 * phone its bar back, so it is never left with neither the bar nor the gestures. Giving it back
 * needs the same permission; if that is gone, the app says so ([stuck]) and points to the phone's
 * own navigation settings, since it cannot write the bar back itself.
 */
class GestureBarHider(private val context: Context, private val handler: Handler) {

    private var wanted = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (wanted && !isHidden()) {
                Log.i(TAG, "GESTURE_BAR_RESET by the system, hiding it again")
                write(hidden = true)
            }
        }
    }

    private var observing = false

    fun canWrite(): Boolean = canWrite(context)

    fun isHidden(): Boolean = isHidden(context)

    /** Brings the bar in line with what the user chose, and keeps it there. */
    fun apply(hide: Boolean) {
        val changed = hide != wanted
        wanted = hide
        if (hide) startObserving() else stopObserving()
        if (!canWrite()) return
        // Only undo what this app did, so a bar the user never asked to hide is left alone.
        if (hide && !isHidden()) write(hidden = true)
        // A bar hidden before the mark existed is still this app's to give back.
        if (hide && isHidden()) markHidden(context, hidden = true)
        if (!hide && changed) show(context)
    }

    fun release() = stopObserving()

    /**
     * The service is going away, so its swipes go with it. A hidden bar would then leave no
     * navigation at all, so the phone's own bar comes back. The next connect hides it again.
     * A bar this app never hid is left alone.
     */
    fun restoreForShutdown() {
        val hiding = wanted
        stopObserving()
        wanted = false
        if (!hiding) return
        if (canWrite()) {
            show(context)
        } else if (stuck(canWrite = false, hiddenByUs(context), isHidden())) {
            // Nothing can write the bar back now, so the history says so instead of staying silent.
            Log.w(TAG, "GESTURE_BAR_STUCK service stopping, no permission to show the bar")
            ServiceLog.add(context, STUCK_LINE)
        }
    }

    private fun write(hidden: Boolean) = write(context, hidden)

    private fun startObserving() {
        if (observing) return
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(FORCE_FSG), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(HIDE_LINE), false, observer)
        observing = true
    }

    private fun stopObserving() {
        if (!observing) return
        context.contentResolver.unregisterContentObserver(observer)
        observing = false
    }

    /** What the Gesture bar section shows. */
    enum class BarState {
        /** No section: not HyperOS, or the Play build without the grant. */
        NONE,
        CAN_HIDE,
        NEEDS_GRANT,

        /** See [stuck]: the user must bring the bar back from the phone's own settings. */
        STUCK_HIDDEN,
    }

    /** What [restoreIfStopped] found and did. */
    enum class Restore { NOT_NEEDED, SHOWN, NOT_SHOWN, NO_PERMISSION }

    companion object {
        fun canWrite(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

        fun isHidden(context: Context): Boolean =
            global(context, FORCE_FSG) == 1 && global(context, HIDE_LINE) == 1

        /** Gives the phone its own gesture bar back. Needs [canWrite]. */
        fun show(context: Context) {
            if (global(context, FORCE_FSG) == 1) write(context, hidden = false)
            if (!isHidden(context)) markHidden(context, hidden = false)
        }

        /**
         * Only a bar this app hid, and only with the gestures really gone: not running, and not
         * switched on either, which is the system about to bind the service. A bank pause has
         * shown the bar already, so it leaves nothing marked.
         */
        fun shouldRestore(hidByUs: Boolean, serviceRunning: Boolean, switchedOn: Boolean): Boolean =
            hidByUs && !serviceRunning && !switchedOn

        /** A bar this app hid is still hidden, and without the permission nothing can show it. */
        fun stuck(canWrite: Boolean, hidByUs: Boolean, hiddenNow: Boolean): Boolean =
            !canWrite && hidByUs && hiddenNow

        fun barState(
            hyperOs: Boolean,
            play: Boolean,
            canWrite: Boolean,
            hidByUs: Boolean,
            hiddenNow: Boolean,
        ): BarState = when {
            stuck(canWrite, hidByUs, hiddenNow) -> BarState.STUCK_HIDDEN
            !hyperOs -> BarState.NONE
            canWrite -> BarState.CAN_HIDE
            // The Play build never shows an adb instruction.
            play -> BarState.NONE
            else -> BarState.NEEDS_GRANT
        }

        /** Reads a few settings and a small preferences file. */
        fun barState(context: Context): BarState = barState(
            hyperOs = NavigationModeReader.isHyperOsDevice(),
            play = BuildConfig.IS_PLAY,
            canWrite = canWrite(context),
            hidByUs = hiddenByUs(context),
            hiddenNow = isHidden(context),
        )

        /** Shows a bar this app left hidden when the service died without its unbind. */
        fun restoreIfStopped(
            context: Context,
            serviceRunning: Boolean,
            switchedOn: Boolean,
        ): Restore {
            val hidByUs = hiddenByUs(context)
            if (!shouldRestore(hidByUs, serviceRunning, switchedOn)) return Restore.NOT_NEEDED
            if (!canWrite(context)) {
                if (stuck(canWrite = false, hidByUs, isHidden(context))) {
                    Log.w(TAG, "GESTURE_BAR_STUCK gestures gone, no permission to show the bar")
                    return Restore.NO_PERMISSION
                }
                // Brought back by hand, so there is nothing left for this app to give back.
                markHidden(context, hidden = false)
                return Restore.NOT_NEEDED
            }
            show(context)
            return if (isHidden(context)) Restore.NOT_SHOWN else Restore.SHOWN
        }

        private fun hiddenByUs(context: Context): Boolean =
            prefs(context).getBoolean(KEY_HIDDEN_BY_US, false)

        // commit, not apply: the process can die right after the settings write.
        private fun markHidden(context: Context, hidden: Boolean) {
            if (hiddenByUs(context) == hidden) return
            prefs(context).edit(commit = true) { putBoolean(KEY_HIDDEN_BY_US, hidden) }
        }

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun write(context: Context, hidden: Boolean) {
            // Recorded before the write, so a kill straight after still leaves the trace.
            if (hidden) markHidden(context, hidden = true)
            runCatching {
                val resolver = context.contentResolver
                if (hidden) Settings.Global.putInt(resolver, HIDE_LINE, 1)
                Settings.Global.putInt(resolver, FORCE_FSG, if (hidden) 1 else 0)
            }.onFailure { Log.w(TAG, "Could not write the gesture-bar settings", it) }
        }

        private fun global(context: Context, key: String): Int =
            runCatching { Settings.Global.getInt(context.contentResolver, key, 0) }.getOrDefault(0)

        /** What the user types once, from a computer, to let this app hide the bar. */
        const val GRANT_COMMAND =
            "adb shell pm grant com.vijayyaso.navgesture android.permission.WRITE_SECURE_SETTINGS"

        /** The history line for a bar left hidden with the gestures gone. */
        const val STUCK_LINE = "the bar stays hidden: the adb permission to show it again is gone"

        private const val PREFS = "gesture_bar"
        private const val KEY_HIDDEN_BY_US = "hidden_by_us"
        private const val FORCE_FSG = "force_fsg_nav_bar"
        private const val HIDE_LINE = "hide_gesture_line"
        private const val TAG = "NavGesture"
    }
}
