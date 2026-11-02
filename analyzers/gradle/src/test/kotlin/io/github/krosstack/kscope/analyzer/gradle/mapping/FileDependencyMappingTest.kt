package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class FileDependencyMappingTest {
    private val workspace = Path.of("/work/tasktide").toAbsolutePath().normalize()
    private val root = workspace.toString().replace(File.separatorChar, '/')
    private val outside = workspace.resolveSibling("kit").toString().replace(File.separatorChar, '/')

    private val json = """[{"schema":1,"path":":app","dir":"$root/app","buildDir":"$root/app/build",
        "build":{"name":"tasktide","rootDir":"$root"},"plugins":["org.jetbrains.kotlin.jvm"],
        "archives":["app-1.0.jar"],
        "dependencies":{"files":[
            {"configuration":"implementation","path":"$root/app/libs/text.jar"},
            {"configuration":"runtimeOnly","path":"$outside/build/libs/kit.jar"}],
        "trees":[
            {"configuration":"implementation","dir":"$outside/build/libs","include":["*.jar"],"exclude":["old-*.jar"]},
            {"configuration":"runtimeOnly","dir":"$root/app/vendor","include":[],"exclude":[]}]}}]"""

    private val model = BuildModelMapper(RepositoryId("tasktide"), WorkspacePaths(workspace))
        .map(ModelDocuments.parse(json)).model

    @Test
    fun `each file of a file dependency becomes an edge to its path in the repository`() {
        val files = model.dependencies.map { edge ->
            val target = (edge.resolution as Resolution.Resolved).target as NodeRef.OfExternal
            Triple(edge.attributes["configuration"], target.coordinate, edge.attributes["path"])
        }.filterNot { it.second.endsWith("/") }
        assertEquals(
            listOf(
                Triple("runtimeOnly", "file:../kit/build/libs/kit.jar", "../../kit/build/libs/kit.jar"),
                Triple("implementation", "file:app/libs/text.jar", "libs/text.jar"),
            ),
            files.sortedBy { it.second },
        )
    }

    @Test
    fun `a file tree becomes an edge to its directory with its patterns and no list of files`() {
        val trees = model.dependencies.map { edge ->
            val target = (edge.resolution as Resolution.Resolved).target as NodeRef.OfExternal
            target.coordinate to edge.attributes
        }.filter { it.first.endsWith("/") }.sortedBy { it.first }
        assertEquals(
            listOf(
                "file:../kit/build/libs/" to mapOf(
                    "configuration" to "implementation",
                    "path" to "../../kit/build/libs",
                    "tree.include" to "*.jar",
                    "tree.exclude" to "old-*.jar",
                ),
                "file:app/vendor/" to mapOf("configuration" to "runtimeOnly", "path" to "vendor"),
            ),
            trees,
        )
    }

    @Test
    fun `the jars a project builds are a module attribute`() {
        assertEquals("app-1.0.jar", model.modules.single().attributes["jvm.archives"])
    }
}
