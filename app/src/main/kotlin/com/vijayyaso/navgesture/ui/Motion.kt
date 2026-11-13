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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

// The only motion numbers the screens use. Compose already scales them by the phone's
// animator duration scale, so "Remove animations" turns them all off.

/** Starts fast and settles softly: things arrive at once and stop without a bump. */
internal val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal const val FADE_IN_MS = 150
internal const val EXPAND_MS = 200
internal const val FADE_OUT_MS = 100
internal const val SHRINK_MS = 150
internal const val SCREEN_CHANGE_MS = 200

/** A block that opens below a switch: it fades in while its space grows. */
internal val BlockEnter: EnterTransition =
    fadeIn(tween(FADE_IN_MS, easing = EaseOutStrong)) + expandVertically(tween(EXPAND_MS, easing = EaseOutStrong))

/** The same block closing: it fades a little faster than its space closes. */
internal val BlockExit: ExitTransition =
    fadeOut(tween(FADE_OUT_MS)) + shrinkVertically(tween(SHRINK_MS, easing = EaseOutStrong))
