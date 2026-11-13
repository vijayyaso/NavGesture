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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.diagnostics.ServiceStatus
import com.vijayyaso.navgesture.gesture.GestureConfiguration

/** The answer to why the app was opened, with the one action the current state needs. */
@Composable
internal fun StatusBlock(
    status: ServiceStatus,
    configuration: GestureConfiguration,
    restrictedSettingsApply: Boolean,
    barStuck: Boolean,
    onOpenNavigation: () -> String?,
    onSwitch: (Boolean) -> Unit,
    onTurnOn: () -> Unit,
) {
    val context = LocalContext.current
    var navigationFailure by remember { mutableStateOf<String?>(null) }
    val working = status == ServiceStatus.WORKING
    val title = when (status) {
        ServiceStatus.OFF -> R.string.status_off
        ServiceStatus.STOPPED -> R.string.status_stopped
        ServiceStatus.WORKING -> R.string.status_working
    }
    val body = when (status) {
        ServiceStatus.OFF -> R.string.status_off_body
        ServiceStatus.STOPPED -> R.string.status_stopped_body
        ServiceStatus.WORKING -> workingBody(configuration)
    }
    val (badge, glyph) = toneColors(if (working) Tone.GOOD else Tone.ATTENTION)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(badge, CircleShape),
            ) {
                Icon(
                    imageVector = if (working) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
                    .testTag(TestTags.SERVICE_STATUS),
            )
            // The quick way out when another app refuses to work while the gestures run.
            val switchLabel = stringResource(R.string.status_switch)
            Switch(
                checked = working,
                onCheckedChange = onSwitch,
                modifier = Modifier
                    .semantics { contentDescription = switchLabel }
                    .testTag(TestTags.STATUS_SWITCH),
            )
        }
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!working) {
            Button(
                onClick = onTurnOn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.STATUS_TURN_ON),
            ) {
                Text(stringResource(if (status == ServiceStatus.OFF) R.string.action_turn_on else R.string.action_fix))
            }
        }
        // Restricted settings arrived in Android 13, for apps installed outside a store.
        if (status == ServiceStatus.OFF && restrictedSettingsApply) {
            Text(
                text = stringResource(R.string.status_restricted_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { openAppInfo(context) }) {
                Text(stringResource(R.string.action_open_app_info))
            }
        }
        // With the gestures gone, a bar this app hid and can no longer show leaves no navigation.
        if (!working && barStuck) {
            Text(
                text = listOfNotNull(
                    stringResource(R.string.status_bar_stuck_hint),
                    navigationFailure?.let { stringResource(R.string.outcome_failed, it) },
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TestTags.STATUS_BAR_STUCK),
            )
            TextButton(
                onClick = { navigationFailure = onOpenNavigation() },
                modifier = Modifier.testTag(TestTags.STATUS_OPEN_NAVIGATION),
            ) {
                Text(stringResource(R.string.action_open_navigation_settings))
            }
        }
    }
}

private fun workingBody(configuration: GestureConfiguration): Int = when {
    configuration.leftEdgeBackEnabled && configuration.rightEdgeBackEnabled ->
        R.string.status_working_body
    configuration.leftEdgeBackEnabled -> R.string.status_working_body_left
    configuration.rightEdgeBackEnabled -> R.string.status_working_body_right
    else -> R.string.status_working_body_bottom
}
