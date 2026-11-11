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

package com.vijayyaso.navgesture.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.vijayyaso.navgesture.diagnostics.ServiceLog
import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.ConfirmVerdict
import com.vijayyaso.navgesture.gesture.CornerShortcut
import com.vijayyaso.navgesture.gesture.GestureAction
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.GestureTracker
import com.vijayyaso.navgesture.gesture.HapticGate
import com.vijayyaso.navgesture.gesture.LandscapeConfirm
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.TouchRecording
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.gesture.TrackerAction

/**
 * Captures raw touches in thin strips along the screen edges and feeds them to a
 * per-edge [GestureTracker].
 *
 * A plain [android.accessibilityservice.AccessibilityService] receives no arbitrary
 * [MotionEvent]s, hence real windows; `TYPE_ACCESSIBILITY_OVERLAY` needs no
 * `SYSTEM_ALERT_WINDOW` permission. A strip must consume `ACTION_DOWN` to see whether a
 * swipe develops, so a touch that is not a gesture goes to [TouchReplayer] instead of
 * being swallowed.
 *
 * While a finger holds a strip, every strip goes `FLAG_NOT_TOUCHABLE` **and** alpha 0.
 * Applying it at `ACTION_DOWN` gives the asynchronous window update the whole touch to
 * take effect, since window changes only affect the targeting of *new* touches; alpha 0
 * because a visible non-touchable overlay still counts toward Android's
 * obscuring-opacity cap, and an injected touch under one is discarded as untrusted.
 */
