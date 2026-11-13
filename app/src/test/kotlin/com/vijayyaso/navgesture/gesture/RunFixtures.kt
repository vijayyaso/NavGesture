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

// What the fixed swipes fire and report today, as the tracker tests expect them.

internal val HOME_RUN = GestureAction.Run(
    SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.SHORT),
    SwipeAction.Home,
)

internal val RECENTS_RUN = GestureAction.Run(
    SlotId.Edge(ScreenEdge.BOTTOM, SwipeZone.WHOLE, SwipeKind.PAUSE),
    SwipeAction.Recents,
)

internal fun backRun(edge: ScreenEdge) = GestureAction.Run(
    SlotId.Edge(edge, SwipeZone.WHOLE, SwipeKind.SHORT),
    SwipeAction.Back,
)

internal fun cornerRun(side: BottomCorner, action: SwipeAction) =
    GestureAction.Run(SlotId.Corner(side), action)

internal val RECENTS_PENDING = TrackerAction.Pending(SwipeAction.Recents, SwipeKind.PAUSE)
