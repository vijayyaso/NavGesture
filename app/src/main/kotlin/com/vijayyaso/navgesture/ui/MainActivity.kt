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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.demo.FirstRun
import com.vijayyaso.navgesture.demo.FirstStack
import com.vijayyaso.navgesture.diagnostics.ServiceStatus
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.setup.ServiceKeeper
import com.vijayyaso.navgesture.setup.SetupFlag
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
    LEFT_EDGE(R.string.label_left_edge_back),
    RIGHT_EDGE(R.string.label_right_edge_back),
    BOTTOM_EDGE(R.string.label_bottom_edge),

    // One picker page per swipe, since a screen is saved by its name alone.
    LEFT_SWIPE_IN(R.string.slot_swipe_in),
    RIGHT_SWIPE_IN(R.string.slot_swipe_in),
    BOTTOM_SWIPE_UP(R.string.slot_swipe_up),
    BOTTOM_SWIPE_UP_PAUSE(R.string.slot_swipe_up_pause),
    LEFT_SWIPE_FAR_IN(R.string.slot_swipe_far_in),
    RIGHT_SWIPE_FAR_IN(R.string.slot_swipe_far_in),
    LEFT_TOP_SWIPE_IN(R.string.slot_top_swipe_in),
    LEFT_TOP_SWIPE_FAR_IN(R.string.slot_top_swipe_far_in),
    LEFT_BOTTOM_SWIPE_IN(R.string.slot_bottom_swipe_in),
    LEFT_BOTTOM_SWIPE_FAR_IN(R.string.slot_bottom_swipe_far_in),
    RIGHT_TOP_SWIPE_IN(R.string.slot_top_swipe_in),
    RIGHT_TOP_SWIPE_FAR_IN(R.string.slot_top_swipe_far_in),
    RIGHT_BOTTOM_SWIPE_IN(R.string.slot_bottom_swipe_in),
    RIGHT_BOTTOM_SWIPE_FAR_IN(R.string.slot_bottom_swipe_far_in),
    LEFT_SWIPE_IN_WAIT(R.string.slot_swipe_in_wait),
    RIGHT_SWIPE_IN_WAIT(R.string.slot_swipe_in_wait),
    LEFT_TOP_SWIPE_IN_WAIT(R.string.slot_top_swipe_in_wait),
    LEFT_BOTTOM_SWIPE_IN_WAIT(R.string.slot_bottom_swipe_in_wait),
    RIGHT_TOP_SWIPE_IN_WAIT(R.string.slot_top_swipe_in_wait),
    RIGHT_BOTTOM_SWIPE_IN_WAIT(R.string.slot_bottom_swipe_in_wait),
    BANK_APPS(R.string.section_bank_apps),
    CIRCLE_TO_SEARCH(R.string.section_circle_to_search),
    UPDATES(R.string.section_updates),
    GESTURE_SETTINGS(R.string.section_gestures),
    TROUBLESHOOTING(R.string.section_troubleshooting),
    ABOUT(R.string.section_about),
    CREDITS(R.string.section_credits),
    DISCLOSURE(R.string.disclosure_title),
    DEMO(R.string.demo_title),

    // Live practice on the real edges; the same title names the page for TalkBack.
    PRACTICE(R.string.demo_title),
}

private fun FirstStack.screens(): Array<Screen> = when (this) {
    FirstStack.HOME -> arrayOf(Screen.HOME)
    FirstStack.DEMO -> arrayOf(Screen.HOME, Screen.DEMO)
    FirstStack.DISCLOSURE -> arrayOf(Screen.HOME, Screen.DISCLOSURE)
}

