package org.moontechlab.selenetv.remote

import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Shared by initial media, HLS playlists/segments and every redirect hop. */
fun remoteMediaHttpClient(base: OkHttpClient, url: String, headers: Map<String, String>): OkHttpClient {
    val origin = url.toHttpUrl()
    return base.newBuilder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val sameOrigin = request.url.scheme == origin.scheme && request.url.host == origin.host && request.url.port == origin.port
        val builder = request.newBuilder()
        headers.forEach { (name, value) ->
            if (!sameOrigin && (name.equals("Cookie", true) || name.equals("Authorization", true))) builder.removeHeader(name)
            else builder.header(name, value)
        }
        if (!sameOrigin) { builder.removeHeader("Cookie"); builder.removeHeader("Authorization") }
        chain.proceed(builder.build())
    }.build()
}

interface RemotePlayerPort {
    val playing: Boolean; val buffering: Boolean; val started: Boolean
    val position: Long; val duration: Long; var rate: Double; val error: String?
    fun play(); fun pause(); fun seek(ms: Long)
}

class RemotePlaybackBinding(private val session: RemoteSession, private val sessionId: String, private val player: RemotePlayerPort, private val stop: () -> Unit) {
    fun refresh(episodeIndex: Int? = null, title: String? = null) {
        val state = when {
            player.error != null -> "failed"
            player.buffering || !player.started -> "loading"
            player.playing -> "playing"
            player.duration > 0 && player.position >= player.duration -> "ended"
            else -> "paused"
        }
        session.update(sessionId, state, player.position, player.duration, player.rate, if (player.error != null) "media_unavailable" else null, episodeIndex, title)
    }
    fun command(request: CommandRequest) {
        requireRemote(session.snapshot().sessionId == sessionId && request.sessionId == sessionId, "stale_session")
        when (request.action) {
            "play" -> player.play()
            "pause" -> player.pause()
            "seek" -> player.seek(request.positionMs!!)
            "rate" -> player.rate = request.rate!!
            "stop" -> { player.pause(); session.release(sessionId); stop(); return }
        }
        refresh()
    }
    fun close() = session.release(sessionId)
}
