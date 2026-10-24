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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.navigation.GestureBarHider
import kotlin.math.roundToInt

@Composable
internal fun GestureSettingsScreen(model: AppModel) {
    val configuration = model.configuration
    var adjustOpen by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.gestures_title))
    CheatSheet(configuration)

    SectionHeader(stringResource(R.string.section_edges))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            fontWeight = FontWeight.Medium,
        )
    }
    Text(
        text = stringResource(R.string.label_edge_width_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
    OutlinedButton(
        onClick = { adjustOpen = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TestTags.EDGE_AREA_ADJUST),
    ) {
        Text(stringResource(R.string.edge_area_adjust))
    }
    Text(
        text = stringResource(R.string.edge_area_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
    if (adjustOpen) {
        TouchAreaAdjuster(
            configuration = configuration,
            range = EDGE_WIDTH_RANGE,
            onCommit = { updated -> model.update { it.copy(edgeWidthDp = updated) } },
            onDismiss = { adjustOpen = false },
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

    SectionHeader(stringResource(R.string.section_distance_and_timing))
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

    SectionHeader(stringResource(R.string.section_gesture_bar))
    // Read on every recomposition, so a grant made while the app is open shows on return.
    val canHide = GestureBarHider.canWrite(LocalContext.current)
    ToggleRow(
        label = stringResource(R.string.label_hide_gesture_bar),
        checked = configuration.hideGestureBar && canHide,
        supporting = if (canHide) {
            stringResource(R.string.hide_gesture_bar_body)
        } else {
            stringResource(R.string.hide_gesture_bar_needs_grant, GestureBarHider.GRANT_COMMAND)
        },
    ) { updated -> if (canHide) model.update { it.copy(hideGestureBar = updated) } }

    SectionHeader(stringResource(R.string.section_feedback))
    ToggleRow(
        label = stringResource(R.string.label_haptics),
        checked = configuration.hapticFeedbackEnabled,
    ) { updated -> model.update { it.copy(hapticFeedbackEnabled = updated) } }

    val resetMessage = stringResource(R.string.snackbar_reset)
    val undo = stringResource(R.string.action_undo)
    TextButton(
        onClick = { model.resetGestureSettings(resetMessage, undo) },
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
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
