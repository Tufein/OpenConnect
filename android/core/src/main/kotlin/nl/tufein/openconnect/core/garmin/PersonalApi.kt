package nl.tufein.openconnect.core.garmin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import nl.tufein.openconnect.core.RetryAfter
import nl.tufein.openconnect.core.RetryPolicy
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64

/** Why a call to Garmin failed, in terms the user can act on. */
class GarminApiException(
    val kind: Kind,
    message: String,
    val status: Int? = null,
    cause: Throwable? = null,
    /** What Garmin asked for with `Retry-After` on a 429 or 5xx, in seconds, when it did. */
    val retryAfterS: Long? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** No key has been saved yet. */
        NO_KEY,

        /** Garmin refused the key: wrong, deleted or expired. */
        KEY_REJECTED,

        /** The key is fine but this one data type is not available to it. */
        ACCESS_DENIED,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER,
        NETWORK,
        BAD_RESPONSE,
    }

    /** A failure that makes every further call pointless, so a sync stops instead of trying the rest. */
    val stopsSync: Boolean get() = kind in STOPPING

    private companion object {
        val STOPPING = setOf(Kind.NO_KEY, Kind.KEY_REJECTED, Kind.RATE_LIMITED, Kind.SERVER, Kind.NETWORK)
    }
}

/**
 * A client of Garmin's Personal API: the owner's own key, exchanged for a 24-hour bearer token, then read-only
 * GET requests for their own data. The key never leaves the device except in the token request to Garmin.
 *
 * Garmin documents the key exchange as `POST {base}/personalapitoken-service/personal-api/personal-api-key/token`
 * with `Authorization: ApiKey <key>`, answered with the access token (a JWT) as the body; data requests then use
 * `Authorization: Bearer <token>`. There are no refresh tokens: a new token is requested from the key.
 */
