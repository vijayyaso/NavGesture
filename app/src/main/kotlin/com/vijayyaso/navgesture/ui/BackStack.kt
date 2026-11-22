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

import androidx.compose.runtime.saveable.Saver

/**
 * One opening of a screen. [id] is new for every opening, so a screen opened again is a new
 * entry. [scroll] is where the screen was when another one covered it.
 */
internal data class StackEntry(val screen: Screen, val id: Int, val scroll: Int = 0)

/** The open screens, bottom first. [nextId] is the id the next opening gets. */
internal data class BackStack(val entries: List<StackEntry>, val nextId: Int) {

    init {
        require(entries.isNotEmpty()) { "The stack always keeps a screen" }
    }

    val top: StackEntry get() = entries.last()
    val screens: List<Screen> get() = entries.map { it.screen }
    val canGoBack: Boolean get() = entries.size > 1

    /**
     * Forward: the covered screen keeps [coveredScroll] and the new one starts at its top. A second
     * tap on a row lands before home leaves the screen, so it must not stack a copy.
     */
    fun open(screen: Screen, coveredScroll: Int): BackStack {
        if (top.screen == screen) return this
        val covered = entries.dropLast(1) + top.copy(scroll = coveredScroll)
        return BackStack(covered + StackEntry(screen, nextId), nextId + 1)
    }

    /** Two Backs can land before one redraw, so the last screen is never popped. */
    fun back(): BackStack = if (canGoBack) copy(entries = entries.dropLast(1)) else this

    /** A screen still fading out must not pop or open anything, so only the top one can. */
    fun backFrom(id: Int): BackStack = if (top.id == id) back() else this

    fun openFrom(id: Int, screen: Screen, coveredScroll: Int): BackStack =
        if (top.id == id) open(screen, coveredScroll) else this

    /**
     * Replaces the top entry with [screen], as a new entry with a new id, but only while the
     * entry [id] is still on top, the same guard as [openFrom]. The live practice page hands off
     * to the drawn demo this way, so Back from the drawn demo returns to the screen under it.
     */
    fun swapTopFrom(id: Int, screen: Screen): BackStack {
        if (top.id != id) return this
        return BackStack(entries.dropLast(1) + StackEntry(screen, nextId), nextId + 1)
    }

    /** A whole new stack, as the tile asks for: every screen opens at its top. */
    fun replacedBy(vararg screens: Screen): BackStack =
        BackStack(screens.mapIndexed { index, screen -> StackEntry(screen, nextId + index) }, nextId + screens.size)

    /** Names, ids and scrolls as three lists of one item per screen, then the id counter. */
    fun toSaved(): ArrayList<Any> = arrayListOf(
        ArrayList(entries.map { it.screen.name }),
        ArrayList(entries.map { it.id }),
        ArrayList(entries.map { it.scroll }),
        nextId,
    )

    companion object {
        fun of(vararg screens: Screen): BackStack =
            BackStack(screens.mapIndexed { index, screen -> StackEntry(screen, index) }, screens.size)
    }
}

/** Every opening gets the newest id, so a newer entry arriving means the user went forward. */
internal fun opensForward(from: StackEntry, to: StackEntry): Boolean = to.id > from.id

/**
 * A name saved by an older build may no longer exist; its id and scroll go with it. The stack
 * always keeps a screen, and ids never repeat one already given out.
 */
internal fun backStackOf(
    names: List<String>,
    ids: List<Int> = emptyList(),
    scrolls: List<Int> = emptyList(),
    nextId: Int = 0,
): BackStack {
    var counter = maxOf(nextId, (ids.maxOrNull() ?: -1) + 1)
    val entries = names.mapIndexedNotNull { index, name ->
        val screen = Screen.entries.firstOrNull { it.name == name } ?: return@mapIndexedNotNull null
        StackEntry(screen, ids.getOrNull(index) ?: counter++, scrolls.getOrNull(index) ?: 0)
    }
    if (entries.isEmpty()) return BackStack(listOf(StackEntry(Screen.HOME, counter)), counter + 1)
    return BackStack(entries, counter)
}

/** Reads what [BackStack.toSaved] wrote; anything else restores to home. */
internal fun backStackFromSaved(saved: List<Any?>): BackStack = backStackOf(
    names = (saved.getOrNull(0) as? List<*>).orEmpty().filterIsInstance<String>(),
    ids = (saved.getOrNull(1) as? List<*>).orEmpty().filterIsInstance<Int>(),
    scrolls = (saved.getOrNull(2) as? List<*>).orEmpty().filterIsInstance<Int>(),
    nextId = saved.getOrNull(3) as? Int ?: 0,
)

/**
 * Null is the stack before the first-run decision. Nothing is saved for it, so a restore
 * decides again.
 */
internal val BackStackSaver = Saver<BackStack?, ArrayList<Any>>(
    save = { it?.toSaved() },
    restore = ::backStackFromSaved,
)
