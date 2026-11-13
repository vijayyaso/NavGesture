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
import org.junit.Test

class DiagnosticsReportTest {

    private val rows = listOf(
        ShownRow("Version", "1.2.0"),
        ShownRow("App build", "github"),
        ShownRow("Android version", "15 (API 35)"),
        ShownRow("Device", "Xiaomi 23049PCD8G"),
        ShownRow("Last failure", "None", tag = "some_tag"),
    )

    @Test
    fun `the title, then every row as label colon value, in order`() {
        assertEquals(
            """
            NavGesture
            Version: 1.2.0
            App build: github
            Android version: 15 (API 35)
            Device: Xiaomi 23049PCD8G
            Last failure: None
            """.trimIndent(),
            diagnosticsReport("NavGesture", rows, emptyList()),
        )
    }

    @Test
    fun `each log follows the rows under its own title, line by line`() {
        val logs = listOf(
            ShownRow("Recorded failure", "back: refused\n"),
            ShownRow("Service history", "10:02 started\n10:01 stopped"),
        )
        assertEquals(
            """
            NavGesture
            Version: 1.2.0

            Recorded failure:
            back: refused

            Service history:
            10:02 started
            10:01 stopped
            """.trimIndent(),
            diagnosticsReport("NavGesture", rows.take(1), logs),
        )
    }

    @Test
    fun `no rows and no logs leave only the title`() {
        assertEquals("NavGesture", diagnosticsReport("NavGesture", emptyList(), emptyList()))
    }

    @Test
    fun `the tag a row carries for the e2e suites is not copied`() {
        val text = diagnosticsReport("NavGesture", rows, emptyList())
        assertEquals(false, text.contains("some_tag"))
    }
}
