package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOT_ANALYSED = "build.publication.not-analysed"
private const val PUBLICATIONS = "maven.publications"
private const val LINE = "org.example.line"

class PublicationAssignedPropertiesStaticTest {
    private val tree = StaticWorkspace("publication-computed")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private val groovyAssignments = listOf(
        "releaseLine = '$LINE'",
        "project.releaseLine = '$LINE'",
        "this.releaseLine = '$LINE'",
        "releaseLine '$LINE'",
        "project.releaseLine '$LINE'",
        "ext.releaseLine = '$LINE'",
        "project.ext.releaseLine = '$LINE'",
        "ext { releaseLine = '$LINE' }",
        "ext { set('releaseLine', '$LINE') }",
        "setProperty('releaseLine', '$LINE')",
        "project.setProperty('releaseLine', '$LINE')",
        "project.extensions.extraProperties.set('releaseLine', '$LINE')",
        "project.ext { releaseLine += '.x' }",
        "ext.set('releaseLine', '$LINE')",
        "ext['releaseLine'] = '$LINE'",
        "ext.releaseLine += '.x'",
    )

    @Test
    fun `a group read directly from a property that a groovy script assigns is left unread`() {
        groovyAssignments.forEach { assignment ->
            writeGroovyModule(assignment, "groupId findProperty('releaseLine')", "artifactId 'extras'")

            assertUnread("the group is computed", assignment)
        }
    }

    @Test
    fun `an artifact id read directly from a property that a groovy script assigns is left unread`() {
        writeGroovyModule(
            "ext.releaseLine = 'extras'",
            "groupId 'org.example.shared'",
            "artifactId property('releaseLine')",
        )

        assertUnread("the artifact id is computed", "ext.releaseLine")
    }

    @Test
    fun `a provider read is not affected by an assignment in the script`() {
        tree.write("gradle.properties", "releaseLine=org.example.root\n")
        writeGroovyModule(
            "ext.releaseLine = '$LINE'",
            "groupId providers.gradleProperty('releaseLine').get()",
            "artifactId 'extras'",
        )

        assertEquals("org.example.root:extras:1.0.0", publicationOfExtras())
    }

    @Test
    fun `a direct read of a property that no script assigns reads the module file`() {
        writeGroovyModule(
            "ext.otherLine = '$LINE'",
            "groupId findProperty('releaseLine')",
            "artifactId 'extras'",
            properties = "releaseLine=org.example.module\n",
        )

        assertEquals("org.example.module:extras:1.0.0", publicationOfExtras())
    }

    @Test
    fun `a group read directly from a property that a kotlin script keeps in extra is left unread`() {
        val arguments = listOf(literal("releaseLine"), literal(LINE))
        val assignments = listOf(
            block("set").copy(receiver = "extra", arguments = arguments),
            block("extra").copy(arguments = arguments),
        )
        assignments.forEach { assignment ->
            writeKotlinModule(assignment)

            assertUnread("the group is computed", "${assignment.receiver}.${assignment.name}")
        }
    }

    private fun literal(text: String) = ScriptArgument(null, text, "\"$text\"")

    private fun publicationOfExtras() =
        tree.provided().model.modules.single { it.path == ":extras" }.attributes[PUBLICATIONS]

    private fun writeKotlinModule(assignment: ScriptCall) {
        val settings = mapOf(
            "groupId" to "findProperty(\"releaseLine\") as String",
            "artifactId" to "\"extras\"",
            "version" to "\"1.0.0\"",
        )
        val publication = block("register", assignments = settings).copy(
            arguments = listOf(literal("release")),
            typeArguments = listOf("MavenPublication"),
        )
        tree.write("settings.gradle", "rootProject.name = 'shared-libs'\ninclude ':extras'\n")
        tree.writeKotlinScript(
            "extras/build.gradle.kts",
            block("plugins", literalCall("id", "java"), literalCall("id", "maven-publish")),
            assignment,
            block("afterEvaluate", block("publishing", block("publications", publication))),
        )
        tree.write("extras/gradle.properties", "releaseLine=org.example.module\n")
    }

    private fun writeGroovyModule(
        assignment: String,
        group: String,
        artifact: String,
        properties: String = "releaseLine=org.example.module\n",
    ) {
        tree.write("settings.gradle", "rootProject.name = 'shared-libs'\ninclude ':extras'\n")
        tree.write(
            "extras/build.gradle",
            """
            plugins {
                id 'java'
                id 'maven-publish'
            }
            $assignment
            afterEvaluate {
                publishing {
                    publications {
                        release(MavenPublication) {
                            $group
                            $artifact
                            version '1.0.0'
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        tree.write("extras/gradle.properties", properties)
    }

    private fun assertUnread(reason: String, form: String) {
        val provided = tree.provided()
        val attributes = provided.model.modules.single { it.path == ":extras" }.attributes
        assertEquals(emptyMap(), attributes.filterKeys { it.startsWith("maven.") }, form)
        val problem = provided.problems(NOT_ANALYSED).single { ":extras" in it.message }
        assertEquals(DiagnosticSeverity.WARNING, problem.severity, form)
        assertTrue(reason in problem.message, form)
    }
}
