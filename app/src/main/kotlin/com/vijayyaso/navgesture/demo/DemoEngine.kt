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

package com.vijayyaso.navgesture.demo

import com.vijayyaso.navgesture.gesture.GestureAction
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.GestureTracker
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlideTravel
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwitcherCancel
import com.vijayyaso.navgesture.gesture.SwitcherSelection
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.gesture.TrackerAction

/** What the drawn phone shows while a finger moves, and how the touch ended. */
sealed interface DemoEvent {
    /** A release now would run [action]. */
    data class Ready(val action: SwipeAction) : DemoEvent

    /** Pulled back: a release now runs nothing. */
    data object Unready : DemoEvent

    /** The app list opened, at app 0. [recents] is false for the favourites drag to the left. */
    data class ListOpened(val recents: Boolean) : DemoEvent

    data class ListMoved(val index: Int) : DemoEvent

    data class Performed(val result: DemoResult) : DemoEvent

    data class Missed(val reason: DemoMiss) : DemoEvent
}

/**
 * Drives [GestureTracker] for the drawn phone, the way the service's touch strips drive it. It
 * copies three of their rules: the per-frame hold poll, the second finger that cancels, and the
 * hand-off to the app list. Each touch gives at most one [DemoEvent.Performed] or
 * [DemoEvent.Missed], however the tracker reports it.
 *
 * Samples are in the drawing's own pixels. The trackers get the drawn width, which places the
 * corners on the drawing, and the real display height, which keeps the Recents line, the pause
 * and the lift at their real sizes. Every other threshold is in dp.
 */
