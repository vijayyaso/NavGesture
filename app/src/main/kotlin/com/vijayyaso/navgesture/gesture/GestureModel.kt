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

    /** Google's Circle to Search, as a long press on the system gesture handle starts it. */
    data object CircleToSearch : GestureAction {
        override val id: String = "CircleToSearch"
    }

    /** Runs [action] for a swipe that ended in [slot]. */
    data class Run(val slot: SlotId, val action: SwipeAction) : GestureAction {
        // A corner is named by its side, Back, Home and Recents keep their capitals, and any
        // other action reads as its stored id. The log and the e2e suites read this.
        override val id: String = when {
            slot is SlotId.Corner -> "Corner${slot.side.name}"
            action == SwipeAction.Back -> "Back"
            action == SwipeAction.Home -> "Home"
            action == SwipeAction.Recents -> "Recents"
            else -> action.id
        }
    }
}

/** Where a swipe's action is kept: a side or bottom swipe of one kind, or a bottom corner. */
sealed interface SlotId {
    data class Edge(val edge: ScreenEdge, val zone: SwipeZone, val kind: SwipeKind) : SlotId

    data class Corner(val side: BottomCorner) : SlotId
}

/** The part of a side edge a touch starts in. */
enum class SwipeZone { WHOLE, TOP_HALF, BOTTOM_HALF }

/** How a swipe ends. PAUSE is the bottom swipe that stops past the Recents line. */
enum class SwipeKind { SHORT, LONG, WAIT, PAUSE }

/** The glyph the bubble draws for an action. */
enum class ActionIcon { BACK, HOME, RECENTS, GENERIC, NONE }

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

/** What a swipe does. [id] is what settings store. */
sealed interface SwipeAction {
    val id: String

    /** The action waits 80 ms before it runs, so the strips are back on screen first. */
    val settles: Boolean
        get() = when (this) {
            is OpenApp, is AppShortcut, PreviousApp, Screenshot -> true
            else -> false
        }

    /** The action opens the app list panel, which the overlay builds only when it is needed. */
    val needsListPanel: Boolean
        get() = this == RecentsList || this == FavouritesList

    /** A stray swipe in a landscape game is as unwanted as Home, so the bottom edge asks twice. */
    val guardedInLandscape: Boolean
        get() = this != None

    val icon: ActionIcon
        get() = when (this) {
            Back -> ActionIcon.BACK
            Home -> ActionIcon.HOME
            Recents -> ActionIcon.RECENTS
            None -> ActionIcon.NONE
            else -> ActionIcon.GENERIC
        }

    data object None : SwipeAction {
        override val id: String = "none"
    }

    data object RecentsList : SwipeAction {
        override val id: String = "recents_list"
    }

    data object FavouritesList : SwipeAction {
        override val id: String = "favourites_list"
    }

    data object Notifications : SwipeAction {
        override val id: String = "notifications"
    }

    data object QuickSettings : SwipeAction {
        override val id: String = "quick_settings"
    }

    data object Screenshot : SwipeAction {
        override val id: String = "screenshot"
    }

    data object LockScreen : SwipeAction {
        override val id: String = "lock_screen"
    }

    data object PowerMenu : SwipeAction {
        override val id: String = "power_menu"
    }

    data object SplitScreen : SwipeAction {
        override val id: String = "split_screen"
    }

    data object Back : SwipeAction {
        override val id: String = "back"
    }

    data object Home : SwipeAction {
        override val id: String = "home"
    }

    data object Recents : SwipeAction {
        override val id: String = "recents"
    }

    data object PreviousApp : SwipeAction {
        override val id: String = "previous_app"
    }

    data object Torch : SwipeAction {
        override val id: String = "torch"
    }

    data class OpenApp(val packageName: String) : SwipeAction {
        override val id: String = APP_PREFIX + packageName
    }

    /** One of an app's fixed shortcuts (the actions its icon offers on a long press). */
    data class AppShortcut(val packageName: String, val shortcutId: String) : SwipeAction {
        override val id: String = SHORTCUT_PREFIX + packageName + "/" + shortcutId
    }

