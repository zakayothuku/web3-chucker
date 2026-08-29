package io.github.web3chucker

import io.github.web3chucker.model.RpcStatus
import io.github.web3chucker.model.Web3RpcTransaction
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * [enabled] defaults to `false` so this interceptor is safe-by-default: it must be
 * explicitly opted into (e.g. `Web3ChuckerInterceptor(enabled = BuildConfig.DEBUG)`)
 * rather than silently logging RPC traffic, headers, and wallet data in production
 * if a consumer forgets to gate it.
 */
class Web3ChuckerInterceptor(
    private val enabled: Boolean = false
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!enabled || !looksLikeJsonRpcCandidate(request)) {
            return chain.proceed(request)
        }

        val requestBodyString = readRequestBody(request)
        val rpcPayload = parseJsonRpcRequest(requestBodyString)

        // The heuristics above are necessarily loose (content-type/path matching); only a
        // request whose body actually declares a JSON-RPC "method" is logged, so unrelated
        // JSON APIs sharing a content-type or an "rpc"-containing path aren't misrepresented.
        if (rpcPayload == null) {
            return chain.proceed(request)
        }

        val (method, paramsJson, rpcId) = rpcPayload

        val decodedCall = RpcTransactionDecoder.decode(method, paramsJson)

        val initialTx = Web3RpcTransaction(
            requestUrl = SensitiveDataRedactor.redactUrl(request.url),
            method = method,
            rawParamsJson = paramsJson,
            rpcId = rpcId,
            decodedCall = decodedCall,
            requestHeaders = SensitiveDataRedactor.redactHeaders(request.headers.toMap()),
            status = RpcStatus.PENDING
        )

        Web3ChuckerRepository.addTransaction(initialTx)
        val startTime = System.currentTimeMillis()

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Web3ChuckerRepository.updateTransaction(initialTx.id) { tx ->
                tx.copy(
                    durationMs = duration,
                    status = RpcStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Network connection failed"
                )
            }
            throw e
        }

        // Reading the body is a second point of failure independent of chain.proceed()
        // succeeding (e.g. the connection can drop mid-stream after headers arrive), so it
        // gets its own try/catch: otherwise a failure here would throw uncaught, leaving the
        // transaction stuck at RpcStatus.PENDING forever with no recorded error.
        val responseBodyString: String
        try {
            responseBodyString = response.body?.string() ?: ""
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Web3ChuckerRepository.updateTransaction(initialTx.id) { tx ->
                tx.copy(
                    responseCode = response.code,
                    durationMs = duration,
                    status = RpcStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Failed to read response body"
                )
            }
            throw e
        }

        val duration = System.currentTimeMillis() - startTime

        val (status, errorMsg, revertReason) = parseJsonRpcResponse(response.code, responseBodyString)

        Web3ChuckerRepository.updateTransaction(initialTx.id) { tx ->
            tx.copy(
                responseCode = response.code,
                responseHeaders = SensitiveDataRedactor.redactHeaders(response.headers.toMap()),
                rawResponseJson = responseBodyString,
                durationMs = duration,
                status = status,
                errorMessage = errorMsg,
                revertReason = revertReason
            )
        }

        // Re-create response body as it can only be consumed once
        val newBody = responseBodyString.toResponseBody(response.body?.contentType())
        return response.newBuilder().body(newBody).build()
    }

    /**
     * Cheap pre-filter to decide whether it's worth reading the body at all. This is
     * intentionally loose (content-type prefix OR a path segment literally named "rpc")
     * — [parseJsonRpcRequest] performs the authoritative check by requiring a "method"
     * field, so false positives here don't result in unrelated traffic being logged.
     */
    private fun looksLikeJsonRpcCandidate(request: Request): Boolean {
        if (request.method != "POST") return false
        val contentType = request.body?.contentType()?.toString()?.lowercase() ?: ""
        val isJsonContentType = contentType.startsWith("application/json") || contentType.endsWith("+json")
        val hasRpcPathSegment = request.url.pathSegments.any { it.equals("rpc", ignoreCase = true) }
        return isJsonContentType || hasRpcPathSegment
    }

    private fun readRequestBody(request: Request): String {
        return try {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body?.writeTo(buffer)
            buffer.readString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseJsonRpcRequest(jsonStr: String): Triple<String, String, String>? {
        return try {
            val json = JSONObject(jsonStr)
            // A JSON-RPC 2.0 request MUST declare "method"; absence means this POST, despite
            // matching the loose candidate heuristics, isn't actually a JSON-RPC call.
            if (!json.has("method")) return null
            val method = json.getString("method")
            val params = json.optJSONArray("params")?.toString()
                ?: json.optJSONObject("params")?.toString()
                ?: "[]"
            val id = json.optString("id", "1")
            Triple(method, params, id)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonRpcResponse(code: Int, jsonStr: String): Triple<RpcStatus, String?, String?> {
        if (code !in 200..299) {
            return Triple(RpcStatus.ERROR, "HTTP Error Status Code $code", null)
        }
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("error")) {
                val errObj = json.getJSONObject("error")
                val errMsg = errObj.optString("message", "JSON-RPC Error")
                val errCode = errObj.optInt("code", 0)

                if (errMsg.contains("revert", ignoreCase = true) || errCode == -32000 || errCode == 3) {
                    Triple(RpcStatus.REVERTED, "Execution Reverted ($errCode)", errMsg)
                } else {
                    Triple(RpcStatus.ERROR, "$errMsg (Code: $errCode)", null)
                }
            } else {
                Triple(RpcStatus.SUCCESS, null, null)
            }
        } catch (e: Exception) {
            Triple(RpcStatus.SUCCESS, null, null)
        }
    }
}
