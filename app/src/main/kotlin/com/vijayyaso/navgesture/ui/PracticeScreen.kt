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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.demo.DemoEvent
import com.vijayyaso.navgesture.demo.DemoProgress
import com.vijayyaso.navgesture.demo.DemoStep
import com.vijayyaso.navgesture.demo.PracticePlan
import com.vijayyaso.navgesture.demo.PracticeSides
import com.vijayyaso.navgesture.demo.PracticeTarget
import com.vijayyaso.navgesture.diagnostics.ServiceStatus
import com.vijayyaso.navgesture.gesture.BottomCorner
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.ScreenEdge
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.gesture.SwipeKind
import com.vijayyaso.navgesture.gesture.SwipeZone
import com.vijayyaso.navgesture.service.NavGestureAccessibilityService
import com.vijayyaso.navgesture.service.PracticeEvent
import com.vijayyaso.navgesture.service.PracticeSession
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/**
 * The one place that picks what "Try the gestures" opens: the live page while the service is
 * working, touch exploration is off, the app is not in split screen or a floating window, and
 * the user's settings give at least one step; else the drawn demo. The Home row, "Practise now",
 * "Try again" and a restored live page all ask here, so making it return [Screen.DEMO] turns live
 * practice off everywhere. A debug build can force the drawn demo for the `demo` suite.
 */
internal fun practiceOrDemo(model: AppModel, touchExploration: Boolean, multiWindow: Boolean): Screen {
    if (model.forcesDrawnDemo()) return Screen.DEMO
    val plan = PracticePlan.steps(model.configuration, NavGestureAccessibilityService.unsupported.value)
    val live = PracticePlan.useLive(
        working = model.status == ServiceStatus.WORKING,
        touchExploration = touchExploration,
        multiWindow = multiWindow,
        planEmpty = plan.isEmpty(),
    )
    return if (live) Screen.PRACTICE else Screen.DEMO
}

/**
 * [practiceOrDemo], to call at the moment of the tap, with the screen reader and the window mode
 * kept current here.
 */
@Composable
internal fun rememberPracticeEntry(model: AppModel): () -> Screen {
    val touchExploration = rememberTouchExploration()
    val multiWindow = rememberMultiWindow()
    return { practiceOrDemo(model, touchExploration, multiWindow) }
}

/**
 * Whether the activity is in split screen or a floating window. Read on each composition, as a
 * change of window size recreates the activity, and kept current by the activity's listener for
 * a change without a recreate.
 */
@Composable
internal fun rememberMultiWindow(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity
    var changed by remember(activity) { mutableStateOf<Boolean?>(null) }
    DisposableEffect(activity) {
        val listener = Consumer<MultiWindowModeChangedInfo> { changed = it.isInMultiWindowMode }
        activity?.addOnMultiWindowModeChangedListener(listener)
        onDispose { activity?.removeOnMultiWindowModeChangedListener(listener) }
    }
    return changed ?: (activity?.isInMultiWindowMode == true)
}

/** The settings the strips were last built with, kept while a rebind takes them away. */
private class LastStrips {
    var configuration: GestureConfiguration? = null
}

/** The page's hold on the gate: in memory only, never saved, so it can never outlive the page. */
private class GateHold {
    var token = 0L

    fun end() {
        PracticeSession.end(token)
        token = 0L
    }
}

/**
 * Live practice: the user swipes the real screen edges, with their own settings. The real strips
 * stay on and the real recogniser judges each swipe, but while this page holds the gate the
 * service reports the gesture here instead of running it. The page holds the gate only while it
 * is resumed, focused, not [leaving], on a step, and the strips are not landscape; any other
 * moment it ends it at once, and the 2 s lease ends it anyway if the page stops renewing.
 *
 * [onSwap] replaces this page on the back stack; every hand-off to the drawn demo uses it.
 */
