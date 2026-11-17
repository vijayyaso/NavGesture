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

import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePlanTest {

    private val defaults = GestureConfiguration()

    private fun slot(edge: ScreenEdge, kind: SwipeKind, zone: SwipeZone = SwipeZone.WHOLE) = SlotId.Edge(edge, zone, kind)

    private fun GestureConfiguration.with(slot: SlotId, action: SwipeAction) = withAction(slot, action)

    private fun GestureConfiguration.split(edge: ScreenEdge) = copy(slots = slots.withSplit(edge, true))

    private fun steps(configuration: GestureConfiguration, unsupported: Set<SwipeAction> = emptySet()) =
        PracticePlan.steps(configuration, unsupported)

    private val noBack = defaults
        .with(slot(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.None)
        .with(slot(ScreenEdge.RIGHT, SwipeKind.SHORT), SwipeAction.None)

    @Test fun `the defaults practise Back, Home, Recents and the app list`() {
        assertEquals(listOf(DemoStep.BACK, DemoStep.HOME, DemoStep.RECENTS, DemoStep.APP_LIST), steps(defaults))
    }

    @Test fun `each step is dropped when its slots all hold nothing`() {
        assertFalse(DemoStep.BACK in steps(noBack))
        assertFalse(DemoStep.HOME in steps(defaults.with(slot(ScreenEdge.BOTTOM, SwipeKind.SHORT), SwipeAction.None)))
        assertFalse(DemoStep.RECENTS in steps(defaults.with(slot(ScreenEdge.BOTTOM, SwipeKind.PAUSE), SwipeAction.None)))
        assertFalse(DemoStep.CORNER in steps(defaults))
        assertFalse(DemoStep.WAIT in steps(defaults))
        assertFalse(DemoStep.LONG in steps(defaults))
    }

    @Test fun `each step is included when one of its slots holds an action`() {
        assertTrue(DemoStep.BACK in steps(noBack.with(slot(ScreenEdge.RIGHT, SwipeKind.SHORT), SwipeAction.Home)))
        assertTrue(DemoStep.CORNER in steps(defaults.copy(rightCornerShortcut = SwipeAction.Notifications)))
        assertTrue(DemoStep.WAIT in steps(defaults.with(slot(ScreenEdge.RIGHT, SwipeKind.WAIT), SwipeAction.Screenshot)))
        val youtube = SwipeAction.OpenApp("com.google.android.youtube")
        assertEquals(
            listOf(DemoStep.BACK, DemoStep.HOME, DemoStep.RECENTS, DemoStep.APP_LIST, DemoStep.LONG),
            steps(defaults.with(slot(ScreenEdge.LEFT, SwipeKind.LONG), youtube)),
        )
    }

    @Test fun `a slot holding an action this phone refuses counts as unset`() {
        val split = setOf<SwipeAction>(SwipeAction.SplitScreen)
        val longSplit = defaults.with(slot(ScreenEdge.LEFT, SwipeKind.LONG), SwipeAction.SplitScreen)
        assertTrue(DemoStep.LONG in steps(longSplit))
        assertFalse(DemoStep.LONG in steps(longSplit, split))
        val cornerSplit = defaults.copy(leftCornerShortcut = SwipeAction.SplitScreen)
        assertFalse(DemoStep.CORNER in steps(cornerSplit, split))
        assertTrue(PracticePlan.target(DemoStep.LONG, longSplit, split).isEmpty())
    }

    @Test fun `a wait only in the top half of a split side is practised there`() {
        val topWait = slot(ScreenEdge.LEFT, SwipeKind.WAIT, SwipeZone.TOP_HALF)
        val configuration = defaults.split(ScreenEdge.LEFT).with(topWait, SwipeAction.Screenshot)
        assertTrue(DemoStep.WAIT in steps(configuration))
        assertEquals(listOf(PracticeTarget.Side(topWait)), PracticePlan.target(DemoStep.WAIT, configuration, emptySet()))
    }

    @Test fun `a wait set only on the whole of a split side cannot be reached`() {
        val configuration = defaults.split(ScreenEdge.LEFT).with(slot(ScreenEdge.LEFT, SwipeKind.WAIT), SwipeAction.Screenshot)
        assertFalse(DemoStep.WAIT in steps(configuration))
    }

    @Test fun `on a split side the Back target is a half, never the whole side`() {
        val configuration = defaults
            .split(ScreenEdge.LEFT)
            .with(slot(ScreenEdge.LEFT, SwipeKind.SHORT, SwipeZone.TOP_HALF), SwipeAction.Home)
            .with(slot(ScreenEdge.LEFT, SwipeKind.SHORT, SwipeZone.BOTTOM_HALF), SwipeAction.Home)
            .with(slot(ScreenEdge.RIGHT, SwipeKind.SHORT), SwipeAction.None)
        assertEquals(SwipeAction.Back, configuration.slots[slot(ScreenEdge.LEFT, SwipeKind.SHORT)])
        assertEquals(
            listOf(PracticeTarget.Side(slot(ScreenEdge.LEFT, SwipeKind.SHORT, SwipeZone.TOP_HALF))),
            PracticePlan.target(DemoStep.BACK, configuration, emptySet()),
        )
    }

    @Test fun `the app list step follows the app list switch`() {
        assertTrue(DemoStep.APP_LIST in steps(defaults))
        assertFalse(DemoStep.APP_LIST in steps(defaults.copy(edgeAppListEnabled = false)))
    }

    @Test fun `targets and sides for left only, right only and both`() {
        val leftOnly = defaults.with(slot(ScreenEdge.RIGHT, SwipeKind.SHORT), SwipeAction.None)
        assertEquals(
            listOf(PracticeTarget.Side(slot(ScreenEdge.LEFT, SwipeKind.SHORT))),
            PracticePlan.target(DemoStep.BACK, leftOnly, emptySet()),
        )
        assertEquals(PracticeSides.LEFT, PracticePlan.sides(DemoStep.BACK, leftOnly, emptySet()))
        val rightOnly = defaults.with(slot(ScreenEdge.LEFT, SwipeKind.SHORT), SwipeAction.None)
        assertEquals(
            listOf(PracticeTarget.Side(slot(ScreenEdge.RIGHT, SwipeKind.SHORT))),
            PracticePlan.target(DemoStep.BACK, rightOnly, emptySet()),
        )
        assertEquals(PracticeSides.RIGHT, PracticePlan.sides(DemoStep.BACK, rightOnly, emptySet()))
        assertEquals(
            listOf(
                PracticeTarget.Side(slot(ScreenEdge.LEFT, SwipeKind.SHORT)),
                PracticeTarget.Side(slot(ScreenEdge.RIGHT, SwipeKind.SHORT)),
            ),
            PracticePlan.target(DemoStep.BACK, defaults, emptySet()),
        )
        assertEquals(PracticeSides.BOTH, PracticePlan.sides(DemoStep.BACK, defaults, emptySet()))
    }

    @Test fun `the bottom steps point at the bottom and the corner step at the set corners`() {
        listOf(DemoStep.HOME, DemoStep.RECENTS, DemoStep.APP_LIST).forEach {
            assertEquals(listOf(PracticeTarget.Bottom), PracticePlan.target(it, defaults, emptySet()))
            assertNull(PracticePlan.sides(it, defaults, emptySet()))
        }
        val rightCorner = defaults.copy(rightCornerShortcut = SwipeAction.Notifications)
        assertEquals(
            listOf(PracticeTarget.Corner(BottomCorner.RIGHT)),
            PracticePlan.target(DemoStep.CORNER, rightCorner, emptySet()),
        )
        assertEquals(PracticeSides.RIGHT, PracticePlan.sides(DemoStep.CORNER, rightCorner, emptySet()))
        val both = rightCorner.copy(leftCornerShortcut = SwipeAction.Screenshot)
        assertEquals(PracticeSides.BOTH, PracticePlan.sides(DemoStep.CORNER, both, emptySet()))
    }

    @Test fun `a step the plan does not hold has no target`() {
        assertTrue(PracticePlan.target(DemoStep.WAIT, defaults, emptySet()).isEmpty())
        assertTrue(PracticePlan.target(DemoStep.CORNER, defaults, emptySet()).isEmpty())
        assertTrue(PracticePlan.target(DemoStep.APP_LIST, defaults.copy(edgeAppListEnabled = false), emptySet()).isEmpty())
        assertNull(PracticePlan.sides(DemoStep.WAIT, defaults, emptySet()))
    }

    @Test fun `the live page opens only when every condition holds`() {
        assertTrue(PracticePlan.useLive(working = true, touchExploration = false, multiWindow = false, planEmpty = false))
        assertFalse(PracticePlan.useLive(working = false, touchExploration = false, multiWindow = false, planEmpty = false))
        assertFalse(PracticePlan.useLive(working = true, touchExploration = true, multiWindow = false, planEmpty = false))
        assertFalse(PracticePlan.useLive(working = true, touchExploration = false, multiWindow = true, planEmpty = false))
        assertFalse(PracticePlan.useLive(working = true, touchExploration = false, multiWindow = false, planEmpty = true))
    }
}
