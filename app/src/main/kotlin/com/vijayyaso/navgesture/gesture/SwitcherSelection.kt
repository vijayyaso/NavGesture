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

package com.vijayyaso.navgesture.gesture

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure selection, scrolling and grid maths for the bottom app switcher. */
object SwitcherSelection {
    /** Default sideways travel per icon. The user can change it in settings. */
    const val DEFAULT_STEP_DP = 72f

    /**
     * How far, in steps, the finger must stray from the current step before the selection
     * moves. Past the half-way line by a quarter step, so a finger resting on a boundary
     * between two apps does not flip between them.
     */
    const val HYSTERESIS_STEPS = 0.75f

    /**
     * The finger's travel counted in whole steps, unbounded in both directions. [previous] is
     * the count this stream showed last; it is kept until the finger has clearly left it.
     */
    fun steps(offsetDp: Float, stepDp: Float, previous: Int): Int {
        if (stepDp <= 0f) return previous
        val continuous = offsetDp / stepDp
        if (abs(continuous - previous) <= HYSTERESIS_STEPS) return previous
        return continuous.roundToInt()
    }

    /**
     * Maps an unbounded step count to an app index. The list loops: past the last app comes
     * the first, and before the first comes the last. [startIndex] keeps the dimmed current
     * app out of reach.
     */
    fun indexFor(steps: Int, count: Int, startIndex: Int): Int? {
        if (count <= 0 || startIndex !in 0 until count) return null
        val span = count - startIndex
        return startIndex + Math.floorMod(steps, span)
    }

    /** Columns and rows of the touch grid; apps past [maxRows] rows are not shown. */
    fun tapGrid(count: Int, availableDp: Float, cellDp: Float, maxRows: Int = 3): Grid {
        if (count <= 0 || cellDp <= 0f) return Grid(0, 0)
        val columns = (availableDp / cellDp).toInt().coerceAtLeast(1).coerceAtMost(count)
        val rows = ceil(count / columns.toFloat()).toInt().coerceAtMost(maxRows)
        return Grid(columns, rows)
    }

    data class Grid(val columns: Int, val rows: Int) {
        val capacity: Int get() = columns * rows
    }
}

/**
 * The sideways travel that drives the app list, with speed-based gain like a laptop trackpad:
 * a slow slide moves through the apps slowly, a quick one covers many apps for the same
 * distance. With [adaptive] off, the travel is the plain finger distance.
 *
 * Gain only scales each movement, so a slow slide back is not undone by a fast one out: the
 * travel is summed piece by piece, not worked out from the start point.
 */
class SlideTravel(private val adaptive: Boolean) {
    private var lastXDp = 0f
    private var lastTimeMs = 0L
    private var started = false
    /** Smoothed finger speed, dp per second. */
    private var speedDpS = 0f

    /** Travel so far in dp, after gain. */
    var dp = 0f
        private set

    fun start(xDp: Float, timeMs: Long) {
        lastXDp = xDp
        lastTimeMs = timeMs
        speedDpS = 0f
        dp = 0f
        started = true
    }

    fun add(xDp: Float, timeMs: Long) {
        if (!started) {
            start(xDp, timeMs)
            return
        }
        val dx = xDp - lastXDp
        val dtMs = (timeMs - lastTimeMs).coerceIn(0L, MAX_GAP_MS)
        lastXDp = xDp
        lastTimeMs = timeMs
        if (!adaptive) {
            dp += dx
            return
        }
        if (dtMs > 0) {
            // A short moving average, so one jittery event does not spike the gain.
            val instant = abs(dx) * 1000f / dtMs
            val weight = dtMs / (dtMs + SMOOTHING_MS)
            speedDpS += (instant - speedDpS) * weight
        }
        dp += dx * gain(speedDpS)
    }

    companion object {
        /** At or below this speed the gain is [SLOW_GAIN], a little faster than the finger. */
        const val SLOW_DP_S = 200f
        /** At or above this speed the gain is [FAST_GAIN]. */
        const val FAST_DP_S = 1500f
        const val SLOW_GAIN = 1.2f
        const val FAST_GAIN = 3f
        private const val SMOOTHING_MS = 40f
        /** A pause longer than this counts as this long, so speed falls to the slow gain. */
        private const val MAX_GAP_MS = 100L

        /** A smooth S-curve from [SLOW_GAIN] to [FAST_GAIN] over the speed range. */
        fun gain(speedDpS: Float): Float {
            val t = ((speedDpS - SLOW_DP_S) / (FAST_DP_S - SLOW_DP_S)).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            return SLOW_GAIN + (FAST_GAIN - SLOW_GAIN) * eased
        }
    }
}
