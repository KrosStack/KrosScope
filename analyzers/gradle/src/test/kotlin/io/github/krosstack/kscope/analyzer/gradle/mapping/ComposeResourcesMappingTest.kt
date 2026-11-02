package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.BuildDocument
import io.github.krosstack.kscope.analyzer.gradle.ComposeResourcesDocument
import io.github.krosstack.kscope.analyzer.gradle.GeneratorsDocument
import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.analyzer.gradle.SUPPORTED_SCHEMA
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeResourcesMappingTest {
    private fun documentOf(generators: GeneratorsDocument) = ProjectDocument(
        schema = SUPPORTED_SCHEMA,
        path = ":ui:shared",
        dir = "/work/ui/shared",
        buildDir = "/work/ui/shared/build",
        build = BuildDocument(name = "work", rootDir = "/work"),
        plugins = listOf("org.jetbrains.compose"),
        generators = generators,
    )

    @Test
    fun `the package, the visibility and the group of the resource class become module attributes`() {
        val compose = ComposeResourcesDocument(
            packageOfResClass = "sample.ui.res",
            publicResClass = true,
            group = "tasktide.ui",
        )

        val attributes = ModuleAttributes.of(documentOf(GeneratorsDocument(composeResources = compose)))

        assertEquals(
            mapOf(
                "compose.resources.group" to "tasktide.ui",
                "compose.resources.packageOfResClass" to "sample.ui.res",
                "compose.resources.publicResClass" to "true",
            ),
            attributes,
        )
    }

    @Test
    fun `a resource class without a package or visibility of its own has the group only`() {
        val compose = ComposeResourcesDocument(packageOfResClass = null, publicResClass = false, group = "tasktide")

        val attributes = ModuleAttributes.of(documentOf(GeneratorsDocument(composeResources = compose)))

        assertEquals(mapOf("compose.resources.group" to "tasktide"), attributes)
    }

    @Test
    fun `a project without the compose extension has none`() {
        assertEquals(emptyMap(), ModuleAttributes.of(documentOf(GeneratorsDocument())))
    }

    @Test
    fun `the document of the model plugin carries the compose resources of a project`() {
        val json = """[{"schema":1,"path":":ui","dir":"/w/ui","buildDir":"/w/ui/build",
            "build":{"name":"w","rootDir":"/w"},"plugins":["org.jetbrains.compose"],
            "generators":{"composeResources":{"packageOfResClass":null,"publicResClass":true,"group":"w"}}}]"""

        val document = ModelDocuments.parse(json).single()

        assertEquals(ComposeResourcesDocument(null, true, "w"), document.generators.composeResources)
    }
}
