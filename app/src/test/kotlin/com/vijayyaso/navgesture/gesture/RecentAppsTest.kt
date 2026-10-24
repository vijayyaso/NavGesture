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

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {
    @Test
    fun `newest first, each package once`() {
        assertEquals(
            listOf("c", "a", "b"),
            RecentApps.order(listOf("a", "b", "a", "c"), "nav", emptySet()) { true },
        )
    }

    @Test
    fun `drops itself, home apps and unlaunchable packages`() {
        assertEquals(
            listOf("x"),
            RecentApps.order(
                listOf("nav", "bitpit.launcher", "x", "y"),
                "nav",
                setOf("bitpit.launcher"),
            ) { it != "y" },
        )
    }
}
