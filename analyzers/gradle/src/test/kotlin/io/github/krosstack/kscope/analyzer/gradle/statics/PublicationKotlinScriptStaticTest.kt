package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PUBLICATIONS = "maven.publications"
private const val HEURISTIC = "maven.heuristic"

class PublicationKotlinScriptStaticTest {
    private val tree = StaticWorkspace("publication-computed")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun attributesOf(path: String) =
        tree.provided().model.modules.single { it.path == path }.attributes.filterKeys { it.startsWith("maven.") }

    @Test
    fun `the kotlin dsl reads afterEvaluate and a property cast to a string`() {
        kotlinModule(
            "kt",
            afterEvaluate(
                publication(
                    "groupId" to "\"org.example.kt\"",
                    "artifactId" to "\"kt-core\"",
                    "version" to "findProperty(\"kt.version\") as String",
                ),
            ),
            properties = "kt.version=7.0.0",
        )

        assertEquals(mapOf(PUBLICATIONS to "org.example.kt:kt-core:7.0.0"), attributesOf(":kt"))
    }

    @Test
    fun `the kotlin dsl reads project afterEvaluate and a provider with the root properties`() {
        tree.write("gradle.properties", "kt.group=org.example.root\nkt.version=8.1.0\n")
        kotlinModule(
            "kt",
            afterEvaluate(
                publication(
                    "groupId" to "property(\"kt.group\") as String",
                    "artifactId" to "\"kt-core\"",
                    "version" to "providers.gradleProperty(\"kt.version\").get()",
                ),
                receiver = "project",
            ),
        )

        assertEquals(mapOf(PUBLICATIONS to "org.example.root:kt-core:8.1.0"), attributesOf(":kt"))
    }

    @Test
    fun `the kotlin dsl records a computed suffix as unspecified and heuristic`() {
        kotlinModule(
            "kt",
            afterEvaluate(
                publication(
                    "groupId" to "\"org.example.kt\"",
                    "artifactId" to "\"kt-core\"",
                    "version" to "findProperty(\"kt.version\").toString() + versionSuffix()",
                ),
            ),
            properties = "kt.version=7.0.0",
        )

        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.kt:kt-core:unspecified"),
            attributesOf(":kt"),
        )
    }

    @Test
    fun `a convention callback runs after the top-level assignments of the module`() {
        tree.write("settings.gradle", "pluginManagement { includeBuild('build-logic') }\ninclude ':kt'\n")
        tree.write("build-logic/settings.gradle", "rootProject.name = 'build-logic'\n")
        tree.writeKotlinScript(
            "build-logic/src/main/kotlin/example.release.gradle.kts",
            block("afterEvaluate", assignments = mapOf("project.version" to "\"2.0\"")),
        )
        tree.writeKotlinScript(
            "kt/build.gradle.kts",
            block(
                "plugins",
                literalCall("id", "java"),
                literalCall("id", "maven-publish"),
                literalCall("id", "example.release"),
            ),
            assignment("group", "org.example.kt"),
            assignment("version", "1.0"),
        )

        assertEquals(mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.kt:kt:2.0"), attributesOf(":kt"))
    }

    private fun assignment(property: String, value: String) = ScriptCall(
        "=",
        property,
        listOf(ScriptArgument(null, value, "\"$value\"")),
        emptyList(),
        emptyMap(),
        block("x").range,
    )

    private fun kotlinModule(name: String, vararg body: ScriptCall, properties: String? = null) {
        tree.write("settings.gradle", "rootProject.name = 'shared-libs'\ninclude ':$name'\n")
        tree.writeKotlinScript(
            "$name/build.gradle.kts",
            block("plugins", literalCall("id", "java"), literalCall("id", "maven-publish")),
            *body,
        )
        properties?.let { tree.write("$name/gradle.properties", it) }
    }

    private fun afterEvaluate(publication: ScriptCall, receiver: String? = null) = block(
        "afterEvaluate",
        block("publishing", block("publications", publication)),
    ).copy(receiver = receiver)

    private fun publication(vararg settings: Pair<String, String>) = block(
        "register",
        assignments = settings.toMap(),
    ).copy(
        arguments = listOf(ScriptArgument(null, "release", "\"release\"")),
        typeArguments = listOf("MavenPublication"),
    )
}
