package com.wovenledger.app.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app may talk to `/api`, and whether it yet knows. */
enum class AuthState {
    /** Room has not been read yet. Neither screen should be shown during this. */
    UNKNOWN,

    SIGNED_IN,

    SIGNED_OUT,
}

/**
 * What the auth interceptor is allowed to know about the session.
 *
 * A plain getter and a plain callback, and nothing else: the interceptor runs on an
 * OkHttp thread with no coroutine and no Context, and reaching into Room from there
 * would make it untestable as well as blocking. [SessionManager] is the only real
 * implementation; tests hand the interceptor a mock.
 */
interface AuthTokens {
    /** The bearer token, or null when signed out. */
    fun token(): String?

    /** The server rejected the token on a call that was not the sign-in itself. */
    fun onUnauthorized()
}

/**
 * The signed-in session, held in memory and mirrored to Room.
 *
 * Split from [TokenStorage] for the same reason [com.wovenledger.app.data.outbox.OutboxManager]
 * is split from its uploader: everything that touches the framework sits on one side of
 * an interface, and the decisions sit on the other where a JVM test can reach them.
 * Nothing in this class imports Android.
 *
 * The token is read on every request, so it is held in a field rather than fetched:
 * a Room read per HTTP call would be a disk hit on an OkHttp thread.
 */
@Singleton
class SessionManager @Inject constructor(
    private val storage: TokenStorage,
) : AuthTokens {

    private val _state = MutableStateFlow(AuthState.UNKNOWN)

    /** What the UI gates on: the login screen or the app, and neither until [restore]. */
    val state: StateFlow<AuthState> = _state.asStateFlow()

    @Volatile
    private var current: String? = null

    /**
     * Wiping the stored token when a 401 arrives cannot suspend — the interceptor is
     * mid-request on an OkHttp thread — so the write is launched and the in-memory
     * state, which is what every later request and the UI both read, is set at once.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun token(): String? = current

    override fun onUnauthorized() {
        current = null
        _state.value = AuthState.SIGNED_OUT
        scope.launch { storage.clear() }
    }

    /**
     * Loads whatever the last run left behind.
     *
     * @return true if the app can carry on without asking for a password. Callers use
     * this to decide whether the cold-start sync is worth attempting at all: every
     * `/api` call would 401 without a token, and a 401 storm on launch would walk the
     * outbox and mark real work as refused.
     */
    suspend fun restore(): Boolean {
        val stored = storage.load()
        current = stored
        _state.value = if (stored == null) AuthState.SIGNED_OUT else AuthState.SIGNED_IN

        return stored != null
    }

    suspend fun signIn(token: String) {
        current = token
        storage.save(token)
        _state.value = AuthState.SIGNED_IN
    }

    suspend fun signOut() {
        current = null
        storage.clear()
        _state.value = AuthState.SIGNED_OUT
    }
}

/** Where the token survives a restart. Room on the phone, a map in a test. */
interface TokenStorage {
    suspend fun load(): String?

    suspend fun save(token: String)

    suspend fun clear()
}
