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

package com.vijayyaso.navgesture.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeSlots
import com.vijayyaso.navgesture.gesture.SwipeZone

/** One row on an edge page: the slot it sets, its picker page and its name. */
private class SlotRow(val slot: SlotId.Edge, val picker: Screen, @param:StringRes val title: Int)

/** The swipes each edge page offers, in the order they are listed. */
private fun rowsOf(edge: ScreenEdge): List<SlotRow> = when (edge) {
    ScreenEdge.LEFT -> listOf(
        SlotRow(SlotId.Edge(edge, SwipeZone.WHOLE, SwipeKind.SHORT), Screen.LEFT_SWIPE_IN, R.string.slot_swipe_in),
    )
    ScreenEdge.RIGHT -> listOf(
        SlotRow(SlotId.Edge(edge, SwipeZone.WHOLE, SwipeKind.SHORT), Screen.RIGHT_SWIPE_IN, R.string.slot_swipe_in),
    )
    ScreenEdge.BOTTOM -> listOf(
        SlotRow(SlotId.Edge(edge, SwipeZone.WHOLE, SwipeKind.SHORT), Screen.BOTTOM_SWIPE_UP, R.string.slot_swipe_up),
        SlotRow(
            SlotId.Edge(edge, SwipeZone.WHOLE, SwipeKind.PAUSE),
            Screen.BOTTOM_SWIPE_UP_PAUSE,
            R.string.slot_swipe_up_pause,
        ),
    )
}

/** The slot a picker page sets, or null for any other screen. Corners included. */
internal val Screen.pickerSlot: SlotId?
    get() = when (this) {
        Screen.LEFT_CORNER -> SlotId.Corner(BottomCorner.LEFT)
        Screen.RIGHT_CORNER -> SlotId.Corner(BottomCorner.RIGHT)
        else -> ScreenEdge.entries.flatMap(::rowsOf).firstOrNull { it.picker == this }?.slot
    }

/** One edge's page: a row per swipe, each showing its action and opening the picker. */
@Composable
internal fun SwipeEdgeScreen(edge: ScreenEdge, model: AppModel, onOpen: (Screen) -> Unit) {
    val slots = model.configuration.slots
    SettingsGroup {
        rowsOf(edge).forEach { row ->
            SwipeActionRow(
                title = stringResource(row.title),
                action = slots[row.slot],
                tag = TestTags.slotRow(row.slot),
            ) { onOpen(row.picker) }
        }
    }
}

/** The one-line summary of an edge's actions, as its row on Gesture settings shows it. */
@Composable
internal fun edgeSummary(edge: ScreenEdge, slots: SwipeSlots): String =
    actionsName(rowsOf(edge).map { slots[it.slot] }) ?: shortcutLabel(SwipeAction.None)

/** "Home and Recents": the actions that are set, joined; null when none is. */
@Composable
internal fun actionsName(actions: List<SwipeAction>): String? {
    val names = actions.filter { it != SwipeAction.None }.map { shortcutLabel(it) }
    return when (names.size) {
        0 -> null
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ").let { stringResource(R.string.pair_and, it, names.last()) }
    }
}

/** An edge's row on Gesture settings: its page, test tag and name. */
internal class EdgeRow(val edge: ScreenEdge, val page: Screen, val tag: String, @param:StringRes val title: Int)

/** The Gesture settings row of each edge, in order. */
internal val EDGE_ROWS: List<EdgeRow> = listOf(
    EdgeRow(ScreenEdge.LEFT, Screen.LEFT_EDGE, TestTags.ROW_LEFT_EDGE, R.string.label_left_edge_back),
    EdgeRow(ScreenEdge.RIGHT, Screen.RIGHT_EDGE, TestTags.ROW_RIGHT_EDGE, R.string.label_right_edge_back),
    EdgeRow(ScreenEdge.BOTTOM, Screen.BOTTOM_EDGE, TestTags.ROW_BOTTOM_EDGE, R.string.label_bottom_edge),
)
