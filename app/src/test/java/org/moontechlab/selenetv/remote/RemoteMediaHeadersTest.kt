package org.moontechlab.selenetv.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RemoteMediaHeadersTest {
    @Test fun credentialsStayOnOriginButUserAgentReachesSegments() {
        MockWebServer().use { origin -> MockWebServer().use { cdn ->
            origin.start(); cdn.start()
            origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", cdn.url("/segment")))
            cdn.enqueue(MockResponse().setBody("ok"))
            val client = remoteMediaHttpClient(OkHttpClient(), origin.url("/video").toString(), mapOf("Cookie" to "test=secret", "Authorization" to "Bearer test", "User-Agent" to "Selene-QA"))
            client.newCall(Request.Builder().url(origin.url("/video")).build()).execute().use { assertEquals(200, it.code) }
            val initial = origin.takeRequest(); val redirected = cdn.takeRequest()
            assertEquals("test=secret", initial.getHeader("Cookie"))
            assertEquals("Bearer test", initial.getHeader("Authorization"))
            assertNull(redirected.getHeader("Cookie")); assertNull(redirected.getHeader("Authorization"))
            assertEquals("Selene-QA", redirected.getHeader("User-Agent"))
        } }
    }
}
