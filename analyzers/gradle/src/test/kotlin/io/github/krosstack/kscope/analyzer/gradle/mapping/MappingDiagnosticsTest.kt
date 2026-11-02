package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MappingDiagnosticsTest {
    private val javaLibrary = SampleModel.load("java-library.json", Path.of("/work/javalib"), "javalib")

    @Test
    fun `a failed section is a warning that names the project and carries no absolute path`() {
        val warning = javaLibrary.mapped.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals("gradle.tooling.section-failed", warning.code.value)
        assertTrue("'classpaths' of project ':lib'" in warning.message)
        assertTrue("<workspace>/lib/libs/missing.jar" in warning.message)
        assertFalse("javalib" in warning.message)
        assertFalse("second line" in warning.message)
    }

    @Test
    fun `a build outside the workspace is reported and not modelled`() {
        val text = SampleModel::class.java.getResource("/models/included-build.json")!!.readText()
        val moved = text.replace("/work/tasktide/build-logic", "/elsewhere/build-logic")
        val mapped = BuildModelMapper(RepositoryId("tasktide"), WorkspacePaths(Path.of("/work/tasktide")))
            .map(ModelDocuments.parse(moved))
        assertEquals(listOf("tasktide//"), mapped.model.buildRoots.map { it.id.value })
        assertTrue(mapped.model.modules.none { it.path == ":" && it.buildRoot.value.endsWith("build-logic") })
        val warning = mapped.diagnostics.single { it.code.value == "gradle.tooling.build-outside-workspace" }
        assertTrue("build-logic" in warning.message)
    }

    @Test
    fun `the model is the same whatever the order of the documents`() {
        val text = SampleModel::class.java.getResource("/models/android-app-flavors.json")!!.readText()
        val documents = ModelDocuments.parse(text)
        val mapper = BuildModelMapper(RepositoryId("tasktide"), WorkspacePaths(Path.of("/work/tasktide")))
        assertEquals(mapper.map(documents).model, mapper.map(documents.reversed()).model)
    }

    @Test
    fun `documents with unknown fields and explicit nulls are read`() {
        val text = """[{"schema":1,"path":":a","dir":"/w/a","buildDir":"/w/a/build","future":1,
            "build":{"name":"w","rootDir":"/w"},"plugins":["java"],"dependencies":null,"kotlin":null,"errors":{}}]"""
        val documents = ModelDocuments.parse(text)
        assertEquals(":a", documents.single().path)
        assertTrue(documents.single().dependencies.projects.isEmpty())
    }
}
