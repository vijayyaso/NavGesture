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
}
