package org.moontechlab.selenetv.remote

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class RemoteSessionTest {
    private fun request(id: String = "r1", mediaId: String = "m1", takeover: Boolean = false) =
        PlayRequest(id, mediaId, takeover, RemoteMedia(title = "Test", url = "https://example.com/video.mp4"))

    @Test fun duplicateStartsOnceAndChangedReuseFails() {
        val s = RemoteSession("tv1")
        assertTrue(s.acceptPlay("phone1", request()).isNew)
        assertFalse(s.acceptPlay("phone1", request()).isNew)
        assertThrows(RemoteFailure::class.java) { s.acceptPlay("phone1", request(mediaId = "changed")) }
    }
    @Test fun ownerAndSessionGuardsPreventStaleControl() {
        val s = RemoteSession("tv1")
        val first = s.acceptPlay("phone1", request()).status
        assertThrows(RemoteFailure::class.java) { s.acceptPlay("phone2", request("r2", "m2")) }
        s.acceptPlay("phone2", request("r3", "m3", true))
        assertThrows(RemoteFailure::class.java) { s.acceptCommand("phone1", CommandRequest("c1", first.sessionId, "m1", "pause")) }
    }
    @Test fun requestCacheIsBoundedAndLiveCannotSeek() {
        val s = RemoteSession("tv1")
        repeat(140) { s.acceptPlay("phone1", request("r$it", "m$it")) }
        assertEquals(128, s.cachedRequests)
        val live = s.acceptPlay("phone1", request("live", "live").copy(media = RemoteMedia(kind = "live", title = "Live", url = "https://example.com/live.m3u8"))).status
        assertFalse(live.capabilities.contains("seek"))
        assertThrows(RemoteFailure::class.java) { s.acceptCommand("phone1", CommandRequest("seek", live.sessionId, "live", "seek", 1)) }
    }
    @Test fun validateRejectsUnsafeOrInvalidDescriptors() {
        listOf("file:///tmp/video", "http://127.0.0.1/v", "http://localhost/v", "http://[::1]/v").forEach {
            assertThrows(RemoteFailure::class.java) { RemoteMedia(title = "Test", url = it).validate() }
        }
        assertThrows(RemoteFailure::class.java) { RemoteMedia(title = "Test", url = "https://example.com/v", startPositionMs = -1).validate() }
    }
    @Test fun sharedFixturesDecode() {
        val raw = javaClass.classLoader!!.getResource("tv_protocol/movie.json")!!.readText()
        val r = Json.decodeFromString<PlayRequest>(raw)
        assertEquals(42000L, r.media.startPositionMs)
        assertEquals(2, r.media.episodes.size)
    }
}
