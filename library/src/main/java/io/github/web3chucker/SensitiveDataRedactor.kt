package io.github.web3chucker

import okhttp3.HttpUrl

/**
 * Centralizes masking of secrets (API keys, auth tokens) before they are ever
 * persisted into [Web3ChuckerRepository], since the repository is a long-lived,
 * in-memory singleton that may be inspected via the debug overlay.
 */
internal object SensitiveDataRedactor {

    private const val REDACTED = "***REDACTED***"

    /** Header names (case-insensitive) that commonly carry secrets. */
    private val SENSITIVE_HEADERS = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "api-key",
        "apikey",
        "x-auth-token",
        "x-access-token",
    )

    /** Query params (case-insensitive) that commonly carry secrets. */
    private val SENSITIVE_QUERY_PARAMS = setOf(
        "key",
        "apikey",
        "api_key",
        "token",
        "access_token",
        "auth",
        "secret",
    )

    /** Path segments longer than this and made only of alnum chars are treated as opaque API keys/project ids. */
    private const val MIN_OPAQUE_SEGMENT_LENGTH = 16
    private val OPAQUE_SEGMENT_REGEX = Regex("^[A-Za-z0-9_-]{$MIN_OPAQUE_SEGMENT_LENGTH,}$")

    fun redactHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.mapValues { (key, value) ->
            if (key.lowercase() in SENSITIVE_HEADERS) REDACTED else value
        }
    }

    /**
     * Masks likely secrets embedded in the URL itself, e.g.
     * `https://mainnet.infura.io/v3/<project-id>` or `?apikey=<secret>`.
     */
    fun redactUrl(url: HttpUrl): String {
        val builder = url.newBuilder()

        val redactedSegments = url.pathSegments.map { segment ->
            if (OPAQUE_SEGMENT_REGEX.matches(segment)) REDACTED else segment
        }
        builder.encodedPath(
            "/" + redactedSegments.joinToString("/")
        )

        url.queryParameterNames.forEach { name ->
            if (name.lowercase() in SENSITIVE_QUERY_PARAMS) {
                // removeAllEncodedQueryParameters + re-add avoids leaking original value while keeping the key visible.
                val count = url.queryParameterValues(name).size
                builder.removeAllQueryParameters(name)
                repeat(count) { builder.addQueryParameter(name, REDACTED) }
            }
        }

        return builder.build().toString()
    }
}
