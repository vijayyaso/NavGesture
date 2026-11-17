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

import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.demo.DemoHint
import com.vijayyaso.navgesture.demo.DemoMiss
import com.vijayyaso.navgesture.demo.DemoResult
import com.vijayyaso.navgesture.demo.DemoStep
import com.vijayyaso.navgesture.demo.PracticeSides
import com.vijayyaso.navgesture.gesture.RejectionReason
import com.vijayyaso.navgesture.gesture.SwipeAction

// The words both demos share, and the live page's own words beside them. The live page names
// steps by the swipe, not the action, because a remapped slot makes "Back" the wrong name.

/** A pass shows its effect this long before the next step. */
internal const val EFFECT_SHOWN_MS = 900L

/** Whether a screen reader drives the screen by touch, kept current while the demo shows. */
@Composable
internal fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/** The hint for a try that did not pass, by what happened and the step it happened in. */
@Composable
internal fun hintText(hint: DemoHint, step: DemoStep): String = when (hint) {
    is DemoHint.Other -> {
        val action = (hint.result as? DemoResult.Ran)?.run?.action
        when {
            step == DemoStep.RECENTS && action == SwipeAction.Home -> stringResource(R.string.demo_hint_recents_home)
            step == DemoStep.WAIT && action == SwipeAction.Back -> stringResource(R.string.demo_hint_wait_back)
            step == DemoStep.LONG && action == SwipeAction.Back -> stringResource(R.string.demo_hint_long_back)
            step == DemoStep.APP_LIST && hint.result == DemoResult.FavouritesOpened ->
                stringResource(R.string.demo_hint_favourites)
            else -> stringResource(R.string.demo_hint_other, resultName(hint.result))
        }
    }
    is DemoHint.Miss -> when (val reason = hint.reason) {
        DemoMiss.NotOnEdge -> stringResource(
            if (step == DemoStep.CORNER) R.string.demo_hint_not_in_corner else R.string.demo_hint_not_on_edge,
        )
        DemoMiss.Cancelled -> stringResource(
            if (step == DemoStep.APP_LIST) R.string.demo_hint_list_closed else R.string.demo_hint_cancelled,
        )
        DemoMiss.PulledBack -> stringResource(R.string.demo_hint_pulled_back)
        is DemoMiss.Rejected -> stringResource(
            when (reason.reason) {
                RejectionReason.NO_MOVEMENT -> R.string.demo_hint_tap
                RejectionReason.INSUFFICIENT_MOVEMENT -> R.string.demo_hint_short
                RejectionReason.WRONG_DIRECTION -> R.string.demo_hint_wrong_way
                RejectionReason.TOO_SLOW_TO_ARM -> R.string.demo_hint_slow
                RejectionReason.OFF_AXIS -> R.string.demo_hint_slanted
                RejectionReason.SLOT_EMPTY -> R.string.demo_hint_nothing
            },
        )
    }
}

@Composable
internal fun resultName(result: DemoResult): String = when (result) {
    is DemoResult.Ran -> shortcutLabel(result.run.action)
    is DemoResult.AppPicked -> stringResource(R.string.demo_name_app_list)
    DemoResult.FavouritesOpened -> stringResource(R.string.demo_name_favourites)
    DemoResult.CircleToSearch -> stringResource(R.string.section_circle_to_search)
}

@get:StringRes
internal val DemoStep.title: Int
    get() = when (this) {
        DemoStep.BACK -> R.string.demo_step_back
        DemoStep.HOME -> R.string.demo_step_home
        DemoStep.RECENTS -> R.string.demo_step_recents
        DemoStep.APP_LIST -> R.string.demo_step_app_list
        DemoStep.CORNER -> R.string.demo_step_corner
        DemoStep.WAIT -> R.string.demo_step_wait
        DemoStep.LONG -> R.string.demo_step_long
    }

@get:StringRes
internal val DemoStep.instruction: Int
    get() = when (this) {
        DemoStep.BACK -> R.string.demo_do_back
        DemoStep.HOME -> R.string.demo_do_home
        DemoStep.RECENTS -> R.string.demo_do_recents
        DemoStep.APP_LIST -> R.string.demo_do_app_list
        DemoStep.CORNER -> R.string.demo_do_corner
        DemoStep.WAIT -> R.string.demo_do_wait
        DemoStep.LONG -> R.string.demo_do_long
    }

@get:StringRes
internal val DemoStep.practiceTitle: Int
    get() = when (this) {
        DemoStep.BACK -> R.string.practice_step_back
        DemoStep.HOME -> R.string.practice_step_home
        DemoStep.RECENTS -> R.string.practice_step_recents
        DemoStep.APP_LIST -> R.string.practice_step_app_list
        DemoStep.CORNER -> R.string.practice_step_corner
        DemoStep.WAIT -> R.string.practice_step_wait
        DemoStep.LONG -> R.string.practice_step_long
    }

/** What to do for [step] on the real edges; [sides] names where a side or corner step starts. */
@Composable
internal fun practiceInstruction(step: DemoStep, sides: PracticeSides?): String {
    val side = stringResource(
        when (sides) {
            PracticeSides.LEFT -> R.string.practice_side_left
            PracticeSides.RIGHT -> R.string.practice_side_right
            PracticeSides.BOTH, null -> R.string.practice_side_both
        },
    )
    return when (step) {
        DemoStep.BACK -> stringResource(R.string.practice_do_back, side)
        DemoStep.HOME -> stringResource(R.string.practice_do_home)
        DemoStep.RECENTS -> stringResource(R.string.practice_do_recents)
        DemoStep.APP_LIST -> stringResource(R.string.practice_do_app_list)
        DemoStep.CORNER -> stringResource(R.string.practice_do_corner, side)
        DemoStep.WAIT -> stringResource(R.string.practice_do_wait, side)
        DemoStep.LONG -> stringResource(R.string.practice_do_long, side)
    }
}

/**
 * The live page's big result line. Back, Home and Recents are "detected"; an app is "Would open"
 * with the app's own name, as `shortcutLabel` would say "Open YouTube" and read twice; any other
 * action "Would run". [label] is the app's name for a pick from the app list.
 */
@Composable
internal fun practiceResult(result: DemoResult, label: String?): String = when (result) {
    is DemoResult.Ran -> when (val action = result.run.action) {
        SwipeAction.Back, SwipeAction.Home, SwipeAction.Recents ->
            stringResource(R.string.practice_result_detected, shortcutLabel(action))
        is SwipeAction.OpenApp -> stringResource(R.string.practice_result_would_open, appLabel(action.packageName))
        else -> stringResource(R.string.practice_result_would_run, shortcutLabel(action))
    }
    is DemoResult.AppPicked -> stringResource(R.string.practice_result_would_open, label ?: resultName(result))
    DemoResult.CircleToSearch -> stringResource(R.string.practice_result_circle)
    DemoResult.FavouritesOpened -> stringResource(R.string.practice_result_would_open, resultName(result))
}

/** An app's own name; the package name while the list loads or once the app is gone. */
@Composable
private fun appLabel(packageName: String): String {
    val apps by rememberLaunchableApps(waitForScreen = false)
    return apps?.firstOrNull { it.packageName == packageName }?.label ?: packageName
}
