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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.settings.PackageListStore
import com.vijayyaso.navgesture.ui.theme.IconTint

@Composable
internal fun HomeScreen(model: AppModel, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
    StatusBlock(
        status = model.status,
        configuration = model.configuration,
        restrictedSettingsApply = model.restrictedSettingsApply,
        barStuck = model.gestureBar == GestureBarHider.BarState.STUCK_HIDDEN,
        onOpenNavigation = model::openNavigationSettings,
        onSwitch = { on -> model.switchService(context, on) },
    ) {
        model.requestAccessibility(context)
    }
    SetupGroup(model)

    val bankApps by remember(context) { PackageListStore.bankApps(context).packages }
        .collectAsState(initial = emptyList())
    val updates = Store.homeTile { onOpen(Screen.UPDATES) }
    val tiles = buildList {
        add(
            HomeTile(
                title = stringResource(R.string.section_app_switch),
                summary = appSwitchingSummary(model),
                icon = GroupIcon(Icons.Filled.Menu, IconTint.BLUE),
                tag = TestTags.ROW_APP_SWITCHING,
                toggle = TileToggle(model.configuration.edgeAppListEnabled, TestTags.SWITCH_EDGE_APP_LIST) { on ->
                    model.update { it.copy(edgeAppListEnabled = on) }
                },
            ) { onOpen(Screen.APP_SWITCHING) },
        )
        add(
            HomeTile(
                title = stringResource(R.string.section_circle_to_search),
                summary = circleToSearchSummary(model),
                icon = GroupIcon(Icons.Filled.Search, IconTint.PURPLE),
                tag = TestTags.ROW_CIRCLE_TO_SEARCH,
                toggle = TileToggle(model.configuration.circleToSearchEnabled, TestTags.SWITCH_CIRCLE_TO_SEARCH) { on ->
                    model.update { it.copy(circleToSearchEnabled = on) }
                },
            ) { onOpen(Screen.CIRCLE_TO_SEARCH) },
        )
        add(
            HomeTile(
                title = stringResource(R.string.section_gestures),
                summary = gestureSettingsSummary(model.configuration),
                icon = GroupIcon(Icons.Filled.Settings, IconTint.GREY),
                tag = TestTags.ROW_GESTURE_SETTINGS,
            ) { onOpen(Screen.GESTURE_SETTINGS) },
        )
        if (!BuildConfig.IS_PLAY) {
            add(
                HomeTile(
                    title = stringResource(R.string.section_bank_apps),
                    summary = bankAppsSummary(model, bankApps.size),
                    icon = GroupIcon(Icons.Filled.Lock, IconTint.TEAL),
                    tag = TestTags.ROW_BANK_APPS,
                ) { onOpen(Screen.BANK_APPS) },
            )
        }
        updates?.let(::add)
        // The checklist sits above until the essentials are done, then folds into a tile.
        if (model.checklist.essentialsDone) {
            val left = model.checklist.recommendedLeft
            add(
                HomeTile(
                    title = stringResource(R.string.section_setup),
                    summary = if (left == 0) {
                        stringResource(R.string.chip_done)
                    } else {
                        pluralStringResource(R.plurals.setup_recommended_left, left, left)
                    },
                    icon = GroupIcon(Icons.Filled.CheckCircle, IconTint.ORANGE),
                    tag = TestTags.ROW_SETUP,
                ) { onOpen(Screen.SETUP) },
            )
        }
    }
    SectionHeader(stringResource(R.string.section_features))
    TileGrid(tiles)

    SettingsGroup(title = stringResource(R.string.section_more)) {
        NavigationRow(
            title = stringResource(R.string.section_troubleshooting),
            tag = TestTags.ROW_TROUBLESHOOTING,
            icon = GroupIcon(Icons.Filled.Build, IconTint.RED),
        ) { onOpen(Screen.TROUBLESHOOTING) }
        NavigationRow(
            title = stringResource(R.string.section_about),
            supporting = model.diagnostics.appVersionName?.let {
                stringResource(R.string.about_version_summary, it)
            },
            tag = TestTags.ROW_ABOUT,
            icon = GroupIcon(Icons.Filled.Info, IconTint.GREY),
        ) { onOpen(Screen.ABOUT) }
    }
}

/** Two tiles a row of equal height; one a row once a large font leaves no room for two. */
@Composable
private fun TileGrid(tiles: List<HomeTile>) {
    val perRow = if (LocalDensity.current.fontScale >= STACKED_FONT_SCALE) 1 else 2
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        tiles.chunked(perRow).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                row.forEach { tile ->
                    FeatureTile(
                        tile,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                // An odd last tile keeps half the width instead of stretching across.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The required steps stay on home, in their own card, until gestures can run unattended. */
@Composable
private fun SetupGroup(model: AppModel) {
    val checklist = model.checklist
    if (checklist.essentialsDone) return
    SettingsGroup(
        title = pluralStringResource(
            R.plurals.setup_progress,
            checklist.required.size,
            checklist.requiredDone,
            checklist.required.size,
        ),
    ) {
        SetupRows(checklist.required, model)
    }
}
