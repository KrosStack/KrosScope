package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSetRole

/** Source set roles by name, and root kinds by directory name, following the Gradle plugin conventions. */
internal object SourceSetNaming {
    private val directoryKinds = mapOf(
        "kotlin" to "kotlin",
        "java" to "java",
        "res" to "android.res",
        "aidl" to "aidl",
        "assets" to "android.assets",
        "jniLibs" to "android.jniLibs",
        "proto" to "proto",
        "sqldelight" to "sqldelight",
        "composeResources" to "compose.resources",
        "resources" to "resources",
        "cpp" to "cpp",
        "jni" to "jni",
    )

    fun roleOf(name: String): SourceSetRole = when {
        isInstrumentedTest(name) -> SourceSetRole.INSTRUMENTED_TEST
        isTestFixtures(name) -> SourceSetRole.TEST_FIXTURES
        isUnitTest(name) -> SourceSetRole.UNIT_TEST
        else -> SourceSetRole.MAIN
    }

    fun rootKindOf(directory: String, fallback: String): SourceRootKind =
        SourceRootKind(directoryKinds[directory.substringAfterLast('/')] ?: fallback)

    /** The kind of root a directory of this name is by convention, if it is one. */
    fun conventionKind(directoryName: String): String? = directoryKinds[directoryName]

    private fun isInstrumentedTest(name: String) = name.startsWith("androidTest") ||
        name.startsWith("androidDeviceTest") ||
        name.startsWith("androidInstrumentedTest") ||
        name.endsWith("AndroidTest") ||
        name.endsWith("DeviceTest") ||
        name.endsWith("InstrumentedTest")

    private fun isTestFixtures(name: String) = name.startsWith("testFixtures") || name.endsWith("TestFixtures")

    private fun isUnitTest(name: String) = name == "test" ||
        name.endsWith("Test") ||
        (name.startsWith("test") && name.getOrNull(TEST_PREFIX_LENGTH)?.isUpperCase() == true)

    private const val TEST_PREFIX_LENGTH = 4
}
