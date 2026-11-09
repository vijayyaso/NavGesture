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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 1080 x 2400 screen at density 2.75: the corner zone is the first and last 129.6 px,
 * and the 32 dp trigger is 88 px.
 */
class CornerGestureTest {
    private val both = GestureConfiguration(
        leftCornerShortcut = CornerShortcut.Notifications,
        rightCornerShortcut = CornerShortcut.RecentsList,
    )

    private fun sample(x: Float, y: Float, t: Long = 0L) = TouchSample(x, y, t)

    private fun tracker(
        configuration: GestureConfiguration = both,
        width: Float? = 1080f,
    ) = GestureTracker(
        ScreenEdge.BOTTOM,
        configuration,
        2.75f,
        handleCenterXPx = 540f,
        screenHeightPx = 2400f,
        screenWidthPx = width,
    )

    private val leftFire = TrackerAction.Fire(GestureAction.Corner(BottomCorner.LEFT), ScreenEdge.BOTTOM)

    @Test fun `a 45 degree diagonal from the left corner fires on release`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        t.onMove(sample(110f, 2345f, 30L))
        assertEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(140f, 2315f, 60L)))
        // Nothing more happens until the finger lifts, however it moves.
        assertEquals(TrackerAction.None, t.onMove(sample(140f, 1500f, 90L)))
        assertEquals(leftFire, t.onUp(sample(140f, 1500f, 120L)))
    }

    @Test fun `the right corner swipes up and to the left`() {
        val t = tracker()
        t.onDown(sample(1030f, 2395f))
        assertEquals(TrackerAction.CornerReady(BottomCorner.RIGHT), t.onMove(sample(950f, 2315f, 40L)))
        assertEquals(
            TrackerAction.Fire(GestureAction.Corner(BottomCorner.RIGHT), ScreenEdge.BOTTOM),
            t.onUp(sample(950f, 2315f, 80L)),
        )
    }

    @Test fun `a slow diagonal past the arm window still counts`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        t.onMove(sample(70f, 2390f, 500L))
        assertEquals(TrackerAction.None, t.onMove(sample(80f, 2380f, 1_200L)))
        assertEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(150f, 2310f, 1_500L)))
        assertEquals(leftFire, t.onUp(sample(150f, 2310f, 1_800L)))
    }

    @Test fun `a slow corner touch that never slants is replayed, not Home`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        t.onMove(sample(62f, 2393f, 1_200L))
        // Past the arm window only the corner may claim it, so a late rise is not Home.
        assertEquals(TrackerAction.None, t.onMove(sample(62f, 2200f, 1_400L)))
        assertTrue(t.onUp(sample(62f, 2200f, 1_500L)) is TrackerAction.Unused)
    }

    @Test fun `a diagonal batched into a down and an up still fires`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        assertEquals(leftFire, t.onUp(sample(150f, 2310f, 40L)))
    }

    @Test fun `a steep swipe from the corner stays Home`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        // About 4 degrees from straight up.
        for ((i, y) in listOf(2350f, 2300f, 2200f, 2000f).withIndex()) {
            assertNotEquals(
                TrackerAction.CornerReady(BottomCorner.LEFT),
                t.onMove(sample(60f + (i + 1) * 7f, y, (i + 1) * 20L)),
            )
        }
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(88f, 2000f, 100L)),
        )
    }

    @Test fun `a flat drag from the corner is not a corner swipe`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        // About 80 degrees from straight up.
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(200f, 2370f, 40L)))
        assertNotEquals(leftFire, t.onUp(sample(200f, 2370f, 80L)))
    }

    @Test fun `the trigger lies between 30 and 75 degrees`() {
        // 60 degrees: inward 100, up 58.
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        assertEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(160f, 2337f, 40L)))
        // 20 degrees: inward 40, up 110.
        val steep = tracker()
        steep.onDown(sample(60f, 2395f))
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), steep.onMove(sample(100f, 2285f, 40L)))
    }

    @Test fun `a short diagonal does not trigger yet`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        // 70 px long, under the 88 px trigger.
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(110f, 2345f, 40L)))
    }

    @Test fun `the corner zone ends at 12 percent of the width`() {
        val inside = tracker()
        inside.onDown(sample(129f, 2395f))
        assertEquals(TrackerAction.CornerReady(BottomCorner.LEFT), inside.onMove(sample(219f, 2305f, 40L)))
        val outside = tracker()
        outside.onDown(sample(131f, 2395f))
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), outside.onMove(sample(221f, 2305f, 40L)))
    }

    @Test fun `a centre start is never a corner`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f))
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(630f, 2305f, 40L)))
    }

    @Test fun `a corner set to nothing is a normal bottom touch`() {
        val t = tracker(GestureConfiguration(rightCornerShortcut = CornerShortcut.Screenshot))
        t.onDown(sample(60f, 2395f))
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(150f, 2305f, 40L)))
    }

    @Test fun `without the screen width there are no corners`() {
        val t = tracker(width = null)
        t.onDown(sample(60f, 2395f))
        assertNotEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(150f, 2305f, 40L)))
    }

    @Test fun `a cancel drops a ready corner`() {
        val t = tracker()
        t.onDown(sample(60f, 2395f))
        assertEquals(TrackerAction.CornerReady(BottomCorner.LEFT), t.onMove(sample(150f, 2305f, 40L)))
        t.onCancel()
        assertNotEquals(leftFire, t.onUp(sample(150f, 2305f, 80L)))
    }

    @Test fun `the corner beats the app list drag`() {
        val t = tracker(both.copy(edgeAppListEnabled = true))
        t.onDown(sample(60f, 2395f))
        // Arms Home at 40 degrees, then slants to 70 degrees past the 110 px list drag.
        t.onMove(sample(85f, 2360f, 20L))
        val action = t.onMove(sample(190f, 2345f, 40L))
        assertEquals(TrackerAction.CornerReady(BottomCorner.LEFT), action)
    }

    @Test fun `the corner ticks once, where it lands`() {
        val gate = HapticGate()
        assertTrue(gate.shouldTick(TrackerAction.CornerReady(BottomCorner.LEFT)))
        assertEquals(false, gate.shouldTick(leftFire))
    }
}
