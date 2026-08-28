package io.github.web3chucker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.web3chucker.Web3ChuckerRepository
import io.github.web3chucker.model.DecodedCall
import io.github.web3chucker.model.RpcStatus
import io.github.web3chucker.model.Web3RpcTransaction

/**
 * Stateful Container Composable listening to repository state.
 */
@Composable
fun Web3ChuckerOverlay(
    modifier: Modifier = Modifier
) {
    val transactions by Web3ChuckerRepository.transactions.collectAsState()

    Web3ChuckerOverlayContent(
        transactions = transactions,
        onClearAll = { Web3ChuckerRepository.clearAll() },
        modifier = modifier
    )
}

/**
 * Stateless Content Composable adhering to Safaricom Compose Previews & Clean Arch standards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Web3ChuckerOverlayContent(
    transactions: List<Web3RpcTransaction>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTx by remember { mutableStateOf<Web3RpcTransaction?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val errorCount = remember(transactions) {
        transactions.count { it.status == RpcStatus.ERROR || it.status == RpcStatus.REVERTED }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Floating Badge Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { isExpanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🌐 Web3",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Surface(
                    shape = CircleShape,
                    color = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "${transactions.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Full Screen Inspector Modal Sheet
        if (isExpanded) {
            ModalBottomSheet(
                onDismissRequest = { isExpanded = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Web3 RPC Inspector",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            TextButton(onClick = onClearAll) {
                                Text("Clear All")
                            }
                            TextButton(onClick = { isExpanded = false }) {
                                Text("Close")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search Filter
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter by method, selector, or contract...") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredList = remember(transactions, searchQuery) {
                        if (searchQuery.isEmpty()) transactions
                        else transactions.filter {
                            it.method.contains(searchQuery, ignoreCase = true) ||
                                    it.decodedCall?.humanReadableSummary?.contains(searchQuery, ignoreCase = true) == true ||
                                    it.requestUrl.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No RPC transactions logged yet.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList, key = { it.id }) { tx ->
                                RpcTransactionCard(tx = tx, onClick = { selectedTx = tx })
                            }
                        }
                    }
                }
            }
        }

        // Detail View Dialog
        selectedTx?.let { tx ->
            RpcDetailDialog(tx = tx, onDismiss = { selectedTx = null })
        }
    }
}

@Composable
private fun RpcTransactionCard(
    tx: Web3RpcTransaction,
    onClick: () -> Unit
) {
    val statusColor = when (tx.status) {
        RpcStatus.SUCCESS -> Color(0xFF4CAF50)
        RpcStatus.REVERTED -> Color(0xFFFF9800)
        RpcStatus.ERROR -> Color(0xFFF44336)
        RpcStatus.PENDING -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tx.method,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor
                ) {
                    Text(
                        text = tx.status.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            tx.decodedCall?.humanReadableSummary?.let { summary ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${tx.durationMs} ms",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    text = tx.requestUrl.takeLast(35),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun RpcDetailDialog(
    tx: Web3RpcTransaction,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = {
                val fullLog = "Method: ${tx.method}\nURL: ${tx.requestUrl}\nParams: ${tx.rawParamsJson}\nResponse: ${tx.rawResponseJson}"
                clipboardManager.setText(AnnotatedString(fullLog))
            }) {
                Text("Copy Raw Log")
            }
        },
        title = { Text(text = "RPC: ${tx.method}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                Text(text = "Status: ${tx.status.name}", fontWeight = FontWeight.Bold)
                tx.errorMessage?.let { Text(text = "Error: $it", color = Color.Red, fontSize = 12.sp) }
                tx.revertReason?.let { Text(text = "Revert Reason: $it", color = Color(0xFFFF9800), fontSize = 12.sp) }

                Spacer(modifier = Modifier.height(8.dp))

                tx.decodedCall?.let { decoded ->
                    Text(text = "Decoded Summary:", fontWeight = FontWeight.Bold)
                    Text(
                        text = decoded.humanReadableSummary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(text = "Raw Parameters:", fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = tx.rawParamsJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Raw Response:", fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = tx.rawResponseJson ?: "(Pending...)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    )
}

// ============================================================================
// PREVIEWS (Following sfc-android-compose-previews standards)
// ============================================================================

@PreviewLightDark
@Composable
private fun Web3ChuckerOverlayContent_Populated_Preview() {
    MaterialTheme {
        Surface {
            Web3ChuckerOverlayContent(
                transactions = listOf(
                    Web3RpcTransaction(
                        method = "eth_sendTransaction",
                        requestUrl = "https://mainnet.infura.io/v3/app-key",
                        rawParamsJson = """[{"to":"0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"}]""",
                        decodedCall = DecodedCall(
                            functionName = "transfer",
                            selectorHex = "0xa9059cbb",
                            decodedParams = mapOf("to" to "0x71C...", "amount" to "100000000"),
                            humanReadableSummary = "transfer(to: 0x71C..., amount: 100 USDC)"
                        ),
                        durationMs = 124,
                        status = RpcStatus.SUCCESS
                    ),
                    Web3RpcTransaction(
                        method = "eth_call",
                        requestUrl = "https://mainnet.infura.io/v3/app-key",
                        rawParamsJson = "[]",
                        durationMs = 85,
                        status = RpcStatus.REVERTED,
                        revertReason = "ERC20: transfer amount exceeds balance"
                    )
                ),
                onClearAll = {}
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun Web3ChuckerOverlayContent_Empty_Preview() {
    MaterialTheme {
        Surface {
            Web3ChuckerOverlayContent(
                transactions = emptyList(),
                onClearAll = {}
            )
        }
    }
}
