package nl.tufein.openconnect.core.whoop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import nl.tufein.openconnect.core.RetryAfter
import nl.tufein.openconnect.core.RetryPolicy
import nl.tufein.openconnect.core.oura.OuraAuth
import nl.tufein.openconnect.core.oura.OuraHttp
import nl.tufein.openconnect.core.oura.OuraResponse
import nl.tufein.openconnect.core.oura.OuraTokens
import nl.tufein.openconnect.core.oura.UrlConnectionOuraHttp
import java.io.IOException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * The one network seam of the WHOOP link. It is the same small seam as the Oura link's (a method, an address, headers and
 * an optional form), so one real implementation serves both and a test answers from memory.
 */
typealias WhoopHttp = OuraHttp
typealias WhoopResponse = OuraResponse
typealias WhoopTokens = OuraTokens

class UrlConnectionWhoopHttp(maxBytes: Int = 20 * 1024 * 1024) : WhoopHttp by UrlConnectionOuraHttp(maxBytes)

class WhoopException(
    val kind: Kind,
    message: String? = null,
    /** What WHOOP asked for on a 429, in seconds, when it did (`Retry-After` or the time until the rate limit window resets). */
    val retryAfterS: Long? = null,
) : Exception(message ?: kind.name) {
    enum class Kind {
        /** No application (client id and secret) or no login yet. */
        NOT_CONNECTED,

        /** WHOOP refuses the login: sign in again. */
        LOGIN_EXPIRED,

        /** 403: a kind of data that was not allowed when the application was made or at login. */
        FORBIDDEN,
        RATE_LIMITED,
        NETWORK,
        SERVER,

        /** The address pasted back after the login is not one of ours, or carries an error. */
        BAD_REDIRECT,
    }
}

/** What is kept between visits. The app keeps the secret and the tokens encrypted. */
interface WhoopStore {
    val canStoreSecrets: Boolean
    var clientId: String?
    var clientSecret: String?
    var redirectUri: String
    var tokens: WhoopTokens?

    /** The state of a login that was started and not finished yet. */
    var pendingLogin: String?
}

/**
 * WHOOP's OAuth2 login (authorization code). The owner registers an application of their own in the WHOOP Developer
 * Dashboard (developer-dashboard.whoop.com) and enters its client id and secret in the app. WHOOP documents a state of at
 * least eight characters and the `offline` scope for a refresh token, and does not document PKCE, so none is sent.
 * Nothing here was tried against the real WHOOP service.
 */
class WhoopAuth(private val http: WhoopHttp, private val store: WhoopStore, private val now: () -> Instant = Instant::now) {
    companion object {
        const val AUTHORIZE_URL = "https://api.prod.whoop.com/oauth/oauth2/auth"
        const val TOKEN_URL = "https://api.prod.whoop.com/oauth/oauth2/token"
        const val DEFAULT_REDIRECT = "openconnect://whoop"
        const val DASHBOARD_URL = "https://developer-dashboard.whoop.com/apps"

        /** Only what the sync reads: the recovery, the cycles (strain), the nights and the workouts, and `offline` for the refresh token. */
        const val SCOPES = "offline read:recovery read:cycles read:workout read:sleep"
        const val USER_AGENT = "OpenConnect/1.0"
        private const val TIMEOUT_MS = 30_000
        private val JSON = Json { ignoreUnknownKeys = true }
    }

    private val random = SecureRandom()

