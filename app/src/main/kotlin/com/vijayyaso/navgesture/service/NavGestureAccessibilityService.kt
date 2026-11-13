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

// The service uses this privilege for navigation alone, without reading window content or
// keystrokes or sending anything. The app goes online only for its opt-in update check.

package com.vijayyaso.navgesture.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.bankpause.BankPause
import com.vijayyaso.navgesture.diagnostics.DiagnosticsCollector
import com.vijayyaso.navgesture.diagnostics.FailureLog
import com.vijayyaso.navgesture.diagnostics.ServiceLog
import com.vijayyaso.navgesture.gesture.GestureAction
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.PlatformDefaults
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.navigation.GestureBarHider
import com.vijayyaso.navgesture.settings.GestureSettingsRepository
import com.vijayyaso.navgesture.settings.PackageListStore
import com.vijayyaso.navgesture.setup.GestureTileService
import com.vijayyaso.navgesture.setup.ServiceKeeper
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the edge strips in-process rather than in a foreground service:
 * `TYPE_ACCESSIBILITY_OVERLAY` needs no `SYSTEM_ALERT_WINDOW` permission and no ongoing
 * notification, and the framework rebinds an enabled service itself, so no watchdog.
 */
class NavGestureAccessibilityService : AccessibilityService(), TouchReplayer {

    /**
     * An uncaught throw in [scope] - realistically DataStore on an unreadable file -
     * kills the process and gets the service reported as malfunctioning.
     */
    private val coroutineFailures = CoroutineExceptionHandler { _, throwable ->
        FailureLog.record(this, "settings collector", throwable)
    }
    private var scope = newScope()

