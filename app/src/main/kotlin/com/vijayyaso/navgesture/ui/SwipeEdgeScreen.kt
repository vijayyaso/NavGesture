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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
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

/** A side swipe's row; a half's row is named like the whole edge's, under its half's heading. */
private fun sideRow(edge: ScreenEdge, zone: SwipeZone, kind: SwipeKind, picker: Screen) = SlotRow(
    SlotId.Edge(edge, zone, kind),
    picker,
    when (kind) {
        SwipeKind.LONG -> R.string.slot_swipe_far_in
        SwipeKind.WAIT -> R.string.slot_swipe_in_wait
        else -> R.string.slot_swipe_in
    },
)

/** Every row each edge page can show, in order: a side lists its whole edge, then each half. */
private fun rowsOf(edge: ScreenEdge): List<SlotRow> = when (edge) {
    ScreenEdge.LEFT -> listOf(
        sideRow(edge, SwipeZone.WHOLE, SwipeKind.SHORT, Screen.LEFT_SWIPE_IN),
        sideRow(edge, SwipeZone.WHOLE, SwipeKind.LONG, Screen.LEFT_SWIPE_FAR_IN),
        sideRow(edge, SwipeZone.WHOLE, SwipeKind.WAIT, Screen.LEFT_SWIPE_IN_WAIT),
        sideRow(edge, SwipeZone.TOP_HALF, SwipeKind.SHORT, Screen.LEFT_TOP_SWIPE_IN),
        sideRow(edge, SwipeZone.TOP_HALF, SwipeKind.LONG, Screen.LEFT_TOP_SWIPE_FAR_IN),
        sideRow(edge, SwipeZone.TOP_HALF, SwipeKind.WAIT, Screen.LEFT_TOP_SWIPE_IN_WAIT),
        sideRow(edge, SwipeZone.BOTTOM_HALF, SwipeKind.SHORT, Screen.LEFT_BOTTOM_SWIPE_IN),
        sideRow(edge, SwipeZone.BOTTOM_HALF, SwipeKind.LONG, Screen.LEFT_BOTTOM_SWIPE_FAR_IN),
        sideRow(edge, SwipeZone.BOTTOM_HALF, SwipeKind.WAIT, Screen.LEFT_BOTTOM_SWIPE_IN_WAIT),
    )
    ScreenEdge.RIGHT -> listOf(
        sideRow(edge, SwipeZone.WHOLE, SwipeKind.SHORT, Screen.RIGHT_SWIPE_IN),
        sideRow(edge, SwipeZone.WHOLE, SwipeKind.LONG, Screen.RIGHT_SWIPE_FAR_IN),
        sideRow(edge, SwipeZone.WHOLE, SwipeKind.WAIT, Screen.RIGHT_SWIPE_IN_WAIT),
        sideRow(edge, SwipeZone.TOP_HALF, SwipeKind.SHORT, Screen.RIGHT_TOP_SWIPE_IN),
        sideRow(edge, SwipeZone.TOP_HALF, SwipeKind.LONG, Screen.RIGHT_TOP_SWIPE_FAR_IN),
        sideRow(edge, SwipeZone.TOP_HALF, SwipeKind.WAIT, Screen.RIGHT_TOP_SWIPE_IN_WAIT),
        sideRow(edge, SwipeZone.BOTTOM_HALF, SwipeKind.SHORT, Screen.RIGHT_BOTTOM_SWIPE_IN),
        sideRow(edge, SwipeZone.BOTTOM_HALF, SwipeKind.LONG, Screen.RIGHT_BOTTOM_SWIPE_FAR_IN),
        sideRow(edge, SwipeZone.BOTTOM_HALF, SwipeKind.WAIT, Screen.RIGHT_BOTTOM_SWIPE_IN_WAIT),
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

/** The rows a touch on [edge] can reach now: the whole edge's, or both halves' when split. */
private fun reachableRows(edge: ScreenEdge, slots: SwipeSlots): List<SlotRow> =
    rowsOf(edge).filter { it.slot.zone in slots.zonesOf(edge) }

/** The slot a picker page sets, or null for any other screen. Corners included. */
internal val Screen.pickerSlot: SlotId?
    get() = when (this) {
        Screen.LEFT_CORNER -> SlotId.Corner(BottomCorner.LEFT)
        Screen.RIGHT_CORNER -> SlotId.Corner(BottomCorner.RIGHT)
        else -> ScreenEdge.entries.flatMap(::rowsOf).firstOrNull { it.picker == this }?.slot
    }

/**
 * One edge's page: a row per swipe, each showing its action and opening the picker. A side
 * also has the split switch; split, its rows show twice, under Top half and Bottom half.
 */
@Composable
internal fun SwipeEdgeScreen(edge: ScreenEdge, model: AppModel, onOpen: (Screen) -> Unit) {
    val slots = model.configuration.slots
    if (edge == ScreenEdge.BOTTOM) {
        ZoneRows(edge, SwipeZone.WHOLE, null, slots, onOpen)
        return
    }
    val split = slots.split(edge)
    SettingsGroup {
        ToggleRow(
            label = stringResource(R.string.label_split_edge),
            checked = split,
            supporting = stringResource(R.string.label_split_edge_body),
            tag = if (edge == ScreenEdge.LEFT) TestTags.SWITCH_SPLIT_LEFT else TestTags.SWITCH_SPLIT_RIGHT,
        ) { on -> model.update { it.copy(slots = it.slots.withSplit(edge, on)) } }
    }
    // The halves keep their own actions, so switching back and forth loses nothing.
    AnimatedVisibility(visible = !split, enter = BlockEnter, exit = BlockExit) {
        Column { ZoneRows(edge, SwipeZone.WHOLE, null, slots, onOpen) }
    }
    AnimatedVisibility(visible = split, enter = BlockEnter, exit = BlockExit) {
        Column {
            ZoneRows(edge, SwipeZone.TOP_HALF, R.string.section_top_half, slots, onOpen)
            ZoneRows(edge, SwipeZone.BOTTOM_HALF, R.string.section_bottom_half, slots, onOpen)
        }
    }
}

/** One zone's rows in their own card, under [heading] when there is one. */
@Composable
private fun ZoneRows(
    edge: ScreenEdge,
    zone: SwipeZone,
    @StringRes heading: Int?,
    slots: SwipeSlots,
    onOpen: (Screen) -> Unit,
) {
    SettingsGroup(title = heading?.let { stringResource(it) }) {
        rowsOf(edge).filter { it.slot.zone == zone }.forEach { row ->
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
    actionsName(reachableRows(edge, slots).map { slots[it.slot] }.distinct()) ?: shortcutLabel(SwipeAction.None)

/**
 * A side named by its short swipe's action, or each half's when split; null when the side has
 * no strip. The status card and the touch-area adjuster label each side with it.
 */
@Composable
internal fun sideName(slots: SwipeSlots, edge: ScreenEdge): String? {
    if (!slots.edgeInUse(edge)) return null
    return actionsName(slots.shortActions(edge)) ?: shortcutLabel(SwipeAction.None)
}

/** "Home and Recents": the actions that are set, joined; null when none is. */
@Composable
internal fun actionsName(actions: List<SwipeAction>): String? {
    val names = actions.filter { it != SwipeAction.None }.distinct().map { shortcutLabel(it) }
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
