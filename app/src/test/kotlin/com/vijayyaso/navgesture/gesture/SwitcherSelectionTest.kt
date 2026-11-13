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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitcherSelectionTest {
    private val step = 72f

    @Test
    fun `no slide stays on step zero`() {
        assertEquals(0, SwitcherSelection.steps(0f, step, 0))
        assertEquals(0, SwitcherSelection.steps(10f, step, 0))
    }

    @Test
    fun `a full step moves one app`() {
        assertEquals(1, SwitcherSelection.steps(step, step, 0))
        assertEquals(2, SwitcherSelection.steps(step * 2, step, 1))
        assertEquals(-1, SwitcherSelection.steps(-step, step, 0))
    }

    @Test
    fun `a finger just past the half-way line does not move yet`() {
        // 0.6 of a step: past the half-way line, inside the hysteresis band.
        assertEquals(0, SwitcherSelection.steps(step * 0.6f, step, 0))
        assertEquals(1, SwitcherSelection.steps(step * 0.8f, step, 0))
    }

    @Test
    fun `jitter around a boundary keeps the selection`() {
        var current = SwitcherSelection.steps(step * 0.8f, step, 0)
        assertEquals(1, current)
        for (offset in listOf(0.45f, 0.55f, 0.4f, 0.6f, 0.3f)) {
            current = SwitcherSelection.steps(step * offset, step, current)
            assertEquals("offset $offset", 1, current)
        }
        // Clearly back: a quarter step below the half-way line.
        assertEquals(0, SwitcherSelection.steps(step * 0.2f, step, current))
    }

    @Test
    fun `a big jump in one event lands on the nearest step`() {
        assertEquals(4, SwitcherSelection.steps(step * 4.1f, step, 0))
    }

    @Test
    fun `a zero step keeps the previous count`() {
        assertEquals(3, SwitcherSelection.steps(500f, 0f, 3))
    }

    @Test
    fun `step zero is the first app`() {
        assertEquals(0, SwitcherSelection.indexFor(0, 5))
    }

    @Test
    fun `past the last app comes the first`() {
        assertEquals(4, SwitcherSelection.indexFor(4, 5))
        assertEquals(0, SwitcherSelection.indexFor(5, 5))
        assertEquals(1, SwitcherSelection.indexFor(6, 5))
    }

    @Test
    fun `before the first app comes the last`() {
        assertEquals(4, SwitcherSelection.indexFor(-1, 5))
        assertEquals(3, SwitcherSelection.indexFor(-2, 5))
    }

    @Test
    fun `an empty list selects nothing`() {
        assertNull(SwitcherSelection.indexFor(2, 0))
    }

    @Test
    fun `a one-app list always selects that app`() {
        assertEquals(0, SwitcherSelection.indexFor(3, 1))
        assertEquals(0, SwitcherSelection.indexFor(-2, 1))
    }

    @Test
    fun `the grid wraps into rows of the columns that fit`() {
        // 360 dp wide, 72 dp cells: 5 columns.
        assertEquals(SwitcherSelection.Grid(5, 2), SwitcherSelection.tapGrid(8, 360f, 72f, 6))
        assertEquals(SwitcherSelection.Grid(3, 1), SwitcherSelection.tapGrid(3, 360f, 72f, 6))
    }

    @Test
    fun `the grid stops at the rows that fit`() {
        val grid = SwitcherSelection.tapGrid(20, 360f, 72f, 3)
        assertEquals(SwitcherSelection.Grid(5, 3), grid)
        assertEquals(15, grid.capacity)
        assertEquals(SwitcherSelection.Grid(5, 4), SwitcherSelection.tapGrid(20, 360f, 72f, 6))
    }

    @Test
    fun `an empty grid has no cells`() {
        assertEquals(0, SwitcherSelection.tapGrid(0, 360f, 72f, 6).capacity)
    }

    @Test
    fun `a tall phone gets more rows, up to the cap`() {
        // 873 dp tall (the owner's phone), 40 dp under the panel: 6 rows, the cap.
        assertEquals(SwitcherSelection.MAX_GRID_ROWS, SwitcherSelection.gridRowsThatFit(873f, 40f))
        // A 640 dp phone: 6 rows of 84 dp fit under the status bar.
        assertEquals(6, SwitcherSelection.gridRowsThatFit(640f, 52f))
        assertEquals(6, SwitcherSelection.MAX_GRID_ROWS)
    }

    @Test
    fun `a short landscape screen keeps the three rows it had`() {
        // 360 dp tall, with a gesture bar under the strip.
        assertEquals(3, SwitcherSelection.gridRowsThatFit(360f, 52f))
        assertEquals(3, SwitcherSelection.gridRowsThatFit(360f, 40f))
    }

    @Test
    fun `a screen too short for a row still shows one`() {
        assertEquals(1, SwitcherSelection.gridRowsThatFit(120f, 40f))
    }

    @Test
    fun `every favourite fits the grid on a 360 by 640 dp phone both ways up`() {
        val max = FavouriteList.MAX_FAVOURITES
        // The panel keeps 14 dp each side; 52 dp under it is a 20 dp strip, a 24 dp gesture
        // bar and an 8 dp gap, the most a phone puts there.
        fun capacity(widthDp: Float, heightDp: Float) = SwitcherSelection.tapGrid(
            max,
            widthDp - 14f * 2,
            SwitcherSelection.GRID_CELL_W_DP,
            SwitcherSelection.gridRowsThatFit(heightDp, 52f),
        ).capacity
        assertTrue(capacity(360f, 640f) >= max)
        assertTrue(capacity(640f, 360f) >= max)
    }

    @Test
    fun `a list still loading or empty shows a message in every style`() {
        for (style in AppListStyle.entries) for (touch in listOf(true, false)) {
            assertEquals(SwitcherView.Loading, SwitcherView.of(size = null, touchMode = touch, style, steps = 2))
            assertEquals(SwitcherView.Empty, SwitcherView.of(size = 0, touchMode = touch, style, steps = 2))
        }
    }

    @Test
    fun `touch mode and the Tap style show the grid`() {
        for (style in AppListStyle.entries) {
            assertEquals(SwitcherView.Grid, SwitcherView.of(size = 5, touchMode = true, style, steps = 0))
        }
        assertEquals(SwitcherView.Grid, SwitcherView.of(size = 5, touchMode = false, AppListStyle.TAP, steps = 3))
    }

    @Test
    fun `a held drag shows the row on the step a lift would open`() {
        assertEquals(SwitcherView.Row(2), SwitcherView.of(size = 5, touchMode = false, AppListStyle.DRAG, steps = 2))
        // Both has no pick at step 0, so its second step is the row's first.
        assertEquals(SwitcherView.Row(1), SwitcherView.of(size = 5, touchMode = false, AppListStyle.BOTH, steps = 2))
        assertEquals(SwitcherView.Row(0), SwitcherView.of(size = 5, touchMode = false, AppListStyle.BOTH, steps = 0))
        assertEquals(SwitcherView.Row(-1), SwitcherView.of(size = 5, touchMode = false, AppListStyle.BOTH, steps = -1))
    }
}
