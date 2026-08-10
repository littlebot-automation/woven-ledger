package com.wovenledger.app.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session as everything else sees it: a token for the interceptor, and a state for
 * the UI to gate on.
 *
 * No Android here at all, which is the point of keeping Room behind [TokenStorage].
 */
class SessionManagerTest {

    private val storage = FakeTokenStorage()
    private val session = SessionManager(storage)

    @Test
    fun `nothing is claimed before storage has been read`() {
        // Guessing SIGNED_OUT here would flash a password prompt at a signed-in user
        // on every launch.
        assertEquals(AuthState.UNKNOWN, session.state.value)
        assertNull(session.token())
    }

    @Test
    fun `a stored token comes back as a signed-in session`() = runTest {
        storage.token = "jwt-from-last-week"

        assertTrue(session.restore())
        assertEquals(AuthState.SIGNED_IN, session.state.value)
        assertEquals("jwt-from-last-week", session.token())
    }

    @Test
    fun `an empty store restores to signed out`() = runTest {
        assertFalse(session.restore())
        assertEquals(AuthState.SIGNED_OUT, session.state.value)
        assertNull(session.token())
    }

    @Test
    fun `signing in holds the token and writes it down`() = runTest {
        session.signIn("jwt-fresh")

        assertEquals("jwt-fresh", session.token())
        assertEquals("jwt-fresh", storage.token)
        assertEquals(AuthState.SIGNED_IN, session.state.value)
    }

    @Test
    fun `signing out forgets the token both places`() = runTest {
        session.signIn("jwt-fresh")

        session.signOut()

        assertNull(session.token())
        assertNull(storage.token)
        assertEquals(AuthState.SIGNED_OUT, session.state.value)
    }

    @Test
    fun `a rejected token is dropped at once, without waiting for the disk`() = runTest {
        session.signIn("jwt-expired")

        // The interceptor calls this mid-request on an OkHttp thread, so the parts
        // that matter — what the next request sends, and which screen is shown — are
        // set synchronously. The stored copy is wiped on the way out.
        session.onUnauthorized()

        assertNull(session.token())
        assertEquals(AuthState.SIGNED_OUT, session.state.value)
    }
}

private class FakeTokenStorage : TokenStorage {
    var token: String? = null

    override suspend fun load(): String? = token

    override suspend fun save(token: String) {
        this.token = token
    }

    override suspend fun clear() {
        token = null
    }
}
