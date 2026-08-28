package io.github.web3chucker

import io.github.web3chucker.model.Web3RpcTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object Web3ChuckerRepository {

    private const val MAX_TRANSACTIONS = 200

    private val _transactions = MutableStateFlow<List<Web3RpcTransaction>>(emptyList())
    val transactions: StateFlow<List<Web3RpcTransaction>> = _transactions.asStateFlow()

    fun addTransaction(tx: Web3RpcTransaction) {
        _transactions.update { current ->
            (listOf(tx) + current).take(MAX_TRANSACTIONS)
        }
    }

    fun updateTransaction(id: String, updateBlock: (Web3RpcTransaction) -> Web3RpcTransaction) {
        _transactions.update { current ->
            current.map { tx ->
                if (tx.id == id) updateBlock(tx) else tx
            }
        }
    }

    fun clearAll() {
        _transactions.value = emptyList()
    }
}
