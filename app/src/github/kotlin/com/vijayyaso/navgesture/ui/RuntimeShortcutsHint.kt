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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
        RuntimeAccess.NEEDS_GRANT -> {
            var copied by remember { mutableStateOf(false) }
            InfoRow(
                title = stringResource(R.string.runtime_shortcuts_title),
                supporting = stringResource(R.string.runtime_shortcuts_needs_grant, RuntimeShortcuts.GRANT_COMMANDS),
                supportingTag = TestTags.RUNTIME_SHORTCUTS_HINT,
                actions = {
                    FilledTonalButton(
                        onClick = { copied = copyCommands(context) },
                        modifier = Modifier.testTag(TestTags.RUNTIME_SHORTCUTS_COPY),
                    ) {
                        Text(stringResource(if (copied) R.string.label_copied else R.string.action_copy_commands))
                    }
                },
            )
        }
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

private fun copyCommands(context: Context): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("adb", RuntimeShortcuts.GRANT_COMMANDS))
}.isSuccess
