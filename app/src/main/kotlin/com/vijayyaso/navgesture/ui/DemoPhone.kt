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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.demo.DemoEngine
import com.vijayyaso.navgesture.demo.DemoResult
import com.vijayyaso.navgesture.demo.DemoStep
import com.vijayyaso.navgesture.gesture.GestureTracker
import com.vijayyaso.navgesture.gesture.SlotId
import com.vijayyaso.navgesture.gesture.SwipeAction
import com.vijayyaso.navgesture.ui.theme.IconTint
import kotlin.math.min

/** The app list while it is open on the drawn phone. [recents] is false for the favourites. */
internal data class DemoListState(val recents: Boolean, val index: Int)

/**
 * What the drawn phone shows at one moment. [effect] plays once each time [effectRun] changes;
 * [ready] names what a release would run now.
 */
internal data class DemoPhoneState(
    val step: DemoStep?,
    val effect: DemoResult?,
    val effectRun: Int,
    val ready: SwipeAction?,
    val list: DemoListState?,
    val fingerDown: Boolean,
)

/** The demo list's four fixed apps, in list order. */
private val DemoApps = listOf(
    GroupIcon(Icons.Filled.Phone, IconTint.GREEN),
    GroupIcon(Icons.Filled.Email, IconTint.BLUE),
    GroupIcon(Icons.Filled.Place, IconTint.RED),
    GroupIcon(Icons.Filled.ShoppingCart, IconTint.ORANGE),
)

private val FavouriteApp = GroupIcon(Icons.Filled.Star, IconTint.ORANGE)

private val PhoneShape = RoundedCornerShape(22.dp)
private val CardCorner = RoundedCornerShape(16.dp)

/** How long an effect takes to play out; the screen keeps it showing for 900 ms in all. */
private const val EFFECT_MS = 450

/** Where the app list row sits, above the bottom start band. */
private val ListRowBottom = 40.dp
private val ListIcon = 30.dp
private val ListGap = 8.dp

/** Which picture an effect draws. */
private enum class Effect { BACK, HOME, RECENTS, APP, FAVOURITES, SHADE, SCREENSHOT, TORCH, NONE }

private fun effectOf(result: DemoResult?): Effect = when (result) {
    null -> Effect.NONE
    is DemoResult.AppPicked -> Effect.APP
    DemoResult.FavouritesOpened -> Effect.FAVOURITES
    // Only the live page gets Circle to Search; the drawn engine never sends it.
    DemoResult.CircleToSearch -> Effect.NONE
    is DemoResult.Ran -> when {
        result.run.slot is SlotId.Corner -> Effect.SHADE
        result.run.action == SwipeAction.Back -> Effect.BACK
        result.run.action == SwipeAction.Home -> Effect.HOME
        result.run.action == SwipeAction.Recents -> Effect.RECENTS
        result.run.action == SwipeAction.Screenshot -> Effect.SCREENSHOT
        result.run.action == SwipeAction.Torch -> Effect.TORCH
        else -> Effect.NONE
    }
}

/** The mock pages' colours, from the theme so both themes stay readable. */
private class PhoneColors(
    val screen: Color,
    val bar: Color,
    val barMark: Color,
    val line: Color,
    val lineSoft: Color,
    val image: Color,
    val recentsBackdrop: Color,
    val shade: Color,
    val card: Color,
    val chip: Color,
    val onChip: Color,
    val dock: Color,
    val bezel: Color,
    val guide: Color,
    val guideFill: Color,
)

@Composable
private fun phoneColors(): PhoneColors {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return PhoneColors(
        screen = scheme.surface,
        bar = scheme.primaryContainer,
        barMark = scheme.onPrimaryContainer.copy(alpha = 0.55f),
        line = scheme.onSurface.copy(alpha = 0.22f),
        lineSoft = scheme.onSurface.copy(alpha = 0.12f),
        image = scheme.secondaryContainer,
        recentsBackdrop = scheme.surfaceDim,
        shade = scheme.surfaceContainerHigh,
        card = scheme.surfaceContainerHighest,
        chip = scheme.inverseSurface,
        onChip = scheme.inverseOnSurface,
        dock = scheme.surfaceContainerHighest.copy(alpha = 0.95f),
        // A dark phone on the light theme, a mid-grey one on the dark theme, so its outline shows.
        bezel = if (dark) Color(0xFF4A505A) else Color(0xFF1F2329),
        guide = scheme.primary,
        guideFill = scheme.primary.copy(alpha = 0.2f),
    )
}

