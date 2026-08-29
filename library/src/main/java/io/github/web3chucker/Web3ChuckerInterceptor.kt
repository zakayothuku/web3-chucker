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

class Web3ChuckerInterceptor(
    private val enabled: Boolean = true
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!enabled || !isJsonRpcRequest(request)) {
            return chain.proceed(request)
        }

        val requestBodyString = readRequestBody(request)
        val rpcPayload = parseJsonRpcRequest(requestBodyString)

        val method = rpcPayload?.first ?: "HTTP ${request.method}"
        val paramsJson = rpcPayload?.second ?: "[]"
        val rpcId = rpcPayload?.third ?: "1"

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

        val duration = System.currentTimeMillis() - startTime
        val responseBody = response.body
        val responseBodyString = responseBody?.string() ?: ""

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
        val newBody = responseBodyString.toResponseBody(responseBody?.contentType())
        return response.newBuilder().body(newBody).build()
    }

    private fun isJsonRpcRequest(request: Request): Boolean {
        val contentType = request.body?.contentType()?.toString() ?: ""
        return request.method == "POST" && (contentType.contains("json") || request.url.encodedPath.contains("rpc"))
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
            val method = json.optString("method", "unknown_method")
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
