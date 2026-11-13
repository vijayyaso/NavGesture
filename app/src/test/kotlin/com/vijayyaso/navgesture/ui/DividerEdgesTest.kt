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

package com.vijayyaso.navgesture.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DividerEdgesTest {

    @Test
    fun `a line sits between each pair of rows`() {
        assertArrayEquals(intArrayOf(10, 30), dividerEdges(listOf(10, 20, 5)))
    }

    @Test
    fun `one row draws no line`() {
        assertArrayEquals(intArrayOf(), dividerEdges(listOf(10)))
        assertArrayEquals(intArrayOf(), dividerEdges(emptyList()))
    }

    @Test
    fun `a folded block in the middle leaves a single line`() {
        assertArrayEquals(intArrayOf(10), dividerEdges(listOf(10, 0, 20)))
    }

    @Test
    fun `a folded last block leaves no line on the card's bottom edge`() {
        assertArrayEquals(intArrayOf(), dividerEdges(listOf(10, 0)))
        assertArrayEquals(intArrayOf(10), dividerEdges(listOf(10, 20, 0)))
    }

    @Test
    fun `a folded first block leaves no line on the card's top edge`() {
        assertArrayEquals(intArrayOf(20), dividerEdges(listOf(0, 20, 5)))
    }

    @Test
    fun `a block still folding keeps its line until it reaches nothing`() {
        assertArrayEquals(intArrayOf(10), dividerEdges(listOf(10, 1)))
    }
}
