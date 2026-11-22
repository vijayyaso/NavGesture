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

import com.vijayyaso.navgesture.gesture.TouchSample
import kotlin.math.hypot

/**
 * Re-injects a touch that an edge strip consumed but did not turn into a gesture. Strips
 * must consume `ACTION_DOWN` to see whether a swipe develops, so without replay every
 * ordinary tap near an edge is silently eaten.
 */
internal fun interface TouchReplayer {

    /** [samples] are display coordinates, oldest first. */
    fun replay(samples: List<TouchSample>, onDone: () -> Unit): Boolean
}

/** How far a touch may move, first sample to last, and still be a tap. */
internal const val TAP_SLOP_PX = 12f

/**
 * Whether [samples] are a tap: the touch moved less than [TAP_SLOP_PX] from its first sample to
 * its last. A still hold is a tap too. The replay and the practice report layer both use this
 * one rule, so a touch is never a tap to one and a swipe to the other. No samples is no tap.
 */
internal fun isTap(samples: List<TouchSample>): Boolean {
    if (samples.isEmpty()) return false
    val first = samples.first()
    val last = samples.last()
    return hypot(last.xPx - first.xPx, last.yPx - first.yPx) < TAP_SLOP_PX
}
