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


package com.vijayyaso.navgesture.bankpause

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseClockTest {

    private val banks = setOf("com.bank.one", "com.bank.two")

    @Test
    fun `no app change keeps the pause`() {
        assertFalse(PauseClock.shouldResume(banks, emptyList(), nowMs = 60_000))
    }

    @Test
    fun `the bank app in front keeps the pause`() {
        val fronts = listOf(FrontApp("com.bank.one", 1_000))
        assertFalse(PauseClock.shouldResume(banks, fronts, nowMs = 60_000))
    }

    @Test
    fun `home for less than the away time keeps the pause`() {
        val fronts = listOf(FrontApp("com.launcher", 10_000))
        assertFalse(PauseClock.shouldResume(banks, fronts, nowMs = 17_999))
    }

    @Test
    fun `home for the away time ends the pause`() {
        val fronts = listOf(FrontApp("com.launcher", 10_000))
        assertTrue(PauseClock.shouldResume(banks, fronts, nowMs = 18_000))
    }

    @Test
    fun `reopening the bank inside the away time keeps the pause`() {
        val fronts = listOf(FrontApp("com.launcher", 10_000), FrontApp("com.bank.one", 14_000))
        assertFalse(PauseClock.shouldResume(banks, fronts, nowMs = 40_000))
    }

    @Test
    fun `away time counts from the first app outside the picked set`() {
        val fronts = listOf(
            FrontApp("com.launcher", 10_000),
            FrontApp("com.chat", 15_000),
        )
        assertTrue(PauseClock.shouldResume(banks, fronts, nowMs = 18_000))
    }

    @Test
    fun `moving between two picked apps keeps the pause`() {
        val fronts = listOf(FrontApp("com.bank.one", 1_000), FrontApp("com.bank.two", 2_000))
        assertFalse(PauseClock.shouldResume(banks, fronts, nowMs = 60_000))
    }

    @Test
    fun `events out of order are read by time`() {
        val fronts = listOf(FrontApp("com.bank.one", 14_000), FrontApp("com.launcher", 10_000))
        assertFalse(PauseClock.shouldResume(banks, fronts, nowMs = 40_000))
    }

    @Test
    fun `leaving the picked set is seen`() {
        assertTrue(PauseClock.leftPicked(banks, listOf(FrontApp("com.launcher", 1))))
        assertFalse(PauseClock.leftPicked(banks, listOf(FrontApp("com.bank.two", 1))))
        assertFalse(PauseClock.leftPicked(banks, emptyList()))
    }
}
