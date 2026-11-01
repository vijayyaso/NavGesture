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


package com.vijayyaso.navgesture.bankpause

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.diagnostics.ServiceLog
import com.vijayyaso.navgesture.service.NavGestureAccessibilityService
import com.vijayyaso.navgesture.setup.ServiceKeeper
import com.vijayyaso.navgesture.ui.MainActivity
import com.vijayyaso.navgesture.ui.hasUsageAccess

/**
 * Keeps a bank pause and ends it. Runs while the Accessibility service is switched off, so
 * it is a foreground service: once a second it reads which app is in front and, when the
 * user has been out of every picked app for [PauseClock.AWAY_MS], switches the service back
 * on with the adb grant. The service's own connect then clears the pause and stops this.
 *
 * Restarted by the system after a kill (START_STICKY): the pause is saved with its start
 * time, so a fresh watcher reads the same history.
 */
class BankPauseWatcher : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var pause: SavedPause? = null
    private var polling = false
    private var readFailures = 0
    private var resumeStartedAt = 0L
    private var resumeAttempts = 0

    private val tick = object : Runnable {
        override fun run() {
            poll()
            if (polling) handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BankPause.watcherRunning = true
        BankPause.resumeHook = { handler.post { resume(hold = true) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val saved = BankPause.paused(this)
        // Android requires startForeground soon after every start, even one that stops at once.
        goForeground(saved)
        if (saved == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        pause = saved
        if (intent?.action == BankPause.ACTION_RESUME) {
            resume(hold = true)
        }
        if (!polling) {
            polling = true
            handler.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        BankPause.watcherRunning = false
        BankPause.resumeHook = null
        super.onDestroy()
    }

    private fun poll() {
        val saved = pause ?: return
        if (resumeStartedAt != 0L) {
            waitForService()
            return
        }
        // Without usage access the history reads as empty, which would hold the pause forever.
        if (!hasUsageAccess(this)) {
            ServiceLog.add(this, "bank pause: usage access is gone, resuming")
            resume(hold = false)
            return
        }
        val fronts = BankPause.frontApps(this, saved.startedAtMs)
        if (fronts == null) {
            readFailures++
            if (readFailures >= PauseClock.MAX_READ_FAILURES) {
                ServiceLog.add(this, "bank pause: the usage history cannot be read, resuming")
                resume(hold = false)
            }
            return
        }
        readFailures = 0
        if (PauseClock.shouldResume(saved.picked, fronts, System.currentTimeMillis())) {
            ServiceLog.add(this, "bank pause: left ${saved.packageName}, resuming")
            resume(hold = false)
        }
    }

    private fun resume(hold: Boolean) {
        val saved = pause ?: BankPause.paused(this)
        if (hold && saved != null) BankPause.setHold(this, saved.picked, System.currentTimeMillis())
        if (resumeStartedAt != 0L) return
        resumeStartedAt = SystemClock.elapsedRealtime()
        resumeAttempts = 1
        turnOn()
    }

    /** The service's connect stops this watcher. Until then, retry a refused write. */
    private fun waitForService() {
        if (NavGestureAccessibilityService.isRunning()) return
        if (SystemClock.elapsedRealtime() - resumeStartedAt < RETRY_AFTER_MS * resumeAttempts) return
        if (resumeAttempts >= MAX_RESUME_ATTEMPTS) {
            ServiceLog.add(this, "bank pause: could not turn the service back on")
            notifyResumeFailed()
            BankPause.clearPause(this)
            stopSelf()
            return
        }
        resumeAttempts++
        turnOn()
    }

    private fun turnOn() {
        val written = ServiceKeeper.turnOn(this)
        ServiceLog.add(this, "bank pause: turning back on (try $resumeAttempts, write ok=$written)")
    }

    private fun goForeground(saved: SavedPause?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.bank_pause_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val label = saved?.let { appLabel(it.packageName) } ?: getString(R.string.bank_pause_a_bank_app)
        val resumeIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BankPauseWatcher::class.java).setAction(BankPause.ACTION_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.bank_pause_title, label))
            .setContentText(getString(R.string.bank_pause_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.bank_pause_text)))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.bank_pause_resume_now), resumeIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun notifyResumeFailed() {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.bank_pause_failed_title))
            .setContentText(getString(R.string.bank_pause_failed_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.bank_pause_failed_text)))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(FAILED_ID, notification)
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun appLabel(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private companion object {
        const val CHANNEL = "bank_pause"
        const val NOTIFICATION_ID = 7
        const val FAILED_ID = 8
        const val POLL_MS = 1_000L
        const val RETRY_AFTER_MS = 4_000L
        const val MAX_RESUME_ATTEMPTS = 3
    }
}
