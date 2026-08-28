package io.github.web3chucker.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.web3chucker.Web3ChuckerInterceptor
import io.github.web3chucker.ui.Web3ChuckerOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Web3ChuckerInterceptor(enabled = true))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SampleAppContent(okHttpClient = okHttpClient)

                        // Attach the Web3Chucker Floating Inspector Overlay
                        Web3ChuckerOverlay()
                    }
                }
            }
        }
    }
}

@Composable
fun SampleAppContent(okHttpClient: OkHttpClient) {
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("Click a button below to trigger simulated JSON-RPC calls.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Web3 Chucker Demo",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "JSON-RPC OkHttp Interceptor & Compose UI Overlay",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    statusMessage = "Sending ERC-20 transfer(to, amount)..."
                    simulateRpcCall(
                        okHttpClient,
                        method = "eth_sendTransaction",
                        params = """[{"from":"0x71C7656EC7ab88b098defB751B7401B5f6d8976F","to":"0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48","data":"0xa9059cbb00000000000000000000000071c7656ec7ab88b098defb751b7401b5f6d8976f000000000000000000000000000000000000000000000005f5e100"}]""",
                        mockResult = """"0x9876543210abcdef9876543210abcdef9876543210abcdef9876543210abcdef""""
                    )
                    statusMessage = "Sent ERC-20 Transfer RPC!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate ERC-20 Transfer (eth_sendTransaction)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    statusMessage = "Sending eth_call (balanceOf)..."
                    simulateRpcCall(
                        okHttpClient,
                        method = "eth_call",
                        params = """[{"to":"0x6B175474E89094C44Da98b954EedeAC495271d0F","data":"0x70a0823100000000000000000000000071c7656ec7ab88b098defb751b7401b5f6d8976f"}, "latest"]""",
                        mockResult = """"0x00000000000000000000000000000000000000000000003635c9adc5dea00000""""
                    )
                    statusMessage = "Sent eth_call Query!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate Query (eth_call)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    statusMessage = "Triggering Execution Revert..."
                    simulateRpcCall(
                        okHttpClient,
                        method = "eth_sendTransaction",
                        params = """[{"to":"0x1234567890123456789012345678901234567890","data":"0x095ea7b3"}]""",
                        mockError = """{"code": -32000, "message": "execution reverted: ERC20: transfer amount exceeds balance"}"""
                    )
                    statusMessage = "Triggered EVM Revert Error!"
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate Reverted Transaction")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = statusMessage,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun simulateRpcCall(
    client: OkHttpClient,
    method: String,
    params: String,
    mockResult: String? = null,
    mockError: String? = null
) {
    val jsonPayload = if (mockError != null) {
        """{"jsonrpc":"2.0","id":1,"error":$mockError}"""
    } else {
        """{"jsonrpc":"2.0","id":1,"result":${mockResult ?: """"0x1""""}}"""
    }

    val requestBodyJson = """{"jsonrpc":"2.0","method":"$method","params":$params,"id":1}"""

    // Using httpbin mock endpoint to test OkHttp Interceptor pipeline
    val request = Request.Builder()
        .url("https://httpbin.org/post")
        .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
        .build()

    try {
        client.newCall(request).execute().close()
    } catch (e: Exception) {
        // Handled by Interceptor
    }
}
