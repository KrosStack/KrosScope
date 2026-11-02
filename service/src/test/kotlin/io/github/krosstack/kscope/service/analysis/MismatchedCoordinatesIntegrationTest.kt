package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Scans a library and an application in two repositories, the application pinning some version of the library. */
class MismatchedCoordinatesIntegrationTest {
    private val root: Path = Files.createTempDirectory("kscope-coordinates")

    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @AfterTest
    fun cleanUp() = TemporaryTree.delete(root)

    private fun repository(directory: String, files: Map<String, String>) {
        files.forEach { (path, text) ->
            val target = root.resolve(directory).resolve(path)
            Files.createDirectories(target.parent)
            Files.writeString(target, text)
        }
        val checkout = root.resolve(directory)
        TestGit.run(checkout, listOf("init", "-q"))
        TestGit.run(checkout, listOf("add", "-A"))
        TestGit.run(checkout, listOf("commit", "-q", "-m", "initial"))
    }

    private fun library(version: String?) = repository(
        "library",
        mapOf(
            "settings.gradle.kts" to "rootProject.name = \"library\"\ninclude(\":model\")\n",
            "model/build.gradle.kts" to "plugins {\n    kotlin(\"jvm\")\n    `maven-publish`\n}\n\n" +
                "group = \"org.example\"\n${version?.let { "version = \"$it\"\n" }.orEmpty()}\n" +
                "publishing {\n    publications {\n        create<MavenPublication>(\"maven\") {\n" +
                "            from(components[\"java\"])\n        }\n    }\n}\n",
            "model/src/main/kotlin/org/example/model/Greeter.kt" to
                "package org.example.model\n\nclass Greeter {\n    fun greet(name: String): String = name\n}\n",
        ),
    )

    private fun application(pinned: String) = repository(
        "application",
        mapOf(
            "settings.gradle.kts" to "rootProject.name = \"application\"\ninclude(\":app\")\n",
            "app/build.gradle.kts" to "plugins {\n    kotlin(\"jvm\")\n}\n\n" +
                "dependencies {\n    implementation(\"org.example:model:$pinned\")\n}\n",
            "app/src/main/kotlin/org/example/app/Main.kt" to
                "package org.example.app\n\nimport org.example.model.Greeter\n\n" +
                "class Main {\n    private val greeter: Greeter = Greeter()\n\n" +
                "    fun run(): String = greeter.greet(\"a\")\n}\n",
        ),
    )

    private fun GraphReader.compilerAnswers(): Map<String, Set<Certainty>> =
        relationships(RelationshipQuery(evidenceClass = EvidenceClass.COMPILER, limit = Int.MAX_VALUE))
            .filter { it.site?.artifact?.path?.value == MAIN }
            .mapNotNull { answer(it) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, certainties) -> certainties.toSet() }

    private fun GraphReader.answer(relationship: Relationship): Pair<String, Certainty>? {
        val resolved = relationship.resolution as? Resolution.Resolved
        val name = (resolved?.target as? NodeRef.OfSymbol)?.id?.let(::symbol)?.qualifiedName
        return if (resolved != null && name != null) name to resolved.certainty else null
    }

    private fun answersAfterScan(): Map<String, Set<Certainty>> = KrosScope.open(root).use { service ->
        service.scan()
        service.graph().compilerAnswers().filterKeys { it.startsWith("org.example.model") }
    }

    @Test
    fun `a class and a method of a library built at another version resolve heuristically`() {
        library("1.2.0")
        application("0.9.0")

        val answers = answersAfterScan()

        assertEquals(setOf(GREETER, GREET), answers.keys)
        assertEquals(listOf(setOf(Certainty.HEURISTIC)), answers.values.distinct())
    }

    @Test
    fun `a class and a method of a library whose version is computed resolve heuristically`() {
        library(null)
        application("3.9.0")

        val answers = answersAfterScan()

        assertEquals(setOf(GREETER, GREET), answers.keys)
        assertEquals(listOf(setOf(Certainty.HEURISTIC)), answers.values.distinct())
    }

    @Test
    fun `a class and a method of a library built at the pinned version resolve exactly`() {
        library("1.2.0")
        application("1.2.0")

        val answers = answersAfterScan()

        assertEquals(setOf(GREETER, GREET), answers.keys)
        assertEquals(listOf(setOf(Certainty.EXACT)), answers.values.distinct())
    }

    private companion object {
        const val MAIN = "app/src/main/kotlin/org/example/app/Main.kt"
        const val GREETER = "org.example.model.Greeter"
        const val GREET = "org.example.model.Greeter.greet"
    }
}
