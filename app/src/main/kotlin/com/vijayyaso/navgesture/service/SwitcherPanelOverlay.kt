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

package com.vijayyaso.navgesture.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.vijayyaso.navgesture.gesture.SwitcherSelection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Metadata resolved off the touch thread, ready for drawing. */
internal data class PanelApp(val packageName: String, val label: String, val icon: Drawable)

/**
 * The app list window. While a finger holds the bottom strip it is a non-touchable row, and
 * the strip keeps ownership of the held touch. In touch mode (after the finger lifted, in
 * the Tap or Both style, or from a corner shortcut) it becomes a touchable grid.
 *
 * Placed by its bottom edge, so a taller grid grows upward and never covers the gesture bar.
 */
internal class SwitcherPanelOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val geometry: ScreenGeometry,
    /** The bottom strip's height; the panel sits above it, or the strip would take its taps. */
    private val bottomStripPx: Int,
) {
    private val view = PanelView(context, geometry.density)
    private var attached = false

    /** A touch on an app cell in touch mode, with the app's index. */
    var onPick: ((Int) -> Unit)? = null

    /** A touch anywhere but an app cell in touch mode. */
    var onDismiss: (() -> Unit)? = null

    /** Any finger down on the touch-mode panel, so a slow user is not timed out mid-touch. */
    var onTouchStart: (() -> Unit)? = null

    init {
        view.onPick = { index -> onPick?.invoke(index) }
        view.onDismiss = { onDismiss?.invoke() }
        view.onTouchStart = { onTouchStart?.invoke() }
    }

    fun install() {
        if (attached) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            NOT_TOUCHABLE_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = maxOf(bottomStripPx + (STRIP_GAP_DP * geometry.density).toInt(), (BOTTOM_GAP_DP * geometry.density).toInt())
            fitInsetsTypes = 0
            alpha = 0f
        }
        runCatching {
            windowManager.addView(view, params)
            attached = true
        }.onFailure { Log.e(TAG, "Could not add app switcher panel", it) }
    }

    fun remove() {
        hide()
        if (attached) runCatching { windowManager.removeView(view) }
        attached = false
    }

    /**
     * The drag row: [startIndex] is where step zero lands, [dimmedIndex] the current app, and
     * [steps] the stream's step count now, so a row that arrives mid-drag does not slide in.
     */
    fun showRow(apps: List<PanelApp>, dimmedIndex: Int?, startIndex: Int, steps: Int) {
        view.showRow(apps, dimmedIndex, startIndex, steps)
        show(touchable = false)
    }

    /** Moves the row's selection to an unbounded step count and the app index it maps to. */
    fun select(steps: Int, index: Int?) = view.select(steps, index)

    /** The touch grid. Touchable only once the finger that opened it has lifted. */
    fun showGrid(apps: List<PanelApp>, dimmedIndex: Int?, touchable: Boolean) {
        view.showGrid(apps, dimmedIndex)
        show(touchable)
    }

    fun showMessage(text: String, touchable: Boolean) {
        view.showMessage(text)
        show(touchable)
    }

    fun hide() {
        view.stopAnimation()
        setWindow(visible = false, touchable = false)
    }

    private fun show(touchable: Boolean) {
        view.requestLayout()
        view.invalidate()
        setWindow(visible = true, touchable = touchable)
    }

    private fun setWindow(visible: Boolean, touchable: Boolean) {
        if (!attached) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val alpha = if (visible) 1f else 0f
        // A hidden panel must never catch a touch, whatever was asked.
        val flags = if (visible && touchable) TOUCHABLE_FLAGS else NOT_TOUCHABLE_FLAGS
        if (params.alpha == alpha && params.flags == flags) return
        params.alpha = alpha
        params.flags = flags
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    @SuppressLint("ViewConstructor")
    private class PanelView(context: Context, private val density: Float) : View(context) {
        private enum class Mode { ROW, GRID, MESSAGE }

        var onPick: ((Int) -> Unit)? = null
        var onDismiss: (() -> Unit)? = null
        var onTouchStart: (() -> Unit)? = null

        private var mode = Mode.MESSAGE
        private var apps: List<PanelApp> = emptyList()
        private var dimmedIndex: Int? = null
        private var startIndex = 0
        private var selected: Int? = null
        private var message: String? = null

        // Row scrolling: the animated position chases the unbounded step count.
        private var targetSteps = 0
        private var position = 0f
        private var animator: ValueAnimator? = null

        // The cell a touch went down in, so only a down and up in one cell opens an app.
        private var downCell: Int? = null

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6141416.toInt() }
        private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFFFFF }
        private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * context.resources.displayMetrics.scaledDensity
        }
        private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * context.resources.displayMetrics.scaledDensity
        }
        private val box = RectF()

        fun showRow(apps: List<PanelApp>, dimmedIndex: Int?, startIndex: Int, steps: Int) {
            mode = Mode.ROW
            this.apps = apps
            this.dimmedIndex = dimmedIndex
            this.startIndex = startIndex
            message = null
            // A fresh row starts where the step count is now, without sliding in.
            stopAnimation()
            targetSteps = steps
            position = steps.toFloat()
        }

        fun showGrid(apps: List<PanelApp>, dimmedIndex: Int?) {
            mode = Mode.GRID
            this.apps = apps
            this.dimmedIndex = dimmedIndex
            message = null
            selected = null
            downCell = null
            stopAnimation()
        }

        fun showMessage(text: String) {
            mode = Mode.MESSAGE
            apps = emptyList()
            message = text
            selected = null
            stopAnimation()
        }

        fun select(steps: Int, index: Int?) {
            selected = index
            if (steps != targetSteps) {
                targetSteps = steps
                animateTo(steps.toFloat())
            }
            invalidate()
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }

        private fun animateTo(target: Float) {
            stopAnimation()
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = SCROLL_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private val availableDp: Float get() = (width / density - SIDE_PADDING_DP * 2).coerceAtLeast(0f)

        private val grid: SwitcherSelection.Grid
            get() = SwitcherSelection.tapGrid(apps.size, availableDp, CELL_W_DP)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val heightDp = when (mode) {
                Mode.ROW -> ROW_HEIGHT_DP
                Mode.MESSAGE -> MESSAGE_HEIGHT_DP
                Mode.GRID -> {
                    val available = (width / density - SIDE_PADDING_DP * 2).coerceAtLeast(0f)
                    val rows = SwitcherSelection.tapGrid(apps.size, available, CELL_W_DP).rows
                    rows.coerceAtLeast(1) * CELL_H_DP + GRID_PADDING_DP * 2
                }
            }
            setMeasuredDimension(width, ceil(heightDp * density).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            when (mode) {
                Mode.MESSAGE -> drawMessage(canvas)
                Mode.ROW -> drawRow(canvas)
                Mode.GRID -> drawGrid(canvas)
            }
        }

        private fun drawPanel(canvas: Canvas, panelWidth: Float): Float {
            val left = (width - panelWidth) / 2f
            box.set(left, 0f, left + panelWidth, height.toFloat())
            canvas.drawRoundRect(box, 20f * density, 20f * density, fill)
            return left
        }

        private fun drawMessage(canvas: Canvas) {
            val panelWidth = width - SIDE_PADDING_DP * density
            drawPanel(canvas, panelWidth)
            drawLabel(canvas, text, message.orEmpty(), panelWidth - 24f * density, width / 2f, height / 2f)
        }

        private fun drawRow(canvas: Canvas) {
            val maxVisible = (availableDp / SLOT_DP).toInt().coerceIn(1, MAX_ROW_SLOTS)
            val span = apps.size - startIndex
            if (apps.size <= maxVisible || span < 2) drawFixedRow(canvas) else drawRing(canvas, maxVisible, span)
            selected?.let { apps.getOrNull(it) }?.let {
                drawLabel(canvas, text, it.label, width - 64f * density, width / 2f, 88f * density)
            }
        }

        /** Every app fits: the row stands still and the highlight slides. */
        private fun drawFixedRow(canvas: Canvas) {
            val panelWidth = (apps.size * SLOT_DP + 28f) * density
            val left = drawPanel(canvas, panelWidth)
            val selectedIndex = selected
            if (selectedIndex != null) {
                // The highlight slides between the slot it came from and the selected one.
                val from = SwitcherSelection.indexFor(floor(position).toInt(), apps.size, startIndex)
                val to = SwitcherSelection.indexFor(floor(position).toInt() + 1, apps.size, startIndex)
                val fraction = position - floor(position)
                val slot = if (from != null && to != null && abs(to - from) == 1) {
                    from + (to - from) * fraction
                } else {
                    selectedIndex.toFloat()
                }
                drawHighlight(canvas, left + (18f + slot * SLOT_DP) * density)
            }
            apps.forEachIndexed { index, app ->
                drawIcon(canvas, app, index, left + (18f + index * SLOT_DP) * density, 18f * density, 255)
            }
        }

        /**
         * More apps than fit: a ring of the selectable apps with the selection fixed in the
         * centre. The ring position is the unbounded step count, so looping never jumps back.
         */
        private fun drawRing(canvas: Canvas, maxVisible: Int, span: Int) {
            // An odd slot count keeps a true centre, and fewer slots than apps keeps any app
            // from showing twice while the ring is between two positions.
            var slots = minOf(maxVisible, span - 1).coerceAtLeast(1)
            if (slots % 2 == 0) slots -= 1
            val half = slots / 2
            val panelWidth = (slots * SLOT_DP + 28f) * density
            val left = drawPanel(canvas, panelWidth)
            val centreX = left + (18f + half * SLOT_DP) * density
            drawHighlight(canvas, centreX)
            canvas.save()
            canvas.clipRect(box)
            val base = floor(position).toInt()
            for (virtual in base - half - 1..base + half + 1) {
                val offset = virtual - position
                if (abs(offset) > half + 1) continue
                val index = startIndex + Math.floorMod(virtual, span)
                // Icons fade as they near the panel's ends.
                val fade = (1f - (abs(offset) - half).coerceIn(0f, 1f))
                drawIcon(canvas, apps[index], index, centreX + offset * SLOT_DP * density, 18f * density, (255 * fade).toInt())
            }
            canvas.restore()
        }

        private fun drawGrid(canvas: Canvas) {
            val shape = grid
            val panelWidth = (shape.columns * CELL_W_DP + GRID_PADDING_DP * 2) * density
            val left = drawPanel(canvas, panelWidth)
            for (index in 0 until minOf(apps.size, shape.capacity)) {
                val cell = cellRect(index, shape, left)
                val iconLeft = cell.centerX() - ICON_DP / 2f * density
                drawIcon(canvas, apps[index], index, iconLeft, cell.top + 8f * density, 255)
                val labelColour = small.color
                if (index == dimmedIndex) small.alpha = DIMMED_ALPHA
                drawLabel(canvas, small, apps[index].label, (CELL_W_DP - 6f) * density, cell.centerX(), cell.top + 70f * density)
                small.color = labelColour
            }
        }

        private fun cellRect(index: Int, shape: SwitcherSelection.Grid, left: Float): RectF {
            val column = index % shape.columns
            val row = index / shape.columns
            val x = left + (GRID_PADDING_DP + column * CELL_W_DP) * density
            val y = (GRID_PADDING_DP + row * CELL_H_DP) * density
            return RectF(x, y, x + CELL_W_DP * density, y + CELL_H_DP * density)
        }

        private fun cellAt(x: Float, y: Float): Int? {
            if (mode != Mode.GRID) return null
            val shape = grid
            if (shape.capacity == 0) return null
            val left = (width - (shape.columns * CELL_W_DP + GRID_PADDING_DP * 2) * density) / 2f
            for (index in 0 until minOf(apps.size, shape.capacity)) {
                if (cellRect(index, shape, left).contains(x, y)) return index
            }
            return null
        }

        // Touch mode only: the window is non-touchable while a finger holds the strip.
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> onDismiss?.invoke()
                MotionEvent.ACTION_DOWN -> {
                    downCell = cellAt(event.x, event.y)
                    onTouchStart?.invoke()
                }
                MotionEvent.ACTION_UP -> {
                    val cell = cellAt(event.x, event.y)
                    val started = downCell
                    downCell = null
                    when {
                        cell != null && cell == started && cell != dimmedIndex -> onPick?.invoke(cell)
                        cell == null && started == null -> onDismiss?.invoke()
                        // Down and up in different cells, or on the dimmed current app: ignore.
                        else -> Unit
                    }
                }
                MotionEvent.ACTION_CANCEL -> downCell = null
            }
            return true
        }

        private fun drawHighlight(canvas: Canvas, iconLeft: Float) {
            val top = 18f * density
            val rect = RectF(iconLeft - 6f * density, top - 6f * density, iconLeft + 54f * density, top + 54f * density)
            canvas.drawRoundRect(rect, 14f * density, 14f * density, highlight)
        }

        private fun drawIcon(canvas: Canvas, app: PanelApp, index: Int, left: Float, top: Float, alpha: Int) {
            if (alpha <= 0) return
            val icon = app.icon
            icon.setBounds(left.toInt(), top.toInt(), (left + ICON_DP * density).toInt(), (top + ICON_DP * density).toInt())
            val dim = if (index == dimmedIndex) DIMMED_ALPHA else 255
            icon.alpha = alpha * dim / 255
            icon.draw(canvas)
            icon.alpha = 255
        }

        private fun drawLabel(canvas: Canvas, paint: TextPaint, label: String, maxWidth: Float, centreX: Float, centreY: Float) {
            val fitted = TextUtils.ellipsize(label, paint, maxWidth.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
            val baseline = centreY - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(fitted, centreX - paint.measureText(fitted) / 2f, baseline, paint)
        }
    }

    private companion object {
        const val TAG = "NavGesture"

        /** Least gap between the panel's bottom edge and the screen's, clear of the gesture bar. */
        const val BOTTOM_GAP_DP = 40f

        /** Gap kept above the bottom strip. */
        const val STRIP_GAP_DP = 8f

        const val ROW_HEIGHT_DP = 112f
        const val MESSAGE_HEIGHT_DP = 72f
        const val SIDE_PADDING_DP = 14f

        /** Row slot: a 48 dp icon and an 8 dp gap. */
        const val SLOT_DP = 56f
        const val MAX_ROW_SLOTS = 6
        const val ICON_DP = 48f

        /** Grid cell: room for the icon and one line of its name. */
        const val CELL_W_DP = 72f
        const val CELL_H_DP = 84f
        const val GRID_PADDING_DP = 12f

        const val DIMMED_ALPHA = 102
        const val SCROLL_MS = 160L

        const val NOT_TOUCHABLE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        /** Touches outside reach the app below and still tell us, so the panel can close. */
        const val TOUCHABLE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
