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

import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone

/**
 * The gesture settings each demo step judges with. Steps 1 to 4 are the real defaults, so a
 * slow or long Back in step 1 is still Back, as it is for real. A later step adds only the
 * action it teaches.
 */
object DemoConfiguration {
    /** Share of the drawn width where the long line sits, so a long swipe fits a small drawing. */
    const val LONG_LINE_OF_WIDTH = 0.45f

    fun forStep(step: DemoStep, widthDp: Float): GestureConfiguration {
        val defaults = GestureConfiguration()
        return when (step) {
            DemoStep.BACK, DemoStep.HOME, DemoStep.RECENTS, DemoStep.APP_LIST -> defaults
            DemoStep.CORNER -> BottomCorner.entries.fold(defaults) { configuration, corner ->
                configuration.withAction(SlotId.Corner(corner), SwipeAction.Notifications)
            }
            DemoStep.WAIT -> sides(defaults, SwipeKind.WAIT, SwipeAction.Screenshot)
            DemoStep.LONG -> sides(defaults, SwipeKind.LONG, SwipeAction.Torch)
                .copy(longSwipeDp = longLineDp(widthDp))
        }
    }

    /** The real 120 dp, or less on a drawing narrower than 267 dp. */
    fun longLineDp(widthDp: Float): Float = minOf(GestureConfiguration().longSwipeDp, LONG_LINE_OF_WIDTH * widthDp)

    private fun sides(base: GestureConfiguration, kind: SwipeKind, action: SwipeAction): GestureConfiguration =
        listOf(ScreenEdge.LEFT, ScreenEdge.RIGHT).fold(base) { configuration, edge ->
            configuration.withAction(SlotId.Edge(edge, SwipeZone.WHOLE, kind), action)
        }
}
