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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.ScreenEdge
import kotlin.math.roundToInt

/**
 * Full-screen adjuster for the edge touch area. The three strips are drawn on the real screen
 * at their real dp size and position, over a dark scrim, so what you see and drag is the area
 * the accessibility service actually listens on. The strips are 80% of the screen height on
 * the sides and 90% of the width at the bottom, centered, matching the service.
 *
 * A strip is only a few dp thick, so its grab area reaches [HIT_EXPAND] further into the
 * screen. That also keeps the drag clear of the system back and home gestures near the edge,
 * which is why the - and + buttons exist as well.
 */
@Composable
internal fun TouchAreaAdjuster(
    configuration: GestureConfiguration,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(configuration.edgeWidthDp) }
    LaunchedEffect(configuration.edgeWidthDp) {
        if (!dragging) local = configuration.edgeWidthDp
    }

    fun commit(value: Float) {
        local = value.coerceIn(range)
        dragging = false
        onCommit(local.roundToInt().toFloat())
    }

    Dialog(
        // The number on screen is the value. Leaving always saves it, so the row behind and
        // this overlay can never show two different numbers.
        onDismissRequest = {
            commit(local)
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM)
                .testTag(TestTags.EDGE_AREA_OVERLAY),
        ) {
            val band = local.dp
            val sideLength = maxHeight * SIDE_LENGTH_FRACTION
            val bottomLength = maxWidth * BOTTOM_LENGTH_FRACTION
            val leftLabel = sideLabel(configuration.leftEdgeBackEnabled)
            val rightLabel = sideLabel(configuration.rightEdgeBackEnabled)
            val description = stringResource(R.string.edge_area_preview_desc, local.roundToInt())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(local, range)
                        setProgress { target ->
                            commit(target.coerceIn(range))
                            true
                        }
                    },
            ) {
                if (leftLabel != null) {
                    EdgeBand(
                        edge = ScreenEdge.LEFT,
                        thickness = band,
                        length = sideLength,
                        dragging = dragging,
                        onDragStart = { dragging = true },
                        onDragEnd = { commit(local) },
                        onDelta = { local = (local + it).coerceIn(range) },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    OverlayLabel(
                        text = stringResource(leftLabel),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = band + HIT_EXPAND + 10.dp),
                    )
                }
                if (rightLabel != null) {
                    EdgeBand(
                        edge = ScreenEdge.RIGHT,
                        thickness = band,
                        length = sideLength,
                        dragging = dragging,
                        onDragStart = { dragging = true },
                        onDragEnd = { commit(local) },
                        onDelta = { local = (local + it).coerceIn(range) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                    OverlayLabel(
                        text = stringResource(rightLabel),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = band + HIT_EXPAND + 10.dp),
                    )
                }
                EdgeBand(
                    edge = ScreenEdge.BOTTOM,
                    thickness = band,
                    length = bottomLength,
                    dragging = dragging,
                    onDragStart = { dragging = true },
                    onDragEnd = { commit(local) },
                    onDelta = { local = (local + it).coerceIn(range) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                OverlayLabel(
                    text = stringResource(
                        if (configuration.edgeAppListEnabled) {
                            R.string.gesture_home_app_list_name
                        } else {
                            R.string.gesture_home_recents_name
                        },
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = band + 16.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        TextButton(onClick = { commit(local - STEP) }) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.value_dp, local.roundToInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(min = 64.dp)
                                .testTag(TestTags.EDGE_AREA_VALUE),
                        )
                        TextButton(onClick = { commit(local + STEP) }) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        TextButton(
                            onClick = {
                                commit(local)
                                onDismiss()
                            },
                        ) {
                            Text(stringResource(R.string.action_done))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.edge_area_overlay_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
    }
}

/** Null when no swipe listens on that side. */
private fun sideLabel(backEnabled: Boolean): Int? =
    if (backEnabled) R.string.gesture_back_name else null

@Composable
private fun OverlayLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.9f),
        modifier = modifier,
    )
}

/** One real-size strip. Its touch area reaches [HIT_EXPAND] inward, past the strip itself. */
@Composable
private fun EdgeBand(
    edge: ScreenEdge,
    thickness: Dp,
    length: Dp,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontal = edge != ScreenEdge.BOTTOM
    val expand = if (horizontal) HIT_EXPAND else BOTTOM_GRAB
    val shape = RoundedCornerShape(
        topStart = if (edge == ScreenEdge.LEFT) 12.dp else 4.dp,
        bottomStart = if (edge == ScreenEdge.LEFT) 12.dp else 4.dp,
        topEnd = if (edge == ScreenEdge.RIGHT) 12.dp else 4.dp,
        bottomEnd = if (edge == ScreenEdge.RIGHT) 12.dp else 4.dp,
    )
    val fill = BAND_FILL.copy(alpha = if (dragging) 0.38f else 0.22f)
    val stroke = BAND_STROKE.copy(alpha = if (dragging) 1f else 0.7f)

    Box(
        modifier = modifier
            .size(
                width = if (horizontal) thickness + expand else length,
                height = if (horizontal) length else thickness + expand,
            )
            .pointerInput(edge) {
                if (horizontal) {
                    detectHorizontalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        val dp = dragAmount / density
                        onDelta(if (edge == ScreenEdge.LEFT) dp else -dp)
                    }
                } else {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        onDelta(-dragAmount / density)
                    }
                }
            },
        contentAlignment = when (edge) {
            ScreenEdge.LEFT -> Alignment.CenterStart
            ScreenEdge.RIGHT -> Alignment.CenterEnd
            ScreenEdge.BOTTOM -> Alignment.BottomCenter
        },
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (horizontal) thickness else length,
                    height = if (horizontal) length else thickness,
                )
                .clip(shape)
                .background(fill, shape)
                .border(1.5.dp, stroke, shape),
            contentAlignment = when (edge) {
                ScreenEdge.LEFT -> Alignment.CenterEnd
                ScreenEdge.RIGHT -> Alignment.CenterStart
                ScreenEdge.BOTTOM -> Alignment.TopCenter
            },
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        end = if (edge == ScreenEdge.LEFT) 4.dp else 0.dp,
                        start = if (edge == ScreenEdge.RIGHT) 4.dp else 0.dp,
                        top = if (edge == ScreenEdge.BOTTOM) 4.dp else 0.dp,
                    )
                    .size(
                        width = if (horizontal) 3.dp else 36.dp,
                        height = if (horizontal) 36.dp else 3.dp,
                    )
                    .clip(RoundedCornerShape(50))
                    .background(BAND_STROKE),
            )
        }
    }
}

private val SCRIM = Color(0xE6000000)
private val BAND_FILL = Color(0xFF8FB4F0)
private val BAND_STROKE = Color(0xFFCFE0FF)

/** Grab room inside a side strip, clear of the system back gesture on the edge. */
private val HIT_EXPAND = 32.dp

/** The system Home swipe owns the last ~30 dp, so the bottom strip must be grabbed higher up. */
private val BOTTOM_GRAB = 96.dp

/** One press of - or +. */
private const val STEP = 4f

/** Side strips span 80% of the screen height, centered, as the service installs them. */
private const val SIDE_LENGTH_FRACTION = 0.8f

/** The bottom strip spans 90% of the screen width, centered. */
private const val BOTTOM_LENGTH_FRACTION = 0.9f
