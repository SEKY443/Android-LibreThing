package io.github.seky443.librething.service

import io.github.seky443.librething.service.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [GoProcessController]'s parser for the daemon's logrus text output. */
class GoProcessControllerLogParsingTest {

    private fun parse(line: String) = GoProcessController.parseLogLine(line)

    @Test
    fun `plain logrus line with quoted msg is parsed into level and message`() {
        val entry = parse("""time="2026-08-18T12:00:00+08:00" level=info msg="running go-librespot v1.2.3"""")

        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("running go-librespot v1.2.3", entry.message)
    }

    @Test
    fun `warning and warn both map to WARN`() {
        assertEquals(LogLevel.WARN, parse("""level=warning msg="disk cache almost full"""").level)
        assertEquals(LogLevel.WARN, parse("""level=warn msg="disk cache almost full"""").level)
    }

    @Test
    fun `error fatal and panic all map to ERROR`() {
        assertEquals(LogLevel.ERROR, parse("""level=error msg="failed to bind api server"""").level)
        assertEquals(LogLevel.ERROR, parse("""level=fatal msg="could not start"""").level)
        assertEquals(LogLevel.ERROR, parse("""level=panic msg="unrecoverable"""").level)
    }

    @Test
    fun `debug and trace both map to DEBUG`() {
        assertEquals(LogLevel.DEBUG, parse("""level=debug msg="dealer ping"""").level)
        assertEquals(LogLevel.DEBUG, parse("""level=trace msg="raw frame"""").level)
    }

    @Test
    fun `escaped quotes inside a quoted msg are unescaped`() {
        // A raw logrus line escapes internal quotes as literal backslash-quote pairs.
        val entry = parse("level=info msg=\"track \\\"Song Title\\\" resolved\"")

        assertEquals("track \"Song Title\" resolved", entry.message)
    }

    @Test
    fun `an error field from WithError is appended to the message`() {
        val entry = parse("level=fatal msg=\"failed loading config\" error=\"unmarshal type mismatch\"")

        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("failed loading config: unmarshal type mismatch", entry.message)
    }

    @Test
    fun `a line with no fields at all falls back to info with the raw line as the message`() {
        val entry = parse("not a logrus line at all")

        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("not a logrus line at all", entry.message)
    }
}
