package io.github.krosstack.kscope.cli.service.page

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.host.WorkspaceState
import io.github.krosstack.kscope.cli.service.host.awaitServed
import io.github.krosstack.kscope.cli.service.host.awaitState
import io.github.krosstack.kscope.cli.service.host.control
import io.github.krosstack.kscope.cli.service.host.withTestService
import io.github.krosstack.kscope.cli.service.inServiceHome
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceStatusPageTest {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private fun send(url: String, method: String, body: String = ""): HttpResponse<String> {
        val publisher = if (body.isEmpty()) {
            HttpRequest.BodyPublishers.noBody()
        } else {
            HttpRequest.BodyPublishers.ofString(body)
        }
        val request = HttpRequest.newBuilder(URI.create(url)).method(method, publisher)
            .timeout(Duration.ofSeconds(20)).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun page(url: String): String {
        val response = send(url, "GET")
        assertEquals(200, response.statusCode(), response.body())
        return response.body()
    }

    private fun withoutMemory(page: String): String = page.replace(Regex("<dt>Memory</dt><dd>[^<]*</dd>"), "")

    @Test
    fun `a started service serves the page on the loopback address with each workspace`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                service.awaitServed(root)

                val response = send(service.instance.pageUrl, "GET")

                assertEquals(200, response.statusCode())
                assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"))
                assertEquals("127.0.0.1", service.instance.pageAddress.hostAddress)
                val body = response.body()
                assertTrue(body.contains(HtmlEscape.text(WorkspacePaths.real(root).toString())), body)
                assertTrue(body.contains("<dd>served."), body)
                assertTrue(body.contains("<dd>built</dd>"), body)
                assertTrue(body.contains("<dd>in memory</dd>"), body)
                assertTrue(body.contains("<dt>Workers</dt><dd>automatic (default)</dd>"), body)
            }
        }
    }

    @Test
    fun `the control channel hands out the address of the page`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                val answer = service.control("page")

                assertEquals(0, answer.exitCode, answer.stderr)
                assertEquals(service.instance.pageUrl + "\n", answer.stdout)
            }
        }
    }

    @Test
    fun `the page accepts no write method and the service keeps its state`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                service.awaitServed(root)
                val before = withoutMemory(page(service.instance.pageUrl))

                for (method in listOf("POST", "PUT", "DELETE", "PATCH")) {
                    val response = send(service.instance.pageUrl, method, "x=1")

                    assertEquals(405, response.statusCode(), method)
                    assertEquals("GET", response.headers().firstValue("Allow").orElse(""), method)
                }
                assertEquals(before, withoutMemory(page(service.instance.pageUrl)))
            }
        }
    }

    @Test
    fun `another token or path shows nothing`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                val base = service.instance.pageUrl.removeSuffix("/").substringBeforeLast("/")

                assertEquals(404, send("$base/", "GET").statusCode())
                assertEquals(404, send("$base/0000/", "GET").statusCode())
            }
        }
    }

    @Test
    fun `a scan through the server shows as the last scan`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                val server = service.awaitServed(root)
                assertTrue(page(service.instance.pageUrl).contains("none since the service took the workspace up"))

                assertEquals(0, server.query(listOf("scan")).exitCode)

                val body = page(service.instance.pageUrl)
                assertFalse(body.contains("none since the service took the workspace up"), body)
                assertTrue(Regex("<dd>\\S+Z, took [0-9,]+ ms</dd>").containsMatchIn(body), body)
            }
        }
    }

    @Test
    fun `an invalid settings file is listed as an error`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            Files.writeString(root.resolve("kscope.toml"), "[resources]\nworkers = \"many\"\n")
            withTestService(home, listOf(root)) { service ->
                service.awaitServed(root)

                val body = page(service.instance.pageUrl)

                assertTrue(body.contains("not valid, so a scan will not start"), body)
                assertTrue(body.contains("<h4>Errors</h4>"), body)
            }
        }
    }

    @Test
    fun `a workspace that does not exist is listed with its reason`() = inServiceHome { home ->
        val missing: Path = home.resolve("missing")
        withTestService(home, listOf(missing)) { service ->
            service.awaitState(missing) { it is WorkspaceState.NotServed }

            val body = page(service.instance.pageUrl)

            assertTrue(body.contains("not served"), body)
            assertTrue(body.contains("<h4>Errors</h4>"), body)
        }
    }

    @Test
    fun `the page is gone when the service has stopped`() = inServiceHome { home ->
        val url = withTestService(home, emptyList()) { it.instance.pageUrl }

        val refused = runCatching { send(url, "GET") }

        assertTrue(refused.isFailure, refused.toString())
    }
}
