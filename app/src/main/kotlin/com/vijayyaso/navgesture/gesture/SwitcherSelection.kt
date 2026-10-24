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

import kotlin.math.floor

/** Pure selection and scrolling maths shared by both switcher edges. */
object SwitcherSelection {
    const val STEP_DP = 56f
    const val CANCEL_DP = 24f

    /** Returns no selection in the cancel zone or when no selectable app remains. */
    fun selected(inwardDp: Float, count: Int, firstIndex: Int): Int? {
        if (inwardDp < CANCEL_DP || count <= 0 || firstIndex !in 0 until count) return null
        val steps = floor((inwardDp - CANCEL_DP) / STEP_DP)
        return (firstIndex.toDouble() + steps).coerceAtMost((count - 1).toDouble()).toInt()
    }

    /** Reveals one more icon as selection moves past the initial window's end. */
    fun visibleWindow(selected: Int, count: Int, maxVisible: Int = 6): IntRange {
        if (count <= 0 || maxVisible <= 0) return IntRange.EMPTY
        val size = minOf(count, maxVisible)
        val start = (selected.coerceIn(0, count - 1) - size + 1).coerceAtLeast(0)
        return start until start + size
    }
}
