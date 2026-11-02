package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuralBuildModelTest {
    private fun scanned(block: (ScanEngine) -> Unit) = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            block(engine)
        }
    }

    private fun SourceSet.rootEntries() = roots.map { it.directory.value to it.kind.value }

    @Test
    fun `the settings directory is the build root and every build script directory a module`() = scanned { engine ->
        val structure = engine.graph().structure()
        val repository = structure.repositories.single().id.value
        assertEquals(listOf("$repository//"), structure.buildRoots.map { it.id.value })
        assertEquals(
            listOf("$repository//:", "$repository//:app", "$repository//:core"),
            structure.modules.map { it.id.value },
        )
        assertEquals(listOf(":", ":app", ":core"), structure.modules.map { it.path })
        assertEquals(listOf("", "app", "core"), structure.modules.map { it.directory.value })
    }

    @Test
    fun `source sets are the children of src with their roots by convention`() = scanned { engine ->
        val byName = engine.graph().structure().sourceSets.associateBy { it.id.value.substringAfterLast("//") }
        val expected = setOf(":app@main", ":app@test", ":app@androidTest")
        assertEquals(expected + setOf(":core@commonMain", ":core@jvmMain", ":core@commonTest"), byName.keys)
        assertEquals(
            listOf(
                "app/src/main/AndroidManifest.xml" to "android.manifest",
                "app/src/main/kotlin" to "kotlin",
                "app/src/main/res" to "android.res",
            ),
            byName.getValue(":app@main").rootEntries(),
        )
        assertEquals(
            listOf("core/src/jvmMain/java" to "java", "core/src/jvmMain/kotlin" to "kotlin"),
            byName.getValue(":core@jvmMain").rootEntries(),
        )
    }

    @Test
    fun `roles follow the source set names`() = scanned { engine ->
        val roles = engine.graph().structure().sourceSets.associate { it.name to it.role }
        assertEquals(SourceSetRole.MAIN, roles["main"])
        assertEquals(SourceSetRole.MAIN, roles["commonMain"])
        assertEquals(SourceSetRole.UNIT_TEST, roles["test"])
        assertEquals(SourceSetRole.UNIT_TEST, roles["commonTest"])
        assertEquals(SourceSetRole.INSTRUMENTED_TEST, roles["androidTest"])
    }

    @Test
    fun `everything is structural and heuristic and carries no targets variants or dependencies`() = scanned { engine ->
        val structure = engine.graph().structure()
        assertTrue(structure.modules.all { it.evidenceClass == EvidenceClass.STRUCTURAL })
        assertTrue(structure.modules.all { it.certainty == Certainty.HEURISTIC })
        assertTrue(structure.sourceSets.all { it.evidenceClass == EvidenceClass.STRUCTURAL })
        assertTrue(structure.sourceSets.all { it.certainty == Certainty.HEURISTIC })
        assertTrue(structure.sourceSets.all { it.dependsOn.isEmpty() && it.targets.isEmpty() && it.variants.isEmpty() })
        assertTrue(structure.targets.isEmpty() && structure.variants.isEmpty())
    }

    @Test
    fun `artifacts get the source set with the longest matching root`() = scanned { engine ->
        val sourceSetOf = engine.graph().artifacts()
            .associate { it.ref.path.value to it.sourceSet?.value?.substringAfterLast("//") }
        assertEquals(":app@main", sourceSetOf["app/src/main/kotlin/com/example/app/Main.kt"])
        assertEquals(":app@main", sourceSetOf["app/src/main/AndroidManifest.xml"])
        assertEquals(":app@main", sourceSetOf["app/src/main/res/values/strings.xml"])
        val instrumented = "app/src/androidTest/kotlin/com/example/app/MainInstrumentedTest.kt"
        assertEquals(":app@androidTest", sourceSetOf[instrumented])
        assertEquals(":core@jvmMain", sourceSetOf["core/src/jvmMain/java/com/example/core/Legacy.java"])
        assertEquals(null, sourceSetOf["app/build.gradle.kts"])
        assertEquals(null, sourceSetOf["README.md"])
    }

    @Test
    fun `a workspace without a settings script has no build model`() = TestWorkspace("plain").use { workspace ->
        workspace.engine().use { engine ->
            val report = engine.update()
            assertTrue(engine.graph().structure().modules.isEmpty())
            assertTrue(report.diagnostics.any { it.code.value == "build-model.not-available" })
        }
    }

    @Test
    fun `a build script outside any build root is reported and not modelled`() = TestWorkspace().use { workspace ->
        workspace.write("sub/settings.gradle.kts", "")
        workspace.write("sub/inner/build.gradle.kts", "")
        workspace.write("other/build.gradle.kts", "")
        workspace.engine().use { engine ->
            val report = engine.update()
            assertEquals(listOf(":inner"), engine.graph().structure().modules.map { it.path })
            val orphan = report.diagnostics.single { it.code.value == "build.structural.script-outside-build-root" }
            assertTrue("'other'" in orphan.message)
        }
    }

    @Test
    fun `a nested settings script starts another build root with its own modules`() = TestWorkspace().use { workspace ->
        workspace.write("settings.gradle.kts", "")
        workspace.write("build.gradle.kts", "")
        workspace.write("build-logic/settings.gradle.kts", "")
        workspace.write("build-logic/build.gradle.kts", "")
        workspace.write("build-logic/src/main/kotlin/Plugin.kt", "class Plugin")
        workspace.engine().use { engine ->
            engine.update()
            val structure = engine.graph().structure()
            assertEquals(listOf("", "build-logic"), structure.buildRoots.map { it.directory.value })
            val nested = structure.modules.single { it.directory.value == "build-logic" }
            assertEquals(":", nested.path)
            assertTrue(nested.id.value.endsWith("//build-logic:"))
            val sourceSet = structure.sourceSets.single()
            assertEquals(nested.id, sourceSet.module)
        }
    }
}
