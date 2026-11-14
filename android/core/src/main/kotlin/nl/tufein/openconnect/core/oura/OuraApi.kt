package nl.tufein.openconnect.core.oura

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import nl.tufein.openconnect.core.RetryAfter
import nl.tufein.openconnect.core.RetryPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/** [headers] has lower-case names; it is empty where the answer's headers are not known (a test double). */
class OuraResponse(val status: Int, val body: String, val headers: Map<String, String> = emptyMap())

/** The one network seam of the Oura link: tests answer from memory, the app uses [UrlConnectionOuraHttp]. */
fun interface OuraHttp {
    /** [form] is the body of a POST (application/x-www-form-urlencoded), null for a GET. */
    @Throws(IOException::class)
    fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse
}

class UrlConnectionOuraHttp(private val maxBytes: Int = 20 * 1024 * 1024) : OuraHttp {
    override fun send(method: String, url: String, headers: Map<String, String>, form: String?, timeoutMs: Int): OuraResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (form != null) {
                val bytes = form.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val out = ByteArrayOutputStream()
            stream?.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw IOException("Oura answered with more than $maxBytes bytes")
                    out.write(buffer, 0, n)
                }
            }
            val headers = connection.headerFields.filterKeys { it != null }.entries.associate { (k, v) -> k.lowercase() to v.last() }
            return OuraResponse(status, out.toString("UTF-8"), headers)
        } finally {
            connection.disconnect()
        }
    }
}

class OuraException(
    val kind: Kind,
    message: String? = null,
    /** What Oura asked for with `Retry-After` on a 429, in seconds, when it did. */
    val retryAfterS: Long? = null,
) : Exception(message ?: kind.name) {
    enum class Kind {
        /** No application (client id and secret) or no login yet. */
        NOT_CONNECTED,

        /** Oura refuses the login: sign in again. */
        LOGIN_EXPIRED,

        /** 403: no Oura Membership, or a kind of data that was not allowed at login. */
        FORBIDDEN,
        RATE_LIMITED,
        NETWORK,
        SERVER,

        /** The address pasted back after the login is not one of ours, or carries an error. */
        BAD_REDIRECT,
    }
}

data class OuraTokens(val access: String, val refresh: String?, val expiresAt: Instant)

/** What is kept between visits. The app keeps the secret and the tokens encrypted. */
interface OuraStore {
    val canStoreSecrets: Boolean
    var clientId: String?
    var clientSecret: String?
    var redirectUri: String
    var tokens: OuraTokens?

    /** The PKCE verifier and the state of a login that was started and not finished yet ("verifier state"). */
    var pendingLogin: String?
}

/**
 * Oura's OAuth2 login (authorization code with PKCE). Oura switched personal access tokens off in December 2025, so
 * the owner registers an application of their own at cloud.ouraring.com/oauth/applications and enters its client id
 * and secret in the app. Nothing here was tried against the real Oura service.
 */
