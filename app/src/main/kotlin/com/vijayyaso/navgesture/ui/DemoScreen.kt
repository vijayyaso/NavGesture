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

import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.max
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.demo.DemoConfiguration
import com.vijayyaso.navgesture.demo.DemoEngine
import com.vijayyaso.navgesture.demo.DemoEvent
import com.vijayyaso.navgesture.demo.DemoHint
import com.vijayyaso.navgesture.demo.DemoMiss
import com.vijayyaso.navgesture.demo.DemoProgress
import com.vijayyaso.navgesture.demo.DemoResult
import com.vijayyaso.navgesture.demo.DemoStep
import com.vijayyaso.navgesture.demo.DemoTouchZone
import com.vijayyaso.navgesture.diagnostics.ServiceStatus
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.RejectionReason
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.TouchSample
import com.vijayyaso.navgesture.service.StripPause
import com.vijayyaso.navgesture.ui.theme.IconTint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The practice demo: a phone drawn on the screen that the user swipes on, one gesture per step.
 * Swipes are judged by the real recogniser through [DemoEngine], and nothing real ever runs.
 * The step and the try count survive a rotation; a pass still playing is saved as passed.
 */
@Composable
internal fun DemoScreen(model: AppModel, onClose: () -> Unit, onSetUp: () -> Unit) {
    var progress by rememberSaveable(stateSaver = DemoProgressSaver) { mutableStateOf(DemoProgress()) }
    val step = progress.step
    if (step == null) {
        FinishCard(setupDone = model.checklist.essentialsDone, onClose = onClose, onSetUp = onSetUp)
        return
    }
    // The service's strips sit above the app and would run a practice swipe for real, so they
    // let touches through while the drawn phone is in front. In split screen both apps stay
    // resumed, so window focus decides: the strips work again over the other app.
    val focused = LocalWindowInfo.current.isWindowFocused
    LifecycleResumeEffect(focused) {
        StripPause.set(focused)
        onPauseOrDispose { StripPause.set(false) }
    }
    // A screen reader user cannot draw these swipes, so every step can be skipped at once.
    val touchExploration = rememberTouchExploration()

    var effect by remember { mutableStateOf<DemoResult?>(null) }
    var effectRun by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf<SwipeAction?>(null) }
    var list by remember { mutableStateOf<DemoListState?>(null) }
    var fingerDown by remember { mutableStateOf(false) }

    // A pass plays its effect, then moves on; another gesture plays its effect as briefly.
    LaunchedEffect(effectRun) {
        if (effect == null) return@LaunchedEffect
        delay(EFFECT_SHOWN_MS)
        effect = null
        if (progress.celebrating != null) progress = progress.advance()
    }
    LaunchedEffect(step) {
        ready = null
        list = null
    }

    val handle: (List<DemoEvent>) -> Unit = { events ->
        events.forEach { event ->
            when (event) {
                is DemoEvent.Ready -> ready = event.action
                DemoEvent.Unready -> ready = null
                is DemoEvent.ListOpened -> {
                    ready = null
                    list = DemoListState(event.recents, index = 0)
                }
                is DemoEvent.ListMoved -> list = list?.copy(index = event.index)
                is DemoEvent.Performed -> {
                    ready = null
                    list = null
                    effect = event.result
                    effectRun++
                    progress = progress.after(event)
                }
                is DemoEvent.Missed -> {
                    ready = null
                    list = null
                    progress = progress.after(event)
                }
            }
        }
    }

    StepText(step, progress.hint)
    Spacer(Modifier.size(PHONE_GAP))
    PracticePhone(
        step = step,
        state = DemoPhoneState(step, effect, effectRun, ready, list, fingerDown),
        edgeStripDp = model.configuration.edgeWidthDp.takeIf { model.status != ServiceStatus.OFF },
        accepts = { progress.celebrating == null },
        onFinger = { fingerDown = it },
        onEvents = handle,
    )
    Spacer(Modifier.size(PHONE_GAP))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = buttonRowHeight())
            .padding(horizontal = 16.dp),
    ) {
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .weight(1f)
                .testTag(TestTags.DEMO_SKIP),
        ) {
            Text(stringResource(R.string.demo_skip))
        }
        if ((progress.canSkipStep || touchExploration) && progress.celebrating == null) {
            FilledTonalButton(
                onClick = {
                    effect = null
                    progress = progress.advance()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.DEMO_SKIP_STEP),
            ) {
                Text(stringResource(R.string.demo_skip_step))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** "Step n of 7", the step's name, what to do, and the hint. The hint keeps three lines. */
@Composable
private fun StepText(step: DemoStep, hint: DemoHint?) {
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = TEXT_TOP)) {
        Text(
            text = stringResource(R.string.demo_step_count, step.number, DemoStep.entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(TestTags.DEMO_STEP),
        )
        Spacer(Modifier.size(TEXT_GAP))
        Text(
            text = stringResource(step.title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(TEXT_GAP))
        SupportingText(stringResource(step.instruction))
        Spacer(Modifier.size(HINT_GAP))
        // Read out when it changes, and always three lines tall, so the phone never moves.
        Text(
            text = hint?.let { hintText(it, step) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .heightIn(min = lineHeight * HINT_LINES)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * The drawn phone with its touch handling. A touch that starts on the drawing or in its margin
 * is consumed, so that swipe never scrolls the page. While a finger is down the hold poll runs
 * once a frame.
 */
@Composable
private fun PracticePhone(
    step: DemoStep,
    state: DemoPhoneState,
    edgeStripDp: Float?,
    accepts: () -> Boolean,
    onFinger: (Boolean) -> Unit,
    onEvents: (List<DemoEvent>) -> Unit,
) {
    val density = LocalDensity.current
    val layout = phoneLayout(edgeStripDp, reservedHeight())
    val widthDp = with(density) { layout.widthPx.toDp() }
    val configuration = DemoConfiguration.forStep(step, widthDp.value)
    val engine = remember(layout.widthPx, layout.heightPx, layout.screenHeightPx, density.density) {
        DemoEngine(
            widthPx = layout.widthPx.toFloat(),
            heightPx = layout.heightPx.toFloat(),
            screenHeightPx = layout.screenHeightPx,
            density = density.density,
            configuration = configuration,
        )
    }
    // Applied at once between touches, and after the lift when a finger is down.
    SideEffect { engine.configure(configuration) }

    val settled = LocalScreenSettled.current
    val latestEvents by rememberUpdatedState(onEvents)
    val latestAccepts by rememberUpdatedState(accepts)
    val latestFinger by rememberUpdatedState(onFinger)
    LaunchedEffect(engine, state.fingerDown) {
        if (!state.fingerDown) return@LaunchedEffect
        // A still finger sends no MOVE; MotionEvent times are uptime, so the poll reads that clock.
        while (true) {
            withFrameMillis { }
            latestEvents(engine.frame(SystemClock.uptimeMillis()))
        }
    }

    val description = stringResource(R.string.demo_phone_description, step.number, stringResource(step.title))
    val zone = remember(layout.widthPx, layout.heightPx, density.density) {
        DemoTouchZone(layout.widthPx.toFloat(), layout.heightPx.toFloat(), DemoTouchZone.MARGIN_DP * density.density)
    }
    val offsetX = layout.offsetXPx.toFloat()
    // The touch area is the phone's whole row plus a margin below it, so a thumb that starts a
    // little past a drawn edge still counts (DemoTouchZone). A touch that starts outside the zone
    // is left alone, so it can still scroll the page.
    Box(
        contentAlignment = AbsoluteAlignment.TopLeft,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(engine, zone, offsetX) {
                // A node still sliding in reports a still finger as moving, so a touch that
                // starts before the screen settles, or while a pass plays, is not judged.
                awaitPointerEventScope {
                    var primary: PointerId? = null
                    var judging = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            event.changes.forEach { change ->
                                val x = change.position.x - offsetX
                                val y = change.position.y
                                val (drawnX, drawnY) = zone.onDrawing(x, y)
                                val sample = TouchSample(drawnX, drawnY, change.uptimeMillis)
                                val isPrimary = change.id == primary
                                val firstDown = change.changedToDownIgnoreConsumed() && primary == null && pressed == 1
                                if (firstDown && !zone.accepts(x, y)) return@forEach
                                if (primary == null && !firstDown) return@forEach
                                when {
                                    firstDown -> {
                                        primary = change.id
                                        judging = settled() && latestAccepts()
                                        if (judging) {
                                            latestFinger(true)
                                            latestEvents(engine.down(sample))
                                        }
                                    }
                                    change.changedToDownIgnoreConsumed() ->
                                        if (judging) latestEvents(engine.extraFinger())
                                    isPrimary && change.changedToUpIgnoreConsumed() -> {
                                        if (judging) {
                                            latestEvents(engine.up(sample))
                                            latestFinger(false)
                                        }
                                        primary = null
                                        judging = false
                                    }
                                    isPrimary && change.positionChangeIgnoreConsumed() != Offset.Zero ->
                                        if (judging) latestEvents(engine.move(sample))
                                }
                                change.consume()
                            }
                        }
                    } finally {
                        // The engine was replaced (a new size) or the screen left mid-touch.
                        if (judging) {
                            engine.cancel()
                            latestFinger(false)
                        }
                    }
                }
            }
            .padding(bottom = DemoTouchZone.MARGIN_DP.dp),
    ) {
        DemoPhone(
            state = state,
            recentsLinePx = layout.screenHeightPx * GestureConfiguration().recentsLinePercent / 100f,
            longLinePx = with(density) { DemoConfiguration.longLineDp(widthDp.value).dp.toPx() },
            modifier = Modifier
                .absoluteOffset { IntOffset(layout.offsetXPx, 0) }
                .size(widthDp, with(density) { layout.heightPx.toDp() })
                .clearAndSetSemantics {
                    contentDescription = description
                    testTag = TestTags.DEMO_PHONE
                },
        )
    }
}

/** The drawn phone's size and place, all in pixels. */
private data class PhoneLayout(
    val widthPx: Int,
    val heightPx: Int,
    /** From the left of the screen's column, which the scaffold insets from the window. */
    val offsetXPx: Int,
    /** The real display's height, which the tracker scales its Recents line and pause with. */
    val screenHeightPx: Float,
)

/**
 * Width = min(320 dp, the room between the gaps, what fits the window's height), at least 200
 * dp; 9 wide to 19 high. Each gap to a real side edge is the larger of 40 dp, the system's
 * gesture inset and, with the service not off, the service's own strip there, plus 8 dp: a
 * swipe started on a live strip would run a real Back.
 */
@Composable
private fun phoneLayout(edgeStripDp: Float?, reservedHeight: Dp): PhoneLayout {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val window = LocalWindowInfo.current.containerSize
    val context = LocalContext.current
    // The scaffold pads every screen by these, so they place the screen's column in the window.
    val bars = ScaffoldDefaults.contentWindowInsets
    val gestures = WindowInsets.systemGestures
    val navigation = WindowInsets.navigationBars
    val displayHeight = remember(context, window) {
        context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.height()
    }
    return with(density) {
        fun gap(gesture: Int, navigationBar: Int): Float {
            val strip = edgeStripDp?.let { it.dp.toPx() + navigationBar } ?: 0f
            return maxOf(MIN_EDGE_GAP.toPx(), gesture.toFloat(), strip) + EXTRA_EDGE_GAP.toPx()
        }
        val left = gap(gestures.getLeft(this, direction), navigation.getLeft(this, direction))
        val right = gap(gestures.getRight(this, direction), navigation.getRight(this, direction))
        val room = window.width - left - right
        // The screen's own room: the window less the bars, the top app bar and the column's padding.
        val viewport = window.height - bars.getTop(this) - bars.getBottom(this) -
            TopAppBarDefaults.TopAppBarExpandedHeight.toPx() - SCREEN_BOTTOM_PADDING.toPx()
        val byHeight = (viewport - reservedHeight.toPx()) * PHONE_WIDE / PHONE_HIGH
        val width = minOf(MAX_PHONE_WIDTH.toPx(), room, byHeight).coerceAtLeast(MIN_PHONE_WIDTH.toPx())
        val x = if (room >= width) left + (room - width) / 2f else (window.width - width) / 2f
        PhoneLayout(
            widthPx = width.roundToInt(),
            heightPx = (width * PHONE_HIGH / PHONE_WIDE).roundToInt(),
            offsetXPx = (x - bars.getLeft(this, direction)).coerceAtLeast(0f).roundToInt(),
            screenHeightPx = displayHeight.toFloat(),
        )
    }
}

/** Everything on the screen but the phone, at the current font size, so the phone fits under it. */
@Composable
private fun reservedHeight(): Dp {
    val typography = MaterialTheme.typography
    return with(LocalDensity.current) {
        TEXT_TOP + typography.labelLarge.lineHeight.toDp() + TEXT_GAP + typography.titleLarge.lineHeight.toDp() +
            TEXT_GAP + typography.bodyMedium.lineHeight.toDp() * (INSTRUCTION_LINES + HINT_LINES) + HINT_GAP +
            PHONE_GAP * 2 + DemoTouchZone.MARGIN_DP.dp + buttonRowHeight()
    }
}

@Composable
private fun buttonRowHeight(): Dp = with(LocalDensity.current) {
    max(MIN_BUTTON_ROW, MaterialTheme.typography.labelLarge.lineHeight.toDp() + BUTTON_PADDING)
}

@Composable
private fun FinishCard(setupDone: Boolean, onClose: () -> Unit, onSetUp: () -> Unit) {
    SettingsGroup {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            IconBadge(GroupIcon(Icons.Filled.CheckCircle, IconTint.GREEN), size = 36.dp)
            Text(text = stringResource(R.string.demo_finish_title), style = MaterialTheme.typography.titleMedium)
            SupportingText(
                stringResource(if (setupDone) R.string.demo_finish_body_done else R.string.demo_finish_body_setup),
            )
        }
    }
    // Full width and stacked, so a large font never squeezes either button.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    ) {
        if (!setupDone) {
            Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.demo_set_up_now))
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.DEMO_DONE),
            ) {
                Text(stringResource(R.string.demo_done))
            }
        } else {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.DEMO_DONE),
            ) {
                Text(stringResource(R.string.demo_done))
            }
        }
    }
}

