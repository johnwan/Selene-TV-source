package org.moontechlab.selenetv.remote

import java.util.UUID

class RemoteSession(tvId: String) {
    private var status = RemoteStatus(tvId)
    private val requests = LinkedHashMap<String, Pair<Any, RemoteStatus>>()
    val cachedRequests: Int @Synchronized get() = requests.size
    @Synchronized fun snapshot(): RemoteStatus = status
    private fun id(value: String) = requireRemote(value.isNotBlank() && value.length <= 128)
    private fun duplicate(clientId: String, requestId: String, request: Any): Acceptance? {
        id(requestId)
        val previous = requests["$clientId:$requestId"] ?: return null
        requireRemote(previous.first == request, "request_reused")
        return Acceptance(false, previous.second)
    }
    private fun record(clientId: String, requestId: String, request: Any): Acceptance {
        requests["$clientId:$requestId"] = request to status
        if (requests.size > 128) requests.remove(requests.keys.first())
        return Acceptance(true, status)
    }
    @Synchronized fun acceptPlay(clientId: String, request: PlayRequest): Acceptance {
        request.media.validate(); id(request.mediaId)
        duplicate(clientId, request.requestId, request)?.let { return it }
        requireRemote(status.ownerClientId.isEmpty() || status.ownerClientId == clientId || request.takeOver, "controller_busy")
        status = RemoteStatus(status.tvId, clientId, UUID.randomUUID().toString(), request.mediaId, request.requestId, "loading", request.media.startPositionMs, rate = request.media.rate, capabilities = listOf("play", "pause", "stop", "rate") + if(request.media.kind == "vod") listOf("seek") else emptyList())
        return record(clientId, request.requestId, request)
    }
    @Synchronized fun acceptCommand(clientId: String, request: CommandRequest): Acceptance {
        requireRemote(status.ownerClientId == clientId, "controller_busy")
        requireRemote(status.sessionId == request.sessionId && status.mediaId == request.mediaId && status.sessionId.isNotEmpty(), "stale_session")
        duplicate(clientId, request.requestId, request)?.let { return it }
        requireRemote(request.action in status.capabilities, "unsupported_command")
        if (request.action == "seek") requireRemote(request.positionMs != null && request.positionMs >= 0 && request.positionMs <= 9007199254740991L)
        if (request.action == "rate") requireRemote(request.rate != null && request.rate.isFinite() && request.rate in 0.5..2.0)
        return record(clientId, request.requestId, request)
    }
    @Synchronized fun update(sessionId: String, state: String, positionMs: Long = 0, durationMs: Long = 0, rate: Double = 1.0, error: String? = null) {
        if (status.sessionId != sessionId) return
        status = status.copy(state = state, positionMs = positionMs.coerceAtLeast(0), durationMs = durationMs.coerceAtLeast(0), rate = rate, error = error)
    }
    @Synchronized fun release(sessionId: String) {
        if (status.sessionId == sessionId) status = RemoteStatus(status.tvId)
    }
}
