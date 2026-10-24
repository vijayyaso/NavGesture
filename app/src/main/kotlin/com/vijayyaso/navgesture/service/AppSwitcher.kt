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

package com.vijayyaso.navgesture.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vijayyaso.navgesture.gesture.RecentApps

/**
 * Walks the apps the user came from, which Android exposes to nobody: there is no global
 * action for it, so the order is read from usage events and the app is launched outright.
 *
 * Starting an activity from here is a background start, allowed only because the service
 * holds a visible accessibility overlay. Measured on HyperOS as
 * `BAL_ALLOW_NON_APP_VISIBLE_WINDOW`, so the edge strips are what make this legal.
 *
 * Needs the usage access the user grants in Settings, and reports [lastFailure] when it is
 * missing so the diagnostics panel can say so rather than the gesture looking broken.
 */
class AppSwitcher(internal val context: Context) {

    /** Launches a selected app and reports failures to the diagnostics panel. */
    fun launch(packageName: String): Boolean {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                val message = "app switch: $packageName has no launcher entry"
                lastFailure = message
                Log.w(TAG, message)
                return false
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // HyperOS supplies the slide animation; an activity trampoline added 190ms.
            context.startActivity(intent)
        }.onFailure { throwable ->
            lastFailure = "app switch: starting $packageName threw ${throwable.javaClass.name}"
            Log.w(TAG, "startActivity($packageName) threw", throwable)
        }.onSuccess {
            lastFailure = null
        }.isSuccess
    }

    /**
     * Apps by when each was last in front, newest first, every package once. Our own package
     * is dropped, and so is anything with no launcher entry: the home screen resumes between
     * every pair of apps and advertises CATEGORY_HOME rather than CATEGORY_LAUNCHER, so
     * leaving it in makes a step land on a package that cannot be started.
     */
    fun recentApps(): List<String> {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val events = runCatching { usage.queryEvents(now - TRAIL_WINDOW_MS, now) }.getOrNull()
            ?: return emptyList()
        val resumed = mutableListOf<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            resumed.add(event.packageName ?: continue)
        }
        return RecentApps.order(resumed, context.packageName, homePackages()) { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }
    }

    internal fun homePackages(): Set<String> =
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        ).mapNotNull { it.activityInfo?.packageName }.toSet()

    companion object {
        private const val TAG = "NavGesture"
        private const val TRAIL_WINDOW_MS = 6L * 60L * 60L * 1000L

        @Volatile
        var lastFailure: String? = null
            private set
    }
}
