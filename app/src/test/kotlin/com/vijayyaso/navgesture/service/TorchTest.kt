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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorchTest {

    private val front = TorchCamera("1", isBack = false, hasFlash = true)
    private val backNoFlash = TorchCamera("2", isBack = true, hasFlash = false)
    private val back = TorchCamera("3", isBack = true, hasFlash = true)
    private val otherBack = TorchCamera("4", isBack = true, hasFlash = true)

    private class FakeCameras(var list: List<TorchCamera>) : TorchCameras {
        var answer: TorchRequest = TorchRequest.Done
        var listener: TorchListener? = null
        var registers = 0
        var unregisters = 0
        val requests = mutableListOf<Pair<String, Boolean>>()

        override fun cameras() = list

        override fun register(listener: TorchListener) {
            registers++
            this.listener = listener
        }

        override fun unregister() {
            unregisters++
            listener = null
        }

        override fun setTorch(cameraId: String, on: Boolean): TorchRequest {
            requests += cameraId to on
            return answer
        }
    }

    private val failures = mutableListOf<String>()
    private val lines = mutableListOf<String>()

    private fun torch(cameras: FakeCameras) =
        Torch(cameras, { failures += it }, { lines += it }).also { it.start() }

    @Test fun `it uses the first back camera that has a flash`() {
        val cameras = FakeCameras(listOf(front, backNoFlash, back, otherBack))
        assertTrue(torch(cameras).toggle())
        assertEquals(listOf("3" to true), cameras.requests)
    }

    @Test fun `off goes on`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        cameras.listener?.onChanged("3", false)
        assertTrue(torch.toggle())
        assertEquals(listOf("3" to true), cameras.requests)
        assertEquals(listOf("TORCH on"), lines)
        assertTrue(failures.isEmpty())
    }

    @Test fun `on goes off`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        cameras.listener?.onChanged("3", true)
        assertEquals(TorchState.ON, torch.state)
        assertTrue(torch.toggle())
        assertEquals(listOf("3" to false), cameras.requests)
        assertEquals(listOf("TORCH off"), lines)
    }

    @Test fun `a camera in use is recorded and the state does not change`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        cameras.answer = TorchRequest.Failed("camera in use")
        assertFalse(torch.toggle())
        assertEquals(listOf("torch: camera in use"), failures)
        assertEquals(listOf("TORCH failed: camera in use"), lines)
        assertEquals(TorchState.OFF, torch.state)
        cameras.listener?.onChanged("3", true)
        assertFalse(torch.toggle())
        assertEquals(TorchState.ON, torch.state)
        assertEquals(2, failures.size)
    }

    @Test fun `a phone with no flash records it and asks the camera for nothing`() {
        val cameras = FakeCameras(listOf(front, backNoFlash))
        val torch = torch(cameras)
        assertFalse(torch.toggle())
        assertEquals(listOf("torch: no flash on this phone"), failures)
        assertEquals(listOf("TORCH failed: no flash on this phone"), lines)
        assertTrue(cameras.requests.isEmpty())
        assertEquals(TorchState.OFF, torch.state)
    }

    @Test fun `a camera list that was empty at start is read again on a toggle`() {
        val cameras = FakeCameras(emptyList())
        val torch = torch(cameras)
        cameras.list = listOf(back)
        assertTrue(torch.toggle())
        assertEquals(listOf("3" to true), cameras.requests)
    }

    @Test fun `a change made by another app is respected`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        assertTrue(torch.toggle())
        cameras.listener?.onChanged("3", true)
        cameras.listener?.onChanged("3", false)
        assertEquals(TorchState.OFF, torch.state)
        assertTrue(torch.toggle())
        assertEquals(listOf("3" to true, "3" to true), cameras.requests)
        cameras.listener?.onChanged("3", true)
        assertTrue(torch.toggle())
        assertEquals("3" to false, cameras.requests.last())
    }

    @Test fun `a report about another camera is ignored`() {
        val cameras = FakeCameras(listOf(back, otherBack))
        val torch = torch(cameras)
        cameras.listener?.onChanged("4", true)
        cameras.listener?.onUnavailable("4")
        assertEquals(TorchState.OFF, torch.state)
    }

    @Test fun `an unavailable torch is asked to turn on, and a failure is recorded`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        cameras.listener?.onUnavailable("3")
        assertEquals(TorchState.UNAVAILABLE, torch.state)
        cameras.answer = TorchRequest.Failed("camera in use")
        assertFalse(torch.toggle())
        assertEquals(listOf("3" to true), cameras.requests)
        assertEquals(TorchState.UNAVAILABLE, torch.state)
        assertEquals(1, failures.size)
    }

    @Test fun `it registers once and unregisters on stop`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        torch.start()
        assertEquals(1, cameras.registers)
        torch.stop()
        assertEquals(1, cameras.unregisters)
        assertNull(cameras.listener)
        torch.stop()
        assertEquals(1, cameras.unregisters)
    }

    @Test fun `it can start again after a stop`() {
        val cameras = FakeCameras(listOf(back))
        val torch = torch(cameras)
        torch.stop()
        torch.start()
        assertEquals(2, cameras.registers)
    }

    @Test fun `a stop that never started unregisters nothing`() {
        val cameras = FakeCameras(listOf(back))
        Torch(cameras, { failures += it }, { lines += it }).stop()
        assertEquals(0, cameras.unregisters)
    }
}
