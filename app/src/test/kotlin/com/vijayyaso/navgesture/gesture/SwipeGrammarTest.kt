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

package com.vijayyaso.navgesture.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One row per swipe rule: the slots it is played with, the touch, and what the tracker must
 * say at each step. A null expectation means that step is not what the row is about.
 */
class SwipeGrammarTest {

    private sealed interface Step {
        val expected: TrackerAction?

        data class Down(val x: Float, val y: Float, val t: Long, override val expected: TrackerAction? = null) : Step
        data class Move(val x: Float, val y: Float, val t: Long, override val expected: TrackerAction? = null) : Step
        data class Hold(val t: Long, override val expected: TrackerAction? = null) : Step
        data class Up(val x: Float, val y: Float, val t: Long, override val expected: TrackerAction?) : Step
    }

    private class Row(
        val rule: String,
        val edge: ScreenEdge,
        val slots: Map<SlotId.Edge, SwipeAction>,
        val steps: List<Step>,
    )

    private val leftShort = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val leftLong = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG)
    private val rightShort = SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val bottomShort = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val bottomPause = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE)

    private val slotEmpty = TrackerAction.Unused(RejectionReason.SLOT_EMPTY)
    private val frame = GestureTracker.FRAME_MS

    private fun fire(slot: SlotId.Edge, action: SwipeAction) =
        TrackerAction.Fire(GestureAction.Run(slot, action), slot.edge)

    // 2.75 density: 24 dp = 66 px, 10 dp = 27.5 px; the Recents line is 250 px up.
    private val rows = listOf(
        Row(
            "by default a side swipe arms for Back and fires it",
            ScreenEdge.LEFT,
            emptyMap(),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Up(105f, 1200f, 80L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "a side short swipe runs whatever its slot holds",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Up(105f, 1200f, 80L, fire(leftShort, SwipeAction.Home)),
            ),
        ),
        Row(
            "each side reads its own slot",
            ScreenEdge.RIGHT,
            mapOf(leftShort to SwipeAction.Home, rightShort to SwipeAction.Notifications),
            listOf(
                Step.Down(1075f, 1200f, 0L),
                Step.Move(975f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Notifications)),
                Step.Up(975f, 1200f, 80L, fire(rightShort, SwipeAction.Notifications)),
            ),
        ),
        Row(
            "a side swipe pulled back inside the trigger still reverts",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Screenshot)),
                Step.Move(20f, 1200f, 90L, TrackerAction.Disarmed),
                Step.Up(20f, 1200f, 100L, TrackerAction.Reverted),
            ),
        ),
        Row(
            "a re-arm reports the slot's action again",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.Recents),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L),
                Step.Move(20f, 1200f, 90L, TrackerAction.Disarmed),
                Step.Move(110f, 1200f, 120L, TrackerAction.Armed(null, SwipeAction.Recents)),
                Step.Up(110f, 1200f, 130L, fire(leftShort, SwipeAction.Recents)),
            ),
        ),
        Row(
            "a side with nothing set never arms, and its swipe goes to the app",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.None),
                Step.Up(105f, 1200f, 80L, slotEmpty),
            ),
        ),
        Row(
            "a side with only a long slot arms with nothing pending, and a short swipe goes to the app",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None, leftLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.None)),
                Step.Up(105f, 1200f, 80L, slotEmpty),
            ),
        ),
        Row(
            "a side DOWN then UP with no MOVE runs the short slot",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Up(105f, 1200f, 80L, fire(leftShort, SwipeAction.Home)),
            ),
        ),
        Row(
            "a side DOWN then UP on an empty side goes to the app",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Up(105f, 1200f, 80L, slotEmpty),
            ),
        ),
        Row(
            "a bottom swipe runs the bottom short slot",
            ScreenEdge.BOTTOM,
            mapOf(bottomShort to SwipeAction.Notifications),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2295f, 50L, TrackerAction.Armed(frame, SwipeAction.Notifications)),
                Step.Up(540f, 2295f, 70L, fire(bottomShort, SwipeAction.Notifications)),
            ),
        ),
        Row(
            "with bottom short empty the bottom still arms, and a plain swipe goes to the app",
            ScreenEdge.BOTTOM,
            mapOf(bottomShort to SwipeAction.None),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2295f, 50L, TrackerAction.Armed(frame, SwipeAction.None)),
                Step.Up(540f, 2295f, 70L, slotEmpty),
            ),
        ),
        Row(
            "with bottom short empty a pause still runs the pause slot",
            ScreenEdge.BOTTOM,
            mapOf(bottomShort to SwipeAction.None),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2295f, 50L),
                Step.Move(540f, 2095f, 100L),
                Step.Hold(400L, TrackerAction.Pending(SwipeAction.Recents, SwipeKind.PAUSE)),
                Step.Up(540f, 2095f, 500L, fire(bottomPause, SwipeAction.Recents)),
            ),
        ),
        Row(
            "with bottom short empty a flick out of the pause goes to the app",
            ScreenEdge.BOTTOM,
            mapOf(bottomShort to SwipeAction.None),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2095f, 100L),
                Step.Hold(400L, TrackerAction.Pending(SwipeAction.Recents, SwipeKind.PAUSE)),
                Step.Move(540f, 1600f, 450L),
                Step.Up(540f, 1200f, 460L, slotEmpty),
            ),
        ),
        Row(
            "with bottom short empty a DOWN then UP goes to the app",
            ScreenEdge.BOTTOM,
            mapOf(bottomShort to SwipeAction.None),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Up(540f, 2295f, 40L, slotEmpty),
            ),
        ),
        Row(
            "a pause runs whatever the pause slot holds",
            ScreenEdge.BOTTOM,
            mapOf(bottomPause to SwipeAction.Screenshot),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2295f, 50L, TrackerAction.Armed(frame, SwipeAction.Home)),
                Step.Move(540f, 2095f, 100L),
                Step.Hold(400L, TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.PAUSE)),
                Step.Up(540f, 2095f, 500L, fire(bottomPause, SwipeAction.Screenshot)),
            ),
        ),
        Row(
            "with bottom pause empty nothing polls, and a pause is a plain short swipe",
            ScreenEdge.BOTTOM,
            mapOf(bottomPause to SwipeAction.None),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2295f, 50L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Move(540f, 2095f, 100L),
                Step.Hold(400L, TrackerAction.None),
                Step.Up(540f, 2095f, 500L, fire(bottomShort, SwipeAction.Home)),
            ),
        ),
        Row(
            "with both bottom slots empty the bottom arms with nothing pending and goes to the app",
            ScreenEdge.BOTTOM,
            mapOf(bottomShort to SwipeAction.None, bottomPause to SwipeAction.None),
            listOf(
                Step.Down(540f, 2395f, 0L),
                Step.Move(540f, 2295f, 50L, TrackerAction.Armed(null, SwipeAction.None)),
                Step.Move(540f, 2095f, 100L),
                Step.Up(540f, 2095f, 500L, slotEmpty),
            ),
        ),
    )

    @Test fun `every swipe rule holds`() {
        rows.forEach { row ->
            val slots = row.slots.entries.fold(SwipeSlots()) { all, (slot, action) -> all.with(slot, action) }
            val tracker = GestureTracker(
                row.edge,
                GestureConfiguration(edgeAppListEnabled = false, slots = slots),
                2.75f,
            )
            row.steps.forEachIndexed { index, step ->
                val actual = when (step) {
                    is Step.Down -> tracker.onDown(TouchSample(step.x, step.y, step.t))
                    is Step.Move -> tracker.onMove(TouchSample(step.x, step.y, step.t))
                    is Step.Hold -> tracker.onHoldElapsed(step.t)
                    is Step.Up -> tracker.onUp(TouchSample(step.x, step.y, step.t))
                }
                step.expected?.let { assertEquals("${row.rule}, step ${index + 1}", it, actual) }
            }
        }
    }
}
