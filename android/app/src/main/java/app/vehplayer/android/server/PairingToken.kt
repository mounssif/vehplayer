package app.vehplayer.android.server

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Trust boundary (ARCHITECTURE.md §1): "the hotspot may have other clients.
 * The WS server requires a short-lived pairing token... No open
 * unauthenticated stream." This is the whole implementation of that rule.
 *
 * Deliberately simple: single-process in-memory token, one active token at a
 * time (regenerated per streaming session, not per connection attempt).
 * TODO(claude-code): decide expiry policy at Gate 2, current default (10
 * minutes) is a placeholder, not derived from any real UX testing.
 */
object PairingToken {
    private const val TOKEN_BYTES = 16
    private val random = SecureRandom()
    private val activeTokens = ConcurrentHashMap<String, Long>() // token -> expiry epoch millis

    private const val DEFAULT_TTL_MS = 10 * 60 * 1000L

    fun generate(ttlMs: Long = DEFAULT_TTL_MS): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        activeTokens[token] = System.currentTimeMillis() + ttlMs
        pruneExpired()
        return token
    }

    fun isValid(token: String): Boolean {
        pruneExpired()
        val expiry = activeTokens[token] ?: return false
        return System.currentTimeMillis() < expiry
    }

    /**
     * Slides a still-valid token's expiry forward by another full TTL
     * window. Called on every successful `hello` (LocalMediaServer), so a
     * token never expires mid-drive purely from wall-clock time as long as
     * the car reconnects at least once per TTL window - closes the real
     * failure mode session 10 found: Reverse closes the browser, and a
     * token that happened to lapse right in that gap would otherwise force
     * a full re-pair through /go instead of a resume.
     */
    fun touch(token: String, ttlMs: Long = DEFAULT_TTL_MS) {
        activeTokens.computeIfPresent(token) { _, _ -> System.currentTimeMillis() + ttlMs }
    }

    /** Builds the /go URL with the token embedded, per ARCHITECTURE.md §1 ("rendered as part of the /go URL or a 4-digit code"). */
    fun buildGoUrl(baseUrl: String, token: String): String = "$baseUrl?token=$token"

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        activeTokens.entries.removeIf { it.value < now }
    }
}
