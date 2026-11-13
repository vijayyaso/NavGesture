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

/**
 * Sliding back down after the app list opened gives up. The list can open only a little
 * above the bottom edge, so the slide needed shrinks to the room left below the open point.
 * All values are display pixels, the same space as the touch samples and the screen height.
 */
object SwitcherCancel {
    /** Downward travel from the open point that gives up, when there is room for it. */
    const val CANCEL_DP = 32f

    // A finger's reported centre stops a little short of the glass edge, so 4 dp are left
    // unused; 12 dp is more than the 10 dp rise that opens the list, so wobble cannot cancel.
    const val EDGE_MARGIN_DP = 4f
    const val MIN_DP = 12f

    /** Downward travel from [openYPx] that cancels, on a screen [screenHeightPx] tall. */
    fun distancePx(openYPx: Float, screenHeightPx: Float, density: Float): Float {
        val roomDp = (screenHeightPx - openYPx) / density - EDGE_MARGIN_DP
        return roomDp.coerceIn(MIN_DP, CANCEL_DP) * density
    }

    fun isCancel(openYPx: Float, sampleYPx: Float, screenHeightPx: Float, density: Float): Boolean =
        sampleYPx - openYPx >= distancePx(openYPx, screenHeightPx, density)
}
