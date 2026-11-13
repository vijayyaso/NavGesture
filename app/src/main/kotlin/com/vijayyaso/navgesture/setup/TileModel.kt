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

package com.vijayyaso.navgesture.setup

import android.app.StatusBarManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.vijayyaso.navgesture.R

internal enum class TileTarget { APP, ACCESSIBILITY_SETTINGS }

/** What the tile shows and where a tap leads, kept apart from TileService so it is testable. */
internal data class TileFace(
    val active: Boolean,
    @param:StringRes val subtitle: Int,
    val target: TileTarget,
) {
    companion object {
        fun of(serviceEnabled: Boolean): TileFace = if (serviceEnabled) {
            TileFace(active = true, subtitle = R.string.status_active, target = TileTarget.APP)
        } else {
            TileFace(
                active = false,
                subtitle = R.string.status_inactive,
                target = TileTarget.ACCESSIBILITY_SETTINGS,
            )
        }
    }
}

// AOSP guards the details page with a signature permission, and a denied start fails silently.
/** What one tile tap does. */
internal enum class TileTap { TURN_OFF, SHOW_DISCLOSURE, TURN_ON, OPEN_SETTINGS }

/**
 * The tile's rules, in order. A tap on a tile that is on switches the service off at once, for
 * an app that refuses to work while it runs. Consent comes before the one-tap turn-on, so no tap can switch the
 * service on before the disclosure was agreed to. [canTurnOn] is false in the Play build, without
 * the adb grant, and when the switch is already listed (writing it again would change nothing).
 */
internal fun tileTap(serviceEnabled: Boolean, disclosureAccepted: Boolean, canTurnOn: Boolean): TileTap =
    when {
        serviceEnabled -> TileTap.TURN_OFF
        !disclosureAccepted -> TileTap.SHOW_DISCLOSURE
        canTurnOn -> TileTap.TURN_ON
        else -> TileTap.OPEN_SETTINGS
    }

internal fun detailsReachable(
    resolves: Boolean,
    requiredPermission: String?,
    isGranted: (String) -> Boolean,
): Boolean = resolves && (requiredPermission == null || isGranted(requiredPermission))

internal sealed interface TileAddOutcome {
    data object Added : TileAddOutcome
    data object AlreadyAdded : TileAddOutcome
    data object Dismissed : TileAddOutcome

    /** One of StatusBarManager's TILE_ADD_REQUEST_ERROR codes. */
    data class Refused(val code: Int) : TileAddOutcome
    data class Failed(val reason: String) : TileAddOutcome {
        constructor(error: Throwable) : this(error.message ?: error.javaClass.name)
    }

    /** Android 12 and older have no add prompt, so the tile goes in by editing Control Center. */
    data object Unsupported : TileAddOutcome

    companion object {
        fun promptAvailable(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun fromResult(code: Int): TileAddOutcome = when (code) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> Added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> AlreadyAdded
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Dismissed
            else -> Refused(code)
        }
    }
}
