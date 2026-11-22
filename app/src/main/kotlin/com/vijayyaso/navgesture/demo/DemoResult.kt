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

import com.vijayyaso.navgesture.gesture.GestureAction
import com.vijayyaso.navgesture.gesture.RejectionReason

// Both demos use these types: the drawn phone gets them from DemoEngine, the live practice page
// from the service. So they live apart from the engine, and the service never needs it.

/** A whole practice gesture. Nothing real runs for it; the page only shows it. */
sealed interface DemoResult {
    /** A swipe that the real service would run [run] for. */
    data class Ran(val run: GestureAction.Run) : DemoResult

    /** The app list, lifted on its app [index]. */
    data class AppPicked(val index: Int) : DemoResult

    /** A drag to the left during the rise, which opens the favourite apps for real. */
    data object FavouritesOpened : DemoResult

    /** A long press on the bottom edge that would start Circle to Search. Only the live page gets it. */
    data object CircleToSearch : DemoResult
}

/** Why a practice touch was no gesture. */
sealed interface DemoMiss {
    /** It started away from every drawn edge. */
    data object NotOnEdge : DemoMiss

    /** A second finger went down, or the app list was closed by a slide back down. */
    data object Cancelled : DemoMiss

    /** A side swipe released back inside its trigger distance. */
    data object PulledBack : DemoMiss

    /** The recogniser's own reason. */
    data class Rejected(val reason: RejectionReason) : DemoMiss
}
