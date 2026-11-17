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

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vijayyaso.navgesture.demo.DemoStep
import com.vijayyaso.navgesture.diagnostics.Diagnostics
import com.vijayyaso.navgesture.diagnostics.DiagnosticsCollector
import com.vijayyaso.navgesture.diagnostics.ServiceLog
import com.vijayyaso.navgesture.diagnostics.ServiceStatus
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.navigation.EnableRequestOutcome
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.navigation.NavigationIntegration
import com.vijayyaso.navgesture.navigation.NavigationIntegrationFactory
import com.vijayyaso.navgesture.navigation.NavigationMode
import com.vijayyaso.navgesture.service.NavGestureAccessibilityService
import com.vijayyaso.navgesture.settings.GestureSettingsRepository
import com.vijayyaso.navgesture.settings.debugOverride
import com.vijayyaso.navgesture.setup.ServiceKeeper
import com.vijayyaso.navgesture.setup.SetupChecklist
import com.vijayyaso.navgesture.setup.SetupFacts
import com.vijayyaso.navgesture.setup.SetupFlag
import com.vijayyaso.navgesture.setup.SetupRecord
import com.vijayyaso.navgesture.setup.SetupStateRepository
import com.vijayyaso.navgesture.setup.TileTap
import com.vijayyaso.navgesture.setup.setupState
import com.vijayyaso.navgesture.setup.tileTap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screens show, read once per resume and kept current by the two flows. */
@Stable
internal class AppModel(
    private val context: Context,
    private val settings: GestureSettingsRepository,
    private val setupState: SetupStateRepository,
    private val scope: CoroutineScope,
) {
    val integration: NavigationIntegration = NavigationIntegrationFactory.create(context)
    val snackbar = SnackbarHostState()

    var configuration by mutableStateOf(GestureConfiguration())
        private set
    var record by mutableStateOf(SetupRecord())
        private set

    /** False until the saved record is read; before that, steps confirmed by hand look undone. */
    var recordLoaded by mutableStateOf(false)
        private set
    var diagnostics: Diagnostics by mutableStateOf(DiagnosticsCollector.collect(context))
        private set
    var usageAccess by mutableStateOf(hasUsageAccess(context))
        private set
    var assistant by mutableStateOf(readAssistant(context))
        private set
    private var batteryUnrestricted by mutableStateOf(batteryUnrestricted(context))
    var gestureBar by mutableStateOf(GestureBarHider.barState(context))
        private set

    /** Set by a Turn on tap without consent; the screen stack shows the disclosure and clears it. */
    var disclosureRequested by mutableStateOf(false)

    /**
     * Set by the own-Back snackbar's "Try again". The screen stack opens practice from whatever
     * is on top, as it does for [disclosureRequested], and clears it.
     */
    var practiceRequested by mutableStateOf(false)

    /**
     * One-shot: the step the live practice page hands to the drawn demo when it hands off. The
     * drawn demo reads it once to start there, then clears it.
     */
    var demoStartStep by mutableStateOf<DemoStep?>(null)

    /** Where a Home switch tap put the switch while its request is in flight; null otherwise. */
    var pendingSwitch by mutableStateOf<Boolean?>(null)
        private set

    val status: ServiceStatus get() = diagnostics.serviceStatus

    /** Fixed for the life of the install, so it is read once. */
    val restrictedSettingsApply: Boolean = restrictedSettingsApply()

    val checklist: SetupChecklist
        get() = SetupChecklist.evaluate(
            SetupFacts(
                restrictedSettingsApply = restrictedSettingsApply,
                restrictedSettingsConfirmed = record.restrictedSettingsConfirmed,
                serviceEverEnabled = record.serviceEverEnabled,
                service = status,
                batteryUnrestricted = batteryUnrestricted,
                batteryConfirmed = record.batteryConfirmed,
                autostartApplies = diagnostics.navigationIntegrationAvailable,
                autostartConfirmed = record.autostartConfirmed,
                tileAdded = record.tileAdded,
                navigationBarChangeable = diagnostics.navigationIntegrationAvailable,
                navigationBarHidden = diagnostics.navigationMode == NavigationMode.GESTURAL,
            ),
        )

    private var collecting: Job? = null

    fun refresh() {
        usageAccess = hasUsageAccess(context)
        assistant = readAssistant(context)
        batteryUnrestricted = batteryUnrestricted(context)
        gestureBar = GestureBarHider.barState(context)
        // The collection reads log files, so it runs off the main thread. A newer refresh cancels an older one.
        collecting?.cancel()
        collecting = scope.launch {
            diagnostics = withContext(Dispatchers.IO) { DiagnosticsCollector.collect(context) }
            pendingSwitch = pendingAfterRefresh(pendingSwitch, working = status == ServiceStatus.WORKING)
            if (status != ServiceStatus.OFF && !record.serviceEverEnabled) {
                setFlag(SetupFlag.SERVICE_EVER_ENABLED, true)
            }
        }
    }

    /** Opens the phone's own navigation screen; the reason when it could not. */
    fun openNavigationSettings(): String? =
        (integration.requestEnable() as? EnableRequestOutcome.Failed)?.reason

    fun update(transform: (GestureConfiguration) -> GestureConfiguration) {
        scope.launch { settings.update(transform) }
    }

    /**
     * Every Turn on, Fix and Open button that leads to the Accessibility switch calls this. The
     * screen stack pushes the disclosure and clears [disclosureRequested] in the same step, so a
     * second Turn on after Not now shows it again.
     */
    fun requestAccessibility(context: Context) =
        openAccessibilityOrDisclosure(context, record.disclosureAccepted) { disclosureRequested = true }

    /**
     * The Home switch. Off is at once, for an app that refuses to work while the gestures run.
     * On follows the tile's rules: one tap with the adb grant, else the Accessibility screen.
     */
    fun switchService(context: Context, on: Boolean) {
        // The switch is disabled while a request is in flight; this also stops a tap already queued.
        if (pendingSwitch != null) return
        val inFlight = switchSendsRequest(on) {
            val canTurnOn = ServiceKeeper.canTurnOn(context) &&
                !DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context)
            val tap = tileTap(serviceEnabled = false, record.disclosureAccepted, canTurnOn)
            tap == TileTap.TURN_ON && ServiceKeeper.turnOn(context)
        }
        if (!inFlight) {
            requestAccessibility(context)
            return
        }
        // Undispatched, so the thumb holds the tap from this very frame.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSwitchRequest(
                target = on,
                setPending = { pendingSwitch = it },
                request = {
                    withContext(Dispatchers.IO) {
                        if (on) {
                            ServiceLog.add(this@AppModel.context, "Home switch: turned on")
                        } else {
                            ServiceKeeper.turnOff(this@AppModel.context, "Home switch")
                        }
                    }
                },
                settle = {
                    delay(SWITCH_SETTLE_MS)
                    refreshAndWait()
                },
            )
        }
    }

    /** A newer refresh cancels an older one, so this waits for whichever one is the latest. */
    private suspend fun refreshAndWait() {
        refresh()
        while (true) {
            val job = collecting ?: return
            job.join()
            if (job === collecting) return
        }
    }

    fun acceptDisclosure() = setFlag(SetupFlag.DISCLOSURE_ACCEPTED, true)

    /** A snackbar with one action, which [onAction] runs if the user taps it. */
    fun showMessage(text: String, actionLabel: String, onAction: () -> Unit) {
        scope.launch {
            val result = snackbar.showSnackbar(text, actionLabel = actionLabel, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    /**
     * Debug builds only: `navgesture_force_drawn_demo` = 1 makes "Try the gestures" open the drawn
     * demo even with the service working. Read on each call, so a change needs no rebind.
     */
    fun forcesDrawnDemo(): Boolean = debugOverride(context, FORCE_DRAWN_DEMO_OVERRIDE) == "1"

    fun setFlag(flag: SetupFlag, value: Boolean) {
        scope.launch { setupState.set(flag, value) }
    }

    /** Reset has no confirmation, so the snackbar offers the old values back instead. */
    fun resetGestureSettings(message: String, undoLabel: String) {
        val previous = configuration
        update { it.withGestureSettingsOf(GestureConfiguration()) }
        scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) update { it.withGestureSettingsOf(previous) }
        }
    }

    internal suspend fun observe() {
        scope.launch { settings.configuration.collect { configuration = it } }
        scope.launch {
            setupState.record.collect {
                record = it
                recordLoaded = true
            }
        }
        // UiAutomation, which uiautomator and screen readers use, unbinds the service for a moment.
        // So a stop shows only once it outlasts that, and a return shows at once.
        NavGestureAccessibilityService.running.collectLatest { running ->
            if (!running) delay(STOP_GRACE_MS)
            refresh()
        }
    }
}

private const val STOP_GRACE_MS = 3_000L

/** The service takes itself down about half a second after a switch-off. */
private const val SWITCH_SETTLE_MS = 800L

/** The debug override that keeps the drawn demo with the service on; the `demo` suite sets it. */
private const val FORCE_DRAWN_DEMO_OVERRIDE = "navgesture_force_drawn_demo"

@Composable
internal fun rememberAppModel(): AppModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember {
        AppModel(
            context.applicationContext,
            GestureSettingsRepository(context.applicationContext),
            context.applicationContext.setupState,
            scope,
        )
    }
    LaunchedEffect(model) { model.observe() }
    // Settings changes everything here from another activity, so it is read again on resume.
    LifecycleResumeEffect(model) {
        model.refresh()
        onPauseOrDispose {}
    }
    return model
}
