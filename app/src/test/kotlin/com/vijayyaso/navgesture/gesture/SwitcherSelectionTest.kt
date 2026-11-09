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
    fun `step zero is the start index`() {
        assertEquals(1, SwitcherSelection.indexFor(0, 5, 1))
        assertEquals(0, SwitcherSelection.indexFor(0, 5, 0))
    }

    @Test
    fun `past the last app comes the first`() {
        // 4 selectable apps (1..4) with the dimmed current app at 0.
        assertEquals(4, SwitcherSelection.indexFor(3, 5, 1))
        assertEquals(1, SwitcherSelection.indexFor(4, 5, 1))
        assertEquals(2, SwitcherSelection.indexFor(5, 5, 1))
    }

    @Test
    fun `before the first app comes the last`() {
        assertEquals(4, SwitcherSelection.indexFor(-1, 5, 1))
        assertEquals(3, SwitcherSelection.indexFor(-2, 5, 1))
    }

    @Test
    fun `favourites loop over the whole list`() {
        assertEquals(0, SwitcherSelection.indexFor(3, 3, 0))
        assertEquals(2, SwitcherSelection.indexFor(-1, 3, 0))
    }

    @Test
    fun `an empty list selects nothing`() {
        assertNull(SwitcherSelection.indexFor(2, 0, 0))
    }

    @Test
    fun `a one-app recents list cannot select itself`() {
        assertNull(SwitcherSelection.indexFor(2, 1, 1))
        assertNull(SwitcherSelection.indexFor(2, 3, 4))
    }

    @Test
    fun `the grid wraps into rows of the columns that fit`() {
        // 360 dp wide, 72 dp cells: 5 columns.
        assertEquals(SwitcherSelection.Grid(5, 2), SwitcherSelection.tapGrid(8, 360f, 72f))
        assertEquals(SwitcherSelection.Grid(3, 1), SwitcherSelection.tapGrid(3, 360f, 72f))
    }

    @Test
    fun `the grid stops at three rows`() {
        val grid = SwitcherSelection.tapGrid(20, 360f, 72f)
        assertEquals(SwitcherSelection.Grid(5, 3), grid)
        assertEquals(15, grid.capacity)
    }

    @Test
    fun `an empty grid has no cells`() {
        assertEquals(0, SwitcherSelection.tapGrid(0, 360f, 72f).capacity)
    }
}
