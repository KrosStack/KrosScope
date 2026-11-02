package io.github.krosstack.kscope.service.settings

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsFileTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `a missing file is no settings`() {
        assertEquals(SettingsReading.Absent, fixture.readFile())
    }

    @Test
    fun `an empty file and a file of comments are valid and set nothing`() {
        assertEquals(loaded(ResourceSettings()), fixture.read(""))
        assertEquals(loaded(ResourceSettings()), fixture.read("# nothing to set\n\n"))
        assertEquals(loaded(ResourceSettings()), fixture.read("format = 1\n"))
    }

    @Test
    fun `every key is accepted at its bounds`() {
        val accepted = mapOf(
            "[resources]\nworkers = 1\n" to ResourceSettings(workers = 1),
            "[resources]\nworkers = 8\n" to ResourceSettings(workers = 8),
            "[resources]\nworker-heap-mib = 256\n" to ResourceSettings(workerHeapMib = 256),
            "[resources]\nworker-heap-mib = 8192\n" to ResourceSettings(workerHeapMib = 8192),
            "format = 1\n\n[resources]\nworkers = 4\nworker-heap-mib = 2048\n" to ResourceSettings(4, 2048),
        )
        accepted.forEach { (text, resources) -> assertEquals(loaded(resources), fixture.read(text), text) }
    }

    @Test
    fun `the same keys are accepted in the other forms of toml`() {
        val resources = ResourceSettings(workers = 2, workerHeapMib = 512)
        val quoted = "\"format\" = 1\n[ \"resources\" ]\n\"workers\" = 2\n'worker-heap-mib' = 512\n"
        assertEquals(loaded(resources), fixture.read("resources.workers = 2\nresources.worker-heap-mib = 512\n"))
        assertEquals(loaded(resources), fixture.read("resources = { workers = 2, worker-heap-mib = 512 }\n"))
        assertEquals(loaded(resources), fixture.read(quoted))
        assertEquals(loaded(resources), fixture.read("﻿[resources]\r\nworkers = 2\r\nworker-heap-mib = 512\r\n"))
    }

    @Test
    fun `the message names the file the line and the key`() {
        val issue = fixture.issuesOf("format = 1\n\n[resources]\n\nworkers = 99\n").single()

        assertEquals("kscope.toml:5: resources.workers: must be between 1 and 8", issue.describe("kscope.toml"))
    }

    @Test
    fun `lines are counted whatever ends them`() {
        assertEquals(3, fixture.issuesOf("format = 1\r\n[resources]\r\nworkers = 99\r\n").single().line)
        assertEquals(3, fixture.issuesOf("format = 1\n[resources]\nworkers = 99").single().line)
    }

    @Test
    fun `up to ten errors of one file are reported in order of their lines`() {
        val text = (1..12).joinToString("") { "key$it = 1\n" }

        val issues = fixture.issuesOf(text)

        assertEquals((1..10).toList(), issues.map { it.line })
        assertEquals((1..10).map { "key$it" }, issues.map { it.key })
    }

    @Test
    fun `every kind of error of one file is reported together`() {
        val text = "format = 3\nlevel = 1\n[resources]\nworkers = \"x\"\nworker-heap-mib = 1\n"

        val issues = fixture.issuesOf(text)

        assertEquals(listOf(1, 2, 4, 5), issues.map { it.line })
        assertEquals(listOf("format", "level", "resources.workers", "resources.worker-heap-mib"), issues.map { it.key })
    }
}