    private fun token(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    val hasClient: Boolean get() = !store.clientId.isNullOrBlank() && !store.clientSecret.isNullOrBlank()
    val connected: Boolean get() = store.tokens != null

    /** Starts a login: remembers a fresh state, and gives the address to open in the browser. */
    fun begin(): String {
        val id = store.clientId?.takeIf { it.isNotBlank() } ?: throw WhoopException(WhoopException.Kind.NOT_CONNECTED)
        val state = token(16)
        store.pendingLogin = state
        return AUTHORIZE_URL + "?response_type=code&client_id=" + OuraAuth.enc(id) + "&redirect_uri=" + OuraAuth.enc(store.redirectUri) +
            "&scope=" + OuraAuth.enc(SCOPES) + "&state=" + OuraAuth.enc(state)
    }

    /** Finishes a login with the address WHOOP sent the browser back to (opened by the app, or pasted by hand). */
    @Synchronized
    fun finish(redirect: String) {
        val state = store.pendingLogin ?: throw WhoopException(WhoopException.Kind.BAD_REDIRECT, "no login was started")
        val got = OuraAuth.params(redirect.trim())
        got["error"]?.let { throw WhoopException(WhoopException.Kind.BAD_REDIRECT, it) }
        val code = got["code"] ?: throw WhoopException(WhoopException.Kind.BAD_REDIRECT, "no code in the address")
        if (got["state"] != state) throw WhoopException(WhoopException.Kind.BAD_REDIRECT, "the address belongs to another login")
        val form = "grant_type=authorization_code&code=" + OuraAuth.enc(code) + "&redirect_uri=" + OuraAuth.enc(store.redirectUri) + client()
        store.tokens = request(form)
        store.pendingLogin = null
    }

    /**
     * A token that is good for at least another minute; refreshes it (and stores the new pair) when needed. Synchronized: WHOOP
     * invalidates the old refresh token the moment it is used, so two refreshes at once would end the login.
     */
    @Synchronized
    fun accessToken(forceRefresh: Boolean = false): String {
        val tokens = store.tokens ?: throw WhoopException(WhoopException.Kind.NOT_CONNECTED)
        if (!forceRefresh && tokens.expiresAt.isAfter(now().plusSeconds(60))) return tokens.access
        val refresh = tokens.refresh ?: run {
            store.tokens = null
            throw WhoopException(WhoopException.Kind.LOGIN_EXPIRED)
        }
        // WHOOP's refresh tokens are single-use: the answer carries the next one, which replaces this one.
        val fresh = try {
            request("grant_type=refresh_token&refresh_token=" + OuraAuth.enc(refresh) + "&scope=offline" + client())
        } catch (e: WhoopException) {
            if (e.kind == WhoopException.Kind.LOGIN_EXPIRED) store.tokens = null
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
        val id = store.clientId ?: throw WhoopException(WhoopException.Kind.NOT_CONNECTED)
        val secret = store.clientSecret ?: throw WhoopException(WhoopException.Kind.NOT_CONNECTED)
        return "&client_id=" + OuraAuth.enc(id) + "&client_secret=" + OuraAuth.enc(secret)
    }

    private fun request(form: String): WhoopTokens {
        val response = try {
            http.send("POST", TOKEN_URL, mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json"), form, TIMEOUT_MS)
        } catch (e: IOException) {
            throw WhoopException(WhoopException.Kind.NETWORK, e.message)
        }
        when (response.status) {
            200 -> Unit
            // Only a refusal of the login itself ends it (invalid_grant is a 400, invalid_client a 401: RFC 6749, 5.2). A busy
            // WHOOP (429, 5xx) says nothing about the refresh token, and throwing it away would make the person log in again
            // for something that passes by itself.
            400, 401 -> throw WhoopException(WhoopException.Kind.LOGIN_EXPIRED, "WHOOP answered ${response.status}")
            429 -> throw WhoopException(WhoopException.Kind.RATE_LIMITED, "WHOOP answered 429", WhoopClient.retryAfter(response, now()))
            else -> throw WhoopException(WhoopException.Kind.SERVER, "WHOOP answered ${response.status}")
        }
        val json = try {
            JSON.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            throw WhoopException(WhoopException.Kind.SERVER, "WHOOP's answer could not be read")
        }
        val access = json.string("access_token") ?: throw WhoopException(WhoopException.Kind.SERVER, "no access token in WHOOP's answer")
        val seconds = (json["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 3600L
        return WhoopTokens(access, json.string("refresh_token"), now().plusSeconds(seconds))
    }
}

internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toDoubleOrNull()
internal fun JsonObject.int(name: String): Int? = number(name)?.let { Math.round(it).toInt() }
internal fun JsonObject.long(name: String): Long? = number(name)?.let { Math.round(it) }
internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
internal fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toBooleanStrictOrNull()

/**
 * Reads WHOOP's v2 collections with the login of [WhoopAuth], following `next_token` to the last page. Every collection
 * answers `{"records": [...], "next_token": "..."}`, at most 25 records a page, newest first.
 */
class WhoopClient(
    private val http: WhoopHttp,
    private val auth: WhoopAuth,
    /** What to do about a dropped connection, a 429 or a 5xx before reporting it. The app asks for [RetryPolicy.standard]. */
    private val retry: RetryPolicy = RetryPolicy.NONE,
    private val now: () -> Instant = Instant::now,
) {
    companion object {
        const val BASE = "https://api.prod.whoop.com/developer/v2/"
        const val PAGE = 25
        private const val TIMEOUT_MS = 30_000
        private const val MAX_PAGES = 40
        private val JSON = Json { ignoreUnknownKeys = true }

        /** The wait WHOOP asks for: `Retry-After`, else the seconds until the rate limit window resets (`X-RateLimit-Reset`). */
        fun retryAfter(response: WhoopResponse, now: Instant): Long? =
            RetryAfter.seconds(response.headers["retry-after"] ?: response.headers["x-ratelimit-reset"], now)
    }

    /** All records of [path] (such as "cycle" or "activity/sleep") that started at or after [start]. */
    fun records(path: String, start: Instant): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        var next: String? = null
        for (page in 0 until MAX_PAGES) {
            val url = BASE + path + "?limit=" + PAGE + "&start=" + OuraAuth.enc(start.toString()) + (next?.let { "&nextToken=" + OuraAuth.enc(it) } ?: "")
            val json = get(url)
            (json["records"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(out::add) }
            next = json.string("next_token")?.takeIf { it.isNotEmpty() } ?: break
        }
        return out
    }

    private fun get(url: String): JsonObject {
        var response = call(url, auth.accessToken())
        // A token WHOOP no longer takes although it has not run out by the clock: get a new one, once.
        if (response.status == 401) response = call(url, auth.accessToken(forceRefresh = true))
        when (response.status) {
            200 -> Unit
            401 -> throw WhoopException(WhoopException.Kind.LOGIN_EXPIRED)
            403 -> throw WhoopException(WhoopException.Kind.FORBIDDEN)
            429 -> throw WhoopException(WhoopException.Kind.RATE_LIMITED, retryAfterS = retryAfter(response, now()))
            else -> throw WhoopException(WhoopException.Kind.SERVER, "WHOOP answered ${response.status}")
        }
        return try {
            JSON.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            throw WhoopException(WhoopException.Kind.SERVER, "WHOOP's answer could not be read")
        }
    }

    private fun call(url: String, token: String): WhoopResponse = try {
        val headers = mapOf("Authorization" to "Bearer $token", "User-Agent" to WhoopAuth.USER_AGENT, "Accept" to "application/json")
        retry.call(
            now,
            { http.send("GET", url, headers, null, TIMEOUT_MS) },
            { it.status },
            // WHOOP says how long a rate limit lasts in X-RateLimit-Reset; the retry policy reads Retry-After, so hand it that.
            { r -> r.headers["x-ratelimit-reset"]?.let { r.headers + ("retry-after" to (r.headers["retry-after"] ?: it)) } ?: r.headers },
        )
    } catch (e: IOException) {
        throw WhoopException(WhoopException.Kind.NETWORK, e.message)
    }
}
