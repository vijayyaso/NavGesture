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
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.GestureTracker
import com.vijayyaso.navgesture.gesture.RejectionReason
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.gesture.TrackerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every case runs on a 300 x 633 dp and a 200 x 422 dp drawing, at density 3, on a phone whose
 * real display is 800 dp tall. Paths are written in dp from the drawing's top-left corner.
 */
class DemoEngineTest {

    private class Drawing(val widthDp: Float, val heightDp: Float) {
        val midY: Float get() = heightDp / 2f
        val centreX: Float get() = widthDp / 2f
        val bottomY: Float get() = heightDp - 10f
        override fun toString(): String = "${widthDp.toInt()} dp drawing"
    }

    private val drawings = listOf(Drawing(300f, 633f), Drawing(200f, 422f))

    /**
     * One finger's path: DOWN at the start, a MOVE every frame along each leg, and the UP where
     * the path ends, at [upAtMs]. A hold adds time with no MOVE, as a still finger does.
     */
    private class Stroke(xDp: Float, yDp: Float) {
        val down = TouchSample(xDp * DENSITY, yDp * DENSITY, START_MS)
        val moves = mutableListOf<TouchSample>()
        var upAtMs = START_MS
            private set
        private var x = xDp
        private var y = yDp

        fun to(xDp: Float, yDp: Float, durationMs: Long): Stroke {
            val frames = (durationMs / FRAME_MS).coerceAtLeast(1)
            val fromX = x
            val fromY = y
            for (frame in 1..frames) {
                val share = frame.toFloat() / frames
                x = fromX + (xDp - fromX) * share
                y = fromY + (yDp - fromY) * share
                upAtMs += durationMs / frames
                moves += TouchSample(x * DENSITY, y * DENSITY, upAtMs)
            }
            return this
        }

        fun hold(ms: Long): Stroke {
            upAtMs += ms
            return this
        }

        val up: TouchSample get() = TouchSample(x * DENSITY, y * DENSITY, upAtMs)
    }

    private fun engine(drawing: Drawing, step: DemoStep) = DemoEngine(
        widthPx = drawing.widthDp * DENSITY,
        heightPx = drawing.heightDp * DENSITY,
        screenHeightPx = REAL_HEIGHT_DP * DENSITY,
        density = DENSITY,
        configuration = DemoConfiguration.forStep(step, drawing.widthDp),
    )

    // The screen's frame poll: one call per frame between samples while the finger is down.
    private fun DemoEngine.play(stroke: Stroke): List<DemoEvent> {
        val events = down(stroke.down).toMutableList()
        var now = stroke.down.timestampMs
        fun pollUntil(timeMs: Long) {
            while (now + FRAME_MS <= timeMs) {
                now += FRAME_MS
                events += frame(now)
            }
        }
        stroke.moves.forEach {
            pollUntil(it.timestampMs)
            events += move(it)
            now = it.timestampMs
        }
        pollUntil(stroke.upAtMs)
        events += up(stroke.up)
        return events
    }

    private fun List<DemoEvent>.results(): List<DemoEvent> =
        filter { it is DemoEvent.Performed || it is DemoEvent.Missed }

    private fun ran(slot: SlotId, action: SwipeAction): DemoEvent =
        DemoEvent.Performed(DemoResult.Ran(GestureAction.Run(slot, action)))

    private fun edgeSlot(edge: ScreenEdge, kind: SwipeKind) = SlotId.Edge(edge, SwipeZone.WHOLE, kind)

    // The seven gestures, as a user draws them on a drawing.
    private fun back(d: Drawing) = Stroke(10f, d.midY).to(70f, d.midY, 120L)

    private fun backFromRight(d: Drawing) = Stroke(d.widthDp - 10f, d.midY).to(d.widthDp - 70f, d.midY, 120L)

    private fun home(d: Drawing) = Stroke(d.centreX, d.bottomY).to(d.centreX, d.bottomY - 150f, 100L)

    private fun recents(d: Drawing) =
        Stroke(d.centreX, d.bottomY).to(d.centreX, d.bottomY - 130f, 160L).hold(300L)

    // Up past the 10 dp trigger, then right past the 40 dp sideways drag: the list opens.
    private fun openList(d: Drawing, toRight: Boolean = true): Stroke {
        val sideways = if (toRight) 45f else -45f
        return Stroke(d.centreX, d.bottomY)
            .to(d.centreX, d.bottomY - 30f, 60L)
            .to(d.centreX + sideways, d.bottomY - 30f, 450L)
    }