class OuraAuth(private val http: OuraHttp, private val store: OuraStore, private val now: () -> Instant = Instant::now) {
    companion object {
        const val AUTHORIZE_URL = "https://cloud.ouraring.com/oauth/authorize"
        const val TOKEN_URL = "https://api.ouraring.com/oauth/token"
        const val DEFAULT_REDIRECT = "openconnect://oura"

        /** Only what the sync reads: day summaries, sleep, workouts and blood oxygen. */
        const val SCOPES = "daily workout spo2 personal"
        const val USER_AGENT = "OpenConnect/1.0"
        private const val TIMEOUT_MS = 30_000
        private val JSON = Json { ignoreUnknownKeys = true }

        fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

        fun challenge(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        /** The query (and fragment) parameters of an address, decoded. */
        fun params(url: String): Map<String, String> {
            val query = url.substringAfter('?', "").substringBefore('#') + "&" + url.substringAfter('#', "")
            return query.split('&').filter { it.contains('=') }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
        }
    }

    private val random = SecureRandom()

    private fun token(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    val hasClient: Boolean get() = !store.clientId.isNullOrBlank() && !store.clientSecret.isNullOrBlank()
    val connected: Boolean get() = store.tokens != null

    /** Starts a login: remembers a fresh verifier and state, and gives the address to open in the browser. */
    fun begin(): String {
        val id = store.clientId?.takeIf { it.isNotBlank() } ?: throw OuraException(OuraException.Kind.NOT_CONNECTED)
        val verifier = token(48)
        val state = token(16)
        store.pendingLogin = "$verifier $state"
        return AUTHORIZE_URL + "?response_type=code&client_id=" + enc(id) + "&redirect_uri=" + enc(store.redirectUri) +
            "&scope=" + enc(SCOPES) + "&state=" + enc(state) + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256"
    }

    /** Finishes a login with the address Oura sent the browser back to (opened by the app, or pasted by hand). */
    fun finish(redirect: String) {
        val pending = store.pendingLogin?.split(' ') ?: throw OuraException(OuraException.Kind.BAD_REDIRECT, "no login was started")
        val got = params(redirect.trim())
        got["error"]?.let { throw OuraException(OuraException.Kind.BAD_REDIRECT, it) }
        val code = got["code"] ?: throw OuraException(OuraException.Kind.BAD_REDIRECT, "no code in the address")
        if (got["state"] != pending[1]) throw OuraException(OuraException.Kind.BAD_REDIRECT, "the address belongs to another login")
        val form = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(store.redirectUri) +
            "&code_verifier=" + enc(pending[0]) + client()
        store.tokens = request(form)
        store.pendingLogin = null
    }

    /** A token that is good for at least another minute; refreshes it (and stores the new pair) when needed. */
    fun accessToken(forceRefresh: Boolean = false): String {
        val tokens = store.tokens ?: throw OuraException(OuraException.Kind.NOT_CONNECTED)
        if (!forceRefresh && tokens.expiresAt.isAfter(now().plusSeconds(60))) return tokens.access
        val refresh = tokens.refresh ?: run {
            store.tokens = null
            throw OuraException(OuraException.Kind.LOGIN_EXPIRED)
        }
        // Oura's refresh tokens are single-use: the answer carries the next one, which replaces this one.
        val fresh = try {
            request("grant_type=refresh_token&refresh_token=" + enc(refresh) + client())
        } catch (e: OuraException) {
            if (e.kind == OuraException.Kind.LOGIN_EXPIRED) store.tokens = null
            throw e
        }
        store.tokens = fresh
        return fresh.access
    }

    fun disconnect() {
        store.tokens = null
        store.pendingLogin = null
    }

    private fun client(): String {
        val id = store.clientId ?: throw OuraException(OuraException.Kind.NOT_CONNECTED)
        val secret = store.clientSecret ?: throw OuraException(OuraException.Kind.NOT_CONNECTED)
        return "&client_id=" + enc(id) + "&client_secret=" + enc(secret)
    }

    private fun request(form: String): OuraTokens {
        val response = try {
            http.send("POST", TOKEN_URL, mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json"), form, TIMEOUT_MS)
        } catch (e: IOException) {
            throw OuraException(OuraException.Kind.NETWORK, e.message)
        }
        when (response.status) {
            200 -> Unit
            // Only a refusal of the login itself ends it: invalid_grant is a 400 and invalid_client a 401 (RFC 6749, 5.2). A busy
            // Oura (429, 5xx) or any other answer says nothing about the refresh token, and throwing it away would make the
            // person log in again for something that passes by itself.
            400, 401 -> throw OuraException(OuraException.Kind.LOGIN_EXPIRED, "Oura answered ${response.status}")
            429 -> throw OuraException(OuraException.Kind.RATE_LIMITED, "Oura answered 429", RetryAfter.seconds(response.headers["retry-after"], now()))
            else -> throw OuraException(OuraException.Kind.SERVER, "Oura answered ${response.status}")
        }
        val json = try {
            JSON.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            throw OuraException(OuraException.Kind.SERVER, "Oura's answer could not be read")
        }
        val access = json.string("access_token") ?: throw OuraException(OuraException.Kind.SERVER, "no access token in Oura's answer")
        val seconds = (json["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 3600L
        return OuraTokens(access, json.string("refresh_token"), now().plusSeconds(seconds))
    }
}

internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toDoubleOrNull()
internal fun JsonObject.int(name: String): Int? = number(name)?.let { Math.round(it).toInt() }

/** Reads Oura's v2 collections with the login of [OuraAuth], following `next_token` to the last page. */
class OuraClient(
    private val http: OuraHttp,
    private val auth: OuraAuth,
    /** What to do about a dropped connection, a 429 or a 5xx before reporting it. The app asks for [RetryPolicy.standard]. */
    private val retry: RetryPolicy = RetryPolicy.NONE,
    private val now: () -> Instant = Instant::now,
) {
    private companion object {
        const val BASE = "https://api.ouraring.com/v2/usercollection/"
        const val TIMEOUT_MS = 30_000
        const val MAX_PAGES = 20
        val JSON = Json { ignoreUnknownKeys = true }
    }

    /** All documents of [collection] between two days (both included), for instance "daily_activity". */
    fun documents(collection: String, start: String, end: String): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        var next: String? = null
        for (page in 0 until MAX_PAGES) {
            val url = BASE + collection + "?start_date=" + start + "&end_date=" + end + (next?.let { "&next_token=" + OuraAuth.enc(it) } ?: "")
            val json = get(url)
            (json["data"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(out::add) }
            next = json.string("next_token") ?: break
        }
        return out
    }

    private fun get(url: String): JsonObject {
        var response = call(url, auth.accessToken())
        // A token Oura no longer takes although it has not run out by the clock: get a new one, once.
        if (response.status == 401) response = call(url, auth.accessToken(forceRefresh = true))
        when (response.status) {
            200 -> Unit
            401 -> throw OuraException(OuraException.Kind.LOGIN_EXPIRED)
            403 -> throw OuraException(OuraException.Kind.FORBIDDEN)
            429 -> throw OuraException(OuraException.Kind.RATE_LIMITED, retryAfterS = RetryAfter.seconds(response.headers["retry-after"], now()))
            else -> throw OuraException(OuraException.Kind.SERVER, "Oura answered ${response.status}")
        }
        return try {
            JSON.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            throw OuraException(OuraException.Kind.SERVER, "Oura's answer could not be read")
        }
    }

    private fun call(url: String, token: String): OuraResponse = try {
        val headers = mapOf("Authorization" to "Bearer $token", "User-Agent" to OuraAuth.USER_AGENT, "Accept" to "application/json")
        retry.call(now, { http.send("GET", url, headers, null, TIMEOUT_MS) }, { it.status }, { it.headers })
    } catch (e: IOException) {
        throw OuraException(OuraException.Kind.NETWORK, e.message)
    }
}
