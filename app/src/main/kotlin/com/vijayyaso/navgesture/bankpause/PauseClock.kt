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


package com.vijayyaso.navgesture.bankpause

/** One app coming to the front, from usage events: its package and when. */
internal data class FrontApp(val packageName: String, val atMs: Long)

/**
 * Decides, from the apps that came to the front, when a bank pause may end. Pure, so the
 * watcher and the service share it and unit tests cover it.
 *
 * The pause starts with the bank app in front. Any app outside the picked set - the home
 * screen, NavGesture itself, another app - starts the away time; a picked app coming back
 * cancels it. A bank that closed itself on the first open can be opened again within
 * [AWAY_MS] and finds the pause still on.
 */
internal object PauseClock {

    /** How long the user must stay out of every picked app before gestures come back. */
    const val AWAY_MS = 8_000L

    /** Read failures in a row that end the pause anyway, so a broken read never strands it. */
    const val MAX_READ_FAILURES = 5

    /** True once the front app has been outside [picked] for [awayMs]. Events in any order. */
    fun shouldResume(
        picked: Set<String>,
        fronts: List<FrontApp>,
        nowMs: Long,
        awayMs: Long = AWAY_MS,
    ): Boolean {
        val since = awaySince(picked, fronts) ?: return false
        return nowMs - since >= awayMs
    }

    /** True if any app outside [picked] came to the front, which ends a Resume-now hold. */
    fun leftPicked(picked: Set<String>, fronts: List<FrontApp>): Boolean =
        fronts.any { it.packageName !in picked }

    private fun awaySince(picked: Set<String>, fronts: List<FrontApp>): Long? {
        var since: Long? = null
        for (front in fronts.sortedBy { it.atMs }) {
            since = when {
                front.packageName in picked -> null
                since == null -> front.atMs
                else -> since
            }
        }
        return since
    }
}
