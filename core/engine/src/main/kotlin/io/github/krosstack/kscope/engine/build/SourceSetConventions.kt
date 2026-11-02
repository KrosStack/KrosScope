package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSetRole

/** Source set layout that Gradle projects follow by convention: `src/<source set>/<root>`. */
internal object SourceSetConventions {
    const val SOURCE_DIRECTORY = "src"

    private val directoryRoots = mapOf(
        "kotlin" to "kotlin",
        "java" to "java",
        "res" to "android.res",
        "aidl" to "aidl",
        "proto" to "proto",
        "sqldelight" to "sqldelight",
        "composeresources" to "compose.resources",
        "resources" to "resources",
    )

    private const val MANIFEST_FILE = "androidmanifest.xml"
    private const val MANIFEST_KIND = "android.manifest"

    fun directoryRootKind(entry: String): SourceRootKind? = directoryRoots[entry.lowercase()]?.let(::SourceRootKind)

    fun isManifestFile(entry: String): Boolean = entry.lowercase() == MANIFEST_FILE

    fun manifestKind(): SourceRootKind = SourceRootKind(MANIFEST_KIND)

    fun roleOf(sourceSetName: String): SourceSetRole = when {
        isInstrumentedTest(sourceSetName) -> SourceSetRole.INSTRUMENTED_TEST
        isTestFixtures(sourceSetName) -> SourceSetRole.TEST_FIXTURES
        sourceSetName.endsWith("Test") || sourceSetName.equals("test", ignoreCase = true) -> SourceSetRole.UNIT_TEST
        else -> SourceSetRole.MAIN
    }

    private fun isInstrumentedTest(name: String) = name.equals("androidTest", ignoreCase = true) ||
        name.endsWith("AndroidTest") ||
        name.equals("androidInstrumentedTest", ignoreCase = true) ||
        name.endsWith("AndroidInstrumentedTest")

    private fun isTestFixtures(name: String) = name.equals("testFixtures", ignoreCase = true) ||
        name.endsWith("TestFixtures")
}
