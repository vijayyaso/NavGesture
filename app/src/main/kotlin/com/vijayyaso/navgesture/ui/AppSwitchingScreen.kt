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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.ui.theme.IconTint

@Composable
internal fun AppSwitchingScreen(
    model: AppModel,
    onOpenFavourites: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = model.configuration
    SettingsGroup {
        ToggleRow(
            label = stringResource(R.string.label_edge_app_list),
            checked = configuration.edgeAppListEnabled,
            supporting = stringResource(
                if (configuration.swapAppLists) R.string.edge_app_list_body_swapped else R.string.edge_app_list_body,
            ),
            tag = TestTags.SWITCH_EDGE_APP_LIST,
        ) { updated -> model.update { it.copy(edgeAppListEnabled = updated) } }
        NavigationRow(
            title = stringResource(R.string.section_favourite_apps),
            tag = TestTags.ROW_FAVOURITE_APPS,
            icon = GroupIcon(Icons.Filled.Star, IconTint.ORANGE),
            onClick = onOpenFavourites,
        )
        ToggleRow(
            label = stringResource(R.string.label_swap_app_lists),
            checked = configuration.swapAppLists,
            supporting = stringResource(R.string.label_swap_app_lists_body),
            tag = TestTags.SWITCH_SWAP_APP_LISTS,
        ) { updated -> model.update { it.copy(swapAppLists = updated) } }
    }
    SettingsGroup(title = stringResource(R.string.section_list_style)) {
        AppListStyleChoice(configuration.appListStyle) { style ->
            model.update { it.copy(appListStyle = style) }
        }
        ThresholdSlider(
            label = stringResource(R.string.label_drag_step),
            value = configuration.switcherStepDp,
            range = DRAG_STEP_RANGE,
            formatter = { stringResource(R.string.value_dp, it) },
            supporting = stringResource(R.string.label_drag_step_body),
        ) { updated -> model.update { it.copy(switcherStepDp = updated) } }
        ToggleRow(
            label = stringResource(R.string.label_adaptive_speed),
            checked = configuration.switcherAdaptiveSpeed,
            supporting = stringResource(R.string.label_adaptive_speed_body),
            tag = TestTags.SWITCH_ADAPTIVE_SPEED,
        ) { updated -> model.update { it.copy(switcherAdaptiveSpeed = updated) } }
    }
    // Shown only while the permission is missing: once granted there is nothing to do here.
    AnimatedVisibility(visible = !model.usageAccess, enter = BlockEnter, exit = BlockExit) {
        SettingsGroup(
            title = stringResource(R.string.section_needs),
            footer = stringResource(R.string.usage_access_explainer),
        ) {
            InfoRow(
                title = stringResource(R.string.label_usage_access),
                supporting = stringResource(R.string.label_usage_access_missing),
                supportingTag = TestTags.USAGE_ACCESS,
                trailing = {
                    FilledTonalButton(onClick = { openUsageAccess(context) }) {
                        Text(stringResource(R.string.action_grant_usage_access))
                    }
                },
            )
        }
    }
    LastProblem(model.diagnostics.lastAppSwitchFailure)
}

/** Sideways travel per app, from twitchy to calm. */
private val DRAG_STEP_RANGE = 40f..120f

/** The supporting text of a feature row on home, naming what it still needs. */
@Composable
internal fun appSwitchingSummary(model: AppModel): String = stringResource(
    when {
        !model.configuration.edgeAppListEnabled -> R.string.feature_off
        !model.usageAccess -> R.string.feature_needs_usage_access
        else -> R.string.feature_on
    },
)
