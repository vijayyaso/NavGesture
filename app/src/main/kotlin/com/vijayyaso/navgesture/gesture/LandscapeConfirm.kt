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

package com.vijayyaso.navgesture.gesture

/** The pill HyperOS shows at the bottom of a landscape app when a first swipe is held back. */
object ConfirmPill {
    /** How long the pill waits for the swipe that confirms it. */
    const val WINDOW_MS = 2_000L

    /** As wide as the system gesture handle. */
    const val WIDTH_DP = 108f
    const val HEIGHT_DP = 4f
    const val ABOVE_EDGE_DP = 6f
}

sealed interface ConfirmVerdict {
    data object Perform : ConfirmVerdict

    /** Held back, with the pill up until [expiresAtMs] unless a bottom touch starts first. */
    data class ShowPill(val expiresAtMs: Long) : ConfirmVerdict
}

// Whether a bottom gesture goes ahead or waits for a second swipe. Kept out of the tracker, since
// the pill spans two touches and is kept when the strips are rebuilt.
class LandscapeConfirm(private val windowMs: Long = ConfirmPill.WINDOW_MS) {

    private var required = false

    private var landscape: Boolean? = null

    private var shownAtMs: Long? = null

    // The bottom touch in progress began while the pill was up, so it is the confirmation.
    private var confirming = false

    val pillShown: Boolean get() = shownAtMs != null

    val expiresAtMs: Long? get() = shownAtMs?.plus(windowMs)

    /**
     * When the expiry timer should next run, or null for no timer. A confirming touch holds the
     * pill until it ends, so a timer then could only spin; its end runs [expire] instead.
     */
    val wakeAtMs: Long? get() = if (confirming) null else expiresAtMs

    /** A rotation drops a pill, since it asked about the orientation that is gone. */
    fun configure(enabled: Boolean, landscape: Boolean) {
        if (this.landscape != landscape) dismiss()
        this.landscape = landscape
        required = enabled && landscape
        if (!required) dismiss()
    }

    /** Any new touch ends the one before, so a flag left by a lost end never carries over. */
    fun onDown(edge: ScreenEdge, nowMs: Long) {
        confirming = false
        if (edge != ScreenEdge.BOTTOM) return
        expire(nowMs)
        confirming = pillShown
    }

    /** Any other gesture drops the pill, so it never confirms a later bottom swipe. */
    fun onFire(action: GestureAction, edge: ScreenEdge, nowMs: Long): ConfirmVerdict =
        decide(guarded = edge == ScreenEdge.BOTTOM && isGuarded(action), nowMs)

    /** The sideways drag that opens the app list: a game thumb drifts too, and lifting switches app. */
    fun onOpenList(edge: ScreenEdge, nowMs: Long): ConfirmVerdict =
        decide(guarded = edge == ScreenEdge.BOTTOM, nowMs)

    private fun decide(guarded: Boolean, nowMs: Long): ConfirmVerdict {
        if (!guarded || !required || confirming) {
            dismiss()
            return ConfirmVerdict.Perform
        }
        shownAtMs = nowMs
        return ConfirmVerdict.ShowPill(nowMs + windowMs)
    }

    /** A bottom touch handed to the app is the user going back to it, so the pill goes. */
    fun onReplayed(edge: ScreenEdge) {
        if (edge == ScreenEdge.BOTTOM) dismiss()
    }

    fun onStreamEnd(edge: ScreenEdge, nowMs: Long) {
        if (edge != ScreenEdge.BOTTOM) return
        confirming = false
        expire(nowMs)
    }

    /** The touch in progress was dropped without its end, so the pill goes back to its timer. */
    fun onTouchLost() {
        confirming = false
    }

    /** A touch that started inside the window keeps the pill until it ends. */
    fun expire(nowMs: Long) {
        val expiry = expiresAtMs ?: return
        if (!confirming && nowMs >= expiry) dismiss()
    }

    fun dismiss() {
        shownAtMs = null
        confirming = false
    }

    private companion object {
        /** Corner shortcuts too: a stray corner swipe in a game is as unwanted as Home. */
        fun isGuarded(action: GestureAction): Boolean =
            action in GUARDED || action is GestureAction.Corner

        // Circle to Search is a still hold rather than a swipe, so it never leaves by accident.
        val GUARDED = setOf(
            GestureAction.Home,
            GestureAction.Recents,
        )
    }
}
