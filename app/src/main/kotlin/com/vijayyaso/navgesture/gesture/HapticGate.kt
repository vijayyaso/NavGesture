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

/**
 * Buzzes once per activation on [edge]. A fire after an arm that ticked is quiet, and a re-arm
 * ticks. Entering a wait gives the overlay's stronger buzz.
 */
class HapticGate(private val edge: ScreenEdge) {

    private var armTicked = false

    // The wait action was pending, so the move that leaves it stays quiet whatever band it finds.
    private var waitShown = false

    fun reset() {
        armTicked = false
        waitShown = false
    }

    fun shouldTick(action: TrackerAction): Boolean = when (action) {
        // A bottom arm with the pause poll stays quiet (Home buzzes on fire); a side arm ticks even
        // while its still clock polls. An arm with nothing to run stays quiet.
        is TrackerAction.Armed -> {
            val pausePoll = edge == ScreenEdge.BOTTOM && action.scheduleHoldMs != null
            (!pausePoll && action.pending != SwipeAction.None).also { if (it) armTicked = true }
        }
        is TrackerAction.Fire -> {
            val ticked = armTicked
            armTicked = false
            !ticked
        }
        is TrackerAction.Pending -> pendingTicks(action).also { if (it) armTicked = true }
        // The corner ticks where its diagonal lands; the shortcut's release then stays quiet.
        is TrackerAction.CornerReady -> true.also { armTicked = true }
        is TrackerAction.Disarmed -> {
            armTicked = false
            waitShown = false
            false
        }
        else -> false
    }

    // POCO's performEnterRecent tick, a tick on entering a long swipe, and the wait's buzz.
    // Falling back to the short swipe, or leaving a wait, is silent.
    private fun pendingTicks(action: TrackerAction.Pending): Boolean {
        val leavingWait = waitShown
        waitShown = action.kind == SwipeKind.WAIT
        return when (action.kind) {
            SwipeKind.PAUSE, SwipeKind.WAIT -> true
            SwipeKind.LONG -> !leavingWait
            SwipeKind.SHORT -> false
        }
    }
}
