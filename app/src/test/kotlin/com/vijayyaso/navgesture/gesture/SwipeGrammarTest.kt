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
        /** Splits both side edges into halves. */
        val split: Boolean = false,
        /** Changes the distances a row is played with. */
        val tune: (GestureConfiguration) -> GestureConfiguration = { it },
    )

    private val leftShort = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val leftLong = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG)
    private val topShort = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.SHORT)
    private val topLong = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.LONG)
    private val lowerShort = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.BOTTOM_HALF, SwipeKind.SHORT)
    private val rightLong = SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.WHOLE, SwipeKind.LONG)
    private val rightShort = SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val bottomShort = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val bottomPause = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE)
    private val leftWait = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.WAIT)
    private val topWait = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.WAIT)
    private val lowerWait = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.BOTTOM_HALF, SwipeKind.WAIT)

    private val slotEmpty = TrackerAction.Unused(RejectionReason.SLOT_EMPTY)
    private val frame = GestureTracker.FRAME_MS
    private val poll = TrackerAction.RearmHold(GestureTracker.FRAME_MS)

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

        // Long swipes. The long line is 120 dp = 330 px from DOWN, so from x 5 it is at x 335.
        Row(
            "past the trigger and under the long line a side swipe is short, even with a long slot",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(330f, 1200f, 90L, TrackerAction.None),
                Step.Up(330f, 1200f, 100L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "past the long line with the long slot set the long action is pending and runs",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(335f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Move(500f, 1200f, 110L, TrackerAction.None),
                Step.Up(500f, 1200f, 120L, fire(leftLong, SwipeAction.Home)),
            ),
        ),
        Row(
            "past the long line with the long slot empty the short action stays, with no report",
            ScreenEdge.LEFT,
            emptyMap(),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(500f, 1200f, 90L, TrackerAction.None),
                Step.Up(500f, 1200f, 100L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "the right edge measures its long line inward too",
            ScreenEdge.RIGHT,
            mapOf(rightLong to SwipeAction.Recents),
            listOf(
                Step.Down(1075f, 1200f, 0L),
                Step.Move(975f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(700f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Recents, SwipeKind.LONG)),
                Step.Up(700f, 1200f, 100L, fire(rightLong, SwipeAction.Recents)),
            ),
        ),
        Row(
            "pulled back across the long line the swipe is short again, and long again when pushed out",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(400f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Move(200f, 1200f, 120L, TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT)),
                Step.Move(400f, 1200f, 150L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Up(400f, 1200f, 160L, fire(leftLong, SwipeAction.Home)),
            ),
        ),
        Row(
            "a long swipe pulled back under the long line and lifted runs the short action",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(400f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Move(200f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT)),
                Step.Up(200f, 1200f, 100L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "a long swipe pulled inside the trigger disarms, and pushed out re-arms in the band it reaches",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(400f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Move(20f, 1200f, 120L, TrackerAction.Disarmed),
                Step.Move(400f, 1200f, 150L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Move(20f, 1200f, 180L, TrackerAction.Disarmed),
                Step.Move(105f, 1200f, 210L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(400f, 1200f, 240L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Up(400f, 1200f, 250L, fire(leftLong, SwipeAction.Home)),
            ),
        ),
        Row(
            "a long swipe released inside the trigger reverts",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(400f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Up(20f, 1200f, 90L, TrackerAction.Reverted),
            ),
        ),
        Row(
            "a DOWN then UP past the long line runs the long action",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Up(400f, 1200f, 80L, fire(leftLong, SwipeAction.Home)),
            ),
        ),
        Row(
            "a DOWN then UP under the long line runs the short action, even with a long slot",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Up(200f, 1200f, 80L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "with only a long slot a long swipe runs it",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None, leftLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.None)),
                Step.Move(400f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.LONG)),
                Step.Up(400f, 1200f, 100L, fire(leftLong, SwipeAction.Screenshot)),
            ),
        ),
        Row(
            "with only a long slot a long swipe pulled back under the line goes to the app",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None, leftLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(400f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Screenshot)),
                Step.Move(200f, 1200f, 90L, TrackerAction.Pending(SwipeAction.None, SwipeKind.SHORT)),
                Step.Up(200f, 1200f, 100L, slotEmpty),
            ),
        ),
        Row(
            "with only a long slot a DOWN then UP under the line goes to the app",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None, leftLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Up(200f, 1200f, 80L, slotEmpty),
            ),
        ),
        Row(
            "a trigger as far as the long distance puts the long line 24 dp past the trigger",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                // Trigger 150 dp = 412.5 px; the long line is 174 dp = 478.5 px, from x 5 at x 483.5.
                Step.Down(5f, 1200f, 0L),
                Step.Move(450f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(480f, 1200f, 80L, TrackerAction.None),
                Step.Move(490f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Up(490f, 1200f, 100L, fire(leftLong, SwipeAction.Home)),
            ),
            tune = { it.copy(sideMinimumSwipeDistanceDp = 150f, longSwipeDp = 120f) },
        ),
        Row(
            "a far swipe distance set further moves the long line with it",
            ScreenEdge.LEFT,
            mapOf(leftLong to SwipeAction.Home),
            listOf(
                // 200 dp = 550 px, from x 5 at x 555.
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(550f, 1200f, 80L, TrackerAction.None),
                Step.Move(560f, 1200f, 90L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.LONG)),
                Step.Up(560f, 1200f, 100L, fire(leftLong, SwipeAction.Home)),
            ),
            tune = { it.copy(longSwipeDp = 200f) },
        ),

        // Halves. The default screen is 2400 px tall, so the strip's centre is y 1200.
        Row(
            "on a split edge a DOWN just above the centre reads the top half",
            ScreenEdge.LEFT,
            mapOf(topShort to SwipeAction.Home, lowerShort to SwipeAction.Recents),
            listOf(
                Step.Down(5f, 1199f, 0L),
                Step.Move(105f, 1199f, 60L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Up(105f, 1199f, 80L, fire(topShort, SwipeAction.Home)),
            ),
            split = true,
        ),
        Row(
            "on a split edge a DOWN at the centre reads the bottom half",
            ScreenEdge.LEFT,
            mapOf(topShort to SwipeAction.Home, lowerShort to SwipeAction.Recents),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Recents)),
                Step.Up(105f, 1200f, 80L, fire(lowerShort, SwipeAction.Recents)),
            ),
            split = true,
        ),
        Row(
            "on a split edge a DOWN just below the centre reads the bottom half",
            ScreenEdge.LEFT,
            mapOf(topShort to SwipeAction.Home, lowerShort to SwipeAction.Recents),
            listOf(
                Step.Down(5f, 1201f, 0L),
                Step.Up(105f, 1201f, 80L, fire(lowerShort, SwipeAction.Recents)),
            ),
            split = true,
        ),
        Row(
            "the half is read at DOWN, so a swipe that drifts across the centre keeps it",
            ScreenEdge.LEFT,
            mapOf(topShort to SwipeAction.Home, lowerShort to SwipeAction.Recents),
            listOf(
                Step.Down(5f, 1190f, 0L),
                Step.Move(105f, 1230f, 60L, TrackerAction.Armed(null, SwipeAction.Home)),
                Step.Up(110f, 1240f, 80L, fire(topShort, SwipeAction.Home)),
            ),
            split = true,
        ),
        Row(
            "each half has its own long swipe",
            ScreenEdge.LEFT,
            mapOf(topLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 600f, 0L),
                Step.Move(105f, 600f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(400f, 600f, 90L, TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.LONG)),
                Step.Up(400f, 600f, 100L, fire(topLong, SwipeAction.Screenshot)),
            ),
            split = true,
        ),
        Row(
            "the other half's long slot does not reach this half",
            ScreenEdge.LEFT,
            mapOf(topLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1800f, 0L),
                Step.Move(105f, 1800f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(400f, 1800f, 90L, TrackerAction.None),
                Step.Up(400f, 1800f, 100L, fire(lowerShort, SwipeAction.Back)),
            ),
            split = true,
        ),
        Row(
            "an edge that is not split reads the whole edge, even when its halves hold actions",
            ScreenEdge.LEFT,
            mapOf(topShort to SwipeAction.Home, lowerShort to SwipeAction.Recents, topLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 600f, 0L),
                Step.Move(105f, 600f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(400f, 600f, 90L, TrackerAction.None),
                Step.Up(400f, 600f, 100L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "a split half with nothing set never arms, and its swipe goes to the app",
            ScreenEdge.LEFT,
            mapOf(topShort to SwipeAction.None),
            listOf(
                Step.Down(5f, 600f, 0L),
                Step.Move(105f, 600f, 60L, TrackerAction.None),
                Step.Up(105f, 600f, 80L, slotEmpty),
            ),
            split = true,
        ),

        // Swipe and wait. The wait time is 350 ms and the touch slop 8 dp = 22 px; the still
        // clock runs from the sample that armed, here the MOVE at 60 ms.
        Row(
            "still past the trigger for the wait time, the wait action is pending and the lift runs it",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(200L, poll),
                Step.Hold(409L, poll),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Hold(430L, TrackerAction.None),
                Step.Up(105f, 1200f, 500L, fire(leftWait, SwipeAction.Home)),
            ),
        ),
        Row(
            "a lift before the wait time runs the short action",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(300L, poll),
                Step.Up(105f, 1200f, 400L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "jitter inside the touch slop does not restart the still clock",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Move(120f, 1210f, 200L, TrackerAction.None),
                Step.Move(95f, 1195f, 300L, TrackerAction.None),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Move(110f, 1205f, 420L, TrackerAction.None),
                Step.Up(100f, 1200f, 450L, fire(leftWait, SwipeAction.Home)),
            ),
        ),
        Row(
            "a move past the slop restarts the still clock before the wait is reached",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Move(200f, 1200f, 300L, TrackerAction.None),
                Step.Hold(410L, poll),
                Step.Hold(649L, poll),
                Step.Hold(650L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Up(200f, 1200f, 660L, fire(leftWait, SwipeAction.Home)),
            ),
        ),
        Row(
            "moving on past the slop after the wait returns to the short action, and a new stillness waits again",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Move(200f, 1200f, 450L, TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT, frame)),
                Step.Move(210f, 1200f, 500L, TrackerAction.None),
                Step.Hold(700L, poll),
                Step.Hold(800L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Up(210f, 1200f, 820L, fire(leftWait, SwipeAction.Home)),
            ),
        ),
        Row(
            "moving on after the wait and lifted at once runs the short action",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Move(200f, 1200f, 450L, TrackerAction.Pending(SwipeAction.Back, SwipeKind.SHORT, frame)),
                Step.Up(200f, 1200f, 470L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "moving on past the long line after the wait returns to the long action",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home, leftLong to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Move(400f, 1200f, 450L, TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.LONG, frame)),
                Step.Up(400f, 1200f, 470L, fire(leftLong, SwipeAction.Screenshot)),
            ),
        ),
        Row(
            "a lift that itself moves past the slop after the wait runs the band at the release point",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Up(200f, 1200f, 450L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "a retract while waiting stops the poll, and the lift reverts",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(200L, poll),
                Step.Move(20f, 1200f, 250L, TrackerAction.Disarmed),
                Step.Hold(700L, TrackerAction.None),
                Step.Up(20f, 1200f, 720L, TrackerAction.Reverted),
            ),
        ),
        Row(
            "a retract after the wait is reached disarms it, and the lift reverts",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Move(20f, 1200f, 450L, TrackerAction.Disarmed),
                Step.Hold(900L, TrackerAction.None),
                Step.Up(20f, 1200f, 920L, TrackerAction.Reverted),
            ),
        ),
        Row(
            "a re-arm starts the still clock again, from the re-arm",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Move(20f, 1200f, 200L, TrackerAction.Disarmed),
                Step.Move(110f, 1200f, 300L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(500L, poll),
                Step.Hold(650L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Up(110f, 1200f, 670L, fire(leftWait, SwipeAction.Home)),
            ),
        ),
        Row(
            "with the wait slot empty a side arm asks for no poll, and a still finger stays Back",
            ScreenEdge.LEFT,
            emptyMap(),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Hold(1000L, TrackerAction.None),
                Step.Up(105f, 1200f, 1100L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "with the wait slot empty a re-arm asks for no poll either",
            ScreenEdge.RIGHT,
            emptyMap(),
            listOf(
                Step.Down(1075f, 1200f, 0L),
                Step.Move(975f, 1200f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Move(1060f, 1200f, 90L, TrackerAction.Disarmed),
                Step.Move(975f, 1200f, 120L, TrackerAction.Armed(null, SwipeAction.Back)),
            ),
        ),
        Row(
            "on a split edge the whole edge's wait slot is out of reach, so nothing polls",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 600f, 0L),
                Step.Move(105f, 600f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Hold(1000L, TrackerAction.None),
                Step.Up(105f, 600f, 1100L, fire(topShort, SwipeAction.Back)),
            ),
            split = true,
        ),
        Row(
            "a DOWN then UP never runs the wait action, however long it took",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Up(105f, 1200f, 900L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
        Row(
            "with only a wait slot the arm has nothing pending, and a quick swipe goes to the app",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None, leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.None)),
                Step.Up(105f, 1200f, 100L, slotEmpty),
            ),
        ),
        Row(
            "with only a wait slot a waited swipe runs it",
            ScreenEdge.LEFT,
            mapOf(leftShort to SwipeAction.None, leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L, TrackerAction.Armed(frame, SwipeAction.None)),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Up(105f, 1200f, 450L, fire(leftWait, SwipeAction.Home)),
            ),
        ),
        Row(
            "a wait in a half runs that half's wait slot",
            ScreenEdge.LEFT,
            mapOf(topWait to SwipeAction.Home, lowerWait to SwipeAction.Screenshot),
            listOf(
                Step.Down(5f, 1800f, 0L),
                Step.Move(105f, 1800f, 60L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Screenshot, SwipeKind.WAIT)),
                Step.Up(105f, 1800f, 450L, fire(lowerWait, SwipeAction.Screenshot)),
            ),
            split = true,
        ),
        Row(
            "the other half's wait slot does not reach this half, so nothing polls",
            ScreenEdge.LEFT,
            mapOf(topWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1800f, 0L),
                Step.Move(105f, 1800f, 60L, TrackerAction.Armed(null, SwipeAction.Back)),
                Step.Hold(1000L, TrackerAction.None),
                Step.Up(105f, 1800f, 1100L, fire(lowerShort, SwipeAction.Back)),
            ),
            split = true,
        ),
        Row(
            "a wait set further needs that much stillness",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L),
                Step.Hold(410L, poll),
                Step.Hold(860L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
            ),
            tune = { it.copy(waitMs = 800L) },
        ),
        Row(
            "the next touch on the same tracker starts with no wait left over",
            ScreenEdge.LEFT,
            mapOf(leftWait to SwipeAction.Home),
            listOf(
                Step.Down(5f, 1200f, 0L),
                Step.Move(105f, 1200f, 60L),
                Step.Hold(410L, TrackerAction.Pending(SwipeAction.Home, SwipeKind.WAIT)),
                Step.Up(105f, 1200f, 450L, fire(leftWait, SwipeAction.Home)),
                Step.Down(5f, 1200f, 1000L),
                Step.Up(105f, 1200f, 1080L, fire(leftShort, SwipeAction.Back)),
                Step.Down(5f, 1200f, 2000L),
                Step.Move(105f, 1200f, 2060L, TrackerAction.Armed(frame, SwipeAction.Back)),
                Step.Hold(2100L, poll),
                Step.Up(105f, 1200f, 2120L, fire(leftShort, SwipeAction.Back)),
            ),
        ),
    )

    @Test fun `every swipe rule holds`() {
        rows.forEach { row ->
            val slots = row.slots.entries.fold(SwipeSlots(leftSplit = row.split, rightSplit = row.split)) { all, (slot, action) ->
                all.with(slot, action)
            }
            val tracker = GestureTracker(
                row.edge,
                row.tune(GestureConfiguration(edgeAppListEnabled = false, slots = slots)),
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
