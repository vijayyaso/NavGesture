// Copyright (C) 2026  Vijayyaso
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

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.service.RuntimeAccess
import com.vijayyaso.navgesture.service.RuntimeShortcuts

/**
 * The row at the top of the picker's App shortcuts card while the run-time shortcuts are
 * locked: the one-time computer step, with a button that copies its commands. Nothing once
 * they can be read, or when reading them failed for a reason the user cannot fix.
 */
@Composable
internal fun RuntimeShortcutsHint(access: RuntimeAccess) {
    val context = LocalContext.current
    when (access) {
        RuntimeAccess.NEEDS_GRANT -> InfoRow(
            title = stringResource(R.string.runtime_shortcuts_title),
            supporting = stringResource(R.string.runtime_shortcuts_needs_grant, RuntimeShortcuts.GRANT_COMMANDS),
            supportingTag = TestTags.RUNTIME_SHORTCUTS_HINT,
            actions = {
                CopyButton(
                    text = RuntimeShortcuts.GRANT_COMMANDS,
                    label = stringResource(R.string.action_copy_commands),
                    tag = TestTags.RUNTIME_SHORTCUTS_COPY,
                )
            },
        )
        RuntimeAccess.NEEDS_USAGE_ACCESS -> InfoRow(
            title = stringResource(R.string.runtime_shortcuts_title),
            supporting = stringResource(R.string.runtime_shortcuts_needs_usage),
            supportingTag = TestTags.RUNTIME_SHORTCUTS_HINT,
            actions = {
                FilledTonalButton(onClick = { openUsageAccess(context) }) {
                    Text(stringResource(R.string.action_open))
                }
            },
        )
        RuntimeAccess.NOT_IN_BUILD, RuntimeAccess.UNREADABLE, RuntimeAccess.READY -> Unit
    }
}
