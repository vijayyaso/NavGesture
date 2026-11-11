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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The samples are real `dumpsys shortcut` output, read as the app's own uid (run-as) on the
 * API 36 and API 30 emulators with the shortcut test app from e2e/fixtures/shortcut-app installed.
 */
class ShortcutDumpTest {

    private fun sample(name: String): String =
        requireNotNull(ShortcutDumpTest::class.java.getResource("/shortcut-dump/$name")).readText()

    private val all by lazy { ShortcutDump.parse(sample("api36-all.txt")) }

    private fun shortcut(packageName: String, id: String): DumpedShortcut? =
        all.firstOrNull { it.packageName == packageName && it.id == id }

    @Test
    fun `a GPay-style run-time shortcut keeps its action, flags, target and string extra`() {
        val qr = requireNotNull(shortcut(FIXTURE, "qr_scanner"))
        assertEquals("Scan QR", qr.label)
        assertEquals(1, qr.flags and 1) // dynamic
        val intent = qr.intents.single()
        assertEquals("RUN", intent.action)
        assertEquals(0x14000000, intent.flags)
        assertEquals("$FIXTURE/.LauncherActivity", intent.component)
        assertEquals(mapOf("some unique action key" to "QR_SCANNER"), intent.extras)
    }

    @Test
    fun `extras are typed by how they print, and commas or equals in a value stay in it`() {
        val typed = requireNotNull(shortcut(FIXTURE, "typed"))
        val extras = typed.intents.single().extras
        assertEquals(3, extras["count"])
        assertEquals(9_999_999_999L, extras["big"])
        assertEquals(true, extras["on"])
        assertEquals("a, b", extras["note"])
        assertEquals("x=y", extras["pair"])
        assertEquals(5, extras.size)
    }

    @Test
    fun `a shortcut with a data URI is skipped, since the dump hides the URI`() {
        assertNull(shortcut(FIXTURE, "with_data"))
        // Fixed shortcuts with data too: the dialer's content URI, Drive's empty and http ones.
        assertNull(shortcut("com.google.android.dialer", "dialer-shortcut-add-contact"))
        assertNull(shortcut("com.google.android.apps.docs", "launcher_shortcut_upload"))
        assertNull(shortcut("com.google.android.apps.docs", "launcher_shortcut_scan"))
    }

    @Test
    fun `the parser leaves the export check to the app, so a private target still parses`() {
        val hidden = requireNotNull(shortcut(FIXTURE, "private"))
        assertEquals("$FIXTURE/.PrivateActivity", hidden.intents.single().component)
        assertEquals(emptyMap<String, Any>(), hidden.intents.single().extras)
    }

    @Test
    fun `Clock's run-time shortcuts read with labels, icons and extras`() {
        val timer = requireNotNull(shortcut(CLOCK, "Timer_Create"))
        assertEquals("New timer", timer.label)
        assertEquals(2131231737, timer.iconRes)
        val intent = timer.intents.single()
        assertEquals("android.intent.action.SET_TIMER", intent.action)
        assertEquals("$CLOCK/com.android.deskclock.HandleSetApiCalls", intent.component)
        assertEquals(mapOf("com.android.deskclock.extra.EVENT_LABEL" to "Shortcut"), intent.extras)

        val stopwatch = requireNotNull(shortcut(CLOCK, "Stopwatch"))
        assertEquals(emptyMap<String, Any>(), stopwatch.intents.single().extras)
        assertEquals(4, all.count { it.packageName == CLOCK })
    }

    @Test
    fun `fixed shortcuts in the dump read too, with no target and boolean extras`() {
        val search = requireNotNull(shortcut("com.google.android.youtube", "search-shortcut"))
        assertEquals(0x1a4, search.flags)
        assertNull(search.intents.single().component)
        assertEquals(0x1000c000, search.intents.single().flags)

        val gmail = requireNotNull(shortcut("com.google.android.gm", "manifest_compose_shortcut"))
        assertEquals(
            mapOf("is-default-cse-allowed" to true, "from-launcher-shortcut" to true),
            gmail.intents.single().extras,
        )
        assertTrue(all.none { it.label == "null" })
    }

