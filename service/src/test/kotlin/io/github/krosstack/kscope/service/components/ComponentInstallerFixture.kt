package io.github.krosstack.kscope.service.components

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals

open class ComponentInstallerFixture {
    @TempDir
    protected lateinit var root: Path
    protected val plain: ByteArray = "component licence".toByteArray()

    protected fun entry(bytes: ByteArray) = ComponentEntry(
        "sample",
        "1",
        "Apache-2.0",
        "LICENSE",
        "Sample component",
        listOf(file("bundle.zip", bytes), file("LICENSE", plain)),
    )

    private fun file(path: String, bytes: ByteArray) = ComponentFile(
        path,
        "any",
        listOf("https://example.test/$path"),
        bytes.size.toLong(),
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
    )

    protected fun zip(path: String, payload: String = "payload"): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry(path).apply { time = 0 })
            it.write(payload.toByteArray())
            it.closeEntry()
        }
    }.toByteArray()

    protected fun assertEmpty() {
        Files.list(root.resolve("sample")).use { paths ->
            assertEquals(0, paths.filter { it.fileName.toString() != ComponentDirectory.LOCK_FILE }.count())
        }
    }

    protected fun server(action: (ComponentInstaller) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bytes = zip("runtime/data")
        server.createContext("/") { exchange ->
            if (exchange.requestURI.path == "/redirect") {
                exchange.responseHeaders.add("Location", "/LICENSE")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val body = if (exchange.requestURI.path.endsWith("zip")) bytes else plain
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        try {
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
            action(
                ComponentInstaller(ComponentDirectory(root)) { uri ->
                    val local = URI("http://127.0.0.1:${server.address.port}${uri.path}")
                    client.send(HttpRequest.newBuilder(local).build(), HttpResponse.BodyHandlers.ofInputStream()).body()
                },
            )
        } finally {
            server.stop(0)
        }
    }
}
