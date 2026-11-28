package io.github.seky443.librething.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMathTest {

    @Test
    fun `end points map to end points for any device step count`() {
        for (max in listOf(1, 7, 15, 25, 100, 150)) {
            assertEquals(0, VolumeMath.remoteToDevice(0, max))
            assertEquals(max, VolumeMath.remoteToDevice(VolumeMath.REMOTE_STEPS, max))
            assertEquals(0, VolumeMath.deviceToRemote(0, max))
            assertEquals(VolumeMath.REMOTE_STEPS, VolumeMath.deviceToRemote(max, max))
        }
    }

    @Test
    fun `our own write echoed back is always seen as already in sync`() {
        // The device->remote->device round trip must land on the same device step, otherwise the
        // echo of a mirrored write would be pushed back to the daemon as a "new" change (the drift
        // that made the first two-way sync get reverted). Covers every step of every common range,
        // including 150 where a single direction alone isn't stable.
        for (max in listOf(15, 25, 100, 150)) {
            for (device in 0..max) {
                val remote = VolumeMath.deviceToRemote(device, max)
                assertTrue("max=$max device=$device remote=$remote", VolumeMath.isInSync(device, remote, max))
            }
        }
    }

    @Test
    fun `a daemon value written to the stream is in sync with the stream afterwards`() {
        for (max in listOf(15, 25, 100, 150)) {
            for (remote in 0..VolumeMath.REMOTE_STEPS) {
                val device = VolumeMath.remoteToDevice(remote, max)
                assertTrue("max=$max remote=$remote device=$device", VolumeMath.isInSync(device, remote, max))
            }
        }
    }

    @Test
    fun `a real change of one device step is not in sync`() {
        // Spotify at 100% while the phone sits at 7/15: pressing up (8/15) must count as a change.
        assertFalse(VolumeMath.isInSync(8, 100, 15))
        assertEquals(53, VolumeMath.deviceToRemote(8, 15))
    }

    @Test
    fun `results are always clamped into range`() {
        assertEquals(15, VolumeMath.remoteToDevice(500, 15))
        assertEquals(0, VolumeMath.remoteToDevice(-5, 15))
        assertEquals(VolumeMath.REMOTE_STEPS, VolumeMath.deviceToRemote(99, 15))
    }
}
