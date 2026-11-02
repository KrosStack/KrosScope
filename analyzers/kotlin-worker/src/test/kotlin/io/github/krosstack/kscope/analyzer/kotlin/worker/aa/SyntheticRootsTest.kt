package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.SyntheticSource
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyntheticRootsTest {
    private fun stub(path: String, language: String = "kotlin") =
        SyntheticSource(path, language, "package gen\n", SourceSetId("replaced"), "test")

    private fun moduleWith(vararg stubs: SyntheticSource) = SampleWorkspace.module(
        "gen",
        Platform.JVM,
        SampleWorkspace.sourceSet("gen", "main", "generated/main", syntheticSources = stubs.toList()),
    )

    @Test
    fun `stubs are written below a root of their source set`() {
        val module = moduleWith(stub("gen/R.kt"))
        SyntheticRoots.write(listOf(module)).use { roots ->
            val root = checkNotNull(roots.rootOf(module.sourceSets.single()))
            assertTrue(Files.isRegularFile(root.resolve("gen").resolve("R.kt")))
            assertEquals(listOf(root.resolve("gen").resolve("R.kt")), roots.files.map { it.path })
        }
    }

    @Test
    fun `a virtual path without a source extension gets the one of the stub language`() {
        val module = moduleWith(stub("gen/Kotlin"), stub("gen/Java", "java"))
        SyntheticRoots.write(listOf(module)).use { roots ->
            val names = roots.files.map { it.path.fileName.toString() }
            assertEquals(listOf("Kotlin.kt", "Java.java"), names)
        }
    }

    @Test
    fun `a virtual path that leaves the root is not written`() {
        val module = moduleWith(stub("../escaped.kt"), stub("/absolute.kt"))
        SyntheticRoots.write(listOf(module)).use { roots ->
            assertEquals(emptyList(), roots.files)
        }
    }

    @Test
    fun `closing removes the written files and a source set without stubs has no root`() {
        val withStub = moduleWith(stub("gen/R.kt"))
        val without = SampleWorkspace.singleModule("plain", "shapes/main")
        val roots = SyntheticRoots.write(listOf(withStub, without))
        val written = roots.files.single().path
        assertNull(roots.rootOf(without.sourceSets.single()))
        roots.close()
        assertFalse(Files.exists(written))
    }
}
