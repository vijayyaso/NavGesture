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

import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind

/** The seven gestures the practice demo teaches, in the order it teaches them. */
enum class DemoStep {
    BACK,
    HOME,
    RECENTS,
    APP_LIST,
    CORNER,
    WAIT,
    LONG,
    ;

    /** 1 to 7, as "Step n of 7" shows it. */
    val number: Int get() = ordinal + 1

    /** The step after this one, or null after the last. */
    val next: DemoStep? get() = entries.getOrNull(ordinal + 1)

    /** Whether [result] is the gesture this step teaches. */
    fun passes(result: DemoResult): Boolean = when (this) {
        BACK -> result.ran(SwipeAction.Back)
        HOME -> result.ran(SwipeAction.Home)
        RECENTS -> result.ran(SwipeAction.Recents)
        APP_LIST -> result is DemoResult.AppPicked
        CORNER -> result is DemoResult.Ran && result.run.slot is SlotId.Corner
        WAIT -> result is DemoResult.Ran && (result.run.slot as? SlotId.Edge)?.kind == SwipeKind.WAIT
        LONG -> result.ran(SwipeAction.Torch)
    }

    private fun DemoResult.ran(action: SwipeAction): Boolean = this is DemoResult.Ran && run.action == action
}

/** What the line under the instruction explains after a try that did not pass. */
sealed interface DemoHint {
    /** A whole gesture, but not this step's. */
    data class Other(val result: DemoResult) : DemoHint

    /** No gesture at all. */
    data class Miss(val reason: DemoMiss) : DemoHint
}

/**
 * Where the user is in the demo. [step] is null once the last step is done. [celebrating] is
 * the passing result while its effect plays; the screen then calls [advance].
 */
data class DemoProgress(
    val step: DemoStep? = DemoStep.BACK,
    val tries: Int = 0,
    val hint: DemoHint? = null,
    val celebrating: DemoResult? = null,
) {
    val finished: Boolean get() = step == null

    /** Two tries that did not pass, so no one is stuck on a step. */
    val canSkipStep: Boolean get() = tries >= SKIP_AFTER_TRIES

    /**
     * Only a whole gesture or a miss changes the progress. While a pass is celebrated the screen
     * sends no touches, and anything that still arrives is ignored.
     */
    fun after(event: DemoEvent): DemoProgress {
        val current = step ?: return this
        if (celebrating != null) return this
        return when (event) {
            is DemoEvent.Performed -> if (current.passes(event.result)) {
                copy(hint = null, celebrating = event.result)
            } else {
                copy(tries = tries + 1, hint = DemoHint.Other(event.result))
            }
            is DemoEvent.Missed -> copy(tries = tries + 1, hint = DemoHint.Miss(event.reason))
            is DemoEvent.Ready, DemoEvent.Unready, is DemoEvent.ListOpened, is DemoEvent.ListMoved -> this
        }
    }

    /** The next step with no tries and no hint; after the last step, finished. Skip uses it too. */
    fun advance(): DemoProgress = DemoProgress(step = step?.next)

    companion object {
        const val SKIP_AFTER_TRIES = 2
    }
}
