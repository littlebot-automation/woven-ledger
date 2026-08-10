package com.wovenledger.app.data.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Room half of the session: a single row, read once at launch and rewritten on
 * sign-in or sign-out.
 *
 * This is the only class in the auth package that knows a database exists, which is
 * what keeps [SessionManager] and the interceptor testable on a plain JVM.
 */
@Singleton
class RoomTokenStorage @Inject constructor(
    private val dao: AuthTokenDao,
) : TokenStorage {

    override suspend fun load(): String? = dao.find()?.token

    override suspend fun save(token: String) =
        dao.insert(AuthToken(token = token, issuedAt = System.currentTimeMillis()))

    override suspend fun clear() = dao.clear()
}
