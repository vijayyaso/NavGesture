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

package com.vijayyaso.navgesture.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * When the service starts, stops and comes back, with the time. A force stop switches the
 * service off with no callback at all, so a gap between "stopped" lines and a "restored"
 * line is the only trace it leaves. Kept in app storage, shown in Troubleshooting.
 */
object ServiceLog {

    private const val TAG = "NavGesture"
    private const val FILE_NAME = "service_history.txt"
    private const val MAX_LINES = 30

    fun add(context: Context, event: String) {
        Log.i(TAG, "SERVICE_LOG $event")
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        runCatching {
            synchronized(this) {
                val file = file(context)
                val lines = if (file.exists()) file.readLines() else emptyList()
                // The tile repeats its check each time Control Center opens; one line is enough.
                if (lines.lastOrNull()?.substringAfter("  ") == event) return@synchronized
                file.writeText((lines + "$stamp  $event").takeLast(MAX_LINES).joinToString("\n"))
            }
        }.onFailure { Log.e(TAG, "Could not write the service history", it) }
    }

    /** Newest first, so the screen opens on what just happened. */
    fun read(context: Context): String? = runCatching {
        val file = file(context)
        if (file.exists()) file.readLines().asReversed().joinToString("\n").ifBlank { null } else null
    }.getOrNull()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
