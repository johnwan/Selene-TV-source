package org.moontechlab.selenetv.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.moontechlab.selenetv.util.imageProxyFallback

class RemoteCoverTest {
    @Test fun coverMetadataIsOptionalAndPreserved() {
        val json = Json { ignoreUnknownKeys = false }
        val media = json.decodeFromString<RemoteMedia>("""{"title":"Movie","url":"https://example.com/v","coverUrl":"https://example.com/a.jpg"}""")
        assertEquals("https://example.com/a.jpg", media.coverUrl)
        assertEquals("", json.decodeFromString<RemoteMedia>("""{"title":"Movie","url":"https://example.com/v"}""").coverUrl)
        assertEquals("", media.copy(coverUrl = "null").safeCoverUrl())
        assertEquals("", media.copy(coverUrl = "https://user:secret@example.com/a.jpg").safeCoverUrl())
    }
    @Test fun fallbackUsesServerOnceAndNeverProxiesServerOrLocalUrls() {
        assertEquals("https://server.example/api/image-proxy?url=https%3A%2F%2Fimages.example%2Fa.jpg", imageProxyFallback("https://images.example/a.jpg", "https://server.example"))
        assertNull(imageProxyFallback("https://server.example/api/image-proxy?url=x", "https://server.example"))
        assertNull(imageProxyFallback("http://127.0.0.1/a.jpg", "https://server.example"))
        assertNull(imageProxyFallback("http://192.168.1.2/a.jpg", "https://server.example"))
        assertNull(imageProxyFallback("http://[fd00::1]/a.jpg", "https://server.example"))
        assertNull(imageProxyFallback("http://router.local/a.jpg", "https://server.example"))
    }
}