@Composable
internal fun PracticeScreen(
    model: AppModel,
    leaving: Boolean,
    onClose: () -> Unit,
    onSwap: (Screen) -> Unit,
    onSetUp: () -> Unit,
) {
    PortraitLock()
    val strips by PracticeSession.strips.collectAsState()
    val unsupported by NavGestureAccessibilityService.unsupported.collectAsState()
    // The settings the strips were built with, also through a rebind (each uiautomator dump does
    // one), when they are away for a moment; before the first install, the app's settings flow.
    val lastStrips = remember { LastStrips() }
    strips?.let { lastStrips.configuration = it.configuration }
    val configuration = lastStrips.configuration ?: model.configuration
    val plan = remember(configuration, unsupported) { PracticePlan.steps(configuration, unsupported) }
    val currentPlan by rememberUpdatedState(plan)
    var saved by rememberSaveable(stateSaver = PracticeProgressSaver) { mutableStateOf(DemoProgress.start(plan)) }
    // A settings change rebuilds the plan: the step stays if the plan still has it, else the next.
    val progress = saved.withPlan(plan)
    val step = progress.step
    val gate = remember { GateHold() }
    val landscape = strips?.landscape == true

    val choice = rememberPracticeEntry(model)()
    val handOff: () -> Unit = {
        gate.end()
        // An empty plan has no step of its own, so the drawn demo gets the step the page was on.
        model.demoStartStep = saved.withPlan(currentPlan).step ?: saved.step
        onSwap(Screen.DEMO)
    }
    // A service stop (after model.status's own grace), TalkBack, split screen, an empty plan, or
    // a restored page that should no longer be live: the drawn demo carries on at this step.
    // [saved] is read, not [step]: an empty plan makes [step] null.
    val handingOff = !leaving && saved.step != null && (choice != Screen.PRACTICE || plan.isEmpty())
    if (handingOff) LaunchedEffect(Unit) { handOff() }
    // Strips that stay landscape: Android did not honour the portrait lock.
    if (!leaving && step != null && landscape) {
        LaunchedEffect(Unit) {
            delay(LANDSCAPE_HAND_OFF_MS)
            handOff()
        }
    }

    val ownBack = stringResource(R.string.practice_own_back)
    val tryAgain = stringResource(R.string.practice_try_again)
    // Any Back that reaches the app closes the page. On the Back step with the gate on, it was the
    // phone's own Back, from outside the strips, so the screen under the page says so.
    BackHandler(enabled = !leaving) {
        val renewed = gate.token != 0L && PracticeSession.renew(gate.token)
        val ownBackHint = renewed && strips != null && step == DemoStep.BACK
        gate.end()
        onClose()
        if (ownBackHint) model.showMessage(ownBack, tryAgain) { model.practiceRequested = true }
    }
    val close = {
        gate.end()
        onClose()
    }

    if (step == null) {
        // An empty plan hands off instead; a plan that ran out shows the finish card.
        if (handingOff) return
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag(TestTags.PRACTICE_PAGE),
        ) {
            FinishCard(setupDone = model.checklist.essentialsDone, onClose = close, onSetUp = onSetUp)
        }
        return
    }

    var result by remember { mutableStateOf<PracticeEvent.Caught?>(null) }
    var listWaiting by remember { mutableStateOf(false) }
    val onEvent by rememberUpdatedState<(PracticeEvent) -> Unit> { event ->
        val current = saved.withPlan(currentPlan)
        // No step is taken while the strips are away, and nothing counts while a pass shows.
        if (current.step != null && current.celebrating == null && strips != null) {
            when (event) {
                is PracticeEvent.Caught -> {
                    result = event
                    listWaiting = false
                    saved = current.after(DemoEvent.Performed(event.result))
                }
                is PracticeEvent.Missed -> {
                    result = null
                    listWaiting = false
                    saved = current.after(DemoEvent.Missed(event.reason))
                }
                PracticeEvent.ListWaiting -> listWaiting = true
            }
        }
    }
    LaunchedEffect(progress.celebrating, step) {
        if (progress.celebrating == null) return@LaunchedEffect
        delay(EFFECT_SHOWN_MS)
        saved = saved.withPlan(currentPlan).advance()
    }

    // The gate rule. The Finish card returned above, so this runs only on a step.
    val focused = LocalWindowInfo.current.isWindowFocused
    val scope = rememberCoroutineScope()
    if (!handingOff) {
        LifecycleResumeEffect(focused, leaving, landscape) {
            val job = if (focused && !leaving && !landscape) {
                scope.launch {
                    // Subscribed before the gate is taken, so no gesture lands between the two.
                    launch {
                        PracticeSession.events
                            .onSubscription { gate.token = PracticeSession.begin() }
                            .collect { onEvent(it) }
                    }
                    while (true) {
                        delay(RENEW_MS)
                        // A lease that ran out, as on a main-thread stall, is taken again at once.
                        if (!PracticeSession.renew(gate.token)) gate.token = PracticeSession.begin()
                    }
                }
            } else {
                null
            }
            onPauseOrDispose {
                job?.cancel()
                gate.end()
            }
        }
    }

    val targets = PracticePlan.target(step, configuration, unsupported)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.PRACTICE_PAGE),
    ) {
        PracticeArrows(targets, Modifier.fillMaxSize())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top centre: the side strips are at the far left and right, the bottom strip below.
            TextButton(onClick = close, modifier = Modifier.testTag(TestTags.PRACTICE_CLOSE)) {
                Text(stringResource(R.string.practice_close))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TEXT_GAP, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SIDE_PADDING),
            ) {
                StepLines(
                    step = step,
                    plan = plan,
                    paused = !focused,
                    waiting = strips == null || landscape,
                    does = stepActionLabel(step, targets, configuration),
                    sides = PracticePlan.sides(step, configuration, unsupported),
                )
                Text(
                    text = result?.let { practiceResult(it.result, it.label) }.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = RESULT_GAP)
                        .testTag(TestTags.PRACTICE_RESULT)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
                Text(
                    text = when {
                        listWaiting -> stringResource(R.string.practice_hint_list_tap)
                        else -> progress.hint?.let { hintText(it, step) }.orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .heightIn(min = lineHeight * HINT_LINES)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (progress.canSkipStep && progress.celebrating == null) {
                    FilledTonalButton(
                        onClick = {
                            result = null
                            listWaiting = false
                            saved = saved.withPlan(currentPlan).advance()
                        },
                        modifier = Modifier.testTag(TestTags.PRACTICE_SKIP_STEP),
                    ) {
                        Text(stringResource(R.string.demo_skip_step))
                    }
                }
            }
        }
    }
}

