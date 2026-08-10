package com.wovenledger.app.data.api

import com.google.gson.annotations.SerializedName
import com.wovenledger.app.data.auth.LOGIN_PATH
import retrofit2.http.Body
import retrofit2.http.POST

/** What LexikJWT's `/api/login_check` expects: a username and a password, nothing else. */
data class LoginRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String,
)

/** `{"token": "<jwt>"}`. There is no refresh token, by design. */
data class LoginResponse(
    @SerializedName("token")
    val token: String,
)

/**
 * The one endpoint that is reached without a token.
 *
 * A 401 from here is a wrong password and belongs on the sign-in form; the auth
 * interceptor leaves this path alone precisely so the two cannot be confused.
 */
interface AuthApiService {
    @POST(LOGIN_PATH)
    suspend fun login(@Body body: LoginRequest): LoginResponse
}