/** Whether a screen reader drives the screen by touch, kept current while the demo shows. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/** The hint for a try that did not pass, by what happened and the step it happened in. */
@Composable
private fun hintText(hint: DemoHint, step: DemoStep): String = when (hint) {
    is DemoHint.Other -> {
        val action = (hint.result as? DemoResult.Ran)?.run?.action
        when {
            step == DemoStep.RECENTS && action == SwipeAction.Home -> stringResource(R.string.demo_hint_recents_home)
            step == DemoStep.WAIT && action == SwipeAction.Back -> stringResource(R.string.demo_hint_wait_back)
            step == DemoStep.LONG && action == SwipeAction.Back -> stringResource(R.string.demo_hint_long_back)
            step == DemoStep.APP_LIST && hint.result == DemoResult.FavouritesOpened ->
                stringResource(R.string.demo_hint_favourites)
            else -> stringResource(R.string.demo_hint_other, resultName(hint.result))
        }
    }
    is DemoHint.Miss -> when (val reason = hint.reason) {
        DemoMiss.NotOnEdge -> stringResource(
            if (step == DemoStep.CORNER) R.string.demo_hint_not_in_corner else R.string.demo_hint_not_on_edge,
        )
        DemoMiss.Cancelled -> stringResource(
            if (step == DemoStep.APP_LIST) R.string.demo_hint_list_closed else R.string.demo_hint_cancelled,
        )
        DemoMiss.PulledBack -> stringResource(R.string.demo_hint_pulled_back)
        is DemoMiss.Rejected -> stringResource(
            when (reason.reason) {
                RejectionReason.NO_MOVEMENT -> R.string.demo_hint_tap
                RejectionReason.INSUFFICIENT_MOVEMENT -> R.string.demo_hint_short
                RejectionReason.WRONG_DIRECTION -> R.string.demo_hint_wrong_way
                RejectionReason.TOO_SLOW_TO_ARM -> R.string.demo_hint_slow
                RejectionReason.OFF_AXIS -> R.string.demo_hint_slanted
                RejectionReason.SLOT_EMPTY -> R.string.demo_hint_nothing
            },
        )
    }
}

