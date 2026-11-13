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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.diagnostics.Diagnostics
import com.vijayyaso.navgesture.navigation.GenericAndroidNavigationIntegration
import com.vijayyaso.navgesture.navigation.HyperOSNavigationIntegration
import com.vijayyaso.navgesture.navigation.NavigationMode

@Composable
internal fun TroubleshootingScreen(model: AppModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.refresh() }
    val diagnostics = model.diagnostics
    SettingsGroup(title = stringResource(R.string.section_known_fixes)) {
        InfoRow(
            title = stringResource(R.string.fix_stopped_title),
            supporting = stringResource(R.string.fix_stopped_body),
            actions = {
                FilledTonalButton(onClick = { model.requestAccessibility(context) }) {
                    Text(stringResource(R.string.action_open))
                }
            },
        )
        if (model.restrictedSettingsApply) {
            InfoRow(
                title = stringResource(R.string.fix_greyed_out_title),
                supporting = stringResource(R.string.fix_greyed_out_body),
                actions = {
                    FilledTonalButton(onClick = { openAppInfo(context) }) {
                        Text(stringResource(R.string.action_open_app_info))
                    }
                },
            )
        }
    }

    // One list feeds both the cards and the copied report, so the two always agree.
    val deviceRows = deviceRows(diagnostics)
    val problemRows = problemRows(diagnostics)
    val logs = listOfNotNull(
        diagnostics.lastRecordedFailure?.let { failure ->
            ShownRow(stringResource(R.string.diag_recorded_failure), failure.take(MAX_FAILURE_CHARS))
        },
        diagnostics.serviceHistory?.let { history ->
            ShownRow(stringResource(R.string.diag_service_history), history.take(MAX_FAILURE_CHARS))
        },
    )

    SettingsGroup(title = stringResource(R.string.section_this_device)) {
        deviceRows.forEach { ValueRow(it.label, it.value, it.tag) }
    }

    SettingsGroup(title = stringResource(R.string.section_last_problems)) {
        problemRows.forEach { ValueRow(it.label, it.value, it.tag) }
        logs.forEach { LogBlock(it.label, it.value) }
    }

    TextButton(onClick = model::refresh, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.action_refresh_diagnostics))
    }
    // The report starts with the version and build, which this screen does not show.
    val unknown = stringResource(R.string.diag_unknown)
    val build = listOf(
        ShownRow(stringResource(R.string.about_version), diagnostics.appVersionName ?: unknown),
        ShownRow(stringResource(R.string.diag_app_build), BuildConfig.FLAVOR),
    )
    CopyButton(
        text = diagnosticsReport(stringResource(R.string.app_name), build + deviceRows + problemRows, logs),
        label = stringResource(R.string.action_copy_diagnostics),
        tag = TestTags.DIAG_COPY,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    FootNote(stringResource(R.string.diag_local_only), Modifier.padding(horizontal = 16.dp))
}

/** A title with a log under it, kept as one child so the card draws no line between them. */
@Composable
private fun LogBlock(title: String, text: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        InfoRow(title = title)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun deviceRows(diagnostics: Diagnostics): List<ShownRow> {
    val none = stringResource(R.string.diag_none)
    val unknown = stringResource(R.string.diag_unknown)
    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    fun yesNo(value: Boolean) = if (value) yes else no
    return listOf(
        ShownRow(
            stringResource(R.string.diag_android_version),
            stringResource(R.string.value_android_version, diagnostics.androidRelease ?: unknown, diagnostics.sdkInt),
        ),
        ShownRow(
            stringResource(R.string.diag_device),
            listOfNotNull(diagnostics.manufacturer, diagnostics.model).joinToString(" ").ifBlank { unknown },
        ),
        ShownRow(stringResource(R.string.diag_hyperos), diagnostics.hyperOsVersion ?: diagnostics.miuiVersion ?: none),
        ShownRow(
            stringResource(R.string.diag_navigation_mode),
            stringResource(navigationModeLabel(diagnostics.navigationMode)),
        ),
        ShownRow(stringResource(R.string.diag_service_enabled), yesNo(diagnostics.accessibilityServiceSwitchedOn)),
        ShownRow(stringResource(R.string.diag_service_bound), yesNo(diagnostics.accessibilityServiceEnabled)),
        ShownRow(stringResource(R.string.diag_service_running), yesNo(diagnostics.accessibilityServiceRunning)),
        ShownRow(stringResource(R.string.diag_integration), integrationLabel(diagnostics.navigationIntegrationId)),
    )
}

@Composable
private fun problemRows(diagnostics: Diagnostics): List<ShownRow> {
    val none = stringResource(R.string.diag_none)
    return listOf(
        ShownRow(stringResource(R.string.diag_last_failure), diagnostics.lastGlobalActionFailure ?: none),
        ShownRow(stringResource(R.string.label_last_app_switch_failure), diagnostics.lastAppSwitchFailure ?: none),
        ShownRow(
            label = stringResource(R.string.diag_last_circle_to_search_failure),
            value = diagnostics.lastCircleToSearchFailure ?: none,
            tag = TestTags.DIAG_CIRCLE_TO_SEARCH_FAILURE,
        ),
    )
}

private fun navigationModeLabel(mode: NavigationMode): Int = when (mode) {
    NavigationMode.GESTURAL -> R.string.value_navigation_gestural
    NavigationMode.THREE_BUTTON -> R.string.value_navigation_buttons
    NavigationMode.UNKNOWN -> R.string.diag_unknown
}

@Composable
private fun integrationLabel(id: String): String = when (id) {
    HyperOSNavigationIntegration.ID -> stringResource(R.string.integration_hyperos)
    GenericAndroidNavigationIntegration.ID -> stringResource(R.string.integration_generic)
    else -> id
}

private const val MAX_FAILURE_CHARS = 1200
