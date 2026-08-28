package io.github.web3chucker

import io.github.web3chucker.model.RpcStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class Web3ChuckerInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        Web3ChuckerRepository.clearAll()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = OkHttpClient.Builder()
            .addInterceptor(Web3ChuckerInterceptor(enabled = true))
            .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        Web3ChuckerRepository.clearAll()
    }

    @Test
    fun `test interceptor logs successful JSON-RPC request and response`() = runBlocking {
        val mockResponseBody = """{"jsonrpc":"2.0","id":1,"result":"0x123"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponseBody)
        )

        val jsonRequest = """{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}"""
        val request = Request.Builder()
            .url(mockWebServer.url("/rpc"))
            .post(jsonRequest.toRequestBody(okhttp3.MediaType.get("application/json")))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        assertEquals(mockResponseBody, response.body?.string())

        val loggedTxs = Web3ChuckerRepository.transactions.first()
        assertEquals(1, loggedTxs.size)

        val tx = loggedTxs.first()
        assertEquals("eth_blockNumber", tx.method)
        assertEquals(RpcStatus.SUCCESS, tx.status)
        assertEquals(200, tx.responseCode)
        assertEquals(mockResponseBody, tx.rawResponseJson)
    }

    @Test
    fun `test interceptor handles EVM execution revert JSON-RPC error`() = runBlocking {
        val mockErrorResponseBody = """
            {"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"execution reverted: ERC20: transfer amount exceeds balance"}}
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockErrorResponseBody)
        )

        val jsonRequest = """{"jsonrpc":"2.0","method":"eth_sendTransaction","params":[],"id":1}"""
        val request = Request.Builder()
            .url(mockWebServer.url("/rpc"))
            .post(jsonRequest.toRequestBody(okhttp3.MediaType.get("application/json")))
            .build()

        client.newCall(request).execute().close()

        val loggedTxs = Web3ChuckerRepository.transactions.first()
        assertEquals(1, loggedTxs.size)

        val tx = loggedTxs.first()
        assertEquals("eth_sendTransaction", tx.method)
        assertEquals(RpcStatus.REVERTED, tx.status)
        assertNotNull(tx.revertReason)
    }

    @Test
    fun `test interceptor logs HTTP 500 error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val jsonRequest = """{"jsonrpc":"2.0","method":"eth_call","params":[],"id":1}"""
        val request = Request.Builder()
            .url(mockWebServer.url("/rpc"))
            .post(jsonRequest.toRequestBody(okhttp3.MediaType.get("application/json")))
            .build()

        client.newCall(request).execute().close()

        val loggedTxs = Web3ChuckerRepository.transactions.first()
        assertEquals(1, loggedTxs.size)

        val tx = loggedTxs.first()
        assertEquals(RpcStatus.ERROR, tx.status)
        assertEquals(500, tx.responseCode)
    }
}
