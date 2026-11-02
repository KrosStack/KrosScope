package io.github.krosstack.kscope.analyzer.schema.web.copy

import kotlin.test.Test
import kotlin.test.assertEquals

private fun sourcesOf(script: String): List<CopySource> = CopyScriptReader(script).sources()

class CopyFromCallsTest {
    @Test
    fun `every literal argument of a from call is a source`() {
        val script = """
            processResources {
                from("a", "b/c", file("d"), "${'$'}rootDir/e")
            }
        """.trimIndent()

        assertEquals(
            listOf("processResources PLAIN a", "processResources PLAIN b/c", "processResources BUILD_ROOT e"),
            described(script),
        )
    }

    @Test
    fun `the destination of a source is the into of its own block, else the into of the copy block`() {
        val script = """
            tasks.register<Copy>("web") {
                into("static")
                from("a") { into("own") }
                from("b")
                from("c") { exclude("*.map") }
                from("d") { into(layout.buildDirectory) }
            }
        """.trimIndent()

        assertEquals(
            listOf("web PLAIN a -> own", "web PLAIN b -> static", "web PLAIN c -> static", "web PLAIN d -> static"),
            described(script),
        )
    }

    @Test
    fun `the last into of a block counts and one that is not a literal is none`() {
        val script = """
            processResources {
                from("a") {
                    into("first")
                    into("second")
                }
                from("b") {
                    into("first")
                    into(computed)
                }
                into("outer")
                into("${'$'}buildDir/templated")
            }
        """.trimIndent()

        assertEquals(
            listOf("processResources PLAIN a -> second", "processResources PLAIN b"),
            described(script),
        )
    }

    @Test
    fun `an into of another from does not leak to its neighbours`() {
        val script = """
            processResources {
                from("a") { into("x") }
                from("b")
            }
        """.trimIndent()

        assertEquals(listOf("processResources PLAIN a -> x", "processResources PLAIN b"), described(script))
    }

    @Test
    fun `the anchors of the build script and of the module are read and the others are not`() {
        val script = """
            processResources {
                from("${'$'}rootDir/../web/dist")
                from("${'$'}{rootDir}/a")
                from("${'$'}{rootProject.rootDir}/b")
                from("${'$'}projectDir/c")
                from("${'$'}{projectDir}/d")
                from("${'$'}{project.projectDir}/e")
                from("${'$'}buildDir/f")
                from("${'$'}{layout.buildDirectory}/g")
                from("${'$'}rootDirectory/h")
                from("${'$'}rootDir/${'$'}name")
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "processResources BUILD_ROOT ../web/dist",
                "processResources BUILD_ROOT a",
                "processResources BUILD_ROOT b",
                "processResources MODULE c",
                "processResources MODULE d",
                "processResources MODULE e",
            ),
            described(script),
        )
    }

    @Test
    fun `an argument that is not one literal is skipped and the literals beside it are kept`() {
        val script = """
            processResources {
                from(layout.buildDirectory.dir("web"))
                from("a" + suffix)
                from(files("b"))
                from(variable)
                from("c")
                from("d\\e")
                from("")
            }
        """.trimIndent()

        assertEquals(listOf("processResources PLAIN c"), described(script))
    }

    @Test
    fun `a Groovy command is a source when the literal ends the expression`() {
        val script = """
            processResources {
                from 'a'
                from 'b'.toLowerCase()
                from "${'$'}rootDir/c"
            }
        """.trimIndent()

        assertEquals(listOf("processResources PLAIN a", "processResources BUILD_ROOT c"), described(script))
    }

    @Test
    fun `a source sits at its from call and is named by the from`() {
        val script = "processResources {\n    from(\"a\") {\n        into(\"x\")\n    }\n}\n"
        val source = sourcesOf(script).single()

        assertEquals("from(\"a\")", script.substring(source.call.start, source.call.end))
        assertEquals("from", script.substring(source.name.start, source.name.end))
        assertEquals("a", source.path.text)
    }

    @Test
    fun `the from calls of nested blocks are not those of the copy block`() {
        val script = """
            processResources {
                from("a")
                eachFile { from("b") }
                with(copySpec { from("c") })
                doLast { from("d") }
            }
        """.trimIndent()

        assertEquals(listOf("processResources PLAIN a"), described(script))
    }

    @Test
    fun `the sources come in the order of the script`() {
        val script = """
            tasks.named("b") { from("2") }
            processResources { from("1") }
            tasks.register<Copy>("a") { from("3") }
        """.trimIndent()

        assertEquals(listOf("b PLAIN 2", "processResources PLAIN 1", "a PLAIN 3"), described(script))
    }
}
