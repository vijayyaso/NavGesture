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

class GestureTrackerTest {

    /** 24dp = 66px, 10dp = 27.5px. On the default 2400px screen the Recents line is 250px up. */
    private val phoneDensity = 2.75f

    private val tabletDensity = 3.5f

    private fun tracker(
        edge: ScreenEdge,
        density: Float = phoneDensity,
        configuration: GestureConfiguration = GestureConfiguration(edgeAppListEnabled = false),
    ) = GestureTracker(edge, configuration, density)

    private fun sample(x: Float, y: Float, t: Long) = TouchSample(x, y, t)

    @Test
    fun `inward swipe from the left edge fires Back on release`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onDown(sample(5f, 1200f, 0L)))
        // 100px is 36dp, past the 24dp side threshold.
        val armed = t.onMove(sample(105f, 1200f, 60L))
        assertTrue("expected arming, got $armed", armed is TrackerAction.Armed)
        assertEquals(null, (armed as TrackerAction.Armed).scheduleHoldMs)
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(105f, 1200f, 80L)),
        )
    }

    @Test
    fun `inward swipe from the right edge fires Back on release`() {
        val t = tracker(ScreenEdge.RIGHT)
        t.onDown(sample(1075f, 1200f, 0L))
        assertTrue(t.onMove(sample(975f, 1200f, 60L)) is TrackerAction.Armed)
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.RIGHT),
            t.onUp(sample(975f, 1200f, 80L)),
        )
    }

    @Test
    fun `a side hold does nothing because side edges have no hold action`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(105f, 1200f, 60L))
        assertEquals(TrackerAction.None, t.onHoldElapsed(500L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(105f, 1200f, 80L)),
        )
    }

    @Test
    fun `upward swipe released while armed fires Home`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        val armed = t.onMove(sample(540f, 2295f, 50L))
        assertTrue(armed is TrackerAction.Armed)
        // The bottom edge polls for a pause once per frame.
        assertEquals(GestureTracker.FRAME_MS, (armed as TrackerAction.Armed).scheduleHoldMs)
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 2295f, 70L)),
        )
    }

    @Test
    fun `a pause low on the screen keeps polling and the release goes back to the app`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        // 100px up is short of half the 250px line, where POCO stays in the app.
        t.onMove(sample(540f, 2295f, 50L))
        assertEquals(TrackerAction.RearmHold(GestureTracker.FRAME_MS), t.onHoldElapsed(600L))
        assertEquals(TrackerAction.Reverted, t.onUp(sample(540f, 2295f, 700L)))
    }

    @Test
    fun `a short quick flick released low is still Home`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2330f, 20L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 2295f, 40L)),
        )
    }

    @Test
    fun `a pause past the Recents line primes Recents and the release fires it`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        // 300px up is past the line; the finger then stays put.
        t.onMove(sample(540f, 2095f, 100L))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(400L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 2095f, 500L)),
        )
    }

    @Test
    fun `a pause right after crossing the line is not yet a pause`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 30L))
        t.onMove(sample(540f, 2095f, 60L))
        // One frame later the tail is still far behind a finger moving 6.6px/ms.
        assertEquals(TrackerAction.RearmHold(GestureTracker.FRAME_MS), t.onHoldElapsed(76L))
    }

    @Test
    fun `a fast flick after priming is still Home`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2095f, 100L))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(400L))
        t.onMove(sample(540f, 1600f, 450L))
        // 895px in 360ms is 0.9dp/ms, past the 0.5dp/ms flick.
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 1200f, 460L)),
        )
    }

    @Test
    fun `a fast swipe past the line with no pause is Home`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2200f, 30L))
        assertEquals(TrackerAction.RearmHold(GestureTracker.FRAME_MS), t.onHoldElapsed(40L))
        t.onMove(sample(540f, 1600f, 60L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 1300f, 80L)),
        )
    }

    @Test
    fun `Recents primes only once`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2095f, 100L))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(400L))
        assertEquals(TrackerAction.None, t.onHoldElapsed(416L))
    }

    @Test
    fun `a long hold after arming is still a gesture, not a timeout`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        // 5s is far past the 1000ms maximumArmDurationMs.
        assertEquals(TrackerAction.None, t.onMove(sample(540f, 2000f, 5_000L)))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(5_400L))
    }

    @Test
    fun `the Recents line scales with the screen height`() {
        // 300px up passes the line on a 2400px screen (250px) but not on a 3200px one (333px).
        val tall = GestureTracker(ScreenEdge.BOTTOM, GestureConfiguration(), phoneDensity, screenHeightPx = 3200f)
        tall.onDown(sample(540f, 3195f, 0L))
        tall.onMove(sample(540f, 2895f, 100L))
        assertEquals(TrackerAction.RearmHold(GestureTracker.FRAME_MS), tall.onHoldElapsed(400L))
    }

    @Test
    fun `a tap is unused so the caller can replay it`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        val result = t.onUp(sample(540f, 2395f, 40L))
        assertEquals(TrackerAction.Unused(RejectionReason.NO_MOVEMENT), result)
    }

    @Test
    fun `too short a swipe is unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(25f, 1200f, 40L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.INSUFFICIENT_MOVEMENT),
            t.onUp(sample(25f, 1200f, 60L)),
        )
    }

    @Test
    fun `swiping outward from an edge is unused as the wrong direction`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(50f, 1200f, 0L))
        t.onMove(sample(10f, 1200f, 40L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.WRONG_DIRECTION),
            t.onUp(sample(10f, 1200f, 60L)),
        )
    }

    @Test
    fun `swiping downward from the bottom edge is unused as the wrong direction`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2300f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.WRONG_DIRECTION),
            t.onUp(sample(540f, 2360f, 60L)),
        )
    }

    @Test
    fun `a diagonal swipe outside the tolerance cone does not arm`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        // 100px right, 200px down: outside the 45-degree cone.
        assertEquals(TrackerAction.None, t.onMove(sample(105f, 1400f, 60L)))
        assertEquals(
            TrackerAction.Unused(RejectionReason.OFF_AXIS),
            t.onUp(sample(105f, 1400f, 80L)),
        )
    }

    @Test
    fun `a swipe inside the tolerance cone still arms`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        // 100px right, 80px down: inside the 45-degree cone.
        assertTrue(t.onMove(sample(105f, 1280f, 60L)) is TrackerAction.Armed)
    }

    @Test
    fun `taking too long to arm reports a timeout and stops tracking`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM),
            t.onMove(sample(15f, 1200f, 1_500L)),
        )
        assertEquals(TrackerAction.None, t.onMove(sample(200f, 1200f, 1_600L)))
        assertTrue(t.onUp(sample(200f, 1200f, 1_700L)) is TrackerAction.Unused)
    }

    @Test
    fun `a cancelled stream produces nothing on a later hold`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        t.onCancel()
        assertEquals(TrackerAction.None, t.onHoldElapsed(500L))
    }

    @Test
    fun `arming distance scales with density rather than pixels`() {
        // 80px is 29dp at 2.75x (arms, past 24dp) but only 23dp at 3.5x (does not).
        val onPhone = tracker(ScreenEdge.LEFT, phoneDensity)
        onPhone.onDown(sample(5f, 1200f, 0L))
        assertTrue(onPhone.onMove(sample(85f, 1200f, 60L)) is TrackerAction.Armed)

        val onTablet = tracker(ScreenEdge.LEFT, tabletDensity)
        onTablet.onDown(sample(5f, 1200f, 0L))
        assertEquals(TrackerAction.None, onTablet.onMove(sample(85f, 1200f, 60L)))
    }

    @Test
    fun `the bottom edge arms sooner than the sides by design`() {
        // 40px is 14dp: past the bottom's 10dp threshold, short of the sides' 24dp.
        val bottom = tracker(ScreenEdge.BOTTOM)
        bottom.onDown(sample(540f, 2395f, 0L))
        assertTrue(bottom.onMove(sample(540f, 2355f, 40L)) is TrackerAction.Armed)

        val side = tracker(ScreenEdge.LEFT)
        side.onDown(sample(5f, 1200f, 0L))
        assertEquals(TrackerAction.None, side.onMove(sample(45f, 1200f, 40L)))
    }

    @Test
    fun `a pause below a higher Recents line goes back to the app`() {
        // 30% of 2400px is 720px; a pause 300px up is below it.
        val configuration = GestureConfiguration(recentsLinePercent = 30f)
        val t = tracker(ScreenEdge.BOTTOM, configuration = configuration)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2095f, 100L))
        // Not primed, and 300px is below half of 720px.
        assertEquals(TrackerAction.RearmHold(GestureTracker.FRAME_MS), t.onHoldElapsed(400L))
        assertEquals(TrackerAction.Reverted, t.onUp(sample(540f, 2095f, 500L)))
    }

    @Test
    fun `a larger minimum distance delays arming`() {
        val configuration = GestureConfiguration(sideMinimumSwipeDistanceDp = 60f)
        val t = tracker(ScreenEdge.LEFT, configuration = configuration)
        t.onDown(sample(5f, 1200f, 0L))
        // 100px is 36dp: enough by default, not enough at 60dp.
        assertEquals(TrackerAction.None, t.onMove(sample(105f, 1200f, 60L)))
        assertTrue(t.onMove(sample(180f, 1200f, 90L)) is TrackerAction.Armed)
    }

    /**
     * Event batching can deliver a stream as DOWN then UP with no MOVE at all, which the
     * emulator does under load. The release alone still has to count as a swipe.
     */
    @Test
    fun `a swipe delivered as down then up with no move still fires`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onDown(sample(2f, 1200f, 0L)))
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(300f, 1200f, 120L)),
        )
    }

    @Test
    fun `a down then up pair that is too slow to arm is still unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(2f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM),
            t.onUp(sample(300f, 1200f, 5_000L)),
        )
    }

    @Test
    fun `a down then up pair that is too short is still unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(2f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.INSUFFICIENT_MOVEMENT),
            t.onUp(sample(20f, 1200f, 60L)),
        )
    }

    @Test
    fun `a down then up pair going the wrong way is still unused`() {
        val t = tracker(ScreenEdge.RIGHT, density = phoneDensity)
        t.onDown(sample(1070f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.WRONG_DIRECTION),
            t.onUp(sample(1078f, 1200f, 60L)),
        )
    }

    @Test
    fun `a move with no preceding down is ignored`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onMove(sample(105f, 1200f, 60L)))
    }

    @Test
    fun `a release with no preceding down reports no movement`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(
            TrackerAction.Unused(RejectionReason.NO_MOVEMENT),
            t.onUp(sample(105f, 1200f, 60L)),
        )
    }

    @Test
    fun `a hold that elapses before arming does nothing`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        assertEquals(TrackerAction.None, t.onHoldElapsed(500L))
    }

    @Test
    fun `moving after Recents is primed stays primed`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2095f, 100L))
        assertEquals(TrackerAction.RecentsPrimed, t.onHoldElapsed(400L))
        assertEquals(TrackerAction.None, t.onMove(sample(560f, 2000f, 600L)))
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onUp(sample(560f, 2000f, 900L)),
        )
    }

    @Test
    fun `moving after arming on a side edge restarts no hold it does not have`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        assertTrue(t.onMove(sample(105f, 1200f, 60L)) is TrackerAction.Armed)
        assertEquals(TrackerAction.None, t.onMove(sample(205f, 1200f, 90L)))
    }

    @Test
    fun `sliding sideways past the line is not a pause while it moves`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2095f, 100L))
        // 360px sideways over the last 110ms is 1.2dp/ms, past the 1dp/ms hold speed.
        t.onMove(sample(900f, 2095f, 200L))
        assertEquals(TrackerAction.RearmHold(GestureTracker.FRAME_MS), t.onHoldElapsed(210L))
    }

    @Test
    fun `a release that only moved along the edge is unused as too short`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.INSUFFICIENT_MOVEMENT),
            t.onUp(sample(5f, 1300f, 60L)),
        )
    }

    @Test
    fun `a second release after the gesture already ended is unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        assertTrue(t.onMove(sample(105f, 1200f, 60L)) is TrackerAction.Armed)
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(105f, 1200f, 80L)),
        )
        assertEquals(
            TrackerAction.Unused(RejectionReason.INSUFFICIENT_MOVEMENT),
            t.onUp(sample(105f, 1200f, 90L)),
        )
    }

    @Test
    fun `a side swipe pulled back inside the trigger disarms`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        assertTrue(t.onMove(sample(205f, 1200f, 60L)) is TrackerAction.Armed)
        // 50px of inward travel is 18dp, back inside the 24dp trigger.
        assertEquals(TrackerAction.Disarmed, t.onMove(sample(55f, 1200f, 120L)))
        assertEquals(TrackerAction.None, t.onMove(sample(10f, 1200f, 160L)))
    }

    @Test
    fun `a side swipe brought back to the edge does nothing on release`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(205f, 1200f, 60L))
        t.onMove(sample(5f, 1200f, 160L))
        assertEquals(TrackerAction.Reverted, t.onUp(sample(5f, 1200f, 180L)))
    }

    @Test
    fun `a right-edge swipe brought back to the edge does nothing on release`() {
        val t = tracker(ScreenEdge.RIGHT)
        t.onDown(sample(1075f, 1200f, 0L))
        t.onMove(sample(875f, 1200f, 60L))
        assertEquals(TrackerAction.Disarmed, t.onMove(sample(1070f, 1200f, 160L)))
        assertEquals(TrackerAction.Reverted, t.onUp(sample(1070f, 1200f, 180L)))
    }

    @Test
    fun `a release batched back at the edge reverts with no move to disarm it`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(205f, 1200f, 60L))
        assertEquals(TrackerAction.Reverted, t.onUp(sample(5f, 1200f, 160L)))
    }

    @Test
    fun `a reverted swipe pushed back out re-arms and fires Back`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(205f, 1200f, 60L))
        assertEquals(TrackerAction.Disarmed, t.onMove(sample(20f, 1200f, 160L)))
        assertEquals(TrackerAction.Armed(scheduleHoldMs = null), t.onMove(sample(205f, 1200f, 260L)))
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(205f, 1200f, 280L)),
        )
    }

    @Test
    fun `a small wobble toward the edge before release still fires Back`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(205f, 1200f, 60L))
        // 30px back still leaves 170px, well past the 66px trigger.
        assertEquals(TrackerAction.None, t.onMove(sample(175f, 1200f, 90L)))
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(175f, 1200f, 100L)),
        )
    }

    @Test
    fun `a revert is measured against the configured trigger distance`() {
        val configuration = GestureConfiguration(sideMinimumSwipeDistanceDp = 60f)
        val t = tracker(ScreenEdge.LEFT, configuration = configuration)
        t.onDown(sample(5f, 1200f, 0L))
        assertTrue(t.onMove(sample(205f, 1200f, 60L)) is TrackerAction.Armed)
        // 105px is 38dp: past the 24dp default, short of the 60dp asked for.
        assertEquals(TrackerAction.Reverted, t.onUp(sample(110f, 1200f, 120L)))
    }

    // Upstream fired Home here; NavGesture follows POCO, where pulling back returns to the app.
    @Test
    fun `a swipe pulled back down goes back to the app`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        assertTrue(t.onMove(sample(540f, 2295f, 50L)) is TrackerAction.Armed)
        t.onMove(sample(540f, 2340f, 120L))
        t.onMove(sample(540f, 2390f, 200L))
        assertEquals(TrackerAction.Reverted, t.onUp(sample(540f, 2390f, 210L)))
    }
}
