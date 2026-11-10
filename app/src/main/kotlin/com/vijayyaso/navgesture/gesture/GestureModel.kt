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

/**
 * [id] exists because R8 obfuscates class names in release builds: `::class.simpleName`
 * reads as "a" / "b" / "c" once minified.
 */
sealed interface GestureAction {
    val id: String

    data object Back : GestureAction {
        override val id: String = "Back"
    }

    data object Home : GestureAction {
        override val id: String = "Home"
    }

    data object Recents : GestureAction {
        override val id: String = "Recents"
    }

    /** Google's Circle to Search, as a long press on the system gesture handle starts it. */
    data object CircleToSearch : GestureAction {
        override val id: String = "CircleToSearch"
    }

    /** A swipe up and inward from a bottom corner; the corner's shortcut says what it does. */
    data class Corner(val side: BottomCorner) : GestureAction {
        override val id: String = "Corner${side.name}"
    }
}

enum class ScreenEdge { LEFT, RIGHT, BOTTOM }

enum class BottomCorner { LEFT, RIGHT }

/** How the bottom app list is picked from. */
enum class AppListStyle {
    /** The selection follows the held finger; lifting opens it. */
    DRAG,

    /** The list stays open after the finger lifts, and the user touches an app. */
    TAP,

    /** Drag to pick, or lift without moving a step to keep the list open for touching. */
    BOTH,
    ;

    /**
     * Whether lifting the finger opens the selected app. Both treats a lift with no step slid
     * as "leave the list open for touching", so nothing is selected until a step is slid.
     */
    fun opensOnLift(steps: Int): Boolean = when (this) {
        DRAG -> true
        TAP -> false
        BOTH -> steps != 0
    }

    /** Whether the row highlights an app while the finger is down; what shows is what opens. */
    fun showsSelection(steps: Int): Boolean = this != TAP && opensOnLift(steps)

    /**
     * The step that picks the app. Drag picks the app under step 0. Both has no pick at step 0
     * (a lift there leaves the list open), so the first step right highlights the app already in
     * the middle, and the next step moves on. Sliding left from 0 goes to the last app.
     */
    fun effectiveSteps(steps: Int): Int = when {
        this == BOTH && steps > 0 -> steps - 1
        else -> steps
    }

    companion object {
        fun decode(stored: String?): AppListStyle =
            entries.firstOrNull { it.name == stored } ?: DRAG
    }
}

/** What a corner swipe does. [id] is what settings store. */
sealed interface CornerShortcut {
    val id: String

    data object None : CornerShortcut {
        override val id: String = "none"
    }

    data object RecentsList : CornerShortcut {
        override val id: String = "recents_list"
    }

    data object FavouritesList : CornerShortcut {
        override val id: String = "favourites_list"
    }

    data object Notifications : CornerShortcut {
        override val id: String = "notifications"
    }

    data object QuickSettings : CornerShortcut {
        override val id: String = "quick_settings"
    }

    data object Screenshot : CornerShortcut {
        override val id: String = "screenshot"
    }

    data object LockScreen : CornerShortcut {
        override val id: String = "lock_screen"
    }

    data object PowerMenu : CornerShortcut {
        override val id: String = "power_menu"
    }

    data object SplitScreen : CornerShortcut {
        override val id: String = "split_screen"
    }

    data class OpenApp(val packageName: String) : CornerShortcut {
        override val id: String = APP_PREFIX + packageName
    }

    /** One of an app's fixed shortcuts (the actions its icon offers on a long press). */
    data class AppShortcut(val packageName: String, val shortcutId: String) : CornerShortcut {
        override val id: String = SHORTCUT_PREFIX + packageName + "/" + shortcutId
    }

    companion object {
        private const val APP_PREFIX = "app:"
        private const val SHORTCUT_PREFIX = "shortcut:"

        /** Every choice that is not an app, in the order the picker lists them. */
        val fixed: List<CornerShortcut> = listOf(
            None,
            RecentsList,
            FavouritesList,
            Notifications,
            QuickSettings,
            Screenshot,
            LockScreen,
            PowerMenu,
            SplitScreen,
        )

        /** An unknown or empty id is [None], so a downgrade never leaves a broken corner. */
        fun decode(stored: String?): CornerShortcut {
            if (stored == null) return None
            if (stored.startsWith(APP_PREFIX)) {
                val packageName = stored.removePrefix(APP_PREFIX)
                return if (packageName.isBlank()) None else OpenApp(packageName)
            }
            if (stored.startsWith(SHORTCUT_PREFIX)) {
                // A package name never holds a '/', so the first one ends it; the id may hold more.
                val rest = stored.removePrefix(SHORTCUT_PREFIX)
                val slash = rest.indexOf('/')
                if (slash < 1) return None
                val packageName = rest.substring(0, slash)
                val shortcutId = rest.substring(slash + 1)
                return if (packageName.isBlank() || shortcutId.isBlank()) None else AppShortcut(packageName, shortcutId)
            }
            return fixed.firstOrNull { it.id == stored } ?: None
        }
    }
}

data class TouchSample(val xPx: Float, val yPx: Float, val timestampMs: Long)

