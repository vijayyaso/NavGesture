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

/** One intent of a dumped shortcut, as text; [component] is the short flattened form. */
data class DumpedIntent(
    val action: String,
    val categories: List<String>,
    val type: String?,
    val identifier: String?,
    val flags: Int,
    val packageName: String?,
    val component: String?,
    /** String, Int, Long, Double or Boolean values, typed by how the dump prints them. */
    val extras: Map<String, Any>,
)

/** A shortcut read off `dumpsys shortcut`, kept only when its intents can be rebuilt exactly. */
data class DumpedShortcut(
    val packageName: String,
    val id: String,
    val flags: Int,
    val label: String,
    val iconRes: Int,
    val intents: List<DumpedIntent>,
)

/** Why a dump came back without shortcuts: the service's own refusal text. */
enum class DumpDenial { NONE, NO_PERMISSION, NO_USAGE_ACCESS }

/**
 * Reads the debug text of `dumpsys shortcut` (ShortcutPackage.dump and ShortcutInfo.toDumpString
 * in AOSP; the same layout on Android 11 to 16). It is debug text, not an API, so every rule
 * here fails soft: a block that does not read as expected is skipped, never guessed at.
 *
 * What the text cannot carry, and so what makes a shortcut skipped:
 *  - a data URI (`dat=`): Intent.toString prints Uri.toSafeString, which hides the path;
 *  - clip data, a selector or an original intent;
 *  - extras still parcelled, or array and nested-bundle extras, which print as object names.
 *
 * Extras print without their types, so a value is read as a boolean when it is `true` or
 * `false`, a whole number as an Int (Long when too big), a decimal as a Double, else a String.
 */
object ShortcutDump {

