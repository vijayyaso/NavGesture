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
