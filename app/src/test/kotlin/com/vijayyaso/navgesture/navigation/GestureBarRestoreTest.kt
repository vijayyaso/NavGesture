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

package com.vijayyaso.navgesture.navigation

import com.vijayyaso.navgesture.navigation.GestureBarHider.BarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureBarRestoreTest {

    @Test
    fun `a bar this app hid comes back once the gestures are gone`() {
        assertTrue(GestureBarHider.shouldRestore(hidByUs = true, serviceRunning = false, switchedOn = false))
    }

    @Test
    fun `a running service keeps the bar hidden`() {
        assertFalse(GestureBarHider.shouldRestore(hidByUs = true, serviceRunning = true, switchedOn = true))
    }

    @Test
    fun `a service switched on but not yet bound is left to the system`() {
        assertFalse(GestureBarHider.shouldRestore(hidByUs = true, serviceRunning = false, switchedOn = true))
    }

    @Test
    fun `a bar this app never hid is left alone`() {
        assertFalse(GestureBarHider.shouldRestore(hidByUs = false, serviceRunning = false, switchedOn = false))
    }

    private fun section(
        hyperOs: Boolean = true,
        play: Boolean = false,
        canWrite: Boolean = false,
        hidByUs: Boolean = false,
        hiddenNow: Boolean = false,
    ) = GestureBarHider.barState(hyperOs, play, canWrite, hidByUs, hiddenNow)

    @Test
    fun `with the permission the switch works`() {
        assertEquals(BarState.CAN_HIDE, section(canWrite = true))
        assertEquals(BarState.CAN_HIDE, section(canWrite = true, play = true, hidByUs = true, hiddenNow = true))
    }

    @Test
    fun `without the permission the github build shows the grant`() {
        assertEquals(BarState.NEEDS_GRANT, section())
    }

    @Test
    fun `the play build never shows the grant`() {
        assertEquals(BarState.NONE, section(play = true))
    }

    @Test
    fun `a bar this app hid that cannot come back is said plainly, in both builds`() {
        assertEquals(BarState.STUCK_HIDDEN, section(hidByUs = true, hiddenNow = true))
        assertEquals(BarState.STUCK_HIDDEN, section(play = true, hidByUs = true, hiddenNow = true))
    }

    @Test
    fun `a bar already brought back by hand is not reported as stuck`() {
        assertEquals(BarState.NEEDS_GRANT, section(hidByUs = true, hiddenNow = false))
        assertEquals(BarState.NONE, section(play = true, hidByUs = true, hiddenNow = false))
    }

    @Test
    fun `a bar hidden by someone else is not this app's to report`() {
        assertEquals(BarState.NEEDS_GRANT, section(hidByUs = false, hiddenNow = true))
    }

    @Test
    fun `off HyperOS there is no section`() {
        assertEquals(BarState.NONE, section(hyperOs = false))
        assertEquals(BarState.NONE, section(hyperOs = false, canWrite = true))
    }

    @Test
    fun `only a hidden bar this app hid, with no permission, is stuck`() {
        assertTrue(GestureBarHider.stuck(canWrite = false, hidByUs = true, hiddenNow = true))
        assertFalse(GestureBarHider.stuck(canWrite = true, hidByUs = true, hiddenNow = true))
        assertFalse(GestureBarHider.stuck(canWrite = false, hidByUs = false, hiddenNow = true))
        assertFalse(GestureBarHider.stuck(canWrite = false, hidByUs = true, hiddenNow = false))
    }
}
