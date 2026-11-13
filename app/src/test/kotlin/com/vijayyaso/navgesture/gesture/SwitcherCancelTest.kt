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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitcherCancelTest {
    // A 2400 px tall phone at density 2.75, so 1 dp is 2.75 px.
    private val density = 2.75f
    private val screen = 2400f

    /** The open point [aboveDp] above the bottom edge of the glass, in screen pixels. */
    private fun openAt(aboveDp: Float) = screen - aboveDp * density

    private fun neededDp(aboveDp: Float) =
        SwitcherCancel.distancePx(openAt(aboveDp), screen, density) / density

    @Test
    fun `far above the bottom the full slide is needed`() {
        assertEquals(32f, neededDp(400f), 0.01f)
        assertEquals(32f, neededDp(60f), 0.01f)
    }

    @Test
    fun `40 dp above the bottom still needs the full slide`() {
        assertEquals(32f, neededDp(40f), 0.01f)
    }

    @Test
    fun `25 dp above the bottom needs only the room down to the edge margin`() {
        assertEquals(25f - SwitcherCancel.EDGE_MARGIN_DP, neededDp(25f), 0.01f)
        // The finger reaches it before the last few dp of the glass.
        assertTrue(SwitcherCancel.isCancel(openAt(25f), openAt(SwitcherCancel.EDGE_MARGIN_DP), screen, density))
    }

    @Test
    fun `15 dp above the bottom needs the floor`() {
        assertEquals(SwitcherCancel.MIN_DP, neededDp(15f), 0.01f)
    }

    @Test
    fun `at the very bottom the floor still holds`() {
        assertEquals(SwitcherCancel.MIN_DP, neededDp(0f), 0.01f)
        // Below the glass: a bad height reading must not make cancel instant.
        assertEquals(SwitcherCancel.MIN_DP, neededDp(-5f), 0.01f)
    }

    @Test
    fun `the needed slide stays between the floor and 32 dp at every height`() {
        var above = -10f
        while (above <= 200f) {
            val needed = neededDp(above)
            assertTrue("at $above dp: $needed", needed >= SwitcherCancel.MIN_DP - 0.01f)
            assertTrue("at $above dp: $needed", needed <= SwitcherCancel.CANCEL_DP + 0.01f)
            above += 0.5f
        }
    }

    @Test
    fun `the floor is more than the rise that opens the list`() {
        assertTrue(SwitcherCancel.MIN_DP > GestureConfiguration().bottomMinimumSwipeDistanceDp)
    }

    @Test
    fun `sideways wobble under the floor never cancels`() {
        val open = openAt(15f)
        val wobble = (SwitcherCancel.MIN_DP - 1f) * density
        assertFalse(SwitcherCancel.isCancel(open, open + wobble, screen, density))
        // Rising never cancels.
        assertFalse(SwitcherCancel.isCancel(open, open - 100f, screen, density))
    }

    @Test
    fun `far above the bottom a short slide down does not cancel and the full one does`() {
        val open = openAt(300f)
        assertFalse(SwitcherCancel.isCancel(open, open + 31f * density, screen, density))
        assertTrue(SwitcherCancel.isCancel(open, open + 32f * density, screen, density))
    }
}
