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

/** A label and its value as a screen shows them; [tag] is the value's e2e resource id. */
internal data class ShownRow(val label: String, val value: String, val tag: String? = null)

/**
 * The plain text that Copy diagnostics puts on the clipboard: the title, each row as
 * "label: value", then each log under its own title. It holds only what it is given.
 */
internal fun diagnosticsReport(title: String, rows: List<ShownRow>, logs: List<ShownRow>): String =
    buildString {
        append(title)
        rows.forEach { append('\n').append(it.label).append(": ").append(it.value) }
        logs.forEach { log ->
            append("\n\n").append(log.label).append(':')
            append('\n').append(log.value.trimEnd())
        }
    }
