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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.navigation.GestureBarHider.BarState
import kotlin.math.roundToInt

@Composable
internal fun GestureSettingsScreen(model: AppModel, onOpenCorner: (BottomCorner) -> Unit) {
    val configuration = model.configuration
    var adjustOpen by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = stringResource(R.string.gestures_title)) {
        CheatSheet(configuration)
    }

    SettingsGroup(title = stringResource(R.string.section_edges)) {
        // One child, so no line cuts through the touch-area block.
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_edge_width),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.value_dp, configuration.edgeWidthDp.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.label_edge_width_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            FilledTonalButton(
                onClick = { adjustOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .testTag(TestTags.EDGE_AREA_ADJUST),
            ) {
                Text(stringResource(R.string.edge_area_adjust))
            }
            Text(
                text = stringResource(R.string.edge_area_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
        ToggleRow(
            label = stringResource(R.string.label_left_edge_back),
            checked = configuration.leftEdgeBackEnabled,
        ) { updated -> model.update { it.copy(leftEdgeBackEnabled = updated) } }
        ToggleRow(
            label = stringResource(R.string.label_right_edge_back),
            checked = configuration.rightEdgeBackEnabled,
        ) { updated -> model.update { it.copy(rightEdgeBackEnabled = updated) } }
        ToggleRow(
            label = stringResource(R.string.label_landscape_confirm),
            checked = configuration.landscapeConfirmEnabled,
            supporting = stringResource(R.string.label_landscape_confirm_body),
            tag = TestTags.SWITCH_LANDSCAPE_CONFIRM,
        ) { updated -> model.update { it.copy(landscapeConfirmEnabled = updated) } }
    }
    if (adjustOpen) {
        TouchAreaAdjuster(
            configuration = configuration,
            range = EDGE_WIDTH_RANGE,
            onCommit = { updated -> model.update { it.copy(edgeWidthDp = updated) } },
            onDismiss = { adjustOpen = false },
        )
    }

    // Here, not on App switching: a corner runs any action, even with the app list off.
    SettingsGroup(
        title = stringResource(R.string.section_corner_shortcuts),
        footer = stringResource(R.string.corner_shortcuts_body),
    ) {
        CornerShortcutRow(
            title = stringResource(R.string.label_left_corner),
            shortcut = configuration.leftCornerShortcut,
            tag = TestTags.ROW_LEFT_CORNER,
        ) { onOpenCorner(BottomCorner.LEFT) }
        CornerShortcutRow(
            title = stringResource(R.string.label_right_corner),
            shortcut = configuration.rightCornerShortcut,
            tag = TestTags.ROW_RIGHT_CORNER,
        ) { onOpenCorner(BottomCorner.RIGHT) }
    }

    SettingsGroup(title = stringResource(R.string.section_distance_and_timing)) {
        ThresholdSlider(
            label = stringResource(R.string.label_side_distance),
            value = configuration.sideMinimumSwipeDistanceDp,
            range = 8f..80f,
            formatter = { stringResource(R.string.value_dp, it) },
            supporting = stringResource(R.string.label_side_distance_body),
        ) { updated -> model.update { it.copy(sideMinimumSwipeDistanceDp = updated) } }
        ThresholdSlider(
            label = stringResource(R.string.label_bottom_distance),
            value = configuration.bottomMinimumSwipeDistanceDp,
            range = 4f..60f,
            formatter = { stringResource(R.string.value_dp, it) },
            supporting = stringResource(R.string.label_bottom_distance_body),
        ) { updated -> model.update { it.copy(bottomMinimumSwipeDistanceDp = updated) } }
        ThresholdSlider(
            label = stringResource(R.string.label_recents_line),
            value = configuration.recentsLinePercent,
            range = 5f..30f,
            formatter = { stringResource(R.string.value_percent, it) },
            supporting = stringResource(R.string.label_recents_line_body),
        ) { updated -> model.update { it.copy(recentsLinePercent = updated) } }
        ThresholdSlider(
            label = stringResource(R.string.label_maximum_duration),
            value = configuration.maximumArmDurationMs.toFloat(),
            range = 300f..3000f,
            formatter = { stringResource(R.string.value_seconds, it / 1000f) },
            supporting = stringResource(R.string.label_maximum_duration_body),
        ) { updated -> model.update { it.copy(maximumArmDurationMs = updated.toLong()) } }
    }

    // AppModel reads this again on every return, so a grant made from a computer shows at once.
    // The bar settings exist only on HyperOS. The Play build never shows an adb instruction, so
    // there the switch appears only once the grant is in place, or to say a bar it hid is stuck.
    val bar = model.gestureBar
    if (bar != BarState.NONE) GestureBarSection(configuration, bar, model)

    SettingsGroup(title = stringResource(R.string.section_feedback)) {
        ToggleRow(
            label = stringResource(R.string.label_haptics),
            checked = configuration.hapticFeedbackEnabled,
        ) { updated -> model.update { it.copy(hapticFeedbackEnabled = updated) } }
    }

    val resetMessage = stringResource(R.string.snackbar_reset)
    val undo = stringResource(R.string.action_undo)
    TextButton(
        onClick = { model.resetGestureSettings(resetMessage, undo) },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.action_reset_defaults))
    }
}

