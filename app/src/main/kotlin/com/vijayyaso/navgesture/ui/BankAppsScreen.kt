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

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.FavouriteList
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.settings.PackageListStore
import kotlinx.coroutines.launch

/**
 * Bank pause: the apps that switch NavGesture off while they are open, and the three things
 * the pause needs. The mechanism is in bankpause/BankPause.kt. The github build only; home
 * hides the row in the play build.
 */
@Composable
internal fun BankAppsScreen(model: AppModel) {
    val context = LocalContext.current
    val store = remember(context) { PackageListStore.bankApps(context) }
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<List<String>?>(null) }
    val apps by rememberLaunchableApps()
    // Bumped on every return, so a grant made in Settings or from a computer shows at once.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(store) { store.packages.collect { picked = it } }

    val chosen = picked.orEmpty()
    val labels = remember(apps) { apps.orEmpty().associate { it.packageName to it.label } }
    val icons = remember(apps) { apps.orEmpty().associate { it.packageName to it.icon } }
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) context.startFirst(notificationSettings(context.packageName))
        resumes++
    }
    val save: (List<String>) -> Unit = { updated ->
        picked = updated
        scope.launch { store.set(updated) }
    }

    FootNote(stringResource(R.string.bank_apps_intro), Modifier.padding(horizontal = 16.dp))
    FootNote(stringResource(R.string.bank_apps_first_open), Modifier.padding(horizontal = 16.dp))

    val canWrite = resumes >= 0 && GestureBarHider.canWrite(context)
    val notifications = resumes >= 0 && NotificationManagerCompat.from(context).areNotificationsEnabled()
    SettingsGroup(title = stringResource(R.string.section_needs)) {
        InfoRow(
            title = stringResource(R.string.label_adb_grant),
            supporting = if (canWrite) {
                stringResource(R.string.label_granted)
            } else {
                stringResource(R.string.bank_apps_needs_grant, GestureBarHider.GRANT_COMMAND)
            },
            // Home shows this screen only in the github build, so the Play build never sees it.
            actions = if (canWrite) null else { { CopyGrantButton(TestTags.BANK_APPS_COPY) } },
        )
        InfoRow(
            title = stringResource(R.string.label_usage_access),
            supporting = stringResource(
                if (model.usageAccess) R.string.label_usage_access_granted else R.string.bank_apps_needs_usage,
            ),
            trailing = if (model.usageAccess) {
                null
            } else {
                {
                    FilledTonalButton(onClick = { openUsageAccess(context) }) {
                        Text(stringResource(R.string.action_grant_usage_access))
                    }
                }
            },
        )
        InfoRow(
            title = stringResource(R.string.label_notifications),
            supporting = stringResource(
                if (notifications) R.string.bank_apps_notifications_on else R.string.bank_apps_notifications_off,
            ),
            trailing = if (notifications) {
                null
            } else {
                {
                    FilledTonalButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startFirst(notificationSettings(context.packageName))
                        }
                    }) { Text(stringResource(R.string.action_allow)) }
                }
            },
        )
    }
    SettingsGroup(title = stringResource(R.string.section_bank_apps_picked)) {
        if (chosen.isEmpty()) FootNote(stringResource(R.string.bank_apps_empty))
        chosen.forEach { packageName ->
            ListItem(
                leadingContent = { AppIconImage(icons[packageName]) },
                headlineContent = { Text(labels[packageName] ?: packageName) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
    AppChecklist(apps, chosen) { packageName -> save(FavouriteList.toggle(chosen, packageName)) }
}

/** The home row's supporting text: what is picked, or what the pause still needs. */
@Composable
internal fun bankAppsSummary(model: AppModel, count: Int): String {
    val context = LocalContext.current
    return when {
        count == 0 -> stringResource(R.string.feature_off)
        !GestureBarHider.canWrite(context) -> stringResource(R.string.bank_apps_summary_needs_grant)
        !model.usageAccess -> stringResource(R.string.feature_needs_usage_access)
        else -> pluralStringResource(R.plurals.bank_apps_summary, count, count)
    }
}

private fun notificationSettings(packageName: String) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
