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
    @Test
    fun `no slide is cancel`() {
        assertNull(SwitcherSelection.selected(10f, 5, 1))
        assertNull(SwitcherSelection.selected(-10f, 5, 1))
    }

    @Test
    fun `first step selects firstIndex`() {
        assertEquals(1, SwitcherSelection.selected(24f, 5, 1))
        assertEquals(1, SwitcherSelection.selected(79f, 5, 1))
        assertEquals(2, SwitcherSelection.selected(80f, 5, 1))
        assertEquals(0, SwitcherSelection.selected(24f, 5, 0))
    }

    @Test
    fun `clamped to the last app`() {
        assertEquals(2, SwitcherSelection.selected(1000f, 3, 1))
    }

    @Test
    fun `an empty list selects nothing`() {
        assertNull(SwitcherSelection.selected(200f, 0, 0))
    }

    @Test
    fun `a one-app recents list cannot select itself`() {
        assertNull(SwitcherSelection.selected(200f, 1, 1))
        assertNull(SwitcherSelection.selected(200f, 3, 4))
    }

    @Test
    fun `window scrolls with the selector`() {
        assertEquals(2..7, SwitcherSelection.visibleWindow(7, 10))
        assertEquals(0..5, SwitcherSelection.visibleWindow(0, 10))
        assertEquals(0..2, SwitcherSelection.visibleWindow(2, 3))
        assertEquals(0..5, SwitcherSelection.visibleWindow(5, 10))
        assertEquals(1..6, SwitcherSelection.visibleWindow(6, 10))
        assertEquals(4..9, SwitcherSelection.visibleWindow(9, 10))
        assertEquals(3..5, SwitcherSelection.visibleWindow(5, 10, 3))
        assertEquals(IntRange.EMPTY, SwitcherSelection.visibleWindow(0, 0))
    }
}
