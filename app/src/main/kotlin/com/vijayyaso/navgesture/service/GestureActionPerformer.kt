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

package com.vijayyaso.navgesture.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.widget.Toast
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.CornerShortcut
import com.vijayyaso.navgesture.gesture.GestureAction
import com.vijayyaso.navgesture.navigation.NavigationIntegrationFactory

/**
 * MIUI/HyperOS occasionally returns `false` from
 * [AccessibilityService.performGlobalAction] for no clear reason, so failures are
 * recorded for the diagnostics panel instead of looking like a recognizer bug.
 */
class GestureActionPerformer(private val service: AccessibilityService) {

    val switcher = AppSwitcher(service)

    /** One at a time, so two quick corner swipes start their shortcuts in order. */
    private val shortcutThread = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "NavGesture-shortcut").apply { isDaemon = true }
    }

    // Set when the service goes away, so a shortcut still queued then starts nothing.
    @Volatile
    private var closed = false

    private val assistantTag: String? by lazy {
        NavigationIntegrationFactory.create(service).assistantAttributionTag
    }

    fun perform(action: GestureAction): Boolean {
        val globalAction = when (action) {
            GestureAction.Back -> AccessibilityService.GLOBAL_ACTION_BACK
            GestureAction.Home -> AccessibilityService.GLOBAL_ACTION_HOME
            GestureAction.Recents -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GestureAction.CircleToSearch -> return circleToSearch()
            // The overlay resolves a corner to its shortcut and calls [performShortcut].
            is GestureAction.Corner -> {
                lastFailure = "${action.id}: corner reached perform() without its shortcut"
                return false
            }
        }
        return global(action.id, globalAction)
    }

    /** Runs a corner shortcut. The two app-list shortcuts are the overlay's, not this. */
    fun performShortcut(shortcut: CornerShortcut): Boolean {
        if (shortcut is CornerShortcut.AppShortcut) return appShortcut(shortcut)
        val globalAction = when (shortcut) {
            CornerShortcut.Notifications -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            CornerShortcut.QuickSettings -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            CornerShortcut.Screenshot -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            CornerShortcut.LockScreen -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            CornerShortcut.PowerMenu -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            CornerShortcut.SplitScreen -> AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            is CornerShortcut.OpenApp -> {
                val launched = switcher.launch(shortcut.packageName)
                if (!launched) lastFailure = "corner ${shortcut.id}: ${AppSwitcher.lastFailure}"
                return launched
            }
            CornerShortcut.None, CornerShortcut.RecentsList, CornerShortcut.FavouritesList,
            is CornerShortcut.AppShortcut,
            -> return false
        }
        Log.i(TAG, "CORNER_SHORTCUT ${shortcut.id}")
        return global(shortcut.id, globalAction)
    }

    /**
     * Read again on every use, so an app update that moved the shortcut is followed. The read
     * can run `dumpsys` for up to a few seconds (run-time shortcuts, github build), so it runs
     * on [shortcutThread], never on the main thread that handles touches. The caller ignores
     * the result, so this returns true once the work is queued.
     */
    private fun appShortcut(shortcut: CornerShortcut.AppShortcut): Boolean {
        Log.i(TAG, "CORNER_SHORTCUT ${shortcut.id}")
        // A swipe racing the service's end finds the thread shut; that must not throw here.
        runCatching {
            shortcutThread.execute {
                if (closed) return@execute
                val found = ShortcutCatalog.find(service, shortcut.packageName, shortcut.shortcutId)
                if (found == null) {
                    lastFailure = "corner ${shortcut.id}: shortcut not found"
                    Log.w(TAG, lastFailure.orEmpty())
                    return@execute
                }
                if (closed) return@execute
                if (!ShortcutCatalog.launch(service, found)) {
                    lastFailure = "corner ${shortcut.id}: ${ShortcutCatalog.lastFailure}"
                }
            }
        }.onFailure { Log.w(TAG, "corner ${shortcut.id}: dropped, the service is stopping") }
        return true
    }

    /** The service is going away: the thread ends once the lookup in flight, if any, returns. */
    fun close() {
        closed = true
        shortcutThread.shutdown()
    }

    private fun global(name: String, globalAction: Int): Boolean {

        // performGlobalAction throws instead of returning false once the service has been
        // unbound; uncaught that kills the process and flags the service "malfunctioning".
        val performed = runCatching { service.performGlobalAction(globalAction) }
            .getOrElse { throwable ->
                lastFailure = "$name: performGlobalAction threw ${throwable.javaClass.name}"
                Log.w(TAG, "performGlobalAction($name) threw", throwable)
                return false
            }
        if (!performed) {
            // No retry: a repeated global action can double-navigate.
            lastFailure = "$name: performGlobalAction returned false"
            Log.w(TAG, "performGlobalAction($name) returned false")
        }
        return performed
    }

    // A missing default assistant is invisible from a gesture, so it is said on screen too.
    private fun circleToSearch(): Boolean {
        val problem = CircleToSearchLauncher.launch(service, assistantTag) ?: return true
        lastCircleToSearchFailure = problem
        Log.w(TAG, "CIRCLE_TO_SEARCH_FAILED $problem")
        runCatching {
            Toast.makeText(service, R.string.toast_circle_to_search_failed, Toast.LENGTH_LONG)
                .show()
        }
        return false
    }

    companion object {
        private const val TAG = "NavGesture"

        @Volatile
        var lastCircleToSearchFailure: String? = null
            private set

        @Volatile
        var lastFailure: String? = null
            private set
    }
}