@Composable
private fun NavGestureApp(openDisclosure: MutableState<Boolean>) {
    val model = rememberAppModel()
    val context = LocalContext.current
    Store.Bind()
    var saved by rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf<BackStack?>(null) }
    val stack = saved
    if (stack == null) {
        // Undecided until the saved record has loaded. Only the background shows, so no Home frame
        // flashes before the demo, and the tile's request is read at the moment of the decision.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        if (model.recordLoaded) {
            LaunchedEffect(Unit) {
                val record = model.record
                val first = FirstRun.firstStack(
                    record,
                    serviceOn = model.status != ServiceStatus.OFF,
                    tileAskedDisclosure = openDisclosure.value,
                )
                saved = BackStack.of(*first.screens())
                openDisclosure.value = false
                // Saved now, not when the demo closes: a rotation also disposes it.
                if (FirstRun.marksDemoSeen(record, first)) model.setFlag(SetupFlag.DEMO_SEEN, true)
            }
        }
        return
    }

    // Every change starts from the newest stack, since a lambda can outlive the frame that made it.
    fun update(change: (BackStack) -> BackStack) {
        saved = saved?.let(change)
    }

    val screens = updateTransition(stack.top, label = "screen")
    // A Back while a screen is still arriving is swallowed, so one quick double press never
    // pops twice, and never closes the app on its way home.
    val arriving = screens.currentState != screens.targetState
    BackHandler(enabled = stack.canGoBack || arriving) {
        if (!arriving) update { it.back() }
    }

    // From the tile: home with the disclosure on top, whatever was showing before.
    if (openDisclosure.value) {
        LaunchedEffect(Unit) {
            update { it.replacedBy(Screen.HOME, Screen.DISCLOSURE) }
            openDisclosure.value = false
        }
    }

    // Read here, once, for "Try again" from the own-Back snackbar.
    val practiceEntry = rememberPracticeEntry(model)

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Test tags become resource ids, so the e2e suites select rows without matching text.
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(model.snackbar) },
    ) { padding ->
        // Each slot is one stack entry, with its own bar and scroll, so the bar leaves with its
        // screen. Forward opens at the top; Back finds the screen where it was left.
        screens.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { screenChange(forward = opensForward(initialState, targetState), rtl = rtl) },
            contentKey = { it.id },
        ) { entry ->
            val leaving = screens.targetState.id != entry.id
            val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(entry.scroll) }
            val open: (Screen) -> Unit = { screen -> update { it.openFrom(entry.id, screen, scroll.value) } }
            val close = { update { it.backFrom(entry.id) } }
            val replace: (List<Screen>) -> Unit = { screens -> update { it.replacedBy(*screens.toTypedArray()) } }
            val swap: (Screen) -> Unit = { screen -> update { it.swapTopFrom(entry.id, screen) } }
            // From a Turn on, Fix or Open button: the disclosure goes on top of the current screen.
            if (model.disclosureRequested && !leaving) {
                LaunchedEffect(Unit) {
                    open(Screen.DISCLOSURE)
                    model.disclosureRequested = false
                }
            }
            // From the own-Back snackbar's "Try again": practice opens again from the current screen.
            if (model.practiceRequested && !leaving) {
                LaunchedEffect(Unit) {
                    open(practiceEntry())
                    model.practiceRequested = false
                }
            }
            val title = stringResource(entry.screen.title)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (leaving) LeavingScreen else Modifier.semantics { paneTitle = title }),
            ) {
                // Home opens on its own large title instead, so it has no bar and no back arrow. The
                // live practice page is full screen and has its own Close.
                val fullScreen = entry.screen == Screen.PRACTICE
                if (entry.screen != Screen.HOME && !fullScreen) ScreenBar(title, onBack = close)
                val settled = remember(entry.id) {
                    { screens.targetState.id == entry.id && screens.currentState == screens.targetState }
                }
                val content = @Composable {
                    ScreenContent(
                        screen = entry.screen,
                        model = model,
                        leaving = leaving,
                        open = open,
                        close = close,
                        replace = replace,
                        swap = swap,
                        onAgree = {
                            if (saved?.top?.id == entry.id) {
                                model.acceptDisclosure()
                                close()
                                openAccessibility(context)
                            }
                        },
                    )
                }
                CompositionLocalProvider(LocalScreenSettled provides settled) {
                    if (fullScreen) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) { content() }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(scroll)
                                .padding(bottom = 24.dp),
                        ) { content() }
                    }
                }
            }
        }
    }
}

/**
 * The screen fading out: it takes no touch and is hidden from TalkBack and uiautomator, so a
 * late tap or a test cannot reach it and focus lands on the screen arriving.
 */