/** The paused notice, the waiting line, "Step n of m", the step's name, what it does and what to do. */
@Composable
private fun StepLines(
    step: DemoStep,
    plan: List<DemoStep>,
    paused: Boolean,
    waiting: Boolean,
    does: String,
    sides: PracticeSides?,
) {
    if (paused) {
        Text(
            text = stringResource(R.string.practice_paused),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(TestTags.PRACTICE_PAUSED),
        )
    }
    // An extra line: the step and the result stay as they are while the strips come back.
    if (waiting) {
        Text(
            text = stringResource(R.string.practice_waiting),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(TestTags.PRACTICE_WAITING),
        )
    }
    Text(
        text = stringResource(R.string.demo_step_count, plan.indexOf(step) + 1, plan.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(TestTags.PRACTICE_STEP),
    )
    Text(
        text = stringResource(step.practiceTitle),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    SupportingText(stringResource(R.string.practice_does, does))
    Spacer(Modifier.size(TEXT_GAP))
    Text(
        text = practiceInstruction(step, sides),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

/** What the step's swipe does on this phone: the action of the slot the arrow points at. */
@Composable
private fun stepActionLabel(step: DemoStep, targets: List<PracticeTarget>, configuration: GestureConfiguration): String {
    val action = when (val first = targets.firstOrNull()) {
        is PracticeTarget.Side -> configuration.slots[first.slot]
        is PracticeTarget.Corner -> configuration.shortcutFor(first.corner)
        PracticeTarget.Bottom -> when (step) {
            DemoStep.HOME -> configuration.slots[SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT)]
            DemoStep.RECENTS -> configuration.slots[SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE)]
            else -> null
        }
        null -> null
    }
    return if (action == null || action == SwipeAction.None) {
        stringResource(R.string.demo_step_app_list)
    } else {
        shortcutLabel(action)
    }
}

/**
 * A moving arrow at each real edge the step starts from, sliding inward along the swipe and
 * repeating. With animations off it stands still.
 */
@Composable
private fun PracticeArrows(targets: List<PracticeTarget>, modifier: Modifier) {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val travel: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "practice arrow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ARROW_CYCLE_MS, easing = LinearEasing)),
            label = "travel",
        )
    } else {
        remember { mutableFloatStateOf(STILL_TRAVEL) }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        targets.forEach { drawArrow(it, travel.value, color) }
    }
}

