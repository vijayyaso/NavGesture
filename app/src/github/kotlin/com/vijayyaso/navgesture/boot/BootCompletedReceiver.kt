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

package com.vijayyaso.navgesture.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vijayyaso.navgesture.navigation.NavigationIntegrationFactory
import com.vijayyaso.navgesture.navigation.NavigationMode
import com.vijayyaso.navgesture.setup.ServiceKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android rebinds the accessibility service on boot by itself. This puts the service back
 * when the system dropped its switch, see [ServiceKeeper]. It also
 * observes the navigation mode HyperOS resets across a reboot, for the UI to report.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Puts back a switch the system dropped before the restart. A force-stopped app gets
        // no boot broadcast, so the tile and the app opening cover that case.
        val pending = goAsync()
        work.launch {
            try {
                ServiceKeeper.check(context.applicationContext, "boot")
            } finally {
                pending.finish()
            }
        }

        val status = NavigationIntegrationFactory.create(context).status()
        if (status.available && status.currentMode != NavigationMode.GESTURAL) {
            Log.i(
                TAG,
                "Navigation mode after boot is ${status.currentMode}; " +
                    "gestural navigation was not restored by the system.",
            )
        }
    }

    private companion object {
        const val TAG = "NavGesture"
        val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
