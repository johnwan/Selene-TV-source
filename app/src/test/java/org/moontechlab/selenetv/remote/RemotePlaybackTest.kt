package org.moontechlab.selenetv.remote

import org.junit.Assert.*
import org.junit.Test

class RemotePlaybackTest {
    private class FakePlayer : RemotePlayerPort {
        override var playing = false; override var buffering = true; override var started = false
        override var position = 42000L; override var duration = 90000L; override var rate = 1.0
        override var error: String? = null
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun seek(ms: Long) { position = ms }
    }
    @Test fun statusComesFromPlayerAndCommandsAreExplicit() {
        val s = RemoteSession("tv"); val status = s.acceptPlay("phone", PlayRequest("r", "m", media = RemoteMedia(title = "T", url = "https://example.com/v"))).status
        val p = FakePlayer(); var stopped = false
        val b = RemotePlaybackBinding(s, status.sessionId, p) { stopped = true }
        b.refresh(); assertEquals("loading", s.snapshot().state)
        p.buffering = false; p.started = true; p.playing = true; b.refresh()
        assertEquals("playing", s.snapshot().state); assertEquals(42000L, s.snapshot().positionMs)
        b.command(CommandRequest("c", status.sessionId, "m", "pause")); assertFalse(p.playing)
        b.command(CommandRequest("c2", status.sessionId, "m", "pause")); assertFalse(p.playing)
        b.command(CommandRequest("c3", status.sessionId, "m", "play")); assertTrue(p.playing)
        b.command(CommandRequest("c4", status.sessionId, "m", "stop")); assertTrue(stopped)
        assertEquals("idle", s.snapshot().state)
    }
    @Test fun oldBindingCannotAffectReplacement() {
        val s = RemoteSession("tv"); val media = RemoteMedia(title = "T", url = "https://example.com/v")
        val first = s.acceptPlay("phone", PlayRequest("r", "m", media = media)).status
        val p = FakePlayer(); val old = RemotePlaybackBinding(s, first.sessionId, p) {}
        s.acceptPlay("phone", PlayRequest("r2", "m2", media = media))
        old.refresh(); old.close()
        assertEquals("m2", s.snapshot().mediaId)
    }
}
