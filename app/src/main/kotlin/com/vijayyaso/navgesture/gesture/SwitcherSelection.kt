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

import kotlin.math.roundToInt

/** Pure selection and scrolling maths for the bottom app switcher. */
object SwitcherSelection {
    /** Sideways travel per icon. */
    const val STEP_DP = 56f

    /**
     * Maps sideways finger travel to an index, one icon per [STEP_DP]. The list loops: past
     * the last icon comes the first, and before the first comes the last, so a short drag
     * always finds every app. [startIndex] keeps the dimmed current app out of reach.
     */
    fun looped(offsetDp: Float, count: Int, startIndex: Int): Int? {
        if (count <= 0 || startIndex !in 0 until count) return null
        val span = count - startIndex
        if (span <= 0) return null
        val steps = (offsetDp / STEP_DP).roundToInt()
        return startIndex + Math.floorMod(steps, span)
    }

    /** Reveals one more icon as selection moves past the initial window's end. */
    fun visibleWindow(selected: Int, count: Int, maxVisible: Int = 6): IntRange {
        if (count <= 0 || maxVisible <= 0) return IntRange.EMPTY
        val size = minOf(count, maxVisible)
        val start = (selected.coerceIn(0, count - 1) - size + 1).coerceAtLeast(0)
        return start until start + size
    }
}
