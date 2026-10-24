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

import android.util.Log
import android.util.LruCache
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.FavouriteList
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SwitcherSelection
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.gesture.TrackerAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns one held selector stream, including its cancellable metadata lookup. */
internal class EdgeSwitcherController(
    private val panel: SwitcherPanelOverlay,
    private val switcher: AppSwitcher,
    private val favourites: () -> List<String>,
    private val density: Float,
    /** Display height, which places the panel just above the bottom edge. */
    private val screenHeightPx: Float,
    private val haptic: (Int) -> Unit,
) {
    private val context = switcher.context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val icons = LruCache<String, Drawable>(24)
    private var load: Job? = null
    private var generation = 0L
    /** True when this stream shows recent apps; false when it shows favourites. */
    private var recents = true
    private var origin: TouchSample? = null
    private var latest: TouchSample? = null
    /** Both lists are resolved at prepare time; the drag direction picks one at open. */
    private var recentsApps: List<PanelApp>? = null
    private var favouriteApps: List<PanelApp>? = null
    private var apps: List<PanelApp>? = null
    private var opened = false
    private var selected: Int? = null

    /**
     * Called once a bottom swipe arms, before it can pause. The drag direction is not known
     * yet, so both lists load in the background and the open picks one.
     */
    fun prepare(edge: ScreenEdge) {
        cancel()
        if (edge != ScreenEdge.BOTTOM) return
        val token = generation
        val savedFavourites = favourites().toList()
        load = scope.launch {
            val resolved = try {
                withContext(Dispatchers.IO) {
                    val manager = context.packageManager
                    val homes = switcher.homePackages()
                    fun resolve(packages: List<String>) = packages.distinct().mapNotNull { packageName ->
                        runCatching {
                            val info = manager.getApplicationInfo(packageName, 0)
                            val icon = icons.get(packageName) ?: manager.getApplicationIcon(info).also {
                                icons.put(packageName, it)
                            }
                            PanelApp(packageName, manager.getApplicationLabel(info).toString(), icon)
                        }.getOrNull()
                    }
                    val favouriteNow = FavouriteList.usable(savedFavourites) {
                        it != context.packageName && it !in homes &&
                            manager.getLaunchIntentForPackage(it) != null
                    }
                    resolve(switcher.recentApps()) to resolve(favouriteNow)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Could not load app switcher", failure)
                null
            }
            // A cancelled lookup may finish its blocking package query after a new DOWN.
            if (token != generation) return@launch
            if (resolved != null) {
                recentsApps = resolved.first
                favouriteApps = resolved.second
                if (opened) {
                    // The open may have run before this lookup finished, so take the list now.
                    apps = if (recents) recentsApps else favouriteApps
                    render()
                }
            }
        }
    }

    fun open(action: TrackerAction.OpenSwitcher) {
        if (action.edge != ScreenEdge.BOTTOM || opened) return
        origin = action.origin
        latest = action.origin
        recents = action.recents
        apps = if (recents) recentsApps else favouriteApps
        opened = true
        render()
        // A distinct buzz at once: the row may still say "Loading apps", and this is the
        // only sign the short drag landed.
        haptic(HapticFeedbackConstants.CONFIRM)
        Log.i(TAG, "SWITCHER_OPEN bottom ${if (recents) "recents" else "favourites"}")
    }

    fun move(sample: TouchSample) {
        if (!opened) return
        latest = sample
        // Sliding back down to the edge gives up: close without opening anything.
        val openedAt = origin?.yPx ?: return
        if (sample.yPx - openedAt >= density * CANCEL_DP) {
            Log.i(TAG, "SWITCHER_CANCEL")
            cancel()
            return
        }
        updateSelection()
    }

    fun release(sample: TouchSample) {
        if (!opened) {
            cancel()
            return
        }
        latest = sample
        updateSelection()
        val packageName = selected?.let { apps?.getOrNull(it)?.packageName }
        // Hide before starting another activity, even if metadata arrived only just now.
        cancel()
        if (packageName != null && switcher.launch(packageName)) {
            haptic(HapticFeedbackConstants.CONFIRM)
            Log.i(TAG, "SWITCHER_LAUNCHED $packageName")
        }
    }

    fun cancel() {
        generation++
        load?.cancel()
        load = null
        origin = null
        latest = null
        recentsApps = null
        favouriteApps = null
        apps = null
        selected = null
        opened = false
        panel.hide()
    }

    private fun render() {
        val y = panelCentreY()
        val list = apps
        Log.i(
            TAG,
            "SWITCHER_ROWS ${if (recents) "recents" else "favourites"} " +
                (list?.joinToString(",") { it.packageName } ?: "loading"),
        )
        when {
            list == null -> panel.showMessage(context.getString(R.string.switcher_loading), y)
            list.isEmpty() -> panel.showMessage(context.getString(
                if (recents) R.string.switcher_no_recents else R.string.switcher_no_favourites,
            ), y)
            else -> panel.show(list, if (recents) 0 else null, y)
        }
        updateSelection()
    }

    /** The panel floats just above the bottom edge, where the gesture started. */
    private fun panelCentreY(): Float = screenHeightPx - density * BOTTOM_PANEL_CENTRE_DP

    private fun updateSelection() {
        val start = origin ?: return
        val sample = latest ?: return
        // Recents show the current app first, dimmed, so selection starts at 1 and never
        // falls to it. Favourites have no such slot and start at 0.
        val next = SwitcherSelection.looped(
            offsetDp = (sample.xPx - start.xPx) / density,
            count = apps?.size ?: 0,
            startIndex = if (recents) 1 else 0,
        )
        if (next != selected) {
            selected = next
            Log.i(TAG, "SWITCHER_SELECTED $next")
            haptic(HapticFeedbackConstants.CLOCK_TICK)
        }
        panel.select(selected)
    }

    private companion object {
        const val TAG = "NavGesture"

        /** Panel centre above the bottom edge; its lower edge then clears the gesture bar. */
        const val BOTTOM_PANEL_CENTRE_DP = 96f

        /** Downward travel from the open point that gives up and closes the row. */
        const val CANCEL_DP = 32f
    }
}