    @Test
    fun `every shortcut is filed under the package whose section it is in`() {
        val sections = Regex("""Package: (\S+)\s+UID""").findAll(sample("api36-all.txt"))
            .map { it.groupValues[1] }.toSet()
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.packageName in sections })
        // 27 blocks in the sample; 6 carry a data URI.
        assertEquals(21, all.size)
    }

    @Test
    fun `a one-package dump reads the same as that package in the full one`() {
        val one = ShortcutDump.parse(sample("api36-one-package.txt"))
        assertEquals(all.filter { it.packageName == FIXTURE }.toSet(), one.toSet())
        assertEquals(setOf("qr_scanner", "typed", "private"), one.map { it.id }.toSet())
    }

    @Test
    fun `Android 11 prints the same layout, so the same shortcuts read the same way`() {
        val api30 = ShortcutDump.parse(sample("api30-one-package.txt"))
        assertEquals(all.filter { it.packageName == FIXTURE }.toSet(), api30.toSet())
    }

    @Test
    fun `refusals are told apart and give no shortcuts`() {
        val noDump = sample("api36-no-dump.txt")
        val noUsage = sample("api36-no-usage-access.txt")
        assertEquals(DumpDenial.NO_PERMISSION, ShortcutDump.denial(noDump))
        assertEquals(DumpDenial.NO_USAGE_ACCESS, ShortcutDump.denial(noUsage))
        assertEquals(DumpDenial.NONE, ShortcutDump.denial(sample("api36-all.txt")))
        assertEquals(emptyList<DumpedShortcut>(), ShortcutDump.parse(noDump))
        assertEquals(emptyList<DumpedShortcut>(), ShortcutDump.parse(noUsage))
    }

    @Test
    fun `a label with the refusal's words is not a refusal`() {
        val sample = sample("api36-one-package.txt")
        val inLabel = sample.replace("shortLabel=Scan QR", "shortLabel=Permission Denial: app-op")
        val ownLine = sample.replace("shortLabel=Scan QR", "shortLabel=x\nPermission Denial: app-op")
        assertEquals(DumpDenial.NONE, ShortcutDump.denial(inLabel))
        assertEquals(DumpDenial.NONE, ShortcutDump.denial(ownLine))
        assertTrue(ShortcutDump.parse(inLabel).any { it.id == "qr_scanner" })
    }

    @Test
    fun `disabled, shadow, key-only and cached-only shortcuts are skipped`() {
        val block = sample("api36-one-package.txt")
        val qrFlags = "id=qr_scanner, flags=0x81 [DynStr]"
        fun withFlags(hex: String) = ShortcutDump.parse(block.replace(qrFlags, "id=qr_scanner, flags=0x$hex [x]"))
            .any { it.id == "qr_scanner" }
        assertTrue(withFlags("81"))
        assertTrue(withFlags("82")) // pinned only
        assertTrue(!withFlags("c1")) // disabled
        assertTrue(!withFlags("1081")) // shadow
        assertTrue(!withFlags("91")) // key fields only
        assertTrue(!withFlags("4080")) // cached for notifications, not dynamic or pinned
    }

    @Test
    fun `a block claiming another package than its section is skipped`() {
        val forged = sample("api36-one-package.txt")
            .replace("packageName=$FIXTURE", "packageName=com.example.bank")
        assertEquals(emptyList<DumpedShortcut>(), ShortcutDump.parse(forged))
    }

    @Test
    fun `text the intent cannot be rebuilt from makes the shortcut skipped`() {
        assertNull(ShortcutDump.parseIntent("act=RUN dat=fixture://open/... cmp=a/.B"))
        assertNull(ShortcutDump.parseIntent("act=RUN cmp=a/.B (has extras)"))
        assertNull(ShortcutDump.parseIntent("act=RUN clip={text/plain T:x}"))
        assertNull(ShortcutDump.parseIntent("act=RUN cmp=a/.B sel=act=VIEW}"))
        assertNull(ShortcutDump.parseIntent("cmp=a/.B")) // no action
        val full = requireNotNull(ShortcutDump.parseIntent("act=RUN cat=[a.B,c.D] typ=text/plain flg=0x10000000 pkg=a cmp=a/.B"))
        assertEquals(listOf("a.B", "c.D"), full.categories)
        assertEquals("text/plain", full.type)
        assertEquals("a", full.packageName)
    }

    @Test
    fun `parcelled, array and nested extras are refused rather than guessed`() {
        assertNull(ShortcutDump.parseExtras("PersistableBundle[mParcelledData.dataSize=120]"))
        assertNull(ShortcutDump.parseExtras("PersistableBundle[EMPTY_PARCEL]"))
        assertNull(ShortcutDump.parseExtras("PersistableBundle[{ids=[I@1a2b3c}]"))
        assertNull(ShortcutDump.parseExtras("PersistableBundle[{names=[Ljava.lang.String;@9f}]"))
        assertNull(ShortcutDump.parseExtras("PersistableBundle[{inner=PersistableBundle[{a=1}]}]"))
        assertEquals(emptyMap<String, Any>(), ShortcutDump.parseExtras("PersistableBundle[{}]"))
        assertEquals(emptyMap<String, Any>(), ShortcutDump.parseExtras("null"))
        assertEquals(1.5, ShortcutDump.parseExtras("PersistableBundle[{d=1.5}]")!!["d"])
        assertEquals("NaN", ShortcutDump.typed("NaN"))
        assertEquals("99999999999999999999", ShortcutDump.typed("99999999999999999999"))
    }

    @Test
    fun `an intents line that is not a list of intents gives nothing`() {
        assertNull(ShortcutDump.parseIntents("null"))
        assertNull(ShortcutDump.parseIntents("size:1"))
        assertNull(ShortcutDump.parseIntents("[]"))
        val two = requireNotNull(
            ShortcutDump.parseIntents(
                "[Intent { act=MAIN cmp=a/.Home }/null, Intent { act=RUN cmp=a/.Scan }/PersistableBundle[{k=v}]]",
            ),
        )
        assertEquals(listOf("MAIN", "RUN"), two.map { it.action })
        assertEquals(mapOf("k" to "v"), two[1].extras)
    }

    private companion object {
        const val FIXTURE = "com.vijayyaso.navgesture.fixture.shortcuts"
        const val CLOCK = "com.google.android.deskclock"
    }
}
