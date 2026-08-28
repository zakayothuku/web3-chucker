package io.github.web3chucker

/**
 * Registry interface for matching 4-byte EVM function selectors to human readable ABI function signatures.
 */
interface AbiSignatureRegistry {
    fun findSignature(selectorHex: String): FunctionSignature?
}

data class FunctionParam(val name: String, val type: String)

data class FunctionSignature(
    val name: String,
    val params: List<FunctionParam>
)

object DefaultAbiSignatureRegistry : AbiSignatureRegistry {

    private val selectors = mapOf(
        "0xa9059cbb" to FunctionSignature("transfer", listOf(FunctionParam("to", "address"), FunctionParam("amount", "uint256"))),
        "0x095ea7b3" to FunctionSignature("approve", listOf(FunctionParam("spender", "address"), FunctionParam("amount", "uint256"))),
        "0x23b872dd" to FunctionSignature("transferFrom", listOf(FunctionParam("from", "address"), FunctionParam("to", "address"), FunctionParam("amount", "uint256"))),
        "0x70a08231" to FunctionSignature("balanceOf", listOf(FunctionParam("owner", "address"))),
        "0xdd62ed3e" to FunctionSignature("allowance", listOf(FunctionParam("owner", "address"), FunctionParam("spender", "address"))),
        "0xd0e30db0" to FunctionSignature("deposit", emptyList()),
        "0x2e1a7d4d" to FunctionSignature("withdraw", listOf(FunctionParam("amount", "uint256"))),
        "0x38ed1739" to FunctionSignature("swapExactTokensForTokens", listOf(FunctionParam("amountIn", "uint256"), FunctionParam("amountOutMin", "uint256"), FunctionParam("path", "address[]"), FunctionParam("to", "address"), FunctionParam("deadline", "uint256"))),
        "0x7ff36450" to FunctionSignature("swapExactETHForTokens", listOf(FunctionParam("amountOutMin", "uint256"), FunctionParam("path", "address[]"), FunctionParam("to", "address"), FunctionParam("deadline", "uint256"))),
        "0x18cbafe5" to FunctionSignature("swapExactTokensForETH", listOf(FunctionParam("amountIn", "uint256"), FunctionParam("amountOutMin", "uint256"), FunctionParam("path", "address[]"), FunctionParam("to", "address"), FunctionParam("deadline", "uint256"))),
        "0x42842e0e" to FunctionSignature("safeTransferFrom", listOf(FunctionParam("from", "address"), FunctionParam("to", "address"), FunctionParam("tokenId", "uint256")))
    )

    override fun findSignature(selectorHex: String): FunctionSignature? {
        return selectors[selectorHex.lowercase()]
    }
}
