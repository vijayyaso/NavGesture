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

package com.vijayyaso.navgesture.gesture

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Tracks one finger on one screen edge. Contains no Android types on purpose: that is the
 * whole reason gesture recognition can be tested on a plain JVM.
 *
 * [GestureConfiguration.maximumArmDurationMs] gates only the pre-arm phase; once armed a
 * gesture may be held indefinitely, or swipe-and-hold for Recents would be discarded.
 *
 * The bottom edge copies the POCO launcher (`NavStubView`, `GestureHomeCalculator`) rather
 * than timing a hold. A follow point chases a damped "pivot" of the finger a quarter of the
 * way every frame; the swipe has paused when it catches up. A pause counts only past the
 * Recents line, so a slow Home swipe that hesitates near the bottom stays Home. The pause
 * only primes Recents, with a tick: the release decides, and a fast upward flick is still
 * Home.
 *
 * Released low and not moving up, a bottom swipe reverts to the app it started on, as POCO's
 * does in its safe area.
 *
 * Arming has deliberately no minimum-velocity threshold, which would penalise the slow,
 * deliberate swipes a careful user makes.
 */
class GestureTracker(
    private val edge: ScreenEdge,
    private val configuration: GestureConfiguration,
    private val density: Float,
    /** Display x of the handle's centre, where a long press counts, or null for the whole edge. */
    private val handleCenterXPx: Float? = null,
    /** Full display height; the POCO constants scale with it. */
    private val screenHeightPx: Float = REFERENCE_HEIGHT_PX,
) {

    // What an armed gesture became, as one field.
    private sealed interface Arm {
        /** Armed for [shortAction]; on the bottom edge Recents is not yet primed. */
        data object Rising : Arm

        /** Paused past the Recents line: the release is Recents unless it is a flick. */
        data object Primed : Arm

        /** The app selector owns movement and release until the stream ends. */
        data object Switcher : Arm

        /** A long press already fired mid-gesture, so the release does nothing. */
        data object Fired : Arm

        /** A side swipe pulled back inside the trigger, which re-arms if pushed out again. */
        data object Retracted : Arm
    }

    // The DOWN sample, kept after tracking ends so a release can still be explained: the arm
    // timeout stops the gesture and the UP behind it still reports a rejection reason.
    private var start: TouchSample? = null

    // The same sample while the gesture is live, and null when nothing is tracked. One field
    // rather than a boolean next to a nullable, which could disagree with it.
    private var tracked: TouchSample? = null

    // Null until the gesture arms.
    private var arm: Arm? = null

    // A still touch that may yet become a long press. Once the finger strays it stays cleared,
    // and a slow swipe that pauses later cannot start one.
    private var longPressPending = false

    // The latest sample, and the recent ones the release velocity is measured over.
    private var latest: TouchSample? = null
    private val recent = ArrayDeque<TouchSample>()

    // The follow point, in pivot coordinates, and when it was last advanced.
    private var tailX = 0f
    private var tailY = 0f
    private var tailAtMs = 0L

    private val backEnabled: Boolean
        get() = when (edge) {
            ScreenEdge.LEFT -> configuration.leftEdgeBackEnabled
            ScreenEdge.RIGHT -> configuration.rightEdgeBackEnabled
            ScreenEdge.BOTTOM -> true
        }

    private val shortAction: GestureAction =
        if (edge == ScreenEdge.BOTTOM) GestureAction.Home else GestureAction.Back

    private val holdAction: GestureAction? =
        if (edge == ScreenEdge.BOTTOM) GestureAction.Recents else null

    private val longPressAction: GestureAction? =
        if (edge == ScreenEdge.BOTTOM && configuration.circleToSearchEnabled) {
            GestureAction.CircleToSearch
        } else {
            null
        }

    private val minimumDistancePx: Float
        get() = density * when (edge) {
            ScreenEdge.BOTTOM -> configuration.bottomMinimumSwipeDistanceDp
            ScreenEdge.LEFT, ScreenEdge.RIGHT -> configuration.sideMinimumSwipeDistanceDp
        }

    private val recentsLinePx: Float
        get() = screenHeightPx * configuration.recentsLinePercent / 100f

    // POCO's pixel constants were written for a 2400px-tall screen, so they scale with it.
    private val tailGapPx: Float get() = screenHeightPx * TAIL_GAP_OF_HEIGHT
    private val liftPx: Float get() = screenHeightPx * LIFT_OF_HEIGHT

    private val longPressSlopPx: Float get() = configuration.platform.touchSlopDp * density

    // A side hold is a deliberate gesture, so a resting finger may drift more than the
    // system's 8 dp touch slop before Back starts. Keep the bottom handle strict.
    private val holdSlopPx: Float
        get() = if (edge == ScreenEdge.BOTTOM) longPressSlopPx else
            maxOf(longPressSlopPx, density * SWITCHER_HOLD_SLOP_DP)

    private val handleHalfWidthPx: Float
        get() = density / 2f * maxOf(
            configuration.platform.handleWidthDp,
            PlatformDefaults.MIN_TOUCH_TARGET_DP,
        )

    fun onDown(sample: TouchSample): TrackerAction {
        start = sample
        tracked = sample
        arm = null
        latest = sample
        recent.clear()
        recent.addLast(sample)
        tailX = pivotX(sample)
        tailY = pivotY(sample)
        tailAtMs = sample.timestampMs
        longPressPending = if (edge == ScreenEdge.BOTTOM) {
            longPressAction != null && overHandle(sample)
        } else {
            configuration.edgeAppListEnabled
        }
        return if (longPressPending) {
            TrackerAction.ScheduleLongPress(
                if (edge == ScreenEdge.BOTTOM) configuration.effectiveCircleToSearchHoldMs else EDGE_HOLD_MS,
            )
        } else {
            TrackerAction.None
        }
    }

    fun onMove(sample: TouchSample): TrackerAction {
        if (arm == Arm.Switcher) return TrackerAction.None
        val origin = tracked ?: return TrackerAction.None
        if (longPressPending && strayedBeyondSlop(origin, sample)) longPressPending = false
        // The tail chases the pivot as it stood until now, then the pivot moves.
        advanceTail(sample.timestampMs)
        remember(sample)
        val state = arm
        return if (state == null) whileArming(origin, sample) else whileArmed(origin, state, sample)
    }

    private fun whileArming(origin: TouchSample, sample: TouchSample): TrackerAction {
        // A pending long press holds the arm window open, or a system touch and hold delay
        // of 1000 or 1500 ms would be cut off by it and never fire.
        if (!longPressPending &&
            sample.timestampMs - origin.timestampMs > configuration.maximumArmDurationMs
        ) {
            tracked = null
            return TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM)
        }
        if (!backEnabled || !hasArmed(origin, sample)) return TrackerAction.None

        arm = Arm.Rising
        return TrackerAction.Armed(scheduleHoldMs = if (holdAction != null) FRAME_MS else null)
    }

    private fun whileArmed(origin: TouchSample, state: Arm, sample: TouchSample): TrackerAction {
        if (holdAction == null) return followSide(origin, state, sample)
        // A primed or fired gesture owns the release.
        if (state != Arm.Rising) return TrackerAction.None
        return TrackerAction.None
    }

    // A back swipe is judged by where the finger is, against the same distance that armed it,
    // so pulling back inside the trigger disarms it and pushing out again re-arms it.
    private fun followSide(origin: TouchSample, state: Arm, sample: TouchSample): TrackerAction {
        val retracted = state is Arm.Retracted
        val past = isPastTrigger(origin, sample)
        if (retracted && past) {
            arm = Arm.Rising
            return TrackerAction.Armed(scheduleHoldMs = null)
        }
        if (!retracted && !past) {
            arm = Arm.Retracted
            return TrackerAction.Disarmed
        }
        return TrackerAction.None
    }

    /**
     * Polled every frame while a bottom swipe is rising, because a finger held still sends
     * no MOVE at all and the tail still has to catch up with it.
     */
    fun onHoldElapsed(nowMs: Long): TrackerAction {
        if (holdAction == null || arm != Arm.Rising) return TrackerAction.None
        val origin = tracked ?: return TrackerAction.None
        val sample = latest ?: return TrackerAction.None
        advanceTail(nowMs)
        val pastLine = travelFrom(origin, sample) >= recentsLinePx
        val slow = speedDpPerMs(nowMs) < HOLD_MAX_DP_PER_MS
        if (pastLine && slow && tailDistance(sample) < tailGapPx) {
            arm = Arm.Primed
            return TrackerAction.RecentsPrimed
        }
        return TrackerAction.RearmHold(FRAME_MS)
    }

    /** Fires only for a touch still down, unarmed and within the slop since DOWN. */
    fun onLongPressElapsed(): TrackerAction {
        if (!longPressPending || arm != null) return TrackerAction.None
        if (edge != ScreenEdge.BOTTOM) {
            val origin = tracked ?: return TrackerAction.None
            longPressPending = false
            arm = Arm.Switcher
            return TrackerAction.OpenSwitcher(edge, origin)
        }
        val action = longPressAction ?: return TrackerAction.None
        longPressPending = false
        // Marked fired, so the release is consumed rather than read as Home or replayed.
        arm = Arm.Fired
        return TrackerAction.Fire(action, edge)
    }

    fun onUp(sample: TouchSample): TrackerAction {
        val origin = tracked
        val state = arm
        tracked = null
        longPressPending = false

        if (state == Arm.Switcher || state is Arm.Fired) return TrackerAction.None
        if (origin == null) return TrackerAction.Unused(rejectionFor(start, sample))
        remember(sample)
        if (state != null) {
            // The release point decides, so a swipe brought back to the edge in one batched
            // event reverts as surely as one that disarmed on the way.
            if (holdAction == null && !isPastTrigger(origin, sample)) return TrackerAction.Reverted
            if (holdAction != null && isPulledBack(origin, sample)) return TrackerAction.Reverted
            if (state == Arm.Primed) {
                val flick = upwardDpPerMs(sample.timestampMs) > configuration.homeFlickDpPerMs
                return TrackerAction.Fire(if (flick) shortAction else GestureAction.Recents, edge)
            }
            return TrackerAction.Fire(shortAction, edge)
        }

        // A stream can arrive as DOWN then UP with no MOVE between - event batching under
        // load does this, and a slow emulator does it reliably. The release is still a
        // swipe if it clears the threshold on its own, so judge it by the same rule.
        if (sample.timestampMs - origin.timestampMs <= configuration.maximumArmDurationMs) {
            if (backEnabled && hasArmed(origin, sample)) return TrackerAction.Fire(shortAction, edge)
        }
        return TrackerAction.Unused(rejectionFor(origin, sample))
    }

    fun onCancel() {
        start = null
        tracked = null
        arm = null
        longPressPending = false
        latest = null
        recent.clear()
    }

    /** Low on the screen and not rising: POCO's safe area with no fast pull up. */
    private fun isPulledBack(origin: TouchSample, sample: TouchSample): Boolean =
        travelFrom(origin, sample) < recentsLinePx * RETURN_LINE_SHARE &&
            upwardDpPerMs(sample.timestampMs) <= 0f

    private fun remember(sample: TouchSample) {
        latest = sample
        recent.addLast(sample)
        // One sample older than the window stays, as the anchor the velocity is measured from.
        while (recent.size > 1 && sample.timestampMs - recent[1].timestampMs >= VELOCITY_WINDOW_MS) {
            recent.removeFirst()
        }
    }

    /** POCO's `updateTail`: a quarter of the gap closed per 16ms frame, kept frame-rate free. */
    private fun advanceTail(nowMs: Long) {
        val sample = latest ?: return
        val elapsed = nowMs - tailAtMs
        if (elapsed <= 0L) return
        val kept = TAIL_KEEP_PER_FRAME.pow(elapsed.toFloat() / FRAME_MS)
        tailX = pivotX(sample) + (tailX - pivotX(sample)) * kept
        tailY = pivotY(sample) + (tailY - pivotY(sample)) * kept
        tailAtMs = nowMs
    }

    private fun tailDistance(sample: TouchSample): Float =
        hypot(pivotX(sample) - tailX, pivotY(sample) - tailY)

    /** POCO's pivot: half the sideways travel, and the upward travel eased by a cubic. */
    private fun pivotX(sample: TouchSample): Float {
        val down = start ?: return sample.xPx
        return (sample.xPx + down.xPx) / 2f
    }

    private fun pivotY(sample: TouchSample): Float {
        val down = start ?: return sample.yPx
        val progress = ((down.yPx - sample.yPx) / screenHeightPx).coerceIn(0f, 1f)
        return -(1f - (1f - progress).pow(3)) * liftPx
    }

    /**
     * Measured up to [nowMs], not to the last sample: a finger held still sends nothing, so
     * the time since its last MOVE has to count as standing still.
     */
    private fun speedDpPerMs(nowMs: Long): Float {
        val anchor = anchorFor(nowMs) ?: return 0f
        val last = latest ?: return 0f
        val elapsed = nowMs - anchor.timestampMs
        if (elapsed <= 0L) return 0f
        return hypot(last.xPx - anchor.xPx, last.yPx - anchor.yPx) / elapsed / density
    }

    private fun upwardDpPerMs(nowMs: Long): Float {
        val anchor = anchorFor(nowMs) ?: return 0f
        val last = latest ?: return 0f
        val elapsed = nowMs - anchor.timestampMs
        if (elapsed <= 0L) return 0f
        return (anchor.yPx - last.yPx) / elapsed / density
    }

    /** The newest sample at least a window old, or the oldest one kept. */
    private fun anchorFor(nowMs: Long): TouchSample? =
        recent.lastOrNull { nowMs - it.timestampMs >= VELOCITY_WINDOW_MS } ?: recent.firstOrNull()

    // Launcher3 aborts a handle long press past the platform touch slop, as a distance.
    private fun strayedBeyondSlop(reference: TouchSample, sample: TouchSample): Boolean {
        val dx = sample.xPx - reference.xPx
        val dy = sample.yPx - reference.yPx
        return dx * dx + dy * dy > holdSlopPx * holdSlopPx
    }

    private fun overHandle(sample: TouchSample): Boolean {
        val center = handleCenterXPx ?: return true
        return abs(sample.xPx - center) <= handleHalfWidthPx
    }

    private fun hasArmed(origin: TouchSample, sample: TouchSample): Boolean {
        val travelPx = travelFrom(origin, sample)
        if (travelPx < minimumDistancePx) return false
        val offAxisPx = offAxisFrom(origin, sample)
        return abs(offAxisPx) <= travelPx * configuration.offAxisToleranceRatio
    }

    private fun isPastTrigger(origin: TouchSample, sample: TouchSample): Boolean =
        travelFrom(origin, sample) >= minimumDistancePx

    /** Positive inward, negative outward. */
    private fun travelFrom(origin: TouchSample, sample: TouchSample): Float = when (edge) {
        ScreenEdge.LEFT -> sample.xPx - origin.xPx
        ScreenEdge.RIGHT -> origin.xPx - sample.xPx
        ScreenEdge.BOTTOM -> origin.yPx - sample.yPx
    }

    private fun offAxisFrom(origin: TouchSample, sample: TouchSample): Float = when (edge) {
        ScreenEdge.LEFT, ScreenEdge.RIGHT -> sample.yPx - origin.yPx
        ScreenEdge.BOTTOM -> sample.xPx - origin.xPx
    }

    private fun rejectionFor(origin: TouchSample?, sample: TouchSample): RejectionReason {
        if (origin == null) return RejectionReason.NO_MOVEMENT
        val travelPx = travelFrom(origin, sample)
        val offAxisPx = abs(offAxisFrom(origin, sample))
        val movedAtAll = abs(travelPx) > 0f || offAxisPx > 0f
        return when {
            !movedAtAll -> RejectionReason.NO_MOVEMENT
            travelPx < 0f -> RejectionReason.WRONG_DIRECTION
            travelPx < minimumDistancePx -> RejectionReason.INSUFFICIENT_MOVEMENT
            offAxisPx > travelPx * configuration.offAxisToleranceRatio -> RejectionReason.OFF_AXIS
            sample.timestampMs - origin.timestampMs > configuration.maximumArmDurationMs ->
                RejectionReason.TOO_SLOW_TO_ARM
            else -> RejectionReason.INSUFFICIENT_MOVEMENT
        }
    }

    companion object {
        /** A still side touch opens the app selector after this delay. */
        const val EDGE_HOLD_MS = 300L

        /** A resting finger may wander this far before the side hold becomes a Back swipe. */
        const val SWITCHER_HOLD_SLOP_DP = 24f

        /** POCO samples its tail once per 60Hz frame. */
        const val FRAME_MS = 16L

        /** The screen height POCO's pixel constants were written for. */
        const val REFERENCE_HEIGHT_PX = 2400f

        /** `updateTail` closes a quarter of the gap per frame, keeping three quarters. */
        private const val TAIL_KEEP_PER_FRAME = 0.75f

        /** `isPauseWhileSwipeUp`'s 25px tail gap on a 2400px screen. */
        private const val TAIL_GAP_OF_HEIGHT = 25f / REFERENCE_HEIGHT_PX

        /** `updatePivotLoc` lifts the pivot at most 444px on a 2400px screen. */
        private const val LIFT_OF_HEIGHT = 444f / REFERENCE_HEIGHT_PX

        /** `isToAppHoldState` also needs the finger slower than `density` px/ms. */
        private const val HOLD_MAX_DP_PER_MS = 1f

        /** A release below this share of the line, not moving up, goes back to the app. */
        private const val RETURN_LINE_SHARE = 0.5f

        /** The span the release velocity is measured over. */
        private const val VELOCITY_WINDOW_MS = 80L
    }
}