/**
 * The drawn phone: a page of a made-up app, the start band and a still arrow for the step, and
 * the effect of the last practice gesture. Plain shapes and the app's own icons only; nothing
 * loops. [modifier] carries the size, the touch handling and the one content description.
 */
@Composable
internal fun DemoPhone(
    state: DemoPhoneState,
    recentsLinePx: Float,
    longLinePx: Float,
    modifier: Modifier = Modifier,
) {
    val colors = phoneColors()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.effectRun) {
        progress.snapTo(0f)
        if (state.effect != null) progress.animateTo(1f, tween(EFFECT_MS, easing = EaseOutStrong))
    }
    val effect = effectOf(state.effect)
    Box(
        modifier = modifier
            .clip(PhoneShape)
            .background(colors.screen),
    ) {
        EffectLayers(effect, state.effect, colors) { progress.value }
        if (effect == Effect.NONE) {
            Guide(state.step, showArrow = !state.fingerDown, recentsLinePx, longLinePx, colors)
        }
        state.list?.let { ListRow(it, colors) }
        if (state.ready != null && state.list == null) ReadyChip(state.ready, colors)
        Box(
            Modifier
                .fillMaxSize()
                .border(5.dp, colors.bezel, PhoneShape),
        )
    }
}

@Composable
private fun BoxScope.EffectLayers(
    effect: Effect,
    result: DemoResult?,
    colors: PhoneColors,
    progress: () -> Float,
) {
    when (effect) {
        Effect.NONE -> Page(colors) { detailPage(it) }
        // The detail page slides away to the right and the list page under it shows.
        Effect.BACK -> {
            Page(colors) { listPage(it) }
            Page(colors, Modifier.graphicsLayer { translationX = size.width * progress() }) { detailPage(it) }
        }
        // The app shrinks into its icon on the home grid.
        Effect.HOME -> {
            Page(colors) { homeGrid() }
            Page(
                colors,
                Modifier.graphicsLayer {
                    val p = progress()
                    val scale = mix(1f, HOME_ICON_SCALE, p)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - p
                    transformOrigin = TransformOrigin(HOME_ICON_X, HOME_ICON_Y)
                },
            ) { detailPage(it) }
        }
        // The app becomes a card between two others.
        Effect.RECENTS -> {
            Box(
                Modifier
                    .matchParentSize()
                    .background(colors.recentsBackdrop),
            )
            listOf(-1, 1).forEach { side ->
                RecentsCard(colors, Modifier.graphicsLayer {
                    alpha = progress()
                    scaleX = RECENTS_CARD_SCALE
                    scaleY = RECENTS_CARD_SCALE
                    translationX = side * size.width * (RECENTS_CARD_SCALE + RECENTS_CARD_GAP)
                }) { if (side < 0) listPage(it) else homeGrid() }
            }
            RecentsCard(colors, Modifier.graphicsLayer {
                val scale = mix(1f, RECENTS_CARD_SCALE, progress())
                scaleX = scale
                scaleY = scale
            }) { detailPage(it) }
        }
        // The picked app opens from its place in the row.
        Effect.APP -> {
            Page(colors) { detailPage(it) }
            val index = (result as? DemoResult.AppPicked)?.index ?: 0
            OpenedApp(DemoApps[index % DemoApps.size], index, progress)
        }
        // A drag to the left lists the favourites instead; the row shows them for a moment.
        Effect.FAVOURITES -> {
            Page(colors) { detailPage(it) }
            ListRow(DemoListState(recents = false, index = 0), colors)
        }
        // A notification shade drops from the top.
        Effect.SHADE -> {
            Page(colors) { detailPage(it) }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progress() }
                    .background(Color.Black.copy(alpha = SHADE_DIM)),
            )
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { translationY = -size.height * SHADE_SHARE * (1f - progress()) },
            ) { shade(colors) }
        }
        // A white flash, then a small copy of the page in the corner.
        Effect.SCREENSHOT -> {
            Page(colors) { detailPage(it) }
            Page(
                colors,
                Modifier.graphicsLayer {
                    val p = progress()
                    alpha = ((p - THUMB_FROM) / (1f - THUMB_FROM)).coerceIn(0f, 1f)
                    scaleX = THUMB_SCALE
                    scaleY = THUMB_SCALE
                    transformOrigin = TransformOrigin(THUMB_ORIGIN, 1f - THUMB_ORIGIN)
                },
                frame = colors.bezel,
            ) { detailPage(it) }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = (1f - progress() * 2f).coerceIn(0f, 1f) }
                    .background(Color.White),
            )
        }
        // The torch lights and the page takes a warm tint.
        Effect.TORCH -> {
            Page(colors) { detailPage(it) }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progress() }
                    .background(TorchWarm.copy(alpha = TORCH_TINT)),
            )
            TorchLight(progress)
        }
    }
}

