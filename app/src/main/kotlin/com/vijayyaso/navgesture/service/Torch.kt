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

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** One camera as the torch rule sees it. */
internal data class TorchCamera(val id: String, val isBack: Boolean, val hasFlash: Boolean)

/** What the camera service said to a torch request. */
internal sealed interface TorchRequest {
    data object Done : TorchRequest

    data class Failed(val reason: String) : TorchRequest
}

/** Hears the camera service's torch reports. */
internal interface TorchListener {
    fun onChanged(cameraId: String, on: Boolean)

    fun onUnavailable(cameraId: String)
}

/** The camera calls the torch needs, so a test can stand in for the phone. */
internal interface TorchCameras {
    /** Every camera, or none when the list cannot be read. */
    fun cameras(): List<TorchCamera>

    fun register(listener: TorchListener)

    fun unregister()

    fun setTorch(cameraId: String, on: Boolean): TorchRequest
}

/** What the camera service last said about the torch. */
internal enum class TorchState { OFF, ON, UNAVAILABLE }

/**
 * Turns the back flash on or off. The state is only what the camera service reports, so a torch
 * that another app switched is read correctly. A failure is recorded and nothing else changes.
 */
internal class Torch(
    private val cameras: TorchCameras,
    private val record: (String) -> Unit,
    private val log: (String) -> Unit,
) : TorchListener {

    private var cameraId: String? = null

    private var registered = false

    @Volatile
    var state = TorchState.OFF
        private set

    /** Starts listening. A second call while listening does nothing. */
    fun start() {
        if (registered) return
        cameraId = findCamera()
        cameras.register(this)
        registered = true
    }

    /** Stops listening, as the service ends. */
    fun stop() {
        if (!registered) return
        cameras.unregister()
        registered = false
        state = TorchState.OFF
    }

    /** Asks for the opposite of the reported state. True when the camera service took it. */
    fun toggle(): Boolean {
        // The list may have been empty at start, if the camera service was not up yet.
        val id = cameraId ?: findCamera()?.also { cameraId = it }
        if (id == null) return fail("no flash on this phone")
        val turnOn = state != TorchState.ON
        return when (val result = cameras.setTorch(id, turnOn)) {
            TorchRequest.Done -> {
                log("TORCH ${if (turnOn) "on" else "off"}")
                true
            }
            is TorchRequest.Failed -> fail(result.reason)
        }
    }

    override fun onChanged(cameraId: String, on: Boolean) {
        if (cameraId == this.cameraId) state = if (on) TorchState.ON else TorchState.OFF
    }

    override fun onUnavailable(cameraId: String) {
        if (cameraId == this.cameraId) state = TorchState.UNAVAILABLE
    }

    private fun findCamera(): String? =
        cameras.cameras().firstOrNull { it.isBack && it.hasFlash }?.id

    private fun fail(reason: String): Boolean {
        record("torch: $reason")
        log("TORCH failed: $reason")
        return false
    }
}

/** The phone's own cameras. No permission is needed to read them or to set the torch. */
internal class AndroidTorchCameras(context: Context) : TorchCameras {

    private val manager: CameraManager? = context.getSystemService(CameraManager::class.java)

    private var callback: CameraManager.TorchCallback? = null

    override fun cameras(): List<TorchCamera> {
        val camera = manager ?: return emptyList()
        val ids = try {
            camera.cameraIdList
        } catch (e: CameraAccessException) {
            return emptyList()
        }
        return ids.mapNotNull { id ->
            try {
                val traits = camera.getCameraCharacteristics(id)
                TorchCamera(
                    id = id,
                    isBack = traits.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK,
                    hasFlash = traits.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                )
            } catch (e: CameraAccessException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override fun register(listener: TorchListener) {
        val camera = manager ?: return
        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) =
                listener.onChanged(cameraId, enabled)

            override fun onTorchModeUnavailable(cameraId: String) = listener.onUnavailable(cameraId)
        }
        callback = torchCallback
        camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }

    override fun unregister() {
        val torchCallback = callback ?: return
        callback = null
        manager?.unregisterTorchCallback(torchCallback)
    }

    override fun setTorch(cameraId: String, on: Boolean): TorchRequest {
        val camera = manager ?: return TorchRequest.Failed("no camera service")
        return try {
            camera.setTorchMode(cameraId, on)
            TorchRequest.Done
        } catch (e: CameraAccessException) {
            TorchRequest.Failed(
                when (e.reason) {
                    CameraAccessException.CAMERA_IN_USE,
                    CameraAccessException.MAX_CAMERAS_IN_USE,
                    -> "camera in use"
                    else -> "camera service not available"
                },
            )
        } catch (e: IllegalArgumentException) {
            TorchRequest.Failed("that camera has no flash")
        }
    }
}