class DemoEngine(
    private val widthPx: Float,
    private val heightPx: Float,
    private val screenHeightPx: Float,
    private val density: Float,
    configuration: GestureConfiguration,
) {
    // The app list while it is open: where it opened and how far the finger slid since.
    private class AppList(val origin: TouchSample, val recents: Boolean, adaptive: Boolean) {
        val travel = SlideTravel(adaptive)
        var steps = 0
        var index = 0
    }

    private var configuration = configuration
    private var trackers = trackersFor(configuration)

    // A step change that arrived mid-touch, applied once the finger lifts.
    private var nextConfiguration: GestureConfiguration? = null

    // The edge this touch started on, null between touches and for a touch off every edge.
    private var edge: ScreenEdge? = null

    // A finger is down: from DOWN until the last UP or a cancel.
    private var touching = false

    // This touch already gave its result, so the rest of it is ignored.
    private var ended = false

    // When the next hold poll is due, on the touch clock; null when nothing polls.
    private var pollAtMs: Long? = null

    private var list: AppList? = null

    /** New trackers for [next]; while a finger is down they wait for it to lift. */
    fun configure(next: GestureConfiguration) {
        if (touching) {
            nextConfiguration = next
            return
        }
        nextConfiguration = null
        if (next == configuration) return
        configuration = next
        trackers = trackersFor(next)
    }

    /** The first finger went down. The bottom band wins over the side bands in a corner. */
    fun down(sample: TouchSample): List<DemoEvent> {
        endTouch()
        touching = true
        ended = false
        val band = BAND_DP * density
        val picked = when {
            sample.yPx >= heightPx - band -> ScreenEdge.BOTTOM
            sample.xPx <= band -> ScreenEdge.LEFT
            sample.xPx >= widthPx - band -> ScreenEdge.RIGHT
            else -> return end(DemoEvent.Missed(DemoMiss.NotOnEdge))
        }
        edge = picked
        return handle(tracker(picked).onDown(sample), sample.timestampMs)
    }

    fun move(sample: TouchSample): List<DemoEvent> {
        if (!touching || ended) return emptyList()
        list?.let { return slideList(it, sample, lifted = false) }
        val current = edge ?: return emptyList()
        return handle(tracker(current).onMove(sample), sample.timestampMs)
    }

    /** The last finger lifted. */
    fun up(sample: TouchSample): List<DemoEvent> {
        if (!touching) return emptyList()
        val open = list
        val events = when {
            ended -> emptyList()
            open != null -> slideList(open, sample, lifted = true)
            else -> edge?.let { handle(tracker(it).onUp(sample), sample.timestampMs) }.orEmpty()
        }
        endTouch()
        return events
    }

    /** A second finger: never a navigation gesture, as on the real strips. */
    fun extraFinger(): List<DemoEvent> {
        if (!touching || ended) return emptyList()
        return end(DemoEvent.Missed(DemoMiss.Cancelled))
    }

    /** The touch was taken away without an UP. No try is counted. */
    fun cancel() {
        edge?.let { tracker(it).onCancel() }
        endTouch()
    }

    /** Called every frame while a finger is down, since a still finger sends no MOVE. */
    fun frame(nowMs: Long): List<DemoEvent> {
        if (!touching || ended || list != null) return emptyList()
        val due = pollAtMs ?: return emptyList()
        val current = edge ?: return emptyList()
        if (nowMs < due) return emptyList()
        pollAtMs = null
        return handle(tracker(current).onHoldElapsed(nowMs), nowMs)
    }

    // The poll schedule follows the overlay: armed, re-armed and pending set it, and every
    // result that ends the rise clears it.
    private fun handle(action: TrackerAction, nowMs: Long): List<DemoEvent> = when (action) {
        // Circle to Search is off in every demo configuration, so no long press is ever scheduled.
        TrackerAction.None, is TrackerAction.ScheduleLongPress -> emptyList()
        is TrackerAction.Armed -> {
            action.scheduleHoldMs?.let { pollAtMs = nowMs + it }
            readyFor(action.pending)
        }
        is TrackerAction.RearmHold -> {
            pollAtMs = nowMs + action.delayMs
            emptyList()
        }
        is TrackerAction.Pending -> {
            action.scheduleHoldMs?.let { pollAtMs = nowMs + it }
            readyFor(action.action)
        }
        is TrackerAction.OpenSwitcher -> {
            pollAtMs = null
            list = AppList(action.origin, action.recents, configuration.switcherAdaptiveSpeed).apply {
                travel.start(action.origin.xPx / density, action.origin.timestampMs)
            }
            listOf(DemoEvent.ListOpened(action.recents))
        }
        is TrackerAction.CornerReady -> {
            pollAtMs = null
            readyFor(configuration.shortcutFor(action.corner))
        }
        is TrackerAction.Fire -> when (val gesture = action.action) {
            is GestureAction.Run -> end(DemoEvent.Performed(DemoResult.Ran(gesture)))
            GestureAction.CircleToSearch -> end(null)
        }
        TrackerAction.Disarmed -> {
            pollAtMs = null
            listOf(DemoEvent.Unready)
        }
        TrackerAction.Reverted -> end(DemoEvent.Missed(DemoMiss.PulledBack))
        is TrackerAction.Unused -> end(DemoEvent.Missed(DemoMiss.Rejected(action.reason)))
    }

    private fun readyFor(action: SwipeAction): List<DemoEvent> =
        listOf(if (action == SwipeAction.None) DemoEvent.Unready else DemoEvent.Ready(action))

    // The real list's rules: sliding back down closes it, sideways travel picks with the same
    // gain and hysteresis, and the list loops so every app is reachable on a narrow drawing.
    private fun slideList(open: AppList, sample: TouchSample, lifted: Boolean): List<DemoEvent> {
        if (SwitcherCancel.isCancel(open.origin.yPx, sample.yPx, heightPx, density)) {
            return end(DemoEvent.Missed(DemoMiss.Cancelled))
        }
        open.travel.add(sample.xPx / density, sample.timestampMs)
        open.steps = SwitcherSelection.steps(open.travel.dp, configuration.switcherStepDp, open.steps)
        val index = SwitcherSelection.indexFor(open.steps, APP_COUNT) ?: 0
        val moved = if (index != open.index) DemoEvent.ListMoved(index) else null
        open.index = index
        if (!lifted) return listOfNotNull(moved)
        // The tracker returns nothing for a release in the list, so the engine names the result.
        val result = if (open.recents) DemoResult.AppPicked(index) else DemoResult.FavouritesOpened
        return listOfNotNull(moved) + end(DemoEvent.Performed(result))
    }

    // The touch's one result; everything after it is ignored until the next DOWN.
    private fun end(event: DemoEvent?): List<DemoEvent> {
        ended = true
        pollAtMs = null
        list = null
        edge?.let { tracker(it).onCancel() }
        return listOfNotNull(event)
    }

    private fun endTouch() {
        touching = false
        edge = null
        list = null
        pollAtMs = null
        nextConfiguration?.let(::configure)
    }

    private fun tracker(edge: ScreenEdge): GestureTracker = checkNotNull(trackers[edge])

    private fun trackersFor(configuration: GestureConfiguration): Map<ScreenEdge, GestureTracker> =
        ScreenEdge.entries.associateWith { edge ->
            GestureTracker(
                edge,
                configuration,
                density,
                handleCenterXPx = null,
                screenHeightPx = screenHeightPx,
                screenWidthPx = widthPx,
            )
        }

    companion object {
        /** How close to a drawn edge a touch must start; the real default strip is 20 dp. */
        const val BAND_DP = 28f

        /** The demo list's fixed apps. */
        const val APP_COUNT = 4
    }
}
