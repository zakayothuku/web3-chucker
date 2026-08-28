package io.github.web3chucker.model

import java.util.UUID

enum class RpcStatus {
    PENDING,
    SUCCESS,
    REVERTED,
    ERROR
}

data class DecodedCall(
    val functionName: String,
    val selectorHex: String,
    val decodedParams: Map<String, String>,
    val humanReadableSummary: String
)

data class Web3RpcTransaction(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val requestUrl: String,
    val jsonRpcVersion: String = "2.0",
    val rpcId: String = "1",
    val method: String,
    val rawParamsJson: String,
    val decodedCall: DecodedCall? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val rawResponseJson: String? = null,
    val responseCode: Int = 0,
    val durationMs: Long = 0,
    val status: RpcStatus = RpcStatus.PENDING,
    val errorMessage: String? = null,
    val revertReason: String? = null
)