@Composable
private fun resultName(result: DemoResult): String = when (result) {
    is DemoResult.Ran -> shortcutLabel(result.run.action)
    is DemoResult.AppPicked -> stringResource(R.string.demo_name_app_list)
    DemoResult.FavouritesOpened -> stringResource(R.string.demo_name_favourites)
    DemoResult.CircleToSearch -> stringResource(R.string.section_circle_to_search)
}

@get:StringRes
private val DemoStep.title: Int
    get() = when (this) {
        DemoStep.BACK -> R.string.demo_step_back
        DemoStep.HOME -> R.string.demo_step_home
        DemoStep.RECENTS -> R.string.demo_step_recents
        DemoStep.APP_LIST -> R.string.demo_step_app_list
        DemoStep.CORNER -> R.string.demo_step_corner
        DemoStep.WAIT -> R.string.demo_step_wait
        DemoStep.LONG -> R.string.demo_step_long
    }

@get:StringRes
private val DemoStep.instruction: Int
    get() = when (this) {
        DemoStep.BACK -> R.string.demo_do_back
        DemoStep.HOME -> R.string.demo_do_home
        DemoStep.RECENTS -> R.string.demo_do_recents
        DemoStep.APP_LIST -> R.string.demo_do_app_list
        DemoStep.CORNER -> R.string.demo_do_corner
        DemoStep.WAIT -> R.string.demo_do_wait
        DemoStep.LONG -> R.string.demo_do_long
    }