/** Only the gestures this configuration has, so a turned-off one is not advertised. */
@Composable
private fun CheatSheet(configuration: GestureConfiguration) {
    val back = when {
        configuration.leftEdgeBackEnabled && configuration.rightEdgeBackEnabled -> R.string.gesture_back_how
        configuration.leftEdgeBackEnabled -> R.string.gesture_back_left_how
        configuration.rightEdgeBackEnabled -> R.string.gesture_back_right_how
        else -> null
    }
    buildList {
        back?.let { add(R.string.gesture_back_name to it) }
        add(R.string.gesture_home_recents_name to R.string.gesture_home_recents_how)
        if (configuration.edgeAppListEnabled) {
            add(R.string.gesture_switch_apps_name to R.string.gesture_switch_apps_how)
        }
        if (configuration.circleToSearchEnabled) {
            add(R.string.gesture_circle_to_search_name to R.string.gesture_circle_to_search_how)
        }
        if (configuration.anyCornerShortcut) {
            add(R.string.gesture_corner_name to R.string.gesture_corner_how)
        }
    }.forEach { (name, how) -> InfoRow(title = stringResource(name), supporting = stringResource(how)) }
}

@Composable
internal fun gestureSettingsSummary(configuration: GestureConfiguration): String = stringResource(
    R.string.gesture_settings_summary,
    configuration.edgeWidthDp.roundToInt(),
    configuration.sideMinimumSwipeDistanceDp.roundToInt(),
    configuration.bottomMinimumSwipeDistanceDp.roundToInt(),
)

/** The same 8 to 64 dp the touch strips have always offered. */
private val EDGE_WIDTH_RANGE = 8f..64f

@Composable
private fun GestureBarSection(configuration: GestureConfiguration, bar: BarState, model: AppModel) {
    var failure by remember { mutableStateOf<String?>(null) }
    val openNavigation = { failure = model.openNavigationSettings() }
    SettingsGroup(title = stringResource(R.string.section_gesture_bar)) {
        when (bar) {
            BarState.CAN_HIDE -> ToggleRow(
                label = stringResource(R.string.label_hide_gesture_bar),
                checked = configuration.hideGestureBar,
                supporting = stringResource(R.string.hide_gesture_bar_body),
            ) { updated -> model.update { it.copy(hideGestureBar = updated) } }
            BarState.NEEDS_GRANT -> ToggleRow(
                label = stringResource(R.string.label_hide_gesture_bar),
                checked = false,
                supporting = stringResource(R.string.hide_gesture_bar_needs_grant, GestureBarHider.GRANT_COMMAND),
                actions = { CopyGrantButton() },
            ) {}
            BarState.STUCK_HIDDEN -> {
                // It really is hidden, so the switch says on; only the phone's own screen can undo it.
                ToggleRow(
                    label = stringResource(R.string.label_hide_gesture_bar),
                    checked = true,
                    supporting = stringResource(R.string.hide_gesture_bar_stuck),
                ) { openNavigation() }
                InfoRow(
                    title = stringResource(R.string.gesture_bar_stuck_title),
                    supporting = listOfNotNull(
                        stringResource(R.string.gesture_bar_stuck_body),
                        stringResource(R.string.gesture_bar_stuck_grant, GestureBarHider.GRANT_COMMAND)
                            .takeUnless { BuildConfig.IS_PLAY },
                        failure?.let { stringResource(R.string.outcome_failed, it) },
                    ).joinToString("\n"),
                    supportingTag = TestTags.GESTURE_BAR_STUCK,
                    actions = {
                        FilledTonalButton(
                            onClick = openNavigation,
                            modifier = Modifier.testTag(TestTags.GESTURE_BAR_OPEN_NAVIGATION),
                        ) {
                            Text(stringResource(R.string.action_open_navigation_settings))
                        }
                        // Shown with the grant line above, so never in the Play build.
                        if (!BuildConfig.IS_PLAY) CopyGrantButton()
                    },
                )
            }
            BarState.NONE -> Unit
        }
    }
}

/** Copies the one-time computer command; the Play build never shows it (see barState). */
@Composable
internal fun CopyGrantButton(tag: String = TestTags.GESTURE_BAR_COPY) {
    CopyButton(GestureBarHider.GRANT_COMMAND, stringResource(R.string.action_copy_command), tag = tag)
}