class PersonalApiClient(
    private val http: GarminHttp,
    private val apiKey: () -> String?,
    private val clock: () -> Instant = { Instant.now() },
    private val baseUrl: String = BASE_URL,
    private val timeoutMs: Int = 30_000,
    /** What to do about a dropped connection, a 429 or a 5xx before reporting it. The app asks for [RetryPolicy.standard]. */
    private val retry: RetryPolicy = RetryPolicy.NONE,
) {
    companion object {
        const val BASE_URL = "https://personal.connectapi.garmin.com"
        const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"

        /** A token is renewed when less than this is left, so a long sync does not run into its end. */
        private const val RENEW_BEFORE_EXPIRY_S = 10 * 60L

        /** Used when the token carries no readable expiry. */
        private const val ASSUMED_LIFETIME_S = 12 * 60 * 60L

        private val JSON = Json { ignoreUnknownKeys = true }

        /** What a bearer token looks like: one run of URL-safe characters (a JWT is three, joined by dots). */
        private val TOKEN_SHAPE = Regex("[A-Za-z0-9._~+/=-]{16,}")
    }

    private class Token(val value: String, val expiresAt: Instant, val forKey: String)

    private var token: Token? = null

    /**
     * Asks Garmin for a token without reading any data: the cheapest way to learn whether the saved key is accepted.
     * Throws [GarminApiException] (KEY_REJECTED, NETWORK, ...) when it is not.
     */
    fun verify() {
        bearer()
    }

    /** Forget the token: the next call asks Garmin for a new one. */
    @Synchronized
    fun invalidate() {
        token = null
    }

    @Synchronized
    private fun bearer(): String {
        val key = apiKey()?.trim().orEmpty()
        if (key.isEmpty()) throw GarminApiException(GarminApiException.Kind.NO_KEY, "No Garmin API key has been saved")
        token?.let {
            if (it.forKey == key && it.expiresAt.epochSecond - clock().epochSecond > RENEW_BEFORE_EXPIRY_S) return it.value
        }
        val fresh = exchange(key)
        token = fresh
        return fresh.value
    }

    private fun exchange(key: String): Token {
        val response = send("POST", baseUrl + TOKEN_PATH, mapOf("Authorization" to "ApiKey $key", "Accept" to "*/*"))
        when {
            response.status == 401 || response.status == 403 -> throw GarminApiException(
                GarminApiException.Kind.KEY_REJECTED,
                "Garmin did not accept the API key (HTTP ${response.status}). It may be wrong, deleted or expired.",
                response.status,
            )
            response.status != 200 -> throw failure(response, "token request")
        }
        val value = tokenFromBody(response.text)
            ?: throw GarminApiException(GarminApiException.Kind.BAD_RESPONSE, "Garmin's answer to the token request held no token")
        val expiresAt = jwtExpiry(value) ?: clock().plusSeconds(ASSUMED_LIFETIME_S)
        return Token(value, expiresAt, key)
    }

    /** The documented answer is the bare token; a JSON object with the token in it is accepted as well. */
    private fun tokenFromBody(body: String): String? {
        val text = body.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("{")) {
            val obj = try {
                JSON.parseToJsonElement(text) as? JsonObject
            } catch (_: Exception) {
                null
            }
            return listOf("access_token", "accessToken", "token", "bearer_token")
                .firstNotNullOfOrNull { (obj?.get(it) as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotBlank() }
        }
        return text.trim('"').takeIf { TOKEN_SHAPE.matches(it) }
    }

    private fun jwtExpiry(token: String): Instant? = try {
        val payload = token.split('.').getOrNull(1) ?: return null
        val json = JSON.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)) as JsonObject
        (json["exp"] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochSecond)
    } catch (_: Exception) {
        null
    }

    private fun send(method: String, url: String, headers: Map<String, String>): GarminResponse = try {
        retry.call(clock, { http.send(method, url, headers, timeoutMs) }, { it.status }, { it.headers })
    } catch (e: IOException) {
        throw GarminApiException(GarminApiException.Kind.NETWORK, "No connection to Garmin: ${e.message ?: e.javaClass.simpleName}", cause = e)
    }

    private fun failure(response: GarminResponse, what: String): GarminApiException {
        val status = response.status
        val wait = RetryAfter.seconds(response.headers["retry-after"], clock())
        return when (status) {
            401 -> GarminApiException(GarminApiException.Kind.KEY_REJECTED, "Garmin refused the $what (HTTP 401)", status)
            403 -> GarminApiException(GarminApiException.Kind.ACCESS_DENIED, "Not allowed to read this ($what, HTTP 403)", status)
            404 -> GarminApiException(GarminApiException.Kind.NOT_FOUND, "Not found ($what, HTTP 404)", status)
            429 -> GarminApiException(GarminApiException.Kind.RATE_LIMITED, "Garmin is limiting requests (HTTP 429), try again later", status, retryAfterS = wait)
            in 500..599 -> GarminApiException(GarminApiException.Kind.SERVER, "Garmin had a problem (HTTP $status)", status, retryAfterS = wait)
            else -> GarminApiException(GarminApiException.Kind.BAD_RESPONSE, "Unexpected answer from Garmin ($what, HTTP $status)", status)
        }
    }

    /** A GET with the bearer token. A 401 usually means the token ran out, so it is renewed once and the call repeated. */
    private fun get(path: String, query: List<Pair<String, String>>, accept: String): GarminResponse {
        val url = baseUrl + path + queryString(query)
        var renewed = false
        while (true) {
            val response = send("GET", url, mapOf("Authorization" to "Bearer ${bearer()}", "Accept" to accept))
            if (response.status == 401 && !renewed) {
                renewed = true
                invalidate()
                continue
            }
            if (response.status != 200) throw failure(response, path)
            return response
        }
    }

    /** The JSON of [path], or null when Garmin answers with nothing. */
    fun getJson(path: String, query: List<Pair<String, String>> = emptyList()): JsonElement? {
        val body = get(path, query, "application/json").text
        if (body.isBlank()) return null
        val parsed = try {
            JSON.parseToJsonElement(body)
        } catch (e: Exception) {
            throw GarminApiException(GarminApiException.Kind.BAD_RESPONSE, "The answer for $path is not JSON", cause = e)
        }
        return when (parsed) {
            is JsonNull -> null
            is JsonObject, is JsonArray -> parsed
            // A bare word or number is not an answer Garmin gives (the tree reader would accept <html> as one).
            else -> throw GarminApiException(GarminApiException.Kind.BAD_RESPONSE, "The answer for $path is not a JSON object or list")
        }
    }

    /** The raw bytes of [path] (an activity file comes as a ZIP). */
    fun download(path: String): ByteArray {
        val body = get(path, emptyList(), "application/zip, application/octet-stream, */*").body
        if (body.isEmpty()) throw GarminApiException(GarminApiException.Kind.BAD_RESPONSE, "The download for $path was empty")
        return body
    }

    private fun queryString(query: List<Pair<String, String>>): String =
        if (query.isEmpty()) "" else query.joinToString("&", prefix = "?") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")
}
