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

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.ui.theme.IconTint
import com.vijayyaso.navgesture.update.UpdateViewModel

/**
 * The github build's hooks: the updater and the support row. The play copy of this file draws
 * nothing and does nothing, so the play dex has no updater class to strip.
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

    /**
     * The Buy me a coffee button under About's Help and feedback rows; none in play. It wears the
     * service's own yellow and black in both themes, so it reads as that button at a glance.
     */
    @Composable
    fun SupportRow() {
        val context = LocalContext.current
        val url = stringResource(R.string.url_support)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { context.startFirst(Intent(Intent.ACTION_VIEW, url.toUri())) },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeYellow, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag(TestTags.ROW_SUPPORT),
            ) {
                Icon(painterResource(R.drawable.ic_coffee), contentDescription = null)
                Text(
                    text = stringResource(R.string.support_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                text = stringResource(R.string.support_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The yellow of Buy Me a Coffee's own button. */
private val CoffeeYellow = Color(0xFFFFDD00)
