package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val MAP = "src/main/resources/web/dist/assets/index.js.map"
private const val SCRIPT = "lib/web/greeting.ts"
private const val SOURCES = """["../../src/main.ts","../../../../../../$SCRIPT"]"""

/**
 * A bundle in one Gradle module whose source map names a script in another module that it does not depend on, so that
 * the script may be added after the first scan.
 */
class WebBundleIncrementalIntegrationTest {
    private val sample = SampleWorkspace("web")

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun addLibraryModule() {
        sample.write("settings.gradle.kts", "rootProject.name = \"web\"\n\ninclude(\":lib\")\n")
        sample.write("lib/build.gradle.kts", "plugins { kotlin(\"jvm\") }\n")
        sample.write(MAP, """{"version":3,"file":"index.js","names":[],"mappings":"","sources":$SOURCES}""")
    }

    private fun GraphReader.sourceFiles(): List<String> = relationships(
        RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "source-map", limit = Int.MAX_VALUE),
    ).mapNotNull { link ->
        val target = (link.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol
        target?.let { symbol(it.id) }?.nameLocation?.artifact?.path?.value
    }.sorted()

    @Test
    fun `a script added to another module after a scan is linked and an incremental scan agrees`() {
        addLibraryModule()
        KrosScope.open(sample.root).use { service ->
            service.scan()
            assertEquals(listOf("src/main/resources/web/src/main.ts"), service.graph().sourceFiles())

            sample.write(SCRIPT, "export const greeting = \"hello\";\n")
            val rescan = service.scan(verify = true)
            val verification = assertNotNull(rescan.verification)

            assertTrue(verification.matches, "${verification.differences}")
            assertEquals(listOf(SCRIPT, "src/main/resources/web/src/main.ts"), service.graph().sourceFiles())
        }
    }
}
