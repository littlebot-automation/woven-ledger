package com.wovenledger.app.data.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Where the token is exchanged for a password. The one path that must go out bare. */
const val LOGIN_PATH = "api/login_check"

private const val UNAUTHORIZED = 401

/**
 * Puts the bearer token on every request, and notices when the server stops accepting it.
 *
 * Two 401s mean opposite things and the difference decides what the user sees. On
 * [LOGIN_PATH] it means the password was wrong, and it belongs on the sign-in form —
 * clearing a session there would be clearing one that was never established. Anywhere
 * else it means the token has expired or been revoked, and since there are no refresh
 * tokens the only honest response is to end the session and ask again. The response is
 * still returned rather than swallowed, so Retrofit raises its usual HttpException and
 * the caller — a form, or the outbox drain — can decide for itself what to do about it.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokens: AuthTokens,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val signingIn = original.url.encodedPath.trimStart('/') == LOGIN_PATH
        val token = tokens.token()

        val request = if (signingIn || token == null) {
            original
        } else {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        }

        val response = chain.proceed(request)

        if (response.code == UNAUTHORIZED && !signingIn) {
            tokens.onUnauthorized()
        }

        return response
    }
}
