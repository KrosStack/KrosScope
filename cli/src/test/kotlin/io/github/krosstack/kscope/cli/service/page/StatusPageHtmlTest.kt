package io.github.krosstack.kscope.cli.service.page

import io.github.krosstack.kscope.cli.serve.ScanRecord
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusPageHtmlTest {
    private val served = WorkspaceStatus(
        root = "/work/tasktide",
        state = "served. Watcher: up to date; last update none; 0 automatic updates.",
        indexed = true,
        inMemory = true,
        lastScan = ScanRecord(Instant.parse("2026-10-11T08:31:02Z"), 12_345, null),
        watcher = "Watcher: up to date; last update none; 0 automatic updates.",
        settingsFile = "kscope.toml",
        workers = "4 (kscope.toml)",
        workerHeap = "2,048 MiB (default)",
        errors = emptyList(),
    )
    private val model = StatusPageModel(
        version = "0.1.0",
        pid = 41230,
        startedAt = "2026-10-11T08:12:40.113Z",
        limits = ServiceLimits(idleMinutes = 5, memoryMib = 512),
        heapUsedBytes = 100L * 1_048_576,
        heapMaximumBytes = 512L * 1_048_576,
        workspaces = listOf(served),
    )

    @Test
    fun `the page shows the service and each fact of a workspace`() {
        val page = StatusPageHtml.render(model)

        val expected = listOf(
            "<title>KrosScope service</title>",
            "<dd>0.1.0</dd>",
            "<dd>41230</dd>",
            "<dd>idle 5 minutes, memory 512 MiB</dd>",
            "<dd>heap in use 100 MiB of 512 MiB</dd>",
            "<h3>/work/tasktide</h3>",
            "<dd>built</dd>",
            "<dd>in memory</dd>",
            "<dd>2026-10-11T08:31:02Z, took 12,345 ms</dd>",
            "<dd>up to date; last update none; 0 automatic updates.</dd>",
            "<dd>kscope.toml</dd>",
            "<dd>4 (kscope.toml)</dd>",
            "<dd>2,048 MiB (default)</dd>",
        )
        for (fragment in expected) {
            assertTrue(page.contains(fragment), "$fragment is not in:\n$page")
        }
        assertFalse(page.contains("Errors"), page)
    }

    @Test
    fun `the same model renders the same page`() {
        assertEquals(StatusPageHtml.render(model), StatusPageHtml.render(model.copy()))
    }

    @Test
    fun `the page has no script, form, link or external asset`() {
        val page = StatusPageHtml.render(model.copy(workspaces = listOf(served.copy(errors = listOf("broken")))))

        for (forbidden in listOf("<script", "<form", "<a ", "<link", "<img", "<iframe", "http://", "https://")) {
            assertFalse(page.contains(forbidden, ignoreCase = true), "$forbidden is in:\n$page")
        }
    }

    @Test
    fun `paths and messages are escaped`() {
        val hostile = "C:\\w\\<script>alert(1)</script>&\"'"
        val status = served.copy(root = hostile, errors = listOf("bad <b>key</b> & \"value\""), settingsFile = hostile)

        val page = StatusPageHtml.render(model.copy(workspaces = listOf(status)))

        assertFalse(page.contains("<script>alert"), page)
        assertFalse(page.contains("<b>key"), page)
        assertTrue(page.contains("<h3>C:\\w\\&lt;script&gt;alert(1)&lt;/script&gt;&amp;&quot;&#39;</h3>"), page)
        assertTrue(page.contains("<li>bad &lt;b&gt;key&lt;/b&gt; &amp; &quot;value&quot;</li>"), page)
    }

    @Test
    fun `errors are listed and a failed scan says so`() {
        val failed = ScanRecord(Instant.parse("2026-10-11T09:00:00Z"), 7, "boom")
        val status = served.copy(lastScan = failed, errors = listOf("kscope.toml:3: workers: not a number"))

        val page = StatusPageHtml.render(model.copy(workspaces = listOf(status)))

        assertTrue(page.contains("<dd>failed at 2026-10-11T09:00:00Z after 7 ms</dd>"), page)
        assertTrue(page.contains("<li>kscope.toml:3: workers: not a number</li>"), page)
    }

    @Test
    fun `a workspace that is not served shows its state and no facts of a server`() {
        val status = served.copy(
            state = "not served, because the directory does not exist.",
            indexed = null,
            inMemory = null,
            lastScan = null,
            watcher = null,
            errors = listOf("the directory does not exist"),
        )

        val page = StatusPageHtml.render(model.copy(workspaces = listOf(status)))

        assertTrue(page.contains("<dd>not served, because the directory does not exist.</dd>"), page)
        assertFalse(page.contains("<dt>Watcher</dt>"), page)
        assertFalse(page.contains("<dt>Graph</dt>"), page)
        assertTrue(page.contains("none since the service took the workspace up"), page)
    }

    @Test
    fun `a service that keeps no workspace says so`() {
        val page = StatusPageHtml.render(model.copy(workspaces = emptyList()))

        assertTrue(page.contains("<p>${StatusPageHtml.NO_WORKSPACE}</p>"), page)
    }
}
