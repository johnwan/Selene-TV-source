package org.moontechlab.selenetv.remote

import kotlinx.serialization.Serializable
import java.net.URI

class RemoteFailure(val code: String, override val message: String = code) : RuntimeException(message)
fun requireRemote(condition: Boolean, code: String = "invalid_request") { if (!condition) throw RemoteFailure(code) }

@Serializable data class RemoteEpisode(val title: String, val url: String)
@Serializable data class RemoteMedia(
    val kind: String = "vod", val title: String, val url: String,
    val headers: Map<String, String> = emptyMap(), val sourceId: String = "", val videoId: String = "",
    val episodes: List<RemoteEpisode> = emptyList(), val episodeIndex: Int = 0,
    val startPositionMs: Long = 0, val rate: Double = 1.0, val liveChannelId: String = "", val coverUrl: String = "",
) {
    fun safeCoverUrl(): String {
        if (coverUrl.isEmpty() || coverUrl.length > 8192) return ""
        val uri = runCatching { URI(coverUrl) }.getOrNull() ?: return ""
        return if (uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) coverUrl else ""
    }
    fun validate() {
        requireRemote(kind in listOf("vod", "live") && title.isNotBlank() && title.length <= 512)
        validateMediaUrl(url)
        requireRemote(startPositionMs in 0..9007199254740991L && rate.isFinite() && rate in 0.5..2.0)
        requireRemote(episodes.size <= 2000 && episodeIndex >= 0 && (episodes.isEmpty() || episodeIndex < episodes.size))
        episodes.forEach { validateMediaUrl(it.url) }
        requireRemote(headers.size <= 16)
        headers.forEach { (k,v) -> requireRemote(k.lowercase() in setOf("user-agent", "referer", "origin", "cookie", "authorization") && v.length <= 8192 && '\r' !in v && '\n' !in v) }
    }
}
fun validateMediaUrl(value: String) {
    val uri = try { URI(value) } catch (_: Exception) { throw RemoteFailure("invalid_media") }
    val host = uri.host?.lowercase()?.removeSurrounding("[", "]") ?: ""
    requireRemote(uri.scheme in listOf("https", "http") && host.isNotEmpty() && uri.userInfo == null, "invalid_media")
    requireRemote(host != "localhost" && !host.endsWith(".localhost") && !host.startsWith("127.") && host != "::1" && host != "0.0.0.0" && !host.startsWith("::ffff:127."), "invalid_media")
}
@Serializable data class PlayRequest(val requestId: String, val mediaId: String, val takeOver: Boolean = false, val media: RemoteMedia)
@Serializable data class CommandRequest(val requestId: String, val sessionId: String, val mediaId: String, val action: String, val positionMs: Long? = null, val rate: Double? = null)
@Serializable data class RemoteStatus(val tvId: String, val ownerClientId: String = "", val sessionId: String = "", val mediaId: String = "", val requestId: String = "", val state: String = "idle", val positionMs: Long = 0, val durationMs: Long = 0, val rate: Double = 1.0, val capabilities: List<String> = emptyList(), val error: String? = null, val episodeIndex: Int = 0, val title: String = "")
data class Acceptance(val isNew: Boolean, val status: RemoteStatus)
