package io.github.krosstack.kscope.service.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceResolutionTest {
    private val file = "kscope.toml"

    private fun resolve(
        environment: Map<String, String> = emptyMap(),
        values: ResourceSettings = ResourceSettings(),
    ): ResourcesReport = ResourceResolution(environment::get).resolve(values, file)

    private fun value(value: Int?, origin: String) = SettingValue(value, origin)

    @Test
    fun `without a file or a variable every setting is its default`() {
        val resolved = resolve()

        assertEquals(value(null, "default"), resolved.workers)
        assertEquals(value(2048, "default"), resolved.workerHeapMib)
    }

    @Test
    fun `the file beats the default`() {
        val resolved = resolve(values = ResourceSettings(workers = 4, workerHeapMib = 1024))

        assertEquals(value(4, "kscope.toml"), resolved.workers)
        assertEquals(value(1024, "kscope.toml"), resolved.workerHeapMib)
    }

    @Test
    fun `the variable beats the default and names itself as the origin`() {
        val resolved = resolve(mapOf("KSCOPE_WORKERS" to "3", "KSCOPE_WORKER_HEAP" to "4g"))

        assertEquals(value(3, "KSCOPE_WORKERS"), resolved.workers)
        assertEquals(value(4096, "KSCOPE_WORKER_HEAP"), resolved.workerHeapMib)
    }

    @Test
    fun `the variable beats the file for each setting on its own`() {
        val values = ResourceSettings(workers = 4, workerHeapMib = 1024)

        val both = resolve(mapOf("KSCOPE_WORKERS" to "2", "KSCOPE_WORKER_HEAP" to "512m"), values)
        assertEquals(value(2, "KSCOPE_WORKERS"), both.workers)
        assertEquals(value(512, "KSCOPE_WORKER_HEAP"), both.workerHeapMib)

        val workersOnly = resolve(mapOf("KSCOPE_WORKERS" to "2"), values)
        assertEquals(value(2, "KSCOPE_WORKERS"), workersOnly.workers)
        assertEquals(value(1024, "kscope.toml"), workersOnly.workerHeapMib)

        val heapOnly = resolve(mapOf("KSCOPE_WORKER_HEAP" to "512m"), values)
        assertEquals(value(4, "kscope.toml"), heapOnly.workers)
        assertEquals(value(512, "KSCOPE_WORKER_HEAP"), heapOnly.workerHeapMib)
    }

    @Test
    fun `a variable that is set but not usable counts as not set and the file applies`() {
        val values = ResourceSettings(workers = 4, workerHeapMib = 1024)
        for (unusable in listOf("0", "-1", "many", "", " ", "2.5")) {
            val resolved = resolve(mapOf("KSCOPE_WORKERS" to unusable), values)
            assertEquals(value(4, "kscope.toml"), resolved.workers, unusable)
        }
        for (unusable in listOf("0", "-1g", "a lot", "", " ", "1.5g", "12x")) {
            val resolved = resolve(mapOf("KSCOPE_WORKER_HEAP" to unusable), values)
            assertEquals(value(1024, "kscope.toml"), resolved.workerHeapMib, unusable)
        }
    }

    @Test
    fun `a variable that is set but not usable counts as not set and the default applies`() {
        val resolved = resolve(mapOf("KSCOPE_WORKERS" to "many", "KSCOPE_WORKER_HEAP" to "a lot"))

        assertEquals(value(null, "default"), resolved.workers)
        assertEquals(value(2048, "default"), resolved.workerHeapMib)
    }

    @Test
    fun `a number of workers in the variable is shown as it is used, at most sixty four`() {
        assertEquals(value(64, "KSCOPE_WORKERS"), resolve(mapOf("KSCOPE_WORKERS" to "1000")).workers)
    }

    @Test
    fun `the origin of a value from the file is the origin that was given`() {
        val resolved = ResourceResolution(emptyMap<String, String>()::get)
            .resolve(ResourceSettings(workers = 2), "options")

        assertEquals(value(2, "options"), resolved.workers)
    }
}
