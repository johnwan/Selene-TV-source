package org.moontechlab.selenetv.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteTrustTest {
    private var now = 1000L
    private var saved = ""
    private fun store() = RemoteTrustStore({ saved }, { saved = it }, { now })
    @Test fun approvalPersistsAndRevocationInvalidatesToken() {
        val s = store()
        val p = s.request("phone1", "Phone", "s".repeat(32))
        s.approve(p.pairId)
        val token = s.result(p.pairId, "s".repeat(32)).token!!
        assertEquals("phone1", store().authenticate(token).id)
        s.revoke("phone1")
        assertThrows(RemoteFailure::class.java) { store().authenticate(token) }
    }
    @Test fun onlyOriginatingRequestCanCollectAndDeclineCreatesNoTrust() {
        val s = store(); val p = s.request("phone1", "Phone", "s".repeat(32))
        assertThrows(RemoteFailure::class.java) { s.result(p.pairId, "wrong") }
        s.decline(p.pairId)
        assertEquals("declined", s.result(p.pairId, "s".repeat(32)).state)
        assertTrue(s.phones().isEmpty())
    }
    @Test fun expiresAndOnlyOnePromptCanExist() {
        val s = store(); val p = s.request("phone1", "Phone", "s".repeat(32))
        assertThrows(RemoteFailure::class.java) { s.request("phone2", "Phone2", "t".repeat(32)) }
        now += 60000
        assertEquals("expired", s.result(p.pairId, "s".repeat(32)).state)
        assertThrows(RemoteFailure::class.java) { s.approve(p.pairId) }
        assertTrue(s.phones().isEmpty())
    }
    @Test fun deviceNameOrIdAloneNeverAuthenticates() {
        val s = store(); s.request("phone1", "Phone", "s".repeat(32))
        assertThrows(RemoteFailure::class.java) { s.authenticate("phone1") }
        assertThrows(RemoteFailure::class.java) { s.authenticate("") }
    }
    @Test fun anotherPairRequestCannotStealAnApprovedResult() {
        val s = store()
        val first = s.request("phone1", "Phone1", "s".repeat(32)); s.approve(first.pairId)
        s.request("phone2", "Phone2", "t".repeat(32))
        assertEquals("allowed", s.result(first.pairId, "s".repeat(32)).state)
        assertNotNull(s.result(first.pairId, "s".repeat(32)).token)
    }
    @Test fun requestAuthenticatedBeforeRevokeCannotExecuteAfterBodyArrives() {
        val s = store(); val p = s.request("phone1", "Phone", "s".repeat(32)); s.approve(p.pairId)
        val token = s.result(p.pairId, "s".repeat(32)).token!!
        s.authenticate(token) // Header checked before the slow request body.
        s.revoke("phone1")
        var executed = false
        assertThrows(RemoteFailure::class.java) { s.authorized(token) { executed = true } }
        assertFalse(executed)
    }
    @Test fun interruptedFirstApprovalRecoversWithoutAnotherDialog() {
        val s = store(); val first = s.request("phone1", "Phone", "s".repeat(32)); s.approve(first.pairId)
        now += 120000 // Phone was backgrounded before collecting its token.
        val restarted = store()
        val recovered = restarted.request("phone1", "Phone", "s".repeat(32))
        assertEquals("allowed", recovered.state)
        assertNotNull(recovered.token)
        assertNull(restarted.current())
        assertThrows(RemoteFailure::class.java) { restarted.authorized("wrong") {} }
    }
}
