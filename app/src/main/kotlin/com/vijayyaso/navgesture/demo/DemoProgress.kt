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

import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone

/**
 * The seven gestures the practice demo teaches, in the order it teaches them. Both demos save a
 * step by its ordinal, and the live page hands its step to the drawn demo by ordinal, so the
 * order of these entries must never change.
 */
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

    /**
     * Whether [result] is the gesture this step teaches. It matches the slot the swipe ended in,
     * not the action, because any slot can be remapped: a left short swipe that opens an app is
     * still the Back step's swipe.
     */
    fun passes(result: DemoResult): Boolean {
        if (this == APP_LIST) return result is DemoResult.AppPicked
        val slot = (result as? DemoResult.Ran)?.run?.slot ?: return false
        val edge = slot as? SlotId.Edge
        return when (this) {
            BACK -> edge != null && edge.edge != ScreenEdge.BOTTOM && edge.kind == SwipeKind.SHORT
            HOME -> edge == SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)
            RECENTS -> edge == SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE)
            APP_LIST -> false
            CORNER -> slot is SlotId.Corner
            WAIT -> edge?.kind == SwipeKind.WAIT
            LONG -> edge?.kind == SwipeKind.LONG
        }
    }
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
 * the passing result while its effect plays; the screen then calls [advance]. [plan] is the
 * steps to go through, in [DemoStep] order: every step for the drawn demo, the steps the user's
 * own settings hold for the live page.
 */
data class DemoProgress(
    val step: DemoStep? = DemoStep.BACK,
    val tries: Int = 0,
    val hint: DemoHint? = null,
    val celebrating: DemoResult? = null,
    val plan: List<DemoStep> = DemoStep.entries,
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

    /**
     * The next step of the plan with no tries and no hint; after the last step, finished. Skip
     * uses it too.
     */
    fun advance(): DemoProgress = DemoProgress(step = step?.let { nextInPlan(plan, it) }, plan = plan)

    /**
     * This progress under a new [plan], as when a settings change rebuilds it. The current step
     * stays if the new plan still has it, with its tries and hint; else the next step the plan
     * has takes its place, from the start. A finished progress stays finished.
     */
    fun withPlan(plan: List<DemoStep>): DemoProgress {
        if (plan == this.plan) return this
        val current = step ?: return copy(plan = plan)
        if (current in plan) return copy(plan = plan)
        return DemoProgress(step = plan.firstOrNull { it.ordinal > current.ordinal }, plan = plan)
    }

    companion object {
        const val SKIP_AFTER_TRIES = 2

        /** The first step of [plan], or finished at once for an empty plan. */
        fun start(plan: List<DemoStep>): DemoProgress = DemoProgress(step = plan.firstOrNull(), plan = plan)

        // The plan is in DemoStep order, so the next step is the first one after [step], which
        // also works for a step the plan no longer has.
        private fun nextInPlan(plan: List<DemoStep>, step: DemoStep): DemoStep? =
            plan.firstOrNull { it.ordinal > step.ordinal }
    }
}
