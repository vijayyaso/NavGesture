// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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
import org.junit.Test

/** A sideways drag owns its whole release: the panel decides, and a still finger stays Recents. */
class EdgeSwitcherGestureTest {
    private fun sample(x: Float = 540f, y: Float = 2395f, t: Long = 0L) = TouchSample(x, y, t)

    private fun tracker(
        edge: ScreenEdge = ScreenEdge.BOTTOM,
        configuration: GestureConfiguration = GestureConfiguration(),
    ) = GestureTracker(edge, configuration, 2.75f)

    /** Swipes 100 px up, then drags right. 40 dp is 110 px at this density. */
    private fun dragTrail(t: GestureTracker, sideways: Float = 200f): TouchSample {
        t.onDown(sample())
        t.onMove(sample(y = 2295f, t = 50L))
        val drag = sample(x = 540f + sideways, y = 2295f, t = 100L)
        t.onMove(drag)
        return drag
    }

    @Test fun `a sideways drag during the rise opens the switcher`() {
        val t = tracker()
        t.onDown(sample())
        t.onMove(sample(y = 2295f, t = 50L))
        assertEquals(
            TrackerAction.OpenSwitcher(
                ScreenEdge.BOTTOM,
                sample(x = 740f, y = 2295f, t = 100L),
                recents = true,
            ),
            t.onMove(sample(x = 740f, y = 2295f, t = 100L)),
        )
    }

    @Test fun `a leftward drag opens favourites`() {
        val t = tracker()
        t.onDown(sample(x = 800f))
        t.onMove(sample(x = 800f, y = 2295f, t = 50L))
        assertEquals(
            TrackerAction.OpenSwitcher(
                ScreenEdge.BOTTOM,
                sample(x = 570f, y = 2295f, t = 100L),
                recents = false,
            ),
            t.onMove(sample(x = 570f, y = 2295f, t = 100L)),
        )
    }

    @Test fun `the switcher then owns movement, release and any later pause`() {
        val t = tracker()
        dragTrail(t)
        assertEquals(TrackerAction.None, t.onHoldElapsed(4_000L))
        assertEquals(TrackerAction.None, t.onMove(sample(x = 900f, y = 2295f, t = 5_000L)))
        assertEquals(TrackerAction.None, t.onUp(sample(x = 900f, y = 2295f, t = 6_000L)))
    }

    @Test fun `the drag opens before the Recents line too`() {
        val t = tracker()
        t.onDown(sample())
        t.onMove(sample(y = 2250f, t = 50L))
        assertEquals(
            TrackerAction.OpenSwitcher(
                ScreenEdge.BOTTOM,
                sample(x = 740f, y = 2250f, t = 100L),
                recents = true,
            ),
            t.onMove(sample(x = 740f, y = 2250f, t = 100L)),
        )
    }

    @Test fun `a sideways drift under the trigger is not the app list`() {
        val t = tracker()
        dragTrail(t, sideways = 100f)
        // Below the return line and not moving up, so the swipe reverts rather than firing.
        assertEquals(
            TrackerAction.Reverted,
            t.onUp(sample(x = 640f, y = 2295f, t = 150L)),
        )
    }

    @Test fun `a wavy slow swipe still reaches Recents`() {
        val t = tracker()
        t.onDown(sample())
        // A left-right wobble while rising. The last move passes 40 dp sideways but the
        // rise is far bigger, so it is a swipe up, not a sideways drag.
        assertEquals(TrackerAction.Armed(scheduleHoldMs = 16L), t.onMove(sample(x = 600f, y = 2245f, t = 50L)))
        assertEquals(TrackerAction.None, t.onMove(sample(x = 470f, y = 2145f, t = 100L)))
        assertEquals(TrackerAction.None, t.onMove(sample(x = 670f, y = 2095f, t = 150L)))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(500L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onUp(sample(x = 670f, y = 2095f, t = 600L)),
        )
    }

    @Test fun `a sideways stroke that beats the rise opens the list`() {
        val t = tracker()
        t.onDown(sample())
        t.onMove(sample(y = 2245f, t = 50L))
        val drag = sample(x = 760f, y = 2235f, t = 90L)
        assertEquals(
            TrackerAction.OpenSwitcher(ScreenEdge.BOTTOM, drag, recents = true),
            t.onMove(drag),
        )
    }
    @Test fun `a still pause primes Recents even with the app list on`() {
        val t = tracker()
        t.onDown(sample())
        t.onMove(sample(y = 2295f, t = 50L))
        t.onMove(sample(y = 2095f, t = 100L))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(400L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onUp(sample(y = 2095f, t = 500L)),
        )
    }

    @Test fun `the app list can be switched off`() {
        val t = tracker(configuration = GestureConfiguration(edgeAppListEnabled = false))
        dragTrail(t)
        // With the list off a sideways drag is just a swipe that stopped, so it reverts.
        assertEquals(
            TrackerAction.Reverted,
            t.onUp(sample(x = 740f, y = 2295f, t = 150L)),
        )
    }

    @Test fun `cancel during the switcher leaves nothing to open`() {
        val t = tracker()
        dragTrail(t)
        t.onCancel()
        assertEquals(TrackerAction.None, t.onHoldElapsed(500L))
        assertEquals(TrackerAction.None, t.onMove(sample(x = 900f, y = 2295f, t = 600L)))
    }

    @Test fun `a side edge never opens the switcher`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onDown(TouchSample(2f, 1200f, 0L)))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test fun `a side swipe is still Back with the switcher on`() {
        val t = tracker(ScreenEdge.RIGHT)
        t.onDown(TouchSample(1078f, 1200f, 0L))
        t.onMove(TouchSample(978f, 1200f, 80L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.RIGHT),
            t.onUp(TouchSample(978f, 1200f, 120L)),
        )
    }

    @Test fun `a quick bottom swipe with no pause is still Home`() {
        val t = tracker()
        t.onDown(sample())
        t.onMove(sample(y = 2295f, t = 50L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(y = 2295f, t = 70L)),
        )
    }
}
