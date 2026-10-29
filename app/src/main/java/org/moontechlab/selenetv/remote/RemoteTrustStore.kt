package org.moontechlab.selenetv.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

@Serializable data class TrustedPhone(val id: String, val name: String, val token: String)
@Serializable data class PairResult(val pairId: String, val state: String, val expiresIn: Int = 60, val token: String? = null, val tvId: String? = null)
data class PendingPair(val pairId: String, val clientId: String, val name: String, val secret: String, val expiresAt: Long, var state: String = "pending", var token: String? = null)

class RemoteTrustStore(private val read: () -> String, private val write: (String) -> Unit, private val now: () -> Long = System::currentTimeMillis) {
    private val trusted = (read().takeIf { it.isNotEmpty() }?.let { Json.decodeFromString<List<TrustedPhone>>(it) } ?: emptyList()).associateBy { it.id }.toMutableMap()
    private var pending: PendingPair? = null
    private val completed = LinkedHashMap<String, PendingPair>()
    private val attempts = ArrayDeque<Long>()
    @Synchronized fun phones(): List<TrustedPhone> = trusted.values.toList()
    @Synchronized fun current(): PendingPair? { expire(); return pending?.takeIf { it.state == "pending" } }
    private fun expire() { (completed.values + listOfNotNull(pending)).forEach { if (now() >= it.expiresAt) { it.state = "expired"; it.token = null } } }
    private fun persist() = write(Json.encodeToString(trusted.values.toList()))
    @Synchronized fun request(clientId: String, name: String, secret: String): PairResult {
        requireRemote(clientId.length in 1..128 && name.length in 1..64 && secret.length in 32..128)
        expire()
        pending?.let { if (it.state == "pending") {
            requireRemote(it.clientId == clientId && same(it.secret, secret), "pairing_busy")
            return PairResult(it.pairId, it.state)
        } }
        while (attempts.isNotEmpty() && now() - attempts.first() >= 60000) attempts.removeFirst()
        requireRemote(attempts.size < 3, "rate_limited")
        attempts.addLast(now())
        pending?.let { completed[it.pairId] = it }
        if (completed.size > 16) completed.remove(completed.keys.first())
        val p = PendingPair(UUID.randomUUID().toString(), clientId, name.filter { !it.isISOControl() }.take(64), secret, now() + 60000)
        pending = p
        return PairResult(p.pairId, "pending")
    }
    @Synchronized fun result(pairId: String, secret: String): PairResult {
        expire(); val p = pending?.takeIf { it.pairId == pairId } ?: completed[pairId] ?: throw RemoteFailure("pairing_expired")
        requireRemote(p.pairId == pairId && same(p.secret, secret), "pairing_expired")
        return PairResult(pairId, p.state, token = p.token)
    }
    @Synchronized fun approve(pairId: String) {
        expire(); val p = pending ?: throw RemoteFailure("pairing_expired")
        requireRemote(p.pairId == pairId && p.state == "pending", "pairing_expired")
        requireRemote(trusted.size < 32 || trusted.containsKey(p.clientId), "trusted_limit")
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        trusted[p.clientId] = TrustedPhone(p.clientId, p.name, token)
        persist(); p.token = token; p.state = "allowed"
    }
    @Synchronized fun decline(pairId: String) { pending?.takeIf { it.pairId == pairId && it.state == "pending" }?.state = "declined" }
    @Synchronized fun authenticate(token: String): TrustedPhone = trusted.values.firstOrNull { token.isNotEmpty() && same(it.token, token) } ?: throw RemoteFailure("unauthorized")
    @Synchronized fun revoke(clientId: String) { trusted.remove(clientId); (completed.values + listOfNotNull(pending)).filter { it.clientId == clientId }.forEach { it.token = null; it.state = "declined" }; persist() }
    private fun same(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
