// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.setup.ServiceKeeper
import com.vijayyaso.navgesture.ui.theme.NavGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Held by the activity, not the composable, so onNewIntent can reach it. The screen stack
     * consumes it, and the extra is removed, so a rotation never reopens the disclosure.
     */
    private val openDisclosure = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 draws edge to edge anyway, and without this the status bar keeps white
        // icons on the light theme, so the clock and battery vanish.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consumeDisclosureExtra(intent)
        setContent {
            NavGestureTheme { NavGestureApp(openDisclosure) }
        }
    }

    // singleTop: a tile tap while the app is open arrives here instead of a second copy.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDisclosureExtra(intent)
    }

    private fun consumeDisclosureExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_DISCLOSURE, false) != true) return
        intent.removeExtra(EXTRA_SHOW_DISCLOSURE)
        openDisclosure.value = true
    }

    // Opening the app is the user's first move when the swipes are gone.
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) { ServiceKeeper.check(applicationContext, "app opened") }
    }

    companion object {
        /** Set by the tile: open on the disclosure, since consent is still missing. */
        const val EXTRA_SHOW_DISCLOSURE = "com.vijayyaso.navgesture.SHOW_DISCLOSURE"
    }
}

/** Every screen sits one level under home, so a list of them is the whole back stack. */
internal enum class Screen(@param:StringRes val title: Int) {
    HOME(R.string.app_name),
    SETUP(R.string.section_setup),
    APP_SWITCHING(R.string.section_app_switch),
    FAVOURITE_APPS(R.string.section_favourite_apps),
    LEFT_CORNER(R.string.label_left_corner),
    RIGHT_CORNER(R.string.label_right_corner),
    BANK_APPS(R.string.section_bank_apps),
    CIRCLE_TO_SEARCH(R.string.section_circle_to_search),
    UPDATES(R.string.section_updates),
    GESTURE_SETTINGS(R.string.section_gestures),
    TROUBLESHOOTING(R.string.section_troubleshooting),
    ABOUT(R.string.section_about),
    DISCLOSURE(R.string.disclosure_title),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavGestureApp(openDisclosure: MutableState<Boolean>) {
    val model = rememberAppModel()
    val context = LocalContext.current
    Store.Bind()
    var stack by rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf(listOf(Screen.HOME)) }
    val screen = stack.last()
    val back = { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    // From the tile: home with the disclosure on top, whatever was showing before.
    if (openDisclosure.value) {
        LaunchedEffect(Unit) {
            stack = listOf(Screen.HOME, Screen.DISCLOSURE)
            openDisclosure.value = false
        }
    }
    // From a Turn on, Fix or Open button: the disclosure goes on top of the current screen.
    if (model.disclosureRequested) {
        LaunchedEffect(Unit) {
            stack = stack.opening(Screen.DISCLOSURE)
            model.disclosureRequested = false
        }
    }

    // Test tags become resource ids, so the e2e suites select rows without matching text.
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            // Home opens on its own large title instead, so it has no bar and no back arrow.
            if (screen != Screen.HOME) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(screen.title), fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onClick = back, modifier = Modifier.testTag(TestTags.NAV_BACK)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_navigate_up),
                            )
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(model.snackbar) },
    ) { padding ->
        // Keyed by screen. Each one opens at its top rather than at the previous scroll.
        key(screen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                when (screen) {
                    Screen.HOME -> HomeScreen(model) { stack = stack.opening(it) }
                    Screen.SETUP -> SetupScreen(model)
                    Screen.APP_SWITCHING -> AppSwitchingScreen(
                        model = model,
                        onOpenFavourites = { stack = stack.opening(Screen.FAVOURITE_APPS) },
                    )
                    Screen.LEFT_CORNER -> CornerPickerScreen(BottomCorner.LEFT, model, onDone = back)
                    Screen.RIGHT_CORNER -> CornerPickerScreen(BottomCorner.RIGHT, model, onDone = back)
                    Screen.FAVOURITE_APPS -> FavouriteAppsScreen()
                    Screen.BANK_APPS -> BankAppsScreen(model)
                    Screen.CIRCLE_TO_SEARCH -> CircleToSearchScreen(model)
                    Screen.UPDATES -> Store.UpdatesScreen()
                    Screen.GESTURE_SETTINGS -> GestureSettingsScreen(
                        model = model,
                        onOpenCorner = { corner ->
                            stack = stack.opening(
                                when (corner) {
                                    BottomCorner.LEFT -> Screen.LEFT_CORNER
                                    BottomCorner.RIGHT -> Screen.RIGHT_CORNER
                                },
                            )
                        },
                    )
                    Screen.TROUBLESHOOTING -> TroubleshootingScreen(model)
                    Screen.ABOUT -> AboutScreen(model)
                    // Back and Not now are the same: nothing is recorded.
                    Screen.DISCLOSURE -> DisclosureScreen(
                        onAgree = {
                            model.acceptDisclosure()
                            back()
                            openAccessibility(context)
                        },
                        onNotNow = back,
                    )
                }
            }
        }
    }
}
