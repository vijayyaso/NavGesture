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

/** Side holds share touch slop with bottom holds, but own their entire release. */
class EdgeSwitcherGestureTest {
    private fun sample(x: Float = 2f, t: Long = 0L) = TouchSample(x, 1200f, t)
    private fun tracker(
        edge: ScreenEdge = ScreenEdge.LEFT,
        configuration: GestureConfiguration = GestureConfiguration(),
    ) = GestureTracker(edge, configuration, 2.75f)

    @Test fun `a side touch schedules the switcher hold`() {
        assertEquals(TrackerAction.ScheduleLongPress(300L), tracker().onDown(sample()))
    }

    @Test fun `a held side touch opens the switcher and then never fires Back`() {
        val t = tracker()
        t.onDown(sample())
        assertEquals(TrackerAction.OpenSwitcher(ScreenEdge.LEFT, sample()), t.onLongPressElapsed())
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
        assertEquals(TrackerAction.None, t.onMove(sample(552f, 500L)))
        assertEquals(TrackerAction.None, t.onUp(sample(552f, 600L)))
    }

    @Test fun `moving before the hold is a normal Back`() {
        val t = tracker()
        t.onDown(sample())
        assertTrue(t.onMove(sample(102f, 100L)) is TrackerAction.Armed)
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
        assertEquals(TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT), t.onUp(sample(102f, 200L)))
    }

    @Test fun `a resting finger may drift before the hold opens`() {
        val t = tracker()
        t.onDown(sample())
        // 15 dp of drift: past the system's 8 dp touch slop, still a hold rather than a swipe.
        assertEquals(TrackerAction.None, t.onMove(sample(42f, 100L)))
        assertEquals(TrackerAction.OpenSwitcher(ScreenEdge.LEFT, sample()), t.onLongPressElapsed())
    }

    @Test fun `the switcher outlives maximumArmDurationMs`() {
        val t = tracker()
        t.onDown(sample())
        t.onLongPressElapsed()
        assertEquals(TrackerAction.None, t.onMove(sample(2f, 5000L)))
        assertEquals(TrackerAction.None, t.onUp(sample(552f, 6000L)))
    }

    @Test fun `cancel during the switcher leaves nothing to fire`() {
        val t = tracker()
        t.onDown(sample())
        t.onLongPressElapsed()
        t.onCancel()
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
        assertEquals(TrackerAction.None, t.onMove(sample(552f, 500L)))
    }

    @Test fun `the switcher can be switched off`() {
        val t = tracker(configuration = GestureConfiguration(edgeAppListEnabled = false))
        assertEquals(TrackerAction.None, t.onDown(sample()))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test fun `the right edge opens with its original sample`() {
        val t = tracker(ScreenEdge.RIGHT)
        val down = sample(1078f)
        assertEquals(TrackerAction.ScheduleLongPress(300L), t.onDown(down))
        assertEquals(TrackerAction.OpenSwitcher(ScreenEdge.RIGHT, down), t.onLongPressElapsed())
    }

    @Test fun `disabled Back cannot arm or fire from either installed hold strip`() {
        for (edge in listOf(ScreenEdge.LEFT, ScreenEdge.RIGHT)) {
            val t = tracker(edge, GestureConfiguration(
                leftEdgeBackEnabled = edge != ScreenEdge.LEFT,
                rightEdgeBackEnabled = edge != ScreenEdge.RIGHT,
            ))
            val down = if (edge == ScreenEdge.LEFT) 2f else 1078f
            val inward = if (edge == ScreenEdge.LEFT) 102f else 978f
            assertEquals(TrackerAction.ScheduleLongPress(300L), t.onDown(sample(down)))
            assertEquals(TrackerAction.None, t.onMove(sample(inward, 100L)))
            assertEquals(TrackerAction.None, t.onLongPressElapsed())
            assertTrue(t.onUp(sample(inward, 200L)) is TrackerAction.Unused)
            t.onDown(sample(down))
            assertTrue(t.onUp(sample(inward, 200L)) is TrackerAction.Unused)
            t.onDown(sample(down))
            assertEquals(TrackerAction.OpenSwitcher(edge, sample(down)), t.onLongPressElapsed())
        }
    }
}
