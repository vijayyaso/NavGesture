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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The edge drop: a round head joined to the edge by a gooey metaball neck ([Metaball],
 * after Ramotion's fluid slider), moved by Fluid Navigation Gestures' springs.
 *
 * Coordinates are a local frame centred on the drop's base: x runs along the edge, y grows
 * *into* the edge, so a point [rise] in from the edge sits at y = -rise. The overlay turns
 * the frame into screen x and y for each edge.
 *
 * Fluid behaviour: the neck thins as the head pulls away and its feet spread into the
 * edge (the metaball); the head is viscous, sliding along the edge ahead of its base so the
 * neck leans while the head stays round ([lean]); and the rise spring is a little
 * under-damped, so the drop wobbles once as it forms and snaps back.
 *
 * Android-free, so it is unit tested.
 */
object FluidBubble {

    /** The tallest the head's top stands from the edge, however far the finger goes. */
    const val MAX_RISE_DP = 56f

    /** The head, and the unseen circle under the edge its neck grows out of. */
    const val HEAD_RADIUS_DP = 16f
    const val BASE_RADIUS_DP = 40f

    /** How far the head travels before the neck's feet have fully settled onto the edge. */
    const val RISE_DISTANCE_DP = 12f

    /** How far the head may lead its base along the edge while sliding. */
    const val MAX_LEAN_DP = HEAD_RADIUS_DP

    /** The metaball spreads, tuned by eye on rendered frames for a fat, gooey neck. */
    private const val TOP_SPREAD = 0.6f
    private const val BOTTOM_START_SPREAD = 0.9f
    private const val BOTTOM_END_SPREAD = 0.7f
    private const val HANDLE_RATE = 2.4f

    /**
     * How far the head rises after [travelDp] of finger travel in from the edge: one for
     * one at first, easing to a stop at [MAX_RISE_DP] so it never runs off with the finger.
     */
    fun riseDp(travelDp: Float): Float =
        MAX_RISE_DP * tanh(travelDp.coerceAtLeast(0f) / MAX_RISE_DP)

    /** One frame of the drop, in the local frame, for a head [risePx] tall leaning [leanPx]. */
    class Shape(
        /** The neck outline as [Metaball.neck] lays it out, or null while there is none. */
        val neck: FloatArray?,
        val headX: Float,
        val headY: Float,
        val headRadius: Float,
    )

    fun shape(risePx: Float, leanPx: Float, density: Float, overhangPx: Float): Shape {
        val headRadius = HEAD_RADIUS_DP * density
        val baseRadius = BASE_RADIUS_DP * density
        val headY = -(risePx - headRadius)
        val neck = Metaball.neck(
            bottomX = 0f, bottomY = baseRadius, bottomRadius = baseRadius,
            topX = 0f, topY = headY, topRadius = headRadius,
            edgeY = 0f,
            riseDistance = RISE_DISTANCE_DP * density,
            topSpread = TOP_SPREAD,
            bottomStartSpread = BOTTOM_START_SPREAD,
            bottomEndSpread = BOTTOM_END_SPREAD,
            handleRate = HANDLE_RATE,
            footDrop = overhangPx,
        )
        // The head moves as a solid ball; the neck under it bends, more the higher it is.
        val headBottom = max(1f, risePx - 2 * headRadius)
        neck?.let {
            for (i in it.indices step 2) {
                val height = -it[i + 1]
                it[i] += leanPx * (height / headBottom).coerceIn(0f, 1f)
            }
        }
        return Shape(neck, leanPx, headY, headRadius)
    }

    /** How far the head leads its base: their gap along the edge, held to [MAX_LEAN_DP]. */
    fun lean(headAlongPx: Float, baseAlongPx: Float, density: Float): Float {
        val max = MAX_LEAN_DP * density
        return (headAlongPx - baseAlongPx).coerceIn(-max, max)
    }

    private fun tanh(x: Float): Float = kotlin.math.tanh(x.toDouble()).toFloat()
}

/**
 * One of FNG's Rebound springs. Its springs are built with bounciness 0 and speed 10, which
 * Rebound's `BouncyConversion` (`c.c.a.c`) and origami mapping turn into a tension near 300
 * and friction near 34.5: a damping ratio of 0.99, so it settles without a bounce. The icon
 * fade uses speed 20 (tension near 512, friction near 45).
 */
class BubbleSpring(
    private val tension: Float = TENSION,
    private val friction: Float = FRICTION,
    // How close counts as arrived, in the spring's own unit: pixels unless told otherwise.
    private val settleWithin: Float = SETTLED_PX,
) {
    var value = 0f
        private set

    var target = 0f

    private var velocity = 0f

    /** Jumps straight to [to] with no motion, as a fresh bubble starts flush with the edge. */
    fun snap(to: Float) {
        value = to
        target = to
        velocity = 0f
    }

    /** Advances [dtMs] whole milliseconds; see [stepNanos]. */
    fun step(dtMs: Long) = stepNanos(dtMs * NANOS_PER_MS)

    /** Advances [dtNanos]; sub-stepped, since a dropped frame must not make the spring blow up. */
    fun stepNanos(dtNanos: Long) {
        var remaining = dtNanos.coerceIn(0L, MAX_STEP_MS * NANOS_PER_MS) / NANOS_PER_SECOND
        while (remaining > 0f) {
            val dt = min(remaining, SUB_STEP_S)
            val acceleration = tension * (target - value) - friction * velocity
            velocity += acceleration * dt
            value += velocity * dt
            remaining -= dt
        }
    }

    /** Within [settleWithin] of the target (half a pixel by default) and barely moving. */
    val settled: Boolean get() = abs(target - value) < settleWithin && abs(velocity) < settleWithin * 10f

    companion object {
        const val TENSION = 300f
        const val FRICTION = 34.5f
        const val ICON_TENSION = 512f
        const val ICON_FRICTION = 45f

        /** Surface tension: FNG's tension at a damping ratio of 0.55, one small wobble. */
        const val RISE_FRICTION = 19f

        /** Viscosity: the base follows on a softer, slower spring than the tip. */
        const val BASE_TENSION = 110f
        const val BASE_FRICTION = 21f

        private const val SUB_STEP_S = 0.004f
        private const val MAX_STEP_MS = 64L
        private const val SETTLED_PX = 0.5f
        private const val NANOS_PER_MS = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000f

        /** The icon fade runs from 0 to 1, so "arrived" is a hundredth, not half a pixel. */
        const val SETTLED_ALPHA = 0.01f

        /** The damping ratio, for the record: friction over twice the root of tension. */
        fun dampingRatio(tension: Float, friction: Float): Float = friction / (2f * sqrt(tension))
    }
}

/**
 * The four springs of one bubble. With the phone's "Remove animations" setting on, nothing
 * moves by itself: the drop sits exactly under the finger and is gone the moment it is let go.
 */
class BubbleMotion {
    // The tip follows the finger; the base drags behind it, so a sliding drop leans.
    val along = BubbleSpring()
    val baseAlong = BubbleSpring(BubbleSpring.BASE_TENSION, BubbleSpring.BASE_FRICTION)
    val rise = BubbleSpring(BubbleSpring.TENSION, BubbleSpring.RISE_FRICTION)
    val iconAlpha = BubbleSpring(BubbleSpring.ICON_TENSION, BubbleSpring.ICON_FRICTION, BubbleSpring.SETTLED_ALPHA)

    private var animated = true

    val settled: Boolean get() = along.settled && baseAlong.settled && rise.settled && iconAlpha.settled

    /** A fresh drop flush with the edge at [at]. [animate] is the phone's setting, read per touch. */
    fun start(at: Float, animate: Boolean) {
        animated = animate
        along.snap(at)
        baseAlong.snap(at)
        rise.snap(0f)
        iconAlpha.snap(if (animate) 0f else 1f)
        iconAlpha.target = 1f
    }

    fun moveTo(at: Float, risePx: Float) {
        if (animated) {
            along.target = at
            baseAlong.target = at
            rise.target = risePx
        } else {
            along.snap(at)
            baseAlong.snap(at)
            rise.snap(risePx)
        }
    }

    /** Sends the drop back into its edge. False when it went at once, with nothing to wait for. */
    fun release(): Boolean {
        if (!animated) {
            reset()
            return false
        }
        rise.target = 0f
        iconAlpha.target = 0f
        return true
    }

    /** Gone at once, with no motion. */
    fun reset() {
        rise.snap(0f)
        iconAlpha.snap(0f)
    }

    fun stepNanos(dtNanos: Long) {
        along.stepNanos(dtNanos)
        baseAlong.stepNanos(dtNanos)
        rise.stepNanos(dtNanos)
        iconAlpha.stepNanos(dtNanos)
    }
}
