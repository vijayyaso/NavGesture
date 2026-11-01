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

import androidx.compose.runtime.Composable

/** The play build's updater hooks; the play build has no updater. */
internal object Store {

    /** The play build has no updater. */
    @Composable
    fun Bind() {}

    /** The play build has no updater. */
    @Composable
    fun HomeRow(onOpen: () -> Unit) {}

    /** The play build has no updater. */
    @Composable
    fun UpdatesScreen() {}
}
