// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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

package com.vijayyaso.navgesture.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.gestureDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "gesture_configuration")

/** Defaults are never duplicated here: a missing key falls back to [GestureConfiguration]. */
class GestureSettingsRepository(private val context: Context) {

    val configuration: Flow<GestureConfiguration> =
        context.gestureDataStore.data.map(::toConfiguration)

    suspend fun read(): GestureConfiguration = configuration.first()

    suspend fun update(transform: (GestureConfiguration) -> GestureConfiguration) {
        val updated = transform(read())
        context.gestureDataStore.edit { preferences ->
            preferences[EDGE_WIDTH_DP] = updated.edgeWidthDp
            preferences[SIDE_MIN_DISTANCE_DP] = updated.sideMinimumSwipeDistanceDp
            preferences[BOTTOM_MIN_DISTANCE_DP] = updated.bottomMinimumSwipeDistanceDp
            preferences[MAX_ARM_DURATION_MS] = updated.maximumArmDurationMs
            preferences[RECENTS_LINE_PERCENT] = updated.recentsLinePercent
            preferences[OFF_AXIS_TOLERANCE] = updated.offAxisToleranceRatio
            preferences[EDGE_APP_LIST] = updated.edgeAppListEnabled
            preferences[LEFT_EDGE_BACK] = updated.leftEdgeBackEnabled
            preferences[RIGHT_EDGE_BACK] = updated.rightEdgeBackEnabled
            preferences[HAPTIC_FEEDBACK] = updated.hapticFeedbackEnabled
            preferences[HIDE_GESTURE_BAR] = updated.hideGestureBar
            preferences[LANDSCAPE_CONFIRM] = updated.landscapeConfirmEnabled
            preferences[CIRCLE_TO_SEARCH] = updated.circleToSearchEnabled
            preferences[CIRCLE_TO_SEARCH_SYSTEM_TIMING] = updated.circleToSearchFollowsSystemTiming
            preferences[CIRCLE_TO_SEARCH_HOLD_MS] = updated.circleToSearchHoldMs
        }
    }

    private fun toConfiguration(preferences: Preferences): GestureConfiguration {
        val defaults = GestureConfiguration()
        return GestureConfiguration(
            edgeWidthDp = preferences[EDGE_WIDTH_DP] ?: defaults.edgeWidthDp,
            sideMinimumSwipeDistanceDp =
                preferences[SIDE_MIN_DISTANCE_DP] ?: defaults.sideMinimumSwipeDistanceDp,
            bottomMinimumSwipeDistanceDp =
                preferences[BOTTOM_MIN_DISTANCE_DP] ?: defaults.bottomMinimumSwipeDistanceDp,
            maximumArmDurationMs =
                preferences[MAX_ARM_DURATION_MS] ?: defaults.maximumArmDurationMs,
            recentsLinePercent =
                preferences[RECENTS_LINE_PERCENT] ?: defaults.recentsLinePercent,
            offAxisToleranceRatio =
                preferences[OFF_AXIS_TOLERANCE] ?: defaults.offAxisToleranceRatio,
            edgeAppListEnabled =
                debugEdgeAppListEnabled() ?: (preferences[EDGE_APP_LIST] ?: defaults.edgeAppListEnabled),
            leftEdgeBackEnabled = preferences[LEFT_EDGE_BACK] ?: defaults.leftEdgeBackEnabled,
            rightEdgeBackEnabled = preferences[RIGHT_EDGE_BACK] ?: defaults.rightEdgeBackEnabled,
            hapticFeedbackEnabled = preferences[HAPTIC_FEEDBACK] ?: defaults.hapticFeedbackEnabled,
            hideGestureBar = preferences[HIDE_GESTURE_BAR] ?: defaults.hideGestureBar,
            landscapeConfirmEnabled =
                preferences[LANDSCAPE_CONFIRM] ?: defaults.landscapeConfirmEnabled,
            circleToSearchEnabled =
                preferences[CIRCLE_TO_SEARCH] ?: defaults.circleToSearchEnabled,
            circleToSearchFollowsSystemTiming = preferences[CIRCLE_TO_SEARCH_SYSTEM_TIMING]
                ?: defaults.circleToSearchFollowsSystemTiming,
            circleToSearchHoldMs =
                preferences[CIRCLE_TO_SEARCH_HOLD_MS] ?: defaults.circleToSearchHoldMs,
        )
    }

    /** Test seam that spares the debug emulator run a tap on the toggle. Debug only. */
    private fun debugEdgeAppListEnabled(): Boolean? {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return runCatching {
            Settings.Global.getString(context.contentResolver, EDGE_APP_LIST_OVERRIDE)?.let {
                it == "1" || it.equals("true", ignoreCase = true)
            }
        }.getOrNull()
    }

    private companion object {
        const val EDGE_APP_LIST_OVERRIDE = "navgesture_edge_app_list"

        val EDGE_WIDTH_DP = floatPreferencesKey("edge_width_dp")
        val SIDE_MIN_DISTANCE_DP = floatPreferencesKey("side_minimum_swipe_distance_dp")
        val BOTTOM_MIN_DISTANCE_DP = floatPreferencesKey("bottom_minimum_swipe_distance_dp")
        val MAX_ARM_DURATION_MS = longPreferencesKey("maximum_arm_duration_ms")
        val RECENTS_LINE_PERCENT = floatPreferencesKey("recents_line_percent")
        val OFF_AXIS_TOLERANCE = floatPreferencesKey("off_axis_tolerance_ratio")
        val EDGE_APP_LIST = booleanPreferencesKey("edge_app_list_enabled")
        val LEFT_EDGE_BACK = booleanPreferencesKey("left_edge_back_enabled")
        val RIGHT_EDGE_BACK = booleanPreferencesKey("right_edge_back_enabled")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        val HIDE_GESTURE_BAR = booleanPreferencesKey("hide_gesture_bar")
        val LANDSCAPE_CONFIRM = booleanPreferencesKey("landscape_confirm_enabled")
        val CIRCLE_TO_SEARCH = booleanPreferencesKey("circle_to_search_enabled")
        val CIRCLE_TO_SEARCH_SYSTEM_TIMING =
            booleanPreferencesKey("circle_to_search_follows_system_timing")
        val CIRCLE_TO_SEARCH_HOLD_MS = longPreferencesKey("circle_to_search_hold_ms")
    }
}
