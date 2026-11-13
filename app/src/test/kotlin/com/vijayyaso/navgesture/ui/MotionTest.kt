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

package com.vijayyaso.navgesture.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {

    @Test
    fun `left to right, forward arrives from the right and back from the left`() {
        assertEquals(100, screenSlideOffset(width = 1000, forward = true, rtl = false))
        assertEquals(-100, screenSlideOffset(width = 1000, forward = false, rtl = false))
    }

    @Test
    fun `right to left mirrors both directions`() {
        assertEquals(-100, screenSlideOffset(width = 1000, forward = true, rtl = true))
        assertEquals(100, screenSlideOffset(width = 1000, forward = false, rtl = true))
    }
}
