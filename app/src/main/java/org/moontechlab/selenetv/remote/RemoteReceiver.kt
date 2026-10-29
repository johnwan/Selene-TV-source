package org.moontechlab.selenetv.remote

import android.content.Context
import android.os.Build
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

@Serializable data class PairRequest(val clientId: String, val name: String, val requestSecret: String)
@Serializable data class PairPoll(val pairId: String, val requestSecret: String)
@Serializable data class ReceiverInfo(val version: Int = 1, val tvId: String, val name: String, val capabilities: List<String> = listOf("vod", "live", "control"))
@Serializable data class ReceiverError(val code: String, val message: String = code)

object RemoteReceiver {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    val pending = MutableStateFlow<PendingPair?>(null)
    val phones = MutableStateFlow<List<TrustedPhone>>(emptyList())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val playbackActive = MutableStateFlow(false)
    @Volatile var trustUiOpen = false
    @Volatile private var uiAvailable = false
    @Volatile var onPlay: ((PlayRequest, RemoteStatus) -> Unit)? = null
    @Volatile var onCommand: ((CommandRequest) -> Unit)? = null
    @Volatile var onRevoke: ((String) -> Unit)? = null
    private var engine: ApplicationEngine? = null
    private var discovery: RemoteDiscovery? = null
    private var trust: RemoteTrustStore? = null
    private var receiverId = ""
    lateinit var session: RemoteSession; private set
    private val seen = mutableMapOf<String, Long>()
    val blocksBrowserInput: Boolean get() = trustUiOpen || pending.value != null

    @Synchronized fun start(context: Context) {
        if (engine != null) return
        val folder = File(context.noBackupFilesDir, "phone-control").apply { mkdirs() }
        val idFile = File(folder, "id")
        receiverId = if (idFile.exists()) idFile.readText() else UUID.randomUUID().toString().also { idFile.writeText(it) }
        val trustFile = File(folder, "trusted.json")
        trust = RemoteTrustStore({ if (trustFile.exists()) trustFile.readText() else "" }, { value ->
            val tmp = File(folder, "trusted.tmp"); tmp.writeText(value); check(tmp.renameTo(trustFile))
        })
        phones.value = trust!!.phones()
        session = RemoteSession(receiverId)
        val identity = RemoteTls.load(context)
        val environment = applicationEngineEnvironment {
            sslConnector(identity.store, RemoteTls.ALIAS, { identity.password.toCharArray() }, { identity.password.toCharArray() }) {
                host = "0.0.0.0"; port = 0
            }
            module { routes() }
        }
        val server = embeddedServer(Netty, environment).start(wait = false)
        engine = server
        val port = runBlocking { server.resolvedConnectors().first().port }
        discovery = RemoteDiscovery(context).also { it.register(receiverId, "Selene-TV ${Build.MODEL}".take(60), port) }
    }
    fun setUiAvailable(value: Boolean) {
        uiAvailable = value
        if (!value) { pending.value?.let { trust?.decline(it.pairId) }; pending.value = null }
    }
    fun refreshPair() { pending.value = trust?.current() }
    fun approve(pairId: String) { runCatching { trust?.approve(pairId) }; phones.value = trust?.phones() ?: emptyList(); refreshPair() }
    fun decline(pairId: String) { trust?.decline(pairId); refreshPair() }
    fun revoke(clientId: String) {
        trust?.revoke(clientId); phones.value = trust?.phones() ?: emptyList()
        synchronized(seen) { seen.remove(clientId) }
        onRevoke?.invoke(clientId)
        refreshPair()
    }
    fun stop() { discovery?.close(); discovery = null; engine?.stop(500, 1000); engine = null }

    private suspend inline fun <reified T> ApplicationCall.body(): T {
        val bytes = receiveChannel().readRemaining(262145).readBytes()
        requireRemote(bytes.size <= 262144, "payload_too_large")
        requireRemote(request.contentType().withoutParameters() == ContentType.Application.Json)
        return try { json.decodeFromString(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { throw RemoteFailure("invalid_request") }
    }
    private suspend inline fun <reified T> ApplicationCall.reply(value: T) = respondText(json.encodeToString(value), ContentType.Application.Json)
    private fun ApplicationCall.phone(): TrustedPhone {
        val token = request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ") ?: ""
        return trust?.authenticate(token) ?: throw RemoteFailure("unauthorized")
    }
    private suspend fun ApplicationCall.guarded(action: suspend () -> Unit) {
        try { action() } catch (e: RemoteFailure) {
            respondText(json.encodeToString(ReceiverError(e.code)), ContentType.Application.Json, if (e.code == "unauthorized") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest)
        } catch (_: Exception) { respondText(json.encodeToString(ReceiverError("request_failed")), ContentType.Application.Json, HttpStatusCode.BadRequest) }
    }
    private fun Application.routes() {
        routing {
            route("/v1") {
                get("/info") { call.reply(ReceiverInfo(tvId = receiverId, name = "Selene-TV ${Build.MODEL}")) }
                post("/pair") { call.guarded {
                    requireRemote(uiAvailable, "tv_not_ready")
                    val p = call.body<PairRequest>()
                    val result = trust!!.request(p.clientId, p.name, p.requestSecret)
                    refreshPair(); call.reply(result)
                } }
                post("/pair/result") { call.guarded {
                    val p = call.body<PairPoll>()
                    call.reply(trust!!.result(p.pairId, p.requestSecret).copy(tvId = receiverId))
                } }
                post("/connect") { call.guarded {
                    val phone = call.phone()
                    requireRemote(uiAvailable, "tv_not_ready")
                    val now = System.currentTimeMillis()
                    synchronized(seen) {
                        if (now - (seen[phone.id] ?: 0) > 8000) messages.tryEmit("${phone.name} 已连接")
                        seen[phone.id] = now
                    }
                    call.reply(session.snapshot())
                } }
                get("/status") { call.guarded {
                    val phone = call.phone(); synchronized(seen) { seen[phone.id] = System.currentTimeMillis() }
                    call.reply(session.snapshot())
                } }
                post("/play") { call.guarded {
                    val phone = call.phone(); val request = call.body<PlayRequest>()
                    requireRemote(uiAvailable && onPlay != null, "tv_not_ready")
                    val acceptance = withContext(Dispatchers.Main) {
                        session.acceptPlay(phone.id, request).also { if (it.isNew) onPlay?.invoke(request, it.status) }
                    }
                    call.reply(acceptance.status)
                } }
                post("/command") { call.guarded {
                    val phone = call.phone(); val request = call.body<CommandRequest>()
                    requireRemote(uiAvailable && onCommand != null, "tv_not_ready")
                    val acceptance = withContext(Dispatchers.Main) {
                        session.acceptCommand(phone.id, request).also { if (it.isNew) onCommand?.invoke(request) }
                    }
                    call.reply(acceptance.status)
                } }
            }
        }
    }
}