internal class EdgeTouchOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val replayer: TouchReplayer,
    private val onGestureAction: (GestureAction, ScreenEdge) -> Unit,
    private val switcher: AppSwitcher,
    private val favourites: () -> List<String>,
    /** Runs a corner shortcut that is not an app list: a system action or an app. */
    private val onShortcut: (CornerShortcut) -> Unit,
    private val layout: OverlayLayout = OverlayLayout(),
) {

    private var configuration = GestureConfiguration()

    private val strips = mutableMapOf<ScreenEdge, View>()

    private val bubbles = mutableMapOf<ScreenEdge, FluidBubbleOverlay>()

    private var switcherPanel: SwitcherPanelOverlay? = null
    private var switcherController: EdgeSwitcherController? = null

    private val hold = StreamHold(StreamHold.STUCK_STREAM_MS)

    private var heldEdge: ScreenEdge? = null

    // Owned here rather than by a strip, so a pending pill is kept across a reinstall.
    private val confirmation = LandscapeConfirm()

    private var pill: ConfirmPillOverlay? = null

    private var pillVisible = false

    private val handler = Handler(Looper.getMainLooper())

    private val pillExpiry = Runnable(::onPillTimeout)

    private val stuckCheck = Runnable(::onStuckCheck)

    fun install(configuration: GestureConfiguration, geometry: ScreenGeometry) {
        remove()
        this.configuration = configuration
        confirmation.configure(configuration.landscapeConfirmEnabled, geometry.landscape)
        if (configuration.landscapeConfirmEnabled && geometry.landscape) {
            pill = ConfirmPillOverlay(context, windowManager, geometry).also { it.install() }
        }
        syncPill()

        val listShortcut = listOf(configuration.leftCornerShortcut, configuration.rightCornerShortcut)
            .any { it == CornerShortcut.RecentsList || it == CornerShortcut.FavouritesList }
        if (configuration.edgeAppListEnabled || listShortcut) {
            val stripPx = (configuration.edgeWidthDp * geometry.density).toInt() + geometry.navBottomPx
            val panel = SwitcherPanelOverlay(context, windowManager, geometry, stripPx).also { it.install() }
            switcherPanel = panel
            switcherController = EdgeSwitcherController(
                panel = panel,
                switcher = switcher,
                favourites = favourites,
                density = geometry.density,
                style = configuration.appListStyle,
                stepDp = configuration.switcherStepDp,
                adaptiveSpeed = configuration.switcherAdaptiveSpeed,
            ) { feedback ->
                if (configuration.hapticFeedbackEnabled) {
                    strips.values.firstOrNull()?.performHapticFeedback(feedback)
                }
            }
        }

        val edges = buildList {
            if (configuration.leftEdgeBackEnabled) add(ScreenEdge.LEFT)
            if (configuration.rightEdgeBackEnabled) add(ScreenEdge.RIGHT)
            add(ScreenEdge.BOTTOM)
        }

        for (edge in edges) {
            bubbles[edge] = FluidBubbleOverlay(context, windowManager, edge, geometry).also {
                it.install()
            }
            addStrip(edge, configuration, geometry)
        }
        applyInteractivity()
    }

    fun remove() {
        switcherController?.cancel()
        switcherController = null
        switcherPanel?.remove()
        switcherPanel = null
        strips.values.forEach { view ->
            (view as? TouchCaptureView)?.dispose()
            runCatching { windowManager.removeView(view) }
        }
        strips.clear()
        bubbles.values.forEach(FluidBubbleOverlay::remove)
        bubbles.clear()
        handler.removeCallbacks(pillExpiry)
        handler.removeCallbacks(stuckCheck)
        pill?.remove()
        pill = null
        pillVisible = false
        // Detaching kills any in-flight stream and its UP. The new token also keeps a replay
        // still in flight from freeing the next install's strips mid-touch.
        hold.reset()
        heldEdge = null
    }

    private fun addStrip(
        edge: ScreenEdge,
        configuration: GestureConfiguration,
        geometry: ScreenGeometry,
    ) {
        val view = TouchCaptureView(
            context = context,
            edge = edge,
            tracker = GestureTracker(
                edge,
                configuration,
                geometry.density,
                // Display coordinates, like the rawX the tracker is fed.
                handleCenterXPx = if (edge == ScreenEdge.BOTTOM) geometry.widthPx / 2f else null,
                screenHeightPx = geometry.heightPx.toFloat(),
                screenWidthPx = geometry.widthPx.toFloat(),
            ),
            hapticsEnabled = configuration.hapticFeedbackEnabled,
            bubble = bubbles[edge],
            controller = switcherController,
            dragListEnabled = configuration.edgeAppListEnabled,
            onStreamStart = { onStreamStart(edge) },
            onActivity = ::onActivity,
            admit = { stream, action -> hold.isCurrent(stream) && admit(action, edge) },
            onFire = { stream, action -> if (hold.isCurrent(stream)) onFire(action, edge) },
            onUnused = { stream, samples -> onUnused(edge, stream, samples) },
            onStreamEnd = { stream -> endStream(edge, stream) },
        )
        val params = paramsFor(edge, configuration, geometry)
        runCatching {
            windowManager.addView(view, params)
            strips[edge] = view
            Log.i(TAG, "strip $edge installed ${params.width}x${params.height} geometry=$geometry")
        }.onFailure { Log.e(TAG, "Could not add $edge strip", it) }
    }

    private fun paramsFor(
        edge: ScreenEdge,
        configuration: GestureConfiguration,
        geometry: ScreenGeometry,
    ): WindowManager.LayoutParams {
        val thicknessPx = (configuration.edgeWidthDp * geometry.density).toInt().coerceAtLeast(1)

        // Each strip is extended across its own edge's navigation-bar inset: the bar is
        // slippery, so a swipe starting on it lands on us only after leaving the bar.
        val (widthPx, heightPx, gravity) = when (edge) {
            ScreenEdge.LEFT -> Triple(
                thicknessPx + geometry.navLeftPx,
                (geometry.heightPx * layout.sideLengthPercent / 100).coerceAtLeast(1),
                Gravity.START or Gravity.CENTER_VERTICAL,
            )
            ScreenEdge.RIGHT -> Triple(
                thicknessPx + geometry.navRightPx,
                (geometry.heightPx * layout.sideLengthPercent / 100).coerceAtLeast(1),
                Gravity.END or Gravity.CENTER_VERTICAL,
            )
            ScreenEdge.BOTTOM -> Triple(
                bottomSpan(configuration, geometry).let { (left, right) -> right - left }.coerceAtLeast(1),
                thicknessPx + geometry.navBottomPx,
                Gravity.BOTTOM or Gravity.LEFT,
            )
        }

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            // Without this the strip is pushed inside the inset it was just extended across.
            fitInsetsTypes = 0
            if (edge == ScreenEdge.BOTTOM) x = bottomSpan(configuration, geometry).first
        }
    }

    /**
     * The bottom strip's left and right x. Centred and short of both corners, so they stay
     * natively touchable, except that a corner with a shortcut is covered to the screen edge.
     */
    private fun bottomSpan(configuration: GestureConfiguration, geometry: ScreenGeometry): Pair<Int, Int> {
        val margin = geometry.widthPx * (100 - layout.bottomLengthPercent) / 200
        val left = if (configuration.leftCornerShortcut != CornerShortcut.None) 0 else margin
        val right = if (configuration.rightCornerShortcut != CornerShortcut.None) {
            geometry.widthPx
        } else {
            geometry.widthPx - margin
        }
        return left to right
    }

    private fun onStreamStart(edge: ScreenEdge): Long {
        switcherController?.onStreamStart()
        bubbles.values.forEach(FluidBubbleOverlay::hide)
        val now = SystemClock.uptimeMillis()
        confirmation.onDown(edge, now)
        syncPill()
        val stream = hold.start(now)
        heldEdge = edge
        applyInteractivity()
        scheduleStuckCheck()
        return stream
    }

    private fun onActivity(stream: Long) {
        if (!hold.activity(stream, SystemClock.uptimeMillis())) return
        // The finger was only still, and its replay would be routed back into touchable strips.
        Log.w(TAG, "STREAM_HELD_AGAIN a touch event followed the stuck release")
        applyInteractivity()
        scheduleStuckCheck()
    }

    /** False when the gesture is held back behind the pill instead. */
    private fun admit(action: GestureAction, edge: ScreenEdge): Boolean {
        val verdict = confirmation.onFire(action, edge, SystemClock.uptimeMillis())
        syncPill()
        if (verdict is ConfirmVerdict.ShowPill) Log.i(TAG, "GESTURE_CONFIRM_SHOWN $edge ${action.id}")
        return verdict is ConfirmVerdict.Perform
    }

    private fun onFire(action: GestureAction, edge: ScreenEdge) {
        if (action is GestureAction.Corner) {
            // After the stream ends and the window update settles: the strips are visible again,
            // which HyperOS needs to allow an app launch, and a screenshot misses the bubble.
            handler.postDelayed({ runCorner(action.side) }, CORNER_SETTLE_MS)
            return
        }
        onGestureAction(action, edge)
    }

    private fun runCorner(side: BottomCorner) {
        when (val shortcut = configuration.shortcutFor(side)) {
            CornerShortcut.None -> Unit
            CornerShortcut.RecentsList -> switcherController?.openForTouch(recents = true)
            CornerShortcut.FavouritesList -> switcherController?.openForTouch(recents = false)
            else -> onShortcut(shortcut)
        }
    }

    private fun onUnused(edge: ScreenEdge, stream: Long, samples: List<TouchSample>) {
        if (!hold.isCurrent(stream)) return
        if (!hold.mayReplay(stream)) {
            Log.d(TAG, "replay skipped, the strips were given back during this touch")
            endStream(edge, stream)
            return
        }
        confirmation.onReplayed(edge)
        syncPill(immediate = true)
        val dispatched = replayer.replay(samples) { releaseStream(stream, "replay done") }
        if (!dispatched) releaseStream(stream, "replay refused")
    }

    private fun endStream(edge: ScreenEdge, stream: Long) {
        if (!hold.isCurrent(stream)) return
        confirmation.onStreamEnd(edge, SystemClock.uptimeMillis())
        syncPill()
        releaseStream(stream, "stream ended")
    }

    private fun onPillTimeout() {
        confirmation.expire(SystemClock.uptimeMillis())
        if (!confirmation.pillShown) Log.i(TAG, "GESTURE_CONFIRM_EXPIRED")
        syncPill()
    }

    // Brings the pill window and its expiry timer in line with the model.
    private fun syncPill(immediate: Boolean = false) {
        val expiresAt = confirmation.expiresAtMs
        val overlay = pill
        if (expiresAt != null && overlay != null) {
            if (!pillVisible) overlay.show()
            pillVisible = true
            handler.removeCallbacks(pillExpiry)
            handler.postAtTime(pillExpiry, expiresAt)
            return
        }
        handler.removeCallbacks(pillExpiry)
        if (!pillVisible) return
        pillVisible = false
        if (immediate) overlay?.hide() else overlay?.fadeOut()
    }

    private fun releaseStream(stream: Long, reason: String) {
        if (!hold.release(stream)) {
            Log.d(TAG, "release ignored, stream no longer held ($reason)")
            return
        }
        handler.removeCallbacks(stuckCheck)
        applyInteractivity()
        Log.d(TAG, "stream released ($reason), strips interactive again")
    }

    private fun scheduleStuckCheck() {
        handler.removeCallbacks(stuckCheck)
        hold.deadlineMs?.let { handler.postAtTime(stuckCheck, it) }
    }

    // A stream the system ends without UP or CANCEL leaves every strip inert, since an inert
    // strip can no longer receive the event that releases it. Seen upstream on the Redmi 14
    // with all three strips held and no finger down.
    private fun onStuckCheck() {
        val now = SystemClock.uptimeMillis()
        if (!hold.expire(now)) {
            scheduleStuckCheck()
            return
        }
        // Edge and timing only: touch positions never leave memory.
        val event = "touch strips freed after ${StreamHold.STUCK_STREAM_MS} ms without a touch event"
        Log.w(TAG, "STREAM_STUCK $event, held by ${heldEdge ?: "none"}")
        ServiceLog.add(context, event)
        bubbles.values.forEach(FluidBubbleOverlay::hide)
        heldEdge?.let { confirmation.onStreamEnd(it, now) }
        syncPill()
        applyInteractivity()
    }

    /** See the class comment for why this is alpha 0 as well as non-touchable. */
    private fun applyInteractivity() {
        val interactive = !hold.held
        strips.values.forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            val flags = if (interactive) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            val alpha = if (interactive) 1f else 0f
            if (flags == params.flags && params.alpha == alpha) return@forEach
            params.flags = flags
            params.alpha = alpha
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    /** Reads coordinates and timestamps only — never window content, text or keystrokes. */
    @SuppressLint("ViewConstructor")
    private class TouchCaptureView(
        context: Context,
        private val edge: ScreenEdge,
        private val tracker: GestureTracker,
        private val hapticsEnabled: Boolean,
        private val bubble: FluidBubbleOverlay?,
        private val controller: EdgeSwitcherController?,
        private val dragListEnabled: Boolean,
        private val onStreamStart: () -> Long,
        private val onActivity: (Long) -> Unit,
        private val admit: (Long, GestureAction) -> Boolean,
        private val onFire: (Long, GestureAction) -> Unit,
        private val onUnused: (Long, List<TouchSample>) -> Unit,
        private val onStreamEnd: (Long) -> Unit,
    ) : View(context) {

        // The overlay's token for the touch in progress, so it can tell a stale callback apart.
        private var stream = 0L

        private val samples = TouchRecording(MAX_SAMPLES)
        private var abandoned = false
        private var switching = false
        private val haptics = HapticGate()

        // MotionEvent times are uptime, so the pause poll reads the same clock.
        private val holdRunnable = Runnable {
            val action = tracker.onHoldElapsed(SystemClock.uptimeMillis())
            // The bottom bubble swaps to the Recents icon on the poll that primes it.
            samples.last?.let { bubble?.follow(action, it.xPx, it.yPx) }
            handle(action, replayable = false)
        }

        private val longPressRunnable =
            Runnable { handle(tracker.onLongPressElapsed(), replayable = false) }

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Keep the platform's own back gesture off these pixels.
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        }

        // Nothing here to click: a real tap is replayed to the app underneath instead.
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // rawX/rawY are display coordinates, which is what the replay path expects.
            val sample = TouchSample(event.rawX, event.rawY, event.eventTime)
            if (event.actionMasked != MotionEvent.ACTION_DOWN) onActivity(stream)
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    samples.clear()
                    abandoned = false
                    switching = false
                    haptics.reset()
                    samples.add(sample)
                    Log.d(TAG, "$edge DOWN at ${sample.xPx.toInt()},${sample.yPx.toInt()}")
                    stream = onStreamStart()
                    track(tracker.onDown(sample), sample, replayable = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!abandoned) {
                        samples.add(sample)
                        if (switching) controller?.move(sample) else
                            track(tracker.onMove(sample), sample, replayable = false)
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Never a navigation gesture, and replaying a partial pinch would be
                    // worse than dropping it.
                    Log.d(TAG, "$edge ABANDONED second pointer went down")
                    abandoned = true
                    switching = false
                    controller?.cancel()
                    bubble?.release()
                    cancelHold()
                    tracker.onCancel()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cancelHold()
                    if (switching) {
                        switching = false
                        tracker.onCancel()
                        samples.clear()
                        // HyperOS needs a visible accessibility strip to allow the launch.
                        // Restore it before release hides the panel and starts the activity.
                        onStreamEnd(stream)
                        controller?.release(sample)
                        return true
                    }
                    controller?.cancel()
                    // Every UP must end the stream, or the strips stay inert forever after
                    // the first gesture. Only a started replay defers the release.
                    val releaseDeferred = if (abandoned) {
                        false
                    } else {
                        samples.add(sample)
                        track(tracker.onUp(sample), sample, replayable = true)
                    }
                    samples.clear()
                    if (!releaseDeferred) onStreamEnd(stream)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Commonly SystemUI when the touch began on the navigation bar, which
                    // sits above us in Z order.
                    Log.d(TAG, "$edge CANCELLED stream taken over by another window")
                    switching = false
                    controller?.cancel()
                    bubble?.release()
                    cancelHold()
                    tracker.onCancel()
                    samples.clear()
                    onStreamEnd(stream)
                    true
                }
                else -> false
            }
        }

        /**
         * [replayable] only on release: a mid-gesture rejection must not inject anything
         * while the finger is still down. Returns true when the release was deferred.
         */
        private fun handle(action: TrackerAction, replayable: Boolean): Boolean {
            val admitted = action !is TrackerAction.Fire || admit(stream, action.action)
            val buzz = haptics.shouldTick(action)
            // One buzz per gesture.
            when {
                !admitted && buzz -> confirmTick()
                !admitted || !buzz -> Unit
                action is TrackerAction.Fire && action.action == GestureAction.CircleToSearch -> longPressTick()
                else -> tick()
            }
            when (action) {
                is TrackerAction.None -> Unit
                is TrackerAction.OpenSwitcher -> {
                    cancelHold()
                    bubble?.hide()
                    switching = true
                    controller?.open(action)
                    samples.last?.let { controller?.move(it) }
                }
                is TrackerAction.CornerReady -> {
                    // The diagonal is a shortcut now, not a Home swipe: the drop goes away.
                    cancelHold()
                    bubble?.release()
                    Log.i(TAG, "CORNER_READY ${action.corner}")
                }
                is TrackerAction.ScheduleLongPress -> {
                    removeCallbacks(longPressRunnable)
                    postDelayed(longPressRunnable, action.delayMs)
                }
                is TrackerAction.Armed -> {
                    // A swipe is under way, so this touch can no longer be a long press.
                    removeCallbacks(longPressRunnable)
                    Log.d(TAG, "$edge armed (pause poll=${action.scheduleHoldMs})")
                    // The rise is live: from here a sideways drag opens the app list, so say so.
                    if (edge == ScreenEdge.BOTTOM) confirmTick()
                    action.scheduleHoldMs?.let { postDelayed(holdRunnable, it) }
                }
                is TrackerAction.RearmHold -> {
                    removeCallbacks(holdRunnable)
                    postDelayed(holdRunnable, action.delayMs)
                }
                // The release now means Recents; the haptic gate ticks for it.
                is TrackerAction.RecentsPrimed -> Log.d(TAG, "$edge RECENTS_PRIMED")
                is TrackerAction.Fire -> {
                    cancelHold()
                    if (admitted) {
                        // action.id, not ::class.simpleName - R8 obfuscates class names.
                        Log.i(TAG, "GESTURE_FIRED $edge ${action.action.id}")
                        onFire(stream, action.action)
                    }
                }
                is TrackerAction.Disarmed -> Log.d(TAG, "$edge disarmed, pulled back inside the trigger")
                // Consumed rather than replayed: the app never asked for this swipe.
                is TrackerAction.Reverted -> Log.i(TAG, "GESTURE_REVERTED $edge")
                is TrackerAction.Unused -> {
                    cancelHold()
                    Log.d(TAG, "$edge unused (${action.reason})")
                    if (replayable && !samples.isEmpty()) {
                        onUnused(stream, samples.snapshot())
                        return true
                    }
                }
            }
            return false
        }

        // The bubble moves first. A replay must never land under a visible window.
        private fun track(action: TrackerAction, sample: TouchSample, replayable: Boolean): Boolean {
            bubble?.follow(action, sample.xPx, sample.yPx)
            // Only a real swipe can pause into the app list, so a tap on the bottom strip
            // never pays for a package lookup. Arming happens once per gesture per edge.
            if (action is TrackerAction.Armed && dragListEnabled) controller?.prepare(edge)
            return handle(action, replayable)
        }

        /** Detaching must invalidate callbacks even when the finger never sends UP. */
        fun dispose() {
            cancelHold()
            controller?.cancel()
            tracker.onCancel()
            switching = false
            abandoned = true
            samples.clear()
        }

        override fun onDetachedFromWindow() {
            dispose()
            super.onDetachedFromWindow()
        }

        // Both timers, since a long press left scheduled past the end of a touch would fire on it.
        private fun cancelHold() {
            removeCallbacks(holdRunnable)
            removeCallbacks(longPressRunnable)
        }

        /** View haptics rather than the Vibrator API: no VIBRATE permission needed. */
        private fun tick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        }

        /** Lighter than a gesture, since nothing happened yet. */
        private fun confirmTick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        /** The system's long-press buzz, so the hold reads as having landed. */
        private fun longPressTick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        private companion object {
            const val MAX_SAMPLES = 400
        }
    }

    private companion object {
        const val TAG = "NavGesture"

        /**
         * Wait before a corner shortcut runs: the strips' return to visible is an async
         * window update, and an app launch is allowed only once a visible overlay is back.
         */
        const val CORNER_SETTLE_MS = 80L
    }
}
