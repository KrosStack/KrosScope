package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import io.github.krosstack.kscope.model.SourceRoot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RootsStaticTest {
    private val tree = StaticWorkspace("roots")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun roots(module: String, sourceSet: String): List<Pair<String, String>> =
        tree.provided().sourceSets(module).getValue(sourceSet).roots.map { it.directory.value to it.kind.value }

    private fun generated(roots: List<SourceRoot>): List<Pair<String, String>> =
        roots.filter { it.generated }.map { it.directory.value to it.kind.value }

    @Test
    fun `a module whose only sources are protocol buffers has a main source set with that root`() {
        assertEquals(listOf("wire/src/main/proto" to "proto"), roots(":wire", "main"))
        assertEquals("true", tree.provided().module(":wire").attributes["wire.kotlin"])
    }

    @Test
    fun `sqldelight, native and resource directories are roots beside the kotlin ones`() {
        assertEquals(
            listOf("db/src/main/kotlin" to "kotlin", "db/src/main/sqldelight" to "sqldelight"),
            roots(":db", "main"),
        )
        assertEquals("sample.db", tree.provided().module(":db").attributes["sqldelight.database.TaskDatabase.package"])
        assertEquals(
            listOf(
                "native/src/main/AndroidManifest.xml" to "android.manifest",
                "native/src/main/aidl" to "aidl",
                "native/src/main/cpp" to "cpp",
                "native/src/main/jni" to "jni",
                "native/src/main/res" to "android.res",
            ),
            roots(":native", "main"),
        )
    }

    @Test
    fun `compose resources and resource directories belong to the source set they are in`() {
        assertEquals(
            listOf(
                "kmp/src/commonMain/composeResources" to "compose.resources",
                "kmp/src/commonMain/kotlin" to "kotlin",
                "kmp/src/commonMain/sqldelight" to "sqldelight",
            ),
            roots(":kmp", "commonMain"),
        )
        assertEquals(listOf("kmp/src/jvmMain/resources" to "resources"), roots(":kmp", "jvmMain"))
    }

    @Test
    fun `generator output that exists below the build directory is a generated root and names match exactly`() {
        tree.directory("db/build/generated/sqldelight/code/TaskDatabase/main")
        tree.directory("db/build/generated/source/buildConfig/main")
        tree.directory("wire/build/generated/compose/resourceGenerator/assembledResources/Main")
        tree.directory("kmp/build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors")
        tree.directory("kmp/build/generated/compose/resourceGenerator/kotlin/commonResClass")
        tree.directory("kmp/build/generated/compose/resourceGenerator/assembledResources/jvmMain")
        tree.directory("wire/build/generated/ksp/main/kotlin")
        val provided = tree.provided()
        assertEquals(
            listOf("db/build/generated/sqldelight/code/TaskDatabase/main" to "kotlin"),
            generated(provided.sourceSets(":db").getValue("main").roots),
        )
        assertEquals(
            listOf(
                "kmp/build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors" to "kotlin",
                "kmp/build/generated/compose/resourceGenerator/kotlin/commonResClass" to "kotlin",
            ),
            generated(provided.sourceSets(":kmp").getValue("commonMain").roots),
        )
        assertEquals(
            listOf("kmp/build/generated/compose/resourceGenerator/assembledResources/jvmMain" to "resources"),
            generated(provided.sourceSets(":kmp").getValue("jvmMain").roots),
        )
        assertEquals(
            listOf("wire/build/generated/ksp/main/kotlin" to "kotlin"),
            generated(provided.sourceSets(":wire").getValue("main").roots),
        )
    }

    @Test
    fun `generated roots that do not exist are not recorded`() {
        val provided = tree.provided()
        assertTrue(provided.model.sourceSets.flatMap { it.roots }.none { it.generated })
    }
}
