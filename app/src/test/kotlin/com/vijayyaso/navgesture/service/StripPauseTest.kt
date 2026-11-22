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

package com.vijayyaso.navgesture.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripPauseTest {

    @After
    fun reset() {
        StripPause.listen(null)
        StripPause.set(false)
    }

    @Test
    fun `the strips are not paused until the demo asks`() {
        assertFalse(StripPause.paused)
    }

    @Test
    fun `a change tells the installed overlay once`() {
        var told = 0
        StripPause.listen { told++ }
        StripPause.set(true)
        StripPause.set(true)
        assertTrue(StripPause.paused)
        assertEquals(1, told)
        StripPause.set(false)
        assertFalse(StripPause.paused)
        assertEquals(2, told)
    }

    @Test
    fun `a pause set with no strips installed is kept for the next install`() {
        StripPause.set(true)
        var told = 0
        StripPause.listen { told++ }
        assertTrue(StripPause.paused)
        assertEquals(0, told)
    }
}
