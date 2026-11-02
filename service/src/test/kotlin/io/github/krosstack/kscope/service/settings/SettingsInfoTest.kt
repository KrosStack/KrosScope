package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.InfoResult
import io.github.krosstack.kscope.service.ServiceJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsInfoTest {
    private val settingsFile = "kscope.toml"
    private val lenient = Json { ignoreUnknownKeys = true }

    @Test
    fun `info names no file and shows the defaults when the workspace has none`() {
        SettingsWorkspace().use { workspace ->
            val settings = workspace.open { it.info() }.settings

            val resources = ResourcesReport(SettingValue(null, "default"), SettingValue(2048, "default"))
            assertEquals(defaultReport(null, resources), settings)
        }
    }

    @Test
    fun `info shows the file its values and where each value comes from`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "format = 1\n[resources]\nworkers = 4\nworker-heap-mib = 1024\n")

            val settings = workspace.open { it.info() }.settings

            val resources = ResourcesReport(SettingValue(4, "kscope.toml"), SettingValue(1024, "kscope.toml"))
            assertEquals(defaultReport("kscope.toml", resources), settings)
        }
    }

    @Test
    fun `info shows the environment variable by name when it wins`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "[resources]\nworkers = 4\nworker-heap-mib = 1024\n")

            val settings = workspace.open(environment = mapOf("KSCOPE_WORKERS" to "2")::get) { it.info() }.settings

            assertEquals(SettingValue(2, "KSCOPE_WORKERS"), settings.resources.workers)
            assertEquals(SettingValue(1024, "kscope.toml"), settings.resources.workerHeapMib)
        }
    }

    @Test
    fun `the settings are the same for a workspace that was scanned and one that was not`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "[resources]\nworkers = 4\n")
            val unscanned = workspace.open { it.info() }.settings
            workspace.open { it.scan() }

            val scanned = workspace.open { it.info() }.settings

            assertEquals(unscanned.resources, scanned.resources)
            assertEquals("not-indexed", unscanned.scan.indexState)
            assertEquals("current", scanned.scan.indexState)
        }
    }

    @Test
    fun `the result survives a round trip through json`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "[resources]\nworkers = 99\n")
            val info = workspace.open { it.info() }

            val decoded = lenient.decodeFromString(InfoResult.serializer(), ServiceJson.encode(info))

            assertEquals(info, decoded)
        }
    }
}
