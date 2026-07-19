package app.vehplayer.android.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.net.URLEncoder

/**
 * Serves the cached web client bundle at the phone's local address
 * (ARCHITECTURE.md §6): "the phone's local HTTP server also hosts the
 * last-known-good web client bundle... if the domain is unreachable, the
 * local URL path still works."
 *
 * Assets are expected under `assets/webclient/` in the APK (the built
 * `webclient/dist/` output, see webclient/probe/DEPLOY_CF_PAGES.md's sibling
 * note for the CDN path; this is the *other* path, bundled offline). The
 * `copyWebclientDist` Gradle task (app/build.gradle.kts) copies
 * webclient/dist/ into assets/webclient/ before every build; run
 * `npm run build` in webclient/ first, or this directory is empty/stale.
 *
 * `/go` is the canonical entry point (ARCHITECTURE.md §6: "the /go page
 * doubles as the compatibility probe"): it mints a fresh pairing token and
 * redirects into the bundle with `?token=...&ws=...` set, matching
 * webclient/src/main.ts's query-param expectations. Redirects to the CDN
 * bundle (auto-updates without a new APK, README's "resilience against
 * domain failure" design) rather than the local one; the local bundle stays
 * reachable directly (e.g. http://<phone-ip>:8080/index.html?...) as the
 * manual fallback if the CDN is ever unreachable. Only the static asset
 * origin changes here, the WS URL below always targets the phone directly,
 * the data plane is local, always (Foundation, house rules).
 */
class HttpAssetServer(
    private val context: Context,
    private val wsPort: Int,
    port: Int = 8080,
) : NanoHTTPD(port) {

    companion object {
        private const val CDN_BASE_URL = "https://veh.modev.be"
    }

    /**
     * Zero-adb reachability counter, shown in the dashboard's connect-info
     * overlay: any request at all proves TCP from the client reached this
     * server. Session 9's ERR_CONNECTION_REFUSED in the car was ambiguous
     * between "car firewall refuses RFC1918" and "packet arrived but nothing
     * was listening" - a glance at this counter after a car attempt settles
     * that class of question without a laptop.
     */
    @Volatile
    var requestCount = 0
        private set

    /** Last JSON report POSTed by the in-car /diag page; null until one runs. */
    @Volatile
    var lastDiagReport: String? = null
        private set

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        requestCount++

        if (uri == "/go" || uri == "/go/") {
            val token = PairingToken.generate()
            val host = session.headers["host"]?.substringBefore(':') ?: "localhost"
            val wsUrl = "ws://$host:$wsPort/"
            val redirectTo = "$CDN_BASE_URL/index.html?token=$token&ws=${URLEncoder.encode(wsUrl, "UTF-8")}"
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply {
                addHeader("Location", redirectTo)
            }
        }

        // Diagnostic endpoints (session 9). The /diag page is served from the
        // phone's own http origin, so its fetch/WS/XHR/STUN rows are
        // same-origin and escape the mixed-content block the cloud https
        // probe hits. Loading /diag at all already proves TCP car->phone.
        if (uri == "/ping" || uri == "/ping/") {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "pong")
                .apply { addHeader("Access-Control-Allow-Origin", "*") }
        }
        if (uri == "/diag-config") {
            val body = "{\"wsPort\":$wsPort,\"httpPort\":${listeningPort}}"
            return newFixedLengthResponse(Response.Status.OK, "application/json", body)
                .apply { addHeader("Access-Control-Allow-Origin", "*") }
        }
        if (uri == "/diag-report" && session.method == Method.POST) {
            val len = session.headers["content-length"]?.toIntOrNull() ?: 0
            val json = if (len > 0) {
                val buf = ByteArray(len)
                var read = 0
                while (read < len) {
                    val n = session.inputStream.read(buf, read, len - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read, Charsets.UTF_8)
            } else ""
            lastDiagReport = json
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
                .apply { addHeader("Access-Control-Allow-Origin", "*") }
        }

        val assetPath = when {
            uri == "/" || uri.isEmpty() -> "index.html"
            // Extensionless convenience alias so the car only has to type /diag.
            uri == "/diag" || uri == "/diag/" -> "diag.html"
            else -> uri.removePrefix("/")
        }
        return try {
            val stream = context.assets.open("webclient/$assetPath")
            newChunkedResponse(Response.Status.OK, mimeTypeFor(assetPath), stream)
        } catch (e: java.io.IOException) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found: $assetPath")
        }
    }

    private fun mimeTypeFor(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".wasm") -> "application/wasm"
        else -> "application/octet-stream"
    }
}
