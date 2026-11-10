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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.ui.theme.IconTint
import com.vijayyaso.navgesture.update.UpdateViewModel

/**
 * The github build's updater hooks. The play copy of this file draws nothing and does nothing,
 * so the play dex has no updater class to strip.
 */
internal object Store {

    /** Activity-scoped hook: owns the update view model and its resume check. */
    @Composable
    fun Bind() {
        val updates: UpdateViewModel = viewModel()
        LifecycleResumeEffect(updates) {
            updates.onResume()
            onPauseOrDispose {}
        }
    }

    /** The Updates tile on home; null in play. */
    @Composable
    fun homeTile(onOpen: () -> Unit): HomeTile? {
        val updates = viewModel<UpdateViewModel>()
        val state by updates.state.collectAsState()
        return HomeTile(
            title = stringResource(R.string.section_updates),
            summary = updatesSummary(state),
            icon = GroupIcon(Icons.Filled.Refresh, IconTint.GREEN),
            tag = TestTags.ROW_UPDATES,
            toggle = TileToggle(state.enabled, TestTags.SWITCH_UPDATE_CHECK, updates::setEnabled),
            onOpen = onOpen,
        )
    }

    /** The Updates screen body; draws nothing in play. */
    @Composable
    fun UpdatesScreen() {
        UpdatesScreenContent(viewModel())
    }
}
