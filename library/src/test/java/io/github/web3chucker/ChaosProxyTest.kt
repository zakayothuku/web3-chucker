package io.github.web3chucker

import io.github.web3chucker.model.RpcStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * "Chaos proxy" test suite: instead of only exercising the happy path, each test here
 * points [Web3ChuckerInterceptor] at a [MockWebServer] that is deliberately misbehaving
 * (dropped connections, stalls/timeouts, truncated bodies, garbage payloads, oversized
 * responses, HTTP error codes, etc.) and asserts what actually ends up recorded in
 * [Web3ChuckerRepository].
 *
 * The goal is twofold:
 *  1. Prove the interceptor never crashes the host app's request pipeline outside of
 *     genuinely rethrowing the underlying network exception (which OkHttp callers already
 *     expect to handle).
 *  2. Document precisely what a developer will see logged for each failure mode, since some
 *     of these are surprising (see the notes on each test). [outcomes] accumulates a
 *     human-readable summary of every scenario as the suite runs; [dumpOutcomes] prints it
 *     at the end so it can be captured and pasted into README.md's "Chaos / resilience
 *     testing" section.
 */
class ChaosProxyTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    companion object {
        private val outcomes = mutableListOf<String>()

        @JvmStatic
        @AfterClass
        fun dumpOutcomes() {
            println("\n=== Chaos Proxy Test Outcomes ===")
            outcomes.forEach { println(it) }
            println("=== End Chaos Proxy Test Outcomes ===\n")
        }
    }

    @Before
    fun setup() {
        Web3ChuckerRepository.clearAll()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = OkHttpClient.Builder()
            .addInterceptor(Web3ChuckerInterceptor(enabled = true))
            .callTimeout(3, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        Web3ChuckerRepository.clearAll()
    }

    private fun rpcRequest(method: String = "eth_call", path: String = "/rpc"): Request {
        val jsonRequest = """{"jsonrpc":"2.0","method":"$method","params":[],"id":1}"""
        return Request.Builder()
            .url(mockWebServer.url(path))
            .post(jsonRequest.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun record(scenario: String, expected: String, actual: String) {
        outcomes += "- $scenario -> expected: $expected | actual: $actual"
    }

    @Test
    fun `chaos - connection dropped before any response is sent`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        var thrown: IOException? = null
        try {
            client.newCall(rpcRequest()).execute()
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull("expected an IOException to propagate to the caller", thrown)

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.ERROR, tx.status)
        assertNotNull(tx.errorMessage)

        record(
            "Connection dropped before response (DISCONNECT_AT_START)",
            "IOException propagates to caller; transaction recorded as ERROR",
            "IOException propagated=${thrown != null}; status=${tx.status}"
        )
    }

    @Test
    fun `chaos - connection reset while streaming the response body`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":"0x1234567890""")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        var thrown: IOException? = null
        try {
            client.newCall(rpcRequest()).execute()
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull("expected an IOException while reading a truncated body", thrown)

        // Regression check: reading the response body is a second failure point independent
        // of chain.proceed() succeeding. Before this suite was added, a failure here threw
        // uncaught and left the transaction stuck at PENDING forever with no recorded error.
        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.ERROR, tx.status)
        assertNotNull(tx.errorMessage)

        record(
            "Connection reset mid response body (DISCONNECT_DURING_RESPONSE_BODY)",
            "IOException propagates to caller; transaction recorded as ERROR (not stuck at PENDING)",
            "IOException propagated=${thrown != null}; status=${tx.status}"
        )
    }

    @Test
    fun `chaos - server never responds and the client times out`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        var thrown: IOException? = null
        try {
            client.newCall(rpcRequest()).execute()
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull("expected a timeout IOException", thrown)

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.ERROR, tx.status)

        record(
            "Server never responds (NO_RESPONSE, 2s read timeout)",
            "Client-side timeout raised as IOException; transaction recorded as ERROR",
            "IOException propagated=${thrown != null}; status=${tx.status}"
        )
    }

    @Test
    fun `chaos - slow but eventually complete response is still logged correctly`() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"result":"0xdeadbeef"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
                // Trickle 8 bytes every 200ms; comfortably finishes within the 3s call timeout.
                .throttleBody(8, 200, TimeUnit.MILLISECONDS)
        )

        val response = client.newCall(rpcRequest()).execute()
        assertEquals(200, response.code)
        response.close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.SUCCESS, tx.status)
        assertTrue("expected a measurable non-zero duration for a throttled response", tx.durationMs > 0)

        record(
            "Slow/throttled response that completes within the timeout",
            "Recorded as SUCCESS with a realistic non-zero duration",
            "status=${tx.status}; durationMs=${tx.durationMs}"
        )
    }

    @Test
    fun `chaos - truncated malformed JSON response body`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":"0x1234""") // missing closing braces/quote
        )

        client.newCall(rpcRequest()).execute().close()

        val tx = Web3ChuckerRepository.transactions.first().first()

        // Documents a real gap: parseJsonRpcResponse() falls back to RpcStatus.SUCCESS on any
        // parse failure, so a garbled 200 OK payload is currently indistinguishable from a
        // genuinely successful call in the recorded status (though rawResponseJson still
        // captures the malformed bytes for manual inspection).
        assertEquals(RpcStatus.SUCCESS, tx.status)
        assertNotNull(tx.rawResponseJson)

        record(
            "Truncated/malformed JSON body with HTTP 200",
            "N/A (documenting current behavior)",
            "status=${tx.status} (misleadingly reported as SUCCESS; raw bytes still captured in rawResponseJson for manual inspection)"
        )
    }

    @Test
    fun `chaos - empty response body`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))

        client.newCall(rpcRequest()).execute().close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.SUCCESS, tx.status)
        assertEquals("", tx.rawResponseJson)

        record(
            "Empty response body with HTTP 200",
            "N/A (documenting current behavior)",
            "status=${tx.status} (empty body also falls back to SUCCESS)"
        )
    }

    @Test
    fun `chaos - non-JSON HTML error page returned with HTTP 200`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body>Cloudflare 1xxx error page</body></html>")
        )

        client.newCall(rpcRequest()).execute().close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.SUCCESS, tx.status)

        record(
            "Non-JSON (HTML) body returned with HTTP 200 (e.g. gateway/WAF error page)",
            "N/A (documenting current behavior)",
            "status=${tx.status} (non-JSON payload also falls back to SUCCESS - misleading for debugging RPC gateway issues)"
        )
    }

    @Test
    fun `chaos - HTTP 429 rate limited`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("""{"error":"rate limit exceeded"}""")
        )

        client.newCall(rpcRequest()).execute().close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.ERROR, tx.status)
        assertEquals(429, tx.responseCode)
        assertNotNull(tx.errorMessage)

        record(
            "HTTP 429 rate limited",
            "Recorded as ERROR with responseCode=429",
            "status=${tx.status}; responseCode=${tx.responseCode}"
        )
    }

    @Test
    fun `chaos - HTTP 503 service unavailable`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        client.newCall(rpcRequest()).execute().close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.ERROR, tx.status)
        assertEquals(503, tx.responseCode)

        record(
            "HTTP 503 service unavailable",
            "Recorded as ERROR with responseCode=503",
            "status=${tx.status}; responseCode=${tx.responseCode}"
        )
    }

    @Test
    fun `chaos - oversized response payload is still parsed and logged`() = runBlocking {
        // ~2MB "result" payload - large enough to catch any implicit buffering/size limits.
        val hugeHex = "0x" + "ab".repeat(1_000_000)
        val body = """{"jsonrpc":"2.0","id":1,"result":"$hugeHex"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )

        val response = client.newCall(rpcRequest()).execute()
        assertEquals(200, response.code)
        response.close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.SUCCESS, tx.status)
        assertEquals(body.length, tx.rawResponseJson?.length)

        record(
            "Oversized (~2MB) response payload",
            "Fully read, parsed, and logged as SUCCESS with no truncation",
            "status=${tx.status}; rawResponseJson.length=${tx.rawResponseJson?.length}"
        )
    }

    @Test
    fun `chaos - JSON-RPC error object without an execution-revert message`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""")
        )

        client.newCall(rpcRequest(method = "eth_unknownMethod")).execute().close()

        val tx = Web3ChuckerRepository.transactions.first().first()
        assertEquals(RpcStatus.ERROR, tx.status)
        assertTrue(tx.errorMessage?.contains("Method not found") == true)

        record(
            "JSON-RPC application-level error (-32601 Method not found, no revert)",
            "Recorded as ERROR (not REVERTED) with the JSON-RPC error message captured",
            "status=${tx.status}; errorMessage=${tx.errorMessage}"
        )
    }
}
