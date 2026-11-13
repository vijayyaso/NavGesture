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

package com.vijayyaso.navgesture.gesture

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vijayyaso.navgesture.FilesContext
import com.vijayyaso.navgesture.MemoryDataStore
import com.vijayyaso.navgesture.settings.GestureSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SwipeSlotsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val leftShort = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val rightShort = SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val bottomShort = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val bottomPause = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE)

    private val leftBack = booleanPreferencesKey("left_edge_back_enabled")
    private val rightBack = booleanPreferencesKey("right_edge_back_enabled")
    private val version = intPreferencesKey("slots_version")

    @Test fun `the defaults are today's swipes, Back on each side, Home and Recents at the bottom`() {
        val slots = SwipeSlots()
        for (edge in listOf(ScreenEdge.LEFT, ScreenEdge.RIGHT)) {
            for (zone in SwipeZone.entries) {
                assertEquals(SwipeAction.Back, slots[SlotId.Edge(edge, zone, SwipeKind.SHORT)])
                assertEquals(SwipeAction.None, slots[SlotId.Edge(edge, zone, SwipeKind.LONG)])
                assertEquals(SwipeAction.None, slots[SlotId.Edge(edge, zone, SwipeKind.WAIT)])
            }
        }
        assertEquals(SwipeAction.Home, slots[bottomShort])
        assertEquals(SwipeAction.Recents, slots[bottomPause])
        assertFalse(slots.leftSplit)
        assertFalse(slots.rightSplit)
        assertEquals(SwipeSlots(), GestureConfiguration().slots)
    }

    @Test fun `every edge is in use by default`() {
        ScreenEdge.entries.forEach { assertTrue(it.name, SwipeSlots().edgeInUse(it)) }
    }

    @Test fun `a side with nothing set is not in use, so it gets no strip`() {
        val slots = SwipeSlots().with(leftShort, SwipeAction.None)
        assertFalse(slots.edgeInUse(ScreenEdge.LEFT))
        assertTrue(slots.edgeInUse(ScreenEdge.RIGHT))
    }

    @Test fun `any slot of a side's zone keeps it in use, an unreachable half does not`() {
        val withLong = SwipeSlots().with(leftShort, SwipeAction.None)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.LONG), SwipeAction.Screenshot)
        assertTrue(withLong.edgeInUse(ScreenEdge.LEFT))
        assertTrue(withLong.zoneInUse(ScreenEdge.LEFT, SwipeZone.WHOLE))

        // A half is reached only once the edge is split.
        val halfOnly = SwipeSlots().with(leftShort, SwipeAction.None)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.SHORT), SwipeAction.Home)
            .with(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.BOTTOM_HALF, SwipeKind.SHORT), SwipeAction.None)
        assertFalse(halfOnly.edgeInUse(ScreenEdge.LEFT))
        assertTrue(halfOnly.withSplit(ScreenEdge.LEFT, true).edgeInUse(ScreenEdge.LEFT))
    }

    @Test fun `a touch reads the whole edge unless it is split, then the half it starts in`() {
        val slots = SwipeSlots()
        assertEquals(SwipeZone.WHOLE, slots.zoneAt(ScreenEdge.LEFT, 100f, 1200f))
        val split = slots.withSplit(ScreenEdge.LEFT, true)
        assertEquals(SwipeZone.TOP_HALF, split.zoneAt(ScreenEdge.LEFT, 100f, 1200f))
        assertEquals(SwipeZone.BOTTOM_HALF, split.zoneAt(ScreenEdge.LEFT, 1200f, 1200f))
        assertEquals(SwipeZone.WHOLE, split.zoneAt(ScreenEdge.RIGHT, 100f, 1200f))
        assertEquals(SwipeZone.WHOLE, split.zoneAt(ScreenEdge.BOTTOM, 100f, 1200f))
    }

    @Test fun `a slot set back to its default equals the default, so equal settings compare equal`() {
        val changedBack = SwipeSlots().with(leftShort, SwipeAction.Home).with(leftShort, SwipeAction.Back)
        assertEquals(SwipeSlots(), changedBack)
        assertEquals(SwipeSlots().hashCode(), changedBack.hashCode())
    }

    @Test fun `a slot no edge has is ignored`() {
        val sidePause = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.WHOLE, SwipeKind.PAUSE)
        assertEquals(SwipeSlots(), SwipeSlots().with(sidePause, SwipeAction.Home))
    }

    @Test fun `home and back are found on any slot or corner`() {
        val defaults = GestureConfiguration()
        assertTrue(defaults.givesHome)
        assertTrue(defaults.givesBack)

        val noHome = defaults.copy(slots = defaults.slots.with(bottomShort, SwipeAction.Notifications))
        assertFalse(noHome.givesHome)
        assertTrue(noHome.copy(rightCornerShortcut = SwipeAction.Home).givesHome)
        assertTrue(noHome.copy(slots = noHome.slots.with(leftShort, SwipeAction.Home)).givesHome)
        assertTrue(noHome.copy(slots = noHome.slots.with(bottomPause, SwipeAction.Home)).givesHome)

        val noBack = defaults.copy(
            slots = defaults.slots.with(leftShort, SwipeAction.None).with(rightShort, SwipeAction.Recents),
        )
        assertFalse(noBack.givesBack)
        assertTrue(noBack.copy(leftCornerShortcut = SwipeAction.Back).givesBack)
        assertTrue(noBack.copy(slots = noBack.slots.with(bottomShort, SwipeAction.Back)).givesBack)
    }

    @Test fun `a half that no touch can reach does not count as giving home`() {
        val topHome = SlotId.Edge(ScreenEdge.LEFT, SwipeZone.TOP_HALF, SwipeKind.SHORT)
        val unreached = GestureConfiguration(
            slots = SwipeSlots().with(bottomShort, SwipeAction.None).with(topHome, SwipeAction.Home),
        )
        assertFalse(unreached.givesHome)
        assertTrue(unreached.copy(slots = unreached.slots.withSplit(ScreenEdge.LEFT, true)).givesHome)
    }

    @Test fun `the warnings name what is missing, home first, and none by default`() {
        val defaults = GestureConfiguration()
        assertEquals(emptyList<SwipeWarning>(), defaults.swipeWarnings())
        val noHome = defaults.copy(slots = defaults.slots.with(bottomShort, SwipeAction.None))
        assertEquals(listOf(SwipeWarning.NO_HOME), noHome.swipeWarnings())
        val neither = noHome.copy(
            slots = noHome.slots.with(leftShort, SwipeAction.None).with(rightShort, SwipeAction.None),
        )
        assertEquals(listOf(SwipeWarning.NO_HOME, SwipeWarning.NO_BACK), neither.swipeWarnings())
        assertEquals(listOf(SwipeWarning.NO_BACK), neither.copy(leftCornerShortcut = SwipeAction.Home).swipeWarnings())
    }

    @Test fun `every slot has its own key, named with its zone`() {
        assertEquals("slot_left_whole_short", SwipeSlots.keyOf(leftShort))
        assertEquals(
            "slot_right_top_long",
            SwipeSlots.keyOf(SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.TOP_HALF, SwipeKind.LONG)),
        )
        assertEquals(
            "slot_left_bottom_wait",
            SwipeSlots.keyOf(SlotId.Edge(ScreenEdge.LEFT, SwipeZone.BOTTOM_HALF, SwipeKind.WAIT)),
        )
        assertEquals("slot_bottom_short", SwipeSlots.keyOf(bottomShort))
        assertEquals("slot_bottom_pause", SwipeSlots.keyOf(bottomPause))
        assertEquals(SwipeSlots.ALL.size, SwipeSlots.ALL.map(SwipeSlots::keyOf).toSet().size)
        assertEquals(20, SwipeSlots.ALL.size)
    }

    @Test fun `slots round trip through their keys, defaults included`() {
        val slots = SwipeSlots(leftSplit = true)
            .with(leftShort, SwipeAction.Home)
            .with(bottomPause, SwipeAction.OpenApp("com.whatsapp"))
            .with(SlotId.Edge(ScreenEdge.RIGHT, SwipeZone.BOTTOM_HALF, SwipeKind.WAIT), SwipeAction.Screenshot)
        val stored = slots.encode()
        assertEquals(SwipeSlots.ALL.size, stored.size)
        assertEquals("back", stored["slot_right_whole_short"])
        assertEquals("app:com.whatsapp", stored["slot_bottom_pause"])
        assertEquals(slots, SwipeSlots.decode(leftSplit = true, rightSplit = false, lookup = stored::get))
    }

    @Test fun `a missing key reads its default and an unknown id reads nothing`() {
        val decoded = SwipeSlots.decode(leftSplit = false, rightSplit = false) { key ->
            if (key == "slot_bottom_short") "teleport" else null
        }
        assertEquals(SwipeAction.None, decoded[bottomShort])
        assertEquals(SwipeAction.Back, decoded[leftShort])
        assertEquals(SwipeAction.Recents, decoded[bottomPause])
    }

    @Test fun `an install from before slots keeps a side whose back switch was off empty`() = runBlocking {
        val repository = repositoryWith(mutablePreferencesOf(leftBack to false, rightBack to true))

        val slots = repository.read().slots
        for (zone in SwipeZone.entries) {
            assertEquals(SwipeAction.None, slots[SlotId.Edge(ScreenEdge.LEFT, zone, SwipeKind.SHORT)])
            assertEquals(SwipeAction.Back, slots[SlotId.Edge(ScreenEdge.RIGHT, zone, SwipeKind.SHORT)])
        }
        assertFalse(slots.edgeInUse(ScreenEdge.LEFT))
        assertEquals(SwipeAction.Home, slots[bottomShort])
    }

    @Test fun `an install from before slots with both switches on reads the defaults`() = runBlocking {
        assertEquals(SwipeSlots(), repositoryWith(mutablePreferencesOf()).read().slots)
        assertEquals(SwipeSlots(), repositoryWith(mutablePreferencesOf(rightBack to true)).read().slots)
        val rightOff = repositoryWith(mutablePreferencesOf(rightBack to false)).read().slots
        assertFalse(rightOff.edgeInUse(ScreenEdge.RIGHT))
        assertTrue(rightOff.edgeInUse(ScreenEdge.LEFT))
    }

    @Test fun `the first save writes the version and the slots and drops the old switches`() = runBlocking {
        val store = MemoryDataStore(mutablePreferencesOf(leftBack to false, rightBack to false))
        val repository = GestureSettingsRepository(FilesContext(folder.root), store)

        repository.update { it.copy(hapticFeedbackEnabled = false) }

        val saved = store.data.first()
        assertEquals(1, saved[version])
        assertNull(saved[leftBack])
        assertNull(saved[rightBack])
        assertEquals("none", saved[stringPreferencesKey("slot_left_whole_short")])
        assertEquals("none", saved[stringPreferencesKey("slot_right_top_short")])
        assertEquals("home", saved[stringPreferencesKey("slot_bottom_short")])
        val read = repository.read()
        assertFalse(read.slots.edgeInUse(ScreenEdge.LEFT))
        assertFalse(read.slots.edgeInUse(ScreenEdge.RIGHT))
        assertFalse(read.hapticFeedbackEnabled)
    }

    @Test fun `once versioned, a left-over old switch is no longer read`() = runBlocking {
        val repository = repositoryWith(mutablePreferencesOf(version to 1, leftBack to false))
        assertEquals(SwipeSlots(), repository.read().slots)
    }

    @Test fun `a picked action, the split flags and the two new numbers are stored and read back`() = runBlocking {
        val repository = repositoryWith(mutablePreferencesOf())
        val picked = GestureConfiguration().withAction(leftShort, SwipeAction.Notifications)
            .withAction(SlotId.Corner(BottomCorner.RIGHT), SwipeAction.Home)

        repository.update {
            picked.copy(slots = picked.slots.withSplit(ScreenEdge.RIGHT, true), longSwipeDp = 150f, waitMs = 500L)
        }

        val read = repository.read()
        assertEquals(SwipeAction.Notifications, read.actionFor(leftShort))
        assertEquals(SwipeAction.Home, read.actionFor(SlotId.Corner(BottomCorner.RIGHT)))
        assertTrue(read.slots.rightSplit)
        assertEquals(150f, read.longSwipeDp, 0f)
        assertEquals(500L, read.waitMs)
    }

    @Test fun `reset puts every slot back and undo restores it`() {
        val customised = GestureConfiguration().withAction(bottomShort, SwipeAction.None)
            .withAction(leftShort, SwipeAction.PreviousApp)
        val reset = customised.withGestureSettingsOf(GestureConfiguration())
        assertEquals(SwipeSlots(), reset.slots)
        assertEquals(customised.slots, reset.withGestureSettingsOf(customised).slots)
    }

    private fun repositoryWith(preferences: androidx.datastore.preferences.core.Preferences) =
        GestureSettingsRepository(FilesContext(folder.root), MemoryDataStore(preferences))
}