    // A fresh one per connect: after a bank pause the system can connect this same instance
    // again, and a cancelled scope would silently start nothing.
    private fun newScope() =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineFailures)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: GestureSettingsRepository
    private lateinit var performer: GestureActionPerformer
    private lateinit var windowManager: WindowManager
    private var overlay: EdgeTouchOverlay? = null
    private var gestureBar: GestureBarHider? = null
    private var configuration: GestureConfiguration = GestureConfiguration()
    private var lastGeometry: ScreenGeometry? = null
    private var favourites: List<String> = emptyList()
    private var bankApps: Set<String> = emptySet()
    private var pausing = false
    private var skipLogged = false

    /**
     * Rotation reaches the display *after* the configuration change lands, so
     * [onConfigurationChanged] alone would race it.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) reinstallIfGeometryChanged()
        }
    }

    /**
     * An uncaught throw in setup makes Android mark the service "malfunctioning" and
     * refuse to re-bind it until the user toggles it by hand; degraded but bound is
     * better, and the Diagnostics section shows what failed.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        runningState.value = true
        ServiceLog.add(this, "service started")
        ServiceKeeper.markWanted(this, wanted = true)
        pausing = false
        endBankPause()
        try {
            setUp()
        } catch (t: Throwable) {
            FailureLog.record(this, "onServiceConnected", t)
        }
        GestureTileService.requestRefresh(this)
    }

    private fun setUp() {
        if (!scope.isActive) scope = newScope()
        settings = GestureSettingsRepository(applicationContext)
        performer = GestureActionPerformer(this)
        windowManager = getSystemService(WindowManager::class.java)
        overlay = EdgeTouchOverlay(
            context = this,
            windowManager = windowManager,
            replayer = this,
            onGestureAction = ::onGestureAction,
            switcher = performer.switcher,
            favourites = { favourites },
            onShortcut = { performer.performShortcut(it) },
        )

        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, handler)

        gestureBar = GestureBarHider(this, handler)
        scope.launch {
            PackageListStore.favourites(applicationContext).packages.collect { favourites = it }
        }
        scope.launch {
            PackageListStore.bankApps(applicationContext).packages.collect {
                bankApps = it.toSet()
                listenToBankApps()
            }
        }
        scope.launch {
            settings.configuration.distinctUntilChanged().collect { updated ->
                configuration = updated
                installOverlay()
                gestureBar?.apply(updated.hideGestureBar)
            }
        }
    }

    /**
     * The system unbinds when the switch goes off. The swipes go with it, so a bar this app
     * hid comes back first. A force stop skips this entirely, which is how [ServiceKeeper]
     * tells it apart from the user's own choice.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        if (BankPause.consumeGoingAway(this)) {
            // The bank pause switched us off; "wanted" stays, so a lost watcher still heals.
            ServiceLog.add(this, "service paused for a bank app")
        } else {
            ServiceLog.add(this, "service switched off")
            ServiceKeeper.markWanted(this, wanted = false)
            if (BankPause.paused(this) != null) {
                BankPause.clearPause(this)
                BankPause.stopWatcher(this)
            }
        }
        gestureBar?.restoreForShutdown()
        teardown()
        GestureTileService.requestRefresh(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reinstallIfGeometryChanged()
    }

    /**
     * The XML config requests no events. Only with bank apps picked does [listenToBankApps]
     * ask for window changes, and only from those apps, to start a bank pause.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName in bankApps) pauseFor(packageName)
    }

    /** Changes the current service info, so canPerformGestures and the rest stay as declared. */
    private fun listenToBankApps() {
        val info = serviceInfo ?: return
        val listen = !BuildConfig.IS_PLAY && bankApps.isNotEmpty()
        info.eventTypes = if (listen) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        info.packageNames = if (listen) bankApps.toTypedArray() else null
        runCatching { serviceInfo = info }
            .onFailure { FailureLog.record(this, "bank pause event filter", it) }
    }

    /**
     * Steps aside for a bank app: watcher first (while the strips still allow a background
     * start), the phone's own bar second and read back, the service last. Any step that fails
     * undoes the pause, so the user never ends up with neither gestures nor the bar.
     */
    private fun pauseFor(packageName: String) {
        if (pausing) return
        if (!BankPause.canPause(this)) {
            if (!skipLogged) {
                skipLogged = true
                ServiceLog.add(this, "bank pause skipped for $packageName: needs the adb grant and usage access")
            }
            return
        }
        if (BankPause.holdBlocks(this)) return
        pausing = true
        BankPause.savePause(this, packageName, bankApps, System.currentTimeMillis())
        if (!BankPause.startWatcher(this)) {
            undoPause("Android refused to start the watcher")
            return
        }
        gestureBar?.restoreForShutdown()
        if (configuration.hideGestureBar && GestureBarHider.isHidden(this)) {
            undoPause("the phone's own bar did not come back")
            return
        }
        ServiceLog.add(this, "bank pause: $packageName opened, switching off")
        Log.i(TAG, "BANK_PAUSE $packageName")
        disableSelf()
        confirmPause(attempt = 1)
    }

    /**
     * What a bank reads is the enabled list, so the list decides whether the pause took. The
     * emulator (API 36) drops the entry at once but may never call onUnbind, so once the entry
     * is gone the service takes down its own windows and state rather than wait for it.
     */
    private fun confirmPause(attempt: Int) {
        // A switch-off by the user in the middle of a pause ends the pause's own checks.
        if (instance == null || !pausing) return
        if (!DiagnosticsCollector.isAccessibilityServiceSwitchedOn(this)) {
            ServiceLog.add(this, "service paused for a bank app")
            teardown()
            GestureTileService.requestRefresh(this)
            return
        }
        if (attempt >= DISABLE_CHECKS) {
            undoPause("the system kept the service on")
            return
        }
        handler.postDelayed({ confirmPause(attempt + 1) }, DISABLE_CHECK_MS)
    }

    private fun undoPause(reason: String) {
        ServiceLog.add(this, "bank pause undone: $reason")
        Log.w(TAG, "BANK_PAUSE_UNDONE $reason")
        BankPause.clearPause(this)
        BankPause.consumeGoingAway(this)
        BankPause.stopWatcher(this)
        gestureBar?.apply(configuration.hideGestureBar)
        pausing = false
    }

    /** A saved pause ends when the service is back, however it came back. */
    private fun endBankPause() {
        if (BankPause.paused(this) == null) return
        BankPause.clearPause(this)
        BankPause.stopWatcher(this)
        ServiceLog.add(this, "bank pause over, gestures back")
    }

    /**
     * The user's own switch-off, from the Home switch or the tile. [ServiceKeeper.turnOff] has
     * cleared "wanted" already, so no heal turns the service back on. A bar this app hid comes
     * back before the gestures go.
     */
    private fun switchOffByUser(from: String) {
        if (instance == null) return
        ServiceLog.add(this, "$from: switching off")
        pausing = false
        BankPause.consumeGoingAway(this)
        if (BankPause.paused(this) != null) {
            BankPause.clearPause(this)
            BankPause.stopWatcher(this)
        }
        gestureBar?.restoreForShutdown()
        disableSelf()
        confirmOff(attempt = 1)
    }

    /** As [confirmPause]: the enabled list decides, since onUnbind may never come. */
    private fun confirmOff(attempt: Int) {
        if (instance == null) return
        if (!DiagnosticsCollector.isAccessibilityServiceSwitchedOn(this)) {
            ServiceLog.add(this, "service switched off")
            teardown()
            GestureTileService.requestRefresh(this)
            return
        }
        if (attempt >= DISABLE_CHECKS) {
            ServiceLog.add(this, "switch-off refused: the system kept the service on")
            ServiceKeeper.markWanted(this, wanted = true)
            gestureBar?.apply(configuration.hideGestureBar)
            return
        }
        handler.postDelayed({ confirmOff(attempt + 1) }, DISABLE_CHECK_MS)
    }

    override fun onInterrupt() = Unit

    private fun installOverlay() {
        val geometry = ScreenGeometry.current(this, windowManager)
        lastGeometry = geometry
        overlay?.install(configuration.copy(platform = platformDefaults()), geometry)
    }

    // Read on every install, so a changed touch and hold delay lands with the next settings
    // change or rotation. The AOSP values stand in if the platform refuses.
    private fun platformDefaults(): PlatformDefaults {
        val fallback = PlatformDefaults()
        val touchSlopDp = runCatching {
            ViewConfiguration.get(this).scaledTouchSlop / resources.displayMetrics.density
        }.getOrDefault(fallback.touchSlopDp)
        return fallback.copy(
            longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
            touchSlopDp = touchSlopDp,
        )
    }

    /**
     * Strip windows keep their pixel sizes across a rotation, so a side strip measured
     * against portrait height runs off a landscape screen.
     */
    private fun reinstallIfGeometryChanged() {
        if (overlay == null) return
        if (ScreenGeometry.current(this, windowManager) == lastGeometry) return
        installOverlay()
    }

    private fun teardown() {
        runCatching {
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .unregisterDisplayListener(displayListener)
        }
        overlay?.remove()
        // A failed setUp can get here before the performer exists.
        if (::performer.isInitialized) performer.close()
        gestureBar?.release()
        gestureBar = null
        overlay = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        instance = null
        runningState.value = false
    }

    private fun onGestureAction(action: GestureAction, edge: ScreenEdge) {
        Log.v(TAG, "Gesture from $edge -> $action")
        performer.perform(action)
    }

    /**
     * The strips have been non-touchable and alpha 0 since this touch's `ACTION_DOWN`, so
     * its own duration already counted toward the asynchronous window update and only the
     * remainder of [WINDOW_SETTLE_MS] still has to be waited out.
     */
    override fun replay(samples: List<TouchSample>, onDone: () -> Unit): Boolean {
        if (samples.isEmpty()) return false

        val first = samples.first()
        val last = samples.last()
        val movedPx = hypot(last.xPx - first.xPx, last.yPx - first.yPx)
        val isTap = movedPx < TAP_SLOP_PX
        val rawDurationMs = last.timestampMs - first.timestampMs

        val path = Path().apply {
            moveTo(first.xPx, first.yPx)
            if (isTap) {
                // dispatchGesture rejects a zero-length path. Nudge by a sub-slop pixel:
                // still a tap, not a drag.
                lineTo(first.xPx + 1f, first.yPx + 1f)
            } else {
                for (i in 1 until samples.size) lineTo(samples[i].xPx, samples[i].yPx)
            }
        }

        val durationMs = when {
            !isTap -> rawDurationMs.coerceIn(1L, MAX_REPLAY_MS)
            // A deliberate hold: mirror it so the target's long-press fires too.
            rawDurationMs >= ViewConfiguration.getLongPressTimeout() ->
                rawDurationMs.coerceAtMost(MAX_REPLAY_MS)
            // The click fires at the end of the stroke, so capping it cuts perceived
            // tap latency; the press physically happened already.
            else -> rawDurationMs.coerceIn(MIN_TAP_MS, TAP_STROKE_CAP_MS)
        }

        val gesture = runCatching {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()
        }.getOrElse {
            Log.w(TAG, "Could not build replay gesture", it)
            return false
        }

        val settleDelayMs = (WINDOW_SETTLE_MS - rawDurationMs).coerceAtLeast(0L)
        var finished = false
        val finish = Runnable {
            if (!finished) {
                finished = true
                onDone()
            }
        }
        // Safety net: release the strips even if the result callback never arrives.
        handler.postDelayed(finish, durationMs + settleDelayMs + FINISH_GRACE_MS)
        handler.postDelayed({
            // After this delay the service may be unbound, and dispatchGesture then throws
            // instead of returning false; uncaught that kills the process and gets the
            // service flagged "malfunctioning".
            val dispatched = runCatching {
                dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            releaseStrips()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            // The touch never reached the app underneath, so the tap is
                            // lost; the strips still have to come back either way.
                            Log.w(TAG, "REPLAY_CANCELLED after ${durationMs}ms")
                            releaseStrips()
                        }

                        private fun releaseStrips() {
                            handler.removeCallbacks(finish)
                            finish.run()
                        }
                    },
                    handler,
                )
            }.getOrElse { throwable ->
                FailureLog.record(this, "dispatchGesture", throwable)
                false
            }
            if (dispatched) {
                Log.i(TAG, "REPLAY_DISPATCHED ${samples.size} samples over ${durationMs}ms")
            } else {
                Log.w(TAG, "REPLAY_REFUSED dispatchGesture rejected the replay")
                handler.removeCallbacks(finish)
                finish.run()
            }
        }, settleDelayMs)
        return true
    }

    companion object {
        private const val TAG = "NavGesture"

        private const val MAX_REPLAY_MS = 3_000L

        /** A 1ms stroke completes but never clicks. */
        private const val MIN_TAP_MS = 50L

        private const val TAP_STROKE_CAP_MS = 60L

        private const val TAP_SLOP_PX = 12f

        /**
         * Grace for the non-touchable + alpha-0 update to reach the input pipeline, so the
         * injected touch is not discarded as obscured.
         */
        private const val WINDOW_SETTLE_MS = 65L

        private const val FINISH_GRACE_MS = 1_000L

        /** An upper bound on how long a replay holds the strips past its UP, safety net included. */
        internal const val LONGEST_REPLAY_HOLD_MS = MAX_REPLAY_MS + WINDOW_SETTLE_MS + FINISH_GRACE_MS

        /** disableSelf() gets [DISABLE_CHECKS] looks this far apart before it counts as refused. */
        private const val DISABLE_CHECK_MS = 500L
        private const val DISABLE_CHECKS = 7

        @Volatile
        private var instance: NavGestureAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        /** Asks the running service to switch itself off for the user. False when none runs. */
        fun turnOff(from: String): Boolean {
            val service = instance ?: return false
            service.handler.post { service.switchOffByUser(from) }
            return true
        }

        private val runningState = MutableStateFlow(false)

        /** Lets an open screen see the service start or stop without waiting for a resume. */
        val running: StateFlow<Boolean> = runningState.asStateFlow()
    }
}
