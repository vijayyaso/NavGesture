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

    fun isHidden(): Boolean = global(FORCE_FSG) == 1 && global(HIDE_LINE) == 1

    /** Brings the bar in line with what the user chose, and keeps it there. */
    fun apply(hide: Boolean) {
        val changed = hide != wanted
        wanted = hide
        if (hide) startObserving() else stopObserving()
        if (!canWrite()) return
        // Only undo what this app did, so a bar the user never asked to hide is left alone.
        if (hide && !isHidden()) write(hidden = true)
        if (!hide && changed && global(FORCE_FSG) == 1) write(hidden = false)
    }

    fun release() = stopObserving()

    private fun write(hidden: Boolean) {
        runCatching {
            val resolver = context.contentResolver
            if (hidden) Settings.Global.putInt(resolver, HIDE_LINE, 1)
            Settings.Global.putInt(resolver, FORCE_FSG, if (hidden) 1 else 0)
        }.onFailure { Log.w(TAG, "Could not write the gesture-bar settings", it) }
    }

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

    private fun global(key: String): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, key, 0) }.getOrDefault(0)

    companion object {
        fun canWrite(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

        /** What the user types once, from a computer, to let this app hide the bar. */
        const val GRANT_COMMAND =
            "adb shell pm grant com.vijayyaso.navgesture android.permission.WRITE_SECURE_SETTINGS"

        private const val FORCE_FSG = "force_fsg_nav_bar"
        private const val HIDE_LINE = "hide_gesture_line"
        private const val TAG = "NavGesture"
    }
}