data class GestureConfiguration(
    val edgeWidthDp: Float = 20f,
    val sideMinimumSwipeDistanceDp: Float = 24f,
    /**
     * Lower than the sides on purpose: a swipe starting on the navigation bar only reaches
     * our strip once the bar hands it off, so part of the travel is already spent.
     */
    val bottomMinimumSwipeDistanceDp: Float = 10f,
    /**
     * Gates only the phase *before* arming; once armed a gesture may be held indefinitely,
     * or "swipe up and hold" for Recents would time out and be discarded.
     */
    val maximumArmDurationMs: Long = 1000L,
    /**
     * How far up, as a percentage of the screen height, a bottom swipe must travel before a
     * pause can mean Recents. Below it a pause is still Home. The POCO launcher's own line
     * is 250px on a 2400px screen (`GestureHomeCalculator.isSafeArea`).
     */
    val recentsLinePercent: Float = 10.4f,
    /**
     * An upward release faster than this is Home even after a pause armed Recents. POCO's
     * is `density / 2` px/ms (`GestureHomeCalculator.canFastPullUp`).
     */
    val homeFlickDpPerMs: Float = 0.5f,
    /** Multiple of the on-axis travel; 1.0 is a 45-degree cone. */
    val offAxisToleranceRatio: Float = 1.0f,
    /** Swipe up from the bottom and drag sideways to show the app list. */
    val edgeAppListEnabled: Boolean = true,
    /** Drag to pick, touch to pick, or both. */
    val appListStyle: AppListStyle = AppListStyle.DRAG,
    /** Sideways travel that moves the app list selection by one app. */
    val switcherStepDp: Float = SwitcherSelection.DEFAULT_STEP_DP,
    /** A fast slide moves through more apps than a slow one, like a laptop trackpad. */
    val switcherAdaptiveSpeed: Boolean = true,
    /** What a swipe up and inward from the bottom-left corner does. */
    val leftCornerShortcut: CornerShortcut = CornerShortcut.None,
    /** What a swipe up and inward from the bottom-right corner does. */
    val rightCornerShortcut: CornerShortcut = CornerShortcut.None,
    val leftEdgeBackEnabled: Boolean = true,
    val rightEdgeBackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    /** Off by default: it changes a system setting, and needs a one-time adb grant. */
    val hideGestureBar: Boolean = false,
    /** On by default, as HyperOS asks for a second bottom swipe in a landscape app. */
    val landscapeConfirmEnabled: Boolean = true,
    /** Off by default: it takes a still finger on the bottom edge away from the app underneath. */
    val circleToSearchEnabled: Boolean = false,
    /** On by default, as the system's own handle waits for the "touch and hold delay". */
    val circleToSearchFollowsSystemTiming: Boolean = true,
    /** Used only when [circleToSearchFollowsSystemTiming] is off. */
    val circleToSearchHoldMs: Long = PlatformDefaults.LONG_PRESS_TIMEOUT_MS,
    /** Read from the device at runtime and never persisted. */
    val platform: PlatformDefaults = PlatformDefaults(),
) {
    fun shortcutFor(corner: BottomCorner): CornerShortcut = when (corner) {
        BottomCorner.LEFT -> leftCornerShortcut
        BottomCorner.RIGHT -> rightCornerShortcut
    }

    val anyCornerShortcut: Boolean
        get() = leftCornerShortcut != CornerShortcut.None ||
            rightCornerShortcut != CornerShortcut.None

    val effectiveCircleToSearchHoldMs: Long
        get() = if (circleToSearchFollowsSystemTiming) {
            platform.longPressTimeoutMs
        } else {
            circleToSearchHoldMs
        }
}

/** Launcher3's numbers for a long press on the navigation handle, at AOSP's defaults. */
data class PlatformDefaults(
    /** ViewConfiguration.getLongPressTimeout(), 400 ms on AOSP since Android 12. */
    val longPressTimeoutMs: Long = LONG_PRESS_TIMEOUT_MS,
    /** ViewConfiguration.getScaledTouchSlop() in dp. */
    val touchSlopDp: Float = 8f,
    /** SystemUI navigation_home_handle_width, the part of the edge a long press counts on. */
    val handleWidthDp: Float = 108f,
) {
    companion object {
        const val LONG_PRESS_TIMEOUT_MS = 400L

        /** Launcher3 never lets the handle's long-press target shrink below this. */
        const val MIN_TOUCH_TARGET_DP = 48f
    }
}

enum class RejectionReason {
    NO_MOVEMENT,
    INSUFFICIENT_MOVEMENT,
    WRONG_DIRECTION,
    TOO_SLOW_TO_ARM,
    OFF_AXIS,
}

sealed interface TrackerAction {
    data object None : TrackerAction

    data class Armed(val scheduleHoldMs: Long?) : TrackerAction

    data class RearmHold(val delayMs: Long) : TrackerAction

    /**
     * A bottom swipe paused past the Recents line; the release means Recents.
     */
    data object RecentsPrimed : TrackerAction

    /** Starts the long-press timer, whose expiry is reported through `onLongPressElapsed`. */
    data class ScheduleLongPress(val delayMs: Long) : TrackerAction

    /**
     * A sideways drag during the bottom rise hands the rest of the stream to the selector.
     * Dragging right opens recent apps; dragging left opens favourites.
     */
    data class OpenSwitcher(
        val edge: ScreenEdge,
        val origin: TouchSample,
        val recents: Boolean,
    ) : TrackerAction

    /**
     * A corner diagonal passed its trigger. Nothing runs yet: the release fires
     * [GestureAction.Corner], so a cancel or a second finger can still drop it.
     */
    data class CornerReady(val corner: BottomCorner) : TrackerAction

    data class Fire(val action: GestureAction, val edge: ScreenEdge) : TrackerAction

    /** An armed side swipe pulled back inside its trigger distance, with the finger still down. */
    data object Disarmed : TrackerAction

    /** A side swipe released inside its trigger distance after arming, so nothing fires or replays. */
    data object Reverted : TrackerAction

    data class Unused(val reason: RejectionReason) : TrackerAction
}
