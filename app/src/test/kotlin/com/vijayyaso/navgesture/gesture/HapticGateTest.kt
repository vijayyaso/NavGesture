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

class HapticGateTest {

    // Feeds a real tracker at density 3 through the gate and lists the actions that buzzed.
    private class Run(
        edge: ScreenEdge,
        configuration: GestureConfiguration = GestureConfiguration(),
    ) {
        private val tracker = GestureTracker(edge, configuration, density = 3f)
        private val gate = HapticGate(edge)
        val ticks = mutableListOf<TrackerAction>()
        private var time = 0L

        private fun feed(action: TrackerAction): TrackerAction {
            if (gate.shouldTick(action)) ticks += action
            return action
        }

        fun down(x: Float, y: Float) {
            gate.reset()
            feed(tracker.onDown(TouchSample(x, y, time)))
        }

        fun move(x: Float, y: Float): TrackerAction {
            time += 20L
            return feed(tracker.onMove(TouchSample(x, y, time)))
        }

        fun hold(afterMs: Long = 300L): TrackerAction {
            time += afterMs
            return feed(tracker.onHoldElapsed(time))
        }

        fun up(x: Float, y: Float): TrackerAction {
            time += 20L
            return feed(tracker.onUp(TouchSample(x, y, time)))
        }
    }

