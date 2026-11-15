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

package com.vijayyaso.navgesture.demo

import com.vijayyaso.navgesture.setup.SetupRecord

/** What the app opens on: Home alone, or Home with the demo or the disclosure on top. */
enum class FirstStack { HOME, DEMO, DISCLOSURE }

/** The first-run rule, decided once the saved record has loaded. */
object FirstRun {
    /**
     * The tile's request wins, as it asks for consent. Anyone who saw the demo, has the service
     * on now, had it on before, or agreed to the disclosure is past the first run. [serviceOn]
     * is the live status, because the record learns of a service turned on only later.
     */
    fun firstStack(record: SetupRecord, serviceOn: Boolean, tileAskedDisclosure: Boolean): FirstStack = when {
        tileAskedDisclosure -> FirstStack.DISCLOSURE
        record.demoSeen -> FirstStack.HOME
        serviceOn -> FirstStack.HOME
        record.serviceEverEnabled -> FirstStack.HOME
        record.disclosureAccepted -> FirstStack.HOME
        else -> FirstStack.DEMO
    }

    /**
     * Saved at the decision, so the demo never shows by itself a second time, also after a tile
     * open or a process killed mid-demo. Only a plain Home for a record that has it skips the write.
     */
    fun marksDemoSeen(record: SetupRecord, stack: FirstStack): Boolean =
        stack != FirstStack.HOME || !record.demoSeen
}
