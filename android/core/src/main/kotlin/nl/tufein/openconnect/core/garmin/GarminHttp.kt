package nl.tufein.openconnect.core.garmin

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [headers] has lower-case names; it is empty where the answer's headers are not known (a test double). */
class GarminResponse(val status: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val text: String get() = body.toString(Charsets.UTF_8)
}

/** The one network seam of the Garmin link: tests answer from memory, the app uses [UrlConnectionGarminHttp]. */
fun interface GarminHttp {
    @Throws(IOException::class)
    fun send(method: String, url: String, headers: Map<String, String>, timeoutMs: Int): GarminResponse
}

/** Plain `HttpURLConnection`, no body in either direction of a request, and a cap on how much it reads back. */
class UrlConnectionGarminHttp(private val maxBytes: Int = 30 * 1024 * 1024) : GarminHttp {
    override fun send(method: String, url: String, headers: Map<String, String>, timeoutMs: Int): GarminResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (method == "POST") {
                // The token request has no body; say so explicitly (Content-Length: 0) as some gateways insist.
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val headers = connection.headerFields.filterKeys { it != null }.entries.associate { (k, v) -> k.lowercase() to v.last() }
            return GarminResponse(status, stream?.use { readCapped(it) } ?: ByteArray(0), headers)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > maxBytes) throw IOException("The response is larger than $maxBytes bytes")
        }
        return out.toByteArray()
    }
}