    @Test
    fun `a back swipe buzzes once, on arming`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
        assertTrue(run.ticks.single() is TrackerAction.Armed)
    }

    @Test
    fun `a back swipe pulled in and pushed out again buzzes on the re-arm too`() {
        val run = Run(ScreenEdge.RIGHT)
        run.down(1080f, 1200f)
        run.move(900f, 1200f)
        assertEquals(TrackerAction.Disarmed, run.move(1070f, 1200f))
        run.move(900f, 1200f)
        assertTrue(run.up(900f, 1200f) is TrackerAction.Fire)
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks.all { it is TrackerAction.Armed })
    }

    @Test
    fun `a disarmed swipe released past the trigger in one event buzzes on fire`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        assertEquals(TrackerAction.Disarmed, run.move(30f, 1200f))
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks.last() is TrackerAction.Fire)
    }

    @Test
    fun `a reverted back swipe buzzes only for its arm`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        run.move(3f, 1200f)
        assertEquals(TrackerAction.Reverted, run.up(3f, 1200f))
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `home buzzes on fire, not on the way up`() {
        val run = Run(ScreenEdge.BOTTOM)
        run.down(540f, 2300f)
        assertTrue(run.move(540f, 2200f) is TrackerAction.Armed)
        assertTrue(run.ticks.isEmpty())
        assertTrue(run.up(540f, 2100f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `recents buzzes once, when the pause primes it`() {
        val run = Run(ScreenEdge.BOTTOM)
        run.down(540f, 2395f)
        // 300px up is past the 250px Recents line.
        run.move(540f, 2095f)
        assertEquals(RECENTS_PENDING, run.hold())
        assertEquals(
            TrackerAction.Fire(RECENTS_RUN, ScreenEdge.BOTTOM),
            run.up(540f, 2095f),
        )
        assertEquals(1, run.ticks.size)
        assertEquals(RECENTS_PENDING, run.ticks.single())
    }

    @Test
    fun `a swipe batched into a down and an up still buzzes`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `each gesture buzzes on its own`() {
        val run = Run(ScreenEdge.LEFT)
        repeat(2) {
            run.down(0f, 1200f)
            run.move(180f, 1200f)
            run.up(180f, 1200f)
        }
        assertEquals(2, run.ticks.size)
    }

    @Test
    fun `a bottom arm with no pause action buzzes like a side arm, and its release stays quiet`() {
        val noPause = SwipeSlots().with(SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE), SwipeAction.None)
        val run = Run(ScreenEdge.BOTTOM, GestureConfiguration(slots = noPause))
        run.down(540f, 2300f)
        assertTrue(run.move(540f, 2200f) is TrackerAction.Armed)
        assertTrue(run.up(540f, 2100f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
        assertTrue(run.ticks.single() is TrackerAction.Armed)
    }

    // Density 3: the trigger is 72 px and the long line 360 px from DOWN.
    private val leftLongHome = GestureConfiguration(
        slots = SwipeSlots().with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG), SwipeAction.Home),
    )

    @Test
    fun `a far swipe ticks on arming and again on entering the long swipe, and its release stays quiet`() {
        val run = Run(ScreenEdge.LEFT, leftLongHome)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        assertEquals(TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG), run.move(500f, 1200f))
        assertTrue(run.up(500f, 1200f) is TrackerAction.Fire)
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks[0] is TrackerAction.Armed)
        assertEquals(TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG), run.ticks[1])
    }

    @Test
    fun `falling back from the long swipe to the short one is silent`() {
        val run = Run(ScreenEdge.LEFT, leftLongHome)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        run.move(500f, 1200f)
        assertEquals(TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT), run.move(200f, 1200f))
        assertTrue(run.up(200f, 1200f) is TrackerAction.Fire)
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks.none { it == TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT) })
    }

    @Test
    fun `with only a long slot the arm is quiet and entering the long swipe ticks once`() {
        val longOnly = SwipeSlots()
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT), SwipeAction.None)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG), SwipeAction.Screenshot)
        val run = Run(ScreenEdge.LEFT, GestureConfiguration(slots = longOnly))
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        run.move(500f, 1200f)
        assertTrue(run.up(500f, 1200f) is TrackerAction.Fire)
        assertEquals(listOf<TrackerAction>(TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.LONG)), run.ticks)
    }

    @Test
    fun `an arm with nothing pending stays quiet`() {
        val longOnly = SwipeSlots()
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT), SwipeAction.None)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG), SwipeAction.Screenshot)
        val run = Run(ScreenEdge.LEFT, GestureConfiguration(slots = longOnly))
        run.down(0f, 1200f)
        assertEquals(TrackerAction.Armed(null, SwipeAction.None), run.move(180f, 1200f))
        assertEquals(TrackerAction.Unused(RejectionReason.SLOT_EMPTY), run.up(180f, 1200f))
        assertTrue(run.ticks.isEmpty())
    }

    // Density 3: the touch slop is 24 px; the wait time is 350 ms.
    private val leftWaitHome = GestureConfiguration(
        slots = SwipeSlots()
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.WAIT), SwipeAction.Home)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG), SwipeAction.Screenshot),
    )

    private val waitPending = TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)

    @Test
    fun `a side arm ticks even while a wait action has its still clock polling`() {
        val run = Run(ScreenEdge.LEFT, leftWaitHome)
        run.down(0f, 1200f)
        assertEquals(TrackerAction.Armed(GestureTracker.FRAME_MS, SwipeAction.Back), run.move(180f, 1200f))
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(listOf<TrackerAction>(TrackerAction.Armed(GestureTracker.FRAME_MS, SwipeAction.Back)), run.ticks)
    }

    @Test
    fun `entering the wait buzzes, and the release after it stays quiet`() {
        val run = Run(ScreenEdge.LEFT, leftWaitHome)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        assertEquals(waitPending, run.hold(400L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Run(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.WAIT), SwipeAction.Home), ScreenEdge.LEFT),
            run.up(180f, 1200f),
        )
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks[0] is TrackerAction.Armed)
        assertEquals(waitPending, run.ticks[1])
    }

    @Test
    fun `leaving the wait is silent, back to the short swipe or on into the far swipe`() {
        val short = Run(ScreenEdge.LEFT, leftWaitHome)
        short.down(0f, 1200f)
        short.move(180f, 1200f)
        short.hold(400L)
        assertEquals(TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT, GestureTracker.FRAME_MS), short.move(260f, 1200f))
        assertTrue(short.up(260f, 1200f) is TrackerAction.Fire)
        assertEquals(2, short.ticks.size)
        assertEquals(waitPending, short.ticks.last())

        val far = Run(ScreenEdge.LEFT, leftWaitHome)
        far.down(0f, 1200f)
        far.move(180f, 1200f)
        far.hold(400L)
        assertEquals(TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.LONG, GestureTracker.FRAME_MS), far.move(500f, 1200f))
        assertTrue(far.up(500f, 1200f) is TrackerAction.Fire)
        assertEquals(2, far.ticks.size)
        assertEquals(waitPending, far.ticks.last())
    }

    @Test
    fun `a wait reached again after moving on buzzes again`() {
        val run = Run(ScreenEdge.LEFT, leftWaitHome)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        run.hold(400L)
        run.move(260f, 1200f)
        assertEquals(waitPending, run.hold(400L))
        assertTrue(run.up(260f, 1200f) is TrackerAction.Fire)
        assertEquals(3, run.ticks.size)
        assertEquals(listOf(waitPending, waitPending), run.ticks.drop(1))
    }

    @Test
    fun `with only a wait slot the arm is quiet and the wait buzzes once`() {
        val waitOnly = SwipeSlots()
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT), SwipeAction.None)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.WAIT), SwipeAction.Home)
        val run = Run(ScreenEdge.LEFT, GestureConfiguration(slots = waitOnly))
        run.down(0f, 1200f)
        assertEquals(TrackerAction.Armed(GestureTracker.FRAME_MS, SwipeAction.None), run.move(180f, 1200f))
        run.hold(400L)
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(listOf<TrackerAction>(waitPending), run.ticks)
    }
}