/** One full-screen mock page, drawn by [draw]; [frame] draws a thick border, for the screenshot copy. */
@Composable
private fun BoxScope.Page(
    colors: PhoneColors,
    modifier: Modifier = Modifier,
    frame: Color? = null,
    draw: DrawScope.(PhoneColors) -> Unit,
) {
    Canvas(modifier.matchParentSize()) {
        draw(colors)
        if (frame != null) {
            val width = THUMB_FRAME_DP.dp.toPx()
            drawRect(
                frame,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width),
            )
        }
    }
}

@Composable
private fun BoxScope.RecentsCard(colors: PhoneColors, modifier: Modifier, draw: DrawScope.(PhoneColors) -> Unit) {
    Box(
        modifier
            .matchParentSize()
            .clip(CardCorner),
    ) {
        Canvas(Modifier.matchParentSize()) { draw(colors) }
    }
}

@Composable
private fun BoxScope.OpenedApp(app: GroupIcon, index: Int, progress: () -> Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                val p = progress()
                val scale = mix(OPEN_FROM_SCALE, 1f, p)
                scaleX = scale
                scaleY = scale
                alpha = (p * 2f).coerceIn(0f, 1f)
                // Grows out of its icon in the row.
                val rowWidth = (ListIcon.toPx() + ListGap.toPx()) * DemoApps.size
                val iconX = size.width / 2f - rowWidth / 2f + (ListIcon.toPx() + ListGap.toPx()) * (index + 0.5f)
                val iconY = size.height - ListRowBottom.toPx() - ListIcon.toPx()
                transformOrigin = TransformOrigin(iconX / size.width, iconY / size.height)
            }
            .background(app.tint),
    ) {
        Icon(app.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun BoxScope.TorchLight(progress: () -> Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 64.dp)
            .size(120.dp)
            .graphicsLayer { alpha = progress() }
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(TorchWarm.copy(alpha = 0.7f), Color.Transparent)),
                    radius = size.minDimension / 2f,
                )
            },
    ) {
        fixedIcon(SwipeAction.Torch)?.let { IconBadge(it, size = 52.dp) }
    }
}

/** The app list row, as the service draws it above the bottom edge, with the pick raised. */
@Composable
private fun BoxScope.ListRow(list: DemoListState, colors: PhoneColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ListGap),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = ListRowBottom)
            .background(colors.dock, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        DemoApps.forEachIndexed { index, app ->
            val picked = list.recents && index == list.index
            val scale by animateFloatAsState(if (picked) PICKED_SCALE else 1f, tween(PRESS_IN_MS), label = "pick")
            Box(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                IconBadge(if (list.recents) app else FavouriteApp, size = ListIcon)
            }
        }
    }
}

