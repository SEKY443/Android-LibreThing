package io.github.seky443.librething.service

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for a real crash caught on-device: while the daemon process is still
 * starting up, its API server isn't listening yet, so [GoLibrespotApiClient.getStatus] hits
 * a plain connection-refused error. It's called from a poll loop that already treats a null
 * result as "not ready yet" -- the bug was that the underlying IOException wasn't caught, so
 * it crashed the app instead of being treated as "not ready". Nothing needs to be listening
 * on the daemon's port for this test: that's exactly the condition being tested.
 */
class GoLibrespotApiClientTest {

    @Test
    fun `getStatus returns null instead of throwing when the daemon api server is unreachable`() {
        val client = GoLibrespotApiClient(onEvent = {}, onLog = {})

        val status = client.getStatus()

        assertNull(status)
    }
}
