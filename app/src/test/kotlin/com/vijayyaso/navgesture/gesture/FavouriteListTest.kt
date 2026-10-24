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

class FavouriteListTest {
    @Test
    fun `round trip keeps order`() {
        assertEquals("b,a", FavouriteList.encode(listOf("b", "a")))
        assertEquals(listOf("b", "a"), FavouriteList.decode(FavouriteList.encode(listOf("b", "a"))))
    }

    @Test
    fun `empty or missing is no favourites`() {
        assertEquals(emptyList<String>(), FavouriteList.decode(null))
        assertEquals(emptyList<String>(), FavouriteList.decode(""))
        assertEquals("", FavouriteList.encode(emptyList()))
    }

    @Test
    fun `favourites drop packages with no launch intent`() {
        assertEquals(listOf("a"), FavouriteList.usable(listOf("a", "gone")) { it == "a" })
    }

    @Test
    fun `toggle adds then removes`() {
        val added = FavouriteList.toggle(listOf("b"), "a")
        assertEquals(listOf("b", "a"), added)
        assertEquals(listOf("b"), FavouriteList.toggle(added, "a"))
    }

    @Test
    fun `moveUp swaps with the one above and the first stays put`() {
        val packages = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), FavouriteList.moveUp(packages, 2))
        assertEquals(packages, FavouriteList.moveUp(packages, 0))
    }
}
