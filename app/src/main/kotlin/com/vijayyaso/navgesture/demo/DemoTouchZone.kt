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

/**
 * Where a touch around the drawn phone lands on the drawing. A real thumb that starts on a drawn
 * edge often lands a little past it, so a margin outside the left, right and bottom edges counts
 * as that edge. Points in the margin are moved onto the drawing's border, inside its start band.
 * The top is not an edge (the step text sits there), so it has no margin.
 */
class DemoTouchZone(private val widthPx: Float, private val heightPx: Float, private val marginPx: Float) {

    /** Whether a first finger at this point, in the drawing's own coordinates, starts a try. */
    fun accepts(xPx: Float, yPx: Float): Boolean =
        xPx >= -marginPx && xPx <= widthPx + marginPx && yPx >= 0f && yPx <= heightPx + marginPx

    /** The same point, moved onto the drawing if it is in the margin. */
    fun onDrawing(xPx: Float, yPx: Float): Pair<Float, Float> =
        xPx.coerceIn(0f, widthPx) to yPx.coerceIn(0f, heightPx)

    companion object {
        /** Wide enough for a thumb's centre, and still inside the 48 dp minimum gap to a real edge. */
        const val MARGIN_DP = 24f
    }
}
