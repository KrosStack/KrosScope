package io.github.krosstack.kscope.service.settings

import kotlin.test.AfterTest
import kotlin.test.Test

class SettingsFileValueTest {
    private val fixture = SettingsFileFixture()

    @AfterTest
    fun cleanUp() {
        fixture.close()
    }

    @Test
    fun `a value outside its range is refused with the line and the key`() {
        val range = "must be between"
        val heap = "resources.worker-heap-mib"
        listOf(
            Rejection("format = 0\n", 1, "format", "must be 1"),
            Rejection("\nformat = 2\n", 2, "format", "must be 1"),
            Rejection("[resources]\nworkers = 0\n", 2, "resources.workers", "$range 1 and 8"),
            Rejection("[resources]\nworkers = 9\n", 2, "resources.workers", "$range 1 and 8"),
            Rejection("[resources]\nworkers = -1\n", 2, "resources.workers", "$range 1 and 8"),
            Rejection("[resources]\nworker-heap-mib = 255\n", 2, heap, "$range 256 and 8192"),
            Rejection("[resources]\nworker-heap-mib = 8193\n", 2, heap, "$range 256 and 8192"),
            Rejection("[resources]\n\nworker-heap-mib = 0\n", 3, heap, "$range 256 and 8192"),
        ).forEach(fixture::assertRejected)
    }

    @Test
    fun `a value of another type is refused`() {
        val integer = "must be an integer"
        val heap = "resources.worker-heap-mib"
        listOf(
            Rejection("format = \"1\"\n", 1, "format", integer),
            Rejection("format = 1.0\n", 1, "format", integer),
            Rejection("[resources]\nworkers = \"4\"\n", 2, "resources.workers", integer),
            Rejection("[resources]\nworkers = 4.5\n", 2, "resources.workers", integer),
            Rejection("[resources]\nworkers = true\n", 2, "resources.workers", integer),
            Rejection("[resources]\nworkers = [4]\n", 2, "resources.workers", integer),
            Rejection("[resources]\nworker-heap-mib = 2048.0\n", 2, heap, integer),
            Rejection("[resources]\nworker-heap-mib = {a=1}\n", 2, heap, integer),
            Rejection("resources = 3\n", 1, "resources", "must be a table"),
            Rejection("[[resources]]\nworkers = 1\n", 1, "resources", "must be a table"),
        ).forEach(fixture::assertRejected)
    }
}
