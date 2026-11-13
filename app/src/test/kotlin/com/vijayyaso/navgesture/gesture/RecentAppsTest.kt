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

    @Test
    fun `the app in front is first when it is in the list`() {
        val resumed = listOf("a", "b", "c")
        val ordered = RecentApps.order(resumed, "nav", emptySet()) { true }
        assertEquals(true, RecentApps.currentIsFirst(resumed, ordered))
    }

    @Test
    fun `a hidden app in front means the first listed app is not the current one`() {
        // The user is in Settings, which advertises CATEGORY_HOME and is dropped.
        val resumed = listOf("a", "b", "settings")
        val ordered = RecentApps.order(resumed, "nav", setOf("settings")) { true }
        assertEquals(listOf("b", "a"), ordered)
        assertEquals(false, RecentApps.currentIsFirst(resumed, ordered))
    }

    @Test
    fun `no usage events means no current app`() {
        assertEquals(false, RecentApps.currentIsFirst(emptyList(), emptyList()))
    }

    @Test
    fun `a long stay in one app leaves the short window too thin`() {
        // 40 minutes in one video: no resume inside the last 30 minutes.
        assertEquals(true, RecentApps.tooFew(emptyList(), currentFirst = false))
        // Only the app in front: nothing to switch to.
        assertEquals(true, RecentApps.tooFew(listOf("video"), currentFirst = true))
        // The app in front and one other.
        assertEquals(true, RecentApps.tooFew(listOf("video", "chat"), currentFirst = true))
    }

    @Test
    fun `two apps to switch to are enough`() {
        assertEquals(false, RecentApps.tooFew(listOf("video", "chat", "mail"), currentFirst = true))
        // The app in front was hidden, so both listed apps are others.
        assertEquals(false, RecentApps.tooFew(listOf("chat", "mail"), currentFirst = false))
    }

    @Test
    fun `a wider window only adds older apps after the short list`() {
        val short = listOf("chat", "settings", "video")
        val wide = listOf("maps", "video", "mail", "gone") + short
        val homes = setOf("settings")
        val launchable: (String) -> Boolean = { it != "gone" }
        val shortOrder = RecentApps.order(short, "nav", homes, launchable)
        val wideOrder = RecentApps.order(wide, "nav", homes, launchable)
        assertEquals(listOf("video", "chat"), shortOrder)
        assertEquals(listOf("video", "chat", "mail", "maps"), wideOrder)
        // The newest event is the same, so the app in front is still known and left out.
        assertEquals(RecentApps.currentIsFirst(short, shortOrder), RecentApps.currentIsFirst(wide, wideOrder))
        assertEquals(true, RecentApps.currentIsFirst(wide, wideOrder))
    }

    @Test
    fun `after a long stay the wider window still puts the app in front first`() {
        // The short window was empty; the wide one ends with the video resumed 40 minutes ago.
        val wide = listOf("maps", "chat", "video")
        val ordered = RecentApps.order(wide, "nav", emptySet()) { true }
        assertEquals(listOf("video", "chat", "maps"), ordered)
        assertEquals(true, RecentApps.currentIsFirst(wide, ordered))
        assertEquals(false, RecentApps.tooFew(ordered, currentFirst = true))
    }
}