    private fun corner(d: Drawing) = Stroke(12f, d.heightDp - 8f).to(72f, d.heightDp - 68f, 100L)

    private fun swipeAndWait(d: Drawing) = Stroke(10f, d.midY).to(70f, d.midY, 100L).hold(450L)

    private fun longSwipe(d: Drawing): Stroke {
        val travel = DemoConfiguration.longLineDp(d.widthDp) + 20f
        return Stroke(10f, d.midY).to(10f + travel, d.midY, 150L)
    }

    @Test fun `each gesture passes its own step on both drawings`() {
        drawings.forEach { d ->
            val cases = listOf(
                Triple(DemoStep.BACK, back(d), ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.Back)),
                Triple(DemoStep.HOME, home(d), ran(edgeSlot(ScreenEdge.BOTTOM, SwipeKind.SHORT), SwipeAction.Home)),
                Triple(
                    DemoStep.RECENTS,
                    recents(d),
                    ran(edgeSlot(ScreenEdge.BOTTOM, SwipeKind.PAUSE), SwipeAction.Recents),
                ),
                Triple(
                    DemoStep.CORNER,
                    corner(d),
                    ran(SlotId.Corner(BottomCorner.LEFT), SwipeAction.Notifications),
                ),
                Triple(
                    DemoStep.WAIT,
                    swipeAndWait(d),
                    ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.WAIT), SwipeAction.Screenshot),
                ),
                Triple(DemoStep.LONG, longSwipe(d), ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.LONG), SwipeAction.Torch)),
            )
            cases.forEach { (step, stroke, expected) ->
                val results = engine(d, step).play(stroke).results()
                assertEquals("$step on the $d", listOf(expected), results)
                val passed = (results.single() as DemoEvent.Performed).result
                assertTrue("$step on the $d passes", step.passes(passed))
            }
            val list = engine(d, DemoStep.APP_LIST).play(openList(d)).results()
            val picked = (list.single() as DemoEvent.Performed).result
            assertTrue("the app list on the $d gives $picked", DemoStep.APP_LIST.passes(picked))
        }
    }

    @Test fun `the right edge is Back too`() {
        drawings.forEach { d ->
            assertEquals(
                listOf(ran(edgeSlot(ScreenEdge.RIGHT, SwipeKind.SHORT), SwipeAction.Back)),
                engine(d, DemoStep.BACK).play(backFromRight(d)).results(),
            )
        }
    }

    @Test fun `a release names the action it would run before it runs`() {
        val d = drawings.first()
        val events = engine(d, DemoStep.WAIT).play(swipeAndWait(d))
        assertEquals(
            listOf(DemoEvent.Ready(SwipeAction.Back), DemoEvent.Ready(SwipeAction.Screenshot)),
            events.filterIsInstance<DemoEvent.Ready>(),
        )
    }

    /** The service's own strip, built as `EdgeTouchOverlay.addStrip` builds it, fed by its poll rule. */
    private fun realFire(edge: ScreenEdge, stroke: Stroke, shiftXDp: Float, shiftYDp: Float): TrackerAction {
        val tracker = GestureTracker(
            edge,
            GestureConfiguration(),
            DENSITY,
            handleCenterXPx = if (edge == ScreenEdge.BOTTOM) REAL_WIDTH_DP * DENSITY / 2f else null,
            screenHeightPx = REAL_HEIGHT_DP * DENSITY,
            screenWidthPx = REAL_WIDTH_DP * DENSITY,
        )
        fun shifted(s: TouchSample) = TouchSample(s.xPx + shiftXDp * DENSITY, s.yPx + shiftYDp * DENSITY, s.timestampMs)
        var pollAt: Long? = null
        fun track(action: TrackerAction, now: Long): TrackerAction {
            when (action) {
                is TrackerAction.Armed -> action.scheduleHoldMs?.let { pollAt = now + it }
                is TrackerAction.RearmHold -> pollAt = now + action.delayMs
                is TrackerAction.Pending -> action.scheduleHoldMs?.let { pollAt = now + it }
                else -> Unit
            }
            return action
        }
        var now = stroke.down.timestampMs
        track(tracker.onDown(shifted(stroke.down)), now)
        fun pollUntil(timeMs: Long) {
            while (now + FRAME_MS <= timeMs) {
                now += FRAME_MS
                val due = pollAt
                if (due != null && now >= due) {
                    pollAt = null
                    track(tracker.onHoldElapsed(now), now)
                }
            }
        }
        stroke.moves.forEach {
            pollUntil(it.timestampMs)
            track(tracker.onMove(shifted(it)), it.timestampMs)
            now = it.timestampMs
        }
        pollUntil(stroke.upAtMs)
        return tracker.onUp(shifted(stroke.up))
    }

    @Test fun `Back, Home and Recents fire as they do on the real strips`() {
        drawings.forEach { d ->
            // The drawing sits centred, with its bottom on the real bottom edge.
            val shiftX = (REAL_WIDTH_DP - d.widthDp) / 2f
            val shiftY = REAL_HEIGHT_DP - d.heightDp
            val cases = listOf(
                Triple(DemoStep.BACK, ScreenEdge.LEFT, back(d)),
                Triple(DemoStep.HOME, ScreenEdge.BOTTOM, home(d)),
                Triple(DemoStep.RECENTS, ScreenEdge.BOTTOM, recents(d)),
            )
            cases.forEach { (step, edge, stroke) ->
                val real = realFire(edge, stroke, if (edge == ScreenEdge.BOTTOM) shiftX else 0f, shiftY)
                val demo = engine(d, step).play(stroke).results().single() as DemoEvent.Performed
                assertTrue("$step on the $d fires for real: $real", real is TrackerAction.Fire)
                val fired = (real as TrackerAction.Fire).action
                assertEquals("$step on the $d", fired, (demo.result as DemoResult.Ran).run)
            }
        }
    }

    @Test fun `in step 1 a Back that pauses or travels far is still Back`() {
        val expected = listOf(ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.Back))
        drawings.forEach { d ->
            val paused = Stroke(10f, d.midY).to(70f, d.midY, 100L).hold(400L)
            assertEquals("paused on the $d", expected, engine(d, DemoStep.BACK).play(paused).results())
            val far = Stroke(10f, d.midY).to(160f, d.midY, 150L)
            assertEquals("far on the $d", expected, engine(d, DemoStep.BACK).play(far).results())
        }
    }

    @Test fun `a touch in the middle is not on an edge, and the rest of it gives nothing`() {
        drawings.forEach { d ->
            val engine = engine(d, DemoStep.BACK)
            assertEquals(
                listOf(DemoEvent.Missed(DemoMiss.NotOnEdge)),
                engine.down(TouchSample(d.centreX * DENSITY, d.midY * DENSITY, START_MS)),
            )
            assertEquals(emptyList<DemoEvent>(), engine.move(TouchSample(10f, d.midY * DENSITY, START_MS + 50)))
            assertEquals(emptyList<DemoEvent>(), engine.up(TouchSample(10f, d.midY * DENSITY, START_MS + 90)))
        }
    }

    @Test fun `a swipe slower than the arm time gives exactly one miss`() {
        drawings.forEach { d ->
            val slow = Stroke(10f, d.midY).to(40f, d.midY, 1600L)
            assertEquals(
                "on the $d",
                listOf(DemoEvent.Missed(DemoMiss.Rejected(RejectionReason.TOO_SLOW_TO_ARM))),
                engine(d, DemoStep.BACK).play(slow).results(),
            )
        }
    }

    @Test fun `a second finger gives one cancel and the lift after it gives nothing`() {
        drawings.forEach { d ->
            val engine = engine(d, DemoStep.BACK)
            engine.down(TouchSample(10f * DENSITY, d.midY * DENSITY, START_MS))
            engine.move(TouchSample(40f * DENSITY, d.midY * DENSITY, START_MS + 30))
            assertEquals(listOf(DemoEvent.Missed(DemoMiss.Cancelled)), engine.extraFinger())
            assertEquals(emptyList<DemoEvent>(), engine.extraFinger())
            val later = TouchSample(80f * DENSITY, d.midY * DENSITY, START_MS + 60)
            assertEquals(emptyList<DemoEvent>(), engine.move(later))
            assertEquals(emptyList<DemoEvent>(), engine.up(TouchSample(80f * DENSITY, d.midY * DENSITY, START_MS + 90)))
            // The next touch is judged again.
            assertEquals(
                listOf(ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.Back)),
                engine.play(back(d)).results(),
            )
        }
    }

    @Test fun `a drag to the left in step 4 opens the favourites`() {
        drawings.forEach { d ->
            val events = engine(d, DemoStep.APP_LIST).play(openList(d, toRight = false))
            assertTrue("on the $d", DemoEvent.ListOpened(recents = false) in events)
            assertEquals(listOf(DemoEvent.Performed(DemoResult.FavouritesOpened)), events.results())
        }
    }

    @Test fun `sliding back down closes the list with a cancel`() {
        drawings.forEach { d ->
            val stroke = openList(d).to(d.centreX + 45f, d.bottomY + 8f, 200L)
            assertEquals(
                "on the $d",
                listOf(DemoEvent.Missed(DemoMiss.Cancelled)),
                engine(d, DemoStep.APP_LIST).play(stroke).results(),
            )
        }
    }

    @Test fun `the app list reaches all four apps on the 200 dp drawing`() {
        val d = drawings.last()
        // Slow slides, about 100 dp a second, from where the list opened (about 140 dp across).
        val slides = listOf(0f, 50f, -50f, -110f)
        val picked = slides.map { slide ->
            val stroke = openList(d)
            if (slide != 0f) stroke.to(d.centreX + 45f + slide, d.bottomY - 30f, (kotlin.math.abs(slide) * 10).toLong())
            val result = engine(d, DemoStep.APP_LIST).play(stroke).results().single() as DemoEvent.Performed
            (result.result as DemoResult.AppPicked).index
        }
        assertEquals(setOf(0, 1, 2, 3), picked.toSet())
    }

    @Test fun `steps 1 to 4 judge with the real defaults and later steps add only their own action`() {
        listOf(DemoStep.BACK, DemoStep.HOME, DemoStep.RECENTS, DemoStep.APP_LIST).forEach {
            assertEquals(GestureConfiguration(), DemoConfiguration.forStep(it, 300f))
        }
        val corner = DemoConfiguration.forStep(DemoStep.CORNER, 300f)
        assertEquals(
            GestureConfiguration(
                leftCornerShortcut = SwipeAction.Notifications,
                rightCornerShortcut = SwipeAction.Notifications,
            ),
            corner,
        )
        val wait = DemoConfiguration.forStep(DemoStep.WAIT, 300f)
        assertEquals(SwipeAction.Screenshot, wait.actionFor(edgeSlot(ScreenEdge.LEFT, SwipeKind.WAIT)))
        assertEquals(SwipeAction.Screenshot, wait.actionFor(edgeSlot(ScreenEdge.RIGHT, SwipeKind.WAIT)))
        assertEquals(SwipeAction.None, wait.actionFor(edgeSlot(ScreenEdge.LEFT, SwipeKind.LONG)))
        assertEquals(SwipeAction.None, wait.leftCornerShortcut)
        val long = DemoConfiguration.forStep(DemoStep.LONG, 300f)
        assertEquals(SwipeAction.Torch, long.actionFor(edgeSlot(ScreenEdge.LEFT, SwipeKind.LONG)))
        assertEquals(SwipeAction.Torch, long.actionFor(edgeSlot(ScreenEdge.RIGHT, SwipeKind.LONG)))
        assertEquals(SwipeAction.None, long.actionFor(edgeSlot(ScreenEdge.LEFT, SwipeKind.WAIT)))
        assertEquals(120f, long.longSwipeDp)
        assertEquals(90f, DemoConfiguration.forStep(DemoStep.LONG, 200f).longSwipeDp)
    }

    @Test fun `a step change mid-touch waits for the lift`() {
        val d = drawings.first()
        val engine = engine(d, DemoStep.BACK)
        engine.down(TouchSample(10f * DENSITY, d.midY * DENSITY, START_MS))
        engine.configure(DemoConfiguration.forStep(DemoStep.LONG, d.widthDp))
        engine.move(TouchSample(160f * DENSITY, d.midY * DENSITY, START_MS + 100))
        // Still judged by step 1, which has no long action.
        assertEquals(
            listOf(ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.Back)),
            engine.up(TouchSample(160f * DENSITY, d.midY * DENSITY, START_MS + 120)).results(),
        )
        assertEquals(
            listOf(ran(edgeSlot(ScreenEdge.LEFT, SwipeKind.LONG), SwipeAction.Torch)),
            engine.play(longSwipe(d)).results(),
        )
    }

    private companion object {
        const val DENSITY = 3f
        const val REAL_HEIGHT_DP = 800f
        const val REAL_WIDTH_DP = 360f
        const val FRAME_MS = 16L
        const val START_MS = 10_000L
    }
}
