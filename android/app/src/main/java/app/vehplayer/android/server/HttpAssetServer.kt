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
 * webclient/src/main.ts's query-param expectations.
 */
class HttpAssetServer(
    private val context: Context,
    private val wsPort: Int,
    port: Int = 8080,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (uri == "/go" || uri == "/go/") {
            val token = PairingToken.generate()
            val host = session.headers["host"]?.substringBefore(':') ?: "localhost"
            val wsUrl = "ws://$host:$wsPort/"
            val redirectTo = "/index.html?token=$token&ws=${URLEncoder.encode(wsUrl, "UTF-8")}"
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply {
                addHeader("Location", redirectTo)
            }
        }

        val assetPath = if (uri == "/" || uri.isEmpty()) "index.html" else uri.removePrefix("/")
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
