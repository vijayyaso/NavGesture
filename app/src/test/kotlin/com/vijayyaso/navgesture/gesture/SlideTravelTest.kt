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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideTravelTest {
    /** Slides [distanceDp] to the right in even 8 ms events (a 120 Hz screen) over [durationMs]. */
    private fun slide(travel: SlideTravel, fromDp: Float, distanceDp: Float, durationMs: Long, startMs: Long = 0L) {
        val events = (durationMs / 8L).toInt().coerceAtLeast(1)
        for (i in 1..events) {
            travel.add(fromDp + distanceDp * i / events, startMs + durationMs * i / events)
        }
    }

    @Test fun `off means plain finger distance`() {
        val travel = SlideTravel(adaptive = false).apply { start(100f, 0L) }
        slide(travel, 100f, 200f, 80L)
        assertEquals(200f, travel.dp, 0.01f)
    }

    @Test fun `a slow slide moves less than the finger`() {
        // 144 dp in 1.2 s = 120 dp/s: two steps of finger travel give under two steps.
        val travel = SlideTravel(adaptive = true).apply { start(0f, 0L) }
        slide(travel, 0f, 144f, 1200L)
        assertEquals(144f * SlideTravel.SLOW_GAIN, travel.dp, 1f)
    }

    @Test fun `a fast slide covers many more apps than a slow one over the same distance`() {
        val slow = SlideTravel(adaptive = true).apply { start(0f, 0L) }
        slide(slow, 0f, 200f, 1600L)
        val fast = SlideTravel(adaptive = true).apply { start(0f, 0L) }
        slide(fast, 0f, 200f, 80L)
        assertTrue("fast ${fast.dp} vs slow ${slow.dp}", fast.dp > slow.dp * 3f)
        assertEquals(SwitcherSelection.steps(slow.dp, 72f, 0), 2)
        assertTrue(SwitcherSelection.steps(fast.dp, 72f, 0) >= 6)
    }

    @Test fun `sliding back moves the selection back`() {
        val travel = SlideTravel(adaptive = true).apply { start(0f, 0L) }
        slide(travel, 0f, 200f, 100L)
        val out = travel.dp
        slide(travel, 200f, -100f, 800L, startMs = 100L)
        assertTrue(travel.dp < out)
    }

    @Test fun `a pause resets to the slow gain`() {
        val travel = SlideTravel(adaptive = true).apply { start(0f, 0L) }
        slide(travel, 0f, 200f, 80L)
        val before = travel.dp
        // Finger held still for half a second, then one slow 8 dp move.
        travel.add(200f, 600L)
        travel.add(208f, 700L)
        assertTrue(travel.dp - before < 8f)
    }

    @Test fun `gain curve`() {
        assertEquals(SlideTravel.SLOW_GAIN, SlideTravel.gain(0f), 0.001f)
        assertEquals(SlideTravel.SLOW_GAIN, SlideTravel.gain(SlideTravel.SLOW_DP_S), 0.001f)
        assertEquals(SlideTravel.FAST_GAIN, SlideTravel.gain(5000f), 0.001f)
        assertTrue(SlideTravel.gain(600f) > SlideTravel.gain(400f))
    }
}