/** Only the step and the tries are kept; a pass still playing is kept as passed. */
private val DemoProgressSaver = Saver<DemoProgress, ArrayList<Int>>(
    save = { progress ->
        val kept = if (progress.celebrating != null) progress.advance() else progress
        arrayListOf(kept.step?.ordinal ?: -1, kept.tries)
    },
    restore = { saved ->
        DemoProgress(step = DemoStep.entries.getOrNull(saved.getOrElse(0) { 0 }), tries = saved.getOrElse(1) { 0 })
    },
)

/** A pass shows its effect this long before the next step. */
private const val EFFECT_SHOWN_MS = 900L

private const val PHONE_WIDE = 9f
private const val PHONE_HIGH = 19f
private const val INSTRUCTION_LINES = 3
private const val HINT_LINES = 3
private val MAX_PHONE_WIDTH = 320.dp
private val MIN_PHONE_WIDTH = 200.dp
private val MIN_EDGE_GAP = 40.dp
private val EXTRA_EDGE_GAP = 8.dp

/** MainActivity's bottom padding under every screen's column. */
private val SCREEN_BOTTOM_PADDING = 24.dp
private val TEXT_TOP = 4.dp
private val TEXT_GAP = 4.dp
private val HINT_GAP = 8.dp
private val PHONE_GAP = 12.dp
private val MIN_BUTTON_ROW = 48.dp
private val BUTTON_PADDING = 28.dp
