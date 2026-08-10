package com.wovenledger.app.data.auth

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What the interceptor puts on a request, and what it makes of the answer.
 *
 * The chain is mocked rather than served by a real socket — MockWebServer is not a
 * dependency here, and nothing in this class needs one: the questions are which header
 * went out and whether the session was ended, both of which are visible from the chain.
 */
class AuthInterceptorTest {

    private val tokens: AuthTokens = mock()
    private val interceptor = AuthInterceptor(tokens)

    @Test
    fun `a signed-in request carries the bearer token`() {
        whenever(tokens.token()).thenReturn("jwt-abc")

        val sent = proceed(request("https://invoice.littlebotautomation.com/api/parties"))

        assertEquals("Bearer jwt-abc", sent.header("Authorization"))
    }

    @Test
    fun `the sign-in request goes out bare`() {
        // A stale token must not ride along on the one call whose whole purpose is to
        // replace it — the server would be asked to authenticate two credentials at
        // once, and the 401 that came back would be ambiguous.
        whenever(tokens.token()).thenReturn("jwt-expired")

        val sent = proceed(request("https://invoice.littlebotautomation.com/api/login_check"))

        assertNull(sent.header("Authorization"))
    }

    @Test
    fun `nothing is attached when there is no token`() {
        whenever(tokens.token()).thenReturn(null)

        val sent = proceed(request("https://invoice.littlebotautomation.com/api/parties"))

        assertNull(sent.header("Authorization"))
    }

    @Test
    fun `a 401 from anywhere but the sign-in ends the session`() {
        whenever(tokens.token()).thenReturn("jwt-expired")

        proceed(request("https://invoice.littlebotautomation.com/api/sales-invoices"), code = 401)

        // There are no refresh tokens, so an expired JWT has one honest ending.
        verify(tokens).onUnauthorized()
    }

    @Test
    fun `a 401 from the sign-in is a wrong password, not an expired session`() {
        whenever(tokens.token()).thenReturn(null)

        proceed(request("https://invoice.littlebotautomation.com/api/login_check"), code = 401)

        // Ending a session that was never established would leave the login form
        // reporting a signed-out state as though something had been lost.
        verify(tokens, never()).onUnauthorized()
    }

    @Test
    fun `an accepted request leaves the session alone`() {
        whenever(tokens.token()).thenReturn("jwt-abc")

        proceed(request("https://invoice.littlebotautomation.com/api/parties"), code = 200)

        verify(tokens, never()).onUnauthorized()
    }

    // --------------------------------------------------------------------- helpers

    /** @return the request the interceptor actually handed on */
    private fun proceed(request: Request, code: Int = 200): Request {
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenAnswer { call ->
            answer(call.getArgument(0), code)
        }

        interceptor.intercept(chain)

        val sent = argumentCaptor<Request>()
        verify(chain).proceed(sent.capture())

        return sent.firstValue
    }

    private fun request(url: String) = Request.Builder()
        .url(url)
        .post("{}".toRequestBody("application/json".toMediaType()))
        .build()

    private fun answer(request: Request, code: Int) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 401) "Unauthorized" else "OK")
        .body("{}".toResponseBody("application/json".toMediaType()))
        .build()
}
