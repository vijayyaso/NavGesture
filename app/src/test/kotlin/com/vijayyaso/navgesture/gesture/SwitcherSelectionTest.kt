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
    fun `no slide stays on the start index`() {
        assertEquals(1, SwitcherSelection.looped(0f, 5, 1))
        assertEquals(0, SwitcherSelection.looped(10f, 5, 0))
    }

    @Test
    fun `each step moves one app`() {
        assertEquals(2, SwitcherSelection.looped(56f, 5, 1))
        assertEquals(3, SwitcherSelection.looped(112f, 5, 1))
    }

    @Test
    fun `sliding back moves one app per step`() {
        assertEquals(4, SwitcherSelection.looped(-56f, 5, 1))
    }

    @Test
    fun `past the last app comes the first`() {
        // 4 selectable apps (1..4) with the dimmed current app at 0.
        assertEquals(1, SwitcherSelection.looped(56f * 4, 5, 1))
        assertEquals(2, SwitcherSelection.looped(56f * 5, 5, 1))
    }

    @Test
    fun `before the first app comes the last`() {
        assertEquals(4, SwitcherSelection.looped(-56f, 5, 1))
        assertEquals(3, SwitcherSelection.looped(-112f, 5, 1))
    }

    @Test
    fun `favourites loop over the whole list`() {
        assertEquals(0, SwitcherSelection.looped(56f * 3, 3, 0))
        assertEquals(2, SwitcherSelection.looped(-56f, 3, 0))
    }

    @Test
    fun `an empty list selects nothing`() {
        assertNull(SwitcherSelection.looped(200f, 0, 0))
    }

    @Test
    fun `a one-app recents list cannot select itself`() {
        assertNull(SwitcherSelection.looped(200f, 1, 1))
        assertNull(SwitcherSelection.looped(200f, 3, 4))
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
