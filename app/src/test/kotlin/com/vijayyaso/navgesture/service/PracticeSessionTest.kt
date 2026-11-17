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

package com.vijayyaso.navgesture.service

import com.vijayyaso.navgesture.demo.DemoMiss
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import com.vijayyaso.navgesture.gesture.TouchSample
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTest {

    private val t = 10_000L

    @Test fun `the gate is off until a page begins`() {
        assertFalse(PracticeGate().isActive(t))
    }

    @Test fun `begin turns it on for the 2 s lease`() {
        val gate = PracticeGate()
        gate.begin(t)
        assertTrue(gate.isActive(t))
        assertTrue(gate.isActive(t + 1_999))
        assertFalse(gate.isActive(t + 2_000))
    }

    @Test fun `a renew in time extends the lease from the renew`() {
        val gate = PracticeGate()
        val token = gate.begin(t)
        assertTrue(gate.renew(token, t + 1_500))
        assertTrue(gate.isActive(t + 3_499))
        assertFalse(gate.isActive(t + 3_500))
    }

    @Test fun `a renew after the lease ran out fails and does not revive it`() {
        val gate = PracticeGate()
        val token = gate.begin(t)
        assertFalse(gate.renew(token, t + 2_000))
        assertFalse(gate.isActive(t + 2_000))
        assertFalse(gate.renew(token, t + 2_001))
        assertFalse(gate.isActive(t + 2_001))
    }

    @Test fun `an old token cannot renew or end a newer owner's gate`() {
        val gate = PracticeGate()
        val old = gate.begin(t)
        val current = gate.begin(t + 100)
        gate.end(old)
        assertTrue(gate.isActive(t + 200))
        assertFalse(gate.renew(old, t + 1_500))
        // The old renew did not extend it: the lease still runs from the newer begin.
        assertTrue(gate.isActive(t + 2_099))
        assertFalse(gate.isActive(t + 2_100))
        assertTrue(gate.renew(current, t + 2_000))
        assertTrue(gate.isActive(t + 3_999))
    }

    @Test fun `end by the owner turns it off at once`() {
        val gate = PracticeGate()
        val token = gate.begin(t)
        gate.end(token)
        assertFalse(gate.isActive(t))
        assertFalse(gate.renew(token, t + 1))
    }

    @Test fun `a token of 0 does nothing`() {
        val gate = PracticeGate()
        gate.begin(t)
        gate.end(0L)
        assertTrue(gate.isActive(t + 1))
        assertFalse(gate.renew(0L, t + 1))
    }

    @Test fun `tokens are never 0 and never repeat`() {
        val gate = PracticeGate()
        val tokens = (1..1_000).map { gate.begin(t + it) }
        assertTrue(tokens.none { it == 0L })
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test fun `a second thread sees begin and end`() {
        val gate = PracticeGate()
        val token = gate.begin(t)
        val seenOn = AtomicBoolean(false)
        val seenOff = AtomicBoolean(true)
        val ended = CountDownLatch(1)
        val reader = Thread {
            seenOn.set(gate.isActive(t + 1))
            ended.await(5, TimeUnit.SECONDS)
            seenOff.set(gate.isActive(t + 2))
        }
        reader.start()
        // Let the reader read "on" before the end.
        while (reader.state != Thread.State.WAITING && reader.state != Thread.State.TIMED_WAITING) Thread.yield()
        gate.end(token)
        ended.countDown()
        reader.join(5_000)
        assertTrue(seenOn.get())
        assertFalse(seenOff.get())
    }

    @Test fun `blocks refuses and logs only while the gate is on`() {
        var now = t
        val logged = mutableListOf<String>()
        val state = PracticeState(PracticeGate(), clock = { now }, log = { logged += it })
        assertFalse(state.blocks("gesture", "Back"))
        assertTrue(logged.isEmpty())
        val token = state.begin()
        assertTrue(state.blocks("launch", "com.example"))
        assertEquals(listOf("PRACTICE_BLOCKED launch com.example"), logged)
        state.end(token)
        assertFalse(state.blocks("gesture", "Home"))
        state.begin()
        now += PracticeGate.LEASE_MS
        assertFalse(state.blocks("gesture", "Home"))
        assertEquals(1, logged.size)
    }

    @Test fun `a page that subscribes first gets the next event and no older one`() = runBlocking {
        val state = PracticeState(PracticeGate(), clock = { t }, log = {})
        state.emit(PracticeEvent.ListWaiting)
        var token = 0L
        val event = state.events
            .onSubscription {
                token = state.begin()
                state.emit(PracticeEvent.Missed(DemoMiss.Cancelled))
            }
            .first()
        assertEquals(PracticeEvent.Missed(DemoMiss.Cancelled), event)
        assertNotEquals(0L, token)
        assertTrue(state.isActive())
    }

    @Test fun `the installed strips are a state`() {
        val state = PracticeState(PracticeGate(), clock = { t }, log = {})
        assertEquals(null, state.strips.value)
        val strips = InstalledStrips(GestureConfiguration(), landscape = false)
        state.setStrips(strips)
        assertEquals(strips, state.strips.value)
        state.setStrips(null)
        assertEquals(null, state.strips.value)
    }

    @Test fun `a touch that moved 11 px is a tap and 12 px is not`() {
        fun path(dx: Float) = listOf(
            TouchSample(100f, 500f, 0L),
            TouchSample(100f + dx / 2, 500f, 40L),
            TouchSample(100f + dx, 500f, 80L),
        )
        assertTrue(isTap(path(11f)))
        assertFalse(isTap(path(12f)))
        assertTrue(isTap(listOf(TouchSample(5f, 5f, 0L))))
        assertFalse(isTap(emptyList()))
    }
}