private val LeavingScreen = Modifier
    .clearAndSetSemantics {}
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.NAV_BACK)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_up),
                )
            }
        },
        // The scaffold already pads every screen for the status bar.
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
private fun ScreenContent(
    screen: Screen,
    model: AppModel,
    leaving: Boolean,
    open: (Screen) -> Unit,
    close: () -> Unit,
    replace: (List<Screen>) -> Unit,
    swap: (Screen) -> Unit,
    onAgree: () -> Unit,
) {
    when (screen) {
        Screen.HOME -> HomeScreen(model, onOpen = open)
        Screen.SETUP -> SetupScreen(model, onOpen = open)
        Screen.APP_SWITCHING -> AppSwitchingScreen(
            model = model,
            onOpenFavourites = { open(Screen.FAVOURITE_APPS) },
        )
        Screen.LEFT_CORNER, Screen.RIGHT_CORNER, Screen.LEFT_SWIPE_IN, Screen.RIGHT_SWIPE_IN,
        Screen.BOTTOM_SWIPE_UP, Screen.BOTTOM_SWIPE_UP_PAUSE, Screen.LEFT_SWIPE_FAR_IN, Screen.RIGHT_SWIPE_FAR_IN,
        Screen.LEFT_TOP_SWIPE_IN, Screen.LEFT_TOP_SWIPE_FAR_IN, Screen.LEFT_BOTTOM_SWIPE_IN,
        Screen.LEFT_BOTTOM_SWIPE_FAR_IN, Screen.RIGHT_TOP_SWIPE_IN, Screen.RIGHT_TOP_SWIPE_FAR_IN,
        Screen.RIGHT_BOTTOM_SWIPE_IN, Screen.RIGHT_BOTTOM_SWIPE_FAR_IN, Screen.LEFT_SWIPE_IN_WAIT,
        Screen.RIGHT_SWIPE_IN_WAIT, Screen.LEFT_TOP_SWIPE_IN_WAIT, Screen.LEFT_BOTTOM_SWIPE_IN_WAIT,
        Screen.RIGHT_TOP_SWIPE_IN_WAIT, Screen.RIGHT_BOTTOM_SWIPE_IN_WAIT,
        -> screen.pickerSlot?.let { SwipeActionPickerScreen(it, model, onDone = close) }
        Screen.LEFT_EDGE -> SwipeEdgeScreen(ScreenEdge.LEFT, model, onOpen = open)
        Screen.RIGHT_EDGE -> SwipeEdgeScreen(ScreenEdge.RIGHT, model, onOpen = open)
        Screen.BOTTOM_EDGE -> SwipeEdgeScreen(ScreenEdge.BOTTOM, model, onOpen = open)
        Screen.FAVOURITE_APPS -> FavouriteAppsScreen()
        Screen.BANK_APPS -> BankAppsScreen(model)
        Screen.CIRCLE_TO_SEARCH -> CircleToSearchScreen(model)
        Screen.UPDATES -> Store.UpdatesScreen()
        Screen.GESTURE_SETTINGS -> GestureSettingsScreen(model = model, onOpen = open)
        Screen.TROUBLESHOOTING -> TroubleshootingScreen(model)
        Screen.ABOUT -> AboutScreen(model, onOpenCredits = { open(Screen.CREDITS) })
        Screen.CREDITS -> CreditsScreen()
        // Back and Not now are the same: nothing is recorded.
        Screen.DISCLOSURE -> DisclosureScreen(onAgree = onAgree, onNotNow = close)
        // Set up now opens Setup with Home under it; Done and Skip go back to Home.
        Screen.DEMO -> DemoScreen(
            model = model,
            onClose = close,
            onSetUp = { replace(listOf(Screen.HOME, Screen.SETUP)) },
        )
        // Full screen, with no bar and no scroll column; every hand-off swaps it for the drawn demo.
        Screen.PRACTICE -> PracticeScreen(
            model = model,
            leaving = leaving,
            onClose = close,
            onSwap = swap,
            onSetUp = { replace(listOf(Screen.HOME, Screen.SETUP)) },
        )
    }
}