/** Names the action a release would run now, as the bubble's icon does for real. */
@Composable
private fun BoxScope.ReadyChip(action: SwipeAction, colors: PhoneColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 36.dp, start = 12.dp, end = 12.dp)
            .background(colors.chip, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        fixedIcon(action)?.let {
            IconBadge(it, size = 20.dp)
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = stringResource(R.string.demo_ready, shortcutLabel(action)),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onChip,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The step's start band (or corners), its line, and a still arrow along the path. */
@Composable
private fun BoxScope.Guide(
    step: DemoStep?,
    showArrow: Boolean,
    recentsLinePx: Float,
    longLinePx: Float,
    colors: PhoneColors,
) {
    step ?: return
    Canvas(Modifier.matchParentSize()) {
        val band = DemoEngine.BAND_DP.dp.toPx()
        val start = band / 2f
        val w = size.width
        val h = size.height
        val sides = step == DemoStep.BACK || step == DemoStep.WAIT || step == DemoStep.LONG
        when {
            sides -> {
                drawRect(colors.guideFill, size = Size(band, h))
                drawRect(colors.guideFill, topLeft = Offset(w - band, 0f), size = Size(band, h))
            }
            step == DemoStep.CORNER -> {
                // The tracker's corner zone, on the drawn width.
                val zone = min(w * GestureTracker.CORNER_ZONE_OF_WIDTH, GestureTracker.CORNER_ZONE_MAX_DP.dp.toPx())
                drawRect(colors.guideFill, topLeft = Offset(0f, h - band), size = Size(zone, band))
                drawRect(colors.guideFill, topLeft = Offset(w - zone, h - band), size = Size(zone, band))
            }
            else -> drawRect(colors.guideFill, topLeft = Offset(0f, h - band), size = Size(w, band))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        val thin = 1.5.dp.toPx()
        // The real Recents line, measured up from where a bottom swipe starts.
        if (step == DemoStep.RECENTS) {
            val y = h - start - recentsLinePx
            drawLine(colors.guide, Offset(band, y), Offset(w - band, y), thin, pathEffect = dash)
        }
        // The long line, measured in from where a left swipe starts. The right edge mirrors it,
        // but on a narrow drawing the two lines and arrows would cross, so only the left shows.
        if (step == DemoStep.LONG) {
            val x = start + longLinePx
            drawLine(colors.guide, Offset(x, band), Offset(x, h - band), thin, pathEffect = dash)
        }
        if (showArrow) arrow(step, start, recentsLinePx, longLinePx, colors.guide)
    }
}

private fun DrawScope.arrow(step: DemoStep, start: Float, recentsLinePx: Float, longLinePx: Float, color: Color) {
    val w = size.width
    val h = size.height
    val mid = h * ARROW_SIDE_Y
    val dp = 1.dp.toPx()
    when (step) {
        DemoStep.BACK -> {
            pathArrow(color, Offset(start, mid), Offset(start + 64 * dp, mid))
            pathArrow(color, Offset(w - start, mid), Offset(w - start - 64 * dp, mid))
        }
        DemoStep.HOME -> pathArrow(color, Offset(w / 2f, h - start), Offset(w / 2f, h - start - 130 * dp))
        DemoStep.RECENTS -> {
            val end = Offset(w / 2f, h - start - recentsLinePx - 16 * dp)
            pathArrow(color, Offset(w / 2f, h - start), end)
            holdRing(color, Offset(end.x, end.y - RING_GAP_DP * dp))
        }
        DemoStep.APP_LIST -> pathArrow(
            color,
            from = Offset(w / 2f - 40 * dp, h - start),
            to = Offset(w / 2f + 44 * dp, h - start - 44 * dp),
            turn = Offset(w / 2f - 40 * dp, h - start - 44 * dp),
        )
        DemoStep.CORNER -> {
            pathArrow(color, Offset(start, h - start), Offset(start + 56 * dp, h - start - 56 * dp))
            pathArrow(color, Offset(w - start, h - start), Offset(w - start - 56 * dp, h - start - 56 * dp))
        }
        DemoStep.WAIT -> listOf(1f, -1f).forEach { side ->
            val from = if (side > 0) start else w - start
            val end = Offset(from + side * 64 * dp, mid)
            pathArrow(color, Offset(from, mid), end)
            holdRing(color, Offset(end.x + side * RING_GAP_DP * dp, mid))
        }
        DemoStep.LONG -> pathArrow(color, Offset(start, mid), Offset(start + longLinePx + 16 * dp, mid))
    }
}

/** A line from [from] to [to], through [turn] when there is one, with an arrow head at [to]. */
private fun DrawScope.pathArrow(color: Color, from: Offset, to: Offset, turn: Offset? = null) {
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val line = Path().apply {
        moveTo(from.x, from.y)
        turn?.let { lineTo(it.x, it.y) }
        lineTo(to.x, to.y)
    }
    drawPath(line, color, style = stroke)
    val tip = to
    val last = turn ?: from
    val direction = (tip - last) / (tip - last).getDistance()
    val head = 10.dp.toPx()
    val normal = Offset(-direction.y, direction.x)
    val back = tip - direction * head
    val wings = Path().apply {
        moveTo(back.x + normal.x * head * 0.6f, back.y + normal.y * head * 0.6f)
        lineTo(tip.x, tip.y)
        lineTo(back.x - normal.x * head * 0.6f, back.y - normal.y * head * 0.6f)
    }
    drawPath(wings, color, style = stroke)
}

/** A ring just past where the finger stops and holds still. */
private fun DrawScope.holdRing(color: Color, at: Offset) {
    drawCircle(color, radius = 9.dp.toPx(), center = at, style = Stroke(width = 2.5.dp.toPx()))
}

// The made-up app's detail page: a bar with a back mark, a picture and lines of text.
private fun DrawScope.detailPage(colors: PhoneColors) {
    val w = size.width
    val pad = 16.dp.toPx()
    val bar = 64.dp.toPx()
    drawRect(colors.screen)
    drawRect(colors.bar, size = Size(w, bar))
    pill(colors.barMark, Offset(pad, bar - 26.dp.toPx()), Size(14.dp.toPx(), 10.dp.toPx()))
    pill(colors.barMark, Offset(pad + 24.dp.toPx(), bar - 26.dp.toPx()), Size(w * 0.36f, 10.dp.toPx()))
    val imageTop = bar + pad
    val imageHeight = size.height * 0.26f
    drawRoundRect(colors.image, Offset(pad, imageTop), Size(w - pad * 2, imageHeight), CornerRadius(12.dp.toPx()))
    var y = imageTop + imageHeight + pad
    val widths = listOf(0.62f, 0.78f, 0.7f, 0.82f, 0.5f, 0.74f, 0.66f, 0.8f, 0.58f)
    var index = 0
    while (y < size.height - 48.dp.toPx() && index < widths.size) {
        val tall = index == 0
        val height = if (tall) 12.dp.toPx() else 8.dp.toPx()
        drawRoundRect(
            if (tall) colors.line else colors.lineSoft,
            Offset(pad, y),
            Size((w - pad * 2) * widths[index], height),
            CornerRadius(height / 2f),
        )
        y += height + if (tall) 14.dp.toPx() else 10.dp.toPx()
        index++
    }
}

// The page Back returns to: a list of rows, each a coloured dot and two lines.
private fun DrawScope.listPage(colors: PhoneColors) {
    val w = size.width
    val pad = 16.dp.toPx()
    val bar = 64.dp.toPx()
    drawRect(colors.screen)
    drawRect(colors.bar, size = Size(w, bar))
    pill(colors.barMark, Offset(pad, bar - 26.dp.toPx()), Size(w * 0.3f, 10.dp.toPx()))
    val row = 52.dp.toPx()
    val dot = 13.dp.toPx()
    val tints = listOf(IconTint.BLUE, IconTint.GREEN, IconTint.ORANGE, IconTint.PURPLE, IconTint.TEAL, IconTint.RED)
    var y = bar + pad / 2f
    var index = 0
    while (y + row < size.height - 32.dp.toPx()) {
        drawCircle(tints[index % tints.size], radius = dot, center = Offset(pad + dot, y + row / 2f))
        val textX = pad + dot * 2 + 12.dp.toPx()
        val textWidth = w - textX - pad
        pill(colors.line, Offset(textX, y + row / 2f - 10.dp.toPx()), Size(textWidth * 0.7f, 8.dp.toPx()))
        pill(colors.lineSoft, Offset(textX, y + row / 2f + 4.dp.toPx()), Size(textWidth * 0.45f, 6.dp.toPx()))
        y += row
        index++
    }
}

// A home screen: a wallpaper, a grid of app icons and a dock.
private fun DrawScope.homeGrid() {
    drawRect(Brush.verticalGradient(listOf(WallpaperTop, WallpaperBottom)))
    val tints = listOf(
        IconTint.BLUE, IconTint.GREEN, IconTint.ORANGE, IconTint.PURPLE, IconTint.TEAL, IconTint.RED, IconTint.GREY,
    )
    val columns = 4
    val cell = size.width / columns
    val icon = min(cell * 0.62f, 48.dp.toPx())
    val corner = CornerRadius(icon * 0.27f)
    val top = 64.dp.toPx()
    for (rowIndex in 0 until 4) {
        for (column in 0 until columns) {
            val x = cell * column + (cell - icon) / 2f
            val y = top + rowIndex * (icon + 28.dp.toPx())
            drawRoundRect(tints[(rowIndex * columns + column) % tints.size], Offset(x, y), Size(icon, icon), corner)
        }
    }
    val dockTop = size.height - icon - 40.dp.toPx()
    drawRoundRect(
        Color.White.copy(alpha = 0.25f),
        Offset(12.dp.toPx(), dockTop - 10.dp.toPx()),
        Size(size.width - 24.dp.toPx(), icon + 20.dp.toPx()),
        CornerRadius(20.dp.toPx()),
    )
    for (column in 0 until columns) {
        val x = cell * column + (cell - icon) / 2f
        drawRoundRect(tints[(column + 3) % tints.size], Offset(x, dockTop), Size(icon, icon), corner)
    }
}

// The notification shade: a panel over the top part with two notifications.
private fun DrawScope.shade(colors: PhoneColors) {
    val w = size.width
    val panel = size.height * SHADE_SHARE
    val pad = 12.dp.toPx()
    drawRoundRect(colors.shade, Offset(0f, -24.dp.toPx()), Size(w, panel + 24.dp.toPx()), CornerRadius(24.dp.toPx()))
    val card = 64.dp.toPx()
    listOf(IconTint.BLUE, IconTint.GREEN).forEachIndexed { index, tint ->
        val top = 40.dp.toPx() + index * (card + pad)
        if (top + card > panel - pad) return@forEachIndexed
        drawRoundRect(colors.card, Offset(pad, top), Size(w - pad * 2, card), CornerRadius(14.dp.toPx()))
        drawCircle(tint, radius = 12.dp.toPx(), center = Offset(pad * 2 + 12.dp.toPx(), top + card / 2f))
        val textX = pad * 2 + 32.dp.toPx()
        val textWidth = w - textX - pad * 2
        pill(colors.line, Offset(textX, top + 18.dp.toPx()), Size(textWidth * 0.6f, 8.dp.toPx()))
        pill(colors.lineSoft, Offset(textX, top + 34.dp.toPx()), Size(textWidth * 0.85f, 6.dp.toPx()))
    }
    pill(colors.line, Offset(w / 2f - 18.dp.toPx(), panel - 12.dp.toPx()), Size(36.dp.toPx(), 4.dp.toPx()))
}

/** A bar with fully round ends, as the mock text lines are drawn. */
private fun DrawScope.pill(color: Color, topLeft: Offset, size: Size) {
    drawRoundRect(color, topLeft, size, CornerRadius(size.height / 2f))
}

private fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private val WallpaperTop = Color(0xFF3F5FB8)
private val WallpaperBottom = Color(0xFF7E57C2)
private val TorchWarm = Color(0xFFFFB547)

private const val HOME_ICON_SCALE = 0.14f
private const val HOME_ICON_X = 0.375f
private const val HOME_ICON_Y = 0.3f
private const val RECENTS_CARD_SCALE = 0.62f
private const val RECENTS_CARD_GAP = 0.06f
private const val OPEN_FROM_SCALE = 0.1f
private const val PICKED_SCALE = 1.25f
private const val SHADE_SHARE = 0.48f
private const val SHADE_DIM = 0.3f
private const val THUMB_SCALE = 0.3f
private const val THUMB_ORIGIN = 0.08f
private const val THUMB_FROM = 0.3f
private const val THUMB_FRAME_DP = 10f
private const val TORCH_TINT = 0.3f
private const val ARROW_SIDE_Y = 0.42f
private const val RING_GAP_DP = 16f
