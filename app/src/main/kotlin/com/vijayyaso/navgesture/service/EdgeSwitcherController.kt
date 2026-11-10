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

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.view.HapticFeedbackConstants
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.AppListStyle
import com.vijayyaso.navgesture.gesture.FavouriteList
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SwitcherSelection
import com.vijayyaso.navgesture.gesture.SlideTravel
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.gesture.TrackerAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the app list: one held selector stream, or the touch-mode panel left open after it,
 * including the cancellable metadata lookup.
 *
 * [style] decides what a lift does: Drag opens the selected app, Tap leaves the list open
 * for touching, and Both opens the app only if the finger moved the selection at least one
 * step (step count not zero), else leaves the list open.
 */
internal class EdgeSwitcherController(
    private val panel: SwitcherPanelOverlay,
    private val switcher: AppSwitcher,
    private val favourites: () -> List<String>,
    private val density: Float,
    private val style: AppListStyle,
    private val stepDp: Float,
    private val adaptiveSpeed: Boolean,
    private val haptic: (Int) -> Unit,
) {
    private val context = switcher.context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val icons = LruCache<String, Drawable>(24)
    private var load: Job? = null
    private var generation = 0L
    /** True when this list shows recent apps; false when it shows favourites. */
    private var recents = true
    private var origin: TouchSample? = null
    private var latest: TouchSample? = null
    /** Sideways travel since the open point, with speed gain when that setting is on. */
    private var travel = SlideTravel(adaptiveSpeed)
    /** Both lists are resolved at prepare time; the drag direction picks one at open. */
    private var recentsApps: List<PanelApp>? = null
    /** The first recent app is the app in front now: shown dimmed, never selectable. */
    private var recentsHasCurrent = false
    private var favouriteApps: List<PanelApp>? = null
    private var apps: List<PanelApp>? = null
    private var opened = false
    /** The finger has lifted and the panel waits for a touch. */
    private var touchMode = false
    private var steps = 0
    private var selected: Int? = null
    /** The app the row last highlighted, so a tick follows each visible change. */
    private var lastShown: Int? = null

    private val idleTimeout = Runnable {
        Log.i(TAG, "SWITCHER_TIMEOUT")
        cancel()
    }

    init {
        panel.onPick = ::onPick
        panel.onDismiss = {
            Log.i(TAG, "SWITCHER_DISMISSED")
            cancel()
        }
        panel.onTouchStart = { if (waitingForTouch) armIdleTimeout() }
    }

    /** True while the touch-mode panel waits for a touch. */
    val waitingForTouch: Boolean get() = opened && touchMode

    /**
     * Called once a bottom swipe arms, before it can pause. The drag direction is not known
     * yet, so both lists load in the background and the open picks one.
     */
    fun prepare(edge: ScreenEdge) {
        cancel()
        if (edge != ScreenEdge.BOTTOM) return
        startLoad()
    }

    fun open(action: TrackerAction.OpenSwitcher) {
        if (action.edge != ScreenEdge.BOTTOM || opened) return
        origin = action.origin
        latest = action.origin
        travel = SlideTravel(adaptiveSpeed).apply {
            start(action.origin.xPx / density, action.origin.timestampMs)
        }
        recents = action.recents
        apps = if (recents) recentsApps else favouriteApps
        steps = 0
        opened = true
        touchMode = false
        render()
        // A distinct buzz at once: the row may still say "Loading apps", and this is the
        // only sign the short drag landed.
        haptic(HapticFeedbackConstants.CONFIRM)
        Log.i(TAG, "SWITCHER_OPEN bottom ${if (recents) "recents" else "favourites"} ${style.name}")
    }

    /**
     * Opens the list straight into touch mode, from a corner shortcut. The finger has
     * already lifted, and no arm loaded the lists, so this loads its own.
     */
    fun openForTouch(recents: Boolean) {
        cancel()
        this.recents = recents
        opened = true
        touchMode = true
        startLoad()
        render()
        armIdleTimeout()
        Log.i(TAG, "SWITCHER_OPEN corner ${if (recents) "recents" else "favourites"}")
    }

    fun move(sample: TouchSample) {
        if (!opened || touchMode) return
        latest = sample
        // Sliding back down to the edge gives up: close without opening anything.
        val openedAt = origin?.yPx ?: return
        if (sample.yPx - openedAt >= density * CANCEL_DP) {
            Log.i(TAG, "SWITCHER_CANCEL")
            cancel()
            return
        }
        travel.add(sample.xPx / density, sample.timestampMs)
        if (style != AppListStyle.TAP) updateSelection()
    }

    fun release(sample: TouchSample) {
        if (!opened || touchMode) {
            if (!touchMode) cancel()
            return
        }
        latest = sample
        travel.add(sample.xPx / density, sample.timestampMs)
        if (style != AppListStyle.TAP) updateSelection()
        if (!style.opensOnLift(steps)) {
            touchMode = true
            selected = null
            render()
            armIdleTimeout()
            Log.i(TAG, "SWITCHER_TOUCH_MODE")
            return
        }
        launchAndClose(selected)
    }

    /** A new edge touch closes a panel left open for touching. */
    fun onStreamStart() {
        if (waitingForTouch) {
            Log.i(TAG, "SWITCHER_CLOSED_BY_GESTURE")
            cancel()
        }
    }

    fun cancel() {
        generation++
        load?.cancel()
        load = null
        handler.removeCallbacks(idleTimeout)
        origin = null
        latest = null
        recentsApps = null
        favouriteApps = null
        recentsHasCurrent = false
        apps = null
        steps = 0
        selected = null
        lastShown = null
        opened = false
        touchMode = false
        panel.hide()
    }

    private fun onPick(index: Int) {
        if (!waitingForTouch) return
        launchAndClose(index)
    }

    private fun launchAndClose(index: Int?) {
        val packageName = index?.let { apps?.getOrNull(it)?.packageName }
        // Hide before starting another activity, even if metadata arrived only just now.
        cancel()
        if (packageName != null && switcher.launch(packageName)) {
            haptic(HapticFeedbackConstants.CONFIRM)
            Log.i(TAG, "SWITCHER_LAUNCHED $packageName")
        }
    }

    private fun armIdleTimeout() {
        handler.removeCallbacks(idleTimeout)
        handler.postDelayed(idleTimeout, IDLE_TIMEOUT_MS)
    }

    private fun startLoad() {
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
                    val recentList = switcher.recentApps()
                    val recentPanel = resolve(recentList.packages)
                    // A package that failed to resolve is dropped, so check the first survived.
                    val hasCurrent = recentList.currentFirst &&
                        recentPanel.firstOrNull()?.packageName == recentList.packages.firstOrNull()
                    Loaded(recentPanel, resolve(favouriteNow), hasCurrent)
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
                recentsApps = resolved.recents
                favouriteApps = resolved.favourites
                recentsHasCurrent = resolved.recentsHasCurrent
                if (opened) {
                    // The open may have run before this lookup finished, so take the list now.
                    apps = if (recents) recentsApps else favouriteApps
                    render()
                }
            }
        }
    }

    private fun render() {
        val list = apps
        Log.i(
            TAG,
            "SWITCHER_ROWS ${if (recents) "recents" else "favourites"} " +
                (list?.joinToString(",") { it.packageName } ?: "loading"),
        )
        // Touch mode and the Tap style show the grid; drag styles show the row while held.
        val grid = touchMode || style == AppListStyle.TAP
        when {
            list == null -> panel.showMessage(context.getString(R.string.switcher_loading), touchMode)
            list.isEmpty() -> panel.showMessage(context.getString(
                if (recents) R.string.switcher_no_recents else R.string.switcher_no_favourites,
            ), touchMode)
            grid -> panel.showGrid(list, dimmedIndex(), touchMode)
            else -> {
                panel.showRow(list, dimmedIndex(), startIndex(), steps)
                updateSelection()
            }
        }
    }

    // When the app in front is first in the recents, it is shown dimmed so selection starts at
    // 1 and never falls to it. Otherwise (favourites, or an app the list hides) there is no such
    // slot, and every app can be picked from 0.
    private fun dimmedIndex(): Int? = if (recents && recentsHasCurrent) 0 else null

    private fun startIndex(): Int = if (recents && recentsHasCurrent) 1 else 0

    private fun updateSelection() {
        if (origin == null) return
        steps = SwitcherSelection.steps(travel.dp, stepDp, steps)
        val effective = style.effectiveSteps(steps)
        selected = SwitcherSelection.indexFor(effective, apps?.size ?: 0, startIndex())
        // What the row highlights is what a lift opens: in Both, nothing until a step is slid.
        val shown = selected.takeIf { style.showsSelection(steps) }
        if (shown != lastShown) {
            lastShown = shown
            Log.i(TAG, "SWITCHER_SELECTED $shown")
            haptic(HapticFeedbackConstants.CLOCK_TICK)
        }
        panel.select(effective, shown)
    }

    private class Loaded(
        val recents: List<PanelApp>,
        val favourites: List<PanelApp>,
        val recentsHasCurrent: Boolean,
    )

    private companion object {
        const val TAG = "NavGesture"

        /** Downward travel from the open point that gives up and closes the row. */
        const val CANCEL_DP = 32f

        /** A touch-mode panel left alone this long closes itself; any touch on it restarts it. */
        const val IDLE_TIMEOUT_MS = 10_000L
    }
}
