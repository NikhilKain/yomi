package eu.kanade.tachiyomi.data.sync.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * One-shot HTTP listener on the loopback interface, used to catch the OAuth redirect.
 *
 * Google deprecated custom URI scheme redirects for Android OAuth clients (they are disabled by
 * default and rejected with `invalid_request`), but continues to support the loopback IP flow for
 * "Desktop app" clients. Since the browser and this app are on the same device, the redirect to
 * `http://127.0.0.1:<port>` lands right here.
 *
 * Bound explicitly to 127.0.0.1 rather than all interfaces, so nothing off-device can reach it, and
 * it accepts exactly one request before closing.
 */
class GoogleDriveOAuthServer : Closeable {

    private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = serverSocket.localPort

    val redirectUri: String get() = "http://127.0.0.1:$port"

    /**
     * Blocks until the browser hits the redirect, then returns the parsed query parameters.
     * Cancelling the calling coroutine closes the socket, which unblocks the accept.
     */
    suspend fun awaitRedirect(): Map<String, String> = withContext(Dispatchers.IO) {
        serverSocket.use { server ->
            val socket = try {
                server.accept()
            } catch (e: Exception) {
                // A closed socket here means we were cancelled rather than genuinely failing.
                throw CancellationException("OAuth redirect listener closed").initCause(e)
            }

            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val requestLine = reader.readLine().orEmpty()

                val params = parseQuery(requestLine)

                val body = if (params.containsKey("code")) SUCCESS_PAGE else failurePage(params["error"])
                client.getOutputStream().use { out ->
                    out.write(
                        buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: text/html; charset=utf-8\r\n")
                            append("Content-Length: ${body.toByteArray().size}\r\n")
                            append("Connection: close\r\n\r\n")
                            append(body)
                        }.toByteArray(),
                    )
                    out.flush()
                }

                params
            }
        }
    }

    /**
     * Pulls the query string out of a request line such as `GET /?code=abc&scope=xyz HTTP/1.1`.
     */
    private fun parseQuery(requestLine: String): Map<String, String> {
        val path = requestLine.split(" ").getOrNull(1).orEmpty()
        val query = path.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()

        return query.split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                val key = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = parts.getOrNull(1).orEmpty()
                decode(key) to decode(value)
            }
            .toMap()
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Could not decode OAuth redirect parameter" }
        value
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    private fun failurePage(error: String?) = page(
        title = "Sign-in failed",
        message = error?.let { "Google returned: $it" } ?: "No authorization code was returned.",
    )

    companion object {
        private val SUCCESS_PAGE = page(
            title = "Signed in",
            message = "You can close this tab and go back to Mihon.",
        )

        private fun page(title: String, message: String) = """
            <!doctype html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title></head>
            <body style="font-family:system-ui,sans-serif;text-align:center;padding:3rem 1.5rem">
            <h2>$title</h2><p>$message</p>
            </body></html>
        """.trimIndent()
    }
}
