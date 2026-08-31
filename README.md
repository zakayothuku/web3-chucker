# 🌐 web3-chucker

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zakayothuku/web3-chucker.svg?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.zakayothuku/web3-chucker)
[![Android CI](https://github.com/zakayothuku/web3-chucker/actions/workflows/ci.yml/badge.svg)](https://github.com/zakayothuku/web3-chucker/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-MinSDK%2024-green.svg)](https://developer.android.com)

> **OkHttp JSON-RPC Interceptor & Jetpack Compose Debug UI Overlay for Android Web3 Applications.**

<p align="center">
  <img src="docs/web3_chucker_preview.jpg" alt="web3-chucker UI Preview" width="360" />
</p>

---

## ✨ Features

- 🔍 **Automatic EVM 4-Byte Selector Decoding**: Decodes hex input data (`0xa9059cbb`, `0x095ea7b3`) into human-readable signatures like `transfer(to: 0x71C..., amount: 100 USDC)`.
- ⚡ **Zero-Dependency OkHttp Interceptor**: Simply add `Web3ChuckerInterceptor()` to your `OkHttpClient`.
- 📱 **Jetpack Compose Overlay Drawer**: Floating badge showing live request counts and an expandable modal drawer for full RPC parameter/response inspection.
- 🚨 **Revert & Error Highlighting**: Automatically detects EVM transaction reverts, error codes (`-32000`), and network timeouts with visual status indicators.
- 📋 **1-Tap Log Copy**: Easily copy raw JSON payloads, formatted headers, and decoded params for bug reporting.

---

## 📦 Quickstart

### 1. Add Dependency

Add Maven Central to your `settings.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}
```

Add the dependency to your `:app` or `:feature` `build.gradle.kts`:

```kotlin
dependencies {
    // Debug builds: full JSON-RPC inspector & overlay UI
    debugImplementation("io.github.zakayothuku:web3-chucker:1.0.0")
    
    // Release builds: zero-overhead no-op artifact
    releaseImplementation("io.github.zakayothuku:web3-chucker-noop:1.0.0")
}
```

### 2. Attach OkHttp Interceptor

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(Web3ChuckerInterceptor(enabled = BuildConfig.DEBUG))
    .build()
```

### 3. Attach Compose Overlay UI

Add `Web3ChuckerOverlay()` inside your top-level Jetpack Compose `Surface` or `Scaffold`:

```kotlin
@Composable
fun MainScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        YourAppContent()

        // Attach web3-chucker debug floating badge & drawer
        Web3ChuckerOverlay()
    }
}
```

---

## 🛠️ Architecture Overview

```
web3-chucker/
├── library/
│   ├── src/main/java/io/github/web3chucker/
│   │   ├── Web3ChuckerInterceptor.kt    # OkHttp Interceptor catching JSON-RPC POST requests
│   │   ├── RpcTransactionDecoder.kt     # EVM 4-byte selector & hex parameter parser
│   │   ├── Web3ChuckerRepository.kt     # Circular buffer StateFlow store
│   │   ├── model/
│   │   │   └── Web3RpcTransaction.kt    # Transaction & status data models
│   │   └── ui/
│   │       └── Web3ChuckerOverlay.kt    # Jetpack Compose UI Badge & Modal Inspector
└── sample/                              # Demo app showcasing live RPC interception
```

---

## 🧪 Testing

Run unit tests for the EVM function selector and hex decoder:

```bash
./gradlew :library:test
```

Build the sample app:

```bash
./gradlew :sample:assembleDebug
```

### 🌩️ Chaos / Resilience Testing

`ChaosProxyTest` (`library/src/test/java/io/github/web3chucker/ChaosProxyTest.kt`) points
`Web3ChuckerInterceptor` at a [MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver)
deliberately misbehaving — dropped connections, stalls/timeouts, truncated bodies, garbage
payloads, oversized responses, and JSON-RPC/HTTP error codes — to prove the interceptor never
crashes the host app's request pipeline and to document exactly what gets logged for each
failure mode. Run it in isolation with:

```bash
./gradlew :library:testDebugUnitTest --tests "io.github.web3chucker.ChaosProxyTest"
```

Recorded outcomes from the latest run (11/11 passed):

| # | Scenario | Expected | Actual |
|---|----------|----------|--------|
| 1 | Connection dropped before any response (`DISCONNECT_AT_START`) | `IOException` propagates to caller; transaction recorded as `ERROR` | `IOException` propagated; `status=ERROR` |
| 2 | Connection reset mid response body (`DISCONNECT_DURING_RESPONSE_BODY`) | `IOException` propagates to caller; transaction recorded as `ERROR` (not stuck at `PENDING`) | `IOException` propagated; `status=ERROR` |
| 3 | Server never responds, client times out (`NO_RESPONSE`, 2s read timeout) | Timeout raised as `IOException`; transaction recorded as `ERROR` | `IOException` propagated; `status=ERROR` |
| 4 | Slow/throttled response that completes within the timeout | Recorded as `SUCCESS` with a realistic non-zero duration | `status=SUCCESS`; `durationMs=2250` |
| 5 | Truncated/malformed JSON body with HTTP 200 | *(documents current behavior)* | `status=SUCCESS` — malformed payloads silently fall back to `SUCCESS`; raw bytes are still captured in `rawResponseJson` for manual inspection |
| 6 | Empty response body with HTTP 200 | *(documents current behavior)* | `status=SUCCESS` — an empty body also falls back to `SUCCESS` |
| 7 | Non-JSON (HTML) body returned with HTTP 200 (e.g. gateway/WAF error page) | *(documents current behavior)* | `status=SUCCESS` — non-JSON payloads also fall back to `SUCCESS`, which can be misleading when debugging RPC gateway issues |
| 8 | HTTP 429 rate limited | Recorded as `ERROR` with `responseCode=429` | `status=ERROR`; `responseCode=429` |
| 9 | HTTP 503 service unavailable | Recorded as `ERROR` with `responseCode=503` | `status=ERROR`; `responseCode=503` |
| 10 | Oversized (~2MB) response payload | Fully read, parsed, and logged as `SUCCESS` with no truncation | `status=SUCCESS`; `rawResponseJson.length=2000038` |
| 11 | JSON-RPC application error (`-32601 Method not found`, no revert) | Recorded as `ERROR` (not `REVERTED`) with the JSON-RPC error message captured | `status=ERROR`; `errorMessage=Method not found (Code: -32601)` |

**Note:** scenarios 5–7 document a known gap rather than a guarantee — any response body that
fails JSON parsing currently falls back to `RpcStatus.SUCCESS` instead of `ERROR`. The raw bytes
are still preserved in `rawResponseJson`, so this is safe for debugging (nothing is lost), but it
means a garbled/HTML/empty 200 response won't visually stand out as a failure in the overlay UI.
Tracked as a follow-up improvement.

---

## 📄 License & Author

Developed & maintained by **Zakayo Thuku** ([@zakayothuku](https://github.com/zakayothuku)).

```
MIT License - Copyright (c) 2026 Zakayo Thuku
```
