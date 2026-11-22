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

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

// The only motion numbers the screens use. Compose already scales them by the phone's
// animator duration scale, so "Remove animations" turns them all off.

/** Starts fast and settles softly: things arrive at once and stop without a bump. */
internal val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal const val FADE_IN_MS = 150
internal const val EXPAND_MS = 200
internal const val FADE_OUT_MS = 100
internal const val SHRINK_MS = 150
internal const val SCREEN_CHANGE_MS = 200

/** A new screen slides in from a tenth of the width away. */
internal const val SCREEN_SLIDE_FRACTION = 10

/**
 * Where an arriving screen starts, in pixels: forward from the end side, back from the start
 * side. Offsets are not mirrored for us, so right-to-left flips them here.
 */
internal fun screenSlideOffset(width: Int, forward: Boolean, rtl: Boolean): Int {
    val towardEnd = if (forward) 1 else -1
    val mirror = if (rtl) -1 else 1
    return towardEnd * mirror * width / SCREEN_SLIDE_FRACTION
}

/** Forward or back: the new screen slides a little and fades in; the old one only fades out. */
internal fun screenChange(forward: Boolean, rtl: Boolean): ContentTransform {
    val arrive = slideInHorizontally(tween(SCREEN_CHANGE_MS, easing = EaseOutStrong)) {
        screenSlideOffset(it, forward, rtl)
    } + fadeIn(tween(SCREEN_CHANGE_MS, easing = EaseOutStrong))
    return arrive togetherWith fadeOut(tween(FADE_OUT_MS))
}

/**
 * True once the screen has fully arrived. A heavy screen waits for it before it builds a long
 * list, so the list never lands in the middle of the slide.
 */
internal val LocalScreenSettled = staticCompositionLocalOf<() -> Boolean> { { true } }

/** A colour or tint that follows a state, such as the status badge going from amber to green. */
internal const val TONE_MS = 200

/** A held thing lights up fast and lets go slower. */
internal const val PRESS_IN_MS = 100
internal const val PRESS_OUT_MS = 200

/** The Setup tile waits for the setup card above it to start folding, then grows in from this size. */
internal const val TILE_ARRIVE_DELAY_MS = 150
internal const val TILE_ARRIVE_SCALE = 0.96f

/** A whole card leaving: slower than a block, as it moves more of the page. */
internal const val CARD_FADE_OUT_MS = 150
internal const val CARD_FOLD_MS = 250

/** A block that opens below a switch: it fades in while its space grows. */
internal val BlockEnter: EnterTransition =
    fadeIn(tween(FADE_IN_MS, easing = EaseOutStrong)) + expandVertically(tween(EXPAND_MS, easing = EaseOutStrong))

/** The same block closing: it fades a little faster than its space closes. */
internal val BlockExit: ExitTransition =
    fadeOut(tween(FADE_OUT_MS)) + shrinkVertically(tween(SHRINK_MS, easing = EaseOutStrong))

/**
 * [value] while [shown], else the last value seen while shown. A block folding away keeps
 * drawing what it showed instead of the empty state that hid it.
 */
@Composable
internal fun <T> rememberLastShown(value: T, shown: Boolean): T {
    val held = remember { Held(value) }
    if (shown) held.value = value
    return held.value
}

private class Held<T>(var value: T)

/** A finished card folding away, drawing its last content while it goes. */
internal val CardFoldExit: ExitTransition =
    fadeOut(tween(CARD_FADE_OUT_MS)) + shrinkVertically(tween(CARD_FOLD_MS, easing = EaseOutStrong))
