package org.moontechlab.selenetv.remote

interface RemotePlayerPort {
    val playing: Boolean; val buffering: Boolean; val started: Boolean
    val position: Long; val duration: Long; var rate: Double; val error: String?
    fun play(); fun pause(); fun seek(ms: Long)
}

class RemotePlaybackBinding(private val session: RemoteSession, private val sessionId: String, private val player: RemotePlayerPort, private val stop: () -> Unit) {
    fun refresh() {
        val state = when {
            player.error != null -> "failed"
            player.buffering || !player.started -> "loading"
            player.playing -> "playing"
            player.duration > 0 && player.position >= player.duration -> "ended"
            else -> "paused"
        }
        session.update(sessionId, state, player.position, player.duration, player.rate, if (player.error != null) "media_unavailable" else null)
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