    fun parse(text: String): List<DumpedShortcut> {
        val result = ArrayList<DumpedShortcut>()
        var section: String? = null
        var block: Block? = null
        fun flush() {
            block?.finish(section)?.let { result += it }
            block = null
        }
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val header = PACKAGE_HEADER.matchEntire(line)
            when {
                header != null -> {
                    flush()
                    section = header.groupValues[1]
                }
                line.startsWith(BLOCK_START) -> {
                    flush()
                    block = Block(line.removePrefix(BLOCK_START))
                }
                else -> block?.let { current ->
                    FIELDS.firstOrNull { line.startsWith("$it=") }?.let { key ->
                        current.fields.putIfAbsent(key, line.substring(key.length + 1))
                    }
                    // iconRes is the block's last field (toDumpString's internal data).
                    if (line.startsWith("iconRes=")) flush()
                }
            }
        }
        flush()
        return result
    }

    /** The service's refusal, if the text is one: it prints it and returns normally. */
    fun denial(text: String): DumpDenial {
        val refusal = text.lineSequence().firstOrNull { it.contains("Permission Denial") }
            ?: return DumpDenial.NONE
        return if (refusal.contains("app-op")) DumpDenial.NO_USAGE_ACCESS else DumpDenial.NO_PERMISSION
    }

    /** A ShortcutInfo block whose field lines are still being collected. */
    private class Block(private val firstLine: String) {
        val fields = HashMap<String, String>()

        fun finish(section: String?): DumpedShortcut? {
            // "<id>, flags=0x<hex> [<readable flags>]"; the id may hold anything, so cut at the last.
            val cut = firstLine.lastIndexOf(", flags=0x")
            if (cut <= 0) return null
            val id = firstLine.substring(0, cut)
            val flags = firstLine.substring(cut + ", flags=0x".length).substringBefore(' ')
                .toLongOrNull(16)?.toInt() ?: return null
            if (flags and LISTED == 0 || flags and HIDDEN != 0) return null

            val packageName = fields["packageName"] ?: return null
            if (packageName != section) return null
            val label = fields["shortLabel"]?.substringBeforeLast(", resId=")
                ?.takeUnless { it.isEmpty() || it == "null" } ?: return null
            val iconRes = fields["iconRes"]?.substringBefore('[')?.toIntOrNull() ?: 0
            val intents = parseIntents(fields["intents"] ?: return null) ?: return null
            return DumpedShortcut(packageName, id, flags, label, iconRes, intents)
        }
    }

    /** "[Intent { … }/extras, Intent { … }/extras]"; null if any intent cannot be rebuilt. */
    internal fun parseIntents(text: String): List<DumpedIntent>? {
        if (!text.startsWith("[") || !text.endsWith("]")) return null
        val body = text.substring(1, text.length - 1)
        if (!body.startsWith(INTENT_START)) return null
        val parts = body.removePrefix(INTENT_START).split(", $INTENT_START")
        val intents = parts.map { part ->
            val end = part.indexOf(INTENT_END)
            if (end < 0) return null
            val intent = parseIntent(part.substring(0, end)) ?: return null
            val extras = parseExtras(part.substring(end + INTENT_END.length)) ?: return null
            intent.copy(extras = extras)
        }
        return intents.ifEmpty { null }
    }

    /** Intent.toShortString's space-separated fields; null for one the text hides. */
    internal fun parseIntent(text: String): DumpedIntent? {
        val fields = LinkedHashMap<String, String>()
        var last: String? = null
        text.split(' ').forEach { word ->
            val key = INTENT_KEYS.firstOrNull { word.startsWith(it) }
            if (key != null && !fields.containsKey(key)) {
                fields[key] = word.substring(key.length)
                last = key
            } else {
                // An action or type with a space in it: the word belongs to the field before.
                val previous = last ?: return null
                fields[previous] = fields.getValue(previous) + " " + word
            }
        }
        if (UNREBUILDABLE.any { fields.containsKey(it) }) return null
        val action = fields["act="]?.takeIf { it.isNotEmpty() } ?: return null
        val categories = fields["cat="]?.let { value ->
            if (!value.startsWith("[") || !value.endsWith("]")) return null
            value.substring(1, value.length - 1).split(',').filter { it.isNotEmpty() }
        }.orEmpty()
        val flags = fields["flg=0x"]?.let { it.toLongOrNull(16)?.toInt() ?: return null } ?: 0
        return DumpedIntent(
            action = action,
            categories = categories,
            type = fields["typ="],
            identifier = fields["id="],
            flags = flags,
            packageName = fields["pkg="],
            component = fields["cmp="],
            extras = emptyMap(),
        )
    }

    /** "null", or PersistableBundle.toString of an unparcelled bundle; null when unreadable. */
    internal fun parseExtras(text: String): Map<String, Any>? {
        if (text == "null") return emptyMap()
        if (!text.startsWith(BUNDLE_START) || !text.endsWith(BUNDLE_END)) return null
        val body = text.substring(BUNDLE_START.length, text.length - BUNDLE_END.length)
        if (body.isEmpty()) return emptyMap()
        // ArrayMap.toString: "k=v, k=v". A piece with no "=" belongs to the value before it.
        val entries = ArrayList<Pair<String, String>>()
        body.split(", ").forEach { piece ->
            val equals = piece.indexOf('=')
            if (equals > 0) {
                entries += piece.substring(0, equals) to piece.substring(equals + 1)
            } else {
                val previous = entries.removeLastOrNull() ?: return null
                entries += previous.first to previous.second + ", " + piece
            }
        }
        val extras = LinkedHashMap<String, Any>()
        entries.forEach { (key, value) ->
            if (extras.containsKey(key)) return null
            extras[key] = typed(value) ?: return null
        }
        return extras
    }

    /** The value a printed extra most likely held; null for arrays and nested bundles. */
    internal fun typed(value: String): Any? = when {
        ARRAY.matches(value) || value.startsWith("PersistableBundle[") -> null
        value == "true" -> true
        value == "false" -> false
        WHOLE.matches(value) -> value.toIntOrNull() ?: value.toLongOrNull() ?: value
        DECIMAL.matches(value) -> value.toDoubleOrNull() ?: value
        else -> value
    }

    // ShortcutInfo flags (public constants since Android 7.1).
    private const val FLAG_DYNAMIC = 1
    private const val FLAG_PINNED = 1 shl 1
    private const val FLAG_KEY_FIELDS_ONLY = 1 shl 4
    private const val FLAG_MANIFEST = 1 shl 5
    private const val FLAG_DISABLED = 1 shl 6
    private const val FLAG_SHADOW = 1 shl 12

    /** What a launcher would show or keep: run-time, fixed and pinned shortcuts. */
    private const val LISTED = FLAG_DYNAMIC or FLAG_PINNED or FLAG_MANIFEST

    /** Disabled, restored-but-not-installed, or stripped of its intents. */
    private const val HIDDEN = FLAG_DISABLED or FLAG_SHADOW or FLAG_KEY_FIELDS_ONLY

    private val PACKAGE_HEADER = Regex("""Package: (\S+)\s+UID: \d+""")
    private const val BLOCK_START = "ShortcutInfo {id="
    private val FIELDS = listOf("packageName", "shortLabel", "intents", "iconRes")
    private const val INTENT_START = "Intent { "
    private const val INTENT_END = " }/"
    private const val BUNDLE_START = "PersistableBundle[{"
    private const val BUNDLE_END = "}]"

    // Intent.toShortString's keys, in its order. "id=" is the intent identifier.
    private val INTENT_KEYS = listOf(
        "act=", "cat=", "dat=", "typ=", "id=", "flg=0x", "xflg=0x", "pkg=", "cmp=", "bnds=",
        "clip={", "(has", "u=", "sel=", "org={",
    )
    private val UNREBUILDABLE = listOf("dat=", "clip={", "(has", "sel=", "org={")

    private val ARRAY = Regex("""\[+([IJDZ]|L[\w.$]+;)@\p{XDigit}+""")
    private val WHOLE = Regex("""-?\d+""")
    private val DECIMAL = Regex("""-?\d+\.\d+(E-?\d+)?""")
}
