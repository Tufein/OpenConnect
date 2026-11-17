package nl.tufein.openconnect.core.updates

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The one network seam of the updater: tests answer from memory, the app uses HttpURLConnection. */
fun interface UpdateHttp {
    @Throws(IOException::class)
    fun get(url: String, onProgress: (bytesRead: Int) -> Unit): ByteArray
}

fun UpdateHttp.get(url: String): ByteArray = get(url) {}

/** Plain HTTPS GET with a hard size cap: a broken or hostile server cannot fill the phone's storage. */
class UrlConnectionUpdateHttp(private val maxBytes: Int = 60 * 1024 * 1024) : UpdateHttp {
    override fun get(url: String, onProgress: (bytesRead: Int) -> Unit): ByteArray {
        require(url.startsWith("https://")) { "Only HTTPS update URLs are allowed" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Update download returned HTTP $status")
            val stream = connection.inputStream
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxBytes) throw IOException("Update is larger than ${maxBytes / 1024 / 1024} MB")
                onProgress(out.size())
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
