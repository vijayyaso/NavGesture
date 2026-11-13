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

class SwipeActionTest {
    @Test fun `every fixed choice round trips through its id`() {
        SwipeAction.fixed.forEach { assertEquals(it, SwipeAction.decode(it.id)) }
    }

    @Test fun `the five new actions decode but the picker does not offer them yet`() {
        val added = listOf(
            "back" to SwipeAction.Back,
            "home" to SwipeAction.Home,
            "recents" to SwipeAction.Recents,
            "previous_app" to SwipeAction.PreviousApp,
            "torch" to SwipeAction.Torch,
        )
        added.forEach { (id, action) ->
            assertEquals(id, action.id)
            assertEquals(action, SwipeAction.decode(id))
            assertFalse(action in SwipeAction.fixed)
        }
    }

    @Test fun `a run is named by its corner, by Back Home or Recents, or by its stored id`() {
        val left = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT)
        val bottom = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)
        assertEquals("CornerLEFT", cornerRun(BottomCorner.LEFT, SwipeAction.Notifications).id)
        assertEquals("CornerRIGHT", cornerRun(BottomCorner.RIGHT, SwipeAction.Home).id)
        assertEquals("Back", GestureAction.Run(left, SwipeAction.Back).id)
        assertEquals("Home", GestureAction.Run(bottom, SwipeAction.Home).id)
        assertEquals("Recents", GestureAction.Run(bottom, SwipeAction.Recents).id)
        assertEquals("notifications", GestureAction.Run(left, SwipeAction.Notifications).id)
        assertEquals("previous_app", GestureAction.Run(left, SwipeAction.PreviousApp).id)
    }

    @Test fun `each action says whether it settles, needs the list panel, is guarded and what icon it has`() {
        val settling = listOf(
            SwipeAction.OpenApp("com.whatsapp"),
            SwipeAction.AppShortcut("com.google.android.keep", "new_note"),
            SwipeAction.PreviousApp,
            SwipeAction.Screenshot,
        )
        settling.forEach { assertTrue(it.id, it.settles) }
        listOf(SwipeAction.Back, SwipeAction.Home, SwipeAction.Notifications, SwipeAction.RecentsList)
            .forEach { assertFalse(it.id, it.settles) }
        assertTrue(SwipeAction.RecentsList.needsListPanel)
        assertTrue(SwipeAction.FavouritesList.needsListPanel)
        assertFalse(SwipeAction.Recents.needsListPanel)
        assertFalse(SwipeAction.None.guardedInLandscape)
        assertTrue(SwipeAction.Home.guardedInLandscape)
        assertTrue(SwipeAction.Torch.guardedInLandscape)
        assertEquals(ActionIcon.BACK, SwipeAction.Back.icon)
        assertEquals(ActionIcon.HOME, SwipeAction.Home.icon)
        assertEquals(ActionIcon.RECENTS, SwipeAction.Recents.icon)
        assertEquals(ActionIcon.GENERIC, SwipeAction.Screenshot.icon)
        assertEquals(ActionIcon.NONE, SwipeAction.None.icon)
    }

    @Test fun `an app round trips through its id`() {
        val app = SwipeAction.OpenApp("com.whatsapp")
        assertEquals("app:com.whatsapp", app.id)
        assertEquals(app, SwipeAction.decode(app.id))
    }

    @Test fun `an app shortcut round trips through its id, even with a slash in it`() {
        val shortcut = SwipeAction.AppShortcut("com.google.android.keep", "new/note")
        assertEquals("shortcut:com.google.android.keep/new/note", shortcut.id)
        assertEquals(shortcut, SwipeAction.decode(shortcut.id))
    }

    @Test fun `a broken app shortcut id is nothing`() {
        assertEquals(SwipeAction.None, SwipeAction.decode("shortcut:com.foo"))
        assertEquals(SwipeAction.None, SwipeAction.decode("shortcut:/new_note"))
        assertEquals(SwipeAction.None, SwipeAction.decode("shortcut:com.foo/"))
        assertEquals(SwipeAction.None, SwipeAction.decode("shortcut: /x"))
        assertEquals(SwipeAction.None, SwipeAction.decode("shortcut:"))
    }

    @Test fun `missing, unknown and blank ids are nothing`() {
        assertEquals(SwipeAction.None, SwipeAction.decode(null))
        assertEquals(SwipeAction.None, SwipeAction.decode(""))
        assertEquals(SwipeAction.None, SwipeAction.decode("teleport"))
        assertEquals(SwipeAction.None, SwipeAction.decode("app:"))
    }

    @Test fun `an unknown list style falls back to drag`() {
        assertEquals(AppListStyle.TAP, AppListStyle.decode("TAP"))
        assertEquals(AppListStyle.DRAG, AppListStyle.decode("SWIPE"))
        assertEquals(AppListStyle.DRAG, AppListStyle.decode(null))
    }

    @Test fun `the configuration finds each corner's shortcut`() {
        val configuration = GestureConfiguration(rightCornerShortcut = SwipeAction.LockScreen)
        assertEquals(SwipeAction.None, configuration.shortcutFor(BottomCorner.LEFT))
        assertEquals(SwipeAction.LockScreen, configuration.shortcutFor(BottomCorner.RIGHT))
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
        // The apps reached: the current app is left out, so the middle app is 0.
        assertEquals(0, SwitcherSelection.indexFor(AppListStyle.BOTH.effectiveSteps(1), 5))
        assertEquals(1, SwitcherSelection.indexFor(AppListStyle.BOTH.effectiveSteps(2), 5))
        assertEquals(4, SwitcherSelection.indexFor(AppListStyle.BOTH.effectiveSteps(-1), 5))
    }

    @Test fun `drag keeps step zero as the pick`() {
        assertEquals(0, AppListStyle.DRAG.effectiveSteps(0))
        assertEquals(2, AppListStyle.DRAG.effectiveSteps(2))
        assertEquals(-3, AppListStyle.TAP.effectiveSteps(-3))
    }
}
