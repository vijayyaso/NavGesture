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

import com.vijayyaso.navgesture.gesture.TouchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTouchZoneTest {

    private val zone = DemoTouchZone(widthPx = 300f, heightPx = 633f, marginPx = 24f)

    @Test fun `a touch on the drawing starts a try where it is`() {
        assertTrue(zone.accepts(150f, 300f))
        assertEquals(150f to 300f, zone.onDrawing(150f, 300f))
    }

    @Test fun `a touch just left of the drawing counts as its left edge`() {
        assertTrue(zone.accepts(-20f, 300f))
        assertEquals(0f to 300f, zone.onDrawing(-20f, 300f))
    }

    @Test fun `a touch just right of the drawing counts as its right edge`() {
        assertTrue(zone.accepts(320f, 300f))
        assertEquals(300f to 300f, zone.onDrawing(320f, 300f))
    }

    @Test fun `a touch just below the drawing counts as its bottom edge`() {
        assertTrue(zone.accepts(150f, 650f))
        assertEquals(150f to 633f, zone.onDrawing(150f, 650f))
    }

    @Test fun `a touch below a corner lands on the corner`() {
        assertTrue(zone.accepts(-10f, 650f))
        assertEquals(0f to 633f, zone.onDrawing(-10f, 650f))
    }

    @Test fun `a touch past the margin is not a try`() {
        assertFalse(zone.accepts(-25f, 300f))
        assertFalse(zone.accepts(325f, 300f))
        assertFalse(zone.accepts(150f, 658f))
    }

    @Test fun `the top has no margin`() {
        assertFalse(zone.accepts(150f, -1f))
    }

    @Test fun `a touch in the margin lands inside the engine's start band`() {
        val engine = DemoEngine(
            widthPx = 300f,
            heightPx = 633f,
            screenHeightPx = 2400f,
            density = 3f,
            configuration = DemoConfiguration.forStep(DemoStep.BACK, 100f),
        )
        val (x, y) = zone.onDrawing(-20f, 300f)
        val events = engine.down(TouchSample(x, y, 0L))
        assertTrue(events.none { it is DemoEvent.Missed })
    }
}
