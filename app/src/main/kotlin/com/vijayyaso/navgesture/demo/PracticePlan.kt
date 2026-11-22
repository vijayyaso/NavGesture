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
import com.vijayyaso.navgesture.gesture.SwipeSlots
import com.vijayyaso.navgesture.gesture.SwipeZone

/** Where the live page draws its moving arrow for a step. */
sealed interface PracticeTarget {
    /** A side swipe that starts in [slot]'s edge and zone. */
    data class Side(val slot: SlotId.Edge) : PracticeTarget

    /** A swipe up from the bottom edge. */
    data object Bottom : PracticeTarget

    /**
     * The app list's swipe: up from the bottom, then a slide sideways. [recentsRight] says which
     * way the recent apps open (the "Swap the two lists" setting turns it round).
     */
    data class AppList(val recentsRight: Boolean) : PracticeTarget

    /** A swipe from the bottom [corner]. */
    data class Corner(val corner: BottomCorner) : PracticeTarget
}

/** Which sides the instruction names: "on the left", "on the right" or "on the left or right". */
enum class PracticeSides { LEFT, RIGHT, BOTH }

/**
 * The live page's steps, from the user's own settings. A step is in the plan only when one of
 * its slots holds an action a touch can reach and this phone can run. Steps match by slot, not
 * by action, because any slot can be remapped. [unsupported] is the set of actions this phone
 * refuses, which the service learns; it comes in as a plain set, so this file stays free of the
 * service.
 */
object PracticePlan {

    /** The steps to practise, in [DemoStep] order. */
    fun steps(configuration: GestureConfiguration, unsupported: Set<SwipeAction>): List<DemoStep> =
        DemoStep.entries.filter { included(it, configuration, unsupported) }

    /**
     * Where to point the arrow. For a side step, one place per side that has the step: the first
     * usable slot in [SwipeSlots.ALL] order whose zone a touch can reach. On a split side the
     * WHOLE slot keeps its default but no touch reaches it, so it is never the target. The bottom
     * steps point at the bottom, and the corner step at each corner that is set. Empty for a step
     * the plan does not hold.
     */
    fun target(
        step: DemoStep,
        configuration: GestureConfiguration,
        unsupported: Set<SwipeAction>,
    ): List<PracticeTarget> = when (step) {
        DemoStep.HOME, DemoStep.RECENTS ->
            if (included(step, configuration, unsupported)) listOf(PracticeTarget.Bottom) else emptyList()
        DemoStep.APP_LIST ->
            if (included(step, configuration, unsupported)) {
                listOf(PracticeTarget.AppList(recentsRight = !configuration.swapAppLists))
            } else {
                emptyList()
            }
        DemoStep.CORNER -> BottomCorner.entries
            .filter { usable(configuration.shortcutFor(it), unsupported) }
            .map { PracticeTarget.Corner(it) }
        DemoStep.BACK, DemoStep.WAIT, DemoStep.LONG -> {
            val kind = sideKind(step)
            SIDES.mapNotNull { edge ->
                SwipeSlots.ALL.firstOrNull { slot ->
                    slot.edge == edge && slot.kind == kind && reachable(configuration, slot) &&
                        usable(configuration.slots[slot], unsupported)
                }?.let { PracticeTarget.Side(it) }
            }
        }
    }

    /** The sides the instruction names, or null for the bottom steps. */
    fun sides(step: DemoStep, configuration: GestureConfiguration, unsupported: Set<SwipeAction>): PracticeSides? {
        val targets = target(step, configuration, unsupported)
        val left = targets.any { it.isOn(ScreenEdge.LEFT) }
        val right = targets.any { it.isOn(ScreenEdge.RIGHT) }
        return when {
            left && right -> PracticeSides.BOTH
            left -> PracticeSides.LEFT
            right -> PracticeSides.RIGHT
            else -> null
        }
    }

    /**
     * Whether "Try the gestures" opens the live page: the service is working, touch exploration is
     * off, the app is not in split screen or a floating window, and the plan has a step.
     */
    fun useLive(working: Boolean, touchExploration: Boolean, multiWindow: Boolean, planEmpty: Boolean): Boolean =
        working && !touchExploration && !multiWindow && !planEmpty

    private fun included(step: DemoStep, configuration: GestureConfiguration, unsupported: Set<SwipeAction>): Boolean =
        when (step) {
            DemoStep.HOME -> usable(configuration.slots[BOTTOM_SHORT], unsupported)
            DemoStep.RECENTS -> usable(configuration.slots[BOTTOM_PAUSE], unsupported)
            DemoStep.APP_LIST -> configuration.edgeAppListEnabled
            DemoStep.CORNER -> BottomCorner.entries.any { usable(configuration.shortcutFor(it), unsupported) }
            DemoStep.BACK, DemoStep.WAIT, DemoStep.LONG -> target(step, configuration, unsupported).isNotEmpty()
        }

    private fun sideKind(step: DemoStep): SwipeKind = when (step) {
        DemoStep.WAIT -> SwipeKind.WAIT
        DemoStep.LONG -> SwipeKind.LONG
        else -> SwipeKind.SHORT
    }

    private fun reachable(configuration: GestureConfiguration, slot: SlotId.Edge): Boolean =
        slot.zone in configuration.slots.zonesOf(slot.edge)

    private fun usable(action: SwipeAction, unsupported: Set<SwipeAction>): Boolean =
        action != SwipeAction.None && action !in unsupported

    private fun PracticeTarget.isOn(edge: ScreenEdge): Boolean = when (this) {
        is PracticeTarget.Side -> slot.edge == edge
        is PracticeTarget.Corner -> when (corner) {
            BottomCorner.LEFT -> edge == ScreenEdge.LEFT
            BottomCorner.RIGHT -> edge == ScreenEdge.RIGHT
        }
        PracticeTarget.Bottom, is PracticeTarget.AppList -> false
    }

    private val SIDES = listOf(ScreenEdge.LEFT, ScreenEdge.RIGHT)
    private val BOTTOM_SHORT = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)
    private val BOTTOM_PAUSE = SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE)
}
