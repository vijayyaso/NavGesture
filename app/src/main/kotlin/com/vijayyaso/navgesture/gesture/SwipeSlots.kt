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

/**
 * What every side and bottom swipe does; the corners stay in [GestureConfiguration]. Only
 * slots that differ from the default are kept, so equal settings always compare equal.
 */
class SwipeSlots private constructor(
    private val changed: Map<SlotId.Edge, SwipeAction>,
    val leftSplit: Boolean,
    val rightSplit: Boolean,
) {

    constructor(leftSplit: Boolean = false, rightSplit: Boolean = false) :
        this(emptyMap(), leftSplit, rightSplit)

    operator fun get(slot: SlotId.Edge): SwipeAction = changed[slot] ?: defaultFor(slot)

    /** These slots with [action] in [slot]. A slot no edge has, such as a side PAUSE, is ignored. */
    fun with(slot: SlotId.Edge, action: SwipeAction): SwipeSlots {
        if (slot !in ALL) return this
        val updated = if (action == defaultFor(slot)) changed - slot else changed + (slot to action)
        return SwipeSlots(updated, leftSplit, rightSplit)
    }

    fun split(edge: ScreenEdge): Boolean = when (edge) {
        ScreenEdge.LEFT -> leftSplit
        ScreenEdge.RIGHT -> rightSplit
        ScreenEdge.BOTTOM -> false
    }

    /** The zones a touch on [edge] can start in: the whole edge, or its two halves when split. */
    fun zonesOf(edge: ScreenEdge): List<SwipeZone> =
        if (split(edge)) listOf(SwipeZone.TOP_HALF, SwipeZone.BOTTOM_HALF) else listOf(SwipeZone.WHOLE)

    /** The zone a DOWN at [yPx] starts in; [centreYPx] is the middle of the side strip. */
    fun zoneAt(edge: ScreenEdge, yPx: Float, centreYPx: Float): SwipeZone = when {
        !split(edge) -> SwipeZone.WHOLE
        yPx < centreYPx -> SwipeZone.TOP_HALF
        else -> SwipeZone.BOTTOM_HALF
    }

    /** True when any slot of [zone] on [edge] holds an action, so a swipe there arms. */
    fun zoneInUse(edge: ScreenEdge, zone: SwipeZone): Boolean =
        ALL.any { it.edge == edge && it.zone == zone && this[it] != SwipeAction.None }

    /** True when a touch on [edge] can do anything, so its strip is installed. */
    fun edgeInUse(edge: ScreenEdge): Boolean = zonesOf(edge).any { zoneInUse(edge, it) }

    /** True when a slot of [kind] that a touch can reach holds an action. */
    fun anySet(kind: SwipeKind): Boolean = ScreenEdge.entries.any { edge ->
        zonesOf(edge).any { zone -> this[SlotId.Edge(edge, zone, kind)] != SwipeAction.None }
    }

    /** The short actions a touch on [edge] can reach, each once: one, or one per half when split. */
    fun shortActions(edge: ScreenEdge): List<SwipeAction> =
        zonesOf(edge).map { this[SlotId.Edge(edge, it, SwipeKind.SHORT)] }.distinct()

    /** True when a slot an edge can reach holds [action]. */
    fun holds(action: SwipeAction): Boolean = holds { it == action }

    fun holds(test: (SwipeAction) -> Boolean): Boolean =
        ScreenEdge.entries.any { edge ->
            zonesOf(edge).any { zone -> ALL.any { it.edge == edge && it.zone == zone && test(this[it]) } }
        }

    /** Every slot under its storage key, defaults included. */
    fun encode(): Map<String, String> = ALL.associate { keyOf(it) to this[it].id }

    /** These slots with each one [lookup] knows by key replaced by what it decodes to. */
    fun overriddenBy(lookup: (String) -> String?): SwipeSlots = ALL.fold(this) { slots, slot ->
        val stored = lookup(keyOf(slot)) ?: return@fold slots
        slots.with(slot, SwipeAction.decode(stored))
    }

    fun withSplit(edge: ScreenEdge, on: Boolean): SwipeSlots = when (edge) {
        ScreenEdge.LEFT -> SwipeSlots(changed, on, rightSplit)
        ScreenEdge.RIGHT -> SwipeSlots(changed, leftSplit, on)
        ScreenEdge.BOTTOM -> this
    }

    override fun equals(other: Any?): Boolean = other is SwipeSlots && other.changed == changed &&
        other.leftSplit == leftSplit && other.rightSplit == rightSplit

    override fun hashCode(): Int = (changed.hashCode() * 31 + leftSplit.hashCode()) * 31 + rightSplit.hashCode()

    override fun toString(): String = "SwipeSlots(changed=$changed, leftSplit=$leftSplit, rightSplit=$rightSplit)"

    companion object {
        /** Every slot that is stored. Sides have a short, long and wait slot per zone. */
        val ALL: List<SlotId.Edge> = buildList {
            for (edge in listOf(ScreenEdge.LEFT, ScreenEdge.RIGHT)) {
                for (zone in SwipeZone.entries) {
                    for (kind in listOf(SwipeKind.SHORT, SwipeKind.LONG, SwipeKind.WAIT)) {
                        add(SlotId.Edge(edge, zone, kind))
                    }
                }
            }
            add(SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT))
            add(SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE))
        }

        /** Side short = Back, bottom short = Home, bottom pause = Recents; the rest are empty. */
        fun defaultFor(slot: SlotId.Edge): SwipeAction = when {
            slot.edge == ScreenEdge.BOTTOM && slot.kind == SwipeKind.SHORT -> SwipeAction.Home
            slot.edge == ScreenEdge.BOTTOM && slot.kind == SwipeKind.PAUSE -> SwipeAction.Recents
            slot.edge != ScreenEdge.BOTTOM && slot.kind == SwipeKind.SHORT -> SwipeAction.Back
            else -> SwipeAction.None
        }

        /** `slot_left_whole_short`, ..., `slot_bottom_short`, `slot_bottom_pause`. */
        fun keyOf(slot: SlotId.Edge): String {
            val kind = slot.kind.name.lowercase()
            if (slot.edge == ScreenEdge.BOTTOM) return "slot_bottom_$kind"
            val zone = when (slot.zone) {
                SwipeZone.WHOLE -> "whole"
                SwipeZone.TOP_HALF -> "top"
                SwipeZone.BOTTOM_HALF -> "bottom"
            }
            return "slot_${slot.edge.name.lowercase()}_${zone}_$kind"
        }

        /** Reads every slot by its key; a missing key is the default, an unknown id is None. */
        fun decode(leftSplit: Boolean, rightSplit: Boolean, lookup: (String) -> String?): SwipeSlots =
            SwipeSlots(leftSplit, rightSplit).overriddenBy(lookup)

        /** An install from before slots: a side whose Back switch was off has no short swipe. */
        fun migrated(leftBackEnabled: Boolean, rightBackEnabled: Boolean): SwipeSlots {
            var slots = SwipeSlots()
            for ((edge, enabled) in listOf(ScreenEdge.LEFT to leftBackEnabled, ScreenEdge.RIGHT to rightBackEnabled)) {
                if (enabled) continue
                for (zone in SwipeZone.entries) {
                    slots = slots.with(SlotId.Edge(edge, zone, SwipeKind.SHORT), SwipeAction.None)
                }
            }
            return slots
        }
    }
}

/** A setting that leaves the user without a way Home or Back. It warns and never blocks. */
enum class SwipeWarning { NO_HOME, NO_BACK }

/** True when a swipe an edge can reach, or a corner, runs [action]. */
fun GestureConfiguration.gives(action: SwipeAction): Boolean =
    slots.holds(action) || leftCornerShortcut == action || rightCornerShortcut == action

val GestureConfiguration.givesHome: Boolean get() = gives(SwipeAction.Home)

val GestureConfiguration.givesBack: Boolean get() = gives(SwipeAction.Back)

/** The warnings Gesture settings and its home tile show, Home first. */
fun GestureConfiguration.swipeWarnings(): List<SwipeWarning> = buildList {
    if (!givesHome) add(SwipeWarning.NO_HOME)
    if (!givesBack) add(SwipeWarning.NO_BACK)
}
