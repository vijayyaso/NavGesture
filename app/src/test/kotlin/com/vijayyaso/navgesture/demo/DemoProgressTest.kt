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
import com.vijayyaso.navgesture.gesture.GestureAction
import com.vijayyaso.navgesture.gesture.RejectionReason
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoProgressTest {

    private fun ran(slot: SlotId, action: SwipeAction) = DemoResult.Ran(GestureAction.Run(slot, action))

    private fun edge(edge: ScreenEdge, kind: SwipeKind) = SlotId.Edge(edge, SwipeZone.WHOLE, kind)

    private val back = ran(edge(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.Back)
    private val home = ran(edge(ScreenEdge.BOTTOM, SwipeKind.SHORT), SwipeAction.Home)
    private val recents = ran(edge(ScreenEdge.BOTTOM, SwipeKind.PAUSE), SwipeAction.Recents)
    private val corner = ran(SlotId.Corner(BottomCorner.RIGHT), SwipeAction.Notifications)
    private val wait = ran(edge(ScreenEdge.RIGHT, SwipeKind.WAIT), SwipeAction.Screenshot)
    private val torch = ran(edge(ScreenEdge.LEFT, SwipeKind.LONG), SwipeAction.Torch)

    private val miss = DemoEvent.Missed(DemoMiss.Rejected(RejectionReason.INSUFFICIENT_MOVEMENT))

    @Test fun `the demo starts at step 1 with no tries`() {
        val start = DemoProgress()
        assertEquals(DemoStep.BACK, start.step)
        assertEquals(0, start.tries)
        assertNull(start.hint)
        assertFalse(start.finished)
        assertFalse(start.canSkipStep)
    }

    @Test fun `each step passes on its own gesture alone`() {
        val passing = mapOf(
            DemoStep.BACK to back,
            DemoStep.HOME to home,
            DemoStep.RECENTS to recents,
            DemoStep.APP_LIST to DemoResult.AppPicked(2),
            DemoStep.CORNER to corner,
            DemoStep.WAIT to wait,
            DemoStep.LONG to torch,
        )
        DemoStep.entries.forEach { step ->
            passing.forEach { (owner, result) ->
                assertEquals("$result in $step", owner == step, step.passes(result))
            }
            assertFalse(step.passes(DemoResult.FavouritesOpened))
        }
    }

    @Test fun `a pass celebrates without counting a try`() {
        val progress = DemoProgress().after(DemoEvent.Performed(back))
        assertEquals(back, progress.celebrating)
        assertEquals(0, progress.tries)
        assertEquals(DemoStep.BACK, progress.step)
    }

    @Test fun `while a pass celebrates, nothing more counts`() {
        val celebrating = DemoProgress().after(DemoEvent.Performed(back))
        assertEquals(celebrating, celebrating.after(miss))
        assertEquals(celebrating, celebrating.after(DemoEvent.Performed(home)))
    }

    @Test fun `another gesture counts a try and names that gesture`() {
        val progress = DemoProgress(step = DemoStep.RECENTS).after(DemoEvent.Performed(home))
        assertEquals(1, progress.tries)
        assertEquals(DemoHint.Other(home), progress.hint)
        assertNull(progress.celebrating)
    }

    @Test fun `a miss counts a try and keeps its reason`() {
        val progress = DemoProgress().after(miss)
        assertEquals(1, progress.tries)
        assertEquals(DemoHint.Miss(miss.reason), progress.hint)
    }

    @Test fun `what a finger is doing changes nothing`() {
        val start = DemoProgress()
        listOf(
            DemoEvent.Ready(SwipeAction.Back),
            DemoEvent.Unready,
            DemoEvent.ListOpened(recents = true),
            DemoEvent.ListMoved(1),
        ).forEach { assertEquals(start, start.after(it)) }
    }

    @Test fun `two tries that do not pass offer to skip the step`() {
        val once = DemoProgress(step = DemoStep.RECENTS).after(DemoEvent.Performed(home))
        assertFalse(once.canSkipStep)
        val twice = once.after(miss)
        assertTrue(twice.canSkipStep)
        val skipped = twice.advance()
        assertEquals(DemoStep.APP_LIST, skipped.step)
        assertEquals(0, skipped.tries)
        assertNull(skipped.hint)
        assertFalse(skipped.canSkipStep)
    }

    @Test fun `advance goes through every step in order, then finishes`() {
        var progress = DemoProgress()
        val seen = mutableListOf<DemoStep>()
        while (!progress.finished) {
            seen += checkNotNull(progress.step)
            progress = progress.advance()
        }
        assertEquals(DemoStep.entries, seen)
        assertEquals((1..7).toList(), seen.map { it.number })
        assertNull(progress.celebrating)
        assertEquals(progress, progress.advance())
    }

    @Test fun `a finished demo ignores every event`() {
        val finished = DemoProgress(step = null)
        assertTrue(finished.finished)
        assertEquals(finished, finished.after(DemoEvent.Performed(torch)))
        assertEquals(finished, finished.after(miss))
    }

    @Test fun `a plan goes through only its own steps, then finishes`() {
        val plan = listOf(DemoStep.BACK, DemoStep.HOME, DemoStep.LONG)
        val start = DemoProgress.start(plan)
        assertEquals(DemoStep.BACK, start.step)
        assertEquals(plan, start.plan)
        val home = start.advance()
        assertEquals(DemoStep.HOME, home.step)
        val long = home.advance()
        assertEquals(DemoStep.LONG, long.step)
        assertEquals(plan, long.plan)
        val done = long.advance()
        assertTrue(done.finished)
        assertEquals(done, done.advance())
    }

    @Test fun `a plan that skips the first steps starts at its own first step`() {
        val start = DemoProgress.start(listOf(DemoStep.RECENTS, DemoStep.WAIT))
        assertEquals(DemoStep.RECENTS, start.step)
        assertEquals(DemoStep.WAIT, start.advance().step)
        assertTrue(DemoProgress.start(emptyList()).finished)
    }

    @Test fun `skipping a step after two tries moves on in the plan`() {
        val plan = listOf(DemoStep.HOME, DemoStep.CORNER)
        val twice = DemoProgress.start(plan).after(miss).after(miss)
        assertTrue(twice.canSkipStep)
        val skipped = twice.advance()
        assertEquals(DemoStep.CORNER, skipped.step)
        assertEquals(0, skipped.tries)
        assertEquals(plan, skipped.plan)
    }

    @Test fun `a new plan keeps the step it still has, or moves to the next one it has`() {
        val atHome = DemoProgress.start(listOf(DemoStep.BACK, DemoStep.HOME, DemoStep.LONG)).advance().after(miss)
        val kept = atHome.withPlan(listOf(DemoStep.HOME, DemoStep.WAIT))
        assertEquals(DemoStep.HOME, kept.step)
        assertEquals(1, kept.tries)
        assertEquals(DemoStep.WAIT, kept.advance().step)
        val moved = atHome.withPlan(listOf(DemoStep.BACK, DemoStep.CORNER, DemoStep.LONG))
        assertEquals(DemoStep.CORNER, moved.step)
        assertEquals(0, moved.tries)
        assertTrue(atHome.withPlan(listOf(DemoStep.BACK)).finished)
        assertTrue(DemoProgress(step = null).withPlan(listOf(DemoStep.BACK)).finished)
    }

    @Test fun `a step the plan no longer has advances to the next step the plan has`() {
        val progress = DemoProgress(step = DemoStep.RECENTS, plan = listOf(DemoStep.BACK, DemoStep.WAIT))
        assertEquals(DemoStep.WAIT, progress.advance().step)
    }

    @Test fun `steps match the slot, not the action`() {
        val leftShortApp = ran(edge(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.OpenApp("com.example"))
        val rightShortHome = ran(SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.TOP_HALF, SwipeKind.SHORT), SwipeAction.Home)
        assertTrue(DemoStep.BACK.passes(leftShortApp))
        assertTrue(DemoStep.BACK.passes(rightShortHome))
        val longScreenshot = ran(edge(ScreenEdge.RIGHT, SwipeKind.LONG), SwipeAction.Screenshot)
        assertTrue(DemoStep.LONG.passes(longScreenshot))
        assertFalse(DemoStep.LONG.passes(back))
        assertFalse(DemoStep.BACK.passes(longScreenshot))
        val bottomShortBack = ran(edge(ScreenEdge.BOTTOM, SwipeKind.SHORT), SwipeAction.Back)
        assertTrue(DemoStep.HOME.passes(bottomShortBack))
        assertFalse(DemoStep.BACK.passes(bottomShortBack))
        val bottomPauseTorch = ran(edge(ScreenEdge.BOTTOM, SwipeKind.PAUSE), SwipeAction.Torch)
        assertTrue(DemoStep.RECENTS.passes(bottomPauseTorch))
        assertFalse(DemoStep.HOME.passes(bottomPauseTorch))
        val waitOnTopHalf = ran(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.WAIT), SwipeAction.Back)
        assertTrue(DemoStep.WAIT.passes(waitOnTopHalf))
        assertFalse(DemoStep.BACK.passes(waitOnTopHalf))
    }

    @Test fun `Circle to Search passes no step`() {
        DemoStep.entries.forEach { assertFalse(it.passes(DemoResult.CircleToSearch)) }
    }

    @Test fun `the step order is fixed, as saved progress stores it by ordinal`() {
        assertEquals(
            listOf("BACK", "HOME", "RECENTS", "APP_LIST", "CORNER", "WAIT", "LONG"),
            DemoStep.entries.map { it.name },
        )
    }
}
