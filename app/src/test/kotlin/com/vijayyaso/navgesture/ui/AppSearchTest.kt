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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    private val gpay = LaunchableApp("com.google.android.apps.nbu.paisa.user", "Google Pay", null)
    private val keep = LaunchableApp("com.google.android.keep", "Keep Notes", null)
    private val bank = LaunchableApp("com.sbi.lotusintouch", "YONO SBI", null)
    private val apps = listOf(gpay, keep, bank)

    @Test
    fun `an empty search shows every app in its order`() {
        assertEquals(apps, filterApps(apps, ""))
    }

    @Test
    fun `a search of only spaces shows every app`() {
        assertEquals(apps, filterApps(apps, "   "))
    }

    @Test
    fun `the label matches in any case`() {
        assertEquals(listOf(bank), filterApps(apps, "yono"))
        assertEquals(listOf(keep), filterApps(apps, "NOTES"))
    }

    @Test
    fun `spaces around the search are ignored`() {
        assertEquals(listOf(keep), filterApps(apps, "  keep "))
    }

    @Test
    fun `the package name matches too`() {
        assertEquals(listOf(gpay), filterApps(apps, "paisa"))
        assertEquals(listOf(bank), filterApps(apps, "com.sbi"))
    }

    @Test
    fun `a search that matches nothing leaves an empty list`() {
        assertEquals(emptyList<LaunchableApp>(), filterApps(apps, "whatsapp"))
    }

    @Test
    fun `several matches keep the list order`() {
        assertEquals(listOf(gpay, keep), filterApps(apps, "google"))
    }

    @Test
    fun `the rule on its own`() {
        assertTrue(appMatches("Google Pay", "com.google.android.apps.nbu.paisa.user", "pay"))
        assertTrue(appMatches("Google Pay", "com.google.android.apps.nbu.paisa.user", " PAISA "))
        assertFalse(appMatches("Google Pay", "com.google.android.apps.nbu.paisa.user", "keep"))
        assertTrue(appMatches("Google Pay", "com.google.android.apps.nbu.paisa.user", ""))
    }
}
