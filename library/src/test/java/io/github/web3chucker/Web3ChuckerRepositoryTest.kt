package io.github.web3chucker

import io.github.web3chucker.model.RpcStatus
import io.github.web3chucker.model.Web3RpcTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Web3ChuckerRepositoryTest {

    @Before
    @After
    fun setup() {
        Web3ChuckerRepository.clearAll()
    }

    @Test
    fun `test addTransaction adds to list in reverse chronological order`() = runBlocking {
        val tx1 = Web3RpcTransaction(id = "tx-1", requestUrl = "https://rpc.mainnet.org", method = "eth_call", rawParamsJson = "[]")
        val tx2 = Web3RpcTransaction(id = "tx-2", requestUrl = "https://rpc.mainnet.org", method = "eth_sendTransaction", rawParamsJson = "[]")

        Web3ChuckerRepository.addTransaction(tx1)
        Web3ChuckerRepository.addTransaction(tx2)

        val list = Web3ChuckerRepository.transactions.first()
        assertEquals(2, list.size)
        assertEquals("tx-2", list[0].id)
        assertEquals("tx-1", list[1].id)
    }

    @Test
    fun `test updateTransaction updates matching transaction status`() = runBlocking {
        val tx1 = Web3RpcTransaction(id = "tx-100", requestUrl = "https://rpc.mainnet.org", method = "eth_call", rawParamsJson = "[]", status = RpcStatus.PENDING)
        Web3ChuckerRepository.addTransaction(tx1)

        Web3ChuckerRepository.updateTransaction("tx-100") { current ->
            current.copy(status = RpcStatus.SUCCESS, durationMs = 150)
        }

        val list = Web3ChuckerRepository.transactions.first()
        assertEquals(1, list.size)
        assertEquals(RpcStatus.SUCCESS, list[0].status)
        assertEquals(150L, list[0].durationMs)
    }

    @Test
    fun `test clearAll empties repository`() = runBlocking {
        Web3ChuckerRepository.addTransaction(Web3RpcTransaction(requestUrl = "https://rpc.org", method = "eth_call", rawParamsJson = "[]"))
        Web3ChuckerRepository.clearAll()

        val list = Web3ChuckerRepository.transactions.first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `test circular buffer capacity limit of 200 items`() = runBlocking {
        for (i in 1..250) {
            Web3ChuckerRepository.addTransaction(
                Web3RpcTransaction(id = "tx-$i", requestUrl = "https://rpc.org", method = "eth_call", rawParamsJson = "[]")
            )
        }

        val list = Web3ChuckerRepository.transactions.first()
        assertEquals(200, list.size)
        assertEquals("tx-250", list.first().id) // newest item
        assertEquals("tx-51", list.last().id)   // 50 oldest items evicted
    }
}
