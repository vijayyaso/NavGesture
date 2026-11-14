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

import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackStackTest {

    private val home = BackStack.of(Screen.HOME)

    @Test
    fun `opening a screen puts it on top`() {
        assertEquals(listOf(Screen.HOME, Screen.SETUP), home.open(Screen.SETUP, 0).screens)
    }

    @Test
    fun `opening the screen already on top leaves the stack alone`() {
        val stack = home.open(Screen.SETUP, 0)
        assertSame(stack, stack.open(Screen.SETUP, 500))
    }

    @Test
    fun `a screen lower in the stack opens again on top`() {
        assertEquals(
            listOf(Screen.HOME, Screen.SETUP, Screen.HOME),
            home.open(Screen.SETUP, 0).open(Screen.HOME, 0).screens,
        )
    }

    @Test
    fun `a screen opened forward starts at its top`() {
        assertEquals(0, home.open(Screen.GESTURE_SETTINGS, 300).top.scroll)
    }

    @Test
    fun `back returns the covered screen to where it was left`() {
        val stack = home.open(Screen.GESTURE_SETTINGS, 0).open(Screen.LEFT_CORNER, 840)
        val back = stack.back()
        assertEquals(Screen.GESTURE_SETTINGS, back.top.screen)
        assertEquals(840, back.top.scroll)
    }

    @Test
    fun `the same screen opened forward again starts at its top`() {
        val settings = home.open(Screen.GESTURE_SETTINGS, 0)
        val again = settings.open(Screen.LEFT_CORNER, 840).back().back().open(Screen.GESTURE_SETTINGS, 120)
        assertEquals(0, again.top.scroll)
        assertNotEquals(settings.top.id, again.top.id)
    }

    @Test
    fun `back drops the top entry and its scroll`() {
        val stack = home.open(Screen.APP_SWITCHING, 50).open(Screen.FAVOURITE_APPS, 900)
        val popped = stack.back()
        assertEquals(listOf(Screen.HOME, Screen.APP_SWITCHING), popped.screens)
        assertEquals(listOf(50, 900), popped.entries.map { it.scroll })
        assertEquals(false, popped.entries.any { it.id == stack.top.id })
    }

    @Test
    fun `every opening gets an id no entry has had before`() {
        val first = home.open(Screen.ABOUT, 0)
        val second = first.back().open(Screen.ABOUT, 0)
        assertNotEquals(first.top.id, second.top.id)
        assertEquals(second.entries.size, second.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `back never pops the last screen`() {
        assertSame(home, home.back())
        val twice = home.open(Screen.ABOUT, 0).back().back()
        assertEquals(listOf(Screen.HOME), twice.screens)
    }

    @Test
    fun `back from the screen on top pops it`() {
        val stack = home.open(Screen.GESTURE_SETTINGS, 0).open(Screen.LEFT_CORNER, 0)
        assertEquals(listOf(Screen.HOME, Screen.GESTURE_SETTINGS), stack.backFrom(stack.top.id).screens)
    }

    @Test
    fun `back from a screen that already left does nothing`() {
        val stack = home.open(Screen.GESTURE_SETTINGS, 0).open(Screen.LEFT_CORNER, 0)
        val leaving = stack.top.id
        val popped = stack.backFrom(leaving)
        assertSame(popped, popped.backFrom(leaving))
    }

    @Test
    fun `opening from a screen that already left does nothing`() {
        val stack = home.open(Screen.GESTURE_SETTINGS, 0)
        val leaving = stack.top.id
        val popped = stack.back()
        assertSame(popped, popped.openFrom(leaving, Screen.ABOUT, 0))
        assertEquals(Screen.ABOUT, popped.openFrom(popped.top.id, Screen.ABOUT, 0).top.screen)
    }

    @Test
    fun `a newer entry arriving is forward, an older one is back`() {
        val settings = home.open(Screen.GESTURE_SETTINGS, 0)
        assertEquals(true, opensForward(home.top, settings.top))
        assertEquals(false, opensForward(settings.top, settings.back().top))
        assertEquals(true, opensForward(settings.top, settings.replacedBy(Screen.HOME, Screen.DISCLOSURE).top))
    }

    @Test
    fun `a replaced stack opens every screen at its top with new ids`() {
        val old = home.open(Screen.GESTURE_SETTINGS, 400).open(Screen.LEFT_CORNER, 700)
        val replaced = old.replacedBy(Screen.HOME, Screen.DISCLOSURE)
        assertEquals(listOf(Screen.HOME, Screen.DISCLOSURE), replaced.screens)
        assertEquals(listOf(0, 0), replaced.entries.map { it.scroll })
        assertEquals(false, replaced.entries.any { new -> old.entries.any { it.id == new.id } })
    }

    @Test
    fun `a saved stack restores screens, ids, scrolls and the id counter`() {
        val stack = home.open(Screen.GESTURE_SETTINGS, 400).open(Screen.LEFT_CORNER, 700)
        val restored = backStackFromSaved(stack.toSaved())
        assertEquals(stack, restored)
        assertNotEquals(stack.top.id, restored.open(Screen.ABOUT, 0).top.id)
    }

    @Test
    fun `the saved lists always have one item per screen`() {
        val saved = home.open(Screen.SETUP, 10).open(Screen.ABOUT, 20).toSaved()
        val sizes = saved.take(3).map { (it as List<*>).size }
        assertEquals(listOf(3, 3, 3), sizes)
    }

    @Test
    fun `saved names restore their screens in order`() {
        assertEquals(listOf(Screen.HOME, Screen.ABOUT), backStackOf(listOf("HOME", "ABOUT")).screens)
    }

    @Test
    fun `an unknown saved name is skipped`() {
        assertEquals(
            listOf(Screen.HOME, Screen.SETUP),
            backStackOf(listOf("HOME", "RETIRED", "SETUP")).screens,
        )
    }

    @Test
    fun `an unknown saved name takes its own scroll with it`() {
        val stack = backStackOf(listOf("HOME", "RETIRED", "SETUP"), ids = listOf(0, 1, 2), scrolls = listOf(5, 6, 7))
        assertEquals(listOf(5, 7), stack.entries.map { it.scroll })
        assertEquals(listOf(0, 2), stack.entries.map { it.id })
    }

    @Test
    fun `a stack of unknown names restores to home`() {
        assertEquals(listOf(Screen.HOME), backStackOf(listOf("RETIRED")).screens)
        assertEquals(listOf(Screen.HOME), backStackOf(emptyList()).screens)
    }

    @Test
    fun `a broken saved value restores to home`() {
        assertEquals(listOf(Screen.HOME), backStackFromSaved(listOf("nonsense")).screens)
    }

    @Test
    fun `an edge page and its picker stack on gesture settings and restore by name`() {
        val pages = listOf(
            Screen.LEFT_EDGE to Screen.LEFT_SWIPE_IN,
            Screen.RIGHT_EDGE to Screen.RIGHT_SWIPE_IN,
            Screen.BOTTOM_EDGE to Screen.BOTTOM_SWIPE_UP,
            Screen.BOTTOM_EDGE to Screen.BOTTOM_SWIPE_UP_PAUSE,
            Screen.LEFT_EDGE to Screen.LEFT_SWIPE_FAR_IN,
            Screen.RIGHT_EDGE to Screen.RIGHT_SWIPE_FAR_IN,
            Screen.LEFT_EDGE to Screen.LEFT_TOP_SWIPE_IN,
            Screen.LEFT_EDGE to Screen.LEFT_TOP_SWIPE_FAR_IN,
            Screen.LEFT_EDGE to Screen.LEFT_BOTTOM_SWIPE_IN,
            Screen.LEFT_EDGE to Screen.LEFT_BOTTOM_SWIPE_FAR_IN,
            Screen.RIGHT_EDGE to Screen.RIGHT_TOP_SWIPE_IN,
            Screen.RIGHT_EDGE to Screen.RIGHT_TOP_SWIPE_FAR_IN,
            Screen.RIGHT_EDGE to Screen.RIGHT_BOTTOM_SWIPE_IN,
            Screen.RIGHT_EDGE to Screen.RIGHT_BOTTOM_SWIPE_FAR_IN,
        )
        pages.forEach { (page, picker) ->
            val stack = home.open(Screen.GESTURE_SETTINGS, 300).open(page, 0).open(picker, 120)
            assertEquals(listOf(Screen.HOME, Screen.GESTURE_SETTINGS, page, picker), stack.screens)
            assertEquals(listOf(Screen.HOME, Screen.GESTURE_SETTINGS, page), stack.back().screens)
            assertEquals(120, stack.back().top.scroll)
            assertEquals(stack, backStackFromSaved(stack.toSaved()))
        }
    }

    @Test
    fun `the three edge pages restore from their saved names`() {
        assertEquals(
            listOf(Screen.HOME, Screen.LEFT_EDGE, Screen.RIGHT_EDGE, Screen.BOTTOM_EDGE),
            backStackOf(listOf("HOME", "LEFT_EDGE", "RIGHT_EDGE", "BOTTOM_EDGE")).screens,
        )
    }

    @Test
    fun `each picker page sets its own slot, and no other screen sets one`() {
        val pickers = mapOf(
            Screen.LEFT_CORNER to SlotId.Corner(BottomCorner.LEFT),
            Screen.RIGHT_CORNER to SlotId.Corner(BottomCorner.RIGHT),
            Screen.LEFT_SWIPE_IN to SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT),
            Screen.RIGHT_SWIPE_IN to SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.WHOLE, SwipeKind.SHORT),
            Screen.BOTTOM_SWIPE_UP to SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT),
            Screen.BOTTOM_SWIPE_UP_PAUSE to SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE),
            Screen.LEFT_SWIPE_FAR_IN to SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG),
            Screen.RIGHT_SWIPE_FAR_IN to SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.WHOLE, SwipeKind.LONG),
            Screen.LEFT_TOP_SWIPE_IN to SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.SHORT),
            Screen.LEFT_TOP_SWIPE_FAR_IN to SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.LONG),
            Screen.LEFT_BOTTOM_SWIPE_IN to SlotId.Edge(ScreenEdge.LEFT, SwipeZone.BOTTOM_HALF, SwipeKind.SHORT),
            Screen.LEFT_BOTTOM_SWIPE_FAR_IN to SlotId.Edge(ScreenEdge.LEFT, SwipeZone.BOTTOM_HALF, SwipeKind.LONG),
            Screen.RIGHT_TOP_SWIPE_IN to SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.TOP_HALF, SwipeKind.SHORT),
            Screen.RIGHT_TOP_SWIPE_FAR_IN to SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.TOP_HALF, SwipeKind.LONG),
            Screen.RIGHT_BOTTOM_SWIPE_IN to SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.BOTTOM_HALF, SwipeKind.SHORT),
            Screen.RIGHT_BOTTOM_SWIPE_FAR_IN to SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.BOTTOM_HALF, SwipeKind.LONG),
        )
        Screen.entries.forEach { assertEquals(it.name, pickers[it], it.pickerSlot) }
    }

    @Test
    fun `the far swipe and half pickers restore from their saved names, next to the older ones`() {
        val names = listOf(
            "HOME", "GESTURE_SETTINGS", "LEFT_EDGE", "LEFT_SWIPE_IN", "LEFT_SWIPE_FAR_IN", "RIGHT_TOP_SWIPE_IN",
            "RIGHT_BOTTOM_SWIPE_FAR_IN", "BOTTOM_SWIPE_UP_PAUSE", "LEFT_CORNER",
        )
        assertEquals(names, backStackOf(names).screens.map { it.name })
    }

    @Test
    fun `no two picker pages set the same slot`() {
        val slots = Screen.entries.mapNotNull { it.pickerSlot }
        assertEquals(slots.size, slots.toSet().size)
    }
}
