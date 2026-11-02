package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.host.withTestService
import kotlin.test.Test
import kotlin.test.assertEquals

class ServicePageCommandTest {
    @Test
    fun `the page command says so and exits with 1 when no service runs`() = inServiceHome { home ->
        val page = runService(home, "page")

        assertEquals(1, page.statusCode, page.output)
        assertEquals(listOf("The service does not run."), page.stdoutLines())
    }

    @Test
    fun `the page command prints the address of the page of the running service`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            val page = runService(home, "page")

            assertEquals(0, page.statusCode, page.output)
            assertEquals(listOf(service.instance.pageUrl), page.stdoutLines())
        }
    }
}
