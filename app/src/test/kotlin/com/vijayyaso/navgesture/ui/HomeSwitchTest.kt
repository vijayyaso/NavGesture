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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSwitchTest {

    @Test
    fun `a pending tap wins over the real state`() {
        assertFalse(switchChecked(pending = false, working = true))
        assertTrue(switchChecked(pending = true, working = false))
    }

    @Test
    fun `with nothing pending the switch shows the real state`() {
        assertTrue(switchChecked(pending = null, working = true))
        assertFalse(switchChecked(pending = null, working = false))
    }

    @Test
    fun `the pending tap ends once the real state reaches it`() {
        assertNull(pendingAfterRefresh(pending = false, working = false))
        assertNull(pendingAfterRefresh(pending = true, working = true))
    }

    @Test
    fun `a refresh before the real state moves keeps the tap pending`() {
        assertEquals(false, pendingAfterRefresh(pending = false, working = true))
        assertEquals(true, pendingAfterRefresh(pending = true, working = false))
    }

    @Test
    fun `a refresh with nothing pending leaves nothing pending`() {
        assertNull(pendingAfterRefresh(pending = null, working = true))
        assertNull(pendingAfterRefresh(pending = null, working = false))
    }

    @Test
    fun `off always sends a request, without trying to turn on`() {
        var tried = false
        assertTrue(switchSendsRequest(on = false) { tried = true; true })
        assertFalse(tried)
    }

    @Test
    fun `on sends a request only when it switched on in place`() {
        assertTrue(switchSendsRequest(on = true) { true })
        assertFalse(switchSendsRequest(on = true) { false })
    }

    @Test
    fun `a request holds the tap while it runs and clears it after the settle`() = runBlocking {
        val seen = mutableListOf<Boolean?>()
        var duringSettle: Boolean? = null
        var pending: Boolean? = null
        runSwitchRequest(
            target = false,
            setPending = { pending = it; seen += it },
            request = {},
            settle = { duringSettle = pending },
        )
        assertEquals(false, duringSettle)
        assertEquals(listOf(false, null), seen)
    }

    @Test
    fun `a refused request still clears the tap`() = runBlocking {
        var pending: Boolean? = null
        var refused = false
        runSwitchRequest(
            target = false,
            setPending = { pending = it },
            request = { refused = true },
            settle = {},
        )
        assertTrue(refused)
        assertNull(pending)
    }

    @Test
    fun `a failing request clears the tap and still reports the failure`() = runBlocking {
        var pending: Boolean? = null
        var settled = false
        val failure = runCatching {
            runSwitchRequest(
                target = true,
                setPending = { pending = it },
                request = { error("switch write failed") },
                settle = { settled = true },
            )
        }.exceptionOrNull()
        assertEquals("switch write failed", failure?.message)
        assertFalse(settled)
        assertNull(pending)
    }

    @Test
    fun `a failing settle clears the tap`() = runBlocking {
        var pending: Boolean? = null
        runCatching {
            runSwitchRequest(
                target = false,
                setPending = { pending = it },
                request = {},
                settle = { error("refresh failed") },
            )
        }
        assertNull(pending)
    }
}
