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

import android.os.SystemClock
import android.util.Log
import com.vijayyaso.navgesture.demo.DemoMiss
import com.vijayyaso.navgesture.demo.DemoResult
import com.vijayyaso.navgesture.gesture.GestureConfiguration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Practice is on", with an owner and a lease. Only [begin] turns it on. [end] and [renew] with
 * any token but the owner's do nothing, so an old page's late "off" cannot cancel a new page's
 * "on". It turns itself off [leaseMs] after the last [begin] or [renew], so a page that stops
 * renewing for any reason, a bug included, gives the real gestures back within the lease.
 * Every time is passed in; [PracticeSession] supplies the clock.
 *
 * Read on the main thread and on the performer's worker thread, so one lock guards the owner
 * and the lease.
 */
internal class PracticeGate(private val leaseMs: Long = LEASE_MS) {

    private val lock = Any()
    private var owner = NO_OWNER
    private var leaseUntilMs = 0L
    private var lastToken = NO_OWNER

    /** Takes the gate for a new owner and returns its token, never 0 and never used before. */
    fun begin(nowMs: Long): Long = synchronized(lock) {
        lastToken += 1
        if (lastToken == NO_OWNER) lastToken += 1
        owner = lastToken
        leaseUntilMs = nowMs + leaseMs
        owner
    }

    /** Extends the lease; false, and no change, unless [token] owns it and the lease has not run out. */
    fun renew(token: Long, nowMs: Long): Boolean = synchronized(lock) {
        if (token == NO_OWNER || token != owner || nowMs >= leaseUntilMs) return false
        leaseUntilMs = nowMs + leaseMs
        true
    }

    /** Turns the gate off, only for its owner. */
    fun end(token: Long) {
        synchronized(lock) {
            if (token != NO_OWNER && token == owner) owner = NO_OWNER
        }
    }

    fun isActive(nowMs: Long): Boolean = synchronized(lock) { owner != NO_OWNER && nowMs < leaseUntilMs }

    companion object {
        const val LEASE_MS = 2_000L
        private const val NO_OWNER = 0L
    }
}

/** The strips as they are installed now: the settings they were built with, and their rotation. */
internal data class InstalledStrips(val configuration: GestureConfiguration, val landscape: Boolean)

/** What the overlay tells the live practice page instead of running a gesture. */
internal sealed interface PracticeEvent {
    /** A whole gesture. [label] is the app's name for a pick from the app list, else null. */
    data class Caught(val result: DemoResult, val label: String?) : PracticeEvent

    /** A touch that was no gesture. */
    data class Missed(val reason: DemoMiss) : PracticeEvent

    /** The app list stays open after the lift, waiting for a tap on an app. */
    data object ListWaiting : PracticeEvent
}

/**
 * The gate with its clock, the installed strips and the event flow, as one unit that a test
 * can build with its own clock and log. [PracticeSession] is the one the app uses.
 */
internal open class PracticeState(
    private val gate: PracticeGate,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    private val stripsState = MutableStateFlow<InstalledStrips?>(null)

    /** A state, not an event, so a page that starts listening after the install still sees it. */
    val strips: StateFlow<InstalledStrips?> = stripsState.asStateFlow()

    // No replay, so an old gesture never reaches a page that opens later.
    private val eventFlow = MutableSharedFlow<PracticeEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<PracticeEvent> = eventFlow.asSharedFlow()

    fun begin(): Long = gate.begin(clock())

    fun renew(token: Long): Boolean = gate.renew(token, clock())

    fun end(token: Long) = gate.end(token)

    fun isActive(): Boolean = gate.isActive(clock())

    /** The overlay sets this at the end of every install and clears it when the strips go. */
    fun setStrips(strips: InstalledStrips?) {
        stripsState.value = strips
    }

    /** Sends [event] to the page, if one listens; never waits. */
    fun emit(event: PracticeEvent) {
        eventFlow.tryEmit(event)
    }

    /**
     * The backstop's one check, at the last step before a real action. True, with a log line,
     * when practice is on, so the caller refuses the action. The report layer should have caught
     * the gesture first, so a true here means the gate turned on in the last few milliseconds.
     */
    fun blocks(route: String, id: String): Boolean {
        if (!isActive()) return false
        log("PRACTICE_BLOCKED $route $id")
        return true
    }

    companion object {
        const val EVENT_BUFFER = 16
    }
}

/**
 * The live practice page's gate, in memory only. The service and the app share one process, so
 * no broadcast or intent is used and no other app can fake it, and a killed process can never
 * leave it on. The service never touches the gate when it stops: nothing reads it while the
 * service is down, and the lease alone ends it.
 */
internal object PracticeSession : PracticeState(
    gate = PracticeGate(),
    clock = SystemClock::uptimeMillis,
    log = { Log.i("NavGesture", it) },
)
