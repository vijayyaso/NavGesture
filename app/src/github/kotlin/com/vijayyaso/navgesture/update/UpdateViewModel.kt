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

package com.vijayyaso.navgesture.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Scoped to the activity, which keeps the daily check to one per app open across rotations. */
internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: UpdateController

    // Parent of every check, download and install, so switching off stops all of them at once.
    private val work = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val workScope = CoroutineScope(viewModelScope.coroutineContext + work)

    val state: StateFlow<UpdateState>

    init {
        val transport = UrlConnectionTransport(userAgent(InstalledVersion.name(application)))
        controller = UpdateController(
            source = GitHubReleaseSource(transport),
            downloader = HttpApkDownloader(
                File(application.noBackupFilesDir, "update"),
                transport,
                Dispatchers.Main,
            ),
            installer = SessionApkInstaller(application),
            store = updateStore(application),
            installedVersion = { InstalledVersion.name(application) },
            clock = System::currentTimeMillis,
        )
        state = controller.state
        launchWork { controller.onAppOpen() }
        viewModelScope.launch { InstallEvents.outcomes.collect(controller::onInstallOutcome) }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            launchWork { controller.setEnabled(true) }
            return
        }
        work.cancelChildren()
        // Outside the work, so a later switch-off can never cancel this one half way.
        viewModelScope.launch { controller.setEnabled(false) }
    }

    fun checkNow() = launchWork { controller.checkNow() }

    fun downloadAndInstall() = launchWork { controller.downloadAndInstall() }

    fun onResume() = launchWork { controller.onResume() }

    private fun launchWork(block: suspend () -> Unit) {
        workScope.launch { block() }
    }
}