private fun DrawScope.drawArrow(target: PracticeTarget, travel: Float, color: Color) {
    val (start, direction, length) = when (target) {
        is PracticeTarget.Side -> {
            val y = when (target.slot.zone) {
                SwipeZone.WHOLE -> size.height / 2f
                SwipeZone.TOP_HALF -> size.height * UPPER_ZONE
                SwipeZone.BOTTOM_HALF -> size.height * LOWER_ZONE
            }
            val far = if (target.slot.kind == SwipeKind.LONG) LONG_SHARE else SHORT_SHARE
            val left = target.slot.edge == ScreenEdge.LEFT
            Triple(Offset(if (left) 0f else size.width, y), Offset(if (left) 1f else -1f, 0f), size.width * far)
        }
        PracticeTarget.Bottom -> Triple(Offset(size.width / 2f, size.height), Offset(0f, -1f), size.height * SHORT_SHARE)
        is PracticeTarget.Corner -> {
            val left = target.corner == BottomCorner.LEFT
            val unit = 1f / sqrt(2f)
            Triple(
                Offset(if (left) 0f else size.width, size.height),
                Offset(if (left) unit else -unit, -unit),
                size.width * SHORT_SHARE,
            )
        }
    }
    val tip = start + direction * (length * travel)
    val alpha = 1f - travel * FADE
    val stroke = ARROW_STROKE.toPx()
    drawLine(color.copy(alpha = alpha * TAIL_ALPHA), start, tip, strokeWidth = stroke, cap = StrokeCap.Round)
    // The head: two short lines back from the tip, at 45 degrees to the swipe.
    val head = ARROW_HEAD.toPx()
    val back = direction * -head
    val side = Offset(-direction.y, direction.x) * head
    drawLine(color.copy(alpha = alpha), tip, tip + back + side, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = alpha), tip, tip + back - side, strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * Upright while the page shows. The old value comes back only when the page really leaves: the
 * dispose that the lock's own recreate causes must not undo it, and the value from before the
 * lock is kept across that recreate.
 */
// Practice in landscape is a non-goal (owner, 2026-11-18): the landscape confirm pill must never
// show in practice, and the arrows point at the edges as they are upright.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun PortraitLock() {
    val activity = LocalActivity.current ?: return
    val original = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (!activity.isChangingConfigurations) activity.requestedOrientation = original
        }
    }
}

/** Only the step and the tries are kept, as in the drawn demo; a pass still playing is kept as passed. */
private val PracticeProgressSaver = Saver<DemoProgress, ArrayList<Int>>(
    save = { progress ->
        val kept = if (progress.celebrating != null) progress.advance() else progress
        arrayListOf(kept.step?.ordinal ?: -1, kept.tries)
    },
    restore = { saved ->
        DemoProgress(step = DemoStep.entries.getOrNull(saved.getOrElse(0) { 0 }), tries = saved.getOrElse(1) { 0 })
    },
)

/** The page renews the gate this often; the lease is 2 s. */
private const val RENEW_MS = 500L

/** Strips still landscape this long after the lock: the drawn demo takes over. */
private const val LANDSCAPE_HAND_OFF_MS = 2_000L

private const val ARROW_CYCLE_MS = 1_400
private const val STILL_TRAVEL = 0.6f
private const val FADE = 0.7f
private const val TAIL_ALPHA = 0.5f
private const val UPPER_ZONE = 0.3f
private const val LOWER_ZONE = 0.7f
private const val SHORT_SHARE = 0.25f
private const val LONG_SHARE = 0.45f
private const val HINT_LINES = 3
private val ARROW_STROKE = 6.dp
private val ARROW_HEAD = 18.dp
private val TEXT_GAP = 8.dp
private val RESULT_GAP = 16.dp
private val SIDE_PADDING = 32.dp
