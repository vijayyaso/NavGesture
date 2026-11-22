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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vijayyaso.navgesture.gesture.AppListStyle
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeSlots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.gestureDataStore: DataStore<Preferences> by
    resettingPreferencesDataStore("gesture_configuration")

/**
 * A global setting the e2e suites set in place of the UI, read by debug builds only. Read on each
 * call, so a change needs no rebind.
 */
internal fun debugOverride(context: Context, name: String): String? {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
    return runCatching { Settings.Global.getString(context.contentResolver, name) }
        .getOrNull()?.takeIf { it.isNotEmpty() && it != "null" }
}

/** Defaults are never duplicated here: a missing key falls back to [GestureConfiguration]. */
class GestureSettingsRepository(
    private val context: Context,
    private val store: DataStore<Preferences> = context.gestureDataStore,
) {

    val configuration: Flow<GestureConfiguration> = store.data.map { toConfiguration(it, withOverrides = true) }

    suspend fun read(): GestureConfiguration = configuration.first()

    /**
     * Reads and writes in one transaction, so two quick changes never undo each other. It starts
     * from the stored values without the debug overrides, so an override is never saved.
     */
    suspend fun update(transform: (GestureConfiguration) -> GestureConfiguration) {
        store.edit { preferences ->
            val updated = transform(toConfiguration(preferences, withOverrides = false))
            preferences[EDGE_WIDTH_DP] = updated.edgeWidthDp
            preferences[SIDE_MIN_DISTANCE_DP] = updated.sideMinimumSwipeDistanceDp
            preferences[BOTTOM_MIN_DISTANCE_DP] = updated.bottomMinimumSwipeDistanceDp
            preferences[MAX_ARM_DURATION_MS] = updated.maximumArmDurationMs
            preferences[RECENTS_LINE_PERCENT] = updated.recentsLinePercent
            preferences[OFF_AXIS_TOLERANCE] = updated.offAxisToleranceRatio
            preferences[EDGE_APP_LIST] = updated.edgeAppListEnabled
            preferences[SWAP_APP_LISTS] = updated.swapAppLists
            preferences[APP_LIST_STYLE] = updated.appListStyle.name
            preferences[SWITCHER_STEP_DP] = updated.switcherStepDp
            preferences[SWITCHER_ADAPTIVE_SPEED] = updated.switcherAdaptiveSpeed
            preferences[LEFT_CORNER] = updated.leftCornerShortcut.id
            preferences[RIGHT_CORNER] = updated.rightCornerShortcut.id
            updated.slots.encode().forEach { (key, id) -> preferences[stringPreferencesKey(key)] = id }
            preferences[LEFT_SPLIT] = updated.slots.leftSplit
            preferences[RIGHT_SPLIT] = updated.slots.rightSplit
            preferences[LONG_SWIPE_DP] = updated.longSwipeDp
            preferences[WAIT_MS] = updated.waitMs
            // The old Back switches are read once, to migrate, and never written again.
            preferences[SLOTS_VERSION] = CURRENT_SLOTS_VERSION
            preferences.remove(LEFT_EDGE_BACK)
            preferences.remove(RIGHT_EDGE_BACK)
            preferences[HAPTIC_FEEDBACK] = updated.hapticFeedbackEnabled
            preferences[HIDE_GESTURE_BAR] = updated.hideGestureBar
            preferences[LANDSCAPE_CONFIRM] = updated.landscapeConfirmEnabled
            preferences[CIRCLE_TO_SEARCH] = updated.circleToSearchEnabled
            preferences[CIRCLE_TO_SEARCH_SYSTEM_TIMING] = updated.circleToSearchFollowsSystemTiming
            preferences[CIRCLE_TO_SEARCH_HOLD_MS] = updated.circleToSearchHoldMs
        }
    }

    private fun toConfiguration(preferences: Preferences, withOverrides: Boolean): GestureConfiguration {
        val defaults = GestureConfiguration()
        // Debug overrides apply only to what is read for use, never to what a save starts from.
        val override: (String) -> String? = { name -> if (withOverrides) debugOverride(context, name) else null }
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
                override(EDGE_APP_LIST_OVERRIDE)?.let(::isOn) ?: (preferences[EDGE_APP_LIST] ?: defaults.edgeAppListEnabled),
            swapAppLists = preferences[SWAP_APP_LISTS] ?: defaults.swapAppLists,
            appListStyle = (override(APP_LIST_STYLE_OVERRIDE) ?: preferences[APP_LIST_STYLE])
                ?.let(AppListStyle::decode) ?: defaults.appListStyle,
            switcherStepDp = preferences[SWITCHER_STEP_DP] ?: defaults.switcherStepDp,
            switcherAdaptiveSpeed = preferences[SWITCHER_ADAPTIVE_SPEED] ?: defaults.switcherAdaptiveSpeed,
            leftCornerShortcut = (override(LEFT_CORNER_OVERRIDE) ?: preferences[LEFT_CORNER])
                ?.let(SwipeAction::decode) ?: defaults.leftCornerShortcut,
            rightCornerShortcut = (override(RIGHT_CORNER_OVERRIDE) ?: preferences[RIGHT_CORNER])
                ?.let(SwipeAction::decode) ?: defaults.rightCornerShortcut,
            slots = splitOverridden(storedSlots(preferences), override)
                .overriddenBy { key -> override(OVERRIDE_PREFIX + key) },
            longSwipeDp = preferences[LONG_SWIPE_DP] ?: defaults.longSwipeDp,
            waitMs = preferences[WAIT_MS] ?: defaults.waitMs,
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

    /** An install from before slots has no version yet: its two Back switches say what it had. */
    private fun storedSlots(preferences: Preferences): SwipeSlots {
        if (preferences[SLOTS_VERSION] == null) {
            return SwipeSlots.migrated(
                leftBackEnabled = preferences[LEFT_EDGE_BACK] ?: true,
                rightBackEnabled = preferences[RIGHT_EDGE_BACK] ?: true,
            )
        }
        return SwipeSlots.decode(
            leftSplit = preferences[LEFT_SPLIT] ?: false,
            rightSplit = preferences[RIGHT_SPLIT] ?: false,
        ) { key -> preferences[stringPreferencesKey(key)] }
    }

    /** `navgesture_left_split` and `navgesture_right_split` split a side without the UI. */
    private fun splitOverridden(slots: SwipeSlots, override: (String) -> String?): SwipeSlots =
        listOf(ScreenEdge.LEFT to LEFT_SPLIT_OVERRIDE, ScreenEdge.RIGHT to RIGHT_SPLIT_OVERRIDE)
            .fold(slots) { all, (edge, name) -> override(name)?.let { all.withSplit(edge, isOn(it)) } ?: all }

    /** The app-list override spares the debug emulator run a tap on the toggle. */
    private fun isOn(value: String): Boolean = value == "1" || value.equals("true", ignoreCase = true)

    private companion object {
        const val EDGE_APP_LIST_OVERRIDE = "navgesture_edge_app_list"
        const val APP_LIST_STYLE_OVERRIDE = "navgesture_app_list_style"
        const val LEFT_CORNER_OVERRIDE = "navgesture_left_corner"
        const val RIGHT_CORNER_OVERRIDE = "navgesture_right_corner"
        const val LEFT_SPLIT_OVERRIDE = "navgesture_left_split"
        const val RIGHT_SPLIT_OVERRIDE = "navgesture_right_split"

        /** `navgesture_slot_left_whole_short` sets the left short swipe, and so on. */
        const val OVERRIDE_PREFIX = "navgesture_"

        const val CURRENT_SLOTS_VERSION = 1

        val EDGE_WIDTH_DP = floatPreferencesKey("edge_width_dp")
        val SIDE_MIN_DISTANCE_DP = floatPreferencesKey("side_minimum_swipe_distance_dp")
        val BOTTOM_MIN_DISTANCE_DP = floatPreferencesKey("bottom_minimum_swipe_distance_dp")
        val MAX_ARM_DURATION_MS = longPreferencesKey("maximum_arm_duration_ms")
        val RECENTS_LINE_PERCENT = floatPreferencesKey("recents_line_percent")
        val OFF_AXIS_TOLERANCE = floatPreferencesKey("off_axis_tolerance_ratio")
        val EDGE_APP_LIST = booleanPreferencesKey("edge_app_list_enabled")
        val SWAP_APP_LISTS = booleanPreferencesKey("swap_app_lists")
        val APP_LIST_STYLE = stringPreferencesKey("app_list_style")
        val SWITCHER_STEP_DP = floatPreferencesKey("switcher_step_dp")
        val SWITCHER_ADAPTIVE_SPEED = booleanPreferencesKey("switcher_adaptive_speed")
        val LEFT_CORNER = stringPreferencesKey("left_corner_shortcut")
        val RIGHT_CORNER = stringPreferencesKey("right_corner_shortcut")
        val LEFT_EDGE_BACK = booleanPreferencesKey("left_edge_back_enabled")
        val RIGHT_EDGE_BACK = booleanPreferencesKey("right_edge_back_enabled")
        val SLOTS_VERSION = intPreferencesKey("slots_version")
        val LEFT_SPLIT = booleanPreferencesKey("left_split")
        val RIGHT_SPLIT = booleanPreferencesKey("right_split")
        val LONG_SWIPE_DP = floatPreferencesKey("long_swipe_dp")
        val WAIT_MS = longPreferencesKey("wait_ms")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        val HIDE_GESTURE_BAR = booleanPreferencesKey("hide_gesture_bar")
        val LANDSCAPE_CONFIRM = booleanPreferencesKey("landscape_confirm_enabled")
        val CIRCLE_TO_SEARCH = booleanPreferencesKey("circle_to_search_enabled")
        val CIRCLE_TO_SEARCH_SYSTEM_TIMING =
            booleanPreferencesKey("circle_to_search_follows_system_timing")
        val CIRCLE_TO_SEARCH_HOLD_MS = longPreferencesKey("circle_to_search_hold_ms")
    }
}
