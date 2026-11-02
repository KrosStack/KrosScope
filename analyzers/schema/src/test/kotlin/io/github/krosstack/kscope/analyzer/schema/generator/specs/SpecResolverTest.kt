package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.generator.GeneratorWorkspace
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.analyzer.schema.generator.script.BuildScriptReader
import kotlin.test.Test
import kotlin.test.assertEquals

private const val API = "openapi: 3.0.0\ninfo:\n  title: Api\npaths: {}\n"
private const val OTHER = "name: not-a-spec\n"
private const val PROTO = "syntax = \"proto3\";\npackage p;\nmessage M {}\n"

private val appFiles = mapOf(
    "build.gradle.kts" to "",
    "client/build.gradle.kts" to "",
    "client/src/main/proto/own.proto" to PROTO,
)

private val specFiles = mapOf(
    "openapi/api.yaml" to API,
    "openapi/other.yaml" to OTHER,
    "proto/a/one.proto" to PROTO,
    "proto/a/two.proto" to PROTO,
    "proto/b/three.proto" to PROTO,
)

private fun workspace(specLocation: String = "spec") = GeneratorWorkspace(
    listOf(
        RepositorySpec("app", appFiles, listOf(ModuleSpec(":"), ModuleSpec(":client"))),
        RepositorySpec("spec", specFiles, listOf(ModuleSpec(":", roots = listOf(""))), specLocation),
    ),
)

private fun GeneratorWorkspace.reached(block: String): List<String> {
    val resolver = SpecResolver(model, CheckoutRoots(files, artifacts), SpecFiles(files, artifacts))
    val script = artifact("app", "client/build.gradle.kts")
    return BuildScriptReader(block).inputs().flatMap { resolver.targetsOf(it, script, module("app", ":client")) }
        .map { "${it.artifact.ref.repository.value}:${it.artifact.ref.path.value}" }
}

private fun spec(path: String) = "spec:$path"

private fun openApi(path: String) = "openApiGenerate { inputSpec.set(\"$path\") }"

private fun wire(path: String) = "wire { sourcePath { srcDir(\"$path\") } }"

class SpecResolverTest {
    @Test
    fun `a document of another repository is reached from the directory of the build root`() = workspace().use {
        assertEquals(listOf(spec("openapi/api.yaml")), it.reached(openApi("\$rootDir/../spec/openapi/api.yaml")))
        assertEquals(
            listOf(spec("openapi/api.yaml")),
            it.reached(openApi("\${rootProject.rootDir}/../spec/openapi/api.yaml")),
        )
    }

    @Test
    fun `a document is reached from the directory of the module, and a plain path is the module relative`() =
        workspace().use {
            assertEquals(
                listOf(spec("openapi/api.yaml")),
                it.reached(openApi("\$projectDir/../../spec/openapi/api.yaml")),
            )
            assertEquals(listOf(spec("openapi/api.yaml")), it.reached(openApi("../../spec/openapi/api.yaml")))
            assertEquals(emptyList(), it.reached(openApi("../spec/openapi/api.yaml")))
        }

    @Test
    fun `an absolute path stays absolute`() = workspace().use {
        val absolute = it.roots.getValue("spec").resolve("openapi/api.yaml").toString().replace('\\', '/')

        assertEquals(listOf(spec("openapi/api.yaml")), it.reached(openApi(absolute)))
    }

    @Test
    fun `a path is normalized before it is compared`() = workspace().use {
        assertEquals(
            listOf(spec("openapi/api.yaml")),
            it.reached(openApi("\$rootDir/./../spec/openapi/../openapi/./api.yaml")),
        )
    }

    @Test
    fun `a source path takes every proto file below it, and only those`() = workspace().use {
        assertEquals(
            listOf("proto/a/one.proto", "proto/a/two.proto", "proto/b/three.proto").map(::spec),
            it.reached(wire("\$rootDir/../spec/proto")),
        )
        assertEquals(
            listOf("proto/a/one.proto", "proto/a/two.proto").map(::spec),
            it.reached(wire("\$rootDir/../spec/proto/a")),
        )
        assertEquals(listOf(spec("proto/b/three.proto")), it.reached(wire("\$rootDir/../spec/proto/b/three.proto")))
    }

    @Test
    fun `a directory that merely starts like another directory does not take its files`() = workspace().use {
        assertEquals(emptyList(), it.reached(wire("\$rootDir/../spec/pro")))
        assertEquals(emptyList(), it.reached(wire("\$rootDir/../spec/proto/a/on")))
    }

    @Test
    fun `an OpenAPI input reaches documents only and a Wire input reaches proto files only`() = workspace().use {
        assertEquals(emptyList(), it.reached(openApi("\$rootDir/../spec/openapi/other.yaml")))
        assertEquals(emptyList(), it.reached(openApi("\$rootDir/../spec/proto/a/one.proto")))
        assertEquals(emptyList(), it.reached(openApi("\$rootDir/../spec/openapi")))
        assertEquals(emptyList(), it.reached(wire("\$rootDir/../spec/openapi")))
    }

    @Test
    fun `a path inside the repository of the script is reached as well`() = workspace().use {
        assertEquals(listOf("app:client/src/main/proto/own.proto"), it.reached(wire("\$projectDir/src/main/proto")))
        assertEquals(listOf("app:client/src/main/proto/own.proto"), it.reached(wire("src/main/proto")))
    }

    @Test
    fun `a repository that is not beside the other one is not reached through the parent directory`() =
        workspace(specLocation = "elsewhere/deeper/spec").use {
            assertEquals(emptyList(), it.reached(openApi("\$rootDir/../spec/openapi/api.yaml")))
            assertEquals(
                listOf(spec("openapi/api.yaml")),
                it.reached(openApi("\$rootDir/../elsewhere/deeper/spec/openapi/api.yaml")),
            )
        }

    @Test
    fun `a path outside every checkout, or one that no file system accepts, reaches nothing`() = workspace().use {
        assertEquals(emptyList(), it.reached(openApi("/nowhere/api.yaml")))
        assertEquals(emptyList(), it.reached(openApi("\$rootDir/../../../../../../../api.yaml")))
        assertEquals(emptyList(), it.reached(openApi("\$rootDir/../spec/openapi/\u0000api.yaml")))
    }
}
