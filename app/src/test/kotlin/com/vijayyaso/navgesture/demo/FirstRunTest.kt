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

import com.vijayyaso.navgesture.setup.SetupRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunTest {

    private val fresh = SetupRecord()

    @Test fun `a fresh install with the service off opens on the demo`() {
        assertEquals(FirstStack.DEMO, FirstRun.firstStack(fresh, serviceOn = false, tileAskedDisclosure = false))
    }

    @Test fun `the tile's request wins on a fresh install`() {
        assertEquals(FirstStack.DISCLOSURE, FirstRun.firstStack(fresh, serviceOn = false, tileAskedDisclosure = true))
    }

    @Test fun `the tile's request wins for any record`() {
        val past = SetupRecord(demoSeen = true, serviceEverEnabled = true, disclosureAccepted = true)
        assertEquals(FirstStack.DISCLOSURE, FirstRun.firstStack(past, serviceOn = true, tileAskedDisclosure = true))
    }

    @Test fun `a demo already seen opens on home`() {
        assertEquals(
            FirstStack.HOME,
            FirstRun.firstStack(SetupRecord(demoSeen = true), serviceOn = false, tileAskedDisclosure = false),
        )
    }

    @Test fun `a service on now opens on home, before the record knows`() {
        assertEquals(FirstStack.HOME, FirstRun.firstStack(fresh, serviceOn = true, tileAskedDisclosure = false))
    }

    @Test fun `a service turned on before opens on home`() {
        assertEquals(
            FirstStack.HOME,
            FirstRun.firstStack(SetupRecord(serviceEverEnabled = true), serviceOn = false, tileAskedDisclosure = false),
        )
    }

    @Test fun `a disclosure agreed to before opens on home`() {
        assertEquals(
            FirstStack.HOME,
            FirstRun.firstStack(SetupRecord(disclosureAccepted = true), serviceOn = false, tileAskedDisclosure = false),
        )
    }

    @Test fun `the demo is marked as seen at every decision but a plain home for a seen record`() {
        val seen = SetupRecord(demoSeen = true)
        assertTrue(FirstRun.marksDemoSeen(fresh, FirstStack.DEMO))
        assertTrue(FirstRun.marksDemoSeen(fresh, FirstStack.DISCLOSURE))
        assertTrue(FirstRun.marksDemoSeen(seen, FirstStack.DISCLOSURE))
        assertTrue(FirstRun.marksDemoSeen(SetupRecord(serviceEverEnabled = true), FirstStack.HOME))
        assertFalse(FirstRun.marksDemoSeen(seen, FirstStack.HOME))
    }
}
