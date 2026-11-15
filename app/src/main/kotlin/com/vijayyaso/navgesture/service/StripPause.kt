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

package com.vijayyaso.navgesture.service

/**
 * The practice demo asks the strips to let touches through while it is in front. A strip is a
 * window above the app, so it would take a practice swipe first and run the real action.
 *
 * Kept in memory only: the service and the app share one process, so a killed process can
 * never leave the strips paused. Main thread only.
 */
internal object StripPause {

    var paused = false
        private set

    private var onChange: (() -> Unit)? = null

    fun set(paused: Boolean) {
        if (this.paused == paused) return
        this.paused = paused
        onChange?.invoke()
    }

    /** The installed overlay listens; null when its strips are gone. */
    fun listen(onChange: (() -> Unit)?) {
        this.onChange = onChange
    }
}
