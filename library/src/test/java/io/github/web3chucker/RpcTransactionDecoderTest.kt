package io.github.web3chucker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RpcTransactionDecoderTest {

    @Test
    fun `test EVM transfer function selector decoding`() {
        // 0xa9059cbb = transfer(address,uint256)
        val toAddrParam = "00000000000000000000000071c7656ec7ab88b098defb751b7401b5f6d8976f"
        val amountParam = "000000000000000000000000000000000000000000000005f5e100" // 100,000,000 in hex (100 USDC with 6 decimals)
        val dataHex = "0xa9059cbb$toAddrParam$amountParam"

        val rawParamsJson = """
            [{"to":"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48","data":"$dataHex"}]
        """.trimIndent()

        val decoded = RpcTransactionDecoder.decode("eth_sendTransaction", rawParamsJson)

        assertNotNull(decoded)
        assertEquals("transfer", decoded?.functionName)
        assertEquals("0xa9059cbb", decoded?.selectorHex)
        assertEquals("0x71c7656ec7ab88b098defb751b7401b5f6d8976f", decoded?.decodedParams?.get("to"))
        assertEquals("100000000", decoded?.decodedParams?.get("amount"))
    }

    @Test
    fun `test EVM approve function selector decoding`() {
        // 0x095ea7b3 = approve(address,uint256)
        val spenderParam = "000000000000000000000000def1c0ded6601234567890abcdef1234567890ab"
        val amountParam = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        val dataHex = "0x095ea7b3$spenderParam$amountParam"

        val rawParamsJson = """
            [{"to":"0x6b175474e89094c44da98b954eedeac495271d0f","data":"$dataHex"}]
        """.trimIndent()

        val decoded = RpcTransactionDecoder.decode("eth_call", rawParamsJson)

        assertNotNull(decoded)
        assertEquals("approve", decoded?.functionName)
        assertEquals("0x095ea7b3", decoded?.selectorHex)
    }

    @Test
    fun `test hex to decimal conversion`() {
        assertEquals("255", RpcTransactionDecoder.parseHexToDecimal("0xff"))
        assertEquals("1000", RpcTransactionDecoder.parseHexToDecimal("0x3e8"))
        assertEquals("0", RpcTransactionDecoder.parseHexToDecimal("0x0"))
    }
}
