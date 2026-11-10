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
import org.junit.Test

class CornerShortcutTest {
    @Test fun `every fixed choice round trips through its id`() {
        CornerShortcut.fixed.forEach { assertEquals(it, CornerShortcut.decode(it.id)) }
    }

    @Test fun `an app round trips through its id`() {
        val app = CornerShortcut.OpenApp("com.whatsapp")
        assertEquals("app:com.whatsapp", app.id)
        assertEquals(app, CornerShortcut.decode(app.id))
    }

    @Test fun `missing, unknown and blank ids are nothing`() {
        assertEquals(CornerShortcut.None, CornerShortcut.decode(null))
        assertEquals(CornerShortcut.None, CornerShortcut.decode(""))
        assertEquals(CornerShortcut.None, CornerShortcut.decode("teleport"))
        assertEquals(CornerShortcut.None, CornerShortcut.decode("app:"))
    }

    @Test fun `an unknown list style falls back to drag`() {
        assertEquals(AppListStyle.TAP, AppListStyle.decode("TAP"))
        assertEquals(AppListStyle.DRAG, AppListStyle.decode("SWIPE"))
        assertEquals(AppListStyle.DRAG, AppListStyle.decode(null))
    }

    @Test fun `the configuration finds each corner's shortcut`() {
        val configuration = GestureConfiguration(rightCornerShortcut = CornerShortcut.LockScreen)
        assertEquals(CornerShortcut.None, configuration.shortcutFor(BottomCorner.LEFT))
        assertEquals(CornerShortcut.LockScreen, configuration.shortcutFor(BottomCorner.RIGHT))
        assertEquals(true, configuration.anyCornerShortcut)
        assertEquals(false, GestureConfiguration().anyCornerShortcut)
    }

    @Test fun `drag always opens what is highlighted, tap never highlights`() {
        assertEquals(true, AppListStyle.DRAG.opensOnLift(0))
        assertEquals(true, AppListStyle.DRAG.showsSelection(0))
        assertEquals(false, AppListStyle.TAP.opensOnLift(3))
        assertEquals(false, AppListStyle.TAP.showsSelection(3))
    }

    @Test fun `both highlights and opens only once a step is slid`() {
        assertEquals(false, AppListStyle.BOTH.opensOnLift(0))
        assertEquals(false, AppListStyle.BOTH.showsSelection(0))
        assertEquals(true, AppListStyle.BOTH.opensOnLift(1))
        assertEquals(true, AppListStyle.BOTH.showsSelection(1))
        assertEquals(true, AppListStyle.BOTH.opensOnLift(-2))
    }

    @Test fun `both picks the app in the middle on the first step, then moves on`() {
        // Step 0 is "no slide" in Both, so the first step must land on the middle app.
        assertEquals(0, AppListStyle.BOTH.effectiveSteps(0))
        assertEquals(0, AppListStyle.BOTH.effectiveSteps(1))
        assertEquals(1, AppListStyle.BOTH.effectiveSteps(2))
        assertEquals(-1, AppListStyle.BOTH.effectiveSteps(-1))
        // The apps reached: recents with the current app dimmed first, so the middle app is 1.
        assertEquals(1, SwitcherSelection.indexFor(AppListStyle.BOTH.effectiveSteps(1), 5, 1))
        assertEquals(2, SwitcherSelection.indexFor(AppListStyle.BOTH.effectiveSteps(2), 5, 1))
        assertEquals(4, SwitcherSelection.indexFor(AppListStyle.BOTH.effectiveSteps(-1), 5, 1))
    }

    @Test fun `drag keeps step zero as the pick`() {
        assertEquals(0, AppListStyle.DRAG.effectiveSteps(0))
        assertEquals(2, AppListStyle.DRAG.effectiveSteps(2))
        assertEquals(-3, AppListStyle.TAP.effectiveSteps(-3))
    }
}
