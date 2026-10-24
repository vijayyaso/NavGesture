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
import android.view.View
import android.view.WindowManager
import com.vijayyaso.navgesture.gesture.SwitcherSelection
import kotlin.math.ceil

/** Metadata resolved off the touch thread, ready for drawing. */
internal data class PanelApp(val packageName: String, val label: String, val icon: Drawable)

/** A non-touchable app row; the bottom strip keeps ownership of the held touch. */
internal class SwitcherPanelOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val geometry: ScreenGeometry,
) {
    private val view = PanelView(context, geometry.density)
    private var attached = false

    fun install() {
        if (attached) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
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

    fun show(apps: List<PanelApp>, dimmedIndex: Int?, centreYPx: Float) {
        view.apps = apps
        view.dimmedIndex = dimmedIndex
        view.message = null
        view.selected = null
        showAt(centreYPx)
    }

    fun select(index: Int?) {
        view.selected = index
        view.invalidate()
    }

    fun showMessage(text: String, centreYPx: Float) {
        view.apps = emptyList()
        view.selected = null
        view.message = text
        showAt(centreYPx)
    }

    fun hide() = setWindowVisible(false)

    private fun showAt(centreYPx: Float) {
        if (!attached) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.y = (centreYPx - view.panelHeightPx / 2f).toInt()
            .coerceIn(0, (geometry.heightPx - view.panelHeightPx).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
        view.invalidate()
        setWindowVisible(true)
    }

    private fun setWindowVisible(visible: Boolean) {
        if (!attached) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val alpha = if (visible) 1f else 0f
        if (params.alpha == alpha) return
        params.alpha = alpha
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    @SuppressLint("ViewConstructor")
    private class PanelView(context: Context, private val density: Float) : View(context) {
        var apps: List<PanelApp> = emptyList()
        var dimmedIndex: Int? = null
        var selected: Int? = null
        var message: String? = null
        val panelHeightPx = ceil(112f * density).toInt()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6141416.toInt() }
        private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFFFFF }
        private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * context.resources.displayMetrics.scaledDensity
        }
        private val box = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), panelHeightPx)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val available = (width / density - 28f).coerceAtLeast(0f)
            val maxVisible = (available / 56f).toInt().coerceIn(1, 6)
            val window = SwitcherSelection.visibleWindow(selected ?: 0, apps.size, maxVisible)
            val panelWidth = if (message != null) width.toFloat() else
                ((window.count() * 56f + 28f) * density).coerceAtMost(width.toFloat())
            val left = (width - panelWidth) / 2f
            box.set(left, 0f, left + panelWidth, height.toFloat())
            canvas.drawRoundRect(box, 20f * density, 20f * density, fill)
            val line = message
            if (line != null) {
                drawLabel(canvas, line, panelWidth - 24f * density, height / 2f)
                return
            }
            window.forEachIndexed { slot, index ->
                val x = left + (18f + slot * 56f) * density
                val y = 18f * density
                if (index == selected) {
                    box.set(x - 6f * density, y - 6f * density, x + 54f * density, y + 54f * density)
                    canvas.drawRoundRect(box, 14f * density, 14f * density, highlight)
                }
                val icon = apps[index].icon
                icon.setBounds(x.toInt(), y.toInt(), (x + 48f * density).toInt(), (y + 48f * density).toInt())
                icon.alpha = if (index == dimmedIndex) 102 else 255
                icon.draw(canvas)
                icon.alpha = 255
            }
            selected?.let { apps.getOrNull(it) }?.let {
                drawLabel(canvas, it.label, panelWidth - 24f * density, 88f * density)
            }
        }

        private fun drawLabel(canvas: Canvas, label: String, maxWidth: Float, centreY: Float) {
            val fitted = TextUtils.ellipsize(label, text, maxWidth.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
            val baseline = centreY - (text.ascent() + text.descent()) / 2f
            canvas.drawText(fitted, (width - text.measureText(fitted)) / 2f, baseline, text)
        }
    }

    private companion object {
        const val TAG = "NavGesture"
    }
}
