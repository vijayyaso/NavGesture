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

// The Home switch moves at once and stays where the user put it while the service catches up.
// Only the thumb shows the tap; the status text and the card keep the real state.

/** What the Home switch shows: the tap while its request is in flight, else the real state. */
internal fun switchChecked(pending: Boolean?, working: Boolean): Boolean = pending ?: working

/** A pending tap ends as soon as a refresh finds the real state where the tap put it. */
internal fun pendingAfterRefresh(pending: Boolean?, working: Boolean): Boolean? =
    pending?.takeUnless { it == working }

/**
 * Off always sends a request. On sends one only when [turnOnInPlace] flipped the switch here;
 * otherwise the Accessibility screen or the disclosure opens, and nothing is in flight.
 */
internal fun switchSendsRequest(on: Boolean, turnOnInPlace: () -> Boolean): Boolean = !on || turnOnInPlace()

/**
 * One request: the tap is held while [request] runs and [settle] waits for the real state,
 * then cleared on every path, a refusal or a failure included.
 */
internal suspend fun runSwitchRequest(
    target: Boolean,
    setPending: (Boolean?) -> Unit,
    request: suspend () -> Unit,
    settle: suspend () -> Unit,
) {
    setPending(target)
    try {
        request()
        settle()
    } finally {
        setPending(null)
    }
}
