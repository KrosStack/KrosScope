package io.github.krosstack.kscope.service.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** `info` shows the analyzer switches and whether the index was built with them. */
class SettingsAnalyzersInfoTest {
    @Test
    fun `info shows the switches and whether the index was built with them`() {
        SettingsWorkspace().use { workspace ->
            assertEquals("not-indexed", workspace.open { it.info() }.settings.analyzers.indexState)
            workspace.open { it.scan() }
            val all = workspace.open { it.info() }.settings.analyzers
            assertEquals(AnalyzersReport(true, true, true, true, true, true, true, "current"), all)

            workspace.write("kscope.toml", "[analyzers]\naidl = false\ncompose-resources = false\n")
            val outdated = workspace.open { it.info() }.settings.analyzers
            assertEquals(listOf(AnalyzerKind.AIDL, AnalyzerKind.COMPOSE_RESOURCES), outdated.disabled())
            assertEquals("outdated", outdated.indexState)
            assertEquals("current", workspace.open { it.info() }.settings.scan.indexState)

            workspace.open { it.scan() }
            val current = workspace.open { it.info() }.settings.analyzers
            assertEquals("current", current.indexState)
            assertEquals(false, current.aidl)
            assertEquals(true, current.schemas)

            workspace.write("kscope.toml", "[analyzers]\naidl = 1\n")
            assertEquals("unknown", workspace.open { it.info() }.settings.analyzers.indexState)
        }
    }

    @Test
    fun `the fixed defaults of a benchmark ignore the switches of the file`() {
        SettingsWorkspace().use { workspace ->
            workspace.write("kscope.toml", "[analyzers]\nschemas = false\n")
            val fixed = workspace.options().copy(settings = WorkspaceSettings.DEFAULTS)
            workspace.open(fixed) { it.scan() }

            assertEquals(true, workspace.open(fixed) { it.info() }.settings.analyzers.schemas)
        }
    }
}