    companion object {
        private const val APP_PREFIX = "app:"
        private const val SHORTCUT_PREFIX = "shortcut:"

        // Both lists are lazy: the interface has default members, so a first entry such as
        // [None] starts this companion, which would read that entry while it is still null.

        /** Every choice that is not an app, in the order the picker lists them. */
        val fixed: List<SwipeAction> by lazy {
            listOf(
                None,
                RecentsList,
                FavouritesList,
                Back,
                Home,
                Recents,
                PreviousApp,
                Notifications,
                QuickSettings,
                Screenshot,
                LockScreen,
                PowerMenu,
                SplitScreen,
            )
        }

        // Read from storage but not offered by the picker yet.
        private val extra: List<SwipeAction> by lazy { listOf(Torch) }

        /** An unknown or empty id is [None], so a downgrade never leaves a broken corner. */
        fun decode(stored: String?): SwipeAction {
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
            return (fixed + extra).firstOrNull { it.id == stored } ?: None
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
    /** Off: drag right for recent apps, left for favourites. On: the two sides trade places. */
    val swapAppLists: Boolean = false,
    /** Drag to pick, touch to pick, or both. */
    val appListStyle: AppListStyle = AppListStyle.DRAG,
    /** Sideways travel that moves the app list selection by one app. */
    val switcherStepDp: Float = SwitcherSelection.DEFAULT_STEP_DP,
    /** A fast slide moves through more apps than a slow one, like a laptop trackpad. */
    val switcherAdaptiveSpeed: Boolean = true,
    /** What a swipe up and inward from the bottom-left corner does. */
    val leftCornerShortcut: SwipeAction = SwipeAction.None,
    /** What a swipe up and inward from the bottom-right corner does. */
    val rightCornerShortcut: SwipeAction = SwipeAction.None,
    /** What each side and bottom swipe does. The defaults are Back, Home and Recents. */
    val slots: SwipeSlots = SwipeSlots(),
    /** Travel from the DOWN point past which a side swipe is a long swipe. */
    val longSwipeDp: Float = 120f,
    /** How long a side swipe must stay still to count as "swipe and wait". */
    val waitMs: Long = 350L,
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
    fun shortcutFor(corner: BottomCorner): SwipeAction = when (corner) {
        BottomCorner.LEFT -> leftCornerShortcut
        BottomCorner.RIGHT -> rightCornerShortcut
    }

    /** What [slot] holds, a corner included. */
    fun actionFor(slot: SlotId): SwipeAction = when (slot) {
        is SlotId.Corner -> shortcutFor(slot.side)
        is SlotId.Edge -> slots[slot]
    }

    /** This configuration with [action] in [slot], a corner included. */
    fun withAction(slot: SlotId, action: SwipeAction): GestureConfiguration = when (slot) {
        is SlotId.Corner -> when (slot.side) {
            BottomCorner.LEFT -> copy(leftCornerShortcut = action)
            BottomCorner.RIGHT -> copy(rightCornerShortcut = action)
        }
        is SlotId.Edge -> copy(slots = slots.with(slot, action))
    }

    val effectiveCircleToSearchHoldMs: Long
        get() = if (circleToSearchFollowsSystemTiming) {
            platform.longPressTimeoutMs
        } else {
            circleToSearchHoldMs
        }

    /**
     * Where a side swipe becomes a long swipe, in dp from DOWN. It always lies past the trigger,
     * so a short swipe keeps its own band even when the trigger is set as far as [longSwipeDp].
     */
    val longLineDp: Float
        get() = if (longSwipeDp > sideMinimumSwipeDistanceDp) {
            longSwipeDp
        } else {
            sideMinimumSwipeDistanceDp + LONG_LINE_MARGIN_DP
        }

    /**
     * Takes from [source] only the gesture tunings the Gesture settings screen shows, for its
     * reset and undo. The gesture bar is a system setting the user chose, so it stays.
     */
    fun withGestureSettingsOf(source: GestureConfiguration): GestureConfiguration = copy(
        edgeWidthDp = source.edgeWidthDp,
        slots = source.slots,
        longSwipeDp = source.longSwipeDp,
        waitMs = source.waitMs,
        landscapeConfirmEnabled = source.landscapeConfirmEnabled,
        sideMinimumSwipeDistanceDp = source.sideMinimumSwipeDistanceDp,
        bottomMinimumSwipeDistanceDp = source.bottomMinimumSwipeDistanceDp,
        recentsLinePercent = source.recentsLinePercent,
        maximumArmDurationMs = source.maximumArmDurationMs,
        hapticFeedbackEnabled = source.hapticFeedbackEnabled,
    )

    companion object {
        /** How far past the trigger the long line sits when the trigger reaches [longSwipeDp]. */
        const val LONG_LINE_MARGIN_DP = 24f
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

    /** The swipe was made, but its slot holds no action, so the app underneath gets it. */
    SLOT_EMPTY,
}

sealed interface TrackerAction {
    data object None : TrackerAction

    /** [pending] is the action a release would run now. */
    data class Armed(val scheduleHoldMs: Long?, val pending: SwipeAction) : TrackerAction

    data class RearmHold(val delayMs: Long) : TrackerAction

    /**
     * What a release would run changed after arming: a bottom swipe paused past the Recents
     * line, or a side swipe crossed its long line, out (LONG) or back (SHORT).
     */
    data class Pending(val action: SwipeAction, val kind: SwipeKind) : TrackerAction

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
     * the corner's [GestureAction.Run], so a cancel or a second finger can still drop it.
     */
    data class CornerReady(val corner: BottomCorner) : TrackerAction

    data class Fire(val action: GestureAction, val edge: ScreenEdge) : TrackerAction

    /** An armed side swipe pulled back inside its trigger distance, with the finger still down. */
    data object Disarmed : TrackerAction

    /** A side swipe released inside its trigger distance after arming, so nothing fires or replays. */
    data object Reverted : TrackerAction

    data class Unused(val reason: RejectionReason) : TrackerAction
}
