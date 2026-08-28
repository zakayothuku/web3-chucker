package io.github.web3chucker

import io.github.web3chucker.model.DecodedCall
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

class RpcTransactionDecoder(
    private val registry: AbiSignatureRegistry = DefaultAbiSignatureRegistry
) {

    fun decode(method: String, rawParamsJson: String): DecodedCall? {
        return try {
            when (method) {
                "eth_call", "eth_sendTransaction", "eth_estimateGas" -> decodeEvmCall(rawParamsJson)
                "eth_sendRawTransaction" -> decodeRawTx(rawParamsJson)
                "eth_getBalance" -> decodeGetBalance(rawParamsJson)
                "solana_sendTransaction", "sendTransaction" -> decodeSolanaTx(rawParamsJson)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeEvmCall(rawParamsJson: String): DecodedCall? {
        val array = JSONArray(rawParamsJson)
        if (array.length() == 0) return null
        val txObj = array.optJSONObject(0) ?: return null

        val dataHex = txObj.optString("data", txObj.optString("input", ""))
        val toAddress = txObj.optString("to", "Unknown Contract")
        val valueHex = txObj.optString("value", "0x0")

        if (dataHex.length >= 10) {
            val selector = dataHex.substring(0, 10).lowercase()
            val sig = registry.findSignature(selector)

            if (sig != null) {
                val paramsMap = mutableMapOf<String, String>()
                paramsMap["Target Contract"] = toAddress
                paramsMap["ETH Value"] = parseHexToDecimal(valueHex)

                val payloadHex = dataHex.substring(10)
                if (payloadHex.length >= 64 && sig.params.isNotEmpty()) {
                    val p1 = sig.params[0]
                    paramsMap[p1.name] = formatParam(payloadHex.substring(0, 64), p1.type)
                }
                if (payloadHex.length >= 128 && sig.params.size >= 2) {
                    val p2 = sig.params[1]
                    paramsMap[p2.name] = formatParam(payloadHex.substring(64, 128), p2.type)
                }

                val summary = "${sig.name}(${paramsMap.entries.joinToString { "${it.key}: ${it.value}" }})"
                return DecodedCall(
                    functionName = sig.name,
                    selectorHex = selector,
                    decodedParams = paramsMap,
                    humanReadableSummary = summary
                )
            } else {
                return DecodedCall(
                    functionName = "Unknown Method ($selector)",
                    selectorHex = selector,
                    decodedParams = mapOf("To" to toAddress, "Data Hex" to dataHex),
                    humanReadableSummary = "Call $selector to $toAddress"
                )
            }
        } else if (valueHex != "0x0" && valueHex.isNotEmpty()) {
            val ethVal = parseHexToDecimal(valueHex)
            return DecodedCall(
                functionName = "Native ETH Transfer",
                selectorHex = "0x",
                decodedParams = mapOf("To" to toAddress, "Amount (ETH)" to ethVal),
                humanReadableSummary = "Transfer $ethVal ETH to $toAddress"
            )
        }
        return null
    }

    private fun decodeRawTx(rawParamsJson: String): DecodedCall? {
        val array = JSONArray(rawParamsJson)
        val rawTxHex = if (array.length() > 0) array.getString(0) else ""
        return DecodedCall(
            functionName = "eth_sendRawTransaction",
            selectorHex = "0x",
            decodedParams = mapOf("Raw Tx Length" to "${rawTxHex.length / 2} bytes"),
            humanReadableSummary = "Broadcast Raw Signed Tx (${rawTxHex.take(16)}...)"
        )
    }

    private fun decodeGetBalance(rawParamsJson: String): DecodedCall? {
        val array = JSONArray(rawParamsJson)
        val address = if (array.length() > 0) array.getString(0) else ""
        val block = if (array.length() > 1) array.getString(1) else "latest"
        return DecodedCall(
            functionName = "eth_getBalance",
            selectorHex = "0x",
            decodedParams = mapOf("Address" to address, "Block" to block),
            humanReadableSummary = "Check balance of $address"
        )
    }

    private fun decodeSolanaTx(rawParamsJson: String): DecodedCall? {
        val array = JSONArray(rawParamsJson)
        val txB64 = if (array.length() > 0) array.getString(0) else ""
        return DecodedCall(
            functionName = "solana_sendTransaction",
            selectorHex = "0x",
            decodedParams = mapOf("Base64 Length" to "${txB64.length} chars"),
            humanReadableSummary = "Solana Transaction (${txB64.take(12)}...)"
        )
    }

    private fun formatParam(hexChunk: String, paramType: String): String {
        return when (paramType) {
            "address" -> "0x" + hexChunk.takeLast(40)
            "uint256" -> parseHexToDecimal("0x$hexChunk")
            else -> "0x$hexChunk"
        }
    }

    companion object {
        private val defaultInstance = RpcTransactionDecoder()

        fun decode(method: String, rawParamsJson: String): DecodedCall? =
            defaultInstance.decode(method, rawParamsJson)

        fun parseHexToDecimal(hex: String): String {
            return try {
                val cleanHex = hex.removePrefix("0x").trimStart('0')
                if (cleanHex.isEmpty()) "0"
                else BigInteger(cleanHex, 16).toString()
            } catch (e: Exception) {
                hex
            }
        }
    }
}
