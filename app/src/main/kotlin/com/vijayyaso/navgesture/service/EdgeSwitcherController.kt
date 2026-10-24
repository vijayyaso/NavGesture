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
    private val haptic: (Int) -> Unit,
) {
    private val context = switcher.context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val icons = LruCache<String, Drawable>(24)
    private var load: Job? = null
    private var generation = 0L
    private var edge: ScreenEdge? = null
    private var origin: TouchSample? = null
    private var latest: TouchSample? = null
    private var apps: List<PanelApp>? = null
    private var opened = false
    private var selected: Int? = null

    fun prepare(edge: ScreenEdge) {
        cancel()
        if (edge == ScreenEdge.BOTTOM) return
        this.edge = edge
        val token = generation
        val savedFavourites = favourites().toList()
        load = scope.launch {
            val resolved = try {
                withContext(Dispatchers.IO) {
                    val manager = context.packageManager
                    val homes = switcher.homePackages()
                    val packages = if (edge == ScreenEdge.LEFT) switcher.recentApps() else
                        FavouriteList.usable(savedFavourites) {
                            it != context.packageName && it !in homes &&
                                manager.getLaunchIntentForPackage(it) != null
                        }
                    packages.distinct().mapNotNull { packageName ->
                        runCatching {
                            val info = manager.getApplicationInfo(packageName, 0)
                            val icon = icons.get(packageName) ?: manager.getApplicationIcon(info).also {
                                icons.put(packageName, it)
                            }
                            PanelApp(packageName, manager.getApplicationLabel(info).toString(), icon)
                        }.getOrNull()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Could not load app switcher", failure)
                emptyList()
            }
            // A cancelled lookup may finish its blocking package query after a new DOWN.
            if (token != generation || this@EdgeSwitcherController.edge != edge) return@launch
            apps = resolved
            if (opened) render()
        }
    }

    fun open(action: TrackerAction.OpenSwitcher) {
        if (edge != action.edge || opened) return
        origin = action.origin
        latest = action.origin
        opened = true
        render()
        haptic(HapticFeedbackConstants.LONG_PRESS)
        Log.i(TAG, "SWITCHER_OPEN ${action.edge}")
    }

    fun move(sample: TouchSample) {
        if (!opened) return
        latest = sample
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
        edge = null
        origin = null
        latest = null
        apps = null
        selected = null
        opened = false
        panel.hide()
    }

    private fun render() {
        val y = origin?.yPx ?: return
        val list = apps
        when {
            list == null -> panel.showMessage(context.getString(R.string.switcher_loading), y)
            list.isEmpty() -> panel.showMessage(context.getString(
                if (edge == ScreenEdge.LEFT) R.string.switcher_no_recents else R.string.switcher_no_favourites,
            ), y)
            else -> panel.show(list, if (edge == ScreenEdge.LEFT) 0 else null, y)
        }
        updateSelection()
    }

    private fun updateSelection() {
        val start = origin ?: return
        val sample = latest ?: return
        val inward = if (edge == ScreenEdge.LEFT) sample.xPx - start.xPx else start.xPx - sample.xPx
        val next = SwitcherSelection.selected(inward / density, apps?.size ?: 0,
            if (edge == ScreenEdge.LEFT) 1 else 0)
        if (next != selected) {
            selected = next
            haptic(HapticFeedbackConstants.CLOCK_TICK)
        }
        panel.select(selected)
    }

    private companion object {
        const val TAG = "NavGesture"
    }
}
